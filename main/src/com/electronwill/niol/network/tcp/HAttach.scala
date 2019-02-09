package com.electronwill.niol.network.tcp

import java.nio.channels.{SelectionKey, SocketChannel}
import java.util

import com.electronwill.niol.buffer.storage.{BytesStorage, StorageProvider}
import com.electronwill.niol.buffer.{CircularBuffer, NiolBuffer}

import scala.annotation.tailrec

/**
 * An implementation of [[ClientAttach]] that processes packets that are prefixed by a header
 * (hence the H in HAttach).
 *
 * @param sci infos about the ServerSocketChannel that accepted the client's connection
 * @param channel the SocketChannel used to communicate with the client
 * @param selectionKey the key representing the registration of the SocketChannel with the server
 * @param rTransform a transformation to apply on the read data (received from the client)
 * @param wTransform a transformation to apply on the written data (sent to the client)
 * @tparam A generic parameter
 */
abstract class HAttach[A <: HAttach[A]] (
    private[this] val sci: ServerChannelInfos[A],
    private[this] val channel: SocketChannel,
    private[this] val selectionKey: SelectionKey,
    private[this] var rTransform: BytesTransform = null,
    private[this] var wTransform: BytesTransform = null)
  extends ClientAttach[A] {
  /**
   * The queue that contains the data waiting for being written.
   */
  private[this] val writeQueue = new util.ArrayDeque[(NiolBuffer, Runnable)]
  private[this] var (readStorage, packetBufferBase, packetBufferProvider) = createReadBuffers(rTransform != null)
  private[this] var additionalStorage: BytesStorage = _
  private[this] var packetBuffer: NiolBuffer = packetBufferBase
  private[this] var state = HInputState.READ_HEADER
  private[this] var packetLength = -1
  @volatile
  private[this] var eos: Boolean = false

  final def streamEnded: Boolean = eos

  final def listener: TcpListener[A] = sci.listener

  final def readTransform = Option(rTransform)

  final def readTransform_=(f: BytesTransform): Unit = {
    val add = (f != null) && (rTransform == null)
    val remove = (f == null) && (rTransform != null)
    if (add || remove) {
      // Creates new buffers
      val (newRead, newBase, newProvider) = createReadBuffers(add)

      // Copies the current data to the new buffers
      val additionalLength = packetLength - newBase.capacity
      val newPacketBuffer =
        if (additionalLength > 0) {
          additionalStorage = newProvider.getStorage(additionalLength)
          newBase + CircularBuffer(additionalStorage)
        } else {
          newBase
        }
      packetBuffer.read(newPacketBuffer)

      // Updates the variables
      readStorage = newRead
      packetBufferBase = newBase
      packetBuffer = newPacketBuffer
      packetBufferProvider = newProvider
      rTransform = f
    }
  }

  private def createReadBuffers(hasTransform: Boolean): (BytesStorage, NiolBuffer, StorageProvider) = {
    val s = sci.bufferSettings
    val provider = s.packetStorageProvider

    // Gets the BytesStorage on which the BufferTransform is applied
    val readBuff = if (hasTransform) s.readStorageProvider.getStorage(s.readBufferSize) else null

    // Gets the packetBuffer
    val packetBuff = CircularBuffer(provider.getStorage(s.packetBufferBaseSize))

    // Returns the result
    (readBuff, packetBuff, provider)
  }


  final def writeTransform: Option[BytesTransform] = Option(wTransform)

  final def writeTransform_=(t: BytesTransform): Unit = wTransform = t

  @tailrec
  protected[network] final def readMore(): Unit = {
    if (rTransform == null) {
      // No transformation => simply read the data into packetBuffer
      eos = (packetBuffer.writeSome(channel) < 0)
    } else {
      // Read the data to readStorage, transform it and write the result to packetBuffer
      // 1-read
      val bb = readStorage.byteBuffer
      eos = (channel.read(bb) < 0)
      val length = bb.position()
      val byteArray =
        if (bb.hasArray) {
          bb.array
        } else {
          val arr = new Array[Byte](length)
          bb.get(arr)
          arr
        }
      // 2-transform
      val transformed = rTransform(Bytes(byteArray, length))
      // 3-add the result to packetBuffer
      packetBuffer.write(transformed.array, 0, transformed.length)
    }
    state match {
      case HInputState.READ_HEADER =>
        packetLength = readHeader(packetBuffer)
        if (packetLength >= 0) {
          state = HInputState.READ_DATA
          if (packetBuffer.readableBytes >= packetLength) {
            // All the data is available => handle it
            handleDataView()
          } else if (packetBuffer.capacity < packetLength) {
            // The buffer is too small => create an additional buffer
            val additionalCapacity = packetLength - packetBuffer.capacity
            val additionalStorage = packetBufferProvider.getStorage(additionalCapacity)
            // Creates a CompositeBuffer without copying the data
            packetBuffer = packetBufferBase + CircularBuffer(additionalStorage)

            // Attempts to fill the buffer -- tail recursive call!
            readMore()
          }
          // Unlike a StraightBuffer, a CircularBuffer doesn't need to be compacted.
        }
      case HInputState.READ_DATA =>
        if (packetBuffer.readableBytes >= packetLength) {
          handleDataView()
        }
    }
  }

  /**
   * Tries to read the packet's header.
   *
   * @param buffer the buffer view containing the header
   * @return the length, in bytes, of the next packet data, or -1 if the header is incomplete.
   */
  protected def readHeader(buffer: NiolBuffer): Int

  /**
   * Writes a packet's header.
   * @param data the packet's content
   * @param output where to write the header
   */
  protected def writeHeader(data: NiolBuffer, output: NiolBuffer): Unit

  protected[network] final def writeMore(): Boolean = {
    writeQueue.synchronized { // Sync protects the queue and the consistency of the interestOps
      var queued = writeQueue.peek() // the next element. null if the queue is empty
      while (queued ne null) {
        val buffer = queued._1
        buffer.readSome(channel)
        if (buffer.isEmpty) {
          writeQueue.poll()
          val completionHandler = queued._2
          if (completionHandler ne null) {
            completionHandler.run()
          }
          queued = writeQueue.peek() // fetches the next element
        } else {
          return false
        }
      }
      selectionKey.interestOps(SelectionKey.OP_READ) // Stop listening for OP_WRITE
      true
    }
  }

  /**
   * Writes some data to the client. The data isn't written immediately but at some time in the
   * future. Therefore this method isn't blocking.
   *
   * @param buffer the data to write
   */
  final def write(buffer: NiolBuffer): Unit = write(buffer, null)

  /**
   * Asynchronously writes some data to the client, and executes the given completion handler
   * when the operation completes.
   *
   * @param buffer            the data to write
   * @param completionHandler the handler to execute after the operation
   */
  final def write(buffer: NiolBuffer, completionHandler: Runnable): Unit = {
    val finalBuffer =
      if (wTransform == null) {
        // No transformation => use the buffer as is
        buffer
      } else {
        // Transformation => make a byte array and apply the transformation function
        val array = buffer.toArray()
        val input = Bytes(array, array.length)
        val output = wTransform(input)
        CircularBuffer.wrap(output.array, 0, output.length)
      }
    writeQueue.synchronized { // Sync protects the queue and the consistency of the interestOps
      if (writeQueue.isEmpty) {
        finalBuffer.readSome(channel, finalBuffer.readableBytes)
        if (finalBuffer.readableBytes > 0) {
          // Incomplete write => continue the operation as soon as possible
          writeQueue.offer((finalBuffer, completionHandler))
          selectionKey.interestOps(selectionKey.interestOps | SelectionKey.OP_WRITE)
        }
      } else {
        writeQueue.offer((finalBuffer, completionHandler))
      }
    }
  }

  /**
   * Handles the data of the currently available packet.
   */
  private final def handleDataView(): Unit = {
    // Isolates the packet
    val dataView = packetBuffer.slice(packetLength)

    try {
      // Handles the packet
      handleData(dataView)
    } finally {
      // Prepares for the next packet
      state = HInputState.READ_HEADER // switches the state
      packetBuffer.advance(packetLength) // marks the data as read

      // Discards the additional buffer, if any
      if (packetBuffer ne packetBufferBase) {
        additionalStorage.discardNow()
        additionalStorage = null
        packetBuffer = packetBufferBase
        packetBuffer.clear()
      }
    }
  }
}

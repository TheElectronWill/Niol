package com.electronwill.niol.network.tcp

import java.nio.channels.{SelectionKey, SocketChannel}
import java.util

import com.electronwill.niol.buffer.provider.BufferProvider
import com.electronwill.niol.buffer.{BaseBuffer, CircularBuffer, NiolBuffer, StraightBuffer}

import scala.annotation.tailrec


abstract class HAttach[A <: HAttach[A]] (
    private[this] val sci: ServerChannelInfos[A],
    private[this] val channel: SocketChannel,
    private[this] var transform: NiolBuffer => Array[Byte] = null)
    private[this] var rTransform: BufferTransform = null,
    private[this] var wTransform: BufferTransform = null)
  extends ClientAttach[A] {
  /**
   * The queue that contains the data waiting for being written.
   */
  private[this] val writeQueue = new util.ArrayDeque[(NiolBuffer, Runnable)]
  private[this] var (readBuffer, packetBufferBase, packetBufferProvider) = createReadBuffers(rTransform != null)
  private[this] var packetBuffer: NiolBuffer = packetBufferBase
  private[this] var state = InputState.READ_HEADER
  private[this] var packetLength = -1
  @volatile
  private[this] var eos: Boolean = false

  final def streamEnded: Boolean = eos

  final def listener: TcpListener[A] = sci.listener

  final def readTransform = Option(rTransform)

  final def readTransform_=(f: BufferTransform): Unit = {
    val add = (f != null) && (rTransform == null)
    val remove = (f == null) && (rTransform != null)
    if (add || remove) {
      // Creates new buffers
      val (newRead, newBase, newProvider) = createReadBuffers(add)

      // Copies the current data to the new buffers
      val additionalLength = packetLength - newBase.capacity
      val newPacketBuffer =
        if (additionalLength > 0) {
          newBase + new StraightBuffer(newProvider.get(additionalLength))
        } else {
          newBase
        }
      packetBuffer >>: newPacketBuffer

      // Updates the variables
      readBuffer = newRead
      packetBufferBase = newBase
      packetBuffer = newPacketBuffer
      packetBufferProvider = newProvider
      rTransform = f
    }
  }

  private def createReadBuffers(hasTransform: Boolean): (BaseBuffer, NiolBuffer, BufferProvider) = {
    val s = sci.bufferSettings
    val readBuff = if (hasTransform) s.readBufferProvider.get(s.readBufferSize) else null
    val prov = s.packetBufferProvider
    val packetBuff = new CircularBuffer(prov.get(s.packetBufferBaseSize))
    (readBuff, packetBuff, prov)
  }


  final def writeTransform: Option[BufferTransform] = Option(wTransform)

  final def writeTransform_=(t: BufferTransform): Unit = wTransform = t

  @tailrec
  protected[network] final def readMore(): Unit = {
    if (rTransform == null) {
      eos = (channel >>: packetBuffer)._2
    } else {
      eos = (channel >>: readBuffer)._2
      val transformed = rTransform(readBuffer)
      transformed >>: packetBuffer
    }
    state match {
      // First, the header must be read
      case InputState.READ_HEADER => {
        packetLength = readHeader(packetBuffer)
        if (packetLength >= 0) {
          state = InputState.READ_DATA
          if (packetBuffer.readAvail >= packetLength) {
            // All the data is available => handle it
            handleDataView()
          } else if (packetBuffer.capacity < packetLength) {
            // The buffer is too small => create an additional buffer
            val additional = packetLength - packetBuffer.capacity
            val additionalBuffer = packetBufferProvider.get(additional)
            val additionalStraight = new StraightBuffer(additionalBuffer)
            // Creates a CompositeBuffer without copying the data
            packetBuffer = packetBufferBase + additionalStraight
            // Attempts to fill the buffer -- tail recursive call!
            readMore()
          }
          // Unlike a StraightBuffer, a CircularBuffer doesn't need to be compacted.
        }
      }
      // Then, the data must be read
      case InputState.READ_DATA => {
        if (packetBuffer.readAvail >= packetLength) {
          handleDataView()
        }
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

  protected[network] final def writeMore(): Boolean = {
    writeQueue.synchronized { // Sync protects the queue and the consistency of the interestOps
      var queued = writeQueue.peek() // the next element. null if the queue is empty
      while (queued ne null) {
        val buffer = queued._1
        channel <<: buffer
        if (buffer.readAvail == 0) {
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
      sci.selectionKey.interestOps(SelectionKey.OP_READ) // Stop listening for OP_WRITE
      true
    }
  }

  /**
   * Writes some data to the client. The data isn't written immediately but at some time in the
   * future. Therefore this method isn't blocking.
   *
   * @param buffer the data to write
   */
  final def write(buffer: NiolBuffer): Unit = {
    write(buffer, null)
  }

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
      } else if(buffer.isBase) {
        // The buffer is a BaseBuffer => transform it directly
        wTransform(buffer.asInstanceOf[BaseBuffer])
      } else {
        // The buffer isn't a BaseBuffer => copy its content to a BaseBuffer and transform it
        val baseBuffer = BufferProvider.DefaultInHeapProvider.get(buffer.readAvail)
        buffer >>: baseBuffer
        wTransform(baseBuffer)
      }
    writeQueue.synchronized { // Sync protects the queue and the consistency of the interestOps
      if (writeQueue.isEmpty) {
        channel <<: finalBuffer
        if (finalBuffer.readAvail > 0) {
          // Uncomplete write => continue the operation as soon as possible
          sci.selectionKey.interestOps(SelectionKey.OP_WRITE) // Continue to write later
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
    val dataView = packetBuffer.subRead(maxLength = packetLength)

    try {
      // Handles the packet
      handleData(dataView)
    } finally {
      // Prepares for the next packet
      state = InputState.READ_HEADER // switches the state
      packetBuffer.skipRead(packetLength) // marks the data as read

      // Discards the additional buffer, if any
      if (packetBuffer ne packetBufferBase) {
        packetBuffer.discard()
        packetBufferBase.clear()
        packetBuffer = packetBufferBase
      }
      // Discards the view buffer
      dataView.discard()
    }
  }
}

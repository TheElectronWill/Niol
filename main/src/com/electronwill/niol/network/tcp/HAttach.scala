package com.electronwill.niol.network.tcp

import java.nio.channels.{SelectionKey, SocketChannel}
import java.util

import com.electronwill.niol.buffer.provider.BufferProvider
import com.electronwill.niol.buffer.{CircularBuffer, NiolBuffer, StraightBuffer}

import scala.annotation.tailrec


abstract class HAttach[A <: HAttach[A]] (
    private[this] val sci: ServerChannelInfos[A],
    private[this] val channel: SocketChannel,
    private[this] var transform: NiolBuffer => Array[Byte] = null)
  extends ClientAttach[A] {
  /**
   * The queue that contains the data waiting for being written.
   */
  private[this] val writeQueue = new util.ArrayDeque[(NiolBuffer, Runnable)]

  private[this] var (readBuffer, packetBufferBase, packetBufferProvider) = createBuffers(transform != null)
  private[this] var packetBuffer: NiolBuffer = packetBufferBase
  private[this] var state = InputState.READ_HEADER
  private[this] var packetLength = -1
  @volatile
  private[this] var eos: Boolean = false

  def streamEnded: Boolean = eos

  def listener: TcpListener[A] = sci.listener

  def readTransformation: Option[NiolBuffer => Array[Byte]] = Option(transform)

  final def readTransformation_=(f: NiolBuffer => Array[Byte]): Unit = {
    val add = (f != null) && (transform == null)
    val remove = (f == null) && (transform != null)
    if (add || remove) {
      // Creates new buffers
      val (newRead, newBase, newProvider) = createBuffers(add)

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
      transform = f
    }
  }

  private def createBuffers(withTransform: Boolean): (NiolBuffer, NiolBuffer, BufferProvider) = {
    val s = sci.bufferSettings
    if (withTransform) {
      // With a transformation, data is processed before being copied to the packetBuffer
      val prov = s.postTransformBufferProvider
      val base = new CircularBuffer(prov.get(s.packetBufferBaseSize))
      val read = s.readBufferProvider.get(s.preTransformReadSize)
      (read, base, prov)
    } else {
      // Without a transformation, data is read directly from the SocketChannel to the packetBuffer
      val prov = s.readBufferProvider
      val buff = new CircularBuffer(prov.get(s.packetBufferBaseSize))
      (null, buff, prov)
    }
  }

  @tailrec
  protected[network] final def readMore(): Unit = {
    if (transform == null) {
      eos = (channel >>: packetBuffer)._2
    } else {
      eos = (channel >>: readBuffer)._2
      val transformed = transform(readBuffer)
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
    writeQueue.synchronized { // Sync protects the queue and the consistency of the interestOps
      if (writeQueue.isEmpty) {
        channel <<: buffer
        if (buffer.readAvail > 0) {
          writeQueue.offer((buffer, completionHandler))
          sci.selectionKey.interestOps(SelectionKey.OP_WRITE) // Continue to write later
        }
      } else {
        writeQueue.offer((buffer, completionHandler))
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

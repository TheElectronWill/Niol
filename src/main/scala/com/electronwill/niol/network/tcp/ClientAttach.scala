package com.electronwill.niol.network.tcp

import java.nio.channels.{SelectionKey, SocketChannel}
import java.util

import com.electronwill.niol.buffer.provider.BufferProvider
import com.electronwill.niol.buffer.{CircularBuffer, NiolBuffer, StraightBuffer}

import scala.annotation.tailrec

/**
 * Stores data associated to a unique TCP client. This abstract class provides basic reading and
 * writing functionnality. Additionnal information and features may be added by the subclasses.
 *
 * == Thread-safety ==
 * - The public methods of ClientAttach may be called from any thread without problem.
 * - The others are always called from the Selector thread. In particular, [[readHeader()]] and
 * [[handleData()]] don't need to be thread-safe.
 * - The completion handlers are run on the Selector thread. Therefore they should NOT perform
 * long computations. If you have long computations to do, send them to an ExecutorService or
 * something similar.
 *
 * @author TheElectronWill
 */
abstract class ClientAttach(val sci: ServerChannelInfos[_],
                            val channel: SocketChannel,
                            transformFunction: NiolBuffer => Array[Byte] = null) {
  // read infos
  private[this] var _transform: NiolBuffer => Array[Byte] = transformFunction
  private[this] var (readBuffer, packetBufferBase, packetBufferProvider): (NiolBuffer, NiolBuffer, BufferProvider) = {
    if (_transform == null) {
      val directBuff = sci.readBufferProvider.getBuffer(sci.packetBufferBaseSize)
      val buff = new CircularBuffer(directBuff)
      val prov = sci.readBufferProvider
      (null, buff, prov)
    } else {
      val directBuff = sci.readBufferProvider.getBuffer(sci.preTransformReadSize)
      val heapBuff = sci.postTransformBufferProvider.getBuffer(sci.packetBufferBaseSize)
      val baseBuff = new CircularBuffer(heapBuff)
      val prov = sci.postTransformBufferProvider
      (directBuff, baseBuff, prov) // readBuffer is direct, packetBufferBase is heap-based
    }
  }

  private[this] var packetBuffer: NiolBuffer = packetBufferBase
  private[this] var state: InputState = InputState.READ_HEADER
  private[this] var packetLength: Int = _

  @volatile
  private[this] var eos: Boolean = false

  /** @return true if the end of the stream has been reached, false otherwise */
  def streamEnded: Boolean = {
    eos
  }

  /**
   * The queue that contains the data waiting for being written.
   */
  private[this] val writeQueue = new util.ArrayDeque[(NiolBuffer, Runnable)]

  /** @return the transformation function, if any */
  def transform: Option[NiolBuffer => Array[Byte]] = {
    Option(_transform)
  }

  /**
   * Sets the transformation function, which is applied to the received data just after its receipt. The function is
   * applied '''before''' the packets are reconstructed. To handle the packets, override the [[handleData()]] method.
   *
   * @param f the function
   */
  def transform_=(f: NiolBuffer => Array[Byte]): Unit = {
    val remove = (f == null) && (_transform != null)
    val add = (f != null) && (_transform == null)
    if (remove || add) {
      val (newRead, newBase, newProvider): (NiolBuffer, NiolBuffer, BufferProvider) = {
        if (remove) {
          // Removing the transform, data can be read directly from the SocketChannel to the packetBuffer
          val prov = sci.readBufferProvider
          val buff = new CircularBuffer(prov.getBuffer(sci.packetBufferBaseSize))
          (null, buff, prov)
        } else {
          // Adding the transform, data must be processed before being copied to the packetBuffer
          val prov = sci.postTransformBufferProvider
          val base = new CircularBuffer(prov.getBuffer(sci.packetBufferBaseSize))
          val read = sci.readBufferProvider.getBuffer(sci.preTransformReadSize)
          (read, base, prov)
        }
      }
      // Copies the current data to the new packetBuffer
      val additionalLength = packetLength - newBase.capacity
      val newPacketBuffer =
        if (additionalLength > 0) {
          newBase + new StraightBuffer(newProvider.getBuffer(additionalLength))
        } else {
          newBase
        }
      packetBuffer >>: newPacketBuffer

      // Updates the variables
      readBuffer = newRead
      packetBufferBase = newBase
      packetBuffer = newPacketBuffer
      packetBufferProvider = newProvider
    }
    _transform = f
  }

  /**
   * Reads more data from the SocketChannel.
   */
  @tailrec
  private[network] final def readMore(): Unit = {
    if (_transform == null) {
      eos = (channel >>: packetBuffer)._2
    } else {
      eos = (channel >>: readBuffer)._2
      val transformed = _transform(readBuffer)
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
            val additionalBuffer = packetBufferProvider.getBuffer(additional)
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
   * Writes more pending data, if any, to the SocketChannel.
   *
   * @return true if all the pending data has been written, false otherwise
   */
  private[network] final def writeMore(): Boolean = {
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
                              sci.skey.interestOps(SelectionKey.OP_READ) // Stop listening for OP_WRITE
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
                                  sci.skey.interestOps(SelectionKey.OP_WRITE) // Continue to write later
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

  /**
   * Tries to read the packet's header.
   *
   * @param buffer the buffer view containing the header
   * @return the length, in bytes, of the next packet data, or -1 if the header is incomplete.
   */
  protected def readHeader(buffer: NiolBuffer): Int

  /**
   * Handles the packet's data.
   *
   * @param buffer the buffer view containing all the packet's data.
   */
  protected def handleData(buffer: NiolBuffer): Unit
}

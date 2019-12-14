package com.electronwill.niol.network.tcp

import com.electronwill.niol.buffer.NiolBuffer

/**
 * Stores data associated to one TCP client. This abstract class provides basic reading and
 * writing functionnality. Additionnal information and features may be added by the subclasses.
 *
 * ==Thread-safety==
 *  - The public methods of ClientAttach may be called from any thread without problem.
 *  - The protected methods are called from the Selector thread. In particular,
 *    [[HAttach.readHeader]] and [[ClientAttach.handleData]] don't need to be thread-safe.
 *  - The completion handlers are run on the Selector thread. Therefore they should NOT perform
 *    long computations. If you have long computations to do, send them to an ExecutorService or
 *    something similar.
 *
 * @author TheElectronWill
 */
trait ClientAttach[A <: ClientAttach[A]] {
  /**
   * Checks if the end of the stream has been reached.
   *
   * @return true if the end of the stream has been reached, false otherwise
   */
  def streamEnded: Boolean

  /**
   * Returns the TcpListener to notify when the client disconnects.
   *
   * @return the TcpListener associated to this ClientAttach.
   */
  def listener: TcpListener[A]

  /**
   * Returns the function that transforms the received data.
   *
   * @return the function, if any
   */
  def readTransform: Option[BytesTransform]

  /**
   * Sets the function that is applied to the received data just after its receipt.
   * The function is applied '''before''' the packets are reconstructed.
   *
   * To handle the packets, override the [[ClientAttach.handleData]] method.
   *
   * @param t the function
   */
  def readTransform_=(t: BytesTransform): Unit

  /**
   * Returns the function that transforms the outgoing data.
   *
   * @return the function, if any
   */
  def writeTransform: Option[BytesTransform]

  /**
   * Sets the function that is applied to the outgoing data just before its sending.
   *
   * @param t the function
   */
  def writeTransform_=(t: BytesTransform): Unit

  /**
   * Writes some data to the client. The data isn't written immediately but at some time in the
   * future. Therefore this method isn't blocking.
   *
   * @param buffer the data to write
   */
  def write(buffer: NiolBuffer): Unit

  /**
   * Asynchronously writes some data to the client, and executes the given completion handler
   * when the operation completes.
   *
   * @param buffer            the data to write
   * @param completionHandler the handler to execute after the operation
   */
  def write(buffer: NiolBuffer, completionHandler: () => Unit): Unit

  /**
   * Handles the packet's data.
   *
   * @param buffer the buffer view containing all the packet's data.
   */
  protected def handleData(buffer: NiolBuffer): Unit

  /**
   * Reads more data from the SocketChannel.
   */
  protected[network] def readMore(): Unit

  /**
   * Writes more pending data, if any, to the SocketChannel.
   *
   * @return true if all the pending data has been written, false otherwise
   */
  protected[network] def writeMore(): Boolean
}

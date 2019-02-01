package com.electronwill.niol.network.tcp

import java.net.InetSocketAddress
import java.nio.channels.{SelectionKey, Selector, ServerSocketChannel}

import scala.collection.mutable

/**
 * A ScalableSelector uses one NIO Selector to handle many TCP connections on several ports
 * with only one thread.
 *
 * ==Port listening==
 * To start listening for connections on a port, call the [[ScalableSelector.listen]] method.
 *
 * One (and only one) [[TcpListener]] is assigned for each port.
 * When a new client connects to the port, the listener's [[TcpListener.onAccept]] method is called.
 * It creates an instance of [[ClientAttach]] for the new client. The [[ClientAttach]] will handle
 * the data received from the client and sent to the client.
 *
 * ===Buffer providers and sizes===
 * If there is no data transformation in the [[ClientAttach]], the incoming data will be read
 * in a (generally) low-level off-heap buffer provided by the readBufferProvider. The minimum
 * size of the buffer will be packetBufferBaseSize.
 *
 * If there is a data transformation in the [[ClientAttach]], the incoming data will still be
 * read in a buffer provided by the readBufferProvider, but with a fixed size
 * equal to preTransformReadSize. The read data is then transformed by the transformation
 * function. Once the transformation is done, the packets are reconstructed in an other
 * buffer of minimum size packetBufferBaseSize and provided by postTransformBufferProvider.
 *
 * ===The packet buffer's minimum size===
 * The incoming packets arrive in several parts. One part can contain several packets, and
 * one packet can be split into different parts. Therefore they need to be reconstructed in
 * a packet buffer. To avoid the allocation of a new buffer each time some data is read, a
 * minimum "base" buffer is kept during the while connection. When the incoming packet is
 * larger than the base buffer, an additional buffer is allocated, providing the missing
 * capacity. Once the big packet is handled, the additional buffer is discarded.
 *
 * @param startHandler the function to call when the Selector's thread starts
 * @param stopHandler  the function to call when the Selector's thread stops
 * @param errorHandler handles errors, returns false to stop the Selector's execution
 * @author TheElectronWill
 */
final class ScalableSelector(
    private[this] val startHandler: () => Unit,
    private[this] val stopHandler: () => Unit,
    private[this] val errorHandler: Exception => Boolean)
  extends Runnable {

  private[this] val selector = Selector.open()
  private[this] val serverChannelsInfos = new mutable.LongMap[ServerChannelInfos[_]]
  @volatile private[this] var running = false

  /**
   * Starts a TCP [[ServerSocketChannel]] and registers it to the selector. If there already is a
   * ServerSocketChannel registered with the specified port, this methods returns `false` without
   * starting a new server.
   *
   * @param port     the server's port
   * @param settings the settings to apply to the channel's clients
   * @param listener the listener that will be called when some events (defined in the listener)
   *                 related to the ServerSocketChannel occur.
   * @return true if the server has been started, false if there already is a ServerSocketChannel bound to the
   *         specified port and registered to this selector.
   */
  def listen[A <: ClientAttach[A]](port: Int, settings: BufferSettings, listener: TcpListener[A]): Boolean = {
    if (serverChannelsInfos.contains(port)) {
      false
    } else {
      val ssc = ServerSocketChannel.open()
      ssc.configureBlocking(false)
      ssc.bind(new InetSocketAddress(port))
      serverChannelsInfos(port) = new ServerChannelInfos(ssc, selector, settings, listener)
      true
    }
  }

  /**
   * Stops a [[ServerSocketChannel]] that has been registered with [[listen]].
   *
   * @param port the server's port
   */
  def unlisten(port: Int): Unit = {
    serverChannelsInfos.remove(port)
                       .foreach(channelInfo => {
                         channelInfo.selectionKey.cancel()
                         channelInfo.serverChannel.close()
                       })
  }

  /**
   * Executes the selector loop.
   */
  override def run(): Unit = {
    if (running) {
      throw new IllegalStateException("This selector is already running! Don't call run() by hand.")
    }
    running = true
    startHandler()
    while (running) {
      try {
        selector.select() // Blocking selection
        if (running) { // Don't process the keys if the server has been stopped
          val iter = selector.selectedKeys().iterator()
          while (iter.hasNext) {
            val key = iter.next()
            val ops = key.readyOps()
            iter.remove()

            if ((ops & SelectionKey.OP_ACCEPT) != 0) { // New client -> accept
              accept(key)
            } else {
              // Don't try to read/write from/to a new client, since they
              // haven't been registered for OP_READ nor OP_WRITE operations yet.
              if (key.isValid) {
                if ((ops & SelectionKey.OP_READ) != 0) { // Data available -> read
                  val endOfStream = read(key)
                  if (endOfStream) {
                    cancel(key)
                  }
                }
                if ((ops & SelectionKey.OP_WRITE) != 0) { // Data pending -> write
                  write(key)
                }
              } else { // Invalid key -> cancel
                cancel(key)
              }
            }
          }
        }
      } catch {
        case e: Exception =>
          val continue = errorHandler(e)
          if (!continue) {
            stop()
          }
      }
    }
    selector.close()
    stopHandler()
  }

  /**
   * Checks if the selector is running.
   *
   * @return true if this selector is running
   */
  def isRunning: Boolean = running

  /**
   * Starts the ScalableSelector in a new thread. This method may be called at most once,
   * any further invocation will throw [[IllegalStateException]].
   *
   * The state of the selector can be checked with [[isRunning]].
   *
   * @param threadName the thread's name
   * @return the newly created Thread
   */
  def start(threadName: String): Thread = {
    if (running) {
      throw new IllegalStateException("This selector is already running!")
    }
    val t = new Thread(this, threadName)
    t.start()
    t
  }

  /**
   * Stops the Selector's thread and all registered ServerSocketChannels.
   */
  def stop(): Unit = {
    running = false
    try {
      for (infos <- serverChannelsInfos.values) {
        infos.selectionKey.cancel()
        infos.serverChannel.close()
      }
    } catch {
      case e: Exception => errorHandler(e)
    }
    selector.wakeup()
  }

  /**
   * Accepts a new client: make its SocketChannel non-blocking, call [[TcpListener.onAccept]]
   * and register the channel with [[SelectionKey.OP_READ]].
   *
   * @param key the SelectionKey corresponding to the new client
   * @tparam A the client's attach type
   * @return the client's SelectionKey
   */
  private def accept[A <: ClientAttach[A]](key: SelectionKey): Unit = {
    val sci = key.attachment().asInstanceOf[ServerChannelInfos[A]]
    val clientChannel = key.channel().asInstanceOf[ServerSocketChannel].accept()

    clientChannel.configureBlocking(false)
    val selectionKey = clientChannel.register(selector, SelectionKey.OP_READ)
    val clientAttach = sci.listener.onAccept(sci, clientChannel, selectionKey)
    selectionKey.attach(clientAttach)
  }

  /**
   * Reads more data from the client channel.
   *
   * @return true iff the end of the stream has been reached, false otherwise.
   */
  private def read(key: SelectionKey): Boolean = {
    val attach = key.attachment().asInstanceOf[ClientAttach[_]]
    attach.readMore()
    attach.streamEnded
  }

  /** Write more data to the client channel */
  private def write(key: SelectionKey): Boolean = {
    val attach = key.attachment().asInstanceOf[ClientAttach[_]]
    attach.writeMore()
  }

  /** Cancels a key and call `onDisconnect` */
  private def cancel[A <: ClientAttach[A]](key: SelectionKey): Unit = {
    key.cancel()
    val attach = key.attachment().asInstanceOf[A]
    attach.listener.onDisconnect(attach)
  }
}

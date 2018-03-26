package com.electronwill.niol.network.tcp

import java.net.InetSocketAddress
import java.nio.channels.{SelectionKey, Selector, ServerSocketChannel, SocketChannel}

import com.electronwill.niol.buffer.provider.BufferProvider

import scala.collection.mutable

/**
 * Base class for constructing TCP servers. Only one thread is used to manage all the clients.
 *
 * @author TheElectronWill
 */
final class ScalableSelector(private[this] val errorHandler: Exception => Unit,
							 private[this] val startHandler: () => Unit,
							 private[this] val stopHandler: () => Unit) extends Runnable {

	private[this] val selector = Selector.open()
	private[this] val serverChannelsInfos = new mutable.LongMap[ServerChannelInfos[_]]
	@volatile private[this] var _run = false

	/**
	 * Starts a TCP [[ServerSocketChannel]] and registers it to the selector.
	 *
	 * @param port the port to start the server on
	 * @param l    the listener that will be called when some events (defined in the listener) related to the
	 *             ServerSocketChannel occur.
	 * @return true if the server has been started, false if there already is a ServerSocketChannel bound to the
	 *         specified port and registered to this selector.
	 */
	def listen(port: Int, readBufferSize: Int, packetBufferBaseSize: Int,
			   readBufferProvider: BufferProvider, packetBufferProvider: BufferProvider,
			   l: TcpListener[_]): Boolean = {
		if (serverChannelsInfos.contains(port)) {
			false
		} else {
			val serverChan = ServerSocketChannel.open()
			serverChan.configureBlocking(false)
			serverChan.bind(new InetSocketAddress(port))
			serverChannelsInfos(port) = new ServerChannelInfos(selector, l, serverChan,
				readBufferSize, packetBufferBaseSize, readBufferProvider, packetBufferProvider)
			true
		}
	}

	def listen(port: Int, bufferBaseSize: Int, bufferProvider: BufferProvider, l: TcpListener[_]): Boolean = {
		listen(port, bufferBaseSize, bufferBaseSize, bufferProvider, bufferProvider, l)
	}

	/**
	 * Stops the [[ServerSocketChannel]] that has been registered with [[listen()]] for the specified port, and
	 * unregisters it from the selector.
	 *
	 * @param port the server's port
	 */
	def unlisten(port: Int): Unit = {
		serverChannelsInfos.remove(port).foreach(channelInfo => {
			channelInfo.skey.cancel()
			channelInfo.ssc.close()
		})
	}

	/**
	 * Executes the selector loop.
	 */
	override def run(): Unit = {
		startHandler()
		while (_run) {
			try {
				selector.select() // Blocking selection
				if (_run) { // Don't process the keys if the server has been stopped
					val iter = selector.selectedKeys().iterator()
					while (iter.hasNext) {
						val key = iter.next()
						val ops = key.readyOps()
						iter.remove()

						if ((ops & SelectionKey.OP_ACCEPT) != 0) { // New client -> accept
							val serverChan = key.channel().asInstanceOf[ServerSocketChannel]
							val infos = key.attachment().asInstanceOf[ServerChannelInfos[_]]
							val clientChan = serverChan.accept()
							accept(clientChan, infos)
							// Don't try to read/write from/to a new client, since they
							// haven't been registered for OP_READ nor OP_WRITE operations yet.
						} else {
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
				case e: Exception => errorHandler(e)
			}
		}
		selector.close()
		stopHandler()
	}

	/**
	 * Starts the ScalableSelector in a new thread. This method may be called at most once, any further invocation
	 * will throw [[IllegalStateException]]. The state of the selector can be checked with [[isRunning]].
	 *
	 * @param threadName the thread's name
	 * @return the newly created Thread
	 */
	def start(threadName: String): Thread = {
		if (_run) {
			throw new IllegalStateException("This selector is already running!")
		}
		_run = true
		val t = new Thread(this, threadName)
		t.start()
		t
	}

	/**
	 * Stops the Selector's thread and all registered ServerSocketChannels.
	 */
	def stop(): Unit = {
		_run = false
		try {
			for (infos <- serverChannelsInfos.values) {
				infos.skey.cancel()
				infos.ssc.close()
			}
		} catch {
			case e: Exception => errorHandler(e)
		}
		selector.wakeup()
	}

	/** @return true iff this selector is running */
	def isRunning: Boolean = _run

	/** Accepts the client channel: make it non-blocking, call `onAccept` and register OP_READ */
	private def accept[A](clientChannel: SocketChannel, serverChannel: ServerChannelInfos[A]): Unit = {
		clientChannel.configureBlocking(false)
		val clientAttach = serverChannel.l.onAccept(clientChannel, serverChannel)
		clientChannel.register(selector, SelectionKey.OP_READ, clientAttach)
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
	private def cancel[A](key: SelectionKey): Unit = {
		key.cancel()
		val attach = key.attachment().asInstanceOf[ClientAttach[A]]
		attach.server.l.onDisconnect(attach)
	}
}
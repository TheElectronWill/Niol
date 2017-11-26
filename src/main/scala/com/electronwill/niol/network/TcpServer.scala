package com.electronwill.niol.network

import java.net.InetSocketAddress
import java.nio.channels.{SelectionKey, Selector, ServerSocketChannel, SocketChannel}

import com.electronwill.niol.buffer.provider.BufferProvider

/**
 * Base class for constructing TCP servers. Only one thread is used to manage all the clients.
 *
 * @param port           the server's port
 * @param baseBufferSize the minimum size of the buffer to keep for each client.
 * @param bufferProvider the BufferProvider used to get new buffers for the new clients or
 *                       for the messages that are bigger than `baseBufferSize`.
 * @author TheElectronWill
 */
abstract class TcpServer[A](val port: Int, private[network] val baseBufferSize: Int,
							private[network] val bufferProvider: BufferProvider) extends Runnable {

	private[network] val selector = Selector.open()
	private[this] val serverChannel = ServerSocketChannel.open()
	@volatile private[this] var _run = false

	serverChannel.configureBlocking(false)
	serverChannel.bind(new InetSocketAddress(port))
	serverChannel.register(selector, SelectionKey.OP_ACCEPT)

	/**
	 * Executes the server loop.
	 */
	override final def run(): Unit = {
		onStarted()
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
							val channel = serverChannel.accept()
							accept(channel)
							// Don't try to read/write from/to a new client, since they
							// haven't been registered yet for OP_READ nor OP_WRITE operations.
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
				case e: Exception => onError(e)
			}
		}
		serverChannel.close()
		selector.close()
		onStopped()
	}

	/**
	 * Starts the server in a new thread.
	 *
	 * @param threadName the thread's name
	 * @return the newly created Thread
	 */
	final def start(threadName: String): Thread = {
		if (_run) {
			throw new IllegalStateException("The server is already running!")
		}
		_run = true
		onStart()
		val t = new Thread(this, threadName)
		t.start()
		t
	}

	/**
	 * Stops the server.
	 */
	final def stop(): Unit = {
		onStop()
		_run = false
		selector.wakeup()
	}

	/**
	 * Called when a new TCP client connects to the server. Returns an object ClientAttach that
	 * is uniquely associated to this precise client.
	 *
	 * @param clientChannel the client's SocketChannel
	 * @return a ClientAttach object associated to this client
	 */
	protected def onAccept(clientChannel: SocketChannel): ClientAttach[A]

	/**
	 * Called when a client disconnects from the server.
	 *
	 * @param clientAttach the ClientAttach object associated to this client
	 */
	protected def onDisconnect(clientAttach: ClientAttach[A]): Unit

	/**
	 * Called when an error occurs during the server's execution.
	 *
	 * @param e the error
	 */
	protected def onError(e: Exception): Unit

	/**
	 * Called when the [[start]] method is called, before [[onStarted]].
	 */
	protected def onStart(): Unit = {}

	/**
	 * Called when the server's execution begins in its thread, after [[onStart]].
	 */
	protected def onStarted(): Unit = {}

	/**
	 * Called when the [[stop]] method is called, before [[onStopped]].
	 */
	protected def onStop(): Unit = {}

	/**
	 * Called when the server's execution finally stops, after [[onStop]].
	 */
	protected def onStopped(): Unit = {}

	/** Accepts the client channel: make it non-blocking, call `onAccept` and register OP_READ */
	private def accept(clientChannel: SocketChannel): Unit = {
		clientChannel.configureBlocking(false)
		val clientAttach = onAccept(clientChannel)
		clientChannel.register(selector, SelectionKey.OP_READ, clientAttach)
	}

	/**
	 * Reads more data from the client channel.
	 *
	 * @return true iff the end of the stream has been reached, false otherwise.
	 */
	private def read(key: SelectionKey): Boolean = {
		val attach = key.attachment().asInstanceOf[ClientAttach[A]]
		attach.readMore()
		attach.streamEnded
	}

	/** Write more data to the client channel */
	private def write(key: SelectionKey): Boolean = {
		val attach = key.attachment().asInstanceOf[ClientAttach[A]]
		attach.writeMore()
	}

	/** Cancels a key and call `onDisconnect` */
	private def cancel(key: SelectionKey): Unit = {
		key.cancel()
		val attach = key.attachment().asInstanceOf[ClientAttach[A]]
		onDisconnect(attach)
	}
}
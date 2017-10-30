package com.electronwill.niol.network

import java.net.InetSocketAddress
import java.nio.channels.{SelectionKey, Selector, ServerSocketChannel, SocketChannel}

import com.electronwill.niol.buffer.provider.BufferProvider

/**
 * Base class for constructing TCP servers.
 *
 * @author TheElectronWill
 */
abstract class TcpServer[A](val port: Int, private[network] val baseBufferSize: Int,
							private[network] val bufferProvider: BufferProvider) extends Runnable {

	private[this] val selector = Selector.open()
	private[this] val serverChannel = ServerSocketChannel.open()
	@volatile private[this] var _run = true

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
						} else { // Don't try to read/write from/to a new client, since they
							// haven't been registered yet for OP_READ nor OP_WRITE operations.
							var keep = true
							if ((ops & SelectionKey.OP_READ) != 0) { // Data available -> read
								keep &= read(key)
							}
							if ((ops & SelectionKey.OP_WRITE) != 0) { // Data pending -> write
								keep &= write(key)
							}
							if (!keep || !key.isValid) { // Invalid key -> cancel
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
	 * Called when the [[start]] method is called.
	 */
	protected def onStart(): Unit = {}

	/**
	 * Called when the server's execution begins in its thread.
	 */
	protected def onStarted(): Unit = {}

	/**
	 * Called when the [[stop]] method is called.
	 */
	protected def onStop(): Unit = {}

	/**
	 * Called when the server's execution finally stops.
	 */
	protected def onStopped(): Unit = {}

	private def accept(clientChannel: SocketChannel): Unit = {
		clientChannel.configureBlocking(false)
		val clientAttach = onAccept(clientChannel)
		clientChannel.register(selector, SelectionKey.OP_READ, clientAttach)
	}

	private def read(key: SelectionKey): Boolean = {
		val attach = key.attachment().asInstanceOf[ClientAttach[A]]
		attach.readMore()
		attach.streamEnded
	}

	private def write(key: SelectionKey): Boolean = {
		val attach = key.attachment().asInstanceOf[ClientAttach[A]]
		attach.writeMore()
	}

	private def cancel(key: SelectionKey): Unit = {
		key.cancel()
		val attach = key.attachment().asInstanceOf[ClientAttach[A]]
		onDisconnect(attach)
	}
}
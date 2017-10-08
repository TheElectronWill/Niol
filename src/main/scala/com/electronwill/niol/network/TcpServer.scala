package com.electronwill.niol.network

import java.net.InetSocketAddress
import java.nio.channels.{SelectionKey, Selector, ServerSocketChannel, SocketChannel}

import com.electronwill.niol.buffer.provider.BufferProvider

/**
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

	override final def run(): Unit = {
		while (_run) {
			try {
				selector.select()
				if (_run) {
					val iter = selector.selectedKeys().iterator()
					while (iter.hasNext) {
						val key = iter.next()
						val ops = key.readyOps()
						iter.remove()

						if ((ops & SelectionKey.OP_ACCEPT) != 0) {
							val channel = serverChannel.accept()
							accept(channel)
						} else {
							var keep = true
							if ((ops & SelectionKey.OP_READ) != 0) {
								keep &= read(key)
							}
							if ((ops & SelectionKey.OP_WRITE) != 0) {
								keep &= write(key)
							}
							if (!keep || !key.isValid) {
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
	}

	def start(threadName: String): Thread = {
		val t = new Thread(this, threadName)
		t.start()
		t
	}

	def stop(): Unit = {
		_run = false
		selector.wakeup()
	}

	protected def onAccept(clientChannel: SocketChannel): ClientAttach[A]

	protected def onDisconnect(clientAttach: ClientAttach[A]): Unit

	protected def onError(e: Exception): Unit

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
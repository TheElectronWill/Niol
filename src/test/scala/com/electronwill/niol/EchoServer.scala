package com.electronwill.niol

import java.io.{DataInputStream, DataOutputStream}
import java.net.Socket
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

import com.electronwill.niol.buffer.provider.{DirectNioAllocator, StageBufferPoolBuilder}
import com.electronwill.niol.buffer.{NiolBuffer, StraightBuffer}
import com.electronwill.niol.network.{ClientAttach, TcpServer}

/**
 * @author TheElectronWill
 */
object EchoServer {
	val possibleMessages = Seq("Hello,world", "Hello,test", "this is a big message " +
		(1 to 1000).toBuffer)
	val counter = new AtomicInteger()
	def main(args: Array[String]): Unit = {
		val port = 3000
		val poolBuilder = new StageBufferPoolBuilder
		poolBuilder += (4000, 10, i => DirectNioAllocator.getBuffer(i))
		val bufferPool = poolBuilder.build()
		val server = new TcpServer[Int](port, 150, bufferPool) {
			override def onAccept(clientChannel: SocketChannel): ClientAttach[Int] = {
				println(s"Accepted client ${clientChannel.getLocalAddress}")
				val attach = new Client(clientChannel, this)
				println(s"Assigned client to id ${attach.infos}")
				attach
			}
			override def onDisconnect(clientAttach: ClientAttach[Int]): Unit = {
				println(s"Client ${clientAttach.infos} disconnected")
			}
			override def onError(e: Exception): Unit = {
				println(s"Error (see stack trace): $e")
				e.printStackTrace()
			}
			override protected def onStarted(): Unit = {
				println("Server started")
			}
			override protected def onStopped(): Unit = {
				println("Server stopped")
			}
		}
		val clientRun = new Runnable {
			override def run(): Unit = {
				val socket = new Socket("localhost", port)
				val out = new DataOutputStream(socket.getOutputStream)
				val in = new DataInputStream(socket.getInputStream)
				out.writeShort(10)
				out.write("Hello,test".getBytes(StandardCharsets.UTF_8))
				println("[C] written")
				Thread.sleep(1000)
				var i = 0
				while (true) {
					val header = in.readShort()
					println(s"[C] Received header: $header")
					val array = new Array[Byte](header)
					val read = in.read(array)
					println(s"[C] Read $read")
					println(s"[C] Received message: ${new String(array, StandardCharsets.UTF_8)}")
					//Thread.sleep(2000)
					i += 1
					if (i % 20 == 0) {
						println("======================================================")
						val bytes = possibleMessages(2).getBytes(StandardCharsets.UTF_8)
						out.writeShort(bytes.length)
						out.write(bytes)
					} else {
						out.writeShort(11)
						out.write("Hello,world".getBytes(StandardCharsets.UTF_8))
					}
				}
			}
		}
		server.start("Echo Server")
		Thread.sleep(1000)
		for (i <- 1 to 3) {
			new Thread(clientRun).start()
		}
	}
}
class Client(chan: SocketChannel, server: TcpServer[Int])
	extends ClientAttach[Int](Client.clientId.getAndIncrement(), chan, server) {

	override def readHeader(buffer: NiolBuffer): Int = {
		println(s"[S] available: ${buffer.readAvail}, write: ${buffer.writeAvail}")
		if (buffer.readAvail < 2) {
			-1
		} else {
			val size = buffer.getShort()
			println(s"[S] Message size: $size, remaining: ${buffer.readAvail}")
			size
		}
	}
	override def handleData(buffer: NiolBuffer): Unit = {
		val response = buffer.copyRead
		println(s"[S] available: ${buffer.readAvail}, response.available: ${response.readAvail}")

		val message = buffer.getString(buffer.readAvail, StandardCharsets.UTF_8)
		println(s"[S] Received: (${message.length}) $message")
		println(s"[S] *available: ${buffer.readAvail}, *response.available: ${response.readAvail}")
		assert(EchoServer.possibleMessages.contains(message))

		val sizeBuffer = new StraightBuffer(DirectNioAllocator.getBuffer(2))
		response.readAvail.toShort >>: sizeBuffer
		println(s"[S] sizeBuffer.readAvail: ${sizeBuffer.readAvail}")
		write(sizeBuffer)
		write(response)
		println("[S] written rep")
	}
}
object Client {
	private val clientId = new AtomicInteger(0)
}
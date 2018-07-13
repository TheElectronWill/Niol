package com.electronwill.niol

import java.io.{DataInputStream, DataOutputStream}
import java.net.Socket
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger

import com.electronwill.niol.buffer.provider.{DirectNioAllocator, HeapNioAllocator, StageBufferPoolBuilder}
import com.electronwill.niol.buffer.{NiolBuffer, StraightBuffer}
import com.electronwill.niol.network.tcp.{ClientAttach, ScalableSelector, ServerChannelInfos, TcpListener}

/**
 * @author TheElectronWill
 */
object EchoBench {
	val possibleMessages = Seq("Hello,world", "Hello,test", "this is a big message " +
		(1 to 1000).toBuffer)

	val port = 3001
	val nClients = 200
	val stopBarrier = new CyclicBarrier(2)

	def main(args: Array[String]): Unit = {
		val poolBuilder = new StageBufferPoolBuilder
		poolBuilder += (100, 3000, DirectNioAllocator.getBuffer)
		poolBuilder += (5000, 100, DirectNioAllocator.getBuffer)
		val bufferPool = poolBuilder.build()

		val errorHandler = (e: Exception) => {
			println(s"Error (see stack trace): $e")
			e.printStackTrace()
		}
		val startHandler = () => {
			println("Server started")
			ClientB.start = System.nanoTime()
		}
		val stopHandler = () => {
			println("Server stopped")
		}
		val selector = new ScalableSelector(errorHandler, startHandler, stopHandler)
		selector.listen(port, 3000, bufferPool, new TcpListener[Int] {
			override def onAccept(clientChan: SocketChannel, serverInfos: ServerChannelInfos[Int]): ClientAttach[Int] = {
				val attach = new ClientB(serverInfos, clientChan)
				attach
			}

			override def onDisconnect(clientAttach: ClientAttach[Int]): Unit = {
				println(s"Client ${clientAttach.infos} disconnected")
			}
		})
		val clientRunnable = new Runnable {
			override def run(): Unit = {
				val socket = {
					try {
						new Socket("localhost", port)
					} catch {
						case e: Exception =>
							Thread.sleep(100 + (Math.random() * 100).toInt)
							new Socket("localhost", port)
					}
				}
				val out = new DataOutputStream(socket.getOutputStream)
				val in = new DataInputStream(socket.getInputStream)
				out.writeShort(10)
				out.write("Hello,test".getBytes(StandardCharsets.UTF_8))
				var i = 0
				while (true) {
					val header = in.readShort()
					val array = new Array[Byte](header)
					var read = in.read(array)
					while (read != array.length) {
						val res = in.read(array, read, array.length - read)
						if (res == -1) read = array.length
						else read += res
					}
					val message = new String(array, StandardCharsets.UTF_8)
					if (!possibleMessages.contains(message)) {
						println(s"ERROR: UNKNOWN MESSAGE FROM SERVER at ${System.currentTimeMillis}")
						println(message)
					}
					i += 1
					if (i % 20 == 0) {
						//println("======================================================")
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
		selector.start("Echo Server")

		Thread.sleep(100)
		for (i <- 1 to Math.min(2000, nClients)) {
			new Thread(clientRunnable).start()
		}
		if (nClients > 2000) {
			for (i <- 1 to (nClients - 2000)) {
				new Thread(clientRunnable).start()
				Thread.sleep(2)
			}
		}
		stopBarrier.await()
		selector.stop()
		System.exit(0)
	}
}

class ClientB(serverInfos: ServerChannelInfos[Int], chan: SocketChannel)
	extends ClientAttach[Int](serverInfos, ClientB.getId(), chan) {

	override def readHeader(buffer: NiolBuffer): Int = {
		if (buffer.readAvail < 2) {
			-1
		} else {
			val size = buffer.getShort()
			size
		}
	}

	override def handleData(buffer: NiolBuffer): Unit = {
		val response = buffer.copyRead
		if (buffer.readAvail > 20 && buffer.readAvail != 4926) {
			println("BUGGY BUFFER SIZE")
			println(s"readAvail should be 4926 but it is ${buffer.readAvail}")
		}
		if (response.readAvail != buffer.readAvail) {
			println(s"BUGGY COPYREAD FOR CLIENT $infos")
			println(s"readAvail should be ${buffer.readAvail} but it is ${response.readAvail}")
		}
		val ra = buffer.readAvail
		val message = buffer.getString(ra, StandardCharsets.UTF_8)
		if (!EchoServer.possibleMessages.contains(message)) {
			println(s"ERROR: UNKNOWN MESSAGE FROM CLIENT $infos at ${System.currentTimeMillis}")
			println(message)
			EchoBench.stopBarrier.await()
		}
		ClientB.counter.getAndIncrement()
		if (ClientB.counter.get() == ClientB.MsgGoal) {
			val s = (System.nanoTime() - ClientB.start) / Math.pow(10, 9)
			println(s"${ClientB.MsgGoal} messages in $s seconds, with ${EchoBench.nClients} clients")
			EchoBench.stopBarrier.await()
		}

		val sizeBuffer = new StraightBuffer(HeapNioAllocator.getBuffer(2))
		response.readAvail.toShort >>: sizeBuffer
		write(sizeBuffer)
		write(response)
	}
}

object ClientB {
	private val clientId = new AtomicInteger(0)
	private val counter = new AtomicInteger(0)
	@volatile var start: Double = 0
	val MsgGoal = 100000

	def getId(): Int = {
		val id = clientId.getAndIncrement()
		println(s"Client id: $id")
		id
	}
}
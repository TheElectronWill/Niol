package com.electronwill.niol

import java.io.{DataInputStream, DataOutputStream}
import java.net.Socket
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

import com.electronwill.niol.buffer.provider.{DirectNioAllocator, StageBufferPoolBuilder}
import com.electronwill.niol.buffer.{NiolBuffer, StraightBuffer}
import com.electronwill.niol.network.tcp._

/**
 * @author TheElectronWill
 */
object EchoServer {
  val possibleMessages =
    Seq("Hello,world", "Hello,test", "this is a big message " + (1 to 1000).toBuffer)

  val counter = new AtomicInteger()

  def main(args: Array[String]): Unit = {
    // Setting: server's port
    val port = 3000

    // Create a buffer pool
    val poolBuilder = new StageBufferPoolBuilder
    poolBuilder.addStage(4000, 10, DirectNioAllocator.getBuffer)
    val bufferPool = poolBuilder.build()

    // Create a ScalableSelector
    val startHandler = () => println("Server started")
    val stopHandler = () => println("Server stopped")
    val errorHandler = (e: Exception) => {
      println(s"Error (see stack trace): $e")
      e.printStackTrace()
      Thread.sleep(1000)
    }
    val selector = new ScalableSelector(startHandler, stopHandler, errorHandler)

    // Create a TcpListener and starts a TCP Server on the port
    val listener = new TcpListener[EchoAttach] {
      override def onAccept(clientChannel: SocketChannel, s: ServerChannelInfos[EchoAttach]) = {
        println(s"Accepted client ${clientChannel.getLocalAddress}")
        val attach = new EchoAttach(s, clientChannel)
        println(s"Assigned client to id ${attach.clientId}")
        attach
      }
      override def onDisconnect(clientAttach: EchoAttach) = {
        println(s"Client ${clientAttach.clientId} disconnected")
      }
    }
    selector.listen(port, BufferSettings(150, bufferPool), listener)

    // Start the server
    selector.start("Echo Server")

    // -----------------------------------------------------------
    // Start the clients
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
    Thread.sleep(1000)
    for (i <- 1 to 3) {
      new Thread(clientRun).start()
    }
  }
}
class EchoAttach(serverInfos: ServerChannelInfos[EchoAttach], clientChannel: SocketChannel)
  extends HAttach[EchoAttach](serverInfos, clientChannel) {

  val clientId = EchoAttach.lastId.getAndIncrement()

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
    sizeBuffer.putShort(response.readAvail)
    println(s"[S] sizeBuffer.readAvail: ${sizeBuffer.readAvail}")
    write(sizeBuffer)
    write(response)
    println("[S] written rep")
  }
}
object EchoAttach {
  private val lastId = new AtomicInteger(0)
}

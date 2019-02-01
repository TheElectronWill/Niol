package com.electronwill.niol

import java.io.{DataInputStream, DataOutputStream}
import java.net.Socket
import java.nio.channels.{SelectionKey, SocketChannel}
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

import com.electronwill.niol.buffer.storage.{BytesStorage, StagedPools}
import com.electronwill.niol.buffer.{CircularBuffer, NiolBuffer}
import com.electronwill.niol.network.tcp.{ServerChannelInfos ⇒ SCI, _}

/**
 * @author TheElectronWill
 */
object EchoServer {
  val possibleMessages =
    Seq("Hello,world", "Hello,test", "this is a big message " + "$" * 16000)

  val counter = new AtomicInteger()

  def main(args: Array[String]): Unit = {
    // Setting: server's port
    val port = 3000

    // Create a buffer pool

    //val pool = poolBuilder.build()
    val pool = StagedPools().directStage(4000, 10, isMoreAllocationAllowed=true)
                            .defaultAllocateHeap()
                            .build()

    // Create a ScalableSelector
    val startHandler = () => println("Server started")
    val stopHandler = () => println("Server stopped")
    val errorHandler = (e: Exception) => {
      println(s"Error (see stack trace): $e")
      e.printStackTrace()
      Thread.sleep(1000)
      true
    }
    val selector = new ScalableSelector(startHandler, stopHandler, errorHandler)

    // Create a TcpListener and starts a TCP Server on the port
    val listener = new TcpListener[EchoAttach] {
      override def onAccept(sci: SCI[EchoAttach], c: SocketChannel, k: SelectionKey): EchoAttach = {
        println(s"Accepted client ${c.getLocalAddress}")
        val attach = new EchoAttach(sci, c, k)
        println(s"Assigned client to id ${attach.clientId}")
        attach
      }
      override def onDisconnect(clientAttach: EchoAttach) = {
        println(s"Client ${clientAttach.clientId} disconnected")
      }
    }
    selector.listen(port, BufferSettings(150, pool), listener)

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
class EchoAttach(sci: SCI[EchoAttach], chan: SocketChannel, key: SelectionKey)
  extends HAttach[EchoAttach](sci, chan, key) {

  val clientId = EchoAttach.lastId.getAndIncrement()

  override protected def writeHeader(data: NiolBuffer, output: NiolBuffer): Unit = ???

  override def readHeader(buffer: NiolBuffer): Int = {
    println(s"[S] available: ${buffer.readableBytes}, write: ${buffer.writableBytes}")
    if (buffer.readableBytes < 2) {
      -1
    } else {
      val size = buffer.readShort()
      println(s"[S] Message size: $size, remaining: ${buffer.readableBytes}")
      size
    }
  }
  override def handleData(buffer: NiolBuffer): Unit = {
    val response = buffer.copy(BytesStorage.allocateHeap)
    println(s"[S] available: ${buffer.readableBytes}, response.available: ${response.readableBytes}")

    val message = buffer.readString(buffer.readableBytes, StandardCharsets.UTF_8)
    println(s"[S] Received: (${message.length}) $message")
    println(s"[S] *available: ${buffer.readableBytes}, *response.available: ${response.readableBytes}")
    assert(EchoServer.possibleMessages.contains(message))

    val sizeBuffer = new CircularBuffer(BytesStorage.allocateDirect(2))
    sizeBuffer.writeShort(response.readableBytes)
    println(s"[S] sizeBuffer.readableBytes: ${sizeBuffer.readableBytes}")
    write(sizeBuffer)
    write(response)
    println("[S] written rep")
  }
}
object EchoAttach {
  private val lastId = new AtomicInteger(0)
}

package com.electronwill.niol

import java.io.{DataInputStream, DataOutputStream}
import java.net.Socket
import java.nio.channels.{SelectionKey, SocketChannel}
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

import com.electronwill.niol.buffer.storage.{BytesStorage, StagedPools}
import com.electronwill.niol.buffer.{CircularBuffer, NiolBuffer}
import com.electronwill.niol.network.tcp.{ServerChannelInfos => SCI, _}
import org.junit.jupiter.api.Assertions._

/**
 * @author TheElectronWill
 */
object EchoBenchmark {
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
        out.flush()
        var i = 0
        while (true) {
          val header = in.readShort()
          val array = new Array[Byte](header)
          val read = in.read(array)
          //val readTxt = new String(array, StandardCharsets.UTF_8)
          i += 1
          if (i % 20 == 0) {
            val bytes = possibleMessages(2).getBytes(StandardCharsets.UTF_8)
            out.writeShort(bytes.length)
            out.write(bytes)
          } else {
            out.writeShort(11)
            out.write("Hello,world".getBytes(StandardCharsets.UTF_8))
          }
          out.flush()
        }
      }
    }
    Thread.sleep(1000)
    val n = 1000
    for (i <- 1 to n) {
      new Thread(clientRun).start()
    }
  }

  class EchoAttach(sci: SCI[EchoAttach], chan: SocketChannel, key: SelectionKey)
      extends HAttach[EchoAttach](sci, chan, key) {

    val clientId = EchoAttach.lastId.getAndIncrement()
    var t0 = System.currentTimeMillis

    override protected def makeHeader(data: NiolBuffer) = {
      val buff = CircularBuffer(BytesStorage.allocateHeap(2))
      buff.writeShort(data.readableBytes)
      assertEquals(2, buff.readableBytes)
      buff
    }

    override def readHeader(buffer: NiolBuffer): Int = {
      if (buffer.readableBytes < 2) {
        -1
      } else {
        val size = buffer.readShort()
        size
      }
    }
    override def handleData(buffer: NiolBuffer): Unit = {
      val response = buffer.duplicate
      val readable = buffer.readableBytes
      val count = counter.addAndGet(readable)
      if (clientId == 1) {
        val t1 = System.currentTimeMillis
        val dt = (t1 - t0) / 1000.0
        if (dt > 2.0) {
          t0 = t1
          counter.addAndGet(-count)
          val rate = count/dt
          println(f"$rate%.2f bytes/second, ie ${rate/1000.0}%.4f kB/s or ${rate/1000000.0}%.4f MB/s")
        }
      }

      val message = buffer.readString(readable, StandardCharsets.UTF_8)
      //assert(EchoServer.possibleMessages.contains(message))

      write(response)
    }

    override def toString: String = s"EchoAttach #$clientId"
  }
  object EchoAttach {
    private val lastId = new AtomicInteger()
    val counter = new AtomicLong()
  }
}

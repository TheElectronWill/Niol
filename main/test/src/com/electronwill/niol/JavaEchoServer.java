package com.electronwill.niol;

import com.electronwill.niol.buffer.CircularBuffer;
import com.electronwill.niol.buffer.NiolBuffer;
import com.electronwill.niol.buffer.storage.BytesStorage;
import com.electronwill.niol.buffer.storage.StagedPools;
import com.electronwill.niol.network.tcp.*;
import scala.Function1;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author TheElectronWill
 */
class JavaEchoServer {
  private static final String repeated = new String(new char[16000]).replace("\0", "$");
  private static final List<String> possibleMessages = Arrays.asList("Hello,world", "Hello,test", "this is a big message " + repeated);

  public static void main(String[] args) {
    // Setting: server's port
    int port = 3000;

    // Create a buffer pool

    //val pool = poolBuilder.build()
    StagedPools pool = new StagedPools.Builder()
        .directStage(4000, 10, true)
        .defaultAllocateHeap()
        .build();

    // Create a ScalableSelector
    Runnable startHandler = () -> System.out.println("Server started");
    Runnable stopHandler = () -> System.out.println("Server stopped");
    Function1<Exception, Object> errorHandler = (Exception e) -> {
      System.out.println("Error (see stack trace): " + e);
      e.printStackTrace();
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e1) {
        e1.printStackTrace();
      }
      return true;
    };
    ScalableSelector selector = new ScalableSelector(startHandler, stopHandler, errorHandler);

    // Create a TcpListener and starts a TCP Server on the port
    TcpListener<EchoAttach> listener = new TcpListener<EchoAttach>() {
      @Override
      public EchoAttach onAccept(ServerChannelInfos<EchoAttach> sci, SocketChannel c, SelectionKey k) {
        try {
          System.out.println("Accepted client " + c.getLocalAddress());
          EchoAttach attach = new EchoAttach(sci, c, k);
          System.out.println("Assigned client to id " + attach.clientId);
          return attach;
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      @Override
      public void onDisconnect(EchoAttach clientAttach) {
        System.out.println("Client " + clientAttach.clientId + " disconnected");
      }
    };
    selector.listen(port, new BufferSettings(150, pool), listener);

    // Start the server
    selector.start("Echo Server");

    // -----------------------------------------------------------
    // Start the clients
    Runnable clientRun = () -> {
      try {
        Socket socket = new Socket("localhost", port);
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        DataInputStream in = new DataInputStream(socket.getInputStream());
        out.writeShort(10);
        out.write("Hello,test".getBytes(StandardCharsets.UTF_8));
        out.flush();
        System.out.println("[C] written FIRST message");
        Thread.sleep(1000);
        int i = 0;
        while (true) {
          int header = in.readShort();
          System.out.println("[C] Received header: " + header);
          byte[] array = new byte[header];
          int read = in.read(array);
          String readTxt = new String(array, StandardCharsets.UTF_8);
          System.out.println("[C] Read " + read);
          System.out.println("[C] Received message: " + readTxt.substring(0, Math.min(readTxt.length(), 150)));
          assertTrue(possibleMessages.contains(readTxt));
          //Thread.sleep(2000)
          i += 1;
          if (i % 20 == 0) {
            System.out.println("======================================================");
            byte[] bytes = possibleMessages.get(2).getBytes(StandardCharsets.UTF_8);
            out.writeShort(bytes.length);
            out.write(bytes);
            System.out.println("[C] sent BIG message");
          } else {
            out.writeShort(11);
            out.write("Hello,world".getBytes(StandardCharsets.UTF_8));
            System.out.println("[C] sent SMALL message");
          }
          out.flush();
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    };

    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    int n = 500;
    for (int i = 0; i < n; i++) {
      new Thread(clientRun).start();
    }
  }
  static class EchoAttach extends HAttach<EchoAttach> {

    public EchoAttach(ServerChannelInfos<EchoAttach> sci, SocketChannel chan, SelectionKey key) {
      super(sci, chan, key, null, null);
    }

    final int clientId = lastId.getAndIncrement();

    @Override
    public NiolBuffer makeHeader(NiolBuffer data) {
      NiolBuffer buff = CircularBuffer.apply(BytesStorage.allocateHeap(2));
      buff.writeShort(data.readableBytes());
      assertEquals(2, buff.readableBytes());
      return buff;
    }

    @Override
    public int readHeader(NiolBuffer buffer) {
      System.out.printf("[S] available: %d, write: %d\n", buffer.readableBytes(), buffer.writableBytes());
    if (buffer.readableBytes() < 2) {
      return -1;
    } else {
      int size = buffer.readShort();
      System.out.printf("[S] Message size: %d, remaining: %d\n", size, buffer.readableBytes());
      return size;
    }
  }
    @Override
    public void handleData(NiolBuffer buffer) {
      NiolBuffer response = buffer.duplicate();
      System.out.printf("[S] available: %d, response.available: %d", buffer.readableBytes(), response.readableBytes());

      String message = buffer.readString(buffer.readableBytes(), StandardCharsets.UTF_8);
      System.out.printf("[S] Received: (%d) %s", message.length(), message.substring(0, Math.min(message.length(), 150)));
      System.out.printf("[S] *available: %d, *response.available: %d", buffer.readableBytes(), response.readableBytes());
    assert(JavaEchoServer.possibleMessages.contains(message));

    //val sizeBuffer = CircularBuffer(BytesStorage.allocateDirect(2))
    //sizeBuffer.writeShort(response.readableBytes)
      System.out.printf("[S] response size: %d", response.readableBytes());
    //write(sizeBuffer)
    write(response);
    System.out.println("[S] written rep (with header)");
  }

    @Override
    public String toString() {
      return "EchoAttach #" + clientId;
    }
  }

  private static final AtomicInteger lastId = new AtomicInteger(0);
}


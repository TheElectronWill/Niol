![logo: edelweiss roug](logo.png)

# Niol

Niol is a NIO Library written in Scala and designed to make server programming easier.

## Abstract Inputs/Outputs

The two traits NiolInput and NiolOutput represent something that can receive/give data. They provide convenient operations like `writeVarint` and `writeString`, and shortcuts like `>>:(Array[Byte])`.

NiolInput example:
```scala
val in: NiolInput = ... // some input
val n = in.getVarint()
val bytes = new Array[Byte](100)
bytes <<: in // equivalent to in.getBytes(bytes)
```
NiolOutput example:
```scala
val out: NiolOutput = ... // some output
true >>: out // equivalent to out.putBoolean(true)
("string", UTF_8) >>: out // equivalent to out.putString("string", UTF_8)
bytes >>: out // equivalent to out.putBytes(bytes)
```
Any input can be directly written to an output:
```scala
in >>: out // equivalent to out.putBytes(in)
```

## Channels

Niol uses the standard Java NIO channels `ScatteringByteChannel` and `GatheringByteChannel`. They can be used with any NiolInput/Output.
```scala
scatteringChannel >>: out
gatheringChannel <<: in
```

You can create `ChannelInputs` and `ChannelOutputs` from files or from any channel:
```scala
val fileOut = new ChannelOutput(nioFilePath)
val fileIn = new ChannelInput(nioFilePath)

val channelOut = new ChannelOutput(scatteringChannel)
val channelIn = new ChannelInput(gatheringChannel)
```

## Buffers

Buffers are the core of Niol. A buffer is a data container that inherits from both NiolOutput and NiolInput.
![buffer hierarchy](buffer_hierarchy.png)

Read and write operations are separated from each other, that is, two different position indexes are used.

### NiolBuffer vs RandomAccessBuffer

`NiolBuffer` is the superclass of all buffers. It exposes basic reading and writing methods, but hides the read and write positions. The benefit is that "special" buffers can be created, that doesn't have a linear storage, like the `CircularBufer`.

On the contrary, a `RandomAccessBuffer` is a simpler, linear data container, very similar to a java `ByteBuffer` but with two positions instead of one.

### NioBaseBuffer - Like ByteBuffer, but better

As the name suggests, the `NioBaseBuffer` is based on the Java NIO `ByteBuffer` and serves as a base for the other buffers. It is a simple wrapper that adds no special functionality.

The underlying `ByteBuffer` can be either direct or non-direct (see [the ByteBuffer documentation](https://docs.oracle.com/javase/8/docs/api/java/nio/ByteBuffer.html)).

### Buffer Providers

A `NiolBaseBuffer` cannot be created with a constructor. You have to use a `BufferProvider`, for instance the `DefaultOffHeapProvider` :
```scala
val buff: BaseBuffer = BufferProvider.DefaultOffHeapProvider.getBuffer(capacity)
```

Niol also provides a `StageBufferPool`, which is organized in capacity intervals.

```scala
val builder = new StageBufferPoolBuilder()
builder += (100, 250) // keep up to 250 buffers of capacity 100
builder += (1000, 10) // keep up to 10 buffer of capacity 1000
builder.defaultHandler(HeapNioAllocator.getBuffer) // above 1000, allocate on-demand on the heap
val pool = builder.build()
val smalBuffer = pool.getBuffer(75) // 75 <= 100 so this returns a buffer of capacity 100
val bigBuffer = pool.getBuffer(101) // returns a buffer of capacity 1000
val hugeBuffer = pool.getBuffer(2048) // returns a buffer of capacity 2048
```

### Memory Management

To make the provider system work and to release the direct buffers more efficiently, you have to call the `discard()` method when you don't need a buffer anymore.

A discarded buffer must **never** be used. Calling a method on a discarded buffer may or may not work, Niol provides no guarantee at all.

Some operations like `duplicate()` and `sub()` create buffers that are linked to the original buffer. The original buffer won't be collected until all its sub buffers plus itself are discarded.

```scala
val buff = DirectNioAllocator.getBuffer(4096)
val dup = buff.duplicate
// work
buff.discard() // discard the original - this can be done before or after discarding the duplicate, it doesn't matter!
dup.discard() // discard the duplicate -> triggers memory cleanup (see below)
// Both the original and the duplicate have been discarded, therefore the buffer's memory is released as soon as possible.
```

## Non-blocking TCP Server

The `network` package contains a few classes that allows you to quickly create a TCP server.

### Features

- Only one thread for all the connections, with the NIO `Selector`.
- Reacts to events: client accepted, client disconnected, message received, message sent, etc.
- Handles messages' headers and data separately.
- Efficient buffer management that avoids copying the data.

### Glimpse

The class `TcpServer` implements the core of the server. It is an abstract class, so you need to extend it to get a real server. The server associates each client to a `ClientAttach`, which contains the client's informations and the incoming and outcoming data.
```scala
final class MyServer[A] extends TcpServer[A](port, baseBufferSize, bufferProvider) {
	override def onAccept(clientChannel: SocketChannel) = {
      // Reacts to a new connection
      // Returns a ClientAttach associated to the new client
	}
	override def onDisconnect(client: ClientAttach[A]): Unit = {
      // Reacts to a disconnection
	}
	override def onError(e: Exception): Unit = {
      // Reacts to an exception. You can decide to rethrow an exception to stop the server, or to continue.
	}
	// There are also onStart, onStarted, onStop and onStopped
}
final class MyAttach[A](i:A, c: SocketChannel, s: TcpServer[A]) extends ClientAttach[A](i,c,s) {
	override def readHeader(buffer: NiolBuffer): Int = {
      // Parses the message's header
      // Returns the message's size
	}
	override def handleData(buffer: NiolBuffer): Unit = {
      // Parses the message's data. You can decide to delegate it to another thread.
	}
}
```
The type parameter `A` defines the additional informations carried by the `ClientAttach`, for instance a client ID.
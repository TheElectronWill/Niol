package com.electronwill.niol.network

import java.nio.channels.{SelectionKey, SocketChannel}
import java.util

import com.electronwill.niol.buffer.{CircularBuffer, NiolBuffer, StraightBuffer}

import scala.annotation.tailrec

/**
 * Stores data associated to a unique TCP client. This abstract class provides basic reading and
 * writing functionnality. Additionnal information and features may and should be added by the
 * subclasses, by using the [[infos]] field and/or by adding new methods.
 *
 * == Thread-safety ==
 * - The public methods of ClientAttach may be called from any thread without problem.
 * - The others are always called from the Selector thread. In particular, [[readHeader()]] and
 * [[handleData()]] don't need to be thread-safe.
 * - The completion handlers are run on the Selector thread. Therefore they should NOT perform
 * long computations. If you have long computations to do, send them to an ExecutorService or
 * something similar.
 *
 * @author TheElectronWill
 */
abstract class ClientAttach[+A](val infos: A, val channel: SocketChannel, server: TcpServer[A]) {
	// read infos
	private[this] val baseReadBuffer = {
		val lowLevelBuffer = server.bufferProvider.getBuffer(server.baseBufferSize)
		new CircularBuffer(lowLevelBuffer)
	}
	private[this] var readBuffer: NiolBuffer = baseReadBuffer
	private[this] var state: InputState = InputState.READ_HEADER
	private[this] var dataLength: Int = _
	private[this] val key: SelectionKey = channel.register(server.selector, SelectionKey.OP_READ)

	@volatile
	private[this] var eos: Boolean = false

	/** @return true if the end of the stream has been reached, false otherwise */
	def streamEnded: Boolean = eos

	/**
	 * The queue that contains the data waiting for being written.
	 */
	private[this] val writeQueue = new util.ArrayDeque[(NiolBuffer, Runnable)]

	/**
	 * Reads more data from the SocketChannel.
	 */
	@tailrec
	private[network] final def readMore(): Unit = {
		eos = (channel >>: readBuffer)._2
		state match {
			// First, the header must be read
			case InputState.READ_HEADER =>
				dataLength = readHeader(readBuffer)
				if (dataLength >= 0) {
					state = InputState.READ_DATA
					if (readBuffer.readAvail >= dataLength) {
						// All the data is available => handle it
						handleDataView()
					} else if (readBuffer.capacity < dataLength) {
						// The buffer is too small => create an additional buffer
						val additional = dataLength - readBuffer.capacity
						val additionalBuffer = server.bufferProvider.getBuffer(additional)
						val additionalStraight = new StraightBuffer(additionalBuffer)
						// Creates a CompositeBuffer without copying the data
						readBuffer = baseReadBuffer + additionalStraight
						// Attempts to fill the buffer -- tail recursive call!
						readMore()
					}
					// Unlike a StraightBuffer, a CircularBuffer doesn't need to be compacted.
				}
			// Then, the data must be read
			case InputState.READ_DATA =>
				if (readBuffer.readAvail >= dataLength) {
					handleDataView()
				}
		}
	}

	/**
	 * Writes more pending data, if any, to the SocketChannel.
	 *
	 * @return true if all the pending data has been written, false otherwise
	 */
	private[network] final def writeMore(): Boolean = {
		writeQueue.synchronized { // Sync protects the queue and the consistency of the interestOps
			var queued = writeQueue.peek() // the next element. null if the queue is empty
			while (queued ne null) {
				val buffer = queued._1
				channel <<: buffer
				if (buffer.readAvail == 0) {
					writeQueue.poll()
					val completionHandler = queued._2
					if (completionHandler ne null) {
						completionHandler.run()
					}
					queued = writeQueue.peek() // fetches the next element
				} else {
					return false
				}
			}
			key.interestOps(SelectionKey.OP_READ) // Stop listening for OP_WRITE
			true
		}
	}

	/**
	 * Writes some data to the client. The data isn't written immediately but at some time in the
	 * future. Therefore this method isn't blocking.
	 *
	 * @param buffer the data to write
	 */
	final def write(buffer: NiolBuffer): Unit = write(buffer, null)

	/**
	 * Asynchronously writes some data to the client, and executes the given completion handler
	 * when the operation completes.
	 *
	 * @param buffer            the data to write
	 * @param completionHandler the handler to execute after the operation
	 */
	final def write(buffer: NiolBuffer, completionHandler: Runnable): Unit = {
		writeQueue.synchronized { // Sync protects the queue and the consistency of the interestOps
			if (writeQueue.isEmpty) {
				channel <<: buffer
				if (buffer.readAvail > 0) {
					writeQueue.offer((buffer, completionHandler))
					key.interestOps(SelectionKey.OP_WRITE) // Continue to write later
				}
			} else {
				writeQueue.offer((buffer, completionHandler))
			}
		}
	}

	/**
	 * Handles the data of the currently available packet.
	 */
	private final def handleDataView(): Unit = {
		// Isolates the packet
		val dataView = readBuffer.subRead(maxLength = dataLength)

		try {
			// Handles the packet
			handleData(dataView)
		} finally {
			// Prepares for the next packet
			state = InputState.READ_HEADER // switches the state
			readBuffer.skipRead(dataLength) // marks the data as read

			// Discards the additional buffer, if any
			if (readBuffer != baseReadBuffer) {
				readBuffer.discard()
				baseReadBuffer.clear()
				readBuffer = baseReadBuffer
			}
			// Discards the view buffer
			dataView.discard()
		}
	}

	/**
	 * Tries to read the packet's header.
	 *
	 * @param buffer the buffer view containing the header
	 * @return the length, in bytes, of the next packet data, or -1 if the header is incomplete.
	 */
	protected def readHeader(buffer: NiolBuffer): Int

	/**
	 * Handles the packet's data.
	 *
	 * @param buffer the buffer view containing all the packet's data.
	 */
	protected def handleData(buffer: NiolBuffer): Unit
}
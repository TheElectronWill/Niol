package com.electronwill.niol.network

import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentLinkedQueue

import com.electronwill.niol.buffer.NiolBuffer

import scala.annotation.tailrec

/**
 * @author TheElectronWill
 */
abstract class ClientAttach[+A](val infos: A, val channel: SocketChannel, server: TcpServer[A]) {
	// read infos
	private[this] val baseReadBuffer = server.bufferProvider.getBuffer(server.baseBufferSize)
	private[this] var readBuffer: NiolBuffer = baseReadBuffer
	private[this] var state: InputState = InputState.READ_HEADER
	private[this] var dataLength: Int = _
	private[this] var eos: Boolean = false

	/** @return true if the end of the stream has been reached, false otherwise */
	def streamEnded: Boolean = eos

	// write infos
	private[this] val writeQueue = new ConcurrentLinkedQueue[(NiolBuffer, Runnable)]

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
						// The buffer is too small => create a complementary buffer
						val complement = dataLength - readBuffer.capacity
						val complBuffer = server.bufferProvider.getBuffer(complement)
						readBuffer = baseReadBuffer + complBuffer // Creates a CompositeBuffer without copy
						readMore() // Attempts to fill the buffer -- tail recursive call!
					}
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
		var queued = writeQueue.peek()
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
		true
	}

	final def write(buffer: NiolBuffer): Unit = write(buffer, null)

	final def write(buffer: NiolBuffer, completionHandler: Runnable): Unit = {
		channel <<: buffer
		if (buffer.writeAvail > 0) {
			writeQueue.offer((buffer, completionHandler))
		}
	}

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

			// Discards the complement buffer, if any
			if (readBuffer != baseReadBuffer) {
				readBuffer.discard()
				baseReadBuffer.clear()
				readBuffer = baseReadBuffer
			}
			// Discards the dataView
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
package com.electronwill.niol

import java.nio.ByteBuffer
import java.nio.channels.{GatheringByteChannel, ScatteringByteChannel}

/**
 * @author TheElectronWill
 */
final class ChannelOutput(private[this] val channel: GatheringByteChannel,
						  bufferCapacity: Int = 4096) extends NiolOutput {

	private[this] val buffer: ByteBuffer = ByteBuffer.allocateDirect(bufferCapacity)

	private def ensureAvailable(min: Int): Unit = {
		if (buffer.remaining() < min) {
			flushBuffer()
		}
	}
	private def flushBuffer(): Unit = {
		buffer.flip()
		channel.write(buffer)
		buffer.clear()
	}
	override def putByte(b: Byte): Unit = {
		ensureAvailable(1)
		buffer.put(b)
	}
	override def putShort(s: Short): Unit = {
		ensureAvailable(2)
		buffer.putShort(s)
	}
	override def putInt(i: Int): Unit = {
		ensureAvailable(4)
		buffer.putInt(i)
	}
	override def putLong(l: Long): Unit = {
		ensureAvailable(8)
		buffer.putLong(l)
	}
	override def putFloat(f: Float): Unit = {
		ensureAvailable(4)
		buffer.putFloat(f)
	}
	override def putDouble(d: Double): Unit = {
		ensureAvailable(8)
		buffer.putDouble(d)
	}

	override def putBytes(array: Array[Byte], offset: Int, length: Int): Unit = {
		var remaining = length
		do {
			if (!buffer.hasRemaining) {
				flushBuffer()
			}
			val l = Math.min(remaining, buffer.position)
			val off = length - remaining
			buffer.put(array, off, l)
			remaining += l
		} while (remaining > 0)
	}
	override def putBytes(source: NiolInput): Unit = {
		var remaining = length
		do {

		}
	}
	override def putBytes(source: ByteBuffer): Unit = ???
	override def putBytes(source: ScatteringByteChannel): Int = ???

	override def putShorts(array: Array[Short], offset: Int, length: Int): Unit = ???
	override def putInts(array: Array[Int], offset: Int, length: Int): Unit = ???
	override def putLongs(array: Array[Long], offset: Int, length: Int): Unit = ???
	override def putFloats(array: Array[Float], offset: Int, length: Int): Unit = ???
	override def putDoubles(array: Array[Double], offset: Int, length: Int): Unit = ???

	def flush(): Unit = {
		channel.write(buffer)
	}
}

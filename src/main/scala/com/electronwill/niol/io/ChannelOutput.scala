package com.electronwill.niol.io

import java.io.{Closeable, IOException}
import java.nio.ByteBuffer
import java.nio.channels.{FileChannel, GatheringByteChannel, ScatteringByteChannel}

import com.electronwill.niol.{InputType, NiolInput, NiolOutput}

/**
 * A NiolOutput based on a ByteChannel. The channel must be in blocking mode for the ChannelOutput
 * to work correctly.
 *
 * @author TheElectronWill
 */
final class ChannelOutput(private[this] val channel: GatheringByteChannel,
						  bufferCapacity: Int = 4096,
						  directBuffer: Boolean = true) extends NiolOutput with Closeable {

	private[this] val buffer: ByteBuffer = {
		if (directBuffer) {
			ByteBuffer.allocateDirect(bufferCapacity)
		} else {
			ByteBuffer.allocate(bufferCapacity)
		}
	}

	private def ensureAvailable(min: Int): Unit = {
		if (buffer.remaining() < min) {
			flush()
		}
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

	override def putBytes(src: Array[Byte], offset: Int, length: Int): Unit = {
		var remaining = length
		do {
			if (!buffer.hasRemaining) {
				flush()
			}
			val l = Math.min(remaining, buffer.position)
			val off = length - remaining
			buffer.put(src, off, l)
			remaining += l
		} while (remaining > 0)
	}
	override def putBytes(src: NiolInput): Unit = {
		if (src.inputType == InputType.FILE_CHANNEL) {
			src.asInstanceOf[ChannelInput].fileTransfer(channel)
		} else {
			do {
				if (!buffer.hasRemaining) {
					flush()
				}
				src.getBytes(buffer)
			} while (src.canRead)
		}
	}
	override def putBytes(src: ByteBuffer): Unit = {
		if (src.isDirect) {
			buffer.flip()
			channel.write(Array(buffer, src))
			buffer.clear()
		} else {
			do {
				if (!buffer.hasRemaining) {
					flush()
				}
				buffer.put(src)
			} while (src.hasRemaining)
		}
	}
	override def putBytes(src: ScatteringByteChannel): (Int, Boolean) = {
		src match {
			case fc: FileChannel =>
				flush()
				val pos = fc.position()
				val count = fc.size() - pos
				(fc.transferTo(pos, count, channel).toInt, false)
			case _ =>
				var read = 1
				var totalRead = 0
				var eos = false
				do {
					if (!buffer.hasRemaining) {
						flush()
					}
					read = src.read(buffer)
					if (read == -1) {
						eos = true
					} else {
						totalRead += read
					}
				} while (read > 0)
				(totalRead, eos)
		}
	}

	override def putShorts(src: Array[Short], offset: Int, length: Int): Unit = {
		var remaining = length
		do {
			if (buffer.remaining < 2) {
				flush()
			}
			val l = Math.min(remaining, buffer.position)
			val off = length - remaining
			buffer.asShortBuffer.put(src, off, l)
			remaining += l
		} while (remaining > 0)
	}
	override def putInts(src: Array[Int], offset: Int, length: Int): Unit = {
		var remaining = length
		do {
			if (buffer.remaining < 4) {
				flush()
			}
			val l = Math.min(remaining, buffer.position)
			val off = length - remaining
			buffer.asIntBuffer.put(src, off, l)
			remaining += l
		} while (remaining > 0)
	}
	override def putLongs(src: Array[Long], offset: Int, length: Int): Unit = {
		var remaining = length
		do {
			if (buffer.remaining < 8) {
				flush()
			}
			val l = Math.min(remaining, buffer.position)
			val off = length - remaining
			buffer.asLongBuffer().put(src, off, l)
			remaining += l
		} while (remaining > 0)
	}
	override def putFloats(src: Array[Float], offset: Int, length: Int): Unit = {
		var remaining = length
		do {
			if (buffer.remaining < 4) {
				flush()
			}
			val l = Math.min(remaining, buffer.position)
			val off = length - remaining
			buffer.asFloatBuffer().put(src, off, l)
			remaining += l
		} while (remaining > 0)
	}
	override def putDoubles(src: Array[Double], offset: Int, length: Int): Unit = {
		var remaining = length
		do {
			if (buffer.remaining < 8) {
				flush()
			}
			val l = Math.min(remaining, buffer.position)
			val off = length - remaining
			buffer.asDoubleBuffer().put(src, off, l)
			remaining += l
		} while (remaining > 0)
	}

	def flush(): Unit = {
		buffer.flip()
		channel.write(buffer)
		buffer.clear()
	}

	@throws[IOException]
	def close(): Unit = {
		flush()
		channel.close()
	}
}
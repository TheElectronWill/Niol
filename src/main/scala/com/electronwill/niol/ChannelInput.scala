package com.electronwill.niol

import java.nio.ByteBuffer
import java.nio.channels.{FileChannel, GatheringByteChannel, ScatteringByteChannel}

/**
 * @author TheElectronWill
 */
final class ChannelInput(private[this] val channel: ScatteringByteChannel,
						 bufferCapacity: Int = 4096) extends NiolInput {

	private[this] val buffer: NiolBuffer = new CircularBuffer(NiolBuffer.allocateDirect(bufferCapacity))
	private[this] var notEnded = true

	override protected[niol] val inputType: InputType = {
		if (channel.isInstanceOf[FileChannel]) InputType.FILE_CHANNEL else InputType.OTHER_CHANNEL
	}

	override def canRead = notEnded
	private def ensureReadAvail(minAvail: Int): Unit = {
		if (buffer.readAvail < minAvail) {
			readMore()
		}
	}
	private def readMore(): Boolean = {
		val read: Int = channel >>: buffer
		if (read < 0) {
			notEnded = false
			channel.close()
			//TODO throw exception
		}
		read >= 0 // false iff the end of the stream has been reached
	}
	override def getByte(): Byte = {
		ensureReadAvail(1)
		buffer.getByte()
	}
	override def getShort(): Short = {
		ensureReadAvail(2)
		buffer.getShort()
	}
	override def getChar(): Char = {
		ensureReadAvail(2)
		buffer.getChar()
	}
	override def getInt(): Int = {
		ensureReadAvail(4)
		buffer.getInt()
	}
	override def getLong(): Long = {
		ensureReadAvail(8)
		buffer.getLong()
	}
	override def getFloat(): Float = {
		ensureReadAvail(4)
		buffer.getFloat()
	}
	override def getDouble(): Double = {
		ensureReadAvail(8)
		buffer.getDouble()
	}
	override def getBytes(dest: Array[Byte], offset: Int, length: Int): Unit = {
		var remaining = length
		do {
			if (buffer.readAvail == 0) {
				readMore()
			}
			val l = Math.min(remaining, buffer.readAvail)
			val off = length - remaining
			buffer.getBytes(dest, off, l)
			remaining -= l
		} while (remaining > 0)
	}
	override def getBytes(dest: ByteBuffer): Unit = {
		do {
			buffer.getBytes(dest)
		} while(dest.hasRemaining && readMore())
	}
	override def getBytes(dest: NiolBuffer): Unit = {
		do {
			dest.putBytes(buffer)
		} while(dest.writeAvail > 0 && readMore())
	}
	override def getBytes(dest: GatheringByteChannel): Int = {
		var count = 0
		do {
			count += buffer.getBytes(dest)
		} while(readMore())
		count
	}

	override def getShorts(dest: Array[Short], offset: Int, length: Int): Unit = {
		var remaining = length
		do {
			if (buffer.readAvail == 0) {
				readMore()
			}
			val l = Math.min(remaining, buffer.readAvail)
			val offset = length - remaining
			buffer.getShorts(dest, offset, l)
			remaining -= l
		} while (remaining > 0)
	}
	override def getInts(dest: Array[Int], offset: Int, length: Int): Unit = {
		var remaining = length
		do {
			if (buffer.readAvail == 0) {
				readMore()
			}
			val l = Math.min(remaining, buffer.readAvail)
			val offset = length - remaining
			buffer.getInts(dest, offset, l)
			remaining -= l
		} while (remaining > 0)
	}
	override def getLongs(dest: Array[Long], offset: Int, length: Int): Unit = {
		var remaining = length
		do {
			if (buffer.readAvail == 0) {
				readMore()
			}
			val l = Math.min(remaining, buffer.readAvail)
			val offset = length - remaining
			buffer.getLongs(dest, offset, l)
			remaining -= l
		} while (remaining > 0)
	}
	override def getFloats(dest: Array[Float], offset: Int, length: Int): Unit = {
		var remaining = length
		do {
			if (buffer.readAvail == 0) {
				readMore()
			}
			val l = Math.min(remaining, buffer.readAvail)
			val offset = length - remaining
			buffer.getFloats(dest, offset, l)
			remaining -= l
		} while (remaining > 0)
	}
	override def getDoubles(dest: Array[Double], offset: Int, length: Int): Unit = {
		var remaining = length
		do {
			if (buffer.readAvail == 0) {
				readMore()
			}
			val l = Math.min(remaining, buffer.readAvail)
			val offset = length - remaining
			buffer.getDoubles(dest, offset, l)
			remaining -= l
		} while (remaining > 0)
	}
}
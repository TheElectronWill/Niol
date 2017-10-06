package com.electronwill.niol

import java.nio.ByteBuffer
import java.nio.channels.{FileChannel, GatheringByteChannel, ScatteringByteChannel}

/**
 * @author TheElectronWill
 */
final class ChannelInput(private[this] val channel: ScatteringByteChannel,
						 bufferCapacity: Int = 4096) extends NiolInput {

	private[this] val buffer: NiolBuffer = new CircularBuffer(NiolBuffer.allocateDirect(bufferCapacity))

	override protected[niol] val inputType: InputType = {
		if (channel.isInstanceOf[FileChannel]) InputType.FILE_CHANNEL else InputType.OTHER_CHANNEL
	}

	private def ensureReadAvail(minAvail: Int): Unit = {
		if (buffer.readAvail < minAvail) {
			readMore()
		}
	}
	private def readMore(): Boolean = {
		val read: Int = channel >>: buffer
		if (read < 0) {
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
	override def getBytes(array: Array[Byte], offset: Int, length: Int): Unit = {
		var remaining = length
		do {
			if (buffer.readAvail == 0) {
				readMore()
			}
			val l = Math.min(remaining, buffer.readAvail)
			val off = length - remaining
			buffer.getBytes(array, off, l)
			remaining -= l
		} while (remaining > 0)
	}
	override def getBytes(bb: ByteBuffer): Unit = {
		do {
			buffer.getBytes(bb)
		} while(bb.hasRemaining && readMore())
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

	override def getShorts(array: Array[Short], offset: Int, length: Int): Unit = {
		var remaining = length
		do {
			if (buffer.readAvail == 0) {
				readMore()
			}
			val l = Math.min(remaining, buffer.readAvail)
			val offset = length - remaining
			buffer.getShorts(array, offset, l)
			remaining -= l
		} while (remaining > 0)
	}
	override def getInts(array: Array[Int], offset: Int, length: Int): Unit = {
		var remaining = length
		do {
			if (buffer.readAvail == 0) {
				readMore()
			}
			val l = Math.min(remaining, buffer.readAvail)
			val offset = length - remaining
			buffer.getInts(array, offset, l)
			remaining -= l
		} while (remaining > 0)
	}
	override def getLongs(array: Array[Long], offset: Int, length: Int): Unit = {
		var remaining = length
		do {
			if (buffer.readAvail == 0) {
				readMore()
			}
			val l = Math.min(remaining, buffer.readAvail)
			val offset = length - remaining
			buffer.getLongs(array, offset, l)
			remaining -= l
		} while (remaining > 0)
	}
	override def getFloats(array: Array[Float], offset: Int, length: Int): Unit = {
		var remaining = length
		do {
			if (buffer.readAvail == 0) {
				readMore()
			}
			val l = Math.min(remaining, buffer.readAvail)
			val offset = length - remaining
			buffer.getFloats(array, offset, l)
			remaining -= l
		} while (remaining > 0)
	}
	override def getDoubles(array: Array[Double], offset: Int, length: Int): Unit = {
		var remaining = length
		do {
			if (buffer.readAvail == 0) {
				readMore()
			}
			val l = Math.min(remaining, buffer.readAvail)
			val offset = length - remaining
			buffer.getDoubles(array, offset, l)
			remaining -= l
		} while (remaining > 0)
	}
}
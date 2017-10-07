package com.electronwill.niol

import java.nio.ByteBuffer
import java.nio.channels.{GatheringByteChannel, ScatteringByteChannel}

/**
 * @author TheElectronWill
 */
final class CircularBuffer(private[niol] val buff: NiolBuffer) extends NiolBuffer {
	require(buff.capacity > 0)

	// buffer state
	override protected[niol] val inputType: InputType = InputType.SPECIAL_BUFFER
	override def capacity: Int = buff.capacity
	override def writePos: Int = buff.writePos
	override def writePos(pos: Int): Unit = buff.writePos(pos)
	override def writeLimit: Int = buff.writeLimit
	override def writeLimit(limit: Int): Unit = buff.writeLimit(limit)
	override def markWritePos(): Unit = buff.markWritePos()
	override def resetWritePos(): Unit = buff.markReadPos()
	override def skipWrite(n: Int): Unit = {
		if (writePos + n >= capacity) {
			writeLimit(readPos)
			readLimit(capacity)
			writePos(n - capacity + 1)
		} else {
			writePos(readPos + n)
		}
	}

	override def readPos: Int = buff.readPos
	override def readPos(pos: Int): Unit = buff.readPos(pos)
	override def readLimit: Int = buff.readLimit
	override def readLimit(limit: Int): Unit = buff.readLimit(limit)
	override def markReadPos(): Unit = buff.markReadPos()
	override def resetReadPos(): Unit = buff.resetReadPos()
	override def skipRead(n: Int): Unit = {
		if (readPos + n >= capacity) {
			readLimit(writePos)
			writeLimit(capacity)
			readPos(n - capacity + 1)
		} else {
			readPos(readPos + n)
		}
	}

	override def writeAvail: Int = {
		if (readPos >= writePos) readPos - writePos else capacity - writePos + readPos
	}
	override def readAvail: Int = {
		if (writePos > readPos) writePos - readPos else capacity - readPos + writePos
	}

	// buffer operations
	override def copyRead: NiolBuffer = {
		val copy = NioBasedBuffer.allocateHeap(readAvail)
		if (readPos >= writePos) {
			sub(readPos, capacity) >>: copy
			sub(0, writePos) >>: copy
		} else {
			sub(readPos, writePos) >>: copy
		}
		copy
	}
	override def subRead: NiolBuffer = {
		if (readPos >= writePos) {
			sub(0, writePos) + sub(readPos, writePos)
		} else {
			sub(readPos, writePos)
		}
	}
	override def subWrite: NiolBuffer = {
		if (readPos >= writePos) {
			sub(writePos, readPos)
		} else if (readPos == 0) {
			sub(writePos, capacity)
		} else {
			sub(writePos, capacity) + sub(0, readPos)
		}
	}

	override def copy(begin: Int, end: Int): NiolBuffer = buff.copy(begin, end)
	override def sub(begin: Int, end: Int): NiolBuffer = buff.sub(begin, end)
	override def duplicate = new CircularBuffer(buff.duplicate)

	// get methods
	/** Called when readPos = capacity, to make the buffer circular */
	private def circleReadPos(): Unit = {
		readPos(0)
		readLimit(writePos)
		writeLimit(capacity)
	}
	/** Called when writePos = capacity, to make the buffer circular */
	private def circleWritePos(): Unit = {
		writePos(0)
		writeLimit(readPos)
		readLimit(capacity)
	}
	/** Called after a write operation to update the readLimit if needed */
	private def updateReadLimit(): Unit = {
		if (writePos > readPos) {
			readLimit(writePos)
		} // else readLimit = capacity, already set normally
	}
	/** Called after a read operation to update the writeLimit if needed */
	private def updateWriteLimit(): Unit = {
		if (readPos >= writePos) {
			writeLimit(readPos)
		} // else writeLimit = capacity, already set normally
	}
	override def getByte(): Byte = {
		val b = buff.getByte()
		if (readPos == capacity) {
			circleReadPos()
		} else {
			updateWriteLimit()
		}
		b
	}
	override def getShort(): Short = {
		val newPos = readPos + 2
		if (newPos <= capacity) {
			val v = buff.getShort()
			if (newPos == capacity) {
				circleReadPos()
			} else {
				updateWriteLimit()
			}
			v
		} else {
			(getByte() << 8 | getByte()).toShort
		}
	}
	override def getChar(): Char = getShort().toChar
	override def getInt(): Int = {
		val newPos = readPos + 4
		if (newPos <= capacity) {
			val v = buff.getInt()
			if (newPos == capacity) {
				circleReadPos()
			} else {
				updateWriteLimit()
			}
			v
		} else {
			getByte() << 24 | getByte() << 16 | getByte() << 8 | getByte()
		}
	}
	override def getLong(): Long = {
		val newPos = readPos + 8
		if (newPos <= capacity) {
			val v = buff.getLong()
			if (newPos == capacity) {
				circleReadPos()
			} else {
				updateWriteLimit()
			}
			v
		} else {
			val bytes = getBytes(8)
			bytes(0) << 56 | bytes(1) << 48 | bytes(2) << 40 | bytes(3) << 32 |
				bytes(4) << 24 | bytes(5) << 16 | bytes(6) << 8 | bytes(7)
		}
	}
	override def getFloat(): Float = java.lang.Float.intBitsToFloat(getInt())
	override def getDouble(): Double = java.lang.Double.longBitsToDouble(getLong())

	override def getBytes(dest: Array[Byte], offset: Int, length: Int): Unit = {
		if (writePos > readPos || readPos + length <= capacity) {
			buff.getBytes(dest, offset, length)
			if (readPos == capacity) {
				circleReadPos()
			}
		} else {
			val lengthA = capacity - readPos
			val lengthB = length - lengthA
			buff.getBytes(dest, offset, lengthA)
			circleReadPos()
			buff.getBytes(dest, offset + lengthA, lengthB)
		}
		updateWriteLimit()
	}
	override def getBytes(dest: ByteBuffer): Unit = {
		if (writePos > readPos || readPos + dest.remaining() <= capacity) {
			buff.getBytes(dest)
			if (readPos == capacity) {
				circleReadPos()
			}
		} else {
			buff.getBytes(dest)
			circleReadPos()
			buff.getBytes(dest)
		}
		updateWriteLimit()
	}
	override def getBytes(dest: NiolBuffer): Unit = {
		dest.putBytes(buff)
		if (readPos == capacity) {
			circleReadPos()
			dest.putBytes(buff)
		} else {
			updateWriteLimit()
		}
	}
	override def getBytes(dest: GatheringByteChannel): Int = {
		var count = 0
		if (writePos > readPos) {
			count = buff.getBytes(dest)
			if (readPos == capacity) {
				circleReadPos()
			}
		} else {
			count = buff.getBytes(dest)
			circleReadPos()
			count += buff.getBytes(dest)
		}
		updateWriteLimit()
		count
	}

	override def getShorts(dest: Array[Short], offset: Int, length: Int): Unit = {
		val bytes = getBytes(length * 2)
		ByteBuffer.wrap(bytes).asShortBuffer().get(dest, offset, length)
	}
	override def getInts(dest: Array[Int], offset: Int, length: Int): Unit = {
		val bytes = getBytes(length * 4)
		ByteBuffer.wrap(bytes).asIntBuffer().get(dest, offset, length)
	}
	override def getLongs(dest: Array[Long], offset: Int, length: Int): Unit = {
		val bytes = getBytes(length * 8)
		ByteBuffer.wrap(bytes).asLongBuffer().get(dest, offset, length)
	}
	override def getFloats(dest: Array[Float], offset: Int, length: Int): Unit = {
		val bytes = getBytes(length * 4)
		ByteBuffer.wrap(bytes).asFloatBuffer().get(dest, offset, length)
	}
	override def getDoubles(dest: Array[Double], offset: Int, length: Int): Unit = {
		val bytes = getBytes(length * 8)
		ByteBuffer.wrap(bytes).asDoubleBuffer().get(dest, offset, length)
	}

	// put methods
	override def putByte(b: Byte): Unit = {
		buff.putByte(b)
		if (writePos == capacity) {
			circleWritePos()
		} else {
			updateReadLimit()
		}
	}
	override def putShort(s: Short): Unit = {
		val newPos = readPos + 2
		if (newPos <= capacity) {
			buff.putShort(s)
			if (newPos == capacity) {
				circleReadPos()
			} else {
				updateReadLimit()
			}
		} else {
			putByte(s >> 8)
			putByte(s)
		}
	}
	override def putInt(i: Int): Unit = {
		val newPos = readPos + 4
		if (newPos <= capacity) {
			buff.putInt(i)
			if (newPos == capacity) {
				circleReadPos()
			} else {
				updateReadLimit()
			}
		} else {
			putByte(i >> 24)
			putByte(i >> 16)
			putByte(i >> 8)
			putByte(i)
		}
	}
	override def putLong(l: Long): Unit = {
		val newPos = readPos + 8
		if (newPos <= capacity) {
			buff.putLong(l)
			if (newPos == capacity) {
				circleReadPos()
			} else {
				updateReadLimit()
			}
		} else {
			putByte(l >> 56)
			putByte(l >> 48)
			putByte(l >> 40)
			putByte(l >> 32)
			putByte(l >> 24)
			putByte(l >> 24)
			putByte(l >> 16)
			putByte(l >> 8)
			putByte(l)
		}
	}
	override def putFloat(f: Float): Unit = putInt(java.lang.Float.floatToIntBits(f))
	override def putDouble(d: Double): Unit = putLong(java.lang.Double.doubleToLongBits(d))

	override def putBytes(src: Array[Byte], offset: Int, length: Int): Unit = {
		if (writePos < readPos || writePos + length <= capacity) {
			buff.putBytes(src, offset, length)
			if (writePos == capacity) {
				circleReadPos()
			}
		} else {
			val lengthA = capacity - writePos
			val lengthB = length - lengthA // must be less than readPos
			buff.putBytes(src, offset, lengthA)
			circleWritePos()
			buff.putBytes(src, offset + lengthA, lengthB)
		}
		updateReadLimit()
	}
	override def putBytes(src: ByteBuffer): Unit = {
		if (writePos < readPos || writePos + src.remaining() <= capacity) {
			buff.putBytes(src)
			if (writePos == capacity) {
				circleReadPos()
			}
		} else {
			buff.putBytes(src)
			circleWritePos()
			buff.putBytes(src)
		}
		updateReadLimit()
	}
	override def putBytes(src: ScatteringByteChannel): Int = {
		var count = buff.putBytes(src)
		if (writePos == capacity) {
			circleWritePos()
			count += buff.putBytes(src)
		} else {
			updateReadLimit()
		}
		count
	}

	override def putShorts(src: Array[Short], offset: Int, length: Int): Unit = {
		val bytes = ByteBuffer.allocate(length * 2)
		bytes.asShortBuffer().put(src, offset, length)
		putBytes(bytes)
	}
	override def putInts(src: Array[Int], offset: Int, length: Int): Unit = {
		val bytes = ByteBuffer.allocate(length * 4)
		bytes.asIntBuffer().put(src, offset, length)
		putBytes(bytes)
	}
	override def putLongs(src: Array[Long], offset: Int, length: Int): Unit = {
		val bytes = ByteBuffer.allocate(length * 8)
		bytes.asLongBuffer().put(src, offset, length)
		putBytes(bytes)
	}
	override def putFloats(src: Array[Float], offset: Int, length: Int): Unit = {
		val bytes = ByteBuffer.allocate(length * 4)
		bytes.asFloatBuffer().put(src, offset, length)
		putBytes(bytes)
	}
	override def putDoubles(src: Array[Double], offset: Int, length: Int): Unit = {
		val bytes = ByteBuffer.allocate(length * 8)
		bytes.asDoubleBuffer().put(src, offset, length)
		putBytes(bytes)
	}
}
package com.electronwill.niol

import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

/**
 * @author TheElectronWill
 */
final class CircularBuffer(private[this] val buff: NiolBuffer) extends NiolBuffer {
	require(buff.capacity > 0)

	// buffer state
	override protected[niol] val inputType: InputType = InputType.CIRCULAR_BUFFER
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
		val copy = NiolBuffer.allocateHeap(readAvail)
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

	override def getBytes(array: Array[Byte], offset: Int, length: Int): Unit = {
		if (writePos > readPos || readPos + length <= capacity) {
			buff.getBytes(array, offset, length)
			if (readPos == capacity) {
				circleReadPos()
			}
		} else {
			val lengthA = capacity - readPos
			val lengthB = length - lengthA
			buff.getBytes(array, offset, lengthA)
			circleReadPos()
			buff.getBytes(array, offset + lengthA, lengthB)
		}
		updateWriteLimit()
	}
	override def getBytes(bb: ByteBuffer): Unit = {
		if (writePos > readPos || readPos + bb.remaining() <= capacity) {
			buff.getBytes(bb)
			if (readPos == capacity) {
				circleReadPos()
			}
		} else {
			buff.getBytes(bb)
			circleReadPos()
			buff.getBytes(bb)
		}
		updateWriteLimit()
	}
	override def getShorts(array: Array[Short], offset: Int, length: Int): Unit = {
		val bytes = getBytes(length * 2)
		ByteBuffer.wrap(bytes).asShortBuffer().get(array, offset, length)
	}
	override def getInts(array: Array[Int], offset: Int, length: Int): Unit = {
		val bytes = getBytes(length * 4)
		ByteBuffer.wrap(bytes).asIntBuffer().get(array, offset, length)
	}
	override def getLongs(array: Array[Long], offset: Int, length: Int): Unit = {
		val bytes = getBytes(length * 8)
		ByteBuffer.wrap(bytes).asLongBuffer().get(array, offset, length)
	}
	override def getFloats(array: Array[Float], offset: Int, length: Int): Unit = {
		val bytes = getBytes(length * 4)
		ByteBuffer.wrap(bytes).asFloatBuffer().get(array, offset, length)
	}
	override def getDoubles(array: Array[Double], offset: Int, length: Int): Unit = {
		val bytes = getBytes(length * 8)
		ByteBuffer.wrap(bytes).asDoubleBuffer().get(array, offset, length)
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

	override def putBytes(array: Array[Byte], offset: Int, length: Int): Unit = {
		if (writePos < readPos || writePos + length <= capacity) {
			buff.putBytes(array, offset, length)
			if (writePos == capacity) {
				circleReadPos()
			}
		} else {
			val lengthA = capacity - writePos
			val lengthB = length - lengthA // must be less than readPos
			buff.putBytes(array, offset, lengthA)
			circleWritePos()
			buff.putBytes(array, offset + lengthA, lengthB)
		}
		updateReadLimit()
	}
	override def putBytes(source: ByteBuffer): Unit = {
		if (writePos < readPos || writePos + source.remaining() <= capacity) {
			buff.putBytes(source)
			if (writePos == capacity) {
				circleReadPos()
			}
		} else {
			buff.putBytes(source)
			circleWritePos()
			buff.putBytes(source)
		}
		updateReadLimit()
	}
	override def putShorts(array: Array[Short], offset: Int, length: Int): Unit = {
		val bytes = ByteBuffer.allocate(length * 2)
		bytes.asShortBuffer().put(array, offset, length)
		putBytes(bytes)
	}
	override def putInts(array: Array[Int], offset: Int, length: Int): Unit = {
		val bytes = ByteBuffer.allocate(length * 4)
		bytes.asIntBuffer().put(array, offset, length)
		putBytes(bytes)
	}
	override def putLongs(array: Array[Long], offset: Int, length: Int): Unit = {
		val bytes = ByteBuffer.allocate(length * 8)
		bytes.asLongBuffer().put(array, offset, length)
		putBytes(bytes)
	}
	override def putFloats(array: Array[Float], offset: Int, length: Int): Unit = {
		val bytes = ByteBuffer.allocate(length * 4)
		bytes.asFloatBuffer().put(array, offset, length)
		putBytes(bytes)
	}
	override def putDoubles(array: Array[Double], offset: Int, length: Int): Unit = {
		val bytes = ByteBuffer.allocate(length * 8)
		bytes.asDoubleBuffer().put(array, offset, length)
		putBytes(bytes)
	}
	override def putBytes(source: NiolInput): Unit = {
		source.inputType match {
			case InputType.NIO_BUFFER =>
				putBytes(source.asInstanceOf[NioBasedBuffer].readBuffer)
			case _ => ??? //TODO
		}
	}
	override def putBytes(source: SocketChannel): Int = ??? //TODO
}
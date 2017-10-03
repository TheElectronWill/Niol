package com.electronwill.niol

import java.nio.ByteBuffer

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
		if (writePos > readPos || readPos + length < capacity) {
			buff.getBytes(array, offset, length)
		} else {

		}
	}
	override def getBytes(bb: ByteBuffer): Unit = ???
	override def getShorts(array: Array[Short], offset: Int, length: Int): Unit = ???
	override def getInts(array: Array[Int], offset: Int, length: Int): Unit = ???
	override def getLongs(array: Array[Long], offset: Int, length: Int): Unit = ???
	override def getFloats(array: Array[Float], offset: Int, length: Int): Unit = ???
	override def getDoubles(array: Array[Double], offset: Int, length: Int): Unit = ???
}

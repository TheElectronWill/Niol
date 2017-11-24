package com.electronwill.niol.buffer

import java.nio.ByteBuffer
import java.nio.channels.{GatheringByteChannel, ScatteringByteChannel}

import com.electronwill.niol.InputType
import com.electronwill.niol.buffer.provider.HeapNioAllocator

/**
 * @author TheElectronWill
 */
final class CircularBuffer(private[niol] val buff: RandomAccessBuffer) extends NiolBuffer {
	require(buff.capacity > 0)
	buff.markUsed()

	// buffer state
	override protected[niol] val inputType: InputType = InputType.SPECIAL_BUFFER
	override def capacity: Int = buff.capacity
	private[buffer] def writePos: Int = buff.writePos
	private def writePos(pos: Int): Unit = buff.writePos(pos)
	private[buffer] def writeLimit: Int = buff.writeLimit
	private def writeLimit(limit: Int): Unit = buff.writeLimit(limit)
	override def skipWrite(n: Int): Unit = {
		if (writePos + n >= capacity) {
			writeLimit(readPos)
			readLimit(capacity)
			writePos(n - capacity + 1)
		} else {
			writePos(readPos + n)
		}
	}

	/**
	 * Used when readPos == writePos to differentiate between the situation where everything has
	 * been read (allRead = true, readAvail = 0) and the situation where everything has been written
	 * (allRead = false, writeAvail = 0).
	 */
	private[this] var allRead = true

	private[buffer] def readPos: Int = buff.readPos
	private def readPos(pos: Int): Unit = buff.readPos(pos)
	private[buffer] def readLimit: Int = buff.readLimit
	private def readLimit(limit: Int): Unit = buff.readLimit(limit)
	override def skipRead(n: Int): Unit = {
		val newPos = readPos + n
		if (newPos >= capacity) {
			readLimit(writePos)
			writeLimit(capacity)
			readPos(newPos - capacity)
		} else {
			readPos(newPos)
		}
	}

	override def writeAvail: Int = {
		if (readPos == writePos) if (allRead) capacity else 0
		else if (readPos > writePos) readPos - writePos
		else capacity - writePos + readPos
	}
	override def readAvail: Int = {
		if (readPos == writePos) if (allRead) 0 else capacity
		else if (writePos > readPos) writePos - readPos
		else capacity - readPos + writePos
	}

	// buffer operations
	override def copyRead: NiolBuffer = {
		val copy = HeapNioAllocator.getBuffer(readAvail)
		if (readPos >= writePos) {
			sub(readPos, capacity) >>: copy
			sub(0, writePos) >>: copy
		} else {
			sub(readPos, writePos) >>: copy
		}
		copy.readLimit(copy.writePos) // Allows the data to be immediately read
		copy
	}
	override def subRead: NiolBuffer = {
		if (readPos >= writePos) {
			sub(readPos, capacity) + sub(0, writePos)
		} else {
			sub(readPos, writePos)
		}
	}
	override def subRead(maxLength: Int): NiolBuffer = {
		if (readPos >= writePos) {
			val partA = capacity - readPos
			if (partA >= maxLength) {
				sub(readPos, readPos + maxLength)
			} else {
				var partB = writePos
				if (partA + partB > maxLength) {
					partB = maxLength - partA
				}
				sub(readPos, capacity) + sub(0, partB)
			}
		} else {
			val end = Math.min(writePos, readPos + maxLength)
			sub(readPos, end)
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
	override def subWrite(maxLength: Int): NiolBuffer = {
		if (readPos >= writePos) {
			sub(writePos, Math.min(readPos, writePos + maxLength))
		} else if (readPos == 0) {
			sub(writePos, Math.min(capacity, writePos + maxLength))
		} else {
			val partA = capacity - writePos
			if (partA >= maxLength) {
				sub(writePos, capacity)
			} else {
				var partB = readPos
				if (partA + partB > maxLength) {
					partB = maxLength - partA
				}
				sub(writePos, capacity) + sub(0, partB)
			}
		}
	}

	private def copy(begin: Int, end: Int): NiolBuffer = buff.copy(begin, end)
	private def sub(begin: Int, end: Int): NiolBuffer = {
		val sub = buff.sub(begin, end)
		markUsed()
		sub
	}
	override def duplicate: NiolBuffer = {
		val d = new CircularBuffer(buff.duplicate)
		markUsed()
		d
	}
	override def clear(): Unit = buff.clear()
	override def compact(): Unit = {}
	override def discard(): Unit = {
		if (useCount.decrementAndGet() == 0) {
			buff.discard()
		}
	}
	override protected[niol] def freeMemory(): Unit = buff.freeMemory()

	// get methods
	/** Called when readPos = capacity, to make the buffer circular */
	private def circleReadPos(): Unit = {
		readPos(0)
		readLimit(writePos)
		writeLimit(capacity)
		if (writePos == 0) {
			allRead = true
		}
	}
	/** Called when writePos = capacity, to make the buffer circular */
	private def circleWritePos(): Unit = {
		writePos(0)
		writeLimit(readPos)
		readLimit(capacity)
		if (readPos == 0) {
			allRead = false
		}
	}
	/** Called after a write operation to update the readLimit if needed */
	private def updateReadLimit(): Unit = {
		if (writePos > readPos) {
			readLimit(writePos)
		} else if (writePos == readPos) {
			allRead = false
		}
		// else readLimit = capacity, already set normally
	}
	/** Called after a read operation to update the writeLimit if needed */
	private def updateWriteLimit(): Unit = {
		if (readPos > writePos) {
			writeLimit(readPos)
		} else if (readPos == writePos) {
			allRead = true
		}
		// else writeLimit = capacity, already set normally
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
		val newPos = writePos + 2
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
		val newPos = writePos + 4
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
		val newPos = writePos + 8
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
	override def putBytes(src: ScatteringByteChannel): (Int, Boolean) = {
		val (n, eos) = buff.putBytes(src)
		if (writePos == capacity) {
			circleWritePos()
			val (n2, eos2) = buff.putBytes(src)
			(n + n2, eos2)
		} else {
			updateReadLimit()
			(n, eos)
		}
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
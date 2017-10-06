package com.electronwill.niol

import java.nio.channels.{GatheringByteChannel, ScatteringByteChannel}
import java.nio.{ByteBuffer, InvalidMarkException}

/**
 * A composite buffer made of two buffers A and B. It acts as a continuous buffer containing the
 * elements of A in [0, A.capacity[ followed by the elements of B in [0, B.capacity[.
 *
 * @author TheElectronWill
 */
final class CompositeBuffer(private[this] val first: NiolBuffer,
							private[this] val second: NiolBuffer) extends NiolBuffer {
	require(first.capacity > 0)
	require(second.capacity > 0)

	// buffer state
	override protected[niol] val inputType: InputType = InputType.SPECIAL_BUFFER

	private[this] var currentWrite: NiolBuffer = first
	private[this] var currentRead: NiolBuffer = first

	private[this] var writeMark = -1
	private[this] var readMark = -1

	private[niol] def currentReadBuffer(): NiolBuffer = currentRead

	override def capacity: Int = first.capacity + second.capacity
	override def writePos: Int = {
		if (currentWrite eq first) first.writePos
		else first.capacity + second.writePos
	}
	override def writePos(pos: Int): Unit = {
		if (pos < first.capacity) {
			currentWrite = first
			first.writePos(pos)
		} else {
			currentWrite = second
			second.writePos(first.capacity + pos)
		}
	}
	override def writeLimit: Int = {
		if (first.writeLimit < first.capacity) first.writeLimit
		else first.capacity + second.writeLimit
	}
	override def writeLimit(limit: Int): Unit = {
		if (limit < first.capacity) {
			first.writeLimit(limit)
		} else {
			first.writeLimit(first.capacity)
			second.writeLimit(limit - first.capacity)
		}
	}
	override def markWritePos(): Unit = {
		writeMark = writePos
	}
	override def resetWritePos(): Unit = {
		if (writeMark == -1) throw new InvalidMarkException
		writePos(writeMark)
	}

	override def readPos: Int = {
		if (currentRead eq first) first.readPos
		else first.capacity + second.readPos
	}
	override def readPos(pos: Int): Unit = {
		if (pos < first.capacity) {
			currentRead = first
			first.readPos(pos)
		} else {
			currentRead = second
			second.readPos(first.capacity + pos)
		}
	}
	override def readLimit: Int = {
		if (first.readLimit < first.capacity) first.readLimit
		else first.capacity + second.readLimit
	}
	override def readLimit(limit: Int): Unit = {
		if (limit < first.capacity) {
			first.readLimit(limit)
		} else {
			first.readLimit(first.capacity)
			second.readLimit(limit - first.capacity)
		}
	}
	override def markReadPos(): Unit = {
		readMark = readPos
	}
	override def resetReadPos(): Unit = {
		if (readMark == -1) throw new InvalidMarkException
		writePos(readMark)
	}

	// buffer operations
	override def duplicate: NiolBuffer = {
		new CompositeBuffer(first.duplicate, second.duplicate)
	}
	override def copy(begin: Int, end: Int): NiolBuffer = {
		val copy = NiolBuffer.allocateHeap(end - begin)
		if (begin < first.capacity) {
			val firstEnd = Math.min(first.capacity, end)
			val secondEnd = end - firstEnd
			first.sub(begin, firstEnd) >>: copy
			second.sub(0, secondEnd) >>: copy
		} else {
			second.sub(begin - first.capacity, end - first.capacity) >>: copy
		}
		copy
	}

	override def sub(begin: Int, end: Int): NiolBuffer = {
		if (begin < first.capacity) {
			if (end < first.capacity) {
				first.sub(begin, end)
			} else {
				val secondEnd = end - first.capacity
				first.sub(begin, first.capacity) + second.sub(0, secondEnd)
			}
		} else {
			second.sub(begin - first.capacity, end - first.capacity)
		}
	}

	// get methods
	override def getByte(): Byte = {
		val b = currentRead.getByte()
		if (currentRead.eq(first) && first.readPos == first.capacity) {
			currentRead = second
		}
		b
	}
	override def getShort(): Short = {
		if (currentRead.eq(second) || first.readPos + 2 < first.capacity) {
			currentRead.getShort()
		} else {
			(getByte() << 8 | getByte()).toShort
		}
	}
	override def getChar(): Char = getShort().toChar
	override def getInt(): Int = {
		if (currentRead.eq(second) || first.readPos + 4 < first.capacity) {
			currentRead.getInt()
		} else {
			getByte() << 24 | getByte() << 16 | getByte() << 8 | getByte()
		}
	}
	override def getLong(): Long = {
		if (currentRead.eq(second) || first.readPos + 8 < first.capacity) {
			currentRead.getInt()
		} else {
			val bytes = getBytes(8)
			bytes(0) << 56 | bytes(1) << 48 | bytes(2) << 40 | bytes(3) << 32 |
			bytes(4) << 24 | bytes(5) << 16 | bytes(6) << 8 | bytes(7)
		}
	}
	override def getFloat(): Float = {
		java.lang.Float.intBitsToFloat(getInt())
	}
	override def getDouble(): Double = {
		java.lang.Double.longBitsToDouble(getLong())
	}

	// bulk get methods
	override def getBytes(array: Array[Byte], offset: Int, length: Int): Unit = {
		if (currentRead.eq(second) || first.readPos + length < first.capacity) {
			currentRead.getBytes(array, offset, length)
		} else {
			val firstLength = Math.min(length, first.capacity - first.readPos)
			val secondLength = length - firstLength
			first.getBytes(array, offset, firstLength)
			second.getBytes(array, offset + firstLength, secondLength)
			currentRead = second
		}
	}
	override def getBytes(bb: ByteBuffer): Unit = {
		val length = Math.min(readLimit - readPos, bb.remaining)
		if (currentRead.eq(second) || first.readPos + length < first.capacity) {
			currentRead.getBytes(bb)
		} else {
			first.getBytes(bb)
			second.getBytes(bb)
			currentRead = second
		}
	}
	override def getBytes(dest: NiolBuffer): Unit = {
		dest.putBytes(first)
		dest.putBytes(second)
	}
	override def getBytes(dest: GatheringByteChannel): Int = {
		if (currentRead.eq(second)) {
			second.getBytes(dest)
		} else {
			if (first.inputType == InputType.NIO_BUFFER &&
				second.inputType == InputType.NIO_BUFFER) {
				dest.write(Array(first.asInstanceOf[NioBasedBuffer].readBuffer,
					second.asInstanceOf[NioBasedBuffer].readBuffer)).toInt
			} else {
				first.getBytes(dest) + second.getBytes(dest)
			}
		}
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
		currentWrite.putByte(b)
		if (currentWrite.eq(first) && first.writePos == first.capacity) {
			currentWrite = second
		}
	}
	override def putShort(s: Short): Unit = {
		if (currentWrite.eq(second) || first.writePos + 2 < first.capacity) {
			currentWrite.putShort(s)
		} else {
			putByte(s >> 8)
			putByte(s)
		}
	}
	override def putInt(i: Int): Unit = {
		if (currentWrite.eq(second) || first.writePos + 4 < first.capacity) {
			currentWrite.putInt(i)
		} else {
			putIntBytes(i)
		}
	}
	override def putLong(l: Long): Unit = {
		if (currentWrite.eq(second) || first.writePos + 8 < first.capacity) {
			currentWrite.putLong(l)
		} else {
			putLongBytes(l)
		}
	}
	override def putFloat(f: Float): Unit = {
		if (currentWrite.eq(second) || first.writePos + 4 < first.capacity) {
			currentWrite.putFloat(f)
		} else {
			putIntBytes(java.lang.Float.floatToIntBits(f))
		}
	}
	override def putDouble(d: Double): Unit = {
		if (currentWrite.eq(second) || first.writePos + 8 < first.capacity) {
			currentWrite.putDouble(d)
		} else {
			putLongBytes(java.lang.Double.doubleToLongBits(d))
		}
	}
	private def putIntBytes(i: Int): Unit = {
		putByte(i >> 24)
		putByte(i >> 16)
		putByte(i >> 8)
		putByte(i)
	}
	private def putLongBytes(l: Long): Unit = {
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

	// bulk put methods
	override def putBytes(array: Array[Byte], offset: Int, length: Int): Unit = {
		if (currentWrite.eq(second) || first.writePos + length < first.capacity) {
			currentWrite.putBytes(array, offset, length)
		} else {
			val firstLength = Math.min(length, first.capacity - first.readPos)
			val secondLength = length - firstLength
			first.putBytes(array, offset, firstLength)
			second.putBytes(array, offset + firstLength, secondLength)
			currentWrite = second
		}
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
	override def putBytes(source: ByteBuffer): Unit = {
		val length = Math.min(writeLimit - writePos, source.remaining)
		if (length == 0) return
		if (currentWrite.eq(second) || first.readPos + length < first.capacity) {
			currentWrite.putBytes(source)
		} else {
			first.putBytes(source)
			second.putBytes(source)
			currentWrite = second
		}
	}
	override def putBytes(source: ScatteringByteChannel): Int = {
		if (currentWrite eq second) {
			second.putBytes(source)
		} else {
			if (first.inputType == InputType.NIO_BUFFER &&
				second.inputType == InputType.NIO_BUFFER) {
				source.read(Array(first.asInstanceOf[NioBasedBuffer].readBuffer,
					second.asInstanceOf[NioBasedBuffer].readBuffer)).toInt
			} else {
				first.putBytes(source) + second.putBytes(source)
			}
		}
	}
}
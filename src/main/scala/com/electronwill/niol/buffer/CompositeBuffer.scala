package com.electronwill.niol.buffer

import java.nio.channels.{GatheringByteChannel, ScatteringByteChannel}
import java.nio.{ByteBuffer, InvalidMarkException}

import com.electronwill.niol.InputType

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
		val copy = NioBasedBuffer.allocateHeap(end - begin)
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

	override def compact(): Unit = {
		val data = sub(readPos, writePos)
		clear()
		putBytes(data)
	}

	override def discard(): Unit = {
		first.discard()
		second.discard()
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
	override def getBytes(dest: Array[Byte], offset: Int, length: Int): Unit = {
		if (currentRead.eq(second) || first.readPos + length < first.capacity) {
			currentRead.getBytes(dest, offset, length)
		} else {
			val firstLength = Math.min(length, first.capacity - first.readPos)
			val secondLength = length - firstLength
			first.getBytes(dest, offset, firstLength)
			second.getBytes(dest, offset + firstLength, secondLength)
			currentRead = second
		}
	}
	override def getBytes(dest: ByteBuffer): Unit = {
		val length = Math.min(readLimit - readPos, dest.remaining)
		if (currentRead.eq(second) || first.readPos + length < first.capacity) {
			currentRead.getBytes(dest)
		} else {
			first.getBytes(dest)
			second.getBytes(dest)
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
				dest.write(Array(first.asInstanceOf[NioBasedBuffer].asReadByteBuffer,
					second.asInstanceOf[NioBasedBuffer].asReadByteBuffer)).toInt
			} else {
				first.getBytes(dest) + second.getBytes(dest)
			}
		}
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
	override def putBytes(src: Array[Byte], offset: Int, length: Int): Unit = {
		if (currentWrite.eq(second) || first.writePos + length < first.capacity) {
			currentWrite.putBytes(src, offset, length)
		} else {
			val firstLength = Math.min(length, first.capacity - first.readPos)
			val secondLength = length - firstLength
			first.putBytes(src, offset, firstLength)
			second.putBytes(src, offset + firstLength, secondLength)
			currentWrite = second
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
	override def putBytes(src: ByteBuffer): Unit = {
		val length = Math.min(writeLimit - writePos, src.remaining)
		if (length == 0) return
		if (currentWrite.eq(second) || first.readPos + length < first.capacity) {
			currentWrite.putBytes(src)
		} else {
			first.putBytes(src)
			second.putBytes(src)
			currentWrite = second
		}
	}
	override def putBytes(src: ScatteringByteChannel): Int = {
		if (currentWrite eq second) {
			second.putBytes(src)
		} else {
			if (first.inputType == InputType.NIO_BUFFER &&
				second.inputType == InputType.NIO_BUFFER) {
				src.read(Array(first.asInstanceOf[NioBasedBuffer].asReadByteBuffer,
					second.asInstanceOf[NioBasedBuffer].asReadByteBuffer)).toInt
			} else {
				first.putBytes(src) + second.putBytes(src)
			}
		}
	}
}
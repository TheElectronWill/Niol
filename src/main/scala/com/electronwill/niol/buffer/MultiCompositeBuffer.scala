package com.electronwill.niol.buffer

import java.nio.{BufferUnderflowException, BufferOverflowException, ByteBuffer}
import java.nio.channels.{GatheringByteChannel, ScatteringByteChannel}

import com.electronwill.niol.InputType

/**
 * @author TheElectronWill
 */
final class MultiCompositeBuffer(firstBuffer: NiolBuffer = EmptyBuffer) extends NiolBuffer {
	// The buffers are stored in a kind of LinkedList
	private[this] var head: Node = new Node(firstBuffer)
	private[this] var tail = head
	private[this] var currentRead: Node = head
	private[this] var currentWrite: Node = head
	private[this] var currentCapacity: Int = firstBuffer.capacity

	def discardExhausted(): Unit = {
		while (head.ne(currentRead) && head.ne(currentWrite)) {
			currentCapacity -= head.data.capacity
			head.data.discard()
			head = head.next
		}
	}

	def +=(buffer: NiolBuffer): Unit = {
		val newTail = new Node(buffer)
		tail.next = newTail
		tail = newTail
		currentCapacity += buffer.capacity
	}

	private class Node(var data: NiolBuffer) {
		data.markUsed()
		var next: Node = _
	}

	//TODO composite A,B,C,...
	// buffer state
	override protected[niol] val inputType = InputType.SPECIAL_BUFFER
	override def capacity: Int = currentCapacity
	override def writePos: Int = ???
	override def writePos(pos: Int): Unit = throw new UnsupportedOperationException
	override def writeLimit: Int = ???
	override def writeLimit(limit: Int): Unit = throw new UnsupportedOperationException
	override def markWritePos(): Unit = throw new UnsupportedOperationException
	override def resetWritePos(): Unit = throw new UnsupportedOperationException

	override def readPos: Int = ???
	override def readPos(pos: Int): Unit = throw new UnsupportedOperationException
	override def readLimit: Int = ???
	override def readLimit(limit: Int): Unit = throw new UnsupportedOperationException
	override def markReadPos(): Unit = throw new UnsupportedOperationException
	override def resetReadPos(): Unit = throw new UnsupportedOperationException

	// buffer operations
	override def duplicate: NiolBuffer = ???
	override def copy(begin: Int, end: Int): NiolBuffer = ???
	override def sub(begin: Int, end: Int): NiolBuffer = ???
	override def compact(): Unit = ???
	override def discard(): Unit = ???

	// get operations
	private def canReadDirectly(count: Int): Boolean = {
		val avail = currentRead.data.readAvail
		if (avail == 0) {
			if (currentRead.next eq null) {
				throw new BufferUnderflowException
			} else {
				currentRead = currentRead.next
				true
			}
		} else if (avail < count) {
			if (currentRead.next eq null) {
				throw new BufferUnderflowException
			} else {
				false
			}
		} else {
			true
		}
	}

	override def getByte(): Byte = {
		if (currentRead.data.readAvail == 0) {
			if (currentRead.next eq null) {
				throw new BufferUnderflowException
			} else {
				currentRead = currentRead.next
			}
		}
		currentRead.data.getByte()
	}
	override def getShort(): Short = {
		if (canReadDirectly(2)) {
			currentRead.data.getShort()
		} else {
			(getByte() << 8 | getByte()).toShort
		}
	}
	override def getChar(): Char = getShort().toChar
	override def getInt(): Int = {
		if (canReadDirectly(4)) {
			currentRead.data.getInt()
		} else {
			getByte() << 24 | getByte() << 16 | getByte() << 8 | getByte()
		}
	}
	override def getLong(): Long = {
		if (canReadDirectly(8)) {
			currentRead.data.getLong()
		} else {
			getByte() << 56 | getByte() << 48 | getByte() << 40 | getByte() << 32 |
			getByte() << 24 | getByte() << 16 | getByte() << 8  | getByte()
		}
	}
	override def getFloat(): Float = {
		if (canReadDirectly(4)) {
			currentRead.data.getFloat()
		} else {
			java.lang.Float.intBitsToFloat(getInt())
		}
	}
	override def getDouble(): Double = {
		if (canReadDirectly(8)) {
			currentRead.data.getDouble()
		} else {
			java.lang.Double.longBitsToDouble(getLong())
		}
	}
	override def getBytes(dest: Array[Byte], offset: Int, length: Int): Unit = {
		var remaining = length
		while (remaining > 0) {
			// Copies the bytes
			val l = Math.min(currentRead.data.readAvail, remaining)
			currentRead.data.getBytes(dest, length - remaining, l)
			// Updates the counter
			remaining -= l
			// Moves to the next buffer if needed
			if (remaining > 0) {
				if (currentRead.next eq null) throw new BufferUnderflowException
				currentRead = currentRead.next
			}
		}
	}
	override def getBytes(dest: ByteBuffer): Unit = {
		var again = true
		while (again) {
			currentRead.data.getBytes(dest)
			if (currentRead.data.next ne null && dest.hasRemaining) {
				currentRead = currentRead.next
			} else {
				again = false
			}
		}
	}
	override def getBytes(dest: NiolBuffer): Unit = {
		var again = true
		while (again) {
			currentRead.data.getBytes(dest)
			if (currentRead.next ne null && dest.writeAvail > 0) {
				currentRead = currentRead.next
			} else {
				again = false
			}
		}
	}
	override def getBytes(dest: GatheringByteChannel): Int = {
		currentRead.getBytes(dest)
		while (currentRead.next ne null) {
			currentRead = currentRead.next
			currentRead.getBytes(dest)
		}
	}
	override def getShorts(dest: Array[Short], offset: Int, length: Int): Unit = {
		// TODO optimize: avoid copying
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

	// put operations
	private def canWriteDirectly(count: Int): Boolean = {
		val avail = currentWrite.data.writeAvail
		if (avail == 0) {
			if (currentWrite.next eq null) {
				throw new BufferOverflowException
			} else {
				currentWrite = currentWrite.next
				true
			}
		} else if (avail < count) {
			if (currentWrite.next eq null) {
				throw new BufferOverflowException
			} else {
				false
			}
		} else {
			true
		}
	}
	override def putByte(b: Byte): Unit = {
		if (currentWrite.data.writeAvail == 0) {
			if (currentWrite.next eq null) {
				throw new BufferOverflowException
			} else {
				currentRead = currentRead.next
			}
		}
		currentWrite.data.putByte(b)
	}
	override def putShort(s: Short): Unit = {
		if (canWriteDirectly(2)) {
			currentWrite.putShort(s)
		} else {
			putByte(s >> 8)
			putByte(s)
		}
	}
	override def putInt(i: Int): Unit = {
		if (canWriteDirectly(4)) {
			currentWrite.putInt(i)
		} else {
			putByte(i >> 24)
			putByte(i >> 16)
			putByte(i >> 8)
			putByte(i)
		}
	}
	override def putLong(l: Long): Unit = {
		if (canWriteDirectly(8)) {
			currentWrite.putLong(l)
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
	override def putFloat(f: Float): Unit = {
		putInt(java.lang.Float.floatToIntBits(f))
	}
	override def putDouble(d: Double): Unit = {
		putDouble(java.lang.Double.doubleToLongBits(d))
	}

	// bulk put methods
	override def putBytes(src: Array[Byte], offset: Int, length: Int): Unit = {
		var remaining = length
		while (remaining > 0) {
			// Copies the bytes
			val l = Math.min(currentWrite.data.writeAvail, remaining)
			currentWrite.data.putBytes(src, length - remaining, l)
			// Updates the counter
			remaining -= l
			// Moves to the next buffer if needed
			if (remaining > 0) {
				if (currentWrite.next eq null) throw new BufferOverflowException
				currentWrite = currentWrite.next
			}
		}
	}
	override def putBytes(src: ByteBuffer): Unit = {
		var again = true
		while (again) {
			currentWrite.data.putBytes(src)
			if (currentWrite.data.next ne null && src.hasRemaining) {
				currentWrite = currentWrite.next
			} else {
				again = false
			}
		}
	}
	override def putBytes(src: ScatteringByteChannel): (Int, Boolean) = {
		var totalRead = 0
		var eos = false
		while (!eos) {
			val (read, eos) = currentWrite.data.putBytes(src)
			totalRead += read
			if (!eos) {
				currentWrite = currentWrite.next
			}
		}
		(totalRead, eos)
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

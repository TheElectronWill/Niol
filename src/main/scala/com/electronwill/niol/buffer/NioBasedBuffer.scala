package com.electronwill.niol.buffer

import java.nio.ByteBuffer
import java.nio.channels.{GatheringByteChannel, ScatteringByteChannel}

import com.electronwill.niol.InputType
import com.electronwill.niol.buffer.provider.{BufferProvider, HeapNioAllocator}
import sun.nio.ch.DirectBuffer

/**
 * A buffer based on a [[java.nio.ByteBuffer]].
 *
 * @author TheElectronWill
 */
final class NioBasedBuffer private[niol](private[this] val writeBuffer: ByteBuffer,
										 private[this] val readBuffer: ByteBuffer,
										 private[this] val parent: NiolBuffer,
										 private[this] val provider: BufferProvider)
	extends RandomAccessBuffer {

	private[niol] def this(writeBuff: ByteBuffer, parent: NiolBuffer, provider: BufferProvider) = {
		this(writeBuff, writeBuff.duplicate(), parent, provider)
		readBuffer.position(0)
		readBuffer.limit(writeBuff.position())
	}

	// buffer state
	override protected[niol] val inputType: InputType = InputType.NIO_BUFFER

	override def capacity = writeBuffer.capacity()

	override def writePos = writeBuffer.position()
	override def writePos(pos: Int) = writeBuffer.position(pos)
	override def writeLimit = writeBuffer.limit()
	override def writeLimit(limit: Int) = writeBuffer.limit(limit)
	override def markWritePos() = writeBuffer.mark()
	override def resetWritePos() = writeBuffer.reset()

	override def readPos = readBuffer.position()
	override def readPos(pos: Int) = readBuffer.position(pos)
	override def readLimit = readBuffer.limit()
	override def readLimit(limit: Int) = readBuffer.limit(limit)
	override def markReadPos() = readBuffer.mark()
	override def resetReadPos() = readBuffer.reset()

	private[niol] def asReadByteBuffer: ByteBuffer = readBuffer

	// buffer operations
	override def duplicate: RandomAccessBuffer = {
		val d = new NioBasedBuffer(writeBuffer.duplicate(), readBuffer.duplicate(), this, null)
		markUsed()
		d
	}

	override def copy(begin: Int, end: Int): RandomAccessBuffer = {
		val copy = HeapNioAllocator.getBuffer(end - begin)
		bbView(begin, end) >>: copy
		copy
	}

	override def sub(begin: Int, end: Int): RandomAccessBuffer = {
		val buff = bbView(begin, end).slice()
		markUsed()
		new NioBasedBuffer(buff, this, null)
	}

	private def bbView(begin: Int, end: Int): ByteBuffer = {
		val buff = readBuffer.duplicate()
		buff.limit(end)
		buff.position(begin)
		buff
	}

	override def compact(): Unit = {
		readBuffer.limit(writePos)
		readBuffer.compact() // move [readPos, writePos[ to [0, newWritePos[

		val newWritePos = readBuffer.position()
		writeBuffer.limit(capacity)
		writeBuffer.position(newWritePos)
		readBuffer.position(0)
		readBuffer.limit(newWritePos)
	}

	override def discard(): Unit = {
		if (useCount.decrementAndGet() == 0) {
			if (parent ne null) {
				parent.discard()
			}
			if (provider ne null) {
				provider.discard(this)
			}
		}
	}

	override protected[niol] def freeMemory(): Unit = {
		if (writeBuffer.isDirect) {
			writeBuffer.asInstanceOf[DirectBuffer].cleaner().clean()
		}
	}

	// get methods
	override def getByte() = readBuffer.get()
	override def getShort() = readBuffer.getShort()
	override def getChar() = readBuffer.getChar()
	override def getInt() = readBuffer.getInt()
	override def getLong() = readBuffer.getLong()
	override def getFloat() = readBuffer.getFloat()
	override def getDouble() = readBuffer.getDouble()

	override def getBytes(dest: Array[Byte], offset: Int, length: Int): Unit = {
		readBuffer.get(dest, offset, length)
	}
	override def getBytes(dest: ByteBuffer): Unit = dest.put(readBuffer)
	override def getBytes(dest: NiolBuffer): Unit = dest.putBytes(readBuffer)
	override def getBytes(dest: GatheringByteChannel): Int = dest.write(readBuffer)

	override def getShorts(dest: Array[Short], offset: Int, length: Int): Unit = {
		readBuffer.asShortBuffer().get(dest, offset, length)
	}
	override def getInts(dest: Array[Int], offset: Int, length: Int): Unit = {
		readBuffer.asIntBuffer().get(dest, offset, length)
	}
	override def getLongs(dest: Array[Long], offset: Int, length: Int): Unit = {
		readBuffer.asLongBuffer().get(dest, offset, length)
	}
	override def getFloats(dest: Array[Float], offset: Int, length: Int): Unit = {
		readBuffer.asFloatBuffer().get(dest, offset, length)
	}
	override def getDoubles(dest: Array[Double], offset: Int, length: Int): Unit = {
		readBuffer.asDoubleBuffer().get(dest, offset, length)
	}

	// put methods
	override def putByte(b: Byte): Unit = writeBuffer.put(b)
	override def putShort(s: Short): Unit = writeBuffer.putShort(s)
	override def putInt(i: Int): Unit = writeBuffer.putInt(i)
	override def putLong(l: Long): Unit = writeBuffer.putLong(l)
	override def putFloat(f: Float): Unit = writeBuffer.putFloat(f)
	override def putDouble(d: Double): Unit = writeBuffer.putDouble(d)

	override def putBytes(src: Array[Byte], offset: Int, length: Int): Unit = {
		writeBuffer.put(src, offset, length)
	}
	override def putBytes(src: ByteBuffer): Unit = writeBuffer.put(src)
	override def putBytes(src: ScatteringByteChannel): (Int, Boolean) = {
		val n = src.read(writeBuffer)
		if (n == -1) (0, true) else (n, false)
	}

	override def putShorts(src: Array[Short], offset: Int, length: Int): Unit = {
		writeBuffer.asShortBuffer().put(src, offset, length)
	}
	override def putInts(src: Array[Int], offset: Int, length: Int): Unit = {
		writeBuffer.asIntBuffer().put(src, offset, length)
	}
	override def putLongs(src: Array[Long], offset: Int, length: Int): Unit = {
		writeBuffer.asLongBuffer().put(src, offset, length)
	}
	override def putFloats(src: Array[Float], offset: Int, length: Int): Unit = {
		writeBuffer.asFloatBuffer().put(src, offset, length)
	}
	override def putDoubles(src: Array[Double], offset: Int, length: Int): Unit = {
		writeBuffer.asDoubleBuffer().put(src, offset, length)
	}
}
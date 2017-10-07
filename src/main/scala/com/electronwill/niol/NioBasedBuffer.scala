package com.electronwill.niol

import java.nio.ByteBuffer
import java.nio.channels.{GatheringByteChannel, ScatteringByteChannel}

/**
 * A buffer based on a [[java.nio.ByteBuffer]].
 *
 * @author TheElectronWill
 */
final class NioBasedBuffer(private[this] val writeBuffer: ByteBuffer,
						   private[niol] val readBuffer: ByteBuffer) extends NiolBuffer {
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

	// buffer operations
	override def duplicate: NiolBuffer = {
		new NioBasedBuffer(writeBuffer.duplicate(), readBuffer.duplicate())
	}

	override def copy(begin: Int, end: Int): NiolBuffer = {
		val copy = NioBasedBuffer.allocateHeap(end - begin)
		bbView(begin, end) >>: copy
		copy
	}

	override def sub(begin: Int, end: Int): NiolBuffer = {
		val buff = bbView(begin, end).slice()
		NioBasedBuffer.wrap(buff)
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
	override def putBytes(src: ScatteringByteChannel): Int = src.read(writeBuffer)

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
object NioBasedBuffer {
	def allocateHeap(capacity: Int): NiolBuffer = wrap(ByteBuffer.allocate(capacity))

	def allocateDirect(capacity: Int): NiolBuffer = wrap(ByteBuffer.allocateDirect(capacity))

	def wrap(writeBuffer: ByteBuffer): NiolBuffer = {
		val readBuffer = writeBuffer.duplicate()
		readBuffer.position(0)
		readBuffer.limit(writeBuffer.position())
		new NioBasedBuffer(writeBuffer, readBuffer)
	}
}
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
	override def duplicate: NiolBuffer = new NioBasedBuffer(writeBuffer.duplicate(), readBuffer
																					 .duplicate())

	override def copy(begin: Int, end: Int): NiolBuffer = {
		val copy = NiolBuffer.allocateHeap(end - begin)
		bbView(begin, end) >>: copy
		copy
	}

	override def sub(begin: Int, end: Int): NiolBuffer = {
		val buff = bbView(begin, end).slice()
		NiolBuffer.wrap(buff)
	}

	private def bbView(begin: Int, end: Int): ByteBuffer = {
		val buff = readBuffer.duplicate()
		buff.limit(end)
		buff.position(begin)
		buff
	}

	// get methods
	override def getByte() = readBuffer.get()
	override def getShort() = readBuffer.getShort()
	override def getChar() = readBuffer.getChar()
	override def getInt() = readBuffer.getInt()
	override def getLong() = readBuffer.getLong()
	override def getFloat() = readBuffer.getFloat()
	override def getDouble() = readBuffer.getDouble()

	override def getBytes(array: Array[Byte], offset: Int, length: Int): Unit = {
		readBuffer.get(array, offset, length)
	}
	override def getBytes(bb: ByteBuffer): Unit = {
		bb.put(readBuffer)
	}
	override def getBytes(dest: NiolBuffer): Unit = {
		dest.putBytes(readBuffer)
	}
	override def getBytes(dest: GatheringByteChannel): Int = {
		dest.write(readBuffer)
	}

	override def getShorts(array: Array[Short], offset: Int, length: Int): Unit = {
		readBuffer.asShortBuffer().get(array, offset, length)
	}
	override def getInts(array: Array[Int], offset: Int, length: Int): Unit = {
		readBuffer.asIntBuffer().get(array, offset, length)
	}
	override def getLongs(array: Array[Long], offset: Int, length: Int): Unit = {
		readBuffer.asLongBuffer().get(array, offset, length)
	}
	override def getFloats(array: Array[Float], offset: Int, length: Int): Unit = {
		readBuffer.asFloatBuffer().get(array, offset, length)
	}
	override def getDoubles(array: Array[Double], offset: Int, length: Int): Unit = {
		readBuffer.asDoubleBuffer().get(array, offset, length)
	}

	// put methods
	override def putByte(b: Byte): Unit = writeBuffer.put(b)
	override def putShort(s: Short): Unit = writeBuffer.putShort(s)
	override def putInt(i: Int): Unit = writeBuffer.putInt(i)
	override def putLong(l: Long): Unit = writeBuffer.putLong(l)
	override def putFloat(f: Float): Unit = writeBuffer.putFloat(f)
	override def putDouble(d: Double): Unit = writeBuffer.putDouble(d)

	override def putBytes(array: Array[Byte], offset: Int, length: Int): Unit = {
		writeBuffer.put(array, offset, length)
	}
	override def putBytes(source: ByteBuffer): Unit = {
		writeBuffer.put(source)
	}
	override def putBytes(source: ScatteringByteChannel): Int = {
		source.read(writeBuffer)
	}

	override def putShorts(array: Array[Short], offset: Int, length: Int): Unit = {
		writeBuffer.asShortBuffer().put(array, offset, length)
	}
	override def putInts(array: Array[Int], offset: Int, length: Int): Unit = {
		writeBuffer.asIntBuffer().put(array, offset, length)
	}
	override def putLongs(array: Array[Long], offset: Int, length: Int): Unit = {
		writeBuffer.asLongBuffer().put(array, offset, length)
	}
	override def putFloats(array: Array[Float], offset: Int, length: Int): Unit = {
		writeBuffer.asFloatBuffer().put(array, offset, length)
	}
	override def putDoubles(array: Array[Double], offset: Int, length: Int): Unit = {
		writeBuffer.asDoubleBuffer().put(array, offset, length)
	}
}
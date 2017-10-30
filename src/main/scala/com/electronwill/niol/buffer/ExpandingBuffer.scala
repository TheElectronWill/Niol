package com.electronwill.niol.buffer

import java.nio.channels.{GatheringByteChannel, ScatteringByteChannel}
import java.nio.{BufferOverflowException, ByteBuffer}

import com.electronwill.niol.InputType
import com.electronwill.niol.buffer.provider.BufferProvider

/**
 * A buffer that grows automatically without copying data.
 *
 * @author TheElectronWill
 */
final class ExpandingBuffer(minCapacityIncrement: Int, maxCapacity: Int,
							private[this] val bufferProvider: BufferProvider) extends NiolBuffer {
	private[this] val minIncrement = Math.max(minCapacityIncrement, 32)
	private[this] var buff: NiolBuffer = new CompositeBuffer()

	override val capacity: Int = maxCapacity
	override protected[niol] val inputType: InputType = InputType.SPECIAL_BUFFER

	override def readAvail: Int = buff.readAvail
	override def writeAvail: Int = capacity - buff.capacity + buff.writeAvail

	override def skipWrite(n: Int): Unit = {
		ensureWriteAvail(n)
		buff.skipWrite(n)
	}
	override def skipRead(n: Int): Unit = buff.skipRead(n)
	override def copyRead: NiolBuffer = buff.copyRead
	override def subRead: NiolBuffer = buff.subRead
	override def subRead(maxLength: Int): NiolBuffer = buff.subRead(maxLength)
	override def subWrite: NiolBuffer = buff.subWrite
	override def clear(): Unit = buff.clear()
	override def duplicate: NiolBuffer = buff.duplicate
	override def compact(): Unit = buff.compact()
	override def discard(): Unit = buff.discard()

	override def getByte(): Byte = buff.getByte()
	override def getShort(): Short = buff.getShort()
	override def getChar(): Char = buff.getChar()
	override def getInt(): Int = buff.getInt()
	override def getLong(): Long = buff.getLong()
	override def getFloat(): Float = buff.getFloat()
	override def getDouble(): Double = buff.getDouble()
	override def getBytes(dest: Array[Byte], offset: Int, length: Int): Unit = {
		buff.getBytes(dest, offset, length)
	}
	override def getBytes(dest: ByteBuffer): Unit = buff.getBytes(dest)
	override def getBytes(dest: NiolBuffer): Unit = buff.getBytes(dest)
	override def getBytes(dest: GatheringByteChannel): Int = buff.getBytes(dest)
	override def getShorts(dest: Array[Short], offset: Int, length: Int): Unit = {
		buff.getShorts(dest, offset, length)
	}
	override def getInts(dest: Array[Int], offset: Int, length: Int): Unit = {
		buff.getInts(dest, offset, length)
	}
	override def getLongs(dest: Array[Long], offset: Int, length: Int): Unit = {
		buff.getLongs(dest, offset, length)
	}
	override def getFloats(dest: Array[Float], offset: Int, length: Int): Unit = {
		buff.getFloats(dest, offset, length)
	}
	override def getDoubles(dest: Array[Double], offset: Int, length: Int): Unit = {
		buff.getDoubles(dest, offset, length)
	}

	def ensureWriteAvail(writeLength: Int): Unit = {
		val avail = buff.writeAvail
		if (avail < writeLength) {
			val increment = Math.max(minIncrement, writeLength - writeAvail)
			if (increment + buff.capacity > maxCapacity) {
				throw new BufferOverflowException
			}
			buff += bufferProvider.getBuffer(increment)
		}
	}

	override def putByte(b: Byte): Unit = {
		ensureWriteAvail(1)
		buff.putByte(b)
	}
	override def putShort(s: Short): Unit = {
		ensureWriteAvail(2)
		buff.putShort(s)
	}
	override def putInt(i: Int): Unit = {
		ensureWriteAvail(4)
		buff.putInt(i)
	}
	override def putLong(l: Long): Unit = {
		ensureWriteAvail(8)
		buff.putLong(l)
	}
	override def putFloat(f: Float): Unit = {
		ensureWriteAvail(4)
		buff.putFloat(f)
	}
	override def putDouble(d: Double): Unit = {
		ensureWriteAvail(8)
		buff.putDouble(d)
	}
	override def putBytes(src: Array[Byte], offset: Int, length: Int): Unit = {
		ensureWriteAvail(length)
		buff.putBytes(src, offset, length)
	}
	override def putBytes(src: ByteBuffer): Unit = {
		ensureWriteAvail(src.remaining())
		buff.putBytes(src)
	}
	override def putBytes(src: ScatteringByteChannel): (Int, Boolean) = {
		buff.putBytes(src)
	}
	override def putShorts(src: Array[Short], offset: Int, length: Int): Unit = {
		ensureWriteAvail(length * 2)
		buff.putShorts(src, offset, length)
	}
	override def putInts(src: Array[Int], offset: Int, length: Int): Unit = {
		ensureWriteAvail(length * 4)
		buff.putInts(src, offset, length)
	}
	override def putLongs(src: Array[Long], offset: Int, length: Int): Unit = {
		ensureWriteAvail(length * 8)
		buff.putLongs(src, offset, length)
	}
	override def putFloats(src: Array[Float], offset: Int, length: Int): Unit = {
		ensureWriteAvail(length * 4)
		buff.putFloats(src, offset, length)
	}
	override def putDoubles(src: Array[Double], offset: Int, length: Int): Unit = {
		ensureWriteAvail(length * 8)
		buff.putDoubles(src, offset, length)
	}
}
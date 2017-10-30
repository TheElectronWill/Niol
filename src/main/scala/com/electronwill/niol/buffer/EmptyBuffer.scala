package com.electronwill.niol.buffer

import java.nio.ByteBuffer
import java.nio.channels.{GatheringByteChannel, ScatteringByteChannel}

import com.electronwill.niol.{InputType, NiolInput}

/**
 * @author TheElectronWill
 */
object EmptyBuffer extends NiolBuffer {
	// buffer state
	override protected[niol] val inputType: InputType = InputType.SPECIAL_BUFFER
	override def capacity: Int = 0
	override def writeAvail = 0
	override def readAvail = 0

	// buffer operations
	override def duplicate: NiolBuffer = EmptyBuffer
	override def compact(): Unit = {}
	override def discard(): Unit = {}

	override def skipWrite(n: Int): Unit = !!
	override def skipRead(n: Int): Unit = !!
	override def copyRead: NiolBuffer = EmptyBuffer
	override def subRead: NiolBuffer = EmptyBuffer
	override def subRead(maxLength: Int): NiolBuffer = EmptyBuffer
	override def subWrite: NiolBuffer = EmptyBuffer
	override def clear(): Unit = {}

	// get methods
	override def getByte(): Byte = !!
	override def getShort(): Short = !!
	override def getChar(): Char = !!
	override def getInt(): Int = !!
	override def getLong(): Long = !!
	override def getFloat(): Float = !!
	override def getDouble(): Double = !!

	override def getBytes(array: Array[Byte], offset: Int, length: Int): Unit = !!
	override def getBytes(bb: ByteBuffer): Unit = {}
	override def getBytes(dest: NiolBuffer): Unit = {}
	override def getBytes(dest: GatheringByteChannel) = 0

	override def getShorts(array: Array[Short], offset: Int, length: Int): Unit = !!
	override def getInts(array: Array[Int], offset: Int, length: Int): Unit = !!
	override def getLongs(array: Array[Long], offset: Int, length: Int): Unit = !!
	override def getFloats(array: Array[Float], offset: Int, length: Int): Unit = !!
	override def getDoubles(array: Array[Double], offset: Int, length: Int): Unit = !!

	// put methods
	override def putByte(b: Byte): Unit = !!
	override def putShort(s: Short): Unit = !!
	override def putInt(i: Int): Unit = !!
	override def putLong(l: Long): Unit = !!
	override def putFloat(f: Float): Unit = !!
	override def putDouble(d: Double): Unit = !!

	override def putBytes(array: Array[Byte], offset: Int, length: Int): Unit = !!
	override def putBytes(source: NiolInput): Unit = {}
	override def putBytes(source: ByteBuffer): Unit = {}
	override def putBytes(source: ScatteringByteChannel): (Int, Boolean) = (0, false)

	override def putShorts(array: Array[Short], offset: Int, length: Int): Unit = !!
	override def putInts(array: Array[Int], offset: Int, length: Int): Unit = !!
	override def putLongs(array: Array[Long], offset: Int, length: Int): Unit = !!
	override def putFloats(array: Array[Float], offset: Int, length: Int): Unit = !!
	override def putDoubles(array: Array[Double], offset: Int, length: Int): Unit = !!

	@inline private def !! : Nothing = {
		throw new UnsupportedOperationException("EmptyBuffer")
	}
}
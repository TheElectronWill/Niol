package com.electronwill.niol
import java.nio.ByteBuffer
import java.nio.channels.{GatheringByteChannel, ScatteringByteChannel}

/**
 * @author TheElectronWill
 */
object EmptyBuffer extends NiolBuffer {
	// buffer state
	override protected[niol] val inputType: InputType = InputType.SPECIAL_BUFFER
	override val capacity: Int = 0

	override val writePos: Int = 0
	override def writePos(pos: Int): Unit = {}
	override val writeLimit: Int = 0
	override def writeLimit(limit: Int): Unit = {}
	override def markWritePos(): Unit = {}
	override def resetWritePos(): Unit = {}

	override val readPos: Int = 0
	override def readPos(pos: Int): Unit = {}
	override val readLimit: Int = 0
	override def readLimit(limit: Int): Unit = {}

	override def markReadPos(): Unit = {}
	override def resetReadPos(): Unit = {}

	// buffer operations
	override def duplicate = EmptyBuffer
	override def copy(begin: Int, end: Int) = EmptyBuffer
	override def sub(begin: Int, end: Int) = EmptyBuffer

	// get methods
	override def getByte(): Byte = ???
	override def getShort(): Short = ???
	override def getChar(): Char = ???
	override def getInt(): Int = ???
	override def getLong(): Long = ???
	override def getFloat(): Float = ???
	override def getDouble(): Double = ???

	override def getBytes(array: Array[Byte], offset: Int, length: Int): Unit = ???
	override def getBytes(bb: ByteBuffer): Unit = {}
	override def getBytes(dest: NiolBuffer): Unit = {}
	override def getBytes(dest: GatheringByteChannel) = 0

	override def getShorts(array: Array[Short], offset: Int, length: Int): Unit = ???
	override def getInts(array: Array[Int], offset: Int, length: Int): Unit = ???
	override def getLongs(array: Array[Long], offset: Int, length: Int): Unit = ???
	override def getFloats(array: Array[Float], offset: Int, length: Int): Unit = ???
	override def getDoubles(array: Array[Double], offset: Int, length: Int): Unit = ???

	// put methods
	override def putByte(b: Byte): Unit = ???
	override def putShort(s: Short): Unit = ???
	override def putInt(i: Int): Unit = ???
	override def putLong(l: Long): Unit = ???
	override def putFloat(f: Float): Unit = ???
	override def putDouble(d: Double): Unit = ???

	override def putBytes(array: Array[Byte], offset: Int, length: Int): Unit = ???
	override def putBytes(source: NiolInput): Unit = {}
	override def putBytes(source: ByteBuffer): Unit = {}
	override def putBytes(source: ScatteringByteChannel): Int = 0

	override def putShorts(array: Array[Short], offset: Int, length: Int): Unit = ???
	override def putInts(array: Array[Int], offset: Int, length: Int): Unit = ???
	override def putLongs(array: Array[Long], offset: Int, length: Int): Unit = ???
	override def putFloats(array: Array[Float], offset: Int, length: Int): Unit = ???
	override def putDoubles(array: Array[Double], offset: Int, length: Int): Unit = ???
}
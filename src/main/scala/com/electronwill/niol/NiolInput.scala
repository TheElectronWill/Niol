package com.electronwill.niol

import java.nio.ByteBuffer
import java.nio.channels.GatheringByteChannel
import java.nio.charset.Charset

/**
 * @author TheElectronWill
 */
trait NiolInput {
	// input infos
	protected[niol] val inputType: InputType

	// get methods
	def getByte(): Byte
	def getBool(): Boolean = getByte() == 1
	final def getBool(trueValue: Byte): Boolean = getByte() == trueValue

	def getShort(): Short
	def getChar(): Char
	def getInt(): Int
	def getLong(): Long
	def getFloat(): Float
	def getDouble(): Double

	final def getVarint(): Int = getVarint(5)
	final def getVarint(maxBytes: Int): Int = {
		val maxShift = maxBytes * 7
		var shift: Int = 7
		var result: Int = 0
		var read: Byte = 0
		do {
			read = getByte()
			result |= ((read & 0x7F) << shift)
			if (shift > maxShift) {
				throw new RuntimeException(
					s"VarInt too big: it should take at most $maxBytes bytes")
			}
			shift += 7
		} while ((read & 0x80) != 0)
		result
	}
	final def getVarlong(): Long = getVarlong(10)
	final def getVarlong(maxBytes: Int): Long = {
		val maxShift = maxBytes * 7
		var shift: Int = 7
		var result: Long = 0
		var read: Byte = 0
		do {
			read = getByte()
			result |= ((read & 0x7F) << shift)
			if (shift > maxShift) {
				throw new RuntimeException(
					s"VarLong too big: it should take at most $maxBytes bytes")
			}
			shift += 7
		} while ((read & 0x80) != 0)
		result
	}

	final def getUnsignedByte(): Int = getByte() & 0xFF
	final def getUnsignedShort(): Int = getShort() & 0xFFFF

	def getString(bytesLength: Int, charset: Charset): String = {
		val buff = ByteBuffer.wrap(getBytes(bytesLength))
		charset.decode(buff).toString
	}

	// bulk get methods
	def getBytes(count: Int): Array[Byte] = {
		val array = new Array[Byte](count)
		getBytes(array, 0, count)
		array
	}
	def getBytes(array: Array[Byte], offset: Int, length: Int): Unit
	def getBytes(bb: ByteBuffer): Unit
	def getBytes(dest: NiolBuffer): Unit
	def getBytes(dest: GatheringByteChannel): Int

	def getShorts(array: Array[Short], offset: Int, length: Int): Unit
	def getShorts(count: Int): Array[Short] = {
		val array = new Array[Short](count)
		getShorts(array, 0, count)
		array
	}

	def getInts(array: Array[Int], offset: Int, length: Int): Unit
	def getInts(count: Int): Array[Int] = {
		val array = new Array[Int](count)
		getInts(array, 0, count)
		array
	}

	def getLongs(array: Array[Long], offset: Int, length: Int): Unit
	def getLongs(count: Int): Array[Long] = {
		val array = new Array[Long](count)
		getLongs(array, 0, count)
		array
	}

	def getFloats(array: Array[Float], offset: Int, length: Int): Unit
	def getFloats(count: Int): Array[Float] = {
		val array = new Array[Float](count)
		getFloats(array, 0, count)
		array
	}

	def getDoubles(array: Array[Double], offset: Int, length: Int): Unit
	def getDoubles(count: Int): Array[Double] = {
		val array = new Array[Double](count)
		getDoubles(array, 0, count)
		array
	}
}
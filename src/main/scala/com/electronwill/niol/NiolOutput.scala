package com.electronwill.niol

import java.nio.ByteBuffer
import java.nio.channels.ScatteringByteChannel
import java.nio.charset.Charset

/**
 * @author TheElectronWill
 */
trait NiolOutput {
	// put methods
	def putBool(bool: Boolean): Unit = putByte(if (bool) 1 else 0)

	def putByte(b: Byte): Unit
	final def putByte(i: Int): Unit = putByte(i.toByte)
	final def putByte(l: Long): Unit = putByte(l.toByte)

	def putShort(s: Short): Unit
	def putInt(i: Int): Unit
	def putLong(l: Long): Unit
	def putFloat(f: Float): Unit
	def putDouble(d: Double): Unit
	final def putVarint(i: Int): Unit = {
		var value = i
		do {
			var b = value & 0x7F
			value >>>= 7
			if (value != 0) {
				b |= 0x80
			}
			putByte(b)
		} while (value != 0)
	}
	final def putVarlong(l: Long): Unit = {
		var value = l
		do {
			var b = value & 0x7F
			value >>>= 7
			if (value != 0) {
				b |= 0x80
			}
			putByte(b)
		} while (value != 0)
	}
	final def putString(str: String, charset: Charset): Unit = charset.encode(str) >>: this

	// bulk put methods
	def putBytes(array: Array[Byte]): Unit = putBytes(array, 0, array.length)
	def putBytes(array: Array[Byte], offset: Int, length: Int): Unit
	def putBytes(source: NiolInput): Unit
	def putBytes(source: ByteBuffer): Unit
	def putBytes(source: ScatteringByteChannel): Int

	def putShorts(array: Array[Short]): Unit = putShorts(array, 0, array.length)
	def putShorts(array: Array[Short], offset: Int, length: Int): Unit

	def putInts(array: Array[Int]): Unit = putInts(array, 0, array.length)
	def putInts(array: Array[Int], offset: Int, length: Int): Unit

	def putLongs(array: Array[Long]): Unit = putLongs(array, 0, array.length)
	def putLongs(array: Array[Long], offset: Int, length: Int): Unit

	def putFloats(array: Array[Float]): Unit = putFloats(array, 0, array.length)
	def putFloats(array: Array[Float], offset: Int, length: Int): Unit

	def putDoubles(array: Array[Double]): Unit = putDoubles(array, 0, array.length)
	def putDoubles(array: Array[Double], offset: Int, length: Int): Unit

	// shortcuts
	@inline final def >>:(bool: Boolean): Unit = putBool(bool)
	@inline final def >>:(b: Byte): Unit = putByte(b)
	@inline final def >>:(s: Short): Unit = putShort(s)
	@inline final def >>:(i: Int): Unit = putInt(i)
	@inline final def >>:(l: Long): Unit = putLong(l)
	@inline final def >>:(f: Float): Unit = putFloat(f)
	@inline final def >>:(d: Double): Unit = putDouble(d)
	@inline final def >>:(str: String, charset: Charset): Unit = putString(str, charset)
	@inline final def >>:(array: Array[Byte]): Unit = putBytes(array)
	@inline final def >>:(array: Array[Byte], offset: Int, length: Int): Unit = putBytes(array, offset, length)
	@inline final def >>:(input: NiolInput): Unit = putBytes(input)
	@inline final def >>:(bb: ByteBuffer): Unit = putBytes(bb)
	@inline final def >>:(chan: ScatteringByteChannel): Int = putBytes(chan)
}
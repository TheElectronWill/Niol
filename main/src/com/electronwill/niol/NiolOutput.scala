package com.electronwill.niol

import java.nio.ByteBuffer
import java.nio.channels.ScatteringByteChannel
import java.nio.charset.Charset

/**
 * @author TheElectronWill
 */
trait NiolOutput {
  // put methods
  def putBool(bool: Boolean): Unit = {
    putByte(if (bool) 1 else 0)
  }

  def putByte(b: Byte): Unit

  final def putByte(i: Int): Unit = putByte(i.toByte)

  final def putByte(l: Long): Unit = putByte(l.toByte)

  def putShort(s: Short): Unit

  final def putShort(i: Int): Unit = putShort(i.toShort)

  def putInt(i: Int): Unit

  def putLong(l: Long): Unit

  def putFloat(f: Float): Unit

  final def putFloat(d: Double): Unit = putFloat(d.toFloat)

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

  final def putVarstring(str: String, charset: Charset): Unit = {
    val encoded = charset.encode(str)
    putVarint(encoded.remaining())
    putBytes(encoded)
  }

  // bulk put methods
  def putBytes(src: Array[Byte]): Unit = {
    putBytes(src, 0, src.length)
  }

  def putBytes(src: Array[Byte], offset: Int, length: Int): Unit

  def putBytes(src: NiolInput): Unit

  def putBytes(src: ByteBuffer): Unit

  def putBytes(src: ScatteringByteChannel): (Int, Boolean)

  def putShorts(src: Array[Short]): Unit = putShorts(src, 0, src.length)

  def putShorts(src: Array[Short], offset: Int, length: Int): Unit

  def putInts(src: Array[Int]): Unit = putInts(src, 0, src.length)

  def putInts(src: Array[Int], offset: Int, length: Int): Unit

  def putLongs(src: Array[Long]): Unit = putLongs(src, 0, src.length)

  def putLongs(src: Array[Long], offset: Int, length: Int): Unit

  def putFloats(src: Array[Float]): Unit = putFloats(src, 0, src.length)

  def putFloats(src: Array[Float], offset: Int, length: Int): Unit

  def putDoubles(src: Array[Double]): Unit = putDoubles(src, 0, src.length)

  def putDoubles(src: Array[Double], offset: Int, length: Int): Unit

  // shortcuts
  @inline final def >>:(bool: Boolean): Unit = {
    putBool(bool)
  }

  @inline final def >>:(b: Byte): Unit = putByte(b)

  @inline final def >>:(s: Short): Unit = putShort(s)

  @inline final def >>:(i: Int): Unit = putInt(i)

  @inline final def >>:(l: Long): Unit = putLong(l)

  @inline final def >>:(f: Float): Unit = putFloat(f)

  @inline final def >>:(d: Double): Unit = putDouble(d)

  @inline final def >>:(t: (String, Charset)): Unit = putString(t._1, t._2)

  @inline final def >>:(array: Array[Byte]): Unit = putBytes(array)

  @inline final def >>:(t: (Array[Byte], Int, Int)): Unit = putBytes(t._1, t._2, t._3)

  @inline final def >>:(array: Array[Short]): Unit = putShorts(array)

  @inline final def >>:(array: Array[Int]): Unit = putInts(array)

  @inline final def >>:(array: Array[Long]): Unit = putLongs(array)

  @inline final def >>:(array: Array[Float]): Unit = putFloats(array)

  @inline final def >>:(array: Array[Double]): Unit = putDoubles(array)

  @inline final def >>:(input: NiolInput): Unit = putBytes(input)

  @inline final def >>:(bb: ByteBuffer): Unit = putBytes(bb)

  @inline final def >>:(chan: ScatteringByteChannel): (Int, Boolean) = putBytes(chan)
}
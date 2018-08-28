package com.electronwill.niol

import java.nio.ByteBuffer
import java.nio.channels.ScatteringByteChannel
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID

/**
 * @author TheElectronWill
 */
trait NiolOutput {
  // put methods
  def putBoolean(boolean: Boolean) = putBool(boolean, 1, 0)

  def putBool(bool: Boolean) = putBool(bool, 1, 0)

  def putBool(bool: Boolean, trueValue: Byte, falseValue: Byte): Unit = {
    putByte(if (bool) trueValue else falseValue)
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

  final def putString(str: String, charset: Charset = UTF_8): Unit = putBytes(charset.encode(str))

  final def putVarstring(str: String, charset: Charset= UTF_8): Unit = {
    val encoded = charset.encode(str)
    putVarint(encoded.remaining())
    putBytes(encoded)
  }

  final def putUUID(uuid: UUID): Unit = {
    putLong(uuid.getMostSignificantBits)
    putLong(uuid.getLeastSignificantBits)
  }

  // bulk put methods
  def putBytes(src: Array[Byte]): Unit = putBytes(src, 0, src.length)

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
  @inline final def >>:(src: Array[Byte]): Unit = putBytes(src)

  @inline final def >>:(src: (Array[Byte], Int, Int)): Unit = putBytes(src._1, src._2, src._3)

  @inline final def >>:(src: NiolInput): Unit = putBytes(src)

  @inline final def >>:(src: ByteBuffer): Unit = putBytes(src)

  @inline final def >>:(src: ScatteringByteChannel): (Int, Boolean) = putBytes(src)
}

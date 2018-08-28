package com.electronwill.niol

import java.nio.ByteBuffer
import java.nio.channels.GatheringByteChannel
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID

import com.electronwill.niol.buffer.NiolBuffer

/**
 * @author TheElectronWill
 */
trait NiolInput {
  // input infos
  protected[niol] val inputType: InputType

  def canRead: Boolean

  // get methods
  def getByte(): Byte

  final def getBool(trueValue: Byte = 1): Boolean = getByte() == trueValue

  def getShort(): Short

  def getChar(): Char

  def getInt(): Int

  def getLong(): Long

  def getFloat(): Float

  def getDouble(): Double

  final def getVarint(maxBytes: Int = 5): Int = {
    val maxShift = maxBytes * 7
    var shift: Int = 0
    var result: Int = 0
    var read: Byte = 0
    do {
      read = getByte()
      result |= ((read & 0x7F) << shift)
      if (shift > maxShift) {
        throw new RuntimeException(s"VarInt too big: it should take at most $maxBytes bytes")
      }
      shift += 7
    } while ((read & 0x80) != 0)
    result
  }

  final def getVarlong(maxBytes: Int = 10): Long = {
    val maxShift = maxBytes * 7
    var shift: Int = 0
    var result: Long = 0
    var read: Byte = 0
    do {
      read = getByte()
      result |= ((read & 0x7F) << shift)
      if (shift > maxShift) {
        throw new RuntimeException(s"VarLong too big: it should take at most $maxBytes bytes")
      }
      shift += 7
    } while ((read & 0x80) != 0)
    result
  }

  final def getUnsignedByte(): Int = getByte() & 0xFF

  final def getUnsignedShort(): Int = getShort() & 0xFFFF

  final def getString(bytesLength: Int, charset: Charset = UTF_8): String = {
    val buff = ByteBuffer.wrap(getBytes(bytesLength))
    charset.decode(buff).toString
  }

  final def getVarstring(charset: Charset = UTF_8): String = {
    val size = getVarint()
    getString(size, charset)
  }

  final def getUUID(): UUID = new UUID(getLong(), getLong())

  // bulk get methods
  final def getBytes(count: Int): Array[Byte] = {
    val array = new Array[Byte](count)
    getBytes(array, 0, count)
    array
  }

  def getBytes(dest: Array[Byte]): Unit = getBytes(dest, 0, dest.length)

  def getBytes(dest: Array[Byte], offset: Int, length: Int): Unit

  def getBytes(dest: ByteBuffer): Unit

  def getBytes(dest: NiolBuffer): Unit

  def getBytes(dest: GatheringByteChannel): Int

  def getShorts(dest: Array[Short]): Unit = getShorts(dest, 0, dest.length)

  def getShorts(dest: Array[Short], offset: Int, length: Int): Unit

  def getShorts(count: Int): Array[Short] = {
    val array = new Array[Short](count)
    getShorts(array, 0, count)
    array
  }

  def getInts(dest: Array[Int]): Unit = getInts(dest, 0, dest.length)

  def getInts(dest: Array[Int], offset: Int, length: Int): Unit

  def getInts(count: Int): Array[Int] = {
    val array = new Array[Int](count)
    getInts(array, 0, count)
    array
  }

  def getLongs(dest: Array[Long]): Unit = getLongs(dest, 0, dest.length)

  def getLongs(dest: Array[Long], offset: Int, length: Int): Unit

  def getLongs(count: Int): Array[Long] = {
    val array = new Array[Long](count)
    getLongs(array, 0, count)
    array
  }

  def getFloats(dest: Array[Float]): Unit = getFloats(dest, 0, dest.length)

  def getFloats(dest: Array[Float], offset: Int, length: Int): Unit

  def getFloats(count: Int): Array[Float] = {
    val array = new Array[Float](count)
    getFloats(array, 0, count)
    array
  }

  def getDoubles(dest: Array[Double]): Unit = getDoubles(dest, 0, dest.length)

  def getDoubles(dest: Array[Double], offset: Int, length: Int): Unit

  def getDoubles(count: Int): Array[Double] = {
    val array = new Array[Double](count)
    getDoubles(array, 0, count)
    array
  }

  // shortcuts
  @inline final def <<:(dest: Array[Byte]): Unit = getBytes(dest)

  @inline final def <<:(dest: (Array[Byte], Int, Int)): Unit = getBytes(dest._1, dest._2, dest._3)

  @inline final def <<:(dest: ByteBuffer): Unit = getBytes(dest)

  @inline final def <<:(dest: NiolBuffer): Unit = getBytes(dest)

  @inline final def <<:(dest: GatheringByteChannel): Unit = getBytes(dest)
}

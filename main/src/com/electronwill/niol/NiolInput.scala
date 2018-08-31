package com.electronwill.niol

import java.io.OutputStream
import java.nio.{ByteBuffer, ByteOrder}
import java.nio.channels.GatheringByteChannel
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID

import com.electronwill.niol.buffer.NiolBuffer

/**
 * An advanced input.
 *
 * @author TheElectronWill
 */
abstract class NiolInput {
  // ------------------------------
  // ----- Input information -----
  /***
   * Returns true if at least one byte can be read from this NiolInput.
   *
   * @return true if at least one byte can be read, false otherwise
   */
  def canRead: Boolean


  // --------------------------------
  // ----- Protected operations -----
  /** Implements get without necessarily checking for available data. */
  protected[this] def _get(): Byte

  /** Throws an exception if the operation is incomplete */
  protected[this] def checkComplete(expected: Int, actual: Int, v: String = "value"): Unit = {
    if (actual != expected) throw new IncompleteGetException(expected, actual, v)
  }


  // ---------------------------------------------
  // ----- Primitive single-value operations -----
  /** Reads a byte */
  def get(): Byte = {
    if (canRead) _get()
    else throw new NotEnoughDataException(1, 0)
  }

  /**
   * Attempts to read a byte. If the input is empty, returns -1. Otherwise, returns the byte value
   * as an int in the range 0-255.
   *
   * @return the byte value in range 0-255, or -1 if the input is empty
   */
  def tryGet(): Int = {
    if (canRead) {
      _get().toInt
    } else {
      -1
    }
  }

  /** Reads a boolean */
  def getBoolean(): Boolean = getBoolF(0)

  /** Reads a boolean. */
  def getBool(): Boolean = getBoolean()

  /** Reads one byte and returns true if it's equal to the given value, false otherwise. */
  def getBoolT(trueValue: Byte): Boolean = get() == trueValue

  /** Reads one byte and returns false if it's equal to the given value, true otherwise. */
  def getBoolF(falseValue: Byte): Boolean = get() != falseValue

  /** Reads a byte */
  final def getByte(): Byte = get()

  /** Reads a big-endian short */
  def getShort(): Short = (getByte() << 8 | getByte()).toShort

  /** Reads a little-endian short */
  def getShortLE(): Short = (getByte() | getByte() << 8).toShort

  /** Reads a big-endian char */
  def getChar(): Char = getUnsignedShort().toChar

  /** Reads a little-endian char */
  def getCharLE(): Char = getUnsignedShortLE().toChar

  /** Reads a big-endian 3-bytes integer */
  def getMedium(): Int = getByte() << 16 | getByte() << 8 | getByte()

  /** Reads a little-endian 3-bytes integer */
  def getMediumLE(): Int = getByte() | getByte() << 8 | getByte() << 16

  /** Reads a big-endian 4-bytes integer */
  def getInt(): Int = getByte() << 24 | getByte() << 16 | getByte() << 8 | getByte()

  /** Reads a little-endian 4-bytes integer */
  def getIntLE(): Int = getByte() | getByte() << 8 | getByte() << 16 | getByte() << 24

  /** Reads a big-endian 8-bytes integer */
  def getLong(): Long = {
    val b = getBytes(8)
    b(0) << 56 | b(1) << 48 | b(2) << 40 | b(3) << 32 | b(4) << 24 | b(5) << 16 | b(6) << 8 | b(7)
  }

  /** Reads a little-endian 8-bytes integer */
  def getLongLE(): Long = {
    val b = getBytes(8)
    b(0) | b(1) << 8 | b(2) << 16 | b(3) << 24 | b(4) << 32 | b(5) << 40 | b(6) << 48 | b(7) << 56
  }

  /** Reads a big-endian 4-bytes float */
  def getFloat(): Float = java.lang.Float.intBitsToFloat(getInt())

  /** Reads a little-endian 4-bytes float */
  def getFloatLE(): Float = java.lang.Float.intBitsToFloat(getIntLE())

  /** Reads a big-endian 8-bytes double */
  def getDouble(): Double = java.lang.Double.longBitsToDouble(getLong())

  /** Reads a little-endian 8-bytes double */
  def getDoubleLE(): Double = java.lang.Double.longBitsToDouble(getLongLE())

  /** Reads an unsigned byte as an int */
  final def getUnsignedByte(): Int = getByte() & 0xFF

  /** Reads a big-endian unsigned short as an int */
  final def getUnsignedShort(): Int = getShort() & 0xFFFF

  /** Reads a little-endian unsigned short as an int */
  final def getUnsignedShortLE(): Int = getShortLE() & 0xFFFF

  /** Reads a big-endian unsigned medium as an int */
  final def getUnsignedMedium(): Int = getMedium() & 0xFFFFFF

  /** Reads a little-endian unsigned medium as an int */
  final def getUnsignedMediumLE(): Int = getMediumLE() & 0xFFFFFF

  /** Reads a big-endian unsigned int as a long */
  final def getUnsignedInt(): Long = getInt() & 0xFFFFFFFF

  /** Reads a little-endian unsigned int as a long */
  final def getUnsignedIntLE(): Long = getIntLE() & 0xFFFFFFFF

  /** Reads a big-endian 16-bytes UUID */
  final def getUUID(): UUID = new UUID(getLong(), getLong())


  // -----------------------------------------------
  // ----- Variable-length integers operations -----
  /** Reads a variable-length int using the normal/unsigned encoding. */
  def getVarInt(maxBytes: Int = 5): Int = {
    val maxShift = maxBytes * 7
    var shift: Int = 0
    var result: Int = 0
    var read: Int = 0xFF
    while ((read & 0x80) != 0 && shift < maxShift) {
      if (!canRead) throw new IncompleteGetException(1, "VarLong")
      read = _get()
      result |= ((read & 0x7F) << shift)
      shift += 7
    }
    result
  }

  /** Reads a variable-length long using the normal/unsigned encoding. */
  def getVarLong(maxBytes: Int = 10): Long = {
    val maxShift = maxBytes * 7
    var shift: Int = 0
    var result: Long = 0
    var read: Int = 0xFF
    while ((read & 0x80) != 0 && shift < maxShift) {
      if (!canRead) throw new IncompleteGetException(1, "VarLong")
      read = _get()
      result |= ((read & 0x7F) << shift)
      shift += 7
    }
    result
  }

  /** Reads a variable-length int using the signed/zig-zag encoding. */
  final def getSVarIntZigZag(maxBytes: Int = 5): Int = {
    val n = getVarInt(maxBytes)
    (n >> 1) ^ -(n & 1)
  }

  /** Reads a variable-length long using the signed/zig-zag encoding. */
  final def getSVarLongZigZag(maxBytes: Int = 10): Long = {
    val n = getVarLong(maxBytes)
    (n >> 1) ^ -(n & 1)
  }

  /** Reads the next `bytesLength` bytes as a String encoded with the given charset. */
  final def getString(bytesLength: Int, charset: Charset = UTF_8): String = {
    getCharSequence(bytesLength, charset).toString
  }

  /** Reads a VarInt to determine the string's length, then reads the string. */
  final def getVarString(charset: Charset = UTF_8): String = {
    getVarCharSequence(charset).toString
  }

  /** Reads a big-endian unsigned short to determine the string's length, then reads it. */
  final def getShortString(charset: Charset = UTF_8): String = {
    getShortCharSequence(charset).toString
  }

  /** Reads a little-endian unsigned short to determine the string's length, then reads it. */
  final def getShortStringLE(charset: Charset = UTF_8): String = {
    getShortCharSequenceLE(charset).toString
  }

  /** Reads the next `bytesLength` bytes as a CharSequence encoded with the given charset. */
  final def getCharSequence(bytesLength: Int, charset: Charset = UTF_8): CharSequence = {
    charset.decode(ByteBuffer.wrap(getBytes(bytesLength)))
  }

  /** Reads a VarInt to determine the sequence's length, then reads the CharSequence. */
  final def getVarCharSequence(charset: Charset = UTF_8): CharSequence = {
    getCharSequence(getVarInt(), charset)
  }

  /** Reads a big-endian unsigned short to determine the sequence's length, then reads it. */
  final def getShortCharSequence(charset: Charset = UTF_8): CharSequence = {
    getCharSequence(getUnsignedShort(), charset)
  }

  /** Reads a little-endian unsigned short to determine the sequence's length, then reads it. */
  final def getShortCharSequenceLE(charset: Charset = UTF_8): CharSequence = {
    getCharSequence(getUnsignedShortLE(), charset)
  }


  // ---------------------------------------
  // ----- Get operations for channels -----
  /**
   * Reads exactly `length` bytes and put them into `dst`.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst the destination
   * @param length the number of bytes to read
   */
  def get(dst: GatheringByteChannel, length: Int): Unit = {
    val actual = getSome(dst, length)
    checkComplete(length, actual, "byte")
  }

  /**
   * Reads at most `maxBytes` bytes and put them into `dst`.
   * Returns the actual number of bytes read, possibly zero.
   *
   * @param dst the channel to write to
   * @return the number of bytes read from this NiolInput and written to the channel
   */
  def getSome(dst: GatheringByteChannel, maxBytes: Int = 4096): Int


  // --------------------------------------
  // ----- Get operations for streams -----
  /**
   * Reads exactly `length` bytes and put them into `dst`.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst the destination
   * @param length the number of bytes to read
   */
  def get(dst: OutputStream, length: Int): Unit = {
    val actual = getSome(dst, length)
    checkComplete(length, actual, "byte")
  }

  /**
   * Reads at most `maxLength` bytes and put them into `dst`.
   * Returns the actual number of bytes read, possibly zero.
   *
   * @param dst the stream to write to
   * @return the number of bytes read from this NiolInput and written to the stream
   */
  def getSome(dst: OutputStream, maxLength: Int = 4096): Int


  // ------------------------------------------
  // ----- Get operations for NiolOutputs -----
  /**
   * Reads exactly `length` bytes and put them into `dst`.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst the destination
   * @param length the number of bytes to read
   */
  def get(dst: NiolOutput, length: Int): Unit = dst.put(this, length)

  /**
   * Reads at most `maxLength` bytes and put them into `dst`.
   * Returns the actual number of bytes read, possibly zero.
   *
   * @param dst the stream to write to
   * @return the number of bytes read from this NiolInput and written to the stream
   */
  def getSome(dst: NiolOutput, maxLength: Int = 4096): Int = dst.putSome(this, maxLength)


  // -------------------------------------------
  // ----- Put operations for ByteBuffers ------
  /**
   * Fills the given ByteBuffer.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst the buffer to fill
   */
  def get(dst: ByteBuffer): Unit

  /**
   * Reads at most `dst.remaining()` bytes into `dst`.
   * The buffer's position will be advanced by the number of bytes read from this NiolInput.
   *
   * @param dst the buffer to fill
   */
  def getSome(dst: ByteBuffer): Unit


  // -------------------------------------------
  // ----- Put operations for NiolBuffers ------
  /**
   * Fills the given NiolBuffer.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst the buffer to fill
   */
  def get(dst: NiolBuffer): Unit

  /**
   * Reads at most `dst.readAvail` bytes into `dst`.
   * The buffer's position will be advanced by the number of bytes read from this NiolInput.
   *
   * @param dst the buffer to fill
   */
  def getSome(dst: NiolBuffer): Unit


  // ----------------------------------------------
  // ----- Get operations for arrays of bytes -----
  /**
   * Reads the next `n` bytes.
   * Throws an Exception if there isn't enough data available.
   *
   * @param n the number of bytes to read
   */
  def getBytes(n: Int): Array[Byte] = {
    val array = new Array[Byte](n)
    getBytes(array, 0, n)
    array
  }

  /**
   * Reads the next `dst.length` bytes and put them into `dst`.
   * Throws an exception if there isn't enough data to fill the array.
   *
   * @param dst the array to fill
   */
  def getBytes(dst: Array[Byte]): Unit = getBytes(dst, 0, dst.length)

  /**
   * Reads the next `length` bytes and put them into `dst` at the given offset.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst   the array to fill
   * @param offset the first index to use
   * @param length the number of bytes to read
   */
  def getBytes(dst: Array[Byte], offset: Int, length: Int): Unit = {
    var i = offset
    val l = offset + length
    while (i < l) {
      dst(i) = get()
      i += 1
    }
  }

  /**
   * Reads at most `dst.length` bytes into `dst`.
   * Returns the actual number of bytes read, possibly zero.
   *
   * @param dst the array to fill
   * @return the number of bytes read
   */
  def getSomeBytes(dst: Array[Byte]): Int = getSomeBytes(dst, 0, dst.length)

  /**
   * Reads at most `length` bytes and put them into `dst` at the given offset.
   * Returns the actual number of bytes read, possibly zero.
   *
   * @param dst   the array to fill
   * @param offset the first index to use
   * @param length the maximum number of bytes to read
   * @return the number of bytes read
   */
  def getSomeBytes(dst: Array[Byte], offset: Int, length: Int): Int = {
    var i = offset
    val l = offset + length
    while (i < l && canRead) {
      dst(i) = _get()
      i += 1
    }
    i - offset
  }


  // -----------------------------------------------
  // ----- Get operations for arrays of shorts -----
  /**
   * Reads the next `n` shorts.
   * Uses big-endian for each value.
   * Throws an Exception if there isn't enough data available.
   *
   * @param n the number of shorts to read
   */
  def getShorts(n: Int): Array[Short] = {
    val array = new Array[Short](n)
    getShorts(array, 0, n)
    array
  }

  /**
   * Reads the next `dst.length` shorts and put them into `dst`.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough data to fill the array.
   *
   * @param dst the array to fill
   */
  def getShorts(dst: Array[Short]): Unit = getShorts(dst, 0, dst.length)

  /**
   * Reads the next `length` shorts and put them into `dst` at the given offset.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst   the array to fill
   * @param offset the first index to use
   * @param length the number of shorts to read
   */
  def getShorts(dst: Array[Short], offset: Int, length: Int): Unit = {
    val bytes = getBytes(length * 2)
    ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asShortBuffer().get(dst, offset, length)
  }

  /**
   * Reads the next `n` shorts.
   * Uses little-endian for each value.
   * Throws an Exception if there isn't enough data available.
   *
   * @param n the number of shorts to read
   */
  def getShortsLE(n: Int): Array[Short] = {
    val array = new Array[Short](n)
    getShortsLE(array, 0, n)
    array
  }

  /**
   * Reads the next `dst.length` shorts and put them into `dst`.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough data to fill the array.
   *
   * @param dst the array to fill
   */
  def getShortsLE(dst: Array[Short]): Unit = getShortsLE(dst, 0, dst.length)

  /**
   * Reads the next `length` shorts and put them into `dst` at the given offset.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst   the array to fill
   * @param offset the first index to use
   * @param length the number of shorts to read
   */
  def getShortsLE(dst: Array[Short], offset: Int, length: Int): Unit = {
    val bytes = getBytes(length * 2)
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(dst, offset, length)
  }


  // ---------------------------------------------
  // ----- Get operations for arrays of ints -----
  /**
   * Reads the next `n` ints.
   * Uses big-endian for each value.
   * Throws an Exception if there isn't enough data available.
   *
   * @param n the number of ints to read
   */
  def getInts(n: Int): Array[Int] = {
    val array = new Array[Int](n)
    getInts(array, 0, n)
    array
  }

  /**
   * Reads the next `dst.length` ints and put them into `dst`.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough data to fill the array.
   *
   * @param dst the array to fill
   */
  def getInts(dst: Array[Int]): Unit = getInts(dst, 0, dst.length)

  /**
   * Reads the next `length` ints and put them into `dst` at the given offset.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst   the array to fill
   * @param offset the first index to use
   * @param length the number of ints to read
   */
  def getInts(dst: Array[Int], offset: Int, length: Int): Unit = {
    val bytes = getBytes(length * 2)
    ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asIntBuffer().get(dst, offset, length)
  }

  /**
   * Reads the next `n` ints.
   * Uses little-endian for each value.
   * Throws an Exception if there isn't enough data available.
   *
   * @param n the number of ints to read
   */
  def getIntsLE(n: Int): Array[Int] = {
    val array = new Array[Int](n)
    getIntsLE(array, 0, n)
    array
  }

  /**
   * Reads the next `dst.length` ints and put them into `dst`.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough data to fill the array.
   *
   * @param dst the array to fill
   */
  def getIntsLE(dst: Array[Int]): Unit = getIntsLE(dst, 0, dst.length)

  /**
   * Reads the next `length` ints and put them into `dst` at the given offset.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst   the array to fill
   * @param offset the first index to use
   * @param length the number of ints to read
   */
  def getIntsLE(dst: Array[Int], offset: Int, length: Int): Unit = {
    val bytes = getBytes(length * 2)
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(dst, offset, length)
  }


  // ----------------------------------------------
  // ----- Get operations for arrays of longs -----
  /**
   * Reads the next `n` longs.
   * Uses big-endian for each value.
   * Throws an Exception if there isn't enough data available.
   *
   * @param n the number of longs to read
   */
  def getLongs(n: Int): Array[Long] = {
    val array = new Array[Long](n)
    getLongs(array, 0, n)
    array
  }

  /**
   * Reads the next `dst.length` longs and put them into `dst`.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough data to fill the array.
   *
   * @param dst the array to fill
   */
  def getLongs(dst: Array[Long]): Unit = getLongs(dst, 0, dst.length)

  /**
   * Reads the next `length` longs and put them into `dst` at the given offset.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst   the array to fill
   * @param offset the first index to use
   * @param length the number of longs to read
   */
  def getLongs(dst: Array[Long], offset: Int, length: Int): Unit = {
    val bytes = getBytes(length * 2)
    ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asLongBuffer().get(dst, offset, length)
  }

  /**
   * Reads the next `n` longs.
   * Uses little-endian for each value.
   * Throws an Exception if there isn't enough data available.
   *
   * @param n the number of longs to read
   */
  def getLongsLE(n: Int): Array[Long] = {
    val array = new Array[Long](n)
    getLongsLE(array, 0, n)
    array
  }

  /**
   * Reads the next `dst.length` longs and put them into `dst`.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough data to fill the array.
   *
   * @param dst the array to fill
   */
  def getLongsLE(dst: Array[Long]): Unit = getLongsLE(dst, 0, dst.length)

  /**
   * Reads the next `length` longs and put them into `dst` at the given offset.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst   the array to fill
   * @param offset the first index to use
   * @param length the number of longs to read
   */
  def getLongsLE(dst: Array[Long], offset: Int, length: Int): Unit = {
    val bytes = getBytes(length * 2)
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer().get(dst, offset, length)
  }


  // -----------------------------------------------
  // ----- Get operations for arrays of floats -----
  /**
   * Reads the next `n` floats.
   * Uses big-endian for each value.
   * Throws an Exception if there isn't enough data available.
   *
   * @param n the number of floats to read
   */
  def getFloats(n: Int): Array[Float] = {
    val array = new Array[Float](n)
    getFloats(array, 0, n)
    array
  }

  /**
   * Reads the next `dst.length` floats and put them into `dst`.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough data to fill the array.
   *
   * @param dst the array to fill
   */
  def getFloats(dst: Array[Float]): Unit = getFloats(dst, 0, dst.length)

  /**
   * Reads the next `length` floats and put them into `dst` at the given offset.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst   the array to fill
   * @param offset the first index to use
   * @param length the number of floats to read
   */
  def getFloats(dst: Array[Float], offset: Int, length: Int): Unit = {
    val bytes = getBytes(length * 2)
    ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asFloatBuffer().get(dst, offset, length)
  }

  /**
   * Reads the next `n` floats.
   * Uses little-endian for each value.
   * Throws an Exception if there isn't enough data available.
   *
   * @param n the number of floats to read
   */
  def getFloatsLE(n: Int): Array[Float] = {
    val array = new Array[Float](n)
    getFloatsLE(array, 0, n)
    array
  }

  /**
   * Reads the next `dst.length` floats and put them into `dst`.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough data to fill the array.
   *
   * @param dst the array to fill
   */
  def getFloatsLE(dst: Array[Float]): Unit = getFloatsLE(dst, 0, dst.length)

  /**
   * Reads the next `length` floats and put them into `dst` at the given offset.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst   the array to fill
   * @param offset the first index to use
   * @param length the number of floats to read
   */
  def getFloatsLE(dst: Array[Float], offset: Int, length: Int): Unit = {
    val bytes = getBytes(length * 2)
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(dst, offset, length)
  }


  // ------------------------------------------------
  // ----- Get operations for arrays of doubles -----
  /**
   * Reads the next `n` doubles.
   * Uses big-endian for each value.
   * Throws an Exception if there isn't enough data available.
   *
   * @param n the number of doubles to read
   */
  def getDoubles(n: Int): Array[Double] = {
    val array = new Array[Double](n)
    getDoubles(array, 0, n)
    array
  }

  /**
   * Reads the next `dst.length` doubles and put them into `dst`.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough data to fill the array.
   *
   * @param dst the array to fill
   */
  def getDoubles(dst: Array[Double]): Unit = getDoubles(dst, 0, dst.length)

  /**
   * Reads the next `length` doubles and put them into `dst` at the given offset.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst   the array to fill
   * @param offset the first index to use
   * @param length the number of doubles to read
   */
  def getDoubles(dst: Array[Double], offset: Int, length: Int): Unit = {
    val bytes = getBytes(length * 2)
    ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asDoubleBuffer().get(dst, offset, length)
  }

  /**
   * Reads the next `n` doubles.
   * Uses little-endian for each value.
   * Throws an Exception if there isn't enough data available.
   *
   * @param n the number of doubles to read
   */
  def getDoublesLE(n: Int): Array[Double] = {
    val array = new Array[Double](n)
    getDoublesLE(array, 0, n)
    array
  }

  /**
   * Reads the next `dst.length` doubles and put them into `dst`.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough data to fill the array.
   *
   * @param dst the array to fill
   */
  def getDoublesLE(dst: Array[Double]): Unit = getDoublesLE(dst, 0, dst.length)

  /**
   * Reads the next `length` doubles and put them into `dst` at the given offset.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst   the array to fill
   * @param offset the first index to use
   * @param length the number of doubles to read
   */
  def getDoublesLE(dst: Array[Double], offset: Int, length: Int): Unit = {
    val bytes = getBytes(length * 2)
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asDoubleBuffer().get(dst, offset, length)
  }
}

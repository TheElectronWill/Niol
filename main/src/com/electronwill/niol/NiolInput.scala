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
trait NiolInput {
  // ------------------------------
  // ----- Input information -----
  /***
   * Checks if a byte can be read from this NiolInput.
   *
   * @return true if at least one byte can be read, false otherwise
   */
  def isReadable: Boolean

  /**
   * Checks if this input is closed or has definitively reached its end.
   * If `isEnded == true` then `isReadable == false`.
   *
   * @return true if it's closed or has definitively reached its end, false otherwise
   */
  def isEnded: Boolean


  // --------------------------------
  // ----- Protected operations -----
  /** Implements read without necessarily checking for available data. */
  protected[this] def _read(): Byte

  /** Throws an exception if the operation is incomplete */
  protected[this] def checkComplete(expected: Int, actual: Int, v: String = "value"): Unit = {
    if (actual != expected) throw new IncompleteReadException(expected, actual, v)
  }


  // ---------------------------------------------
  // ----- Primitive single-value operations -----
  /** Reads a byte */
  def read(): Byte = {
    if (isReadable) _read()
    else throw new NotEnoughDataException(1, 0)
  }

  /**
   * Attempts to read a byte. If the input is empty, returns -1. Otherwise, returns the byte value
   * as an int in the range 0-255.
   *
   * @return the byte value in range 0-255, or -1 if the input is empty
   */
  def tryRead(): Int = {
    if (isReadable) {
      _read().toInt
    } else {
      -1
    }
  }

  /** Reads a boolean */
  def readBoolean(): Boolean = readBoolF(0)

  /** Reads a boolean. */
  def readBool(): Boolean = readBoolean()

  /** Reads one byte and returns true if it's equal to the given value, false otherwise. */
  def readBoolT(trueValue: Byte): Boolean = read() == trueValue

  /** Reads one byte and returns false if it's equal to the given value, true otherwise. */
  def readBoolF(falseValue: Byte): Boolean = read() != falseValue

  /** Reads a byte */
  final def readByte(): Byte = read()

  /** Reads a big-endian short */
  def readShort(): Short = (readByte() << 8 | readByte()).toShort

  /** Reads a little-endian short */
  def readShortLE(): Short = (readByte() | readByte() << 8).toShort

  /** Reads a big-endian char */
  def readChar(): Char = readUnsignedShort().toChar

  /** Reads a little-endian char */
  def readCharLE(): Char = readUnsignedShortLE().toChar

  /** Reads a big-endian 3-bytes integer */
  def readMedium(): Int = readByte() << 16 | readByte() << 8 | readByte()

  /** Reads a little-endian 3-bytes integer */
  def readMediumLE(): Int = readByte() | readByte() << 8 | readByte() << 16

  /** Reads a big-endian 4-bytes integer */
  def readInt(): Int = readByte() << 24 | readByte() << 16 | readByte() << 8 | readByte()

  /** Reads a little-endian 4-bytes integer */
  def readIntLE(): Int = readByte() | readByte() << 8 | readByte() << 16 | readByte() << 24

  /** Reads a big-endian 8-bytes integer */
  def readLong(): Long = {
    val b = readBytes(8)
    b(0) << 56 | b(1) << 48 | b(2) << 40 | b(3) << 32 | b(4) << 24 | b(5) << 16 | b(6) << 8 | b(7)
  }

  /** Reads a little-endian 8-bytes integer */
  def readLongLE(): Long = {
    val b = readBytes(8)
    b(0) | b(1) << 8 | b(2) << 16 | b(3) << 24 | b(4) << 32 | b(5) << 40 | b(6) << 48 | b(7) << 56
  }

  /** Reads a big-endian 4-bytes float */
  def readFloat(): Float = java.lang.Float.intBitsToFloat(readInt())

  /** Reads a little-endian 4-bytes float */
  def readFloatLE(): Float = java.lang.Float.intBitsToFloat(readIntLE())

  /** Reads a big-endian 8-bytes double */
  def readDouble(): Double = java.lang.Double.longBitsToDouble(readLong())

  /** Reads a little-endian 8-bytes double */
  def readDoubleLE(): Double = java.lang.Double.longBitsToDouble(readLongLE())

  /** Reads an unsigned byte as an int */
  final def readUnsignedByte(): Int = readByte() & 0xFF

  /** Reads a big-endian unsigned short as an int */
  final def readUnsignedShort(): Int = readShort() & 0xFFFF

  /** Reads a little-endian unsigned short as an int */
  final def readUnsignedShortLE(): Int = readShortLE() & 0xFFFF

  /** Reads a big-endian unsigned medium as an int */
  final def readUnsignedMedium(): Int = readMedium() & 0xFFFFFF

  /** Reads a little-endian unsigned medium as an int */
  final def readUnsignedMediumLE(): Int = readMediumLE() & 0xFFFFFF

  /** Reads a big-endian unsigned int as a long */
  final def readUnsignedInt(): Long = readInt() & 0xFFFFFFFF

  /** Reads a little-endian unsigned int as a long */
  final def readUnsignedIntLE(): Long = readIntLE() & 0xFFFFFFFF

  /** Reads a big-endian 16-bytes UUID */
  final def readUUID(): UUID = new UUID(readLong(), readLong())


  // -----------------------------------------------
  // ----- Variable-length integers operations -----
  /** Reads a variable-length int using the normal/unsigned encoding. */
  def readVarInt(maxBytes: Int = 5): Int = {
    val maxShift = maxBytes * 7
    var shift: Int = 0
    var result: Int = 0
    var read: Int = 0xFF
    while ((read & 0x80) != 0 && shift < maxShift) {
      if (!isReadable) throw new IncompleteReadException(1, "VarLong")
      read = _read()
      result |= ((read & 0x7F) << shift)
      shift += 7
    }
    result
  }

  /** Reads a variable-length long using the normal/unsigned encoding. */
  def readVarLong(maxBytes: Int = 10): Long = {
    val maxShift = maxBytes * 7
    var shift: Int = 0
    var result: Long = 0
    var read: Int = 0xFF
    while ((read & 0x80) != 0 && shift < maxShift) {
      if (!isReadable) throw new IncompleteReadException(1, "VarLong")
      read = _read()
      result |= ((read & 0x7F) << shift)
      shift += 7
    }
    result
  }

  /** Reads a variable-length int using the signed/zig-zag encoding. */
  final def readSVarIntZigZag(maxBytes: Int = 5): Int = {
    val n = readVarInt(maxBytes)
    (n >> 1) ^ -(n & 1)
  }

  /** Reads a variable-length long using the signed/zig-zag encoding. */
  final def readSVarLongZigZag(maxBytes: Int = 10): Long = {
    val n = readVarLong(maxBytes)
    (n >> 1) ^ -(n & 1)
  }

  /** Reads the next `bytesLength` bytes as a String encoded with the given charset. */
  final def readString(bytesLength: Int, charset: Charset = UTF_8): String = {
    readCharSequence(bytesLength, charset).toString
  }

  /** Reads a VarInt to determine the string's length, then reads the string. */
  final def readVarString(charset: Charset = UTF_8): String = {
    readVarCharSequence(charset).toString
  }

  /** Reads a big-endian unsigned short to determine the string's length, then reads it. */
  final def readShortString(charset: Charset = UTF_8): String = {
    readShortCharSequence(charset).toString
  }

  /** Reads a little-endian unsigned short to determine the string's length, then reads it. */
  final def readShortStringLE(charset: Charset = UTF_8): String = {
    readShortCharSequenceLE(charset).toString
  }

  /** Reads the next `bytesLength` bytes as a CharSequence encoded with the given charset. */
  final def readCharSequence(bytesLength: Int, charset: Charset = UTF_8): CharSequence = {
    charset.decode(ByteBuffer.wrap(readBytes(bytesLength)))
  }

  /** Reads a VarInt to determine the sequence's length, then reads the CharSequence. */
  final def readVarCharSequence(charset: Charset = UTF_8): CharSequence = {
    readCharSequence(readVarInt(), charset)
  }

  /** Reads a big-endian unsigned short to determine the sequence's length, then reads it. */
  final def readShortCharSequence(charset: Charset = UTF_8): CharSequence = {
    readCharSequence(readUnsignedShort(), charset)
  }

  /** Reads a little-endian unsigned short to determine the sequence's length, then reads it. */
  final def readShortCharSequenceLE(charset: Charset = UTF_8): CharSequence = {
    readCharSequence(readUnsignedShortLE(), charset)
  }


  // ---------------------------------------
  // ----- Read operations for channels -----
  /**
   * Reads exactly `length` bytes and put them into `dst`.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst the destination
   * @param length the number of bytes to read
   */
  def read(dst: GatheringByteChannel, length: Int): Unit = {
    val actual = readSome(dst, length)
    checkComplete(length, actual, "byte")
  }

  /**
   * Reads at most `maxBytes` bytes and put them into `dst`.
   * Returns the actual number of bytes read, possibly zero.
   *
   * @param dst the channel to write to
   * @return the number of bytes read
   */
  def readSome(dst: GatheringByteChannel, maxBytes: Int = 4096): Int


  // --------------------------------------
  // ----- Read operations for streams -----
  /**
   * Reads exactly `length` bytes and put them into `dst`.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst the destination
   * @param length the number of bytes to read
   */
  def read(dst: OutputStream, length: Int): Unit = {
    val actual = readSome(dst, length)
    checkComplete(length, actual, "byte")
  }

  /**
   * Reads at most `maxLength` bytes and put them into `dst`.
   * Returns the actual number of bytes read, possibly zero.
   *
   * @param dst the stream to write to
   * @return the number of bytes read from this NiolInput and written to the stream
   */
  def readSome(dst: OutputStream, maxLength: Int = 4096): Int


  // ------------------------------------------
  // ----- Read operations for NiolOutputs -----
  /**
   * Reads exactly `length` bytes and put them into `dst`.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst the destination
   * @param length the number of bytes to read
   */
  def read(dst: NiolOutput, length: Int): Unit

  /**
   * Reads at most `maxLength` bytes and put them into `dst`.
   * Returns the actual number of bytes read, possibly zero.
   *
   * @param dst the stream to write to
   * @return the number of bytes read from this NiolInput and written to the stream
   */
  def readSome(dst: NiolOutput, maxLength: Int = 4096): Int

  // -------------------------------------------
  // ----- Put operations for NiolBuffers ------
  /**
   * Fills the given NiolBuffer.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst the buffer to fill
   */
  def read(dst: NiolBuffer): Unit = read(dst, dst.writableBytes)


  // -------------------------------------------
  // ----- Put operations for ByteBuffers ------
  /**
   * Fills the given ByteBuffer.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst the buffer to fill
   */
  def read(dst: ByteBuffer): Unit

  /**
   * Reads at most `dst.remaining()` bytes into `dst`.
   * The buffer's position will be advanced by the number of bytes read from this NiolInput.
   *
   * @param dst the buffer to fill
   */
  def readSome(dst: ByteBuffer): Unit


  // ----------------------------------------------
  // ----- Read operations for arrays of bytes -----
  /**
   * Reads the next `n` bytes.
   * Throws an Exception if there isn't enough data available.
   *
   * @param n the number of bytes to read
   */
  def readBytes(n: Int): Array[Byte] = {
    val array = new Array[Byte](n)
    readBytes(array, 0, n)
    array
  }

  /**
   * Reads the next `dst.length` bytes and put them into `dst`.
   * Throws an exception if there isn't enough data to fill the array.
   *
   * @param dst the array to fill
   */
  def readBytes(dst: Array[Byte]): Unit = readBytes(dst, 0, dst.length)

  /**
   * Reads the next `length` bytes and put them into `dst` at the given offset.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst   the array to fill
   * @param offset the first index to use
   * @param length the number of bytes to read
   */
  def readBytes(dst: Array[Byte], offset: Int, length: Int): Unit = {
    var i = offset
    val l = offset + length
    while (i < l) {
      dst(i) = read()
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
  def readSomeBytes(dst: Array[Byte]): Int = readSomeBytes(dst, 0, dst.length)

  /**
   * Reads at most `length` bytes and put them into `dst` at the given offset.
   * Returns the actual number of bytes read, possibly zero.
   *
   * @param dst   the array to fill
   * @param offset the first index to use
   * @param length the maximum number of bytes to read
   * @return the number of bytes read
   */
  def readSomeBytes(dst: Array[Byte], offset: Int, length: Int): Int = {
    var i = offset
    val l = offset + length
    while (i < l && isReadable) {
      dst(i) = _read()
      i += 1
    }
    i - offset
  }


  // -----------------------------------------------
  // ----- Read operations for arrays of shorts -----
  /**
   * Reads the next `n` shorts.
   * Uses big-endian for each value.
   * Throws an Exception if there isn't enough data available.
   *
   * @param n the number of shorts to read
   */
  def readShorts(n: Int): Array[Short] = {
    val array = new Array[Short](n)
    readShorts(array, 0, n)
    array
  }

  /**
   * Reads the next `dst.length` shorts and put them into `dst`.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough data to fill the array.
   *
   * @param dst the array to fill
   */
  def readShorts(dst: Array[Short]): Unit = readShorts(dst, 0, dst.length)

  /**
   * Reads the next `length` shorts and put them into `dst` at the given offset.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst   the array to fill
   * @param offset the first index to use
   * @param length the number of shorts to read
   */
  def readShorts(dst: Array[Short], offset: Int, length: Int): Unit = {
    val bytes = readBytes(length * 2)
    ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asShortBuffer().get(dst, offset, length)
  }

  /**
   * Reads the next `n` shorts.
   * Uses little-endian for each value.
   * Throws an Exception if there isn't enough data available.
   *
   * @param n the number of shorts to read
   */
  def readShortsLE(n: Int): Array[Short] = {
    val array = new Array[Short](n)
    readShortsLE(array, 0, n)
    array
  }

  /**
   * Reads the next `dst.length` shorts and put them into `dst`.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough data to fill the array.
   *
   * @param dst the array to fill
   */
  def readShortsLE(dst: Array[Short]): Unit = readShortsLE(dst, 0, dst.length)

  /**
   * Reads the next `length` shorts and put them into `dst` at the given offset.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst   the array to fill
   * @param offset the first index to use
   * @param length the number of shorts to read
   */
  def readShortsLE(dst: Array[Short], offset: Int, length: Int): Unit = {
    val bytes = readBytes(length * 2)
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(dst, offset, length)
  }


  // ---------------------------------------------
  // ----- Read operations for arrays of ints -----
  /**
   * Reads the next `n` ints.
   * Uses big-endian for each value.
   * Throws an Exception if there isn't enough data available.
   *
   * @param n the number of ints to read
   */
  def readInts(n: Int): Array[Int] = {
    val array = new Array[Int](n)
    readInts(array, 0, n)
    array
  }

  /**
   * Reads the next `dst.length` ints and put them into `dst`.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough data to fill the array.
   *
   * @param dst the array to fill
   */
  def readInts(dst: Array[Int]): Unit = readInts(dst, 0, dst.length)

  /**
   * Reads the next `length` ints and put them into `dst` at the given offset.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst   the array to fill
   * @param offset the first index to use
   * @param length the number of ints to read
   */
  def readInts(dst: Array[Int], offset: Int, length: Int): Unit = {
    val bytes = readBytes(length * 2)
    ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asIntBuffer().get(dst, offset, length)
  }

  /**
   * Reads the next `n` ints.
   * Uses little-endian for each value.
   * Throws an Exception if there isn't enough data available.
   *
   * @param n the number of ints to read
   */
  def readIntsLE(n: Int): Array[Int] = {
    val array = new Array[Int](n)
    readIntsLE(array, 0, n)
    array
  }

  /**
   * Reads the next `dst.length` ints and put them into `dst`.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough data to fill the array.
   *
   * @param dst the array to fill
   */
  def readIntsLE(dst: Array[Int]): Unit = readIntsLE(dst, 0, dst.length)

  /**
   * Reads the next `length` ints and put them into `dst` at the given offset.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst   the array to fill
   * @param offset the first index to use
   * @param length the number of ints to read
   */
  def readIntsLE(dst: Array[Int], offset: Int, length: Int): Unit = {
    val bytes = readBytes(length * 2)
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(dst, offset, length)
  }


  // ----------------------------------------------
  // ----- Read operations for arrays of longs -----
  /**
   * Reads the next `n` longs.
   * Uses big-endian for each value.
   * Throws an Exception if there isn't enough data available.
   *
   * @param n the number of longs to read
   */
  def readLongs(n: Int): Array[Long] = {
    val array = new Array[Long](n)
    readLongs(array, 0, n)
    array
  }

  /**
   * Reads the next `dst.length` longs and put them into `dst`.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough data to fill the array.
   *
   * @param dst the array to fill
   */
  def readLongs(dst: Array[Long]): Unit = readLongs(dst, 0, dst.length)

  /**
   * Reads the next `length` longs and put them into `dst` at the given offset.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst   the array to fill
   * @param offset the first index to use
   * @param length the number of longs to read
   */
  def readLongs(dst: Array[Long], offset: Int, length: Int): Unit = {
    val bytes = readBytes(length * 2)
    ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asLongBuffer().get(dst, offset, length)
  }

  /**
   * Reads the next `n` longs.
   * Uses little-endian for each value.
   * Throws an Exception if there isn't enough data available.
   *
   * @param n the number of longs to read
   */
  def readLongsLE(n: Int): Array[Long] = {
    val array = new Array[Long](n)
    readLongsLE(array, 0, n)
    array
  }

  /**
   * Reads the next `dst.length` longs and put them into `dst`.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough data to fill the array.
   *
   * @param dst the array to fill
   */
  def readLongsLE(dst: Array[Long]): Unit = readLongsLE(dst, 0, dst.length)

  /**
   * Reads the next `length` longs and put them into `dst` at the given offset.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst   the array to fill
   * @param offset the first index to use
   * @param length the number of longs to read
   */
  def readLongsLE(dst: Array[Long], offset: Int, length: Int): Unit = {
    val bytes = readBytes(length * 2)
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer().get(dst, offset, length)
  }


  // -----------------------------------------------
  // ----- Read operations for arrays of floats -----
  /**
   * Reads the next `n` floats.
   * Uses big-endian for each value.
   * Throws an Exception if there isn't enough data available.
   *
   * @param n the number of floats to read
   */
  def readFloats(n: Int): Array[Float] = {
    val array = new Array[Float](n)
    readFloats(array, 0, n)
    array
  }

  /**
   * Reads the next `dst.length` floats and put them into `dst`.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough data to fill the array.
   *
   * @param dst the array to fill
   */
  def readFloats(dst: Array[Float]): Unit = readFloats(dst, 0, dst.length)

  /**
   * Reads the next `length` floats and put them into `dst` at the given offset.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst   the array to fill
   * @param offset the first index to use
   * @param length the number of floats to read
   */
  def readFloats(dst: Array[Float], offset: Int, length: Int): Unit = {
    val bytes = readBytes(length * 2)
    ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asFloatBuffer().get(dst, offset, length)
  }

  /**
   * Reads the next `n` floats.
   * Uses little-endian for each value.
   * Throws an Exception if there isn't enough data available.
   *
   * @param n the number of floats to read
   */
  def readFloatsLE(n: Int): Array[Float] = {
    val array = new Array[Float](n)
    readFloatsLE(array, 0, n)
    array
  }

  /**
   * Reads the next `dst.length` floats and put them into `dst`.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough data to fill the array.
   *
   * @param dst the array to fill
   */
  def readFloatsLE(dst: Array[Float]): Unit = readFloatsLE(dst, 0, dst.length)

  /**
   * Reads the next `length` floats and put them into `dst` at the given offset.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst   the array to fill
   * @param offset the first index to use
   * @param length the number of floats to read
   */
  def readFloatsLE(dst: Array[Float], offset: Int, length: Int): Unit = {
    val bytes = readBytes(length * 2)
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(dst, offset, length)
  }


  // ------------------------------------------------
  // ----- Read operations for arrays of doubles -----
  /**
   * Reads the next `n` doubles.
   * Uses big-endian for each value.
   * Throws an Exception if there isn't enough data available.
   *
   * @param n the number of doubles to read
   */
  def readDoubles(n: Int): Array[Double] = {
    val array = new Array[Double](n)
    readDoubles(array, 0, n)
    array
  }

  /**
   * Reads the next `dst.length` doubles and put them into `dst`.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough data to fill the array.
   *
   * @param dst the array to fill
   */
  def readDoubles(dst: Array[Double]): Unit = readDoubles(dst, 0, dst.length)

  /**
   * Reads the next `length` doubles and put them into `dst` at the given offset.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst   the array to fill
   * @param offset the first index to use
   * @param length the number of doubles to read
   */
  def readDoubles(dst: Array[Double], offset: Int, length: Int): Unit = {
    val bytes = readBytes(length * 2)
    ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asDoubleBuffer().get(dst, offset, length)
  }

  /**
   * Reads the next `n` doubles.
   * Uses little-endian for each value.
   * Throws an Exception if there isn't enough data available.
   *
   * @param n the number of doubles to read
   */
  def readDoublesLE(n: Int): Array[Double] = {
    val array = new Array[Double](n)
    readDoublesLE(array, 0, n)
    array
  }

  /**
   * Reads the next `dst.length` doubles and put them into `dst`.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough data to fill the array.
   *
   * @param dst the array to fill
   */
  def readDoublesLE(dst: Array[Double]): Unit = readDoublesLE(dst, 0, dst.length)

  /**
   * Reads the next `length` doubles and put them into `dst` at the given offset.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough data available.
   *
   * @param dst   the array to fill
   * @param offset the first index to use
   * @param length the number of doubles to read
   */
  def readDoublesLE(dst: Array[Double], offset: Int, length: Int): Unit = {
    val bytes = readBytes(length * 2)
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asDoubleBuffer().get(dst, offset, length)
  }
}

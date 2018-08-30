package com.electronwill.niol

import java.io.InputStream
import java.nio.channels.ScatteringByteChannel
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.{ByteBuffer, CharBuffer}
import java.util.UUID

import com.electronwill.niol.buffer.NiolBuffer

/**
 * An advanced output.
 *
 * @author TheElectronWill
 */
abstract class NiolOutput {
  // ------------------------------
  // ----- Output information -----
  /**
   * Returns the number of bytes that can be written to this NiolOutput.
   * The result may be underestimated except when it's zero. The result is always >= 0.
   *
   * @return n > 0 if can write n bytes, 0 if EOS or full buffer
   */
  def writeAvail: Int

  /***
   * Returns true if at least one byte can be written to this NiolOutput.
   *
   * @return true if at least one byte can be written, false otherwise
   */
  def canWrite: Boolean = (writeAvail >= 0)

  // --------------------------------
  // ----- Protected operations -----
  /** Implements put without necessarily checking for available space. */
  protected[this] def _put(b: Byte): Unit

  /** Implements put without necessarily checking for available space. */
  protected[this] final def _put(b: Int): Unit =_put(b.toByte)

  /** Implements put without necessarily checking for available space. */
  protected[this] final def _put(b: Long): Unit =_put(b.toByte)

  /** Implements put without necessarily checking for available space. */
  protected[this] def _put(from: Array[Byte], off: Int, len: Int): Unit

  /** Implements put without necessarily checking for available space. */
  protected[this] def _put(from: ByteBuffer, len: Int): Unit

  /** Implements put without necessarily checking for available space. */
  protected[this] def _put(from: NiolBuffer, len: Int): Unit

  /** Checks if at least `n` bytes can be written */
  protected[this] def check(nValues: Int, n: Int): Unit = {
    val avail = writeAvail
    if (avail < n) throw new NotEnoughSpaceException(nValues, n, avail)
  }

  /** Checks if at least `n` bytes can be written */
  protected[this] def checkAvail(n: Int): Unit = {
    val avail = writeAvail
    if (avail < n) throw new NotEnoughSpaceException(n, avail)
  }

  /** Throws an exception if the operation is incomplete */
  protected[this] def checkComplete(expected: Int, actual: Int, v: String = "value"): Unit = {
    if (actual != expected) throw new IncompletePutException(expected, actual, v)
  }

  // ---------------------------------------------
  // ----- Primitive single-value operations -----
  /**
   * Writes a byte.
   *
   * @param b the byte to write
   */
  def put(b: Byte): Unit = {
    if (canWrite) _put(b)
    else throw new NotEnoughSpaceException(1, 0)
  }

  /**
   * Attempts to put a byte. Returns false if the output is full.
   *
   * @param b the byte to put
   * @return true if the byte has been put, false if the output is full
   */
  def tryPut(b: Byte): Boolean = {
    if (canWrite) {
      _put(b)
      true
    } else {
      false
    }
  }

  /**
   * Writes a boolean. The default behavior is as follow:
   * If the value is true, writes the byte `1`. If it's false, writes the byte `0`.
   *
   * @param boolean the boolean to write
   */
  def putBoolean(boolean: Boolean): Unit = putBool(boolean, 1, 0)

  /**
   * Writes a boolean. The default behavior is as follow:
   * If it's true, writes the byte `1`. If it's false, writes the byte `0`.
   *
   * @param bool the boolean to write
   */
  def putBool(bool: Boolean): Unit = putBool(bool, 1, 0)

  /**
   * Writes a boolean. If it's true, writes `trueValue`. If it's false, writes `falseValue`.
   *
   * @param bool       the boolean to write
   * @param trueValue  the byte to write if the boolean is true
   * @param falseValue the byte to write if the boolean is false
   */
  def putBool(bool: Boolean, trueValue: Byte, falseValue: Byte): Unit = {
    checkAvail(1)
    _put(if (bool) trueValue else falseValue)
  }

  /**
   * Writes a byte.
   *
   * @param b the byte to write
   */
  final def putByte(b: Byte): Unit = put(b)

  /**
   * Writes the least significant byte of an int.
   *
   * @param i the value to write
   */
  final def putByte(i: Int): Unit = put(i.toByte)

  /**
   * Writes the least significant byte of a long.
   *
   * @param l the value to write
   */
  final def putByte(l: Long): Unit = put(l.toByte)

  /**
   * Writes a big-endian short.
   *
   * @param s the value to write
   */
  final def putShort(s: Short): Unit = putShort(s.toInt)
  // Note: bit shifting operations are only applied on ints and longs, the value is converted if
  // it's a smaller number. Therefore we delegate putShort(Short) to putShort(Int) to make the
  // short -> int conversion explicit.

  /**
   * Writes a little-endian short.
   *
   * @param s the value to write
   */
  final def putShortLE(s: Short): Unit = putShortLE(s.toInt)

  /**
   * Writes a big-endian short.
   *
   * @param i the value to write
   */
  def putShort(i: Int): Unit = {
    checkAvail(2)
    _put(i >> 8)
    _put(i)
  }

  /**
   * Writes a little-endian short.
   *
   * @param i the value to write
   */
  def putShortLE(i: Int): Unit = {
    checkAvail(2)
    _put(i)
    _put(i >> 8)
  }

  /**
   * Writes a big-endian char.
   *
   * @param c the value to write
   */
  final def putChar(c: Char): Unit = putShort(c.toInt & 0xFFFF)

  /**
   * Writes a little-endian char.
   *
   * @param c the value to write
   */
  final def putCharLE(c: Char): Unit = putShortLE(c.toInt & 0xFFFF)

  /**
   * Writes a big-endian 3-bytes integer.
   * @param m the value to write
   */
  def putMedium(m: Int): Unit = {
    checkAvail(3)
    _put(m >> 16)
    _put(m >> 8)
    _put(m)
  }

  /**
   * Writes a little-endian 3-bytes integer.
   *
   * @param m the value to write
   */
  def putMediumLE(m: Int): Unit = {
    checkAvail(3)
    _put(m)
    _put(m >> 8)
    _put(m >> 16)
  }

  /**
   * Writes a big-endian 4-bytes integer.
   *
   * @param i the value to write
   */
  def putInt(i: Int): Unit = {
    checkAvail(4)
    _put(i >> 24)
    _put(i >> 16)
    _put(i >> 8)
    _put(i)
  }

  /**
   * Writes a little-endian 4-bytes integer.
   *
   * @param i the value to write
   */
  def putIntLE(i: Int): Unit = {
    checkAvail(4)
    _put(i)
    _put(i >> 8)
    _put(i >> 16)
    _put(i >> 24)
  }

  /**
   * Writes a big-endian 8-bytes integer.
   *
   * @param l the value to write
   */
  def putLong(l: Long): Unit = {
    checkAvail(8)
    _put(l >> 56)
    _put(l >> 48)
    _put(l >> 40)
    _put(l >> 32)
    _put(l >> 24)
    _put(l >> 16)
    _put(l >> 8)
    _put(l)
  }

  /**
   * Writes a little-endian 8-bytes integer.
   *
   * @param l the value to write
   */
  def putLongLE(l: Long): Unit = {
    checkAvail(8)
    _put(l)
    _put(l >> 8)
    _put(l >> 16)
    _put(l >> 24)
    _put(l >> 32)
    _put(l >> 40)
    _put(l >> 48)
    _put(l >> 56)
  }

  /**
   * Writes a big-endian 4-bytes float.
   *
   * @param f the value to write
   */
  def putFloat(f: Float): Unit = {
    checkAvail(4)
    val i = java.lang.Float.floatToIntBits(f)
    _put(i >> 24)
    _put(i >> 16)
    _put(i >> 8)
    _put(i)
  }

  /**
   * Writes a big-endian 4-bytes float.
   *
   * @param f the value to write
   */
  final def putFloat(f: Double): Unit = putFloat(f.toFloat)

  /**
   * Writes a little-endian 4-bytes float.
   *
   * @param f the value to write
   */
  def putFloatLE(f: Float): Unit = {
    checkAvail(4)
    val i =java.lang.Float.floatToIntBits(f)
    _put(i)
    _put(i >> 8)
    _put(i >> 16)
    _put(i >> 24)
  }

  /**
   * Writes a little-endian 4-bytes float.
   *
   * @param f the value to write
   */
  final def putFloatLE(f: Double): Unit = putFloatLE(f.toFloat)

  /**
   * Writes a big-endian 8-bytes double.
   *
   * @param d the value to write
   */
  def putDouble(d: Double): Unit = {
    checkAvail(8)
    val l = java.lang.Double.doubleToLongBits(d)
    _put(l >> 56)
    _put(l >> 48)
    _put(l >> 40)
    _put(l >> 32)
    _put(l >> 24)
    _put(l >> 16)
    _put(l >> 8)
    _put(l)
  }

  /**
   * Writes a little-endian 8-bytes double.
   *
   * @param d the value to write
   */
  def putDoubleLE(d: Double): Unit = {
    checkAvail(8)
    val l = java.lang.Double.doubleToLongBits(d)
    _put(l)
    _put(l >> 8)
    _put(l >> 16)
    _put(l >> 24)
    _put(l >> 32)
    _put(l >> 40)
    _put(l >> 48)
    _put(l >> 56)
  }

  /**
   * Writes a UUID as two 8-bytes big-endian integers, most significant bits first.
   *
   * @param uuid the value to write
   */
  final def putUUID(uuid: UUID): Unit = {
    checkAvail(16)
    putLong(uuid.getMostSignificantBits)
    putLong(uuid.getLeastSignificantBits)
  }

  // -----------------------------------------------
  // ----- Variable-length integers operations -----
  /**
   * Writes a variable-length int using the normal/unsigned encoding.
   *
   * @param i the value to write
   */
  def putVarInt(i: Int): Unit = {
    var value = i
    do {
      var bits7 = value & 0x7F
      value >>>= 7
      if (value != 0) {
        bits7 |= 0x80
      }
      if (!canWrite) throw new IncompletePutException(1, "VarInt")
      _put(bits7)
    } while (value != 0)
  }

  /**
   * Writes a variable-length long using the normal/unsigned encoding.
   *
   * @param l the value to write
   */
  def putVarLong(l: Long): Unit = {
    var value = l
    do {
      var bits7 = value & 0x7F
      value >>>= 7
      if (value != 0) {
        bits7 |= 0x80
      }
      if (!canWrite) throw new IncompletePutException(1, "VarLong")
      _put(bits7)
    } while (value != 0)
  }

  /**
   * Writes a variable-length int using the signed/zig-zag encoding.
   *
   * @param n the value to write
   */
  final def putSVarIntZigZag(n: Int): Unit = putVarInt((n << 1) ^ (n >> 31))

  /**
   * Writes a variable-length long using the signed/zig-zag encoding.
   *
   * @param n the value to write
   */
  final def putSVarLongZigZag(n: Long): Unit = putVarLong((n << 1) ^ (n >> 63))

  // ----------------------------------------------
  // ----- String and CharSequence operations -----
  /**
   * Writes a String with the given charset. The written data isn't prefixed by its length.
   *
   * @param str     the value to write
   * @param charset the charset to encode the String with
   */
  final def putString(str: String, charset: Charset = UTF_8): Unit = {
    putCharSequence(str, charset)
  }

  /**
   * Writes a CharSequence with the given charset. The written data isn't prefixed by its length.
   *
   * @param csq     the value to write
   * @param charset the charset to encode the CharSequence with
   */
  final def putCharSequence(csq: CharSequence, charset: Charset = UTF_8): Unit = {
    val bytes = charset.encode(CharBuffer.wrap(csq))
    val rem = bytes.remaining()
    checkAvail(rem)
    _put(bytes, rem)
  }

  /**
   * Writes a CharSequence with the given charset, preceded by its length written as a normal VarInt.
   *
   * @param csq     the value to write
   * @param charset the charset to CharSequence the String with
   */
  final def putVarString(csq: CharSequence, charset: Charset = UTF_8): Unit = {
    val bytes = charset.encode(CharBuffer.wrap(csq))
    val rem = bytes.remaining()
    checkAvail(rem + 1)
    putVarInt(rem)
    _put(bytes, rem)
  }

  /**
   * Writes a CharSequence with the given charset, prefixed by its length written as a big-endian
   * 2-bytes unsigned integer.
   *
   * @param csq     the value to write
   * @param charset the charset to encode the CharSequence with
   */
  final def putShortString(csq: CharSequence, charset: Charset = UTF_8): Unit = {
    val bytes = charset.encode(CharBuffer.wrap(csq))
    val rem = bytes.remaining()
    checkAvail(rem + 2)
    putShort(rem)
    _put(bytes, rem)
  }

  /**
   * Writes a CharSequence with the given charset, prefixed by its length written as a little-endian
   * 2-bytes unsigned integer.
   *
   * @param csq     the value to write
   * @param charset the charset to encode the CharSequence with
   */
  final def putShortStringLE(csq: CharSequence, charset: Charset = UTF_8): Unit = {
    val bytes = charset.encode(CharBuffer.wrap(csq))
    val rem = bytes.remaining()
    checkAvail(rem + 2)
    putShortLE(rem)
    _put(bytes, rem)
  }

  // ----------------------------------------
  // ----- Put operations for channels -----
  /**
   * Writes exactly `length` bytes from `src`.
   * Throws an exception if there isn't enough space for the data or if there isn't enough data.
   *
   * @param src    the channel providing the data
   * @param length the number of bytes to get from the channel
   */
  def put(src: ScatteringByteChannel, length: Int): Unit = {
    checkAvail(length)
    val actual = putSome(src, length)
    checkComplete(length, actual, "byte")
  }

  /**
   * Writes at most `maxBytes` bytes from `src`.
   * Returns the actual number of bytes written, possibly zero.
   *
   * @param src    the channel providing the data
   * @param maxBytes the maximum number of bytes to get from the channel
   * @return the number of bytes read from `src`, or -1 if the end of the stream has been reached
   */
  def putSome(src: ScatteringByteChannel, maxBytes: Int = TMP_BUFFER_SIZE): Int = {
    val l = Math.min(maxBytes, writeAvail)
    val buff = ByteBuffer.allocate(l)
    val read = src.read(buff)
    if (read > 0) {
      buff.flip()
      _put(buff, read)
    }
    read
  }

  // ---------------------------------------
  // ----- Put operations for streams -----
  /**
   * Writes exactly `length` bytes from `src`.
   * Throws an exception if there isn't enough space for the data or if there isn't enough data.
   *
   * @param src    the stream providing the data
   * @param length the number of bytes to get from the stream
   */
  def put(src: InputStream, length: Int): Unit = {
    checkAvail(length)
    val actual = putSome(src, length)
    checkComplete(length, actual, "byte")
  }

  /**
   * Writes at most `maxBytes` bytes from `src`.
   * Returns the actual number of bytes written, possibly zero.
   *
   * @param src    the stream providing the data
   * @param maxBytes the maximum number of bytes to get from the channel
   * @return the number of bytes read from `src`, or -1 if the end of the stream has been reached
   */
  def putSome(src: InputStream, maxBytes: Int = TMP_BUFFER_SIZE): Int = {
    val l = Math.min(maxBytes, writeAvail)
    val buff = new Array[Byte](l)
    val read = src.read(buff)
    if (read > 0) {
      _put(buff, 0, read)
    }
    read
  }

  // --------------------------------------
  // ----- Put operations for inputs -----
  /**
   * Writes exactly `length` bytes from `src`.
   * Throws an exception if there isn't enough space for the data or if there isn't enough data.
   *
   * @param src    the NiolInput providing the data
   * @param length the number of bytes to get from the input
   */
  def put(src: NiolInput, length: Int): Unit = {
    checkAvail(length)
    val actual = putSome(src, length)
    checkComplete(length, actual, "byte")
  }

  /**
   * Writes at most `maxBytes` bytes from `src`.
   * Returns the actual number of bytes written, possibly zero.
   *
   * @param src    the NiolInput providing the data
   * @param maxBytes the maximum number of bytes to get from the channel
   * @return the number of bytes that have been put into this output, >= 0
   */
  def putSome(src: NiolInput, maxBytes: Int = TMP_BUFFER_SIZE): Int = {
    val l = Math.min(maxBytes, writeAvail)
    val buff = new Array[Byte](l)
    val read = src.getSomeBytes(buff)
    if (read > 0) {
      _put(buff, 0, read)
    }
    read
  }

  // -------------------------------------------
  // ----- Put operations for ByteBuffers -----
  /**
   * Writes all of the given ByteBuffer.
   * Throws an exception if there isn't enough space for the data.
   *
   * @param src the buffer to write
   */
  def put(src: ByteBuffer): Unit = {
    val rem = src.remaining()
    checkAvail(rem)
    _put(src, rem)
  }

  /**
   * Writes at most `src.remaining()` bytes from `src` into this output.
   * The buffer's position will be advanced by the number of bytes written to this NiolOutput.
   *
   * @param src the buffer to write
   */
  def putSome(src: ByteBuffer): Unit

  // -------------------------------------------
  // ----- Put operations for NiolBuffers -----
  /**
   * Writes all of the given ByteBuffer.
   * Throws an exception if there isn't enough space for the data.
   *
   * @param src the buffer to write
   */
  def put(src: NiolBuffer): Unit = {
    val ava = src.readAvail
    checkAvail(ava)
    _put(src, ava)
  }

  /**
   * Writes at most `src.readAvail` bytes from `src` into this output.
   * The buffer's read position will be advanced by the number of bytes written to this NiolOutput.
   *
   * @param src the buffer to write
   */
  def putSome(src: NiolBuffer): Unit = {
    while (src.canRead && canWrite) {
      putByte(src.get())
    }
  }

  // ----------------------------------------------
  // ----- Put operations for arrays of bytes -----
  /**
   * Writes all the bytes of `src`.
   * Throws an exception if there isn't enough space for the data.
   *
   * @param src the array to put
   */
  def put(src: Array[Byte]): Unit = put(src, 0, src.length)

  /**
   * Writes the content of `src`, starting at index `offset` and ending at index `offset+length-1`.
   * Throws an exception if there isn't enough space for the data or if the array index goes out
   * of bounds.
   *
   * @param src    the array to put
   * @param offset the first index to use
   * @param length the number of bytes to write
   */
  def put(src: Array[Byte], offset: Int, length: Int): Unit = {
    checkAvail(length)
    _put(src, offset, length)
  }

  /**
   * Writes at most `src.length` bytes of `src`.
   * Returns the actual number of bytes written, possibly zero.
   *
   * @param src the array to put
   * @return the number of bytes written
   */
  def putSome(src: Array[Byte]): Int = putSome(src, 0, src.length)

  /**
   * Writes at most `length` bytes of `src`, starting at index `offset`.
   * Returns the actual number of bytes written, possibly zero.
   *
   * @param src    the array to put
   * @param offset the first index to use
   * @param length the number of bytes to write
   * @return the number of bytes written
   */
  def putSome(src: Array[Byte], offset: Int, length: Int): Int = {
    var i = offset
    val l = offset + length
    while (i < l) {
      putByte(src(i))
      i += 1
    }
    i - offset
  }

  // ----------------------------------------------
  // ----- Put operations for boolean arrays -----
  /**
   * Writes all the booleans of `src`.
   * Throws an exception if there isn't enough space for the data.
   *
   * @param src the array to put
   */
  def putBooleans(src: Array[Boolean]): Unit = putBooleans(src, 0, src.length)

  /**
   * Writes the content of `src`, starting at index `offset` and ending at index `offset+length-1`.
   * Throws an exception if there isn't enough space for the data or if the array index goes out
   * of bounds.
   *
   * @param src    the array to put
   * @param offset the first index to use
   * @param length the number of booleans to write
   */
  def putBooleans(src: Array[Boolean], offset: Int, length: Int): Unit = {
    checkAvail(length)
    putSomeBooleans(src, offset, length)
  }

  /**
   * Writes at most `src.length` booleans of `src`.
   * Returns the actual number of booleans written, possibly zero.
   *
   * @param src the array to put
   * @return the number of booleans written
   */
  def putSomeBooleans(src: Array[Boolean]): Int = putSomeBooleans(src, 0, src.length)

  /**
   * Writes at most `length` booleans of `src`, starting at index `offset`.
   * Returns the actual number of booleans written, possibly zero.
   *
   * @param src    the array to put
   * @param offset the first index to use
   * @param length the number of booleans to write
   * @return the number of booleans written
   */
  def putSomeBooleans(src: Array[Boolean], offset: Int, length: Int): Int = {
    var i = offset
    val l = offset + length
    while (i < l) {
      putBoolean(src(i))
      i += 1
    }
    i - offset
  }

  // -----------------------------------------------
  // ----- Put operations for arrays of shorts -----
  /**
   * Writes all the shorts of `src`.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough space for the data.
   *
   * @param src the array to put
   */
  def putShorts(src: Array[Short]): Unit = putShorts(src, 0, src.length)

  /**
   * Writes the content of `src`, starting at index `offset` and ending at index `offset+length-1`.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough space for the data or if the array index goes out
   * of bounds.
   *
   * @param src    the array to put
   * @param offset the first index to use
   * @param length the number of shorts to write
   */
  def putShorts(src: Array[Short], offset: Int, length: Int): Unit = {
    checkAvail(length)
    putSomeShorts(src, offset, length)
  }

  /**
   * Writes at most `src.length` shorts of `src`.
   * Uses big-endian for each value.
   * Returns the actual number of shorts written, possibly zero.
   *
   * @param src the array to put
   * @return the number of shorts written
   */
  def putSomeShorts(src: Array[Short]): Int = putSomeShorts(src, 0, src.length)

  /**
   * Writes at most `length` shorts of `src`, starting at index `offset`.
   * Uses big-endian for each value.
   * Returns the actual number of shorts written, possibly zero.
   *
   * @param src    the array to put
   * @param offset the first index to use
   * @param length the number of shorts to write
   * @return the number of shorts written
   */
  def putSomeShorts(src: Array[Short], offset: Int, length: Int): Int = {
    var i = offset
    val l = offset + length
    while (i < l) {
      putShort(src(i))
      i += 1
    }
    i - offset
  }

  /**
   * Writes all the shorts of `src`.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough space for the data.
   *
   * @param src the array to put
   */
  def putShortsLE(src: Array[Short]): Unit = putShortsLE(src, 0, src.length)

  /**
   * Writes the content of `src`, starting at index `offset` and ending at index `offset+length-1`.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough space for the data or if the array index goes out
   * of bounds.
   *
   * @param src    the array to put
   * @param offset the first index to use
   * @param length the number of shorts to write
   */
  def putShortsLE(src: Array[Short], offset: Int, length: Int): Unit = {
    checkAvail(length)
    putSomeShortsLE(src, offset, length)
  }

  /**
   * Writes at most `src.length` shorts of `src` for each value.
   * Uses little-endian for each value.
   * Returns the actual number of shorts written, possibly zero.
   *
   * @param src the array to put
   * @return the number of shorts written
   */
  def putSomeShortsLE(src: Array[Short]): Int = putSomeShortsLE(src, 0, src.length)

  /**
   * Writes at most `length` shorts of `src`, starting at index `offset`.
   * Uses little-endian for each value.
   * Returns the actual number of shorts written, possibly zero.
   *
   * @param src    the array to put
   * @param offset the first index to use
   * @param length the number of shorts to write
   * @return the number of shorts written
   */
  def putSomeShortsLE(src: Array[Short], offset: Int, length: Int): Int = {
    var i = offset
    val l = offset + length
    while (i < l) {
      putShortLE(src(i))
      i += 1
    }
    i - offset
  }

  // ---------------------------------------------
  // ----- Put operations for arrays of ints -----
  /**
   * Writes all the ints of `src`.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough space for the data.
   *
   * @param src the array to put
   */
  def putInts(src: Array[Int]): Unit = putInts(src, 0, src.length)

  /**
   * Writes the content of `src`, starting at index `offset` and ending at index `offset+length-1`.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough space for the data or if the array index goes out
   * of bounds.
   *
   * @param src    the array to put
   * @param offset the first index to use
   * @param length the number of ints to write
   */
  def putInts(src: Array[Int], offset: Int, length: Int): Unit = {
    checkAvail(length)
    putSomeInts(src, offset, length)
  }

  /**
   * Writes at most `src.length` ints of `src`.
   * Uses big-endian for each value.
   * Returns the actual number of ints written, possibly zero.
   *
   * @param src the array to put
   * @return the number of ints written
   */
  def putSomeInts(src: Array[Int]): Int = putSomeInts(src, 0, src.length)

  /**
   * Writes at most `length` ints of `src`, starting at index `offset`.
   * Uses big-endian for each value.
   * Returns the actual number of ints written, possibly zero.
   *
   * @param src    the array to put
   * @param offset the first index to use
   * @param length the number of ints to write
   * @return the number of ints written
   */
  def putSomeInts(src: Array[Int], offset: Int, length: Int): Int = {
    var i = offset
    val l = offset + length
    while (i < l) {
      putInt(src(i))
      i += 1
    }
    i - offset
  }

  /**
   * Writes all the ints of `src`.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough space for the data.
   *
   * @param src the array to put
   */
  def putIntsLE(src: Array[Int]): Unit = putIntsLE(src, 0, src.length)

  /**
   * Writes the content of `src`, starting at index `offset` and ending at index `offset+length-1`.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough space for the data or if the array index goes out
   * of bounds.
   *
   * @param src    the array to put
   * @param offset the first index to use
   * @param length the number of ints to write
   */
  def putIntsLE(src: Array[Int], offset: Int, length: Int): Unit = {
    checkAvail(length)
    putSomeIntsLE(src, offset, length)
  }

  /**
   * Writes at most `src.length` ints of `src` for each value.
   * Uses little-endian for each value.
   * Returns the actual number of ints written, possibly zero.
   *
   * @param src the array to put
   * @return the number of ints written
   */
  def putSomeIntsLE(src: Array[Int]): Int = putSomeIntsLE(src, 0, src.length)

  /**
   * Writes at most `length` ints of `src`, starting at index `offset`.
   * Uses little-endian for each value.
   * Returns the actual number of ints written, possibly zero.
   *
   * @param src    the array to put
   * @param offset the first index to use
   * @param length the number of ints to write
   * @return the number of ints written
   */
  def putSomeIntsLE(src: Array[Int], offset: Int, length: Int): Int = {
    var i = offset
    val l = offset + length
    while (i < l) {
      putIntLE(src(i))
      i += 1
    }
    i - offset
  }


  // ----------------------------------------------
  // ----- Put operations for arrays of longs -----
  /**
   * Writes all the longs of `src`.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough space for the data.
   *
   * @param src the array to put
   */
  def putLongs(src: Array[Long]): Unit = putLongs(src, 0, src.length)

  /**
   * Writes the content of `src`, starting at index `offset` and ending at index `offset+length-1`.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough space for the data or if the array index goes out
   * of bounds.
   *
   * @param src    the array to put
   * @param offset the first index to use
   * @param length the number of longs to write
   */
  def putLongs(src: Array[Long], offset: Int, length: Int): Unit = {
    checkAvail(length)
    putSomeLongs(src, offset, length)
  }

  /**
   * Writes at most `src.length` longs of `src`.
   * Uses big-endian for each value.
   * Returns the actual number of longs written, possibly zero.
   *
   * @param src the array to put
   * @return the number of longs written
   */
  def putSomeLongs(src: Array[Long]): Int = putSomeLongs(src, 0, src.length)

  /**
   * Writes at most `length` longs of `src`, starting at index `offset`.
   * Uses big-endian for each value.
   * Returns the actual number of longs written, possibly zero.
   *
   * @param src    the array to put
   * @param offset the first index to use
   * @param length the number of longs to write
   * @return the number of longs written
   */
  def putSomeLongs(src: Array[Long], offset: Int, length: Int): Int = {
    var i = offset
    val l = offset + length
    while (i < l) {
      putLong(src(i))
      i += 1
    }
    i - offset
  }

  /**
   * Writes all the longs of `src`.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough space for the data.
   *
   * @param src the array to put
   */
  def putLongsLE(src: Array[Long]): Unit = putLongsLE(src, 0, src.length)

  /**
   * Writes the content of `src`, starting at index `offset` and ending at index `offset+length-1`.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough space for the data or if the array index goes out
   * of bounds.
   *
   * @param src    the array to put
   * @param offset the first index to use
   * @param length the number of longs to write
   */
  def putLongsLE(src: Array[Long], offset: Int, length: Int): Unit = {
    checkAvail(length)
    putSomeLongsLE(src, offset, length)
  }

  /**
   * Writes at most `src.length` longs of `src` for each value.
   * Uses little-endian for each value.
   * Returns the actual number of longs written, possibly zero.
   *
   * @param src the array to put
   * @return the number of longs written
   */
  def putSomeLongsLE(src: Array[Long]): Int = putSomeLongsLE(src, 0, src.length)

  /**
   * Writes at most `length` longs of `src`, starting at index `offset`.
   * Uses little-endian for each value.
   * Returns the actual number of longs written, possibly zero.
   *
   * @param src    the array to put
   * @param offset the first index to use
   * @param length the number of longs to write
   * @return the number of longs written
   */
  def putSomeLongsLE(src: Array[Long], offset: Int, length: Int): Int = {
    var i = offset
    val l = offset + length
    while (i < l) {
      putLongLE(src(i))
      i += 1
    }
    i - offset
  }
  
  // -----------------------------------------------
  // ----- Put operations for arrays of floats -----
  /**
   * Writes all the floats of `src`.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough space for the data.
   *
   * @param src the array to put
   */
  def putFloats(src: Array[Float]): Unit = putFloats(src, 0, src.length)

  /**
   * Writes the content of `src`, starting at index `offset` and ending at index `offset+length-1`.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough space for the data or if the array index goes out
   * of bounds.
   *
   * @param src    the array to put
   * @param offset the first index to use
   * @param length the number of floats to write
   */
  def putFloats(src: Array[Float], offset: Int, length: Int): Unit = {
    checkAvail(length)
    putSomeFloats(src, offset, length)
  }

  /**
   * Writes at most `src.length` floats of `src`.
   * Uses big-endian for each value.
   * Returns the actual number of floats written, possibly zero.
   *
   * @param src the array to put
   * @return the number of floats written
   */
  def putSomeFloats(src: Array[Float]): Int = putSomeFloats(src, 0, src.length)

  /**
   * Writes at most `length` floats of `src`, starting at index `offset`.
   * Uses big-endian for each value.
   * Returns the actual number of floats written, possibly zero.
   *
   * @param src    the array to put
   * @param offset the first index to use
   * @param length the number of floats to write
   * @return the number of floats written
   */
  def putSomeFloats(src: Array[Float], offset: Int, length: Int): Int = {
    var i = offset
    val l = offset + length
    while (i < l) {
      putFloat(src(i))
      i += 1
    }
    i - offset
  }

  /**
   * Writes all the floats of `src`.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough space for the data.
   *
   * @param src the array to put
   */
  def putFloatsLE(src: Array[Float]): Unit = putFloatsLE(src, 0, src.length)

  /**
   * Writes the content of `src`, starting at index `offset` and ending at index `offset+length-1`.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough space for the data or if the array index goes out
   * of bounds.
   *
   * @param src    the array to put
   * @param offset the first index to use
   * @param length the number of floats to write
   */
  def putFloatsLE(src: Array[Float], offset: Int, length: Int): Unit = {
    checkAvail(length)
    putSomeFloatsLE(src, offset, length)
  }

  /**
   * Writes at most `src.length` floats of `src` for each value.
   * Uses little-endian for each value.
   * Returns the actual number of floats written, possibly zero.
   *
   * @param src the array to put
   * @return the number of floats written
   */
  def putSomeFloatsLE(src: Array[Float]): Int = putSomeFloatsLE(src, 0, src.length)

  /**
   * Writes at most `length` floats of `src`, starting at index `offset`.
   * Uses little-endian for each value.
   * Returns the actual number of floats written, possibly zero.
   *
   * @param src    the array to put
   * @param offset the first index to use
   * @param length the number of floats to write
   * @return the number of floats written
   */
  def putSomeFloatsLE(src: Array[Float], offset: Int, length: Int): Int = {
    var i = offset
    val l = offset + length
    while (i < l) {
      putFloatLE(src(i))
      i += 1
    }
    i - offset
  }


  // ------------------------------------------------
  // ----- Put operations for arrays of doubles -----
  /**
   * Writes all the doubles of `src`.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough space for the data.
   *
   * @param src the array to put
   */
  def putDoubles(src: Array[Double]): Unit = putDoubles(src, 0, src.length)

  /**
   * Writes the content of `src`, starting at index `offset` and ending at index `offset+length-1`.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough space for the data or if the array index goes out
   * of bounds.
   *
   * @param src    the array to put
   * @param offset the first index to use
   * @param length the number of doubles to write
   */
  def putDoubles(src: Array[Double], offset: Int, length: Int): Unit = {
    checkAvail(length)
    putSomeDoubles(src, offset, length)
  }

  /**
   * Writes at most `src.length` doubles of `src`.
   * Uses big-endian for each value.
   * Returns the actual number of doubles written, possibly zero.
   *
   * @param src the array to put
   * @return the number of doubles written
   */
  def putSomeDoubles(src: Array[Double]): Int = putSomeDoubles(src, 0, src.length)

  /**
   * Writes at most `length` doubles of `src`, starting at index `offset`.
   * Uses big-endian for each value.
   * Returns the actual number of doubles written, possibly zero.
   *
   * @param src    the array to put
   * @param offset the first index to use
   * @param length the number of doubles to write
   * @return the number of doubles written
   */
  def putSomeDoubles(src: Array[Double], offset: Int, length: Int): Int = {
    var i = offset
    val l = offset + length
    while (i < l) {
      putDouble(src(i))
      i += 1
    }
    i - offset
  }

  /**
   * Writes all the doubles of `src`.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough space for the data.
   *
   * @param src the array to put
   */
  def putDoublesLE(src: Array[Double]): Unit = putDoublesLE(src, 0, src.length)

  /**
   * Writes the content of `src`, starting at index `offset` and ending at index `offset+length-1`.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough space for the data or if the array index goes out
   * of bounds.
   *
   * @param src    the array to put
   * @param offset the first index to use
   * @param length the number of doubles to write
   */
  def putDoublesLE(src: Array[Double], offset: Int, length: Int): Unit = {
    checkAvail(length)
    putSomeDoublesLE(src, offset, length)
  }

  /**
   * Writes at most `src.length` doubles of `src` for each value.
   * Uses little-endian for each value.
   * Returns the actual number of doubles written, possibly zero.
   *
   * @param src the array to put
   * @return the number of doubles written
   */
  def putSomeDoublesLE(src: Array[Double]): Int = putSomeDoublesLE(src, 0, src.length)

  /**
   * Writes at most `length` doubles of `src`, starting at index `offset`.
   * Uses little-endian for each value.
   * Returns the actual number of doubles written, possibly zero.
   *
   * @param src    the array to put
   * @param offset the first index to use
   * @param length the number of doubles to write
   * @return the number of doubles written
   */
  def putSomeDoublesLE(src: Array[Double], offset: Int, length: Int): Int = {
    var i = offset
    val l = offset + length
    while (i < l) {
      putDoubleLE(src(i))
      i += 1
    }
    i - offset
  }
}

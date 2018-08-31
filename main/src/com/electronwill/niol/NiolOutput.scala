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
  /** Implements write without necessarily checking for available space. */
  protected[this] def _write(b: Byte): Unit

  /** Implements write without necessarily checking for available space. */
  protected[this] final def _write(b: Int): Unit =_write(b.toByte)

  /** Implements write without necessarily checking for available space. */
  protected[this] final def _write(b: Long): Unit =_write(b.toByte)

  /** Implements write without necessarily checking for available space. */
  protected[this] def _write(from: Array[Byte], off: Int, len: Int): Unit

  /** Implements write without necessarily checking for available space. */
  protected[this] def _write(from: ByteBuffer, len: Int): Unit

  /** Implements write without necessarily checking for available space. */
  protected[this] def _write(from: NiolBuffer, len: Int): Unit

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
    if (actual != expected) throw new IncompleteWriteException(expected, actual, v)
  }


  // ---------------------------------------------
  // ----- Primitive single-value operations -----
  /**
   * Writes a byte.
   *
   * @param b the byte to write
   */
  def write(b: Byte): Unit = {
    if (canWrite) _write(b)
    else throw new NotEnoughSpaceException(1, 0)
  }

  /**
   * Attempts to write a byte. Returns false if the output is full.
   *
   * @param b the byte to write
   * @return true if the byte has been write, false if the output is full
   */
  def tryWrite(b: Byte): Boolean = {
    if (canWrite) {
      _write(b)
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
  def writeBoolean(boolean: Boolean): Unit = writeBool(boolean, 1, 0)

  /**
   * Writes a boolean. The default behavior is as follow:
   * If it's true, writes the byte `1`. If it's false, writes the byte `0`.
   *
   * @param bool the boolean to write
   */
  def writeBool(bool: Boolean): Unit = writeBool(bool, 1, 0)

  /**
   * Writes a boolean. If it's true, writes `trueValue`. If it's false, writes `falseValue`.
   *
   * @param bool       the boolean to write
   * @param trueValue  the byte to write if the boolean is true
   * @param falseValue the byte to write if the boolean is false
   */
  def writeBool(bool: Boolean, trueValue: Byte, falseValue: Byte): Unit = {
    checkAvail(1)
    _write(if (bool) trueValue else falseValue)
  }

  /**
   * Writes a byte.
   *
   * @param b the byte to write
   */
  final def writeByte(b: Byte): Unit = write(b)

  /**
   * Writes the least significant byte of an int.
   *
   * @param i the value to write
   */
  final def writeByte(i: Int): Unit = write(i.toByte)

  /**
   * Writes the least significant byte of a long.
   *
   * @param l the value to write
   */
  final def writeByte(l: Long): Unit = write(l.toByte)

  /**
   * Writes a big-endian short.
   *
   * @param s the value to write
   */
  final def writeShort(s: Short): Unit = writeShort(s.toInt)
  // Note: bit shifting operations are only applied on ints and longs, the value is converted if
  // it's a smaller number. Therefore we delegate writeShort(Short) to writeShort(Int) to make the
  // short -> int conversion explicit.

  /**
   * Writes a little-endian short.
   *
   * @param s the value to write
   */
  final def writeShortLE(s: Short): Unit = writeShortLE(s.toInt)

  /**
   * Writes a big-endian short.
   *
   * @param i the value to write
   */
  def writeShort(i: Int): Unit = {
    checkAvail(2)
    _write(i >> 8)
    _write(i)
  }

  /**
   * Writes a little-endian short.
   *
   * @param i the value to write
   */
  def writeShortLE(i: Int): Unit = {
    checkAvail(2)
    _write(i)
    _write(i >> 8)
  }

  /**
   * Writes a big-endian char.
   *
   * @param c the value to write
   */
  final def writeChar(c: Char): Unit = writeShort(c.toInt & 0xFFFF)

  /**
   * Writes a little-endian char.
   *
   * @param c the value to write
   */
  final def writeCharLE(c: Char): Unit = writeShortLE(c.toInt & 0xFFFF)

  /**
   * Writes a big-endian 3-bytes integer.
   * @param m the value to write
   */
  def writeMedium(m: Int): Unit = {
    checkAvail(3)
    _write(m >> 16)
    _write(m >> 8)
    _write(m)
  }

  /**
   * Writes a little-endian 3-bytes integer.
   *
   * @param m the value to write
   */
  def writeMediumLE(m: Int): Unit = {
    checkAvail(3)
    _write(m)
    _write(m >> 8)
    _write(m >> 16)
  }

  /**
   * Writes a big-endian 4-bytes integer.
   *
   * @param i the value to write
   */
  def writeInt(i: Int): Unit = {
    checkAvail(4)
    _write(i >> 24)
    _write(i >> 16)
    _write(i >> 8)
    _write(i)
  }

  /**
   * Writes a little-endian 4-bytes integer.
   *
   * @param i the value to write
   */
  def writeIntLE(i: Int): Unit = {
    checkAvail(4)
    _write(i)
    _write(i >> 8)
    _write(i >> 16)
    _write(i >> 24)
  }

  /**
   * Writes a big-endian 8-bytes integer.
   *
   * @param l the value to write
   */
  def writeLong(l: Long): Unit = {
    checkAvail(8)
    _write(l >> 56)
    _write(l >> 48)
    _write(l >> 40)
    _write(l >> 32)
    _write(l >> 24)
    _write(l >> 16)
    _write(l >> 8)
    _write(l)
  }

  /**
   * Writes a little-endian 8-bytes integer.
   *
   * @param l the value to write
   */
  def writeLongLE(l: Long): Unit = {
    checkAvail(8)
    _write(l)
    _write(l >> 8)
    _write(l >> 16)
    _write(l >> 24)
    _write(l >> 32)
    _write(l >> 40)
    _write(l >> 48)
    _write(l >> 56)
  }

  /**
   * Writes a big-endian 4-bytes float.
   *
   * @param f the value to write
   */
  def writeFloat(f: Float): Unit = {
    checkAvail(4)
    val i = java.lang.Float.floatToIntBits(f)
    _write(i >> 24)
    _write(i >> 16)
    _write(i >> 8)
    _write(i)
  }

  /**
   * Writes a big-endian 4-bytes float.
   *
   * @param f the value to write
   */
  final def writeFloat(f: Double): Unit = writeFloat(f.toFloat)

  /**
   * Writes a little-endian 4-bytes float.
   *
   * @param f the value to write
   */
  def writeFloatLE(f: Float): Unit = {
    checkAvail(4)
    val i =java.lang.Float.floatToIntBits(f)
    _write(i)
    _write(i >> 8)
    _write(i >> 16)
    _write(i >> 24)
  }

  /**
   * Writes a little-endian 4-bytes float.
   *
   * @param f the value to write
   */
  final def writeFloatLE(f: Double): Unit = writeFloatLE(f.toFloat)

  /**
   * Writes a big-endian 8-bytes double.
   *
   * @param d the value to write
   */
  def writeDouble(d: Double): Unit = {
    checkAvail(8)
    val l = java.lang.Double.doubleToLongBits(d)
    _write(l >> 56)
    _write(l >> 48)
    _write(l >> 40)
    _write(l >> 32)
    _write(l >> 24)
    _write(l >> 16)
    _write(l >> 8)
    _write(l)
  }

  /**
   * Writes a little-endian 8-bytes double.
   *
   * @param d the value to write
   */
  def writeDoubleLE(d: Double): Unit = {
    checkAvail(8)
    val l = java.lang.Double.doubleToLongBits(d)
    _write(l)
    _write(l >> 8)
    _write(l >> 16)
    _write(l >> 24)
    _write(l >> 32)
    _write(l >> 40)
    _write(l >> 48)
    _write(l >> 56)
  }

  /**
   * Writes a UUID as two 8-bytes big-endian integers, most significant bits first.
   *
   * @param uuid the value to write
   */
  final def writeUUID(uuid: UUID): Unit = {
    checkAvail(16)
    writeLong(uuid.getMostSignificantBits)
    writeLong(uuid.getLeastSignificantBits)
  }


  // -----------------------------------------------
  // ----- Variable-length integers operations -----
  /**
   * Writes a variable-length int using the normal/unsigned encoding.
   *
   * @param i the value to write
   */
  def writeVarInt(i: Int): Unit = {
    var value = i
    do {
      var bits7 = value & 0x7F
      value >>>= 7
      if (value != 0) {
        bits7 |= 0x80
      }
      if (!canWrite) throw new IncompleteWriteException(1, "VarInt")
      _write(bits7)
    } while (value != 0)
  }

  /**
   * Writes a variable-length long using the normal/unsigned encoding.
   *
   * @param l the value to write
   */
  def writeVarLong(l: Long): Unit = {
    var value = l
    do {
      var bits7 = value & 0x7F
      value >>>= 7
      if (value != 0) {
        bits7 |= 0x80
      }
      if (!canWrite) throw new IncompleteWriteException(1, "VarLong")
      _write(bits7)
    } while (value != 0)
  }

  /**
   * Writes a variable-length int using the signed/zig-zag encoding.
   *
   * @param n the value to write
   */
  final def writeSVarIntZigZag(n: Int): Unit = writeVarInt((n << 1) ^ (n >> 31))

  /**
   * Writes a variable-length long using the signed/zig-zag encoding.
   *
   * @param n the value to write
   */
  final def writeSVarLongZigZag(n: Long): Unit = writeVarLong((n << 1) ^ (n >> 63))


  // ----------------------------------------------
  // ----- String and CharSequence operations -----
  /**
   * Writes a String with the given charset. The written data isn't prefixed by its length.
   *
   * @param str     the value to write
   * @param charset the charset to encode the String with
   */
  final def writeString(str: String, charset: Charset = UTF_8): Unit = {
    writeCharSequence(str, charset)
  }

  /**
   * Writes a CharSequence with the given charset. The written data isn't prefixed by its length.
   *
   * @param csq     the value to write
   * @param charset the charset to encode the CharSequence with
   */
  final def writeCharSequence(csq: CharSequence, charset: Charset = UTF_8): Unit = {
    val bytes = charset.encode(CharBuffer.wrap(csq))
    val rem = bytes.remaining()
    checkAvail(rem)
    _write(bytes, rem)
  }

  /**
   * Writes a CharSequence with the given charset, preceded by its length written as a normal VarInt.
   *
   * @param csq     the value to write
   * @param charset the charset to CharSequence the String with
   */
  final def writeVarString(csq: CharSequence, charset: Charset = UTF_8): Unit = {
    val bytes = charset.encode(CharBuffer.wrap(csq))
    val rem = bytes.remaining()
    checkAvail(rem + 1)
    writeVarInt(rem)
    _write(bytes, rem)
  }

  /**
   * Writes a CharSequence with the given charset, prefixed by its length written as a big-endian
   * 2-bytes unsigned integer.
   *
   * @param csq     the value to write
   * @param charset the charset to encode the CharSequence with
   */
  final def writeShortString(csq: CharSequence, charset: Charset = UTF_8): Unit = {
    val bytes = charset.encode(CharBuffer.wrap(csq))
    val rem = bytes.remaining()
    checkAvail(rem + 2)
    writeShort(rem)
    _write(bytes, rem)
  }

  /**
   * Writes a CharSequence with the given charset, prefixed by its length written as a little-endian
   * 2-bytes unsigned integer.
   *
   * @param csq     the value to write
   * @param charset the charset to encode the CharSequence with
   */
  final def writeShortStringLE(csq: CharSequence, charset: Charset = UTF_8): Unit = {
    val bytes = charset.encode(CharBuffer.wrap(csq))
    val rem = bytes.remaining()
    checkAvail(rem + 2)
    writeShortLE(rem)
    _write(bytes, rem)
  }


  // ----------------------------------------
  // ----- Write operations for channels -----
  /**
   * Writes exactly `length` bytes from `src`.
   * Throws an exception if there isn't enough space for the data or if there isn't enough data.
   *
   * @param src    the channel providing the data
   * @param length the number of bytes to read from the channel
   */
  def write(src: ScatteringByteChannel, length: Int): Unit = {
    checkAvail(length)
    val actual = writeSome(src, length)
    checkComplete(length, actual, "byte")
  }

  /**
   * Writes at most `maxBytes` bytes from `src`.
   * Returns the actual number of bytes written, possibly zero.
   *
   * @param src    the channel providing the data
   * @param maxBytes the maximum number of bytes to read from the channel
   * @return the number of bytes read from `src`, or -1 if the end of the stream has been reached
   */
  def writeSome(src: ScatteringByteChannel, maxBytes: Int = TMP_BUFFER_SIZE): Int = {
    val l = Math.min(maxBytes, writeAvail)
    val buff = ByteBuffer.allocate(l)
    val read = src.read(buff)
    if (read > 0) {
      buff.flip()
      _write(buff, read)
    }
    read
  }


  // ---------------------------------------
  // ----- Write operations for streams -----
  /**
   * Writes exactly `length` bytes from `src`.
   * Throws an exception if there isn't enough space for the data or if there isn't enough data.
   *
   * @param src    the stream providing the data
   * @param length the number of bytes to read from the stream
   */
  def write(src: InputStream, length: Int): Unit = {
    checkAvail(length)
    val actual = writeSome(src, length)
    checkComplete(length, actual, "byte")
  }

  /**
   * Writes at most `maxBytes` bytes from `src`.
   * Returns the actual number of bytes written, possibly zero.
   *
   * @param src    the stream providing the data
   * @param maxBytes the maximum number of bytes to read from the channel
   * @return the number of bytes read from `src`, or -1 if the end of the stream has been reached
   */
  def writeSome(src: InputStream, maxBytes: Int = TMP_BUFFER_SIZE): Int = {
    val l = Math.min(maxBytes, writeAvail)
    val buff = new Array[Byte](l)
    val read = src.read(buff)
    if (read > 0) {
      _write(buff, 0, read)
    }
    read
  }


  // -----------------------------------------
  // ----- Write operations for NiolInputs -----
  /**
   * Writes exactly `length` bytes from `src`.
   * Throws an exception if there isn't enough space for the data or if there isn't enough data.
   *
   * @param src    the NiolInput providing the data
   * @param length the number of bytes to read from the input
   */
  def write(src: NiolInput, length: Int): Unit = {
    checkAvail(length)
    val actual = writeSome(src, length)
    checkComplete(length, actual, "byte")
  }

  /**
   * Writes at most `maxBytes` bytes from `src`.
   * Returns the actual number of bytes written, possibly zero.
   *
   * @param src    the NiolInput providing the data
   * @param maxBytes the maximum number of bytes to read from the channel
   * @return the number of bytes that have been write into this output, >= 0
   */
  def writeSome(src: NiolInput, maxBytes: Int = TMP_BUFFER_SIZE): Int = {
    val l = Math.min(maxBytes, writeAvail)
    val buff = new Array[Byte](l)
    val read = src.readSomeBytes(buff)
    if (read > 0) {
      _write(buff, 0, read)
    }
    read
  }


  // -------------------------------------------
  // ----- Write operations for ByteBuffers ------
  /**
   * Writes all of the given ByteBuffer.
   * Throws an exception if there isn't enough space for the data.
   *
   * @param src the buffer to write
   */
  def write(src: ByteBuffer): Unit = {
    val rem = src.remaining()
    checkAvail(rem)
    _write(src, rem)
  }

  /**
   * Writes at most `src.remaining()` bytes from `src` into this output.
   * The buffer's position will be advanced by the number of bytes written to this NiolOutput.
   *
   * @param src the buffer to write
   */
  def writeSome(src: ByteBuffer): Unit


  // -------------------------------------------
  // ----- Write operations for NiolBuffers ------
  /**
   * Writes all of the given ByteBuffer.
   * Throws an exception if there isn't enough space for the data.
   *
   * @param src the buffer to write
   */
  def write(src: NiolBuffer): Unit = {
    val ava = src.readAvail
    checkAvail(ava)
    _write(src, ava)
  }

  /**
   * Writes at most `src.readAvail` bytes from `src` into this output.
   * The buffer's read position will be advanced by the number of bytes written to this NiolOutput.
   *
   * @param src the buffer to write
   */
  def writeSome(src: NiolBuffer): Unit = {
    while (src.canRead && canWrite) {
      writeByte(src.read())
    }
  }


  // ----------------------------------------------
  // ----- Write operations for arrays of bytes -----
  /**
   * Writes all the bytes of `src`.
   * Throws an exception if there isn't enough space for the data.
   *
   * @param src the array to write
   */
  def write(src: Array[Byte]): Unit = write(src, 0, src.length)

  /**
   * Writes the content of `src`, starting at index `offset` and ending at index `offset+length-1`.
   * Throws an exception if there isn't enough space for the data or if the array index goes out
   * of bounds.
   *
   * @param src    the array to write
   * @param offset the first index to use
   * @param length the number of bytes to write
   */
  def write(src: Array[Byte], offset: Int, length: Int): Unit = {
    checkAvail(length)
    _write(src, offset, length)
  }

  /**
   * Writes at most `src.length` bytes of `src`.
   * Returns the actual number of bytes written, possibly zero.
   *
   * @param src the array to write
   * @return the number of bytes written
   */
  def writeSome(src: Array[Byte]): Int = writeSome(src, 0, src.length)

  /**
   * Writes at most `length` bytes of `src`, starting at index `offset`.
   * Returns the actual number of bytes written, possibly zero.
   *
   * @param src    the array to write
   * @param offset the first index to use
   * @param length the number of bytes to write
   * @return the number of bytes written
   */
  def writeSome(src: Array[Byte], offset: Int, length: Int): Int = {
    val length = Math.min(writeAvail, length)
    var i = offset
    val l = offset + length
    while (i < l) {
      _write(src(i))
      i += 1
    }
    i - offset
  }


  // ----------------------------------------------
  // ----- Write operations for boolean arrays -----
  /**
   * Writes all the booleans of `src`.
   * Throws an exception if there isn't enough space for the data.
   *
   * @param src the array to write
   */
  def writeBooleans(src: Array[Boolean]): Unit = writeBooleans(src, 0, src.length)

  /**
   * Writes the content of `src`, starting at index `offset` and ending at index `offset+length-1`.
   * Throws an exception if there isn't enough space for the data or if the array index goes out
   * of bounds.
   *
   * @param src    the array to write
   * @param offset the first index to use
   * @param length the number of booleans to write
   */
  def writeBooleans(src: Array[Boolean], offset: Int, length: Int): Unit = {
    checkAvail(length)
    writeSomeBooleans(src, offset, length)
  }

  /**
   * Writes at most `src.length` booleans of `src`.
   * Returns the actual number of booleans written, possibly zero.
   *
   * @param src the array to write
   * @return the number of booleans written
   */
  def writeSomeBooleans(src: Array[Boolean]): Int = writeSomeBooleans(src, 0, src.length)

  /**
   * Writes at most `length` booleans of `src`, starting at index `offset`.
   * Returns the actual number of booleans written, possibly zero.
   *
   * @param src    the array to write
   * @param offset the first index to use
   * @param length the number of booleans to write
   * @return the number of booleans written
   */
  def writeSomeBooleans(src: Array[Boolean], offset: Int, length: Int): Int = {
    val length = Math.min(writeAvail, length)
    var i = offset
    val l = offset + length
    while (i < l) {
      writeBoolean(src(i))
      i += 1
    }
    i - offset
  }


  // -----------------------------------------------
  // ----- Write operations for arrays of shorts -----
  /**
   * Writes all the shorts of `src`.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough space for the data.
   *
   * @param src the array to write
   */
  def writeShorts(src: Array[Short]): Unit = writeShorts(src, 0, src.length)

  /**
   * Writes the content of `src`, starting at index `offset` and ending at index `offset+length-1`.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough space for the data or if the array index goes out
   * of bounds.
   *
   * @param src    the array to write
   * @param offset the first index to use
   * @param length the number of shorts to write
   */
  def writeShorts(src: Array[Short], offset: Int, length: Int): Unit = {
    checkAvail(length)
    writeSomeShorts(src, offset, length)
  }

  /**
   * Writes at most `src.length` shorts of `src`.
   * Uses big-endian for each value.
   * Returns the actual number of shorts written, possibly zero.
   *
   * @param src the array to write
   * @return the number of shorts written
   */
  def writeSomeShorts(src: Array[Short]): Int = writeSomeShorts(src, 0, src.length)

  /**
   * Writes at most `length` shorts of `src`, starting at index `offset`.
   * Uses big-endian for each value.
   * Returns the actual number of shorts written, possibly zero.
   *
   * @param src    the array to write
   * @param offset the first index to use
   * @param length the number of shorts to write
   * @return the number of shorts written
   */
  def writeSomeShorts(src: Array[Short], offset: Int, length: Int): Int = {
    val length = Math.min(writeAvail/2, length)
    var i = offset
    val l = offset + length
    while (i < l) {
      writeShort(src(i))
      i += 1
    }
    i - offset
  }

  /**
   * Writes all the shorts of `src`.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough space for the data.
   *
   * @param src the array to write
   */
  def writeShortsLE(src: Array[Short]): Unit = writeShortsLE(src, 0, src.length)

  /**
   * Writes the content of `src`, starting at index `offset` and ending at index `offset+length-1`.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough space for the data or if the array index goes out
   * of bounds.
   *
   * @param src    the array to write
   * @param offset the first index to use
   * @param length the number of shorts to write
   */
  def writeShortsLE(src: Array[Short], offset: Int, length: Int): Unit = {
    checkAvail(length)
    writeSomeShortsLE(src, offset, length)
  }

  /**
   * Writes at most `src.length` shorts of `src` for each value.
   * Uses little-endian for each value.
   * Returns the actual number of shorts written, possibly zero.
   *
   * @param src the array to write
   * @return the number of shorts written
   */
  def writeSomeShortsLE(src: Array[Short]): Int = writeSomeShortsLE(src, 0, src.length)

  /**
   * Writes at most `length` shorts of `src`, starting at index `offset`.
   * Uses little-endian for each value.
   * Returns the actual number of shorts written, possibly zero.
   *
   * @param src    the array to write
   * @param offset the first index to use
   * @param length the number of shorts to write
   * @return the number of shorts written
   */
  def writeSomeShortsLE(src: Array[Short], offset: Int, length: Int): Int = {
    val length = Math.min(writeAvail/2, length)
    var i = offset
    val l = offset + length
    while (i < l) {
      writeShortLE(src(i))
      i += 1
    }
    i - offset
  }


  // ---------------------------------------------
  // ----- Write operations for arrays of ints -----
  /**
   * Writes all the ints of `src`.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough space for the data.
   *
   * @param src the array to write
   */
  def writeInts(src: Array[Int]): Unit = writeInts(src, 0, src.length)

  /**
   * Writes the content of `src`, starting at index `offset` and ending at index `offset+length-1`.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough space for the data or if the array index goes out
   * of bounds.
   *
   * @param src    the array to write
   * @param offset the first index to use
   * @param length the number of ints to write
   */
  def writeInts(src: Array[Int], offset: Int, length: Int): Unit = {
    checkAvail(length)
    writeSomeInts(src, offset, length)
  }

  /**
   * Writes at most `src.length` ints of `src`.
   * Uses big-endian for each value.
   * Returns the actual number of ints written, possibly zero.
   *
   * @param src the array to write
   * @return the number of ints written
   */
  def writeSomeInts(src: Array[Int]): Int = writeSomeInts(src, 0, src.length)

  /**
   * Writes at most `length` ints of `src`, starting at index `offset`.
   * Uses big-endian for each value.
   * Returns the actual number of ints written, possibly zero.
   *
   * @param src    the array to write
   * @param offset the first index to use
   * @param length the number of ints to write
   * @return the number of ints written
   */
  def writeSomeInts(src: Array[Int], offset: Int, length: Int): Int = {
    val length = Math.min(writeAvail/4, length)
    var i = offset
    val l = offset + length
    while (i < l) {
      writeInt(src(i))
      i += 1
    }
    i - offset
  }

  /**
   * Writes all the ints of `src`.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough space for the data.
   *
   * @param src the array to write
   */
  def writeIntsLE(src: Array[Int]): Unit = writeIntsLE(src, 0, src.length)

  /**
   * Writes the content of `src`, starting at index `offset` and ending at index `offset+length-1`.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough space for the data or if the array index goes out
   * of bounds.
   *
   * @param src    the array to write
   * @param offset the first index to use
   * @param length the number of ints to write
   */
  def writeIntsLE(src: Array[Int], offset: Int, length: Int): Unit = {
    checkAvail(length)
    writeSomeIntsLE(src, offset, length)
  }

  /**
   * Writes at most `src.length` ints of `src` for each value.
   * Uses little-endian for each value.
   * Returns the actual number of ints written, possibly zero.
   *
   * @param src the array to write
   * @return the number of ints written
   */
  def writeSomeIntsLE(src: Array[Int]): Int = writeSomeIntsLE(src, 0, src.length)

  /**
   * Writes at most `length` ints of `src`, starting at index `offset`.
   * Uses little-endian for each value.
   * Returns the actual number of ints written, possibly zero.
   *
   * @param src    the array to write
   * @param offset the first index to use
   * @param length the number of ints to write
   * @return the number of ints written
   */
  def writeSomeIntsLE(src: Array[Int], offset: Int, length: Int): Int = {
    val length = Math.min(writeAvail/4, length)
    var i = offset
    val l = offset + length
    while (i < l) {
      writeIntLE(src(i))
      i += 1
    }
    i - offset
  }


  // ----------------------------------------------
  // ----- Write operations for arrays of longs -----
  /**
   * Writes all the longs of `src`.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough space for the data.
   *
   * @param src the array to write
   */
  def writeLongs(src: Array[Long]): Unit = writeLongs(src, 0, src.length)

  /**
   * Writes the content of `src`, starting at index `offset` and ending at index `offset+length-1`.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough space for the data or if the array index goes out
   * of bounds.
   *
   * @param src    the array to write
   * @param offset the first index to use
   * @param length the number of longs to write
   */
  def writeLongs(src: Array[Long], offset: Int, length: Int): Unit = {
    checkAvail(length)
    writeSomeLongs(src, offset, length)
  }

  /**
   * Writes at most `src.length` longs of `src`.
   * Uses big-endian for each value.
   * Returns the actual number of longs written, possibly zero.
   *
   * @param src the array to write
   * @return the number of longs written
   */
  def writeSomeLongs(src: Array[Long]): Int = writeSomeLongs(src, 0, src.length)

  /**
   * Writes at most `length` longs of `src`, starting at index `offset`.
   * Uses big-endian for each value.
   * Returns the actual number of longs written, possibly zero.
   *
   * @param src    the array to write
   * @param offset the first index to use
   * @param length the number of longs to write
   * @return the number of longs written
   */
  def writeSomeLongs(src: Array[Long], offset: Int, length: Int): Int = {
    val length = Math.min(writeAvail/8, length)
    var i = offset
    val l = offset + length
    while (i < l) {
      writeLong(src(i))
      i += 1
    }
    i - offset
  }

  /**
   * Writes all the longs of `src`.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough space for the data.
   *
   * @param src the array to write
   */
  def writeLongsLE(src: Array[Long]): Unit = writeLongsLE(src, 0, src.length)

  /**
   * Writes the content of `src`, starting at index `offset` and ending at index `offset+length-1`.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough space for the data or if the array index goes out
   * of bounds.
   *
   * @param src    the array to write
   * @param offset the first index to use
   * @param length the number of longs to write
   */
  def writeLongsLE(src: Array[Long], offset: Int, length: Int): Unit = {
    checkAvail(length)
    writeSomeLongsLE(src, offset, length)
  }

  /**
   * Writes at most `src.length` longs of `src` for each value.
   * Uses little-endian for each value.
   * Returns the actual number of longs written, possibly zero.
   *
   * @param src the array to write
   * @return the number of longs written
   */
  def writeSomeLongsLE(src: Array[Long]): Int = writeSomeLongsLE(src, 0, src.length)

  /**
   * Writes at most `length` longs of `src`, starting at index `offset`.
   * Uses little-endian for each value.
   * Returns the actual number of longs written, possibly zero.
   *
   * @param src    the array to write
   * @param offset the first index to use
   * @param length the number of longs to write
   * @return the number of longs written
   */
  def writeSomeLongsLE(src: Array[Long], offset: Int, length: Int): Int = {
    val length = Math.min(writeAvail/8, length)
    var i = offset
    val l = offset + length
    while (i < l) {
      writeLongLE(src(i))
      i += 1
    }
    i - offset
  }


  // -----------------------------------------------
  // ----- Write operations for arrays of floats -----
  /**
   * Writes all the floats of `src`.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough space for the data.
   *
   * @param src the array to write
   */
  def writeFloats(src: Array[Float]): Unit = writeFloats(src, 0, src.length)

  /**
   * Writes the content of `src`, starting at index `offset` and ending at index `offset+length-1`.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough space for the data or if the array index goes out
   * of bounds.
   *
   * @param src    the array to write
   * @param offset the first index to use
   * @param length the number of floats to write
   */
  def writeFloats(src: Array[Float], offset: Int, length: Int): Unit = {
    checkAvail(length)
    writeSomeFloats(src, offset, length)
  }

  /**
   * Writes at most `src.length` floats of `src`.
   * Uses big-endian for each value.
   * Returns the actual number of floats written, possibly zero.
   *
   * @param src the array to write
   * @return the number of floats written
   */
  def writeSomeFloats(src: Array[Float]): Int = writeSomeFloats(src, 0, src.length)

  /**
   * Writes at most `length` floats of `src`, starting at index `offset`.
   * Uses big-endian for each value.
   * Returns the actual number of floats written, possibly zero.
   *
   * @param src    the array to write
   * @param offset the first index to use
   * @param length the number of floats to write
   * @return the number of floats written
   */
  def writeSomeFloats(src: Array[Float], offset: Int, length: Int): Int = {
    val length = Math.min(writeAvail/4, length)
    var i = offset
    val l = offset + length
    while (i < l) {
      writeFloat(src(i))
      i += 1
    }
    i - offset
  }

  /**
   * Writes all the floats of `src`.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough space for the data.
   *
   * @param src the array to write
   */
  def writeFloatsLE(src: Array[Float]): Unit = writeFloatsLE(src, 0, src.length)

  /**
   * Writes the content of `src`, starting at index `offset` and ending at index `offset+length-1`.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough space for the data or if the array index goes out
   * of bounds.
   *
   * @param src    the array to write
   * @param offset the first index to use
   * @param length the number of floats to write
   */
  def writeFloatsLE(src: Array[Float], offset: Int, length: Int): Unit = {
    checkAvail(length)
    writeSomeFloatsLE(src, offset, length)
  }

  /**
   * Writes at most `src.length` floats of `src` for each value.
   * Uses little-endian for each value.
   * Returns the actual number of floats written, possibly zero.
   *
   * @param src the array to write
   * @return the number of floats written
   */
  def writeSomeFloatsLE(src: Array[Float]): Int = writeSomeFloatsLE(src, 0, src.length)

  /**
   * Writes at most `length` floats of `src`, starting at index `offset`.
   * Uses little-endian for each value.
   * Returns the actual number of floats written, possibly zero.
   *
   * @param src    the array to write
   * @param offset the first index to use
   * @param length the number of floats to write
   * @return the number of floats written
   */
  def writeSomeFloatsLE(src: Array[Float], offset: Int, length: Int): Int = {
    val length = Math.min(writeAvail/4, length)
    var i = offset
    val l = offset + length
    while (i < l) {
      writeFloatLE(src(i))
      i += 1
    }
    i - offset
  }


  // ------------------------------------------------
  // ----- Write operations for arrays of doubles -----
  /**
   * Writes all the doubles of `src`.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough space for the data.
   *
   * @param src the array to write
   */
  def writeDoubles(src: Array[Double]): Unit = writeDoubles(src, 0, src.length)

  /**
   * Writes the content of `src`, starting at index `offset` and ending at index `offset+length-1`.
   * Uses big-endian for each value.
   * Throws an exception if there isn't enough space for the data or if the array index goes out
   * of bounds.
   *
   * @param src    the array to write
   * @param offset the first index to use
   * @param length the number of doubles to write
   */
  def writeDoubles(src: Array[Double], offset: Int, length: Int): Unit = {
    checkAvail(length)
    writeSomeDoubles(src, offset, length)
  }

  /**
   * Writes at most `src.length` doubles of `src`.
   * Uses big-endian for each value.
   * Returns the actual number of doubles written, possibly zero.
   *
   * @param src the array to write
   * @return the number of doubles written
   */
  def writeSomeDoubles(src: Array[Double]): Int = writeSomeDoubles(src, 0, src.length)

  /**
   * Writes at most `length` doubles of `src`, starting at index `offset`.
   * Uses big-endian for each value.
   * Returns the actual number of doubles written, possibly zero.
   *
   * @param src    the array to write
   * @param offset the first index to use
   * @param length the number of doubles to write
   * @return the number of doubles written
   */
  def writeSomeDoubles(src: Array[Double], offset: Int, length: Int): Int = {
    val length = Math.min(writeAvail/8, length)
    var i = offset
    val l = offset + length
    while (i < l) {
      writeDouble(src(i))
      i += 1
    }
    i - offset
  }

  /**
   * Writes all the doubles of `src`.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough space for the data.
   *
   * @param src the array to write
   */
  def writeDoublesLE(src: Array[Double]): Unit = writeDoublesLE(src, 0, src.length)

  /**
   * Writes the content of `src`, starting at index `offset` and ending at index `offset+length-1`.
   * Uses little-endian for each value.
   * Throws an exception if there isn't enough space for the data or if the array index goes out
   * of bounds.
   *
   * @param src    the array to write
   * @param offset the first index to use
   * @param length the number of doubles to write
   */
  def writeDoublesLE(src: Array[Double], offset: Int, length: Int): Unit = {
    checkAvail(length)
    writeSomeDoublesLE(src, offset, length)
  }

  /**
   * Writes at most `src.length` doubles of `src` for each value.
   * Uses little-endian for each value.
   * Returns the actual number of doubles written, possibly zero.
   *
   * @param src the array to write
   * @return the number of doubles written
   */
  def writeSomeDoublesLE(src: Array[Double]): Int = writeSomeDoublesLE(src, 0, src.length)

  /**
   * Writes at most `length` doubles of `src`, starting at index `offset`.
   * Uses little-endian for each value.
   * Returns the actual number of doubles written, possibly zero.
   *
   * @param src    the array to write
   * @param offset the first index to use
   * @param length the number of doubles to write
   * @return the number of doubles written
   */
  def writeSomeDoublesLE(src: Array[Double], offset: Int, length: Int): Int = {
    val length = Math.min(writeAvail/8, length)
    var i = offset
    val l = offset + length
    while (i < l) {
      writeDoubleLE(src(i))
      i += 1
    }
    i - offset
  }
}

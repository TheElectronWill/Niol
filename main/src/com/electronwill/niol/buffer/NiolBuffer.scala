package com.electronwill.niol.buffer

import java.io.{InputStream, OutputStream}
import java.nio.ByteBuffer
import java.nio.channels.{GatheringByteChannel, ScatteringByteChannel}

import com.electronwill.niol._

abstract class NiolBuffer extends NiolInput with NiolOutput {
  /**
   * The buffer's capacity.
   *
   * @return the current capcity
   */
  def capacity: Int

  /**
   * The number of bytes that can be read from this buffer.
   * This number is always >= 0.
   * If `readableBytes == 0` then no byte can be read and `isReadable == false`.
   *
   * @return n > 0 if can read n bytes, 0 if closed input or empty buffer
   */
  def readableBytes: Int

  /**
   * Checks if the buffer is empty, that is, if `readableBytes == 0`.
   *
   * @return true if no byte can be read from this buffer
   */
  def isEmpty: Boolean

  /**
   * Checks if the buffer is full, that is, if `writableBytes == 0`.
   *
   * @return true if no byte can be written to this buffer
   */
  def isFull: Boolean

  override def isReadable: Boolean = !isEmpty

  override def isWritable: Boolean = !isFull

  override def isEnded: Boolean = false

  /**
   * Creates a "slice" that gives a limited access to the next `length` readable bytes.
   * No copy is involved. Throws an exception if `readableBytes < length`.
   * The returned buffer satisfies `readableBytes == length`.
   *
   * @param length the length of the slice
   * @return the slice
   */
  def slice(length: Int): NiolBuffer

  /**
   * Creates a "slice" that gives a limited access to the next `length` writable bytes.
   * No copy is involved. Throws an exception if `writableBytes < length`.
   * The returned buffer satisfies `writableBytes == length`.
   *
   * @param length the length of the slice
   * @return the writable slice
   */
  def writableSlice(length: Int): NiolBuffer

  /**
   * Returns a new buffer that shares its content with this buffer, but has independent read and
   * write positions. Initially the positions are the same but they evolve differently.
   *
   * @return the duplicate
   */
  def duplicate: NiolBuffer

  /**
   * Makes the buffer empty. After a call to `clear`, [[isReadable]] returns `false`,
   * [[readableBytes]] returns zero and [[writableBytes]] returns the buffer's capacity.
   * The buffer's capacity is not modified.
   */
  def clear(): Unit

  /**
   * Advances the read position by n bytes, as if n bytes had been read into some destination.
   *
   * @param n the number of bytes to skip
   */
  def advance(n: Int): Unit

  /**
   * Creates a new [[BiBuffer]] made of this buffer plus the other buffer, in this order.
   *
   * @param other the other buffer
   * @return a new BiBuffer(thisBuffer, other)
   */
  final def +(other: NiolBuffer): BiBuffer = new BiBuffer(this, other)

  // ----- Protected operations for reading -----
  /** Implements read without necessarily checking for available space. */
  protected[niol] def _read(to: Array[Byte], off: Int, len: Int): Unit

  /** Implements read without necessarily checking for available space. */
  protected[niol] def _read(to: ByteBuffer, len: Int): Unit

  /** Implements read without necessarily checking for available space. */
  protected[niol] def _read(to: NiolBuffer, len: Int): Unit

  /** Checks if at least `n` bytes can be written */
  protected[niol] def checkReadable(n: Int): Unit = {
    val avail = readableBytes
    if (avail < n) throw new NotEnoughDataException(n, avail)
  }

  // ----- Writes specific to NiolBuffer -----
  /**
   * Writes at most `maxBytes` bytes from `src`.
   * Returns the actual number of bytes written, possibly zero.
   *
   * @param src the channel providing the data
   * @return the number of bytes read from `src`, or -1 if the end of the stream has been reached
   */
  def writeSome(src: ScatteringByteChannel): Int

  /**
   * Writes some bytes from `src`.
   * Returns the actual number of bytes written, possibly zero.
   *
   * @param src    the stream providing the data
   * @return the number of bytes read from `src`, or -1 if the end of the stream has been reached
   */
  def writeSome(src: InputStream): Int

  // ----- Reads specific to NiolBuffer -----
  /**
   * Reads some bytes and writes them to `dst`.
   * Returns the actual number of bytes read, possibly zero.
   *
   * @param dst the channel to write to
   * @return the number of bytes read
   */
  def readSome(dst: GatheringByteChannel): Int

  /**
   * Reads some bytes and writes them to `dst`.
   * Returns the actual number of bytes read, possibly zero.
   *
   * @param dst the channel to write to
   * @return the number of bytes read
   */
  def readSome(dst: OutputStream): Int

  // ----- Reads overriden -----
  override def read(dst: ByteBuffer): Unit = {
    val rem = dst.remaining()
    checkReadable(rem)
    _read(dst, rem)
  }

  override def readSome(dst: ByteBuffer): Int = {
    val len = math.min(dst.remaining, readableBytes)
    _read(dst, len)
    len
  }

  override def readBytes(dst: Array[Byte], offset: Int, length: Int): Unit = {
    checkReadable(length)
    _read(dst, offset, length)
  }

  override def readSomeBytes(dst: Array[Byte], offset: Int, length: Int): Int = {
    val len = math.min(readableBytes, length)
    _read(dst, offset, len)
    len
  }

  override def read(dst: NiolBuffer): Unit = {
    val writable = dst.writableBytes
    checkReadable(writable)
    _read(dst, writable)
  }

  // ----- toSomething -----
  /**
   * Reads all the readable bytes of this buffer into a new byte array.
   *
   * @return the byte array containing all the bytes read
   */
  def toArray(): Array[Byte] = {
    val len = readableBytes
    val array = new Array[Byte](len)
    _read(array, 0, len)
    array
  }

  override def toString: String = {
    s"""${getClass.getSimpleName}(
       |  capacity: $capacity,
       |  isEmpty: $isEmpty,
       |  isFull: $isFull,
       |  readable: $readableBytes,
       |  writable: $writableBytes
       |)""".stripMargin
  }
}

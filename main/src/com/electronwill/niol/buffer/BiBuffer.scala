package com.electronwill.niol.buffer

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.{GatheringByteChannel, ScatteringByteChannel}

import com.electronwill.niol.NiolOutput
import com.electronwill.niol.buffer.storage.StorageProvider

/**
 * A buffer made of two buffers.
 *
 * @param a the first buffer
 * @param b the second buffer
 */
final class BiBuffer(
    private[this] val a: NiolBuffer,
    private[this] val b: NiolBuffer)
  extends NiolBuffer {

  // ----- Buffer status -----
  override def capacity: Int = a.capacity + b.capacity
  override def readableBytes: Int = a.readableBytes + b.readableBytes
  override def writableBytes: Int = a.writableBytes + b.writableBytes

  override def isEmpty: Boolean = a.isEmpty
  override def isFull: Boolean = a.isFull && b.isFull

  // ----- Reads -----
  override protected[this] def _read(): Byte = if (a.isEmpty) b.read() else a.read()
  override protected[this] def _read(to: Array[Byte], off: Int, len: Int): Unit = {
    val read = a.readSomeBytes(to, off, len)
    if (read <= len) {
      b.readBytes(to, off, len)
    }
  }
  override protected[this] def _read(to: ByteBuffer, len: Int): Unit = ???

  override protected[this] def _read(to: NiolOutput, len: Int): Unit = ???

  override def read(dst: ByteBuffer): Unit = {
    a.readSome(dst)
    b.read(dst)
  }

  override def readSome(dst: ByteBuffer): Unit = {
    a.readSome(dst)
    b.read(dst)
  }

  override def read(dst: NiolOutput, length: Int): Unit = {
    val read = a.readSome(dst, length)
    val diff = length - read
    if (diff > 0) {
      b.read(dst, diff)
    }
  }

  override def readSome(dst: NiolOutput, maxLength: Int): Int = {
    a.readSome(dst, maxLength)
    b.readSome(dst, maxLength)
  }

  override def read(dst: NiolBuffer): Unit = {
    a.readSome(dst)
    b.read(dst)
  }

  override def readSome(dst: GatheringByteChannel, maxBytes: Int): Int = {
    val read = a.readSome(dst, maxBytes)
    val diff = maxBytes - read
    if (diff > 0) {
      read + b.readSome(dst, diff)
    } else {
      read
    }
  }

  override def readSome(dst: OutputStream, maxLength: Int): Int = {
    val read = a.readSome(dst, maxLength)
    val diff = maxLength - read
    if (diff > 0) {
      read + b.readSome(dst, diff)
    } else {
      read
    }
  }

  override def read(dst: GatheringByteChannel, length: Int): Unit = {
    val read = a.readSome(dst, length)
    b.read(dst, length - read)
  }

  // ----- Writes -----
  override protected[niol] def _write(v: Byte): Unit = if (a.isFull) a.write(v) else b.write(v)

  override protected[niol] def _write(from: Array[Byte], off: Int, len: Int): Unit = {
    if (a.isFull) {
      b.write(from, off, len)
    } else {
      val la = math.min(a.readableBytes, len)
      a.write(from, off, len)
      val diff = len - la
      if (diff > 0) {
        b.write(from, off + la, diff)
      }
    }
  }

  override protected[niol] def _write(from: ByteBuffer, len: Int): Unit = {
    if (a.isFull) {
      b._write(from, len)
    } else {
      val la = math.min(a.readableBytes, len)
      a._write(from, la)
      val diff = len - la
      if (diff > 0) {
        b._write(from, diff)
      }
    }
  }

  override def writeSome(src: ByteBuffer): Unit = {
    a.writeSome(src)
    b.writeSome(src)
  }

  override def write(src: ScatteringByteChannel, length: Int): Unit = {
    val written = a.writeSome(src, length)
    b.write(src, length - written)
  }

  override def writeSome(src: ScatteringByteChannel, maxBytes: Int): Int = {
    val written = a.writeSome(src, maxBytes)
    b.writeSome(src, maxBytes - written)
  }

  // ----- Buffer methods -----
  override def copy(storageSource: StorageProvider): NiolBuffer = {
    val readable = readableBytes
    val bs = storageSource(readable)
    val bb = bs.byteBuffer
    a.readSome(bb)
    b.read(bb)
    new CircularBuffer(bs)
  }

  override def slice(length: Int): NiolBuffer = {
    if (a.isEmpty) {
      b.slice(length)
    } else if (a.readableBytes >= length) {
      a.slice(length)
    } else {
      new BiBuffer(a.duplicate, b.slice(length - a.readableBytes))
    }
  }

  override def writableSlice(length: Int): NiolBuffer = {
    if (a.isEmpty) {
      b.writableSlice(length)
    } else if (a.writableBytes >= length) {
      a.writableSlice(length)
    } else {
      new BiBuffer(a.duplicate, b.writableSlice(length - a.readableBytes))
    }
  }

  override def duplicate: NiolBuffer = new BiBuffer(a.duplicate, b.duplicate)

  override def clear(): Unit = {
    a.clear()
    b.clear()
  }

  override def advance(n: Int): Unit = {
    val nA = math.min(n, a.readableBytes)
    val nB = n - nA
    a.advance(nA)
    if (nB > 0) {
      b.advance(nB)
    }
  }
}

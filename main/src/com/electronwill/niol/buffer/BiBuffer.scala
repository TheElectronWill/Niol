package com.electronwill.niol.buffer

import java.io.{InputStream, OutputStream}
import java.nio.ByteBuffer
import java.nio.channels.{GatheringByteChannel, ScatteringByteChannel}

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

  override def isEmpty: Boolean = a.isEmpty && b.isEmpty
  override def isFull: Boolean = a.isFull && b.isFull

  // ----- Reads -----
  override protected[niol] def _read(): Byte = if (a.isEmpty) b._read() else a._read()

  override protected[niol] def _read(to: Array[Byte], off: Int, len: Int): Unit = {
    val nA = math.min(a.readableBytes, len)
    val nB = len - nA
    a._read(to, off, nA)
    if (nB > 0) {
      b._read(to, off+nA, nB)
    }
  }

  override protected[niol] def _read(to: ByteBuffer, len: Int): Unit = {
    val nA = math.min(a.readableBytes, len)
    val nB = len - nA
    a._read(to, nA)
    if (nB > 0) {
      b._read(to, nB)
    }
  }

  override protected[niol] def _read(to: NiolBuffer, len: Int): Unit = {
    val nA = math.min(a.readableBytes, len)
    val nB = len - nA
    a._read(to, nA)
    if (nB > 0) {
      b._read(to, nB)
    }
  }

  override def read(dst: ByteBuffer): Unit = {
    a.readSome(dst)
    b.read(dst)
  }

  override def readSome(dst: ByteBuffer): Int = {
    a.readSome(dst) + b.readSome(dst)
  }

  override def read(dst: NiolBuffer): Unit = {
    while (a.isReadable) a.readSome(dst)
    b.read(dst)
  }

  override def readSome(dst: NiolBuffer): Int = {
    if (a.isReadable) a.readSome(dst) else b.readSome(dst)
  }

  override def readSome(dst: GatheringByteChannel): Int = {
    if (a.isReadable) a.readSome(dst) else b.readSome(dst)
  }

  override def readSome(dst: OutputStream): Int = {
    if (a.isReadable) a.readSome(dst) else b.readSome(dst)
  }

  // ----- Writes -----
  override protected[niol] def _write(v: Byte): Unit = if (a.isFull) b._write(v) else a._write(v)

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

  override def writeSome(src: ByteBuffer): Int = {
    a.writeSome(src) + b.writeSome(src)
  }

  override def write(src: NiolBuffer): Unit = {
    while (a.isWritable) a.writeSome(src)
    b.write(src)
  }

  override def writeSome(src: NiolBuffer): Int = {
    if (a.isWritable) a.writeSome(src) else b.writeSome(src)
  }

  override def writeSome(src: ScatteringByteChannel): Int = {
    if (a.isWritable) a.writeSome(src) else b.writeSome(src)
  }

  override def writeSome(src: InputStream): Int = {
    if (a.isWritable) a.writeSome(src) else b.writeSome(src)
  }

  // ----- Buffer methods -----
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

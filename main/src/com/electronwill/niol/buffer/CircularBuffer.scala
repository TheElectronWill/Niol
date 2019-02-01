package com.electronwill.niol.buffer

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.{GatheringByteChannel, ScatteringByteChannel}

import com.electronwill.niol.NiolOutput
import com.electronwill.niol.buffer.storage.{BytesStorage, StorageProvider}
import com.electronwill.niol.utils.isPositivePowerOfTwo

/**
 * A fixed-length circular buffer.
 *
 * @param storage stores the bytes
 */
final class CircularBuffer private(
    private[this] val storage: BytesStorage,
    private[this] var readpos: Int,
    private[this] var writepos: Int,
    private[this] var lastOpWrite: Boolean)
  extends NiolBuffer {
  require(isPositivePowerOfTwo(storage.capacity), "The storage's capacity must be a power of 2")

  def this(storage: BytesStorage) = this(storage, 0, 0, false)

  private[this] val capMinus1 = capacity - 1

  // ----- Buffer status -----
  override def capacity: Int = storage.capacity
  override def readableBytes: Int = if (isFull) capacity else count(readpos, writepos)
  override def writableBytes: Int = if (isEmpty) capacity else count(writepos, readpos)

  override def isEmpty = (readpos == writepos && !lastOpWrite)
  override def isFull = (readpos == writepos && lastOpWrite)

  private[this] def count(begin: Int, end: Int) = (end - begin) & capMinus1

  // ----- Reads -----
  override protected[this] def _read(): Byte = {
    val p = readpos
    readpos = (p + 1) & capMinus1
    lastOpWrite = false
    storage.get1(p)
  }

  override protected[this] def _read(to: Array[Byte], off: Int, len: Int): Unit = {
    val p = readpos
    val w = writepos
    if (p < w) {
      // [0...p...w...cap-1]
      storage.get(p, to, off, len) // [p...i], i<w
    } else {
      // [0...w...p...cap-1]
      val right = capMinus1 + 1 - p
      if (right <= len) {
        storage.get(p, to, off, len) // [p...p+len-1]
      } else {
        storage.get(p, to, off, right) // [p...cap-1]
        storage.get(0, to, off + right, len - right) // [0...i], i<w
      }
    }
    readpos = (p + len) & capMinus1
    lastOpWrite = false
  }

  override protected[this] def _read(to: ByteBuffer, len: Int): Unit = {
    val p = readpos
    val w = writepos
    if (p < w) {
      // [0...p...w...cap-1]
      storage.get(p, to, len) // [p...p+len-1], p+len-1<w
    } else {
      // [0...w...p...cap-1]
      val right = capMinus1 + 1 - p
      if (right <= len) {
        storage.get(p, to, len) // [p...p+len-1]
      } else {
        storage.get(p, to, right) // [p...cap-1]
        storage.get(0, to, len - right) // [0...i], i<w
      }
    }
    readpos = (p + len) & capMinus1
    lastOpWrite = false
  }
  override protected[this] def _read(to: NiolOutput, len: Int): Unit = {
    val p = readpos
    val w = writepos
    val bb = storage.byteBuffer
    bb.position(p)
    if (p < w) {
      // [0...p...w...cap-1]
      to._write(bb, len) // [p...p+len-1], p+len-1<w
    } else {
      // [0...w...p...cap-1]
      val right = capMinus1 + 1 - p
      if (right <= len) {
        to._write(bb, len) // [p...p+len-1]
      } else {
        to._write(bb, right) // [p...cap-1]
        bb.position(0)
        to._write(bb, len - right) // [0...i], i<w
      }
    }
    readpos = (p + len) & capMinus1
    lastOpWrite = false
  }

  // ----- Partial reads ------
  override def readSome(dst: GatheringByteChannel, maxBytes: Int): Int = {
    val len = math.min(readableBytes, maxBytes)
    val p = readpos
    val w = writepos
    val written =
      if (p < w) {
        // [0...p...w...cap-1]
        storage.get(p, dst, len) // [p...i], i<w
      } else {
        // [0...w...p...cap-1]
        val right = capMinus1 + 1 - p
        if (right <= len) {
          storage.get(p, dst, len) // [p...p+len-1]
        } else {
          storage.get(p, dst, right) // [p...cap-1]
          + storage.get(0, dst, len - right) // [0...i], i<w
        }
      }
    readpos = (p + len) & capMinus1
    lastOpWrite = false
    written
  }

  override def readSome(dst: OutputStream, maxLength: Int): Int = {
    val len = math.min(readableBytes, maxLength)
    val buff = new Array[Byte](len)
    _read(buff, 0, len)
    dst.write(buff)
    len
  }

  // ----- Writes -----
  override protected[niol] def _write(b: Byte): Unit = {
    val p = writepos
    writepos = (p + 1) & capMinus1
    lastOpWrite = true
    storage.put1(p, b)
  }

  override protected[niol] def _write(from: Array[Byte], off: Int, len: Int): Unit = {
    val p = writepos
    val r = readpos
    if (p < r) {
      storage.put(p, from, off, len)
      writepos = (p + len) & capMinus1
    } else if (p == r) {
      assert(isEmpty)
      storage.put(0, from, off, len)
      readpos = 0
      writepos = len
    } else {
      val l = math.min(len, capacity - p)
      storage.put(p, from, off, l)
      storage.put(0, from, off + l, len - l)
      writepos = (p + len) & capMinus1
    }
    lastOpWrite = true
  }
  override protected[niol] def _write(from: ByteBuffer, len: Int): Unit = {
    val p = writepos
    val r = readpos
    if (p < r) {
      storage.put(p, from, len)
      writepos = (p + len) & capMinus1
    } else if (p == r) {
      assert(isEmpty)
      storage.put(0, from, len)
      readpos = 0
      writepos = len
    } else {
      val l = math.min(len, capacity - p)
      storage.put(p, from, l)
      storage.put(0, from, len - l)
      writepos = (p + len) & capMinus1
    }
    lastOpWrite = true
  }

  override def writeSome(src: ByteBuffer): Unit = {
    _write(src, math.min(src.remaining, writableBytes))
  }

  override def writeSome(src: ScatteringByteChannel, maxBytes: Int): Int = {
    val len = math.min(readableBytes, maxBytes)
    val p = writepos
    val r = readpos
    val read =
      if (p < r) {
        writepos = (p + len) & capMinus1
        storage.put(p, src, len)
      } else if (p == r) {
        assert(isEmpty)
        readpos = 0
        writepos = len
        storage.put(0, src, len)
      } else {
        writepos = (p + len) & capMinus1
        val l = math.min(len, capacity - p)
        storage.put(p, src, l)
        + storage.put(0, src, len - l)
      }
    lastOpWrite = true
    read
  }

  // ----- Buffer methods -----
  override def copy(storageSource: StorageProvider): NiolBuffer = {
    val readable = readableBytes
    val bs = storageSource.getStorage(readable)
    _read(bs.byteBuffer, readable)
    new CircularBuffer(bs)
  }

  override def slice(length: Int): NiolBuffer = {
    checkReadable(length)
    val end = (readpos + length) & capMinus1
    new CircularBuffer(storage, readpos, end, isEmpty)
  }

  override def writableSlice(length: Int): NiolBuffer = {
    checkWritable(length)
    val end = (writepos + length) & capMinus1
    new CircularBuffer(storage, end, writepos, isFull)
  }

  override def duplicate: NiolBuffer = new CircularBuffer(storage, readpos, writepos, lastOpWrite)

  override def clear(): Unit = {
    readpos = 0
    writepos = 0
    lastOpWrite = false
  }

  override def advance(n: Int): Unit = {
    val r = readableBytes
    if (n > r) throw new IndexOutOfBoundsException(s"Cannot advance by $n bytes: only $r readable")
    readpos = (readpos + n) & capMinus1
  }
}

object CircularBuffer {
  import java.nio.ByteBuffer

  def wrap(bb: ByteBuffer): CircularBuffer = {
    val sto = BytesStorage.wrap(bb)
    new CircularBuffer(sto, bb.position(), bb.limit(), true)
  }

  def wrap(bytes: Array[Byte], readOffset: Int, readLength: Int): CircularBuffer = {
    val sto = BytesStorage.wrap(ByteBuffer.wrap(bytes, readOffset, readLength))
    new CircularBuffer(sto, readOffset, readOffset+readLength, true)
  }
}

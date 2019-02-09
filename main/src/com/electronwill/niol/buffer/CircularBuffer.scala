package com.electronwill.niol.buffer

import java.io.{InputStream, OutputStream}
import java.nio.ByteBuffer
import java.nio.channels.{GatheringByteChannel, ScatteringByteChannel}

import com.electronwill.niol.TMP_BUFFER_SIZE
import com.electronwill.niol.buffer.storage.BytesStorage
import com.electronwill.niol.utils.{isPowerOfTwo, positively}

/**
 * A fixed-length circular buffer (aka "ring buffer").
 *
 * @param storage stores the bytes
 */
abstract class CircularBuffer protected(
    protected[this] val storage: BytesStorage,
    protected[this] var readpos: Int,
    protected[this] var writepos: Int,
    protected[this] var lastOpWrite: Boolean)
  extends NiolBuffer {

  // ----- Buffer status -----
  override def capacity: Int = storage.capacity
  override def readableBytes: Int = if (isFull) capacity else countCircular(readpos, writepos)
  override def writableBytes: Int = if (isEmpty) capacity else countCircular(writepos, readpos)

  override def isEmpty = (readpos == writepos && !lastOpWrite)
  override def isFull = (readpos == writepos && lastOpWrite)

  /** Counts the space between two positions: (begin + end) mod capacity */
  protected def countCircular(begin: Int, end: Int): Int

  /** Computes (a + b) mod capacity, for (a+b) >= 0 */
  protected def addCircular(a: Int, b: Int): Int

  // ----- Reads -----
  override protected[niol] def _read(): Byte = {
    val p = readpos
    readpos = addCircular(p, 1)
    lastOpWrite = false
    storage.get1(p)
  }

  override protected[niol] def _read(to: Array[Byte], off: Int, len: Int): Unit = {
    val p = readpos
    val w = writepos
    if (p < w) {
      // [0...p...w...cap-1]
      storage.get(p, to, off, len) // [p...i], i<w
    } else {
      // [0...w...p...cap-1]
      val right = capacity - p
      if (right >= len) {
        storage.get(p, to, off, len) // [p...p+len-1]
      } else {
        storage.get(p, to, off, right) // [p...cap-1]
        storage.get(0, to, off + right, len - right) // [0...i], i<w
      }
    }
    readpos = addCircular(p, len)
    lastOpWrite = false
  }

  override protected[niol] def _read(to: ByteBuffer, len: Int): Unit = {
    val p = readpos
    val w = writepos
    if (p < w) {
      // [0...p...w...cap-1]
      storage.get(p, to, len) // [p...p+len-1], p+len-1<w
    } else {
      // [0...w...p...cap-1]
      val right = capacity - p
      if (right >= len) {
        storage.get(p, to, len) // [p...p+len-1]
      } else {
        storage.get(p, to, right) // [p...cap-1]
        storage.get(0, to, len - right) // [0...i], i<w
      }
    }
    readpos = addCircular(p, len)
    lastOpWrite = false
  }
  override protected[niol] def _read(to: NiolBuffer, len: Int): Unit = {
    val p = readpos
    val w = writepos
    val bb = storage.byteBuffer
    bb.position(p)
    if (p < w) {
      // [0...p...w...cap-1]
      to._write(bb, len) // [p...p+len-1], p+len-1<w
    } else {
      // [0...w...p...cap-1]
      val right = capacity - p
      if (right >= len) {
        to._write(bb, len) // [p...p+len-1]
      } else {
        to._write(bb, right) // [p...cap-1]
        bb.position(0)
        to._write(bb, len - right) // [0...i], i<w
      }
    }
    readpos = addCircular(p, len)
    lastOpWrite = false
  }

  // ----- Partial reads ------
  override def readSome(dst: GatheringByteChannel): Int = {
    val len = readableBytes
    val r = readpos
    val w = writepos
    val read =
      if (r < w) {
        // [0...r...w...cap-1]
        storage.get(r, dst, len) // [r...i], i<w
      } else {
        // [0...w...r...cap-1]
        val right = capacity - r
        if (right >= len) {
          storage.get(r, dst, len) // [r...r+len-1]
        } else {
          val rRight = storage.get(r, dst, right) // [r...cap-1]
          val rLeft  = storage.get(0, dst, len - right) // [0...i], i<w
          rRight + rLeft
        }
      }
    if (read > 0) {
      readpos = addCircular(r, read)
      lastOpWrite = false
    }
    read
  }

  override def readSome(dst: OutputStream): Int = {
    val len = math.min(readableBytes, TMP_BUFFER_SIZE)
    val buff = new Array[Byte](len)
    _read(buff, 0, len)
    dst.write(buff)
    len
  }

  override def readSome(dst: NiolBuffer): Int = {
    val len = math.min(readableBytes, dst.writableBytes)
    _read(dst, len)
    len
  }

  // ----- Writes -----
  override protected[niol] def _write(b: Byte): Unit = {
    val p = writepos
    writepos = addCircular(p, 1)
    lastOpWrite = true
    storage.put1(p, b)
  }

  override protected[niol] def _write(from: Array[Byte], off: Int, len: Int): Unit = {
    val w = writepos
    val r = readpos
    if (w < r) {
      storage.put(w, from, off, len)
      writepos = addCircular(w, len)
    } else if (w == r) {
      assert(isEmpty)
      storage.put(0, from, off, len)
      readpos = 0
      writepos = len
    } else {
      val l = math.min(len, capacity - w)
      storage.put(w, from, off, l)
      storage.put(0, from, off + l, len - l)
      writepos = addCircular(w, len)
    }
    lastOpWrite = true
  }

  override protected[niol] def _write(from: ByteBuffer, len: Int): Unit = {
    val w = writepos
    val r = readpos
    if (w < r) {
      // [0...w...r...cap-1]
      storage.put(w, from, len)
      writepos = addCircular(w, len)
    } else if (w == r) {
      assert(isEmpty)
      storage.put(0, from, len)
      readpos = 0
      writepos = len
    } else {
      // [0...r...w...cap-1]
      val l = math.min(len, capacity - w)
      storage.put(w, from, l)
      storage.put(0, from, len - l)
      writepos = addCircular(w, len)
    }
    lastOpWrite = true
  }

  override def writeSome(src: ByteBuffer): Int = {
    val len = math.min(src.remaining, writableBytes)
    _write(src, len)
    len
  }

  override def writeSome(src: ScatteringByteChannel): Int = {
    if (isFull) return 0
    val maxLen = writableBytes
    var w = writepos
    val r = readpos
    val written =
      if (w < r) {
        storage.put(w, src, maxLen)
      } else if (w == r) {
        // empty buffer => we can restart to index 0
        assert(isEmpty)
        readpos = 0
        w = 0
        storage.put(0, src, maxLen)
      } else {
        val l = math.min(maxLen, capacity - w)
        val wRight = storage.put(w, src, l)
        if (wRight > 0) {
          val wLeft = storage.put(0, src, maxLen - l)
          wRight + positively(wLeft)
        } else {
          wRight
        }
      }
    if (written > 0) {
      writepos = addCircular(w, written)
      lastOpWrite = true
    }
    written
  }

  override def writeSome(src: InputStream): Int = {
    if (isFull) return 0
    val maxLen = writableBytes
    var w = writepos
    val r = readpos
    val written =
      if (storage.byteBuffer.hasArray) {
        // ByteBuffer is backed by an array => write to it directly
        val arr = storage.byteBuffer.array
        if (w < r) {
          src.read(arr, w, maxLen)
        } else if (w == r) {
          // empty buffer => we can restart to index 0
          assert(isEmpty)
          readpos = 0
          w = 0
          src.read(arr, 0, maxLen)
        } else {
          val l = math.min(maxLen, capacity - w)
          val wRight = src.read(arr, w, l)
          if (wRight > 0) {
            val wLeft = src.read(arr, 0, maxLen - l)
            wRight + positively(wLeft)
          } else {
            wRight
          }
        }
      } else {
        // ByteBuffer isn't backed by an array => use a temporary buffer
        val arr = new Array[Byte](math.min(maxLen, TMP_BUFFER_SIZE))
        src.read(arr)
      }
    if (written > 0) {
      writepos = addCircular(w, written)
      lastOpWrite = true
    }
    written
  }

  override def write(src: NiolBuffer): Unit = src.read(this)

  override def writeSome(src: NiolBuffer): Int = src.readSome(this)


  // ----- Buffer methods -----
  override def slice(length: Int): NiolBuffer = {
    checkReadable(length)
    val end = addCircular(readpos, length)
    CircularBuffer(storage, readpos, end, isFull)
  }

  override def writableSlice(length: Int): NiolBuffer = {
    checkWritable(length)
    val end = addCircular(writepos, length)
    CircularBuffer(storage, end, writepos, isEmpty)
  }

  override def duplicate: NiolBuffer = CircularBuffer(storage, readpos, writepos, lastOpWrite)

  override def clear(): Unit = {
    readpos = 0
    writepos = 0
    lastOpWrite = false
  }

  override def advance(n: Int): Unit = {
    val r = readableBytes
    if (n > r) throw new IndexOutOfBoundsException(s"Cannot advance by $n bytes: only $r readable")
    readpos = addCircular(readpos, n)
    lastOpWrite = false
  }

  override def toString: String = s"${super.toString} and (r=$readpos, w=$writepos)"
}

object CircularBuffer {
  import java.nio.ByteBuffer

  private[buffer] def apply(s: BytesStorage, r: Int, w: Int, l: Boolean): CircularBuffer = {
    if (isPowerOfTwo(s.capacity)) {
      new CircularBufferOptiMod2(s, r, w, l)
    } else {
      new CircularBufferGeneralMod(s, r, w, l)
    }
  }

  def apply(storage: BytesStorage): CircularBuffer = {
    apply(storage, 0, 0, false)
  }

  def wrap(bb: ByteBuffer): CircularBuffer = {
    val sto = BytesStorage.wrap(bb)
    apply(sto, bb.position(), bb.limit(), true)
  }

  def wrap(bytes: Array[Byte], readOffset: Int, readLength: Int): CircularBuffer = {
    val sto = BytesStorage.wrap(ByteBuffer.wrap(bytes, readOffset, readLength))
    apply(sto, readOffset, readOffset+readLength, true)
  }
}

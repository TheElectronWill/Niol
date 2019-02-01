package com.electronwill.niol.buffer.storage
import java.nio.ByteBuffer
import java.nio.channels.{ReadableByteChannel, WritableByteChannel}

/**
 * A linear, indexed storage of bytes. Based on the java NIO [[ByteBuffer]] for performance.
 * This class isn't thread-safe.
 */
class BytesStorage(protected[this] var bb: ByteBuffer, private[this] var pool: StoragePool) {
  /** The storage's id. Makes bufferPool.putBack O(1) */
  private[niol] var id: Int = 0

  /** @return the storage's capacity in bytes */
  final def capacity: Int = bb.capacity()

  /** @return the underlying ByteBuffer */
  final def byteBuffer: ByteBuffer = bb

  /** Returns the storage to the pool. A discarded storage cannot be used anymore. */
  def discardNow(): Unit = {
    if (pool != null) {
      pool.putBack(id, bb)
      pool = null // Prevents this storage from being discarded more than once
    }
    bb = null // Prevents this storage from being used after being discarded
  }

  /** @return true if manually discarded by `discardNow`, false otherwise */
  private[niol] def isDiscarded: Boolean = (pool == null)

  // ----- Get -----
  def get1(idx: Int): Byte  = bb.get(idx)
  def get2(idx: Int): Short = bb.getShort(idx)
  def get4(idx: Int): Int   = bb.getInt(idx)
  def get8(idx: Int): Long  = bb.getLong(idx)

  /** reads `this[idx0, idx0+len[` into `to[off, off+len[` */
  def get(idx0: Int, to: BytesStorage, off: Int, len: Int): Unit = {
    to.put(off, this, idx0, len)
  }

  /** reads `this[idx0, idx0+len[` into `to[off, off+len[` */
  def get(idx0: Int, to: Array[Byte], off: Int, len: Int): Unit = {
    bb.position(idx0)
    bb.get(to, off, len)
  }

  /** reads `this[idx0, idx0+len[` into `to[to.position, to.position+len[` */
  def get(idx0: Int, to: ByteBuffer, len: Int): Unit = {
    bb.position(idx0).limit(idx0 + len)
    to.put(bb)
    bb.clear()
  }

  /** @return the number of bytes written to the channel */
  def get(idx0: Int, to: WritableByteChannel, len: Int): Int = {
    bb.position(idx0).limit(idx0 + len)
    val nWritten = to.write(bb)
    bb.clear()
    nWritten
  }

  // ----- Put -----
  def put1(idx: Int, value: Byte): Unit  = bb.put(idx, value)
  def put2(idx: Int, value: Short): Unit = bb.putShort(idx, value)
  def put4(idx: Int, value: Int): Unit   = bb.putInt(idx, value)
  def put8(idx: Int, value: Long): Unit  = bb.putLong(idx, value)

  /** writes `from[off, off+len[` to `this[idx0, idx0+len[` */
  def put(idx0: Int, from: BytesStorage, off: Int, len: Int): Unit = {
    bb.position(idx0)
    val oo = from.byteBuffer
    oo.position(off).limit(len)
    bb.put(oo)
    oo.clear()
  }

  /** writes `from[off, off+len[` to `this[idx0, idx0+len[` */
  def put(idx0: Int, from: Array[Byte], off: Int, len: Int): Unit = {
    bb.position(idx0)
    bb.put(from, off, len)
  }

  /** writes `from[from.position, from.position+len[` to `this[idx0, idx0+len[` */
  def put(idx0: Int, from: ByteBuffer, len: Int): Unit = {
    bb.position(idx0).limit(idx0 + len)
    bb.put(from)
    bb.clear()
  }

  /** @return the number of bytes read from the channel, or -1 if the EOS has been reached */
  def put(idx0: Int, from: ReadableByteChannel, len: Int): Int = {
    bb.position(idx0).limit(idx0 + len)
    val nRead = from.read(bb)
    bb.clear()
    nRead
  }
}
/** Companion of BytesStorage */
object BytesStorage {
  /**
   * Allocates a new BytesStorage on the heap
   *
   * @param capacity the minimal capacity
   * @return a new BytesStorage of capacity &ge; capacity
   */
  def allocateHeap(capacity: Int) = wrap(ByteBuffer.allocate(capacity))

  /**
   * Allocates a new BytesStorage off heap
   *
   * @param capacity the minimal capacity
   * @return a new direct BytesStorage of capacity &ge; capacity
   */
  def allocateDirect(capacity: Int) = wrap(ByteBuffer.allocateDirect(capacity))

  /**
   * Creates a new BytesStorage whose content is shared with the given ByteBuffer.
   *
   * @param bb ByteBuffer to wrap
   * @return a new BytesStorage that wraps the given buffer
   */
  def wrap(bb: ByteBuffer) = {
    bb.clear()
    new BytesStorage(bb, null)
  }

  /**
   * Creates a new BytesStorage whose
   * @param ba
   * @return
   */
  def wrap(ba: Array[Byte]) = new BytesStorage(ByteBuffer.wrap(ba), null)
}

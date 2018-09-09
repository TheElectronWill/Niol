package com.electronwill.niol.buffer.storage
import java.nio.ByteBuffer
import java.nio.channels.{ReadableByteChannel, WritableByteChannel}

/**
 * A basic indexed storage of bytes.
 */
class BytesStorage(protected[this] var bb: ByteBuffer, private[this] var bufferPool: StoragePool) {

  final def capacity: Int = bb.capacity()

  final def bytes: ByteBuffer = bb

  def discardNow(): Unit = {
    if (bufferPool != null) {
      bufferPool.putBack(bb)
      bufferPool = null // Prevents this storage from being discarded more than once
    }
    bb = null // Prevents this storage from being used after being discarded
  }

  // ----- Get -----
  def get1(idx: Int): Byte  = bb.get(idx)
  def get2(idx: Int): Short = bb.getShort(idx)
  def get4(idx: Int): Int   = bb.getInt(idx)
  def get8(idx: Int): Long  = bb.getLong(idx)

  def get(idx0: Int, to: BytesStorage, off: Int, len: Int): Unit = {
    to.put(off, this, idx0, len)
  }

  def get(idx0: Int, to: Array[Byte], off: Int, len: Int): Unit = {
    bb.position(idx0)
    bb.get(to, off, len)
  }

  def get(idx0: Int, to: ByteBuffer, len: Int): Unit = {
    bb.position(idx0).limit(idx0 + len)
    to.put(bb)
    bb.clear()
  }

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

  def put(idx0: Int, from: BytesStorage, off: Int, len: Int): Unit = {
    bb.position(idx0)
    val oo = from.bytes
    oo.position(off).limit(len)
    bb.put(oo)
    oo.clear()
  }

  def put(idx0: Int, from: Array[Byte], off: Int, len: Int): Unit = {
    bb.position(idx0)
    bb.put(from, off, len)
  }

  def put(idx0: Int, from: ByteBuffer, len: Int): Unit = {
    bb.position(idx0).limit(idx0 + len)
    bb.put(from)
    bb.clear()
  }

  def put(idx0: Int, from: ReadableByteChannel, len: Int): Int = {
    bb.position(idx0).limit(idx0 + len)
    val nRead = from.read(bb)
    bb.clear()
    nRead
  }
}

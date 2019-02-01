package com.electronwill.niol.buffer
import java.nio.ByteBuffer

import com.electronwill.niol.NiolOutput
import com.electronwill.niol.buffer.storage.StorageProvider

/**
 * An in-memory output that expands automatically to accept all the data.
 * Expansion is done by obtaining new buffers from the specified `StorageProvider`.
 *
 * @param minBlockSize the minimal size of each new buffer
 * @param provider the StorageProvider to use to get the new buffers when needed
 */
final class ExpandingOutput(
    private[this] val minBlockSize: Int,
    private[this] val provider: StorageProvider)
  extends NiolOutput {

  private[this] var current: CircularBuffer = newBuffer()
  private[this] var top: NiolBuffer = current

  private def newBuffer() = new CircularBuffer(provider.getStorage(minBlockSize))

  private def expand(expansion: Int): Unit = {
    val more = newBuffer()
    top = top + more
    current = more
  }

  /**
   * Returns the output's content as a readable buffer
   *
   * @return a buffer containing all the bytes written to this output
   */
  def asBuffer: NiolBuffer = top

  /**
   * Copies all the data to a byte array.
   *
   * @return a byte array containing all the bytes written to this output
   */
  def toArray: Array[Byte] = top.toArray()

  /**
   * Gets the output's length (in bytes)
   *
   * @return the number of bytes written to this output
   */
  def length: Int = top.readableBytes

  /**
   * Removes all content from this output. After calling `clear`, `length` returns 0.
   */
  def clear(): Unit = {
    current = newBuffer()
    top = current
  }

  override def writableBytes: Int = Int.MaxValue
  override def isEnded: Boolean = false
  override def isWritable: Boolean = true

  override protected def check(nValues: Int, n: Int): Unit = {}
  override protected def checkWritable(n: Int): Unit = {}

  override protected[niol] def _write(b: Byte): Unit = {
    if (!current.isWritable) expand(minBlockSize)
    current._write(b)
  }

  override protected[niol] def _write(from: Array[Byte], off: Int, len: Int): Unit = {
    val nA = math.min(current.writableBytes, len)
    val nB = len - nA
    current._write(from, off, nA)
    if (nB > 0) {
      expand(math.max(minBlockSize, nB))
      current._write(from, off+nA, nB)
    }
  }

  override protected[niol] def _write(from: ByteBuffer, len: Int): Unit = {
    val nA = math.min(current.writableBytes, len)
    val nB = len - nA
    current._write(from, nA)
    if (nB > 0) {
      expand(math.max(minBlockSize, nB))
      current._write(from, nB)
    }
  }
}

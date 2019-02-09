package com.electronwill.niol.buffer
import com.electronwill.niol.buffer.storage.BytesStorage

/**
 * A CircularBuffer that works for the general case (that is, s.capacity doesn't need to be a power
 * of two).
 *
 * @param s bytes container
 * @param r initial read position
 * @param w initial write position
 * @param l true if last operation is a writing, false if it's a reading
 */
private[buffer] final class CircularBufferGeneralMod(s: BytesStorage, r: Int, w: Int, l: Boolean)
  extends CircularBuffer(s,r,w,l) {

  def this(storage: BytesStorage) = this(storage, 0, 0, false)

  override val capacity: Int = storage.capacity

  protected def countCircular(begin: Int, end: Int) = Math.floorMod((end - begin), capacity)
  protected def addCircular(value: Int, incr: Int) = (value + incr) % capacity
}

package com.electronwill.niol.buffer
import com.electronwill.niol.buffer.storage.BytesStorage

/**
 * A CircularBuffer optimized for the case where s.capacity is a power of two.
 *
 * @param s bytes container
 * @param r initial read position
 * @param w initial write position
 * @param l true if last operation is a writing, false if it's a reading
 */
private[buffer] final class CircularBufferOptiMod2(s: BytesStorage, r: Int, w: Int, l: Boolean)
  extends CircularBuffer(s, r, w, l) {

  //require(isPositivePowerOfTwo(storage.capacity), "The storage's capacity must be a power of 2")

  def this(storage: BytesStorage) = this(storage, 0, 0, false)

  private[this] val capMinus1 = capacity - 1

  protected def countCircular(begin: Int, end: Int) = (end - begin) & capMinus1
  protected def addCircular(value: Int, incr: Int) = (value + incr) & capMinus1
}

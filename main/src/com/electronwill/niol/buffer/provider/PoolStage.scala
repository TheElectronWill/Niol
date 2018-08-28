package com.electronwill.niol.buffer.provider

import java.util.concurrent.ArrayBlockingQueue

import com.electronwill.niol.buffer.BaseBuffer

/**
 * @author TheElectronWill
 */
private[provider] final class PoolStage(
    val maxCapacity: Int,
    val maxCached: Int,
    val allocateFunction: Int => BaseBuffer) {
  private[this] val cachedBuffers = new ArrayBlockingQueue[BaseBuffer](maxCached)

  /**
   * Returns an empty buffer with a capacity of [[PoolStage.maxCapacity]]. If possible, the buffer
   * is obtained from the stage's cache. If the cache is empty, then a new buffer is allocated.
   *
   * @return an empty buffer
   */
  def getBuffer(): BaseBuffer = {
    var buff = cachedBuffers.poll()
    if (buff eq null) {
      buff = allocateFunction(maxCapacity)
    }
    buff
  }

  /**
   * Attempts to add a buffer to the stage's cache. Returns `true` if the buffer gets cached.
   *
   * @param buffer the buffer to cache
   * @return true if cached, false if the cache is already full
   * @throws IllegalArgumentException if the buffer's capacity is too big for this stage
   */
  def cache(buffer: BaseBuffer): Boolean = {
    if (buffer.capacity > maxCapacity) {
      val msg = s"Cannot cache the given buffer: its capacity ${buffer.capacity}" +
        s"is too big for this stage (maxCapacity = $maxCapacity)"
      throw new IllegalArgumentException(msg)
    }
    cachedBuffers.offer(buffer)
  }

  /**
   * Attempts to add a buffer to the stage's cache. Returns None if the buffer gets cached.
   *
   * @param buffer the buffer to cache
   * @return None if cached, Some(buffer) if couldn't be cached
   */
  def cacheOrGetBack(buffer: BaseBuffer): Option[BaseBuffer] = {
    if (cache(buffer)) None else Some(buffer)
  }
}

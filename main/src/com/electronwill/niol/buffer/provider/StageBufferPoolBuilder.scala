package com.electronwill.niol.buffer.provider

import com.electronwill.niol.buffer.BaseBuffer

import scala.collection.mutable.ArrayBuffer

/**
 * @author TheElectronWill
 */
final class StageBufferPoolBuilder {
  private[this] val stages = new ArrayBuffer[PoolStage]
  private[this] var defaultHandler: Int => BaseBuffer = StageBufferPoolBuilder.ExceptionDefault

  def addStage(maxCapacity: Int, maxCached: Int, allocator: Int => BaseBuffer): Unit = {
    stages += new PoolStage(maxCapacity, maxCached, allocator)
  }

  def addStage(maxCapacity: Int, maxCached: Int, provider: BufferProvider): Unit = {
    stages += new PoolStage(maxCapacity, maxCached, provider.get)
  }

  def setDefault(handler: Int => BaseBuffer): Unit = {
    defaultHandler = handler
  }

  def setDefault(handler: BufferProvider): Unit = {
    defaultHandler = handler.get
  }

  def build(): StageBufferPool = {
    val array = stages.sortWith(_.maxCapacity < _.maxCapacity).toArray
    new StageBufferPool(array, defaultHandler)
  }
}
object StageBufferPoolBuilder {
  private final val ExceptionDefault: (Int => BaseBuffer) = {
    capacity =>
      val msg = s"Cannot provide a buffer of size $capacity: no corresponding stage"
      throw new NoCorrespondingPoolStageException(msg)
  }
}

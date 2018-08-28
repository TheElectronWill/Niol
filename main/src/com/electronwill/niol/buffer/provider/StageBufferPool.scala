package com.electronwill.niol.buffer.provider

import com.electronwill.niol.buffer.BaseBuffer

/**
 * @param stages The pool stages, in ordered by ascending capacity
 * @author TheElectronWill
 */
final class StageBufferPool private[provider] (
    private[this] val stages: Array[PoolStage],
    private[this] val defaultHandler: Int => BaseBuffer)
  extends BufferProvider {

  override def get(minCapacity: Int): BaseBuffer = {
    findStage(minCapacity) match {
      case Some(stage) => stage.getBuffer()
      case None => defaultHandler(minCapacity)
    }
  }

  override def discard(buffer: BaseBuffer): Unit = {
    buffer.clear()
    findStage(buffer.capacity).flatMap(_.cacheOrGetBack(buffer)).foreach(_.freeMemory())
  }

  private def findStage(capacity: Int): Option[PoolStage] = {
    var i = 0
    while (i < stages.length) {
      val stage = stages(i)
      if (capacity <= stage.maxCapacity) return Some(stage)
      i += 1
    }
    None
  }
}

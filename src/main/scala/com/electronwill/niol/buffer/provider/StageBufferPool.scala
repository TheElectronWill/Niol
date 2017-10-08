package com.electronwill.niol.buffer.provider

import com.electronwill.niol.buffer.NiolBuffer

/**
 * @author TheElectronWill
 */
final class StageBufferPool private[provider](/** in asc capacity order */
											  private[this] val stages: Array[PoolStage],
											  private[this] val defaultHandler: Int => NiolBuffer) extends BufferProvider {

	override def getBuffer(minCapacity: Int): NiolBuffer = {
		findStage(minCapacity) match {
			case Some(stage) => stage.getBuffer()
			case None => defaultHandler(minCapacity)
		}
	}

	override def discard(buffer: NiolBuffer): Unit = {
		buffer.clear()
		findStage(buffer.capacity).flatMap(_.tryCache(buffer)).foreach(_.freeMemory())
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
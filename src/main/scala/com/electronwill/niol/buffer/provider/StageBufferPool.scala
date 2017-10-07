package com.electronwill.niol.buffer.provider

import com.electronwill.niol.InputType
import com.electronwill.niol.buffer.{NioBasedBuffer, NiolBuffer}
import sun.nio.ch.DirectBuffer

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
		findStage(buffer.capacity).flatMap(_.tryCache(buffer)).foreach(freeMemory)
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

	private def freeMemory(buffer: NiolBuffer): Unit = {
		if (buffer.inputType == InputType.NIO_BUFFER) {
			val nioBuff = buffer.asInstanceOf[NioBasedBuffer].asReadByteBuffer
			if (nioBuff.isDirect) {
				nioBuff.asInstanceOf[DirectBuffer].cleaner().clean()
			}
		}
	}
}
package com.electronwill.niol.buffer.provider

import com.electronwill.niol.buffer.{NioBasedBuffer, NiolBuffer}

import scala.collection.mutable.ArrayBuffer

/**
 * @author TheElectronWill
 */
final class StageBufferPoolBuilder {
	private[this] val stages = new ArrayBuffer[PoolStage]
	private[this] var defaultHandler: Int => NiolBuffer = _

	def +=(maxCapacity: Int, maxCached: Int, allocator: Int => NiolBuffer): Unit = {
		stages += new PoolStage(maxCapacity, maxCached, allocator)
	}

	def +=(maxCapacity: Int, maxCached: Int): Unit = {
		stages += new PoolStage(maxCapacity, maxCached, NioBasedBuffer.allocateDirect)
	}

	def defaultHandler(handler: Int => NiolBuffer): Unit = {
		defaultHandler = handler
	}

	def build(): StageBufferPool = {
		val array = stages.sortWith(_.maxCapacity < _.maxCapacity).toArray
		new StageBufferPool(array, defaultHandler)
	}
}
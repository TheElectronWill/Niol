package com.electronwill.niol.buffer.provider

import com.electronwill.niol.buffer.RandomAccessBuffer

import scala.collection.mutable.ArrayBuffer

/**
 * @author TheElectronWill
 */
final class StageBufferPoolBuilder {
	private[this] val stages = new ArrayBuffer[PoolStage]
	private[this] var defaultHandler: Int => RandomAccessBuffer = _

	def +=(maxCapacity: Int, maxCached: Int, allocator: Int => RandomAccessBuffer): Unit = {
		stages += new PoolStage(maxCapacity, maxCached, allocator)
	}

	def +=(maxCapacity: Int, maxCached: Int): Unit = {
		stages += new PoolStage(maxCapacity, maxCached, DirectNioAllocator.getBuffer)
	}

	def defaultHandler(handler: Int => RandomAccessBuffer): Unit = {
		defaultHandler = handler
	}

	def build(): StageBufferPool = {
		val array = stages.sortWith(_.maxCapacity < _.maxCapacity).toArray
		new StageBufferPool(array, defaultHandler)
	}
}
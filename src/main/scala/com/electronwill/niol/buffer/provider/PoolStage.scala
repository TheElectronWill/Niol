package com.electronwill.niol.buffer.provider

import java.util.concurrent.ArrayBlockingQueue

import com.electronwill.niol.buffer.RandomAccessBuffer

/**
 * @author TheElectronWill
 */
private[provider] final class PoolStage(val maxCapacity: Int, val maxCached: Int,
										val allocateFunction: Int => RandomAccessBuffer) {
	private[this] val cachedBuffers = new ArrayBlockingQueue[RandomAccessBuffer](maxCached)

	def getBuffer(): RandomAccessBuffer = {
		var buff = cachedBuffers.poll()
		if (buff eq null) {
			buff = allocateFunction(maxCapacity)
		}
		buff
	}

	def cache(buffer: RandomAccessBuffer): Boolean = {
		if (buffer.capacity > maxCapacity) {
			throw new IllegalArgumentException("Buffer too small to be cached in this stage.")
		}
		cachedBuffers.offer(buffer)
	}

	def tryCache(buffer: RandomAccessBuffer): Option[RandomAccessBuffer] = {
		if (cache(buffer)) None else Some(buffer)
	}
}
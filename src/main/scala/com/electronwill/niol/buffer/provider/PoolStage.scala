package com.electronwill.niol.buffer.provider

import java.util.concurrent.ArrayBlockingQueue

import com.electronwill.niol.buffer.BaseBuffer

/**
 * @author TheElectronWill
 */
private[provider] final class PoolStage(val maxCapacity: Int, val maxCached: Int,
										val allocateFunction: Int => BaseBuffer) {
	private[this] val cachedBuffers = new ArrayBlockingQueue[BaseBuffer](maxCached)

	def getBuffer(): BaseBuffer = {
		var buff = cachedBuffers.poll()
		if (buff eq null) {
			buff = allocateFunction(maxCapacity)
		}
		buff
	}

	def cache(buffer: BaseBuffer): Boolean = {
		if (buffer.capacity > maxCapacity) {
			throw new IllegalArgumentException("Buffer too small to be cached in this stage.")
		}
		cachedBuffers.offer(buffer)
	}

	def tryCache(buffer: BaseBuffer): Option[BaseBuffer] = {
		if (cache(buffer)) None else Some(buffer)
	}
}
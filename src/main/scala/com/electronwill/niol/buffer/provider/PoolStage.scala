package com.electronwill.niol.buffer.provider

import java.util.concurrent.ArrayBlockingQueue

import com.electronwill.niol.buffer.NiolBuffer

/**
 * @author TheElectronWill
 */
private[provider] final class PoolStage(val maxCapacity: Int, val maxCached: Int,
										val allocateFunction: Int => NiolBuffer) {
	private[this] val cachedBuffers = new ArrayBlockingQueue[NiolBuffer](maxCached)

	def getBuffer(): NiolBuffer = {
		var buff = cachedBuffers.poll()
		if (buff eq null) {
			buff = allocateFunction(maxCapacity)
		}
		buff
	}

	def cache(buffer: NiolBuffer): Boolean = {
		if (buffer.capacity > maxCapacity) {
			throw new IllegalArgumentException("Buffer too small to be cached in this stage.")
		}
		cachedBuffers.offer(buffer)
	}

	def tryCache(buffer: NiolBuffer): Option[NiolBuffer] = {
		if (cache(buffer)) None else Some(buffer)
	}
}
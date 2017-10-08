package com.electronwill.niol.buffer.provider

import com.electronwill.niol.buffer.NiolBuffer

/**
 * @author TheElectronWill
 */
trait BufferProvider {
	def getBuffer(minCapacity: Int): NiolBuffer

	def discard(buffer: NiolBuffer): Unit
}
object BufferProvider {
	@volatile var DefaultOffHeapProvider: BufferProvider = DirectNioAllocator
	@volatile var DefaultInHeapProvider: BufferProvider = HeapNioAllocator
}
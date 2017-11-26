package com.electronwill.niol.buffer.provider

import com.electronwill.niol.buffer.BaseBuffer

/**
 * @author TheElectronWill
 */
trait BufferProvider {
	def getBuffer(minCapacity: Int): BaseBuffer

	def discard(buffer: BaseBuffer): Unit
}
object BufferProvider {
	@volatile var DefaultOffHeapProvider: BufferProvider = DirectNioAllocator
	@volatile var DefaultInHeapProvider: BufferProvider = HeapNioAllocator
}
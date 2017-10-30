package com.electronwill.niol.buffer.provider

import com.electronwill.niol.buffer.RandomAccessBuffer

/**
 * @author TheElectronWill
 */
trait BufferProvider {
	def getBuffer(minCapacity: Int): RandomAccessBuffer

	def discard(buffer: RandomAccessBuffer): Unit
}
object BufferProvider {
	@volatile var DefaultOffHeapProvider: BufferProvider = DirectNioAllocator
	@volatile var DefaultInHeapProvider: BufferProvider = HeapNioAllocator
}
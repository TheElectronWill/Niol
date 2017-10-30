package com.electronwill.niol.buffer.provider

import java.nio.ByteBuffer

import com.electronwill.niol.buffer.{NioBasedBuffer, NiolBuffer, RandomAccessBuffer}

/**
 * @author TheElectronWill
 */
object HeapNioAllocator extends BufferProvider {
	override def getBuffer(minCapacity: Int): RandomAccessBuffer = {
		new NioBasedBuffer(ByteBuffer.allocate(minCapacity), null, null)
	}
	override def discard(buffer: RandomAccessBuffer): Unit = {}
}
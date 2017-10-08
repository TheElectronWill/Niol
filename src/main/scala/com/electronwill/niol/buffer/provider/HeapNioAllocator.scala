package com.electronwill.niol.buffer.provider

import java.nio.ByteBuffer

import com.electronwill.niol.buffer.{NioBasedBuffer, NiolBuffer}

/**
 * @author TheElectronWill
 */
object HeapNioAllocator extends BufferProvider {
	override def getBuffer(minCapacity: Int): NiolBuffer = {
		new NioBasedBuffer(ByteBuffer.allocate(minCapacity), null, null)
	}
	override def discard(buffer: NiolBuffer): Unit = {}
}
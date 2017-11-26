package com.electronwill.niol.buffer.provider

import java.nio.ByteBuffer

import com.electronwill.niol.buffer.{BaseBuffer, NioBaseBuffer}

/**
 * @author TheElectronWill
 */
object HeapNioAllocator extends BufferProvider {
	override def getBuffer(minCapacity: Int): BaseBuffer = {
		new NioBaseBuffer(ByteBuffer.allocate(minCapacity), null, null)
	}
	override def discard(buffer: BaseBuffer): Unit = {}
}
package com.electronwill.niol.buffer.provider

import java.nio.ByteBuffer

import com.electronwill.niol.buffer.{NioBasedBuffer, NiolBuffer}

/**
 * @author TheElectronWill
 */
object DirectNioAllocator extends BufferProvider {
	override def getBuffer(minCapacity: Int): NiolBuffer = {
		new NioBasedBuffer(ByteBuffer.allocateDirect(minCapacity), null, this)
	}
	override def discard(buffer: NiolBuffer): Unit = buffer.freeMemory()
}
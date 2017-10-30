package com.electronwill.niol.buffer.provider

import java.nio.ByteBuffer

import com.electronwill.niol.buffer.{NioBasedBuffer, NiolBuffer, RandomAccessBuffer}

/**
 * @author TheElectronWill
 */
object DirectNioAllocator extends BufferProvider {
	override def getBuffer(minCapacity: Int): RandomAccessBuffer = {
		new NioBasedBuffer(ByteBuffer.allocateDirect(minCapacity), null, this)
	}
	override def discard(buffer: RandomAccessBuffer): Unit = buffer.freeMemory()
}
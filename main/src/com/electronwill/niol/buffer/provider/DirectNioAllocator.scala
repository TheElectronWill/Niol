package com.electronwill.niol.buffer.provider

import java.nio.ByteBuffer

import com.electronwill.niol.buffer.{BaseBuffer, NioBaseBuffer}

/**
 * @author TheElectronWill
 */
object DirectNioAllocator extends BufferProvider {
  override def get(minCapacity: Int): BaseBuffer = {
    new NioBaseBuffer(ByteBuffer.allocateDirect(minCapacity), null, this)
  }

  override def discard(buffer: BaseBuffer): Unit = buffer.freeMemory()
}

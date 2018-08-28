package com.electronwill.niol.network.tcp

import com.electronwill.niol.buffer.provider.BufferProvider

/**
 *
 * @param packetBufferBaseSize the size of the packet buffer
 * @param packetBufferProvider the provider of the packet buffer
 * @param readBufferSize       the size of the read buffer, used when there's a [[BufferTransform]]
 * @param readBufferProvider   the provider of the read buffer, used when there's a [[BufferTransform]]
 */
final case class BufferSettings(
    packetBufferBaseSize: Int,
    packetBufferProvider: BufferProvider,
    readBufferSize: Int,
    readBufferProvider: BufferProvider) {

  def this(baseBufferSize: Int, bufferProvider: BufferProvider) = {
    this(baseBufferSize, bufferProvider, baseBufferSize, bufferProvider)
  }
}
object BufferSettings {
  def apply(baseBufferSize: Int, bufferProvider: BufferProvider): BufferSettings = {
    apply(baseBufferSize, bufferProvider, baseBufferSize, bufferProvider)
  }
}

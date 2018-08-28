package com.electronwill.niol.network.tcp

import com.electronwill.niol.buffer.provider.BufferProvider

/**
 *
 * @param preTransformReadSize        the size of the read buffer, if there is a data transformation
 * @param packetBufferBaseSize        the size of the packet buffer which, if there is no transformation, is also the read buffer
 * @param readBufferProvider          the provider of the read buffer
 * @param postTransformBufferProvider the provider of the packet buffer, if there is a data transformation
 */
final case class BufferSettings(
    preTransformReadSize: Int,
    packetBufferBaseSize: Int,
    readBufferProvider: BufferProvider,
    postTransformBufferProvider: BufferProvider) {

  def this(baseBufferSize: Int, bufferProvider: BufferProvider) = {
    this(baseBufferSize, baseBufferSize, bufferProvider, bufferProvider)
  }
}
object BufferSettings {
  def apply(baseBufferSize: Int, bufferProvider: BufferProvider):BufferSettings = {
    apply(baseBufferSize, baseBufferSize, bufferProvider, bufferProvider)
  }
}

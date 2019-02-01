package com.electronwill.niol.network.tcp

import com.electronwill.niol.buffer.storage.StorageProvider

/**
 *
 * @param packetBufferBaseSize  the size of the packet buffer
 * @param packetStorageProvider the provider of the packet buffer
 * @param readBufferSize        the size of the read buffer, used when there's a [[BytesTransform]]
 * @param readStorageProvider   the provider of the read buffer, used when there's a [[BytesTransform]]
 */
final case class BufferSettings(
    packetBufferBaseSize: Int,
    packetStorageProvider: StorageProvider,
    readBufferSize: Int,
    readStorageProvider: StorageProvider) {

  def this(baseBufferSize: Int, bufferProvider: StorageProvider) = {
    this(baseBufferSize, bufferProvider, baseBufferSize, bufferProvider)
  }
}
object BufferSettings {
  def apply(baseBufferSize: Int, bufferProvider: StorageProvider): BufferSettings = {
    apply(baseBufferSize, bufferProvider, baseBufferSize, bufferProvider)
  }
}

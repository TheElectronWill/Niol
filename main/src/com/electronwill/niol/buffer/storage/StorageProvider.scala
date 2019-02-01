package com.electronwill.niol.buffer.storage

/** Provides [[com.electronwill.niol.buffer.storage.BytesStorage]]s */
trait StorageProvider {
  /** @return a storage with a capacity of at least `minCapacity` */
  def getStorage(minCapacity: Int): BytesStorage
}

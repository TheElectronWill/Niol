package com.electronwill.niol.buffer.storage
import java.lang.ref.{PhantomReference, ReferenceQueue}
import java.nio.ByteBuffer

class StorageReference(
    val bb: ByteBuffer,
    referent: BytesStorage,
    refQueue: ReferenceQueue[BytesStorage])
  extends PhantomReference[BytesStorage](referent, refQueue) {
}

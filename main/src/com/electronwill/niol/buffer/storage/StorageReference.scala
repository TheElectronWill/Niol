package com.electronwill.niol.buffer.storage
import java.lang.ref.{PhantomReference, ReferenceQueue}
import java.nio.ByteBuffer

/**
 * A [[java.lang.ref.PhantomReference]] that stores a [[java.nio.ByteBuffer]] in order to return it
 * to the [[StoragePool]] when the referent BytesStorage gets collected by the GC.
 *
 * @param bb the ByteBuffer to return to the pool when the storage gets collected
 * @param referent the BytesStorage we refer to
 * @param refQueue where to put this reference when its referent gets collected.
 */
class StorageReference(
    val bb: ByteBuffer,
    referent: BytesStorage,
    refQueue: ReferenceQueue[BytesStorage])
  extends PhantomReference[BytesStorage](referent, refQueue) {
}

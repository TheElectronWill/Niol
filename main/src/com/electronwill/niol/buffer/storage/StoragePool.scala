package com.electronwill.niol.buffer.storage

import java.lang.ref.ReferenceQueue
import java.nio.ByteBuffer
import java.nio.ByteBuffer.{allocate, allocateDirect}

import com.electronwill.collection.RecyclingIndex

/**
 * A pool of [[BytesStorage]]s.
 *
 * ==Automatic storage return==
 * The pool uses [[java.lang.ref.SoftReference]]s to track the ByteStorages. When a storage is
 * collected by the GC, its underlying [[java.nio.ByteBuffer]] is returned to the pool.
 * Thus, a ByteStorage is nothing more than a thin wrapper around a java ByteBuffer,
 * with automatic pooling management.
 *
 * ==Manual storage return==
 * You can force a ByteStorage to return to the pool by calling its method
 * [[BytesStorage.discardNow]].
 *
 * @param poolCapacity            the pool's capacity
 * @param bufferCapacity          the ByteStorages' capacity
 * @param isMoreAllocationAllowed true to allocate new buffer when all the `poolCapacity`
 *                                storages are used, false to throw an exception
 * @param isDirect                true to allocate direct buffers, false to allocate on the heap
 * @param refProcessingPerGet     the maximum number of [[java.lang.ref.PhantomReference]]s to
 *                                collect each time [[BytesStorage.get]] is called.
 */
class StoragePool(
    val poolCapacity: Int,
    val bufferCapacity: Int,
    val isMoreAllocationAllowed: Boolean,
    val isDirect: Boolean,
    private val refProcessingPerGet: Int = 2) {
  /** Contains the references of the collected storages (this queue is filled by the GC) */
  private[this] val gcRefs = new ReferenceQueue[BytesStorage]

  /** Contains the active references, to prevent them to be collected (otherwise gcRefs is empty) */
  private[this] val activeRefs = new RecyclingIndex[StorageReference](poolCapacity)

  /** Contains the free ByteBuffers, which can be given to the user via get() */
  private[this] val freeBuffers = new Array[ByteBuffer](poolCapacity)
  private[this] var freeBufferCount = 0

  /**
   * Returns a pooled BytesStorage. If no storage is available, allocates a new one.
   *
   * @return a free BytesStorage
   */
  def get(): BytesStorage = this.synchronized {
    processCollectedRefs().orElse(pollOrAddBuffer()) match {
      case Some(buffer) =>
        val sto = new BytesStorage(buffer, this)
        val ref = new StorageReference(buffer, sto, gcRefs)
        sto.id = activeRefs += ref
        sto
      case None if isMoreAllocationAllowed => new BytesStorage(allocateBuffer(), this)
      case None => throw new BufferAllocationException()
    }
  }

  /** Puts a storage back into the pool. Used by [[BytesStorage.discardNow]] */
  private[storage] def putBack(id: Int, bb: ByteBuffer): Unit = this.synchronized {
    activeRefs.remove(id)
    offerBuffer(bb)
  }

  /**
   * Polls at least 1 and at most `refProcessingPerGet` collected references.
   *
   * @return the buffer of the last processed reference, or None if the ReferenceQueue is empty
   */
  private[this] def processCollectedRefs(): Option[ByteBuffer] = {
    var i = 0
    var ref = gcRefs.poll().asInstanceOf[StorageReference] // Tries to get at least 1 buffer
    while (i <= refProcessingPerGet && ref != null) {
      offerBuffer(ref.bb)
      ref = gcRefs.poll().asInstanceOf[StorageReference]
      i += 1
    }
    // Returns the last buffer, if any:
    Option(ref).map(_.bb)
  }

  /** Retrieves a free buffer, or add a new one to the pool (if possible) */
  private[this] def pollOrAddBuffer(): Option[ByteBuffer] = {
    this.synchronized {
      freeBufferCount match {
        case 0 => addNewBuffer()
        case s =>
          val buff = freeBuffers(s - 1)
          freeBufferCount -= 1
          Some(buff)
      }
    }
  }

  /** Adds a buffer to the pool (if possible). Returns false if the pool is full. */
  private[this] def offerBuffer(buffer: ByteBuffer): Boolean = {
    if (freeBufferCount < freeBuffers.length) {
      freeBuffers(freeBufferCount) = buffer
      freeBufferCount += 1
      true
    } else {
      false
    }
  }

  /** Allocates a new buffer and adds it to the pool. */
  private[this] def addNewBuffer(): Option[ByteBuffer] = {
    if (activeRefs.size < poolCapacity) {
      val buffer = allocateBuffer()
      freeBuffers(freeBufferCount) = buffer
      freeBufferCount += 1
      Some(buffer)
    } else {
      None
    }
  }

  /** Allocates a new ByteBuffer. */
  private[this] def allocateBuffer(): ByteBuffer = {
    if (isDirect) allocate(bufferCapacity) else allocateDirect(bufferCapacity)
  }
}

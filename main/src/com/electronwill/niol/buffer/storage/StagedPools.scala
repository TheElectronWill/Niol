package com.electronwill.niol.buffer.storage

import java.nio.ByteBuffer

import scala.collection.mutable.ArrayBuffer

/**
 * A group of [[StoragePool]], organized in stages.
 *
 * ==Example==
 * {{{
 *   val pool = StagedPools().directStage(100, 5, true)    // 1st stage
 *                           .directStage(1000, 10, false) // 2nd stage
 *                           .defaultAllocateHeap()        // default handler
 *                           .build()
 *
 *   val storage = pool.getStorage(512)
 *   val buffer = new CircularBuffer(storage)
 * }}}
 *
 * This code creates a new group of staged pools and uses it to create a CircularBuffer with a
 * capacity of '''at least''' 512 bytes.
 *
 * When `getStorage(capacity)` is called:
 *  - If `capacity &lt;= 100`, a storage from the 1st stage is returned. Up to 5 storages are
 *    kept in the pool, but more storages can be created as needed (isMoreAllocationAllowed=true).
 *  - If `capacity &lt;= 1000`, a storage from the 2nd stage is returned. Up to 10 storages are
 *    kept in the pool. If 10 storages from this stage are already being used, and more storages are
 *    requested, an exception is thrown.
 *  - If `capacity &gt; 1000`, the default handler is called. In this case, `defaultAllocateHeap`
 *    allocates a new heap buffer of the requested capacity.
 *
 * @param stages The pool stages, ordered by ascending capacity
 */
final class StagedPools private[provider] (
    private[this] val stages: Array[StoragePool],
    private[this] val defaultHandler: Int => BytesStorage) {

  def getPool(minCapacity: Int): Option[StoragePool] = {
    var i = 0
    while (i < stages.length) {
      val stage = stages(i)
      if (stage.poolCapacity >= minCapacity) return Some(stage)
      i += 1
    }
    None
  }

  def getStorage(minCapacity: Int): BytesStorage = {
    getPool(minCapacity).map(_.get()).getOrElse(defaultHandler(minCapacity))
  }
}

object StagedPools {
  /** Builds [[com.electronwill.niol.buffer.storage.StagedPools]]*/
  final class Builder {
    private[this] val stages = new ArrayBuffer[StoragePool]
    private[this] var defaultHandler: Int => BytesStorage = Builder.EXCEPTION_THROWER

    def stage(pool: StoragePool): this.type = {
      stages += pool
      this
    }

    def heapStage(bufferCapacity: Int,
                  poolCapacity: Int,
                  isMoreAllocationAllowed: Boolean): this.type = {
      stages += new StoragePool(poolCapacity, bufferCapacity, isMoreAllocationAllowed, false)
      this
    }

    def directStage(bufferCapacity: Int,
                    poolCapacity: Int,
                    isMoreAllocationAllowed: Boolean): this.type = {
      stages += new StoragePool(poolCapacity, bufferCapacity, isMoreAllocationAllowed, true)
      this
    }

    def default(handler: Int => ByteBuffer): this.type = {
      defaultHandler = cap ⇒ new BytesStorage(handler(cap), null)
      this
    }

    def defaultAllocateHeap(): this.type = {
      defaultHandler = cap ⇒ new BytesStorage(ByteBuffer.allocate(cap), null)
      this
    }

    def defaultAllocateDirect(): this.type = {
      defaultHandler = cap ⇒ new BytesStorage(ByteBuffer.allocateDirect(cap), null)
      this
    }

    def defaultError(): this.type = {
      defaultHandler = Builder.EXCEPTION_THROWER
      this
    }

    def build(): StagedPools = {
      val array = stages.sortWith(_.bufferCapacity < _.bufferCapacity).toArray
      new StagedPools(array, defaultHandler)
    }
  }
  object Builder {
    private final val EXCEPTION_THROWER: (Int => BytesStorage) = { capacity =>
      throw new NoCorrespondingStageException(s"Cannot provide a buffer of size $capacity")
    }
    def apply(): Builder = new Builder()
  }
  def apply(): Builder = new Builder()
}

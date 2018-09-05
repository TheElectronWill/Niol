package com.electronwill.niol.buffer.storage

import java.nio.ByteBuffer

import scala.collection.mutable.ArrayBuffer

/**
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

  def getBuffer(minCapacity: Int): BytesStorage = {
    getPool(minCapacity).map(_.get()).getOrElse(defaultHandler(minCapacity))
  }
}

object StagedPools {
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
  }
}

package com.electronwill.niol.buffer

import java.nio.charset.StandardCharsets

import com.electronwill.niol.buffer.storage.BytesStorage
import org.scalatest.flatspec.AnyFlatSpec

class BiBufferSpec extends AnyFlatSpec {

  private def populate(buff: NiolBuffer, k: Int): Unit = {
    buff.writeBool(true)
    buff.writeByte(k + 10)
    buff.writeShort(k + 11)
    buff.writeInt(k + 12)
    buff.writeLong(k + 13)
    buff.writeFloat(k + 14)
    buff.writeDouble(k + 15)
    buff.writeString(s"test$k")
  }

  private def checkRead(buff: NiolBuffer, k: Int): Unit = {
    assert(buff.readBool())
    assert(buff.readByte() == k+10)
    assert(buff.readShort() == k+11)
    assert(buff.readInt() == k+12)
    assert(buff.readLong() == k+13)
    assert(buff.readFloat() == k+14)
    assert(buff.readDouble() == k+15)
    assert(buff.readString(5, StandardCharsets.UTF_8) == s"test$k")
  }

  val cap = 64
  val buffA = CircularBuffer(BytesStorage.allocateHeap(cap))
  val buffB = CircularBuffer(BytesStorage.allocateHeap(cap))
  val composite = new BiBuffer(buffA, buffB)

  "BiBuffer" should "report correct capacity and cursors" in {
    populate(buffA, 0)
    populate(buffB, 1)
    assert(composite.capacity == 2 * cap)
    assert(composite.readableBytes == buffA.readableBytes + buffB.readableBytes)
    assert(composite.writableBytes == buffA.writableBytes + buffB.writableBytes)
  }

  it should "support slicing and duplication" in {
    val slice = composite.slice(composite.readableBytes)
    val duplicate = composite.duplicate
//    println(s"composite: $composite")
//    println(s"slice: $slice")
//    println(s"duplicate: $duplicate")
    assert(slice.readableBytes == composite.readableBytes)
    assert(duplicate.readableBytes == composite.readableBytes)
    checkRead(slice, 0)
    checkRead(slice, 1)
    checkRead(duplicate, 0)
    checkRead(duplicate, 1)
  }

  it should "give the correct elements" in {
    checkRead(composite, 0)
    checkRead(composite, 1)
  }
}

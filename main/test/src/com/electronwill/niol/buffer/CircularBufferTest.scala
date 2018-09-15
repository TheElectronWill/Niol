package com.electronwill.niol.buffer

import com.electronwill.niol.buffer.storage.BytesStorage
import org.junit.jupiter.api.{Assertions, Test}

/**
 * @author TheElectronWill
 */
class CircularBufferTest {
  @Test
  def straightTest(): Unit = {
    val cap = 512
    val buff = new CircularBuffer(BytesStorage.allocateHeap(cap))
    println(buff)
    assert(buff.capacity == cap)
    assert(buff.readableBytes == 0 && buff.writableBytes == cap)

    buff.writeBool(true)
    buff.writeByte(10)
    buff.writeShort(11)
    buff.writeInt(12)
    buff.writeLong(13l)
    buff.writeFloat(14f)
    buff.writeDouble(15d)
    buff.writeString("test")

    println(buff)
    assert(buff.readableBytes == 32)
    assert(buff.writableBytes == cap - 32)

    assert(buff.readBool())
    assert(buff.readByte() == 10)
    assert(buff.readShort() == 11)
    assert(buff.readInt() == 12)
    assert(buff.readLong() == 13l)
    assert(buff.readFloat() == 14f)
    assert(buff.readDouble() == 15d)
    assert(buff.readString(4) == "test")

    println(buff)
  }

  @Test
  def circularTest(): Unit = {
    val cap = 50
    val buff = new CircularBuffer(BytesStorage.allocateHeap(cap))

    writeInts(1, 12, buff)
    println(buff)
    assert(buff.readableBytes == 48 && buff.writableBytes == cap - 48)

    readInts(1, 10, buff)
    println(buff)

    writeInts(1, 10, buff)
    println(buff)

    readInts(1, 12, buff)
    println(buff)
    assert(buff.readableBytes == 0 && buff.writableBytes == cap)
  }

  private def writeInts(v: Int, n: Int, dest: NiolBuffer): Unit = {
    for (i <- 1 to n) {
      dest.writeInt(v)
    }
  }

  private def readInts(v: Int, n: Int, src: NiolBuffer): Unit = {
    for (i <- 1 to n) {
      Assertions.assertEquals(v, src.readInt())
    }
  }
}

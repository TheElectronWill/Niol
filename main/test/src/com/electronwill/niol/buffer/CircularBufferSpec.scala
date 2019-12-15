package com.electronwill.niol.buffer

import com.electronwill.niol.buffer.storage.BytesStorage
import org.scalatest.flatspec.AnyFlatSpec

class CircularBufferSpec extends AnyFlatSpec {

  private def writeInts(v: Int, n: Int, dest: NiolBuffer): Unit = {
    (1 to n) foreach (_ => dest.writeInt(v))
  }

  private def readInts(v: Int, n: Int, src: NiolBuffer): Unit = {
    (1 to n) foreach (_ => assert(src.readInt() == v))
  }

  private def circularTest(cap: Int, buff: NiolBuffer): Unit = {
    assert(buff.capacity == cap)

    writeInts(1, 12, buff)
    //println(buff)
    assert(buff.readableBytes == 48)
    assert(buff.writableBytes == cap - 48)

    readInts(1, 10, buff)
    //println(buff)

    writeInts(1, 10, buff)
    //println(buff)

    readInts(1, 12, buff)
    //println(buff)
    assert(buff.readableBytes == 0)
    assert(buff.writableBytes == cap)

    writeInts(1777, 16, buff)
    //println(buff)

    assert(buff.writableBytes == cap-16*4)
    assert(buff.readableBytes == 16*4)

    readInts(1777, 16, buff)
    //println(buff)

    assert(buff.readableBytes == 0)
    assert(buff.writableBytes == cap)
  }

  "CircularBuffer" should "work in linear read/write" in {
    val cap = 512
    val buff = CircularBuffer(BytesStorage.allocateHeap(cap))
    //println(buff)
    assert(buff.capacity == cap)
    assert(buff.readableBytes == 0)
    assert(buff.writableBytes == cap)

    buff.writeBool(true)
    buff.writeByte(10)
    buff.writeShort(11)
    buff.writeInt(12)
    buff.writeLong(13L)
    buff.writeFloat(14f)
    buff.writeDouble(15d)
    buff.writeString("test")
    //println(buff)
    assert(buff.readableBytes == 32)
    assert(buff.writableBytes == cap - 32)

    assert(buff.readBool())
    assert(buff.readByte() == 10)
    assert(buff.readShort() == 11)
    assert(buff.readInt() == 12)
    assert(buff.readLong() == 13L)
    assert(buff.readFloat() == 14f)
    assert(buff.readDouble() == 15d)
    assert(buff.readString(4) == "test")
    //println(buff)
  }

  it should "write and read longs" in {
    val buff = CircularBuffer(BytesStorage.allocateHeap(48))
    buff.writeLong(1789569706)
    buff.writeLong(4624633867356078080L)
    buff.writeLong(-1234567891011121314L)
    buff.writeLong(-1L)
    buff.writeLong(+1L)
    buff.writeInt(0)
    buff.writeInt(123)
    assert(buff.readLong() == 1789569706)
    assert(buff.readLong() == 4624633867356078080L)
    assert(buff.readLong() == -1234567891011121314L)
    assert(buff.readLong() == -1)
    assert(buff.readLong() == +1)
    assert(buff.readLong() == 123)
  }

  it should "write and read ints" in {
    val buff = CircularBuffer(BytesStorage.allocateHeap(24))
    buff.writeInt(1789569706)
    buff.writeInt(462463386)
    buff.writeInt(-12345678)
    buff.writeInt(-1)
    buff.writeInt(+1)
    buff.writeShort(0)
    buff.writeShort(123)
    assert(buff.readInt() == 1789569706)
    assert(buff.readInt() == 462463386)
    assert(buff.readInt() == -12345678)
    assert(buff.readInt() == -1)
    assert(buff.readInt() == +1)
    assert(buff.readInt() == 123)
  }

  "CircularBuffer (capacity is a power of 2)" should "be optimized" in {
    assert(CircularBuffer(BytesStorage.allocateHeap(8)).isInstanceOf[CircularBufferOptiMod2])
    assert(CircularBuffer(BytesStorage.allocateHeap(64)).isInstanceOf[CircularBufferOptiMod2])
  }
  it should "work properly" in {
    val buff = new CircularBufferOptiMod2(BytesStorage.allocateHeap(64))
    circularTest(64, buff)
  }

  "CircularBuffer (capacity not a power of 2)" should "be general" in {
    assert(CircularBuffer(BytesStorage.allocateHeap(7)).isInstanceOf[CircularBufferGeneralMod])
    assert(CircularBuffer(BytesStorage.allocateHeap(65)).isInstanceOf[CircularBufferGeneralMod])
  }
  it should "work properly" in {
    val buff = new CircularBufferGeneralMod(BytesStorage.allocateHeap(65))
    circularTest(65, buff)
  }
  it should "work when the capacity actually is a power of 2" in {
    val buff = new CircularBufferGeneralMod(BytesStorage.allocateHeap(64))
    circularTest(64, buff)
  }
}

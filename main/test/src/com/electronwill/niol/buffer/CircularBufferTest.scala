package com.electronwill.niol.buffer

import com.electronwill.niol.buffer.storage.BytesStorage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions._

/**
 * @author TheElectronWill
 */
class CircularBufferTest {
  @Test
  def straightTest(): Unit = {
    val cap = 512
    val buff = CircularBuffer(BytesStorage.allocateHeap(cap))
    println(buff)
    assertEquals(cap, buff.capacity)
    assertEquals(0, buff.readableBytes)
    assertEquals(cap, buff.writableBytes)

    buff.writeBool(true)
    buff.writeByte(10)
    buff.writeShort(11)
    buff.writeInt(12)
    buff.writeLong(13l)
    buff.writeFloat(14f)
    buff.writeDouble(15d)
    buff.writeString("test")

    println(buff)
    assertEquals(32, buff.readableBytes)
    assertEquals(cap - 32, buff.writableBytes)

    assertTrue(buff.readBool())
    assertEquals(10, buff.readByte())
    assertEquals(11, buff.readShort())
    assertEquals(12, buff.readInt())
    assertEquals(13l, buff.readLong())
    assertEquals(14f, buff.readFloat())
    assertEquals(15d, buff.readDouble())
    assertEquals("test", buff.readString(4))

    println(buff)
  }

  @Test
  def circularTest(): Unit = {
    val cap = 64
   circularTest(cap, new CircularBufferOptiMod2(BytesStorage.allocateHeap(cap)))
   circularTest(cap,  new CircularBufferGeneralMod(BytesStorage.allocateHeap(cap)))
  }

  private def circularTest(cap: Int, buff: NiolBuffer): Unit = {
    assertEquals(cap, buff.capacity)

    writeInts(1, 12, buff)
    println(buff)
    assertEquals(48, buff.readableBytes)
    assertEquals(cap - 48, buff.writableBytes)

    readInts(1, 10, buff)
    println(buff)

    writeInts(1, 10, buff)
    println(buff)

    readInts(1, 12, buff)
    println(buff)
    assertEquals(0, buff.readableBytes)
    assertEquals(cap, buff.writableBytes)

    writeInts(1777, 16, buff)
    println(buff)

    assertEquals(0, buff.writableBytes)
    assertEquals(cap, buff.readableBytes)

    readInts(1777, 16, buff)
    println(buff)

    assertEquals(0, buff.readableBytes)
    assertEquals(cap, buff.writableBytes)
  }

  @Test
  def longTest(): Unit = {
    val buff = CircularBuffer(BytesStorage.allocateHeap(48))
    buff.writeLong(1789569706)
    buff.writeLong(4624633867356078080L)
    buff.writeLong(-1234567891011121314L)
    buff.writeLong(-1L)
    buff.writeLong(+1L)
    buff.writeInt(0)
    buff.writeInt(123)
    assertEquals(1789569706, buff.readLong())
    assertEquals(4624633867356078080L, buff.readLong())
    assertEquals(-1234567891011121314L, buff.readLong())
    assertEquals(-1, buff.readLong())
    assertEquals(+1, buff.readLong())
    assertEquals(123, buff.readLong())
  }

  @Test
  def intTest(): Unit = {
    val buff = CircularBuffer(BytesStorage.allocateHeap(24))
    buff.writeInt(1789569706)
    buff.writeInt(462463386)
    buff.writeInt(-12345678)
    buff.writeInt(-1)
    buff.writeInt(+1)
    buff.writeShort(0)
    buff.writeShort(123)
    assertEquals(1789569706, buff.readInt())
    assertEquals(462463386, buff.readInt())
    assertEquals(-12345678, buff.readInt())
    assertEquals(-1, buff.readInt())
    assertEquals(+1, buff.readInt())
    assertEquals(123, buff.readInt())
  }

  private def writeInts(v: Int, n: Int, dest: NiolBuffer): Unit = {
    (1 to n) foreach (_=>dest.writeInt(v))
  }

  private def readInts(v: Int, n: Int, src: NiolBuffer): Unit = {
    (1 to n) foreach (_=>assertEquals(v, src.readInt()))
  }
}

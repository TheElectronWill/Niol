package com.electronwill.niol.buffer

import java.nio.charset.StandardCharsets

import com.electronwill.niol.buffer.storage.BytesStorage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions._


/**
 * @author TheElectronWill
 */
class BiBufferTest {
  @Test
  def test(): Unit = {
    val cap = 64
    val buffA = CircularBuffer(BytesStorage.allocateHeap(cap))
    val buffB = CircularBuffer(BytesStorage.allocateHeap(cap))
    populate(buffA, 0)
    populate(buffB, 1)

    val composite = new BiBuffer(buffA, buffB)
    assertEquals(2 * cap, composite.capacity)
    assertEquals(buffA.readableBytes + buffB.readableBytes, composite.readableBytes)
    assertEquals(buffA.writableBytes + buffB.writableBytes, composite.writableBytes)

    val slice = composite.slice(composite.readableBytes)
    val duplicate = composite.duplicate
    println(s"composite: $composite")
    println(s"slice: $slice")
    println(s"duplicate: $duplicate")
    assertEquals(composite.readableBytes, slice.readableBytes)
    assertEquals(composite.readableBytes, duplicate.readableBytes)
    printContent(composite)
    printContent(slice)
    printContent(duplicate)
  }

  private def populate(buff: NiolBuffer, k: Int): Unit = {
    buff.writeBool(true)
    buff.writeByte(k + 10)
    buff.writeShort(k + 11)
    buff.writeInt(k + 12)
    buff.writeLong(k + 13)
    buff.writeFloat(k + 14)
    buff.writeDouble(k + 15)
    buff.writeString("test" + k)
  }

  private def printContent(buff: NiolBuffer): Unit = {
    read(buff)
    println("---")
    if (buff.isReadable) {
      read(buff)
      println("---------------------")
    }
  }

  private def read(buff: NiolBuffer): Unit = {
    println(buff.readBool())
    println(buff.readByte())
    println(buff.readShort())
    println(buff.readInt())
    println(buff.readLong())
    println(buff.readFloat())
    println(buff.readDouble())
    println(buff.readString(5, StandardCharsets.UTF_8))
  }
}

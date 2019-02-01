package com.electronwill.niol.buffer

import java.nio.charset.StandardCharsets

import com.electronwill.niol.buffer.storage.BytesStorage
import org.junit.jupiter.api.Test

/**
 * @author TheElectronWill
 */
class BiBufferTest {
  @Test
  def test(): Unit = {
    val cap = 512
    val buffA = new CircularBuffer(BytesStorage.allocateHeap(cap))
    val buffB = new CircularBuffer(BytesStorage.allocateHeap(cap))
    populate(buffA, 0)
    populate(buffB, 1)

    val composite = new BiBuffer(buffA, buffB)

    assert(composite.capacity == 2 * cap)
    assert(composite.readableBytes == buffA.readableBytes + buffB.readableBytes)
    assert(composite.writableBytes == buffA.writableBytes + buffB.writableBytes)
    printContent(composite.duplicate)

    val read = composite.copy(BytesStorage.allocateHeap)
    val read2 = composite.slice()
    println(read)
    println(read2)
    assert(read.readableBytes == composite.readableBytes)
    assert(read.readableBytes == read2.readableBytes)
    printContent(read)
    printContent(read2)
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

  private def printContent(composite: NiolBuffer): Unit = {
    println(composite)
    println("------ Content ------")
    read(composite)
    println("----")
    read(composite)
    println("----")
    read(composite)
    println("---------------------")
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

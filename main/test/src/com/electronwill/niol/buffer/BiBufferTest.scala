package com.electronwill.niol.buffer

import java.nio.charset.StandardCharsets

import com.electronwill.niol.buffer.provider.HeapNioAllocator
import org.junit.jupiter.api.Test

/**
 * @author TheElectronWill
 */
class CompositeBufferTest {
  @Test
  def test(): Unit = {
    val cap = 512
    val buffA = new StraightBuffer(HeapNioAllocator.get(cap))
    val buffB = new StraightBuffer(HeapNioAllocator.get(cap))
    populate(buffA, 0)
    populate(buffB, 1)

    val composite = new CompositeBuffer(buffA)
    composite += buffB
    composite += buffA.duplicate

    assert(composite.capacity == 3 * cap)
    assert(composite.readAvail == 2 * buffA.readAvail + buffB.readAvail)
    assert(composite.writableBytes == 2 * buffA.writableBytes + buffB.writableBytes)
    printContent(composite.duplicate)

    val read = composite.copyRead
    val read2 = composite.subRead
    printBuffer(read)
    printBuffer(read2)
    assert(read.readAvail == composite.readAvail)
    assert(read.readAvail == read2.readAvail)
    printContent(read)
    printContent(read2)
  }

  private def populate(buff: NiolBuffer, k: Int): Unit = {
    buff.putBool(true)
    buff.putByte(k + 10)
    buff.putShort(k + 11)
    buff.putInt(k + 12)
    buff.putLong(k + 13)
    buff.putFloat(k + 14)
    buff.putDouble(k + 15)
    buff.putString("test" + k)
  }

  private def printContent(composite: NiolBuffer): Unit = {
    printBuffer(composite)
    println("------ Content ------")
    read(composite)
    println("----")
    read(composite)
    println("----")
    read(composite)
    println("---------------------")
  }

  private def read(buff: NiolBuffer): Unit = {
    println(buff.getBool())
    println(buff.getByte())
    println(buff.getShort())
    println(buff.getInt())
    println(buff.getLong())
    println(buff.getFloat())
    println(buff.getDouble())
    println(buff.getString(5, StandardCharsets.UTF_8))
  }
}

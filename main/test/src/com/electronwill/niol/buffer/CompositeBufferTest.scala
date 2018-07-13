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
    val buffA = new StraightBuffer(HeapNioAllocator.getBuffer(cap))
    val buffB = new StraightBuffer(HeapNioAllocator.getBuffer(cap))
    populate(buffA, 0)
    populate(buffB, 1)

    val composite = new CompositeBuffer(buffA)
    composite += buffB
    composite += buffA.duplicate

    assert(composite.capacity == 3 * cap)
    assert(composite.readAvail == 2 * buffA.readAvail + buffB.readAvail)
    assert(composite.writeAvail == 2 * buffA.writeAvail + buffB.writeAvail)
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

  private def populate(buff: NiolBuffer, k: Int) = {
    true >>: buff
    (k + 10).toByte >>: buff
    (k + 11).toShort >>: buff
    (k + 12).toInt >>: buff
    (k + 13).toLong >>: buff
    (k + 14).toFloat >>: buff
    (k + 15).toDouble >>: buff
    ("test" + k, StandardCharsets.UTF_8) >>: buff
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
    println(buff.getBool)
    println(buff.getByte)
    println(buff.getShort)
    println(buff.getInt)
    println(buff.getLong)
    println(buff.getFloat)
    println(buff.getDouble)
    println(buff.getString(5, StandardCharsets.UTF_8))
  }
}

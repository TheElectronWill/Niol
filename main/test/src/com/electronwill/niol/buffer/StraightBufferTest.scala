package com.electronwill.niol.buffer

import com.electronwill.niol.buffer.provider.HeapNioAllocator
import org.junit.jupiter.api.Test

/**
 * @author TheElectronWill
 */
class StraightBufferTest {
  @Test
  def test(): Unit = {
    val cap = 512
    val buff = new StraightBuffer(HeapNioAllocator.get(cap))
    println(buff)
    assert(buff.capacity == cap)
    assert(buff.readPos == 0 && buff.writePos == 0)
    assert(buff.readLimit == 0 && buff.writeLimit == cap)
    assert(buff.readAvail == 0 && buff.writeAvail == cap)

    buff.putBool(true)
    buff.putByte(10)
    buff.putShort(11)
    buff.putInt(12)
    buff.putLong(13l)
    buff.putFloat(14f)
    buff.putDouble(15d)
    buff.putString("test")

    val copy = buff.copyRead
    assertContent(buff)
    assertContent(copy)
  }

  @Test
  def varintTest(): Unit = {
    val buff = new StraightBuffer(HeapNioAllocator.get(256))
    val value = 117
    buff.putVarint(value)
    assert(buff.getVarint() == value)

    buff.putVarlong(value)
    assert(buff.getVarlong() == value)
  }

  private def assertContent(buff: NiolBuffer): Unit = {
    printBuffer(buff)
    assert(buff.getBool())
    assert(buff.getByte() == 10)
    assert(buff.getShort() == 11)
    assert(buff.getInt() == 12)
    assert(buff.getLong() == 13l)
    assert(buff.getFloat() == 14f)
    assert(buff.getDouble() == 15d)
    assert(buff.getString(4) == "test")
    printBuffer(buff)
  }

  @Test
  def bulkTest(): Unit = {
    val length = 500
    val buff = new StraightBuffer(HeapNioAllocator.get(length))
    val array = new Array[Byte](length)
    array >>: buff
    assert(buff.readAvail == array.length)
    assert(buff.writePos == array.length)
    assert(buff.getBytes(length) sameElements array)
  }
}

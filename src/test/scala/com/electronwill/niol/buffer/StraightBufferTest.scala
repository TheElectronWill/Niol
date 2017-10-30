package com.electronwill.niol.buffer

import java.nio.charset.StandardCharsets

import com.electronwill.niol.buffer.provider.HeapNioAllocator
import org.junit.jupiter.api.Test

/**
 * @author TheElectronWill
 */
class StraightBufferTest {
	@Test
	def test(): Unit = {
		val cap = 512
		val buff = new StraightBuffer(HeapNioAllocator.getBuffer(cap))
		assert(buff.capacity == cap)
		assert(buff.readPos == 0 && buff.writePos == 0)
		assert(buff.readLimit == 0 && buff.writeLimit == cap)
		assert(buff.readAvail == 0 && buff.writeAvail == cap)

		true >>: buff
		10.toByte >>: buff
		11.toShort >>: buff
		12 >>: buff
		13l >>: buff
		14f >>: buff
		15d >>: buff
		("test", StandardCharsets.UTF_8) >>: buff

		val copy = buff.copyRead
		assertContent(buff)
		assertContent(copy)
	}

	private def assertContent(buff: NiolBuffer): Unit={
		printBuffer(buff)
		assert(buff.getBool())
		assert(buff.getByte() == 10)
		assert(buff.getShort() == 11)
		assert(buff.getInt() == 12)
		assert(buff.getLong() == 13l)
		assert(buff.getFloat() == 14f)
		assert(buff.getDouble() == 15d)
		assert(buff.getString(4, StandardCharsets.UTF_8) == "test")
		printBuffer(buff)
	}

	@Test
	def bulkTest(): Unit = {
		val length = 500
		val buff = new StraightBuffer(HeapNioAllocator.getBuffer(length))
		val array = new Array[Byte](length)
		array >>: buff
		assert(buff.readAvail == array.length)
		assert(buff.writePos == array.length)
		assert(buff.getBytes(length) sameElements array)
	}
}
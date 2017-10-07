package com.electronwill.niol

import java.nio.charset.StandardCharsets

import org.junit.jupiter.api.Test

/**
 * @author TheElectronWill
 */
class CircularBufferTest {
	@Test
	def straightTest(): Unit = {
		val cap = 512
		val buff = new CircularBuffer(NioBasedBuffer.allocateHeap(cap))
		printBuffer(buff)
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

		printBuffer(buff)
		assert(buff.readAvail == 32)
		assert(buff.writeAvail == cap - 32)

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
	def circularTest(): Unit = {
		val cap = 50
		val buff = new CircularBuffer(NioBasedBuffer.allocateHeap(cap))

		putInts(1, 12, buff)
		printBuffer(buff)
		assert(buff.readAvail == 48 && buff.writeAvail == cap - 48)

		readInts(1, 10, buff)
		printBuffer(buff)

		putInts(1, 10, buff)
		printBuffer(buff)

		readInts(1, 12, buff)
		printBuffer(buff)
		assert(buff.readAvail == 0 && buff.writeAvail == cap)
	}

	private def putInts(v: Int, n: Int, dest: NiolBuffer): Unit = {
		for (i <- 1 to n) {
			v >>: dest
		}
	}

	private def readInts(v: Int, n: Int, src: NiolBuffer): Unit = {
		for (i <- 1 to n) {
			assert(src.getInt() == v)
		}
	}
}
package com.electronwill.niol.buffer

import java.util.concurrent.atomic.AtomicInteger

import com.electronwill.niol._
import com.electronwill.niol.buffer.provider.HeapNioAllocator

/**
 * An abstract readable and writable container of bytes.
 *
 * @author TheElectronWill
 */
abstract class NiolBuffer extends NiolInput with NiolOutput {
	protected[this] val useCount = new AtomicInteger(1)

	// buffer state
	/** @return the buffer's capacity */
	def capacity: Int

	/** @return true iff readAvail > 0 */
	override def canRead: Boolean = readAvail > 0

	/** @return the available space to write. */
	def writeAvail: Int

	/** Skips n writable bytes. */
	def skipWrite(n: Int): Unit

	/** @return the available space to read. */
	def readAvail: Int

	/** Skips n readable bytes. */
	def skipRead(n: Int): Unit

	// buffer operations
	/**
	 * Duplicates this buffer. The returned buffer shares its content with this buffer, but has
	 * a separate position and limit
	 *
	 * @return the new buffer
	 */
	def duplicate: NiolBuffer

	/** Copies the readable content of this buffer in a new buffer. */
	def copyRead: NiolBuffer

	/** Creates a view of the buffer's readable data. */
	def subRead: NiolBuffer

	/** Creates a limited view of the buffer's readable data. */
	def subRead(maxLength: Int): NiolBuffer

	/** Creates a view of the buffer's writeable space. */
	def subWrite: NiolBuffer

	/** Concatenates two buffers without copying their content. */
	def concat(buffer: NiolBuffer): NiolBuffer = {
		if (this.capacity == 0) {if (buffer.capacity == 0) EmptyBuffer else buffer.duplicate}
		else if (buffer.capacity == 0) this.duplicate
		else {
			val res = new CompositeBuffer(this)
			res += buffer
			res
		}
	}

	/** Concatenates two buffers by copying them to a new buffer. */
	def concatCopy(buffer: NiolBuffer): NiolBuffer = {
		val availableThis = this.readAvail
		val availableBuff = buffer.readAvail
		if (availableThis == 0) {if (availableBuff == 0) EmptyBuffer else buffer.copyRead}
		else if (availableBuff == 0) this.copyRead
		else {
			val copy = HeapNioAllocator.getBuffer(availableThis + availableBuff)
			this.duplicate >>: copy
			buffer.duplicate >>: copy
			copy
		}
	}

	/** Compacts this buffer by moving its readable content to position 0 if possible. */
	def compact(): Unit

	/**
	 * Clears this buffer.
	 */
	def clear()

	/**
	 * Discards this buffer: if its use count is 0 (after decrease), returns it to the pool it
	 * comes from. A discarded buffer must no longer be used, except when re-obtained later from
	 * the pool.
	 */
	def discard(): Unit

	/**
	 * Frees the buffer memory. A freed buffer must no longer be used nor obtained from the pool.
	 */
	protected[niol] def freeMemory(): Unit = {}

	/**
	 * Marks this buffer as used by incrementing its use count. Manually calling this method is
	 * rarely necessary because creating, duplicating or subviewing a buffer automatically
	 * increases the use count.
	 */
	def markUsed(): Unit = useCount.getAndIncrement()

	// shortcuts
	/** Concatenates two buffers without copying their content. */
	@inline final def +(buffer: NiolBuffer): NiolBuffer = concat(buffer)

	/** Concatenates two buffers by copying them to a new buffer. */
	@inline final def +++(buffer: NiolBuffer): NiolBuffer = concatCopy(buffer)

	// overrides
	override def putBytes(src: NiolInput): Unit = src.getBytes(this)

	/** @return a String containing the informations about the state of this buffer. */
	override def toString: String =
		s"""${getClass.getSimpleName}(
		   | capacity=$capacity;
		   | writeAvail=$writeAvail;
		   | readAvail=$readAvail)""".stripMargin
}
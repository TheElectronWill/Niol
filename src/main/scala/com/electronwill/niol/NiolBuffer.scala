package com.electronwill.niol

import java.nio.ByteBuffer

/**
 * @author TheElectronWill
 */
trait NiolBuffer extends NiolInput with NiolOutput {
	// buffer state
	def capacity: Int

	def writePos: Int
	def writePos(pos: Int): Unit
	def writeLimit: Int
	def writeLimit(limit: Int): Unit
	def markWritePos(): Unit
	def resetWritePos(): Unit
	/** @return the available space between writePos and writeLimit */
	def writeAvail: Int = writeLimit - writePos
	def skipWrite(n: Int): Unit = writePos(writePos + n)

	def readPos: Int
	def readPos(pos: Int): Unit
	def readLimit: Int
	def readLimit(limit: Int): Unit
	def markReadPos(): Unit
	def resetReadPos(): Unit
	/** @return the available space between readPos and readLimit */
	def readAvail: Int = readLimit - readPos
	def skipRead(n: Int): Unit = readPos(readPos + n)

	// buffer operations
	/**
	 * Duplicates this buffer. The returned buffer shares its content with this buffer, but has
	 * a separate position and limit
	 *
	 * @return the new buffer
	 */
	def duplicate: NiolBuffer

	/** Copies the content between readPos and readLimit in a new NiolBuffer. */
	def copyRead: NiolBuffer = copy(readPos, readLimit)

	/** Copies a portion of this buffer in a new buffer. */
	def copy(begin: Int, end: Int): NiolBuffer //absolute, exclusive end

	/** Creates a view of the buffer's content between readPos and readLimit. */
	def subRead: NiolBuffer = sub(readPos, readLimit)

	/** Creates a view of the buffer's content between writePos and writeLimit. */
	def subWrite: NiolBuffer = sub(writePos, writeLimit)

	/** Creates a view of a portion of this buffer. */
	def sub(begin: Int, end: Int): NiolBuffer // absolute, exclusive end

	/** Concatenates two buffers without copying their content. */
	def concat(buffer: NiolBuffer): NiolBuffer = {
		if (this.capacity == 0) { if(buffer.capacity == 0) EmptyBuffer else buffer.duplicate }
		else if (buffer.capacity == 0) this.duplicate
		else new CompositeBuffer(this, buffer)
	}

	/** Concatenates two buffers in a newly allocated buffer. */
	def concatCopy(buffer: NiolBuffer): NiolBuffer = {
		val availableThis = this.readAvail
		val availableBuff = buffer.readAvail
		if (availableThis == 0) { if(availableBuff == 0) EmptyBuffer else buffer.copyRead }
		else if (availableBuff == 0) this.copyRead
		else {
			val copy = NiolBuffer.allocateHeap(availableThis + availableBuff)
			this.duplicate >>: copy
			buffer.duplicate >>: copy
			copy
		}
	}

	// shortcuts
	@inline final def +(buffer: NiolBuffer): NiolBuffer = concat(buffer)
	@inline final def +++(buffer: NiolBuffer): NiolBuffer = concatCopy(buffer)

	// overrides
	override def putBytes(source: NiolInput): Unit = source.getBytes(this)
}
object NiolBuffer {
	def allocateHeap(capacity: Int): NiolBuffer = wrap(ByteBuffer.allocate(capacity))

	def allocateDirect(capacity: Int): NiolBuffer = wrap(ByteBuffer.allocateDirect(capacity))

	def wrap(writeBuffer: ByteBuffer): NiolBuffer = {
		val readBuffer = writeBuffer.duplicate()
		new NioBasedBuffer(writeBuffer, readBuffer)
	}
}
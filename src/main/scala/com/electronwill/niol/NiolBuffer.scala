package com.electronwill.niol

/**
 * @author TheElectronWill
 */
trait NiolBuffer extends NiolInput with NiolOutput {
	// buffer state
	/** @return the buffer's capacity */
	def capacity: Int
	/** @return true iff readAvail > 0 */
	override def canRead: Boolean = readAvail > 0

	/** @return the current write position, at which the next put operation will occur. */
	def writePos: Int

	/** Sets the write position, at which the next put operation will occur. */
	def writePos(pos: Int): Unit

	/** @return the current write limit */
	def writeLimit: Int

	/** Sets the write limit. */
	def writeLimit(limit: Int): Unit

	/** Marks the current write position */
	def markWritePos(): Unit

	/** Resets the write position to the last position marked by [[markWritePos]]. */
	def resetWritePos(): Unit

	/** @return the available space to write, generally writeLimit - writePos */
	def writeAvail: Int = writeLimit - writePos

	/** Increases the write position by n. */
	def skipWrite(n: Int): Unit = writePos(writePos + n)

	/** @return the current read position, at which the next get operation will occur. */
	def readPos: Int

	/** Sets the read position, at which the next get operation will occur. */
	def readPos(pos: Int): Unit

	/** @return the current read limit */
	def readLimit: Int

	/** Sets the read limit. */
	def readLimit(limit: Int): Unit

	/** Marks the current read position */
	def markReadPos(): Unit

	/** Resets the write position to the last position marked by [[markReadPos]]. */
	def resetReadPos(): Unit

	/** @return the available space to read, generally readLimit - readPos */
	def readAvail: Int = readLimit - readPos

	/** Increases the read position by n. */
	def skipRead(n: Int): Unit = readPos(readPos + n)

	// buffer operations
	/**
	 * Duplicates this buffer. The returned buffer shares its content with this buffer, but has
	 * a separate position and limit
	 *
	 * @return the new buffer
	 */
	def duplicate: NiolBuffer

	/** Copies the readable content of this buffer in a new NiolBuffer. */
	def copyRead: NiolBuffer = copy(readPos, readLimit)

	/** Copies a portion of this buffer in a new buffer. */
	def copy(begin: Int, end: Int): NiolBuffer //absolute, exclusive end

	/** Creates a view of the buffer's readable data. */
	def subRead: NiolBuffer = sub(readPos, readLimit)

	/** Creates a view of the buffer's writeable space. */
	def subWrite: NiolBuffer = sub(writePos, writeLimit)

	/** Creates a view of a portion of this buffer. */
	def sub(begin: Int, end: Int): NiolBuffer // absolute, exclusive end

	/** Concatenates two buffers without copying their content. */
	def concat(buffer: NiolBuffer): NiolBuffer = {
		if (this.capacity == 0) {if (buffer.capacity == 0) EmptyBuffer else buffer.duplicate}
		else if (buffer.capacity == 0) this.duplicate
		else new CompositeBuffer(this, buffer)
	}

	/** Concatenates two buffers by copying them to a new buffer. */
	def concatCopy(buffer: NiolBuffer): NiolBuffer = {
		val availableThis = this.readAvail
		val availableBuff = buffer.readAvail
		if (availableThis == 0) {if (availableBuff == 0) EmptyBuffer else buffer.copyRead}
		else if (availableBuff == 0) this.copyRead
		else {
			val copy = NioBasedBuffer.allocateHeap(availableThis + availableBuff)
			this.duplicate >>: copy
			buffer.duplicate >>: copy
			copy
		}
	}

	/** Compacts this buffer by moving its readable content to position 0 if possible. */
	def compact(): Unit

	/**
	 * Clears this buffer. readPos, readLimit and writePos are set to 0 and the writeLimit is
	 * set to the capacity.
	 */
	def clear() = {
		readPos(0)
		readLimit(0)
		writePos(0)
		writeLimit(capacity)
	}

	// shortcuts
	/** Concatenates two buffers without copying their content. */
	@inline final def +(buffer: NiolBuffer): NiolBuffer = concat(buffer)

	/** Concatenates two buffers by copying them to a new buffer. */
	@inline final def +++(buffer: NiolBuffer): NiolBuffer = concatCopy(buffer)

	// overrides
	override def putBytes(src: NiolInput): Unit = src.getBytes(this)

	/** @return a String containing the informations about the state of this buffer. */
	override def toString: String =
		s"""NiolBuffer(
		   | capacity=$capacity;
		   | writePos=$writePos,
		   | writeLimit=$writeLimit,
		   | writeAvail=$writeAvail;
		   | readPos=$readPos,
		   | readLimit=$readLimit,
		   | readAvail=$readAvail)""".stripMargin
}
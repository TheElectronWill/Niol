package com.electronwill.niol.buffer

/**
 * A NiolBuffer that can be randomly accessed like an array of bytes.
 *
 * @author TheElectronWill
 */
abstract class RandomAccessBuffer extends NiolBuffer {
	// write state
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

	override def writeAvail: Int = writeLimit - writePos

	override def skipWrite(n: Int): Unit = writePos(writePos + n)

	// read state
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

	override def readAvail: Int = readLimit - readPos

	override def skipRead(n: Int): Unit = readPos(readPos + n)

	// buffer operations
	def copyRead: RandomAccessBuffer = copy(readPos, readLimit)
	/**
	 * Copies a portion of this buffer in a new buffer.
	 *
	 * @param begin the beginning of the copy, inclusive
	 * @param end   the end of the copy, exclusive
	 */
	def copy(begin: Int, end: Int): RandomAccessBuffer //absolute, exclusive end

	/**
	 * Creates a view of a portion of this buffer.
	 *
	 * @param begin the beginning of the view, inclusive
	 * @param end   the end of the view, exclusive
	 */
	def sub(begin: Int, end: Int): RandomAccessBuffer // absolute, exclusive end

	override def subRead: RandomAccessBuffer = subRead(readAvail)

	def subRead(maxLength: Int): RandomAccessBuffer

	override def subWrite: RandomAccessBuffer = subWrite(writeAvail)

	def subWrite(maxLength: Int): RandomAccessBuffer

	def duplicate: RandomAccessBuffer

	/**
	 * Clears this buffer. readPos, readLimit and writePos are set to 0 and the writeLimit is
	 * set to the capacity.
	 */
	override def clear(): Unit = {
		readPos(0)
		readLimit(0)
		writePos(0)
		writeLimit(capacity)
	}

	override def toString: String =
		s"""${getClass.getSimpleName}(
		   | capacity=$capacity;
		   | writePos=$writePos,
		   | writeLimit=$writeLimit,
		   | writeAvail=$writeAvail;
		   | readPos=$readPos,
		   | readLimit=$readLimit,
		   | readAvail=$readAvail)""".stripMargin
}
package com.electronwill.niol.buffer

import java.nio.ByteBuffer
import java.nio.channels.{GatheringByteChannel, ScatteringByteChannel}

/**
 * A straight buffer enforcing 0 <= readPos < readLimit = writePos < writeLimit
 *
 * @author TheElectronWill
 */
final class StraightBuffer(private[this] val buff: RandomAccessBuffer) extends RandomAccessBuffer {
  require(buff.capacity > 0)
  buff.markUsed()

  // buffer state
  override def capacity: Int = buff.capacity

  override def writePos: Int = buff.writePos

  override def writePos(pos: Int): Unit = buff.writePos(pos)

  override def writeLimit: Int = buff.writeLimit

  override def writeLimit(limit: Int): Unit = buff.writeLimit(limit)

  override def markWritePos(): Unit = buff.markWritePos()

  override def resetWritePos(): Unit = buff.markReadPos()

  override def skipWrite(n: Int): Unit = writePos(readPos + n)

  override def readPos: Int = buff.readPos

  override def readPos(pos: Int): Unit = buff.readPos(pos)

  override def readLimit: Int = buff.readLimit

  override def readLimit(limit: Int): Unit = buff.readLimit(limit)

  override def markReadPos(): Unit = buff.markReadPos()

  override def resetReadPos(): Unit = buff.resetReadPos()

  override def skipRead(n: Int): Unit = readPos(readPos + n)

  // buffer operations
  override def copy(begin: Int, end: Int): RandomAccessBuffer = buff.copy(begin, end)

  override def subRead(maxLength: Int): RandomAccessBuffer = buff.subRead(maxLength)

  override def subWrite(maxLength: Int): RandomAccessBuffer = buff.subWrite(maxLength)

  override def lsub(begin: Int, end: Int): RandomAccessBuffer = buff.lsub(begin, end)

  override def sub(begin: Int, end: Int): RandomAccessBuffer = buff.sub(begin, end)

  override def duplicate: RandomAccessBuffer = new StraightBuffer(buff.duplicate)

  override def compact(): Unit = buff.compact()

  override def discard(): Unit = {
    if (useCount.decrementAndGet() == 0) {
      buff.discard()
    }
  }

  override protected[niol] def freeMemory(): Unit = buff.freeMemory()

  // get methods
  override def getByte(): Byte = buff.getByte()

  override def getShort(): Short = buff.getShort()

  override def getChar(): Char = buff.getChar()

  override def getInt(): Int = buff.getInt()

  override def getLong(): Long = buff.getLong()

  override def getFloat(): Float = java.lang.Float.intBitsToFloat(getInt())

  override def getDouble(): Double = java.lang.Double.longBitsToDouble(getLong())

  override def getBytes(dest: ByteBuffer): Unit = buff.getBytes(dest)

  override def getBytes(dest: NiolBuffer): Unit = buff.getBytes(dest)

  override def getBytes(dest: GatheringByteChannel): Int = buff.getBytes(dest)

  override def getBytes(dest: Array[Byte], offset: Int, length: Int): Unit = {
    buff.getBytes(dest, offset, length)
  }

  override def getShorts(dest: Array[Short], offset: Int, length: Int): Unit = {
    buff.getShorts(dest, offset, length)
  }

  override def getInts(dest: Array[Int], offset: Int, length: Int): Unit = {
    buff.getInts(dest, offset, length)
  }

  override def getLongs(dest: Array[Long], offset: Int, length: Int): Unit = {
    buff.getLongs(dest, offset, length)
  }

  override def getFloats(dest: Array[Float], offset: Int, length: Int): Unit = {
    buff.getFloats(dest, offset, length)
  }

  override def getDoubles(dest: Array[Double], offset: Int, length: Int): Unit = {
    buff.getDoubles(dest, offset, length)
  }

  // put methods
  override def putByte(b: Byte): Unit = {
    buff.putByte(b)
    readLimit(writePos)
  }

  override def putShort(s: Short): Unit = {
    buff.putShort(s)
    readLimit(writePos)
  }

  override def putInt(i: Int): Unit = {
    buff.putInt(i)
    readLimit(writePos)
  }

  override def putLong(l: Long): Unit = {
    buff.putLong(l)
    readLimit(writePos)
  }

  override def putFloat(f: Float): Unit = {
    buff.putFloat(f)
    readLimit(writePos)
  }

  override def putDouble(d: Double): Unit = {
    buff.putDouble(d)
    readLimit(writePos)
  }

  override def putBytes(src: Array[Byte], offset: Int, length: Int): Unit = {
    buff.putBytes(src, offset, length)
    readLimit(writePos)
  }

  override def putBytes(src: ByteBuffer): Unit = {
    buff.putBytes(src)
    readLimit(writePos)
  }

  override def putBytes(src: ScatteringByteChannel): (Int, Boolean) = {
    val result = buff.putBytes(src)
    readLimit(writePos)
    result
  }

  override def putShorts(src: Array[Short], offset: Int, length: Int): Unit = {
    putShorts(src, offset, length)
    readLimit(writePos)
  }

  override def putInts(src: Array[Int], offset: Int, length: Int): Unit = {
    putInts(src, offset, length)
    readLimit(writePos)
  }

  override def putLongs(src: Array[Long], offset: Int, length: Int): Unit = {
    putLongs(src, offset, length)
    readLimit(writePos)
  }

  override def putFloats(src: Array[Float], offset: Int, length: Int): Unit = {
    putFloats(src, offset, length)
    readLimit(writePos)
  }

  override def putDoubles(src: Array[Double], offset: Int, length: Int): Unit = {
    putDoubles(src, offset, length)
    readLimit(writePos)
  }
}

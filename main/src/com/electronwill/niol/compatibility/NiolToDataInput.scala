package com.electronwill.niol.compatibility

import java.io.DataInput
import java.nio.charset.StandardCharsets

import com.electronwill.niol.NiolInput

/**
 * DataInput that wraps a NiolInput.
 *
 * @param source the NiolInput to wrap
 */
final class NiolToDataInput(private[this] val source: NiolInput) extends DataInput {

  override def readFully(bytes: Array[Byte]): Unit = source.getBytes(bytes)

  override def readFully(bytes: Array[Byte], offset: Int, length: Int): Unit = {
    source.getBytes(bytes, offset, length)
  }

  override def skipBytes(n: Int): Int = {
    var i = 0
    while (i < n) {
      source.getByte()
      i += 1
    }
    n
  }

  override def readBoolean(): Boolean = source.getBool()

  override def readByte(): Byte = source.getByte()

  override def readUnsignedByte(): Int = source.getUnsignedByte()

  override def readShort(): Short = source.getShort()

  override def readUnsignedShort(): Int = source.getUnsignedShort()

  override def readChar(): Char = source.getChar()

  override def readInt(): Int = source.getInt()

  override def readLong(): Long = source.getLong()

  override def readFloat(): Float = source.getFloat()

  override def readDouble(): Double = source.getDouble()

  override def readLine(): String = {
    val sb = new StringBuilder()
    var c = source.getChar()
    while (c != '\n') {
      sb.append(c)
      c = source.getChar()
    }
    sb.toString()
  }

  override def readUTF(): String = {
    val size = source.getUnsignedShort()
    source.getString(size, StandardCharsets.UTF_8)
  }
}

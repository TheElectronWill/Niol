package com.electronwill.niol.compatibility

import java.io.DataInput
import java.nio.charset.StandardCharsets

import com.electronwill.niol.NiolInput

/**
 * A [[DataInput]] wrapping a [[NiolInput]].
 *
 * @param in the NiolInput to use
 */
final class NiolToDataInput(private[this] val in: NiolInput) extends DataInput {

  override def readBoolean(): Boolean = in.readBool()

  override def readByte(): Byte = in.readByte()

  override def readUnsignedByte(): Int = in.readUnsignedByte()

  override def readShort(): Short = in.readShort()

  override def readUnsignedShort(): Int = in.readUnsignedShort()

  override def readChar(): Char = in.readChar()

  override def readInt(): Int = in.readInt()

  override def readLong(): Long = in.readLong()

  override def readFloat(): Float = in.readFloat()

  override def readDouble(): Double = in.readDouble()

  override def readFully(bytes: Array[Byte]): Unit = in.readBytes(bytes)

  override def readFully(bytes: Array[Byte], offset: Int, length: Int): Unit = {
    in.readBytes(bytes, offset, length)
  }

  override def skipBytes(n: Int): Int = {
    var i = 0
    while (i < n) {
      in.readByte()
      i += 1
    }
    n
  }

  override def readLine(): String = {
    val sb = new StringBuilder()
    var c = in.readChar()
    while (c != '\n') {
      sb.append(c)
      c = in.readChar()
    }
    sb.toString()
  }

  override def readUTF(): String = {
    val size = in.readUnsignedShort()
    in.readString(size, StandardCharsets.UTF_8)
  }
}

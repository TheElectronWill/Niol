package com.electronwill.niol.compatibility

import java.io.DataOutput
import java.nio.charset.StandardCharsets

import com.electronwill.niol.NiolOutput

/**
 * A [[DataOutput]] wrapping a [[NiolOutput]].
 *
 * @param out the NiolOutput to use
 */
final class NiolToDataOutput(private[this] val out: NiolOutput) extends DataOutput {

  override def write(i: Int): Unit = out.writeInt(i)

  override def write(bytes: Array[Byte]): Unit = out.write(bytes)

  override def write(bytes: Array[Byte], offset: Int, length: Int): Unit = {
    out.write(bytes, offset, length)
  }

  override def writeBoolean(b: Boolean): Unit = out.writeBool(b)

  override def writeByte(i: Int): Unit = out.writeByte(i)

  override def writeShort(i: Int): Unit = out.writeShort(i)

  override def writeChar(i: Int): Unit = out.writeShort(i)

  override def writeInt(i: Int): Unit = out.writeInt(i)

  override def writeLong(l: Long): Unit = out.writeLong(l)

  override def writeFloat(f: Float): Unit = out.writeFloat(f)

  override def writeDouble(d: Double): Unit = out.writeDouble(d)

  override def writeBytes(s: String): Unit = out.writeString(s, StandardCharsets.UTF_8)

  override def writeChars(s: String): Unit = out.writeString(s, StandardCharsets.UTF_16)

  override def writeUTF(s: String): Unit = {
    val encoded = StandardCharsets.UTF_8.encode(s)
    out.writeShort(encoded.remaining() & 0xffff)
    out.write(encoded)
  }
}

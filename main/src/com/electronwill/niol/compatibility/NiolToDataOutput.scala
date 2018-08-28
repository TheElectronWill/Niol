package com.electronwill.niol.compatibility

import java.io.DataOutput
import java.nio.charset.StandardCharsets

import com.electronwill.niol.NiolOutput

/**
 * DataOutput that wraps a NiolOutput.
 *
 * @param out the NiolOutput to use
 */
final class NiolToDataOutput(private[this] val out: NiolOutput) extends DataOutput {
  override def write(i: Int): Unit = out.putInt(i)

  override def write(bytes: Array[Byte]): Unit = out.putBytes(bytes)

  override def write(bytes: Array[Byte], offset: Int, length: Int): Unit = {
    out.putBytes(bytes, offset, length)
  }

  override def writeBoolean(b: Boolean): Unit = out.putBool(b)

  override def writeByte(i: Int): Unit = out.putByte(i)

  override def writeShort(i: Int): Unit = out.putShort(i)

  override def writeChar(i: Int): Unit = out.putShort(i)

  override def writeInt(i: Int): Unit = out.putInt(i)

  override def writeLong(l: Long): Unit = out.putLong(l)

  override def writeFloat(f: Float): Unit = out.putFloat(f)

  override def writeDouble(d: Double): Unit = out.putDouble(d)

  override def writeBytes(s: String): Unit = out.putString(s, StandardCharsets.UTF_8)

  override def writeChars(s: String): Unit = out.putString(s, StandardCharsets.UTF_16)

  override def writeUTF(s: String): Unit = {
    val encoded = StandardCharsets.UTF_8.encode(s)
    out.putShort(encoded.remaining() & 0xffff)
    out.putBytes(encoded)
  }
}

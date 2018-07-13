package com.electronwill.niol.compatibility

import java.io.DataOutput
import java.nio.charset.StandardCharsets

import com.electronwill.niol.NiolOutput

/**
 * DataOutput that wraps a NiolOutput.
 *
 * @param dest the NiolOutput to use
 */
final class NiolToDataOutput(private[this] val dest: NiolOutput) extends DataOutput {
  override def write(i: Int): Unit = {
    i >>: dest
  }

  override def write(bytes: Array[Byte]): Unit = {
    bytes >>: dest
  }

  override def write(bytes: Array[Byte], offset: Int, length: Int): Unit = {
    (bytes, offset, length) >>: dest
  }

  override def writeBoolean(b: Boolean): Unit = b >>: dest

  override def writeByte(i: Int): Unit = i.toByte >>: dest

  override def writeShort(i: Int): Unit = i.toShort >>: dest

  override def writeChar(i: Int): Unit = i.toChar >>: dest

  override def writeInt(i: Int): Unit = i >>: dest

  override def writeLong(l: Long): Unit = l >>: dest

  override def writeFloat(v: Float): Unit = v >>: dest

  override def writeDouble(v: Double): Unit = v >>: dest

  override def writeBytes(s: String): Unit = (s, StandardCharsets.UTF_8) >>: dest

  override def writeChars(s: String): Unit = (s, StandardCharsets.UTF_16) >>: dest

  override def writeUTF(s: String): Unit = {
    val encoded = StandardCharsets.UTF_8.encode(s)
    dest.putShort((encoded.remaining() & 0xffff).toShort)
    dest.putBytes(encoded)
  }
}

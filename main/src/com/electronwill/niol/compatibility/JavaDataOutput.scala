package com.electronwill.niol.compatibility

import java.io.{DataOutputStream, IOException}
import java.nio.ByteBuffer

import com.electronwill.niol.NiolOutput

/**
 * Niol wrapper around a [[DataOutputStream]].
 *
 * @param out the DataOutputStream to use
 */
final class JavaDataOutput(private[this] val out: DataOutputStream) extends NiolOutput {
  private[this] var closed = false

  /** @return true if the [[close]] method has been called on this objet. */
  def isClosed: Boolean = closed

  @throws[IOException]
  def close(): Unit = {
    out.close()
    closed = true
  }

  // ----- Required implementations -----
  override def isEnded: Boolean = isClosed
  override def writableBytes: Int = if (closed) 0 else Int.MaxValue

  override protected[this] def _write(b: Byte): Unit = out.write(b)
  override protected[this] def _write(from: Array[Byte], off: Int, len: Int): Unit = {
    out.write(from, off, len)
  }

  override protected[niol] def _write(from: ByteBuffer, len: Int): Unit = {
    if (from.hasArray) {
      // faster write without an intermediate buffer
      out.write(from.array(), from.position(), len)
    } else {
      val tmp = new Array[Byte](len)
      from.get(tmp)
      out.write(tmp)
    }
  }

  // ----- Overrides -----
  override def writeShort(s: Int): Unit = out.writeShort(s)

  override def writeInt(i: Int): Unit = out.writeInt(i)

  override def writeLong(l: Long): Unit = out.writeLong(l)

  override def writeFloat(f: Float): Unit = out.writeFloat(f)

  override def writeDouble(d: Double): Unit = out.writeDouble(d)

  override def write(src: Array[Byte], o: Int, l: Int): Unit = out.write(src, o, l)
}

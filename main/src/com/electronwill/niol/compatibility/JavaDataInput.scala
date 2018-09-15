package com.electronwill.niol.compatibility

import java.io.{DataInputStream, IOException, OutputStream}
import java.nio.ByteBuffer
import java.nio.channels.GatheringByteChannel

import com.electronwill.niol.buffer.NiolBuffer
import com.electronwill.niol.{NiolInput, NiolOutput}

/**
 * Niol wrapper around a [[DataInputStream]].
 *
 * @param in the DataInputStream to use
 */
final class JavaDataInput(private[this] val in: DataInputStream) extends NiolInput {
  private[this] var nextByte = in.read()
  private[this] var closed = false

  /** @return true if the [[close]] method has been called on this objet. */
  def isClosed: Boolean = closed

  @throws[IOException]
  def close(): Unit = {
    in.close()
    closed = true
  }

  // ----- Required implementations -----
  override def isReadable: Boolean = nextByte >= 0
  override def isEnded: Boolean = nextByte < 0 || closed

  override def _read(): Byte = tryRead().toByte

  override def tryRead(): Int = {
    val read = nextByte
    nextByte = in.read()
    read
  }

  override def readSome(dst: GatheringByteChannel, maxBytes: Int): Int = {
    if (isEnded) return 0
    val tmp = new Array[Byte](maxBytes)
    val read = in.read(tmp)
    if (read < 0) {
      nextByte = -1
    } else {
      val buffer = ByteBuffer.wrap(tmp)
      buffer.limit(read)
      dst.write(buffer)
    }
    read
  }

  override def readSome(dst: OutputStream, maxLength: Int): Int = {
    if (isEnded) return 0
    val tmp = new Array[Byte](maxLength)
    val read = in.read(tmp)
    if (read < 0) {
      nextByte = -1
    } else {
      dst.write(tmp, 0, read)
    }
    read
  }

  override def readSome(dst: NiolOutput, maxLength: Int): Int = {
    if (isEnded) return 0
    val tmp = new Array[Byte](maxLength)
    val read = in.read(tmp)
    if (read < 0) {
      nextByte = -1
    } else {
      dst.write(tmp, 0, read)
    }
    read
  }

  override def readSome(dst: ByteBuffer): Unit = {
    if (isEnded) return 0
    val tmp = new Array[Byte](dst.remaining())
    val read = in.read(tmp)
    if (read < 0) {
      nextByte = -1
    } else {
      dst.put(tmp, 0, read)
    }
  }

  override def read(dest: ByteBuffer): Unit = {
    val tmp = new Array[Byte](dest.remaining())
    val actualLength = in.read(tmp)
    if (actualLength < 0) {
      nextByte = -1
    } else {
      dest.put(tmp, 0, actualLength)
    }
  }

  override def read(dst: NiolOutput, length: Int): Unit = {
    val tmp = new Array[Byte](length)
    in.readFully(tmp)
  }

  // ----- Overrides -----
  override def readBytes(dest: Array[Byte], o: Int, l: Int): Unit = in.readFully(dest, o, l)

  override def read(dest: NiolBuffer): Unit = {
    val tmp = new Array[Byte](dest.writableBytes)
    val actualLength = in.read(tmp)
    dest.write(tmp, 0, actualLength)
  }
}

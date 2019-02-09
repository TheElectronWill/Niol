package com.electronwill.niol.io

import java.io.{Closeable, IOException}
import java.nio.ByteBuffer
import java.nio.channels.{FileChannel, GatheringByteChannel, ScatteringByteChannel}
import java.nio.file.{Files, Path, StandardOpenOption}

import com.electronwill.niol.buffer.storage.{BytesStorage, StorageProvider}
import com.electronwill.niol.buffer.{CircularBuffer, NiolBuffer}
import com.electronwill.niol.{NiolInput, TMP_BUFFER_SIZE}

/**
 * A NiolInput based on a ByteChannel. The channel must be in blocking mode for the ChannelInput
 * to work correctly.
 *
 * @author TheElectronWill
 */
final class ChannelInput(val channel: ScatteringByteChannel, storage: BytesStorage)
  extends NiolInput with Closeable {

  private[this] var ended = true
  private[this] val buffer = CircularBuffer(storage)

  def this(fc: FileChannel, prov: StorageProvider) = {
    this(fc, prov.getStorage(math.min(TMP_BUFFER_SIZE, fc.size().toInt)))
  }

  def this(path: Path, storage: BytesStorage) = {
    this(FileChannel.open(path, StandardOpenOption.READ), storage)
  }

  def this(path: Path, prov: StorageProvider) = {
    this(path,
         prov.getStorage(
           if (Files.isRegularFile(path)) {
             math.min(TMP_BUFFER_SIZE, Files.size(path).toInt)
           } else {
             TMP_BUFFER_SIZE
           }
         ))
  }

  override def isEnded: Boolean = ended
  override def isReadable: Boolean = !ended

  @throws[IOException]
  override def close(): Unit = {
    ended = true
    channel.close()
  }

  private[niol] def fileTransfer(dst: GatheringByteChannel, maxLen: Int): Long = {
    buffer.readSome(dst)
    val fileChannel = channel.asInstanceOf[FileChannel]
    val pos = fileChannel.position()
    fileChannel.transferTo(pos, maxLen, dst)
  }

  private def makeReadable(nBytes: Int): Unit = {
    if (buffer.readableBytes < nBytes) {
      readMore()
    }
  }

  /** @return false if EOS reached */
  private def readMore(): Boolean = {
    val eos = buffer.writeSome(channel) < 0
    if (eos) {
      close()
    }
    !eos
  }

  override protected[niol] def _read(): Byte = {
    makeReadable(1)
    buffer.readByte()
  }

  override def readShort(): Short = {
    makeReadable(2)
    buffer.readShort()
  }

  override def readChar(): Char = {
    makeReadable(2)
    buffer.readChar()
  }

  override def readInt(): Int = {
    makeReadable(4)
    buffer.readInt()
  }

  override def readLong(): Long = {
    makeReadable(8)
    buffer.readLong()
  }

  override def readFloat(): Float = {
    makeReadable(4)
    buffer.readFloat()
  }

  override def readDouble(): Double = {
    makeReadable(8)
    buffer.readDouble()
  }

  override def readBytes(dst: Array[Byte], offset: Int, length: Int): Unit = {
    var remaining = length
    do {
      if (buffer.readableBytes == 0) {
        readMore()
      }
      val l = Math.min(remaining, buffer.readableBytes)
      val off = length - remaining
      buffer.readBytes(dst, off, l)
      remaining -= l
    } while (remaining > 0)
  }

  override def read(dst: ByteBuffer): Unit = {
    do {
      buffer.read(dst)
    } while (dst.hasRemaining && readMore())
  }

  override def readSome(dst: ByteBuffer): Int = {
    makeReadable(dst.remaining())
    buffer.readSome(dst)
  }

  override def read(dst: NiolBuffer): Unit = {
    do {
      buffer.read(dst)
    } while (dst.isWritable && readMore())
  }

  override def readSome(dst: NiolBuffer): Int = {
    makeReadable(dst.writableBytes)
    buffer.readSome(dst)
  }
}

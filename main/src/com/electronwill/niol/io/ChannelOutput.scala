package com.electronwill.niol.io

import java.io.{Closeable, IOException}
import java.nio.ByteBuffer
import java.nio.channels.{FileChannel, GatheringByteChannel}
import java.nio.file.{Files, Path, StandardOpenOption}

import com.electronwill.niol.buffer.storage.{BytesStorage, StorageProvider}
import com.electronwill.niol.buffer.{CircularBuffer, NiolBuffer}
import com.electronwill.niol.{NiolOutput, TMP_BUFFER_SIZE}

/**
 * A NiolOutput based on a ByteChannel. The channel must be in blocking mode for the ChannelOutput
 * to work correctly.
 *
 * @author TheElectronWill
 */
final class ChannelOutput(val channel: GatheringByteChannel, storage: BytesStorage)
  extends NiolOutput with Closeable {

  private[this] var closed = true
  private[this] val buffer = CircularBuffer(storage)

  def this(fc: FileChannel, prov: StorageProvider) = {
    this(fc, prov.getStorage(math.min(TMP_BUFFER_SIZE, fc.size().toInt)))
  }

  def this(path: Path, storage: BytesStorage) = {
    this(FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE), storage)
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

  override def isEnded: Boolean = closed
  override def isWritable: Boolean = !closed
  override def writableBytes: Int = if (closed) 0 else Int.MaxValue

  @throws[IOException]
  def close(): Unit = {
    flush()
    channel.close()
    closed = true
  }

  def flush(): Unit = {
    buffer.readSome(channel)
  }

  private def makeWritable(n: Int): Unit = {
    if (buffer.writableBytes < n) {
      flush()
    }
  }

  override protected[niol] def _write(b: Byte): Unit = {
    makeWritable(1)
    buffer.write(b)
  }

  override protected[niol] def _write(from: Array[Byte], off: Int, len: Int): Unit = {
    flush()
    val bb = ByteBuffer.wrap(from, off, len)
    channel.write(bb)
  }

  override protected[niol] def _write(from: ByteBuffer, len: Int): Unit = {
    flush()
    channel.write(from)
  }


  override def writeShort(s: Int): Unit = {
    makeWritable(2)
    buffer.writeShort(s)
  }

  override def writeInt(i: Int): Unit = {
    makeWritable(4)
    buffer.writeInt(i)
  }

  override def writeLong(l: Long): Unit = {
    makeWritable(8)
    buffer.writeLong(l)
  }

  override def writeFloat(f: Float): Unit = {
    makeWritable(4)
    buffer.writeFloat(f)
  }

  override def writeDouble(d: Double): Unit = {
    makeWritable(8)
    buffer.writeDouble(d)
  }
  override def write(src: NiolBuffer): Unit = {
    flush()
    var written = src.readSome(channel)
    while (written > 0 && src.isReadable) {
      written = src.readSome(channel)
    }
  }

  override def writeSome(src: NiolBuffer): Int = {
    flush()
    src.readSome(channel)
  }
}

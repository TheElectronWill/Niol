package com.electronwill.niol.compatibility

import java.io.{DataInputStream, IOException}
import java.nio.ByteBuffer

import com.electronwill.niol.buffer.NiolBuffer
import com.electronwill.niol.{IncompleteReadException, NiolInput, NotEnoughDataException, TMP_BUFFER_SIZE}

/**
 * Niol wrapper around a [[DataInputStream]].
 *
 * @param in the DataInputStream to use
 */
final class JavaDataInput(private[this] val in: DataInputStream) extends NiolInput {
  private[this] var end = false

  @throws[IOException]
  def close(): Unit = {
    in.close()
    end = true
  }

  override def isReadable: Boolean = !end
  override def isEnded: Boolean = end

  override protected[niol] def _read(): Byte = tryRead().toByte

  override def tryRead(): Int = in.read()

  override def readSome(dst: ByteBuffer): Int = {
    if (isEnded) return 0
    if (dst.hasArray) {
      in.read(dst.array, dst.arrayOffset + dst.position(), dst.limit)
    } else {
      val tmp = new Array[Byte](math.min(TMP_BUFFER_SIZE, dst.remaining))
      val read = in.read(tmp)
      if (read > 0) {
        dst.put(tmp, 0, read)
      }
      read
    }
  }

  override def read(dst: ByteBuffer): Unit = {
    if (isEnded) {
      throw new NotEnoughDataException("InputStream has reached its end or has been closed")
    }
    val goal = dst.remaining
    if (dst.hasArray) {
      val read = in.read(dst.array, dst.arrayOffset + dst.position(), dst.limit)
      if (read < goal) {
        throw new IncompleteReadException(goal, read, "byte")
      }
    } else if (dst.remaining <= TMP_BUFFER_SIZE) {
      val tmp = new Array[Byte](dst.remaining)
      val read = in.read(tmp)
      if (read < goal) {
        throw new IncompleteReadException(goal, read, "byte")
      }
      dst.put(tmp, 0, read)
    } else {
      val tmp = new Array[Byte](TMP_BUFFER_SIZE)
      var read = in.read(tmp)
      var total = read
      while (read > 0 && total < goal) {
        dst.put(tmp, 0, read)
        read = in.read(tmp)
        total += read
      }
      if (total < goal) {
        throw new IncompleteReadException(goal, read, "byte")
      }
    }
  }

  override def read(dst: NiolBuffer): Unit = {
    var read = dst.writeSome(in)
    while (read > 0) {
      read = dst.writeSome(in)
    }
    if (dst.isWritable) {
      throw new IncompleteReadException("Couldn't fill the NiolBuffer")
    }
  }

  override def readSome(dst: NiolBuffer): Int = dst.writeSome(in)

  override def readBytes(dest: Array[Byte], o: Int, l: Int): Unit = in.readFully(dest, o, l)
}

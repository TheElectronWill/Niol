package com.electronwill.niol.buffer

import java.nio.channels.{GatheringByteChannel, ScatteringByteChannel}
import java.nio.{BufferOverflowException, BufferUnderflowException, ByteBuffer}

/**
 * @author TheElectronWill
 */
final class CompositeBuffer private (h: Node, r: Node, w: Node) extends NiolBuffer {
  private def this(firstNode: Node) = {
    this(firstNode, firstNode, firstNode)
  }

  def this(firstBuffer: NiolBuffer = EmptyBuffer) = {
    this(new Node(firstBuffer))
  }

  // The buffers are stored in a kind of LinkedList
  private[this] var head: Node = h
  private[this] var tail = head
  private[this] var currentRead: Node = r
  private[this] var currentWrite: Node = w
  private[this] var currentCapacity: Int = h.data.capacity

  // The number of bytes available in the next read buffers
  private[this] var readAvailNext = 0

  // The number of bytes available in the next write buffers
  private[this] var writeAvailNext = 0

  def discardExhausted(): Unit = {
    while (head.ne(currentRead) && head.ne(currentWrite)) {
      currentCapacity -= head.data.capacity
      head.data.discard()
      head = head.next
    }
  }

  def +=(buffer: NiolBuffer): Unit = {
    val newTail = new Node(buffer)
    tail.next = newTail
    tail = newTail
    currentCapacity += buffer.capacity
    readAvailNext += buffer.readAvail
    writeAvailNext += buffer.writeAvail
  }

  def +=(mBuffer: CompositeBuffer): Unit = {
    mBuffer.discardExhausted()
    chainAddDuplicates(this, mBuffer.headNode)
  }

  private def headNode: Node = head

  override def concat(buffer: NiolBuffer): NiolBuffer = {
    if (this.capacity == 0) { if (buffer.capacity == 0) EmptyBuffer else buffer.duplicate } else if (buffer.capacity == 0) {
      this.duplicate
    } else {
      val res = new CompositeBuffer()
      res += this
      res += buffer
      res
    }
  }

  // buffer state
  override def capacity: Int = currentCapacity

  override def readAvail: Int = currentRead.data.readAvail + readAvailNext

  override def writeAvail: Int = currentWrite.data.writeAvail + writeAvailNext

  // buffer operations
  private def chainAddDuplicates(dest: CompositeBuffer, begin: Node): Unit = {
    var node = begin
    while (node ne null) {
      dest += node.data.duplicate
      node = node.next
    }
  }

  override def duplicate: NiolBuffer = {
    // Duplicates the head
    discardExhausted()
    val dupRead = currentRead.shallowDuplicate
    val dupWrite = currentWrite.shallowDuplicate
    val dupHead = if (head eq currentRead) dupRead else dupWrite
    val buff = new CompositeBuffer(dupHead, dupRead, dupWrite)

    // Duplicates the next nodes
    chainAddDuplicates(buff, head.next)

    // Returns the result
    buff
  }

  override def copyRead: NiolBuffer = {
    // Copies the read head
    val copiedRead = currentRead.data.copyRead
    val buff = new CompositeBuffer(copiedRead)
    // Copies the other read buffers
    var node = currentRead.next
    while (node ne null) {
      buff += node.data.copyRead
      node = node.next
    }
    buff
  }

  override def subRead: NiolBuffer = {
    // Adds the read head
    val dupRead = currentRead.shallowDuplicate
    val buff = new CompositeBuffer(dupRead)
    // Adds the other read buffers
    chainAddDuplicates(buff, currentRead.next)
    buff
  }

  override def subRead(maxLength: Int): NiolBuffer = {
    val readHead = currentRead.data
    val headAvail = readHead.readAvail
    if (headAvail >= maxLength) {
      readHead.subRead(maxLength)
    } else {
      val buff = new CompositeBuffer(readHead.subRead)
      var node = currentRead.next
      var remaining = maxLength - headAvail
      while (node ne null) {
        val nodeData = node.data
        val nodeAvail = nodeData.readAvail
        if (nodeAvail > remaining) {
          buff += nodeData.subRead(remaining)
          node = null
        } else {
          buff += nodeData.duplicate
          remaining -= nodeAvail
          node = node.next
        }
      }
      buff
    }

  }

  override def subWrite: NiolBuffer = {
    // Adds the write head
    val dupWrite = currentWrite.shallowDuplicate
    val buff = new CompositeBuffer(dupWrite)
    // Adds the other write buffers
    chainAddDuplicates(buff, currentWrite.next)
    buff
  }

  override def subWrite(maxLength: Int): NiolBuffer = {
    val writeHead = currentWrite.data
    val headAvail = writeHead.writeAvail
    if (headAvail >= maxLength) {
      writeHead.subWrite(maxLength)
    } else {
      val buff = new CompositeBuffer(writeHead.subWrite)
      var node = currentWrite.next
      var remaining = maxLength - headAvail
      while (node ne null) {
        val nodeData = node.data
        val nodeAvail = nodeData.writeAvail
        if (nodeAvail > remaining) {
          buff += nodeData.subWrite(remaining)
          node = null
        } else {
          buff += nodeData.duplicate
          remaining -= nodeAvail
          node = node.next
        }
      }
      buff
    }
  }

  override def clear(): Unit = {
    discardExhausted()
    var node = head
    while (node ne null) {
      node.data.clear()
      node = node.next
    }
  }

  override def compact(): Unit = discardExhausted()

  override def discard(): Unit = {
    if (useCount.decrementAndGet() == 0) {
      var node = head
      while (node ne null) {
        node.data.discard()
        node = node.next
      }
      head = null
      currentRead = null
      currentWrite = null
    }
  }

  override def skipWrite(n: Int): Unit = {
    if (writeAvail < n) throw new BufferUnderflowException
    var remaining = n
    while (remaining > 0) {
      // Skips the bytes
      val l = Math.min(currentWrite.data.writeAvail, remaining)
      currentWrite.data.skipWrite(l)
      // Updates the counter
      remaining -= l
      // Moves to the next buffer if needed
      if (remaining > 0) {
        moveToNextWrite()
      }
    }
  }

  override def skipRead(n: Int): Unit = {
    if (readAvail < n) throw new BufferUnderflowException
    var remaining = n
    while (remaining > 0) {
      // Skips the bytes
      val l = Math.min(currentRead.data.readAvail, remaining)
      currentRead.data.skipRead(l)
      // Updates the counter
      remaining -= l
      // Moves to the next buffer if needed
      if (remaining > 0) {
        moveToNextRead()
      }
    }
  }

  // get operations
  private def moveToNextRead(): Unit = {
    currentRead = currentRead.next
    readAvailNext -= currentRead.data.readAvail
  }

  private def removeWriteAvail(n: Int): Unit = {
    if (currentWrite ne currentRead) {
      writeAvailNext -= n
    }
  }

  private def canReadDirectly(count: Int): Boolean = {
    val avail = currentRead.data.readAvail
    if (avail == 0) {
      if (currentRead.next eq null) {
        throw new BufferUnderflowException
      } else {
        do {
          moveToNextRead()
        } while (currentRead.data.readAvail == 0)
        currentRead.data.readAvail >= count
      }
    } else if (avail < count) {
      if (currentRead.next eq null) {
        throw new BufferUnderflowException
      } else {
        false
      }
    } else {
      true
    }
  }

  override def getByte(): Byte = {
    while (!currentRead.data.canRead) {
      if (currentRead.next eq null) {
        throw new BufferUnderflowException
      }
      moveToNextRead()
    }
    currentRead.data.getByte()
  }

  override def getShort(): Short = {
    if (canReadDirectly(2)) {
      currentRead.data.getShort()
    } else {
      (getByte() << 8 | getByte()).toShort
    }
  }

  override def getChar(): Char = getShort().toChar

  override def getInt(): Int = {
    if (canReadDirectly(4)) {
      currentRead.data.getInt()
    } else {
      getByte() << 24 | getByte() << 16 | getByte() << 8 | getByte()
    }
  }

  override def getLong(): Long = {
    if (canReadDirectly(8)) {
      currentRead.data.getLong()
    } else {
      getByte() << 56 | getByte() << 48 | getByte() << 40 | getByte() << 32 |
        getByte() << 24 | getByte() << 16 | getByte() << 8 | getByte()
    }
  }

  override def getFloat(): Float = {
    if (canReadDirectly(4)) {
      currentRead.data.getFloat()
    } else {
      java.lang.Float.intBitsToFloat(getInt())
    }
  }

  override def getDouble(): Double = {
    if (canReadDirectly(8)) {
      currentRead.data.getDouble()
    } else {
      java.lang.Double.longBitsToDouble(getLong())
    }
  }

  override def getBytes(dest: Array[Byte], offset: Int, length: Int): Unit = {
    var remaining = length
    while (remaining > 0) {
      // Copies the bytes
      val l = Math.min(currentRead.data.readAvail, remaining)
      currentRead.data.getBytes(dest, length - remaining, l)
      // Updates the counter
      remaining -= l
      // Updates writeAvail
      removeWriteAvail(l)
      // Moves to the next buffer if needed
      if (remaining > 0) {
        if (currentRead.next eq null) throw new BufferUnderflowException
        moveToNextRead()
      }
    }
  }

  override def getBytes(dest: ByteBuffer): Unit = {
    var again = true
    while (again) {
      val pos0 = dest.position()
      currentRead.data.getBytes(dest)
      if ((currentRead.next ne null) && dest.hasRemaining) {
        removeWriteAvail(dest.position() - pos0)
        moveToNextRead()
      } else {
        again = false
      }
    }
  }

  override def getBytes(dest: NiolBuffer): Unit = {
    var again = true
    while (again) {
      val readData = currentRead.data
      val avail0 = readData.readAvail
      readData.getBytes(dest)
      removeWriteAvail(readData.readAvail - avail0)
      if ((currentRead.next ne null) && dest.writeAvail > 0) {
        moveToNextRead()
      } else {
        again = false
      }
    }
  }

  override def getBytes(dest: GatheringByteChannel): Int = {
    var count = 0
    count += currentRead.data.getBytes(dest)
    removeWriteAvail(count)
    while (currentRead.next ne null) {
      moveToNextRead()
      val l = currentRead.data.getBytes(dest)
      removeWriteAvail(l)
      count += l
    }
    count
  }

  override def getShorts(dest: Array[Short], offset: Int, length: Int): Unit = {
    // TODO optimize: avoid copying
    val bytes = getBytes(length * 2)
    ByteBuffer.wrap(bytes).asShortBuffer().get(dest, offset, length)
  }

  override def getInts(dest: Array[Int], offset: Int, length: Int): Unit = {
    val bytes = getBytes(length * 4)
    ByteBuffer.wrap(bytes).asIntBuffer().get(dest, offset, length)
  }

  override def getLongs(dest: Array[Long], offset: Int, length: Int): Unit = {
    val bytes = getBytes(length * 8)
    ByteBuffer.wrap(bytes).asLongBuffer().get(dest, offset, length)
  }

  override def getFloats(dest: Array[Float], offset: Int, length: Int): Unit = {
    val bytes = getBytes(length * 4)
    ByteBuffer.wrap(bytes).asFloatBuffer().get(dest, offset, length)
  }

  override def getDoubles(dest: Array[Double], offset: Int, length: Int): Unit = {
    val bytes = getBytes(length * 8)
    ByteBuffer.wrap(bytes).asDoubleBuffer().get(dest, offset, length)
  }

  // put operations
  private def moveToNextWrite(): Unit = {
    currentWrite = currentWrite.next
    writeAvailNext -= currentWrite.data.writeAvail
  }

  private def addReadAvail(n: Int): Unit = {
    if (currentWrite ne currentRead) {
      readAvailNext += n
    }
  }

  private def canWriteDirectly(count: Int): Boolean = {
    val avail = currentWrite.data.writeAvail
    if (avail == 0) {
      if (currentWrite.next eq null) {
        throw new BufferOverflowException
      } else {
        do {
          moveToNextWrite()
        } while (currentWrite.data.writeAvail == 0)
        currentWrite.data.writeAvail >= count
      }
    } else if (avail < count) {
      if (currentWrite.next eq null) {
        throw new BufferOverflowException
      } else {
        false
      }
    } else {
      true
    }
  }

  override def putByte(b: Byte): Unit = {
    while (currentWrite.data.writeAvail == 0) {
      if (currentWrite.next eq null) {
        throw new BufferOverflowException
      }
      moveToNextWrite()
    }
    currentWrite.data.putByte(b)
  }

  override def putShort(s: Short): Unit = {
    if (canWriteDirectly(2)) {
      currentWrite.data.putShort(s)
    } else {
      putByte(s >> 8)
      putByte(s)
    }
  }

  override def putInt(i: Int): Unit = {
    if (canWriteDirectly(4)) {
      currentWrite.data.putInt(i)
    } else {
      putByte(i >> 24)
      putByte(i >> 16)
      putByte(i >> 8)
      putByte(i)
    }
  }

  override def putLong(l: Long): Unit = {
    if (canWriteDirectly(8)) {
      currentWrite.data.putLong(l)
    } else {
      putByte(l >> 56)
      putByte(l >> 48)
      putByte(l >> 40)
      putByte(l >> 32)
      putByte(l >> 24)
      putByte(l >> 24)
      putByte(l >> 16)
      putByte(l >> 8)
      putByte(l)
    }
  }

  override def putFloat(f: Float): Unit = {
    putInt(java.lang.Float.floatToIntBits(f))
  }

  override def putDouble(d: Double): Unit = {
    putDouble(java.lang.Double.doubleToLongBits(d))
  }

  // bulk put methods
  override def putBytes(src: Array[Byte], offset: Int, length: Int): Unit = {
    var remaining = length
    while (remaining > 0) {
      // Copies the bytes
      val l = Math.min(currentWrite.data.writeAvail, remaining)
      currentWrite.data.putBytes(src, length - remaining, l)
      // Updates the counter
      remaining -= l
      // Updates readAvail
      addReadAvail(l)
      // Moves to the next buffer if needed
      if (remaining > 0) {
        if (currentWrite.next eq null) throw new BufferOverflowException
        moveToNextWrite()
      }
    }
  }

  override def putBytes(src: ByteBuffer): Unit = {
    var again = true
    while (again) {
      val pos0 = src.position()
      currentWrite.data.putBytes(src)
      if ((currentWrite.next ne null) && src.hasRemaining) {
        addReadAvail(src.position() - pos0)
        moveToNextWrite()
      } else {
        again = false
      }
    }
  }

  override def putBytes(src: ScatteringByteChannel): (Int, Boolean) = {
    var totalRead = 0
    var stop = false
    var resultEos = false
    while (!stop) {
      val (read, eos) = currentWrite.data.putBytes(src)
      totalRead += read
      stop = eos || (currentWrite.next eq null)
      addReadAvail(read)
      if (!stop) {
        moveToNextWrite()
      } else {
        resultEos = eos
      }
    }
    (totalRead, resultEos)
  }

  override def putShorts(src: Array[Short], offset: Int, length: Int): Unit = {
    val bytes = ByteBuffer.allocate(length * 2)
    bytes.asShortBuffer().put(src, offset, length)
    putBytes(bytes)
  }

  override def putInts(src: Array[Int], offset: Int, length: Int): Unit = {
    val bytes = ByteBuffer.allocate(length * 4)
    bytes.asIntBuffer().put(src, offset, length)
    putBytes(bytes)
  }

  override def putLongs(src: Array[Long], offset: Int, length: Int): Unit = {
    val bytes = ByteBuffer.allocate(length * 8)
    bytes.asLongBuffer().put(src, offset, length)
    putBytes(bytes)
  }

  override def putFloats(src: Array[Float], offset: Int, length: Int): Unit = {
    val bytes = ByteBuffer.allocate(length * 4)
    bytes.asFloatBuffer().put(src, offset, length)
    putBytes(bytes)
  }

  override def putDoubles(src: Array[Double], offset: Int, length: Int): Unit = {
    val bytes = ByteBuffer.allocate(length * 8)
    bytes.asDoubleBuffer().put(src, offset, length)
    putBytes(bytes)
  }
}

private final class Node(var data: NiolBuffer) {
  data.markUsed()
  var next: Node = _

  def shallowDuplicate: Node = new Node(data.duplicate)
}

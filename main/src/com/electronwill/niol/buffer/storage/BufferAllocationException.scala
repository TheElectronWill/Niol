package com.electronwill.niol.buffer.storage

class BufferAllocationException(msg: String) extends Exception(msg) {
  def this() = this("All the pool's buffers are in use and no more allocation is allowed")
}

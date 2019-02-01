package com.electronwill

package object niol {
  private[niol] final val TMP_BUFFER_SIZE = 4096

  /** Throws an exception if the write operation is incomplete */
  def checkCompleteWrite(expected: Int, actual: Int, v: String = "value"): Unit = {
    if (actual != expected) throw new IncompleteWriteException(expected, actual, v)
  }

  /** Throws an exception if the read operation is incomplete */
  def checkCompleteRead(expected: Int, actual: Int, v: String = "value"): Unit = {
    if (actual != expected) throw new IncompleteReadException(expected, actual, v)
  }
}

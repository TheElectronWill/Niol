package com.electronwill.niol

/**
 * Trait for objects that can be read from [[NiolInput]].
 */
trait Readable {
  /**
   * Reads this object.
   *
   * @param in the input to read from
   */
  def readFrom(in: NiolInput): Unit
}

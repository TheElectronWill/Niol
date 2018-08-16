package com.electronwill.niol

/**
 * Trait for objects that can be written to [[NiolOutput]].
 */
trait Writeable {
  /**
   * Writes this object.
   *
   * @param out the output to write to
   */
  def writeTo(out: NiolOutput): Unit
}

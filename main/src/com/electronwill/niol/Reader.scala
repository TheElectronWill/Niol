package com.electronwill.niol

/**
 * A reader can read some objects from [[NiolInput]].
 *
 * @tparam A the result type
 */
trait Reader[A] {
  /**
   * Reads an object.
   *
   * @param in the input to read from
   */
  def readFrom(in: NiolInput): A
}

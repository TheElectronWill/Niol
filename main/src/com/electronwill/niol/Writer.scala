package com.electronwill.niol

/**
 * A writer can write some objects to [[NiolOutput]].
 *
 * @tparam A the object type
 */
trait Writer[-A] {
  /**
   * Reads an object.
   *
   * @param to the output to write to
   */
  def write(data: A, to: NiolOutput): Unit
}

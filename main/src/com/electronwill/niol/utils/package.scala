package com.electronwill.niol
import scala.reflect.ClassTag

package object utils {
  private[utils] def grow[T: ClassTag](array: Array[T], newLength: Int): Array[T] = {
    val newArray = new Array[T](newLength)
    System.arraycopy(array, 0, newArray, 0, array.length)
    newArray
  }
  private[utils] def shrink[T: ClassTag](array: Array[T], newLength: Int): Array[T] = {
    val newArray = new Array[T](newLength)
    System.arraycopy(array, 0, newArray, 0, newLength)
    newArray
  }
  private[utils] def growAmortize[T: ClassTag](array: Array[T], minLength: Int): Array[T] = {
    val l = array.length
    grow(array, Math.max(minLength, l + l >> 1))
  }

  /**
   * Checks if a positive integer is a power of 2.
   *
   * @param n the integer, > 0
   * @return true if it's a power of two
   */
  def isPowerOfTwo(n: Int): Boolean = (n & (n-1)) == 0

  /**
   * Checks if n is a strictly positive power of 2.
   *
   * @param n the number to check
   * @return true if it's a positive power of two
   */
  def isPositivePowerOfTwo(n: Int): Boolean = (n > 0) && isPowerOfTwo(n)
}

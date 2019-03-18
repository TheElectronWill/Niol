package com.electronwill.niol.network

package object tcp {

  /**
   * @param array an array containing the data, starting at index 0
   * @param length the length of the data
   */
  final case class Bytes(array: Array[Byte], length: Int)

  type BytesTransform = Bytes => Bytes
}

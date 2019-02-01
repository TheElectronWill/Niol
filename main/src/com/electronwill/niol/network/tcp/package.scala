package com.electronwill.niol.network

package object tcp {
  final case class Bytes(array: Array[Byte], length: Int)

  type BytesTransform = Bytes => Bytes
}

package com.electronwill.niol.network.tcp

import java.security.Key

import com.electronwill.niol.buffer.NiolBuffer
import javax.crypto.Cipher

/**
 * A transformation function that decrypts data with a [[Cipher]].
 *
 * @param cipher  the cipher to use, it must be initialized properly
 * @param inPlace true to use the same array as the input and output of the cipher's update method.
 */
final class DecipherTransform(private[this] val cipher: Cipher, private[this] val inPlace: Boolean)
    extends NiolBuffer => Array[Byte] {

  /**
   * Creates a new DecipherTransform with a new Cipher instance created in decryption mode.
   *
   * @param algo the decryption algorithm to use
   * @param key  the decryption key
   */
  def this(algo: String, key: Key, inPlace: Boolean) = {
    this(Cipher.getInstance(algo), inPlace)
    cipher.init(Cipher.DECRYPT_MODE, key)
  }

  override def apply(buff: NiolBuffer): Array[Byte] = {
    val bytes = new Array[Byte](buff.readAvail)
    bytes <<: buff
    if (inPlace) {
      cipher.update(bytes, 0, bytes.length, bytes)
      bytes
    } else {
      cipher.update(bytes)
    }
  }
}

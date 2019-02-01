package com.electronwill.niol.network.tcp

import java.security.Key
import java.security.spec.AlgorithmParameterSpec

import javax.crypto.Cipher

/**
 * A transformation function that encrypts or decrypts data with a [[Cipher]].
 *
 * @param cipher the cipher to use, must be initialized
 */
final class CipherTransform(private[this] val cipher: Cipher) extends BytesTransform {
  override def apply(input: Bytes): Bytes = {
    val outputArray  = new Array[Byte](cipher.getOutputSize(input.length))
    val outputLength = cipher.update(input.array, 0, input.length, outputArray)
    Bytes(outputArray, outputLength)
  }
}
object CipherTransform {
  def encrypt(algorithm: String, key: Key): CipherTransform = {
    val cipher = Cipher.getInstance(algorithm)
    cipher.init(Cipher.ENCRYPT_MODE, key)
    new CipherTransform(cipher)
  }

  def encrypt(algorithm: String, key: Key, parameterSpec: AlgorithmParameterSpec): CipherTransform = {
    val cipher = Cipher.getInstance(algorithm)
    cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec)
    new CipherTransform(cipher)
  }

  def decrypt(algorithm: String, key: Key): CipherTransform = {
    val cipher = Cipher.getInstance(algorithm)
    cipher.init(Cipher.DECRYPT_MODE, key)
    new CipherTransform(cipher)
  }

  def decrypt(algorithm: String, key: Key, parameterSpec: AlgorithmParameterSpec): CipherTransform = {
    val cipher = Cipher.getInstance(algorithm)
    cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec)
    new CipherTransform(cipher)
  }
}

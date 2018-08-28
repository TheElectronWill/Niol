package com.electronwill.niol.buffer

import java.nio.ByteBuffer

/**
 * Traits for basic data containers without any "special" functionality.
 *
 * @author TheElectronWill
 */
trait BaseBuffer extends RandomAccessBuffer {
  /**
   * Returns a read-only view of this BaseBuffer as a Java ByteBuffer. The returned ByteBuffer
   * shares its content with the BaseBuffer.
   *
   * @return a read-only ByteBuffer view
   */
  def readBB: ByteBuffer

  /**
   * Returns a write-only view of this BaseBuffer as a Java ByteBuffer. The returned ByteBuffer
   * shares its content with the BaseBuffer.
   *
   * @return a write-only ByteBuffer view
   */
  def writeBB: ByteBuffer
}

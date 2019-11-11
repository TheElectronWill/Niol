package com.electronwill.niol

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions._
import Utils._

class MiscTest {
  @Test
  def testPositively(): Unit = {
    assertEquals(0, positively(-1))
    assertEquals(0, positively(Int.MinValue))

    assertEquals(0, positively(0))
    assertEquals(1, positively(1))
    assertEquals(2, positively(2))
    assertEquals(Int.MaxValue, positively(Int.MaxValue))
  }
}

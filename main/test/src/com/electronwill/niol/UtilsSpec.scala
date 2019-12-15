package com.electronwill.niol

import com.electronwill.niol.Utils._
import org.scalatest.flatspec.AnyFlatSpec

class UtilsSpec extends AnyFlatSpec {

  "positively(x)" should "be x if x >= 0" in {
    assert(positively(0) == 0)
    assert(positively(1) == 1)
    assert(positively(2) == 2)
    assert(positively(Int.MaxValue) == Int.MaxValue)
  }

  it should "be 0 if x < 0" in {
    assert(positively(-1) == 0)
    assert(positively(-3216547) == 0)
    assert(positively(Int.MinValue) == 0)
  }

  "isPowerOfTwo(x)" should "be true for powers of two" in {
    assert(isPowerOfTwo(0))
    assert(isPowerOfTwo(1))
    assert(isPowerOfTwo(2))
    assert(isPowerOfTwo(4))
    assert(isPowerOfTwo(8))
    assert(isPowerOfTwo(16))
    assert(isPowerOfTwo(1 << 31))
    assert(isPowerOfTwo(1 << 32))
  }

  it should "be false for other numbers" in {
    assert(!isPowerOfTwo(3))
    assert(!isPowerOfTwo(5))
    assert(!isPowerOfTwo(6))
    assert(!isPowerOfTwo(7))
    assert(!isPowerOfTwo(9))
    assert(!isPowerOfTwo(10))
    assert(!isPowerOfTwo(11))
    assert(!isPowerOfTwo(Int.MaxValue))
  }
}

package com.electronwill.niol

/**
 * Thrown when there isn't enough space to write the data. It is thrown before any data is written.
 *
 * @param msg the message
 */
class NotEnoughSpaceException(msg: String) extends Exception(msg) {
  def this(required: Int, avail: Int) = this(s"Cannot write $required bytes: writableBytes = $avail")
  def this(nValues: Int, required: Int, avail: Int) =
    this(s"Cannot write $nValues value(s) ($required bytes): writableBytes = $avail")
}

/**
 * Thrown when there isn't enough data to read. It is thrown before any data is read.
 *
 * @param msg the message
 */
class NotEnoughDataException(msg: String) extends Exception(msg) {
  def this(required: Int, avail: Int) = this(s"Cannot read $required bytes: readableBytes = $avail")
  def this(nValues: Int, required: Int, avail: Int) =
    this(s"Cannot read $nValues value(s) ($required bytes): readableBytes = $avail")
}

/**
 * Thrown when a `write` operation couldn't complete as expected. It is thrown when there isn't
 * enough space and some data has already been written.
 *
 * @param msg the message
 */
class IncompleteWriteException(msg: String) extends Exception(msg) {
  def this(nValues: Int, v: String = "value") = this(s"Tried to write $nValues $v(s) but couldn't finish")
  def this(expected: Int, actual: Int, v: String = "value") =
    this(s"Tried to write $expected ${v}s, actually wrote $actual")
}

/**
 * Thrown when a `read` operation couldn't complete as expected. It is thrown when there isn't
 * enough data to retrive and some data has already been read.
 *
 * @param msg the message
 */
class IncompleteReadException(msg: String) extends Exception(msg) {
  def this(nValues: Int, v: String = "value") = this(s"Tried to read $nValues $v(s) but couldn't finish")
  def this(expected: Int, actual: Int, v: String = "value") =
    this(s"Tried to read $expected ${v}s, actually got $actual")}

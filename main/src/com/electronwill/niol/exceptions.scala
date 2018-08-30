package com.electronwill.niol

/**
 * Thrown when there isn't enough space to put the data. It is thrown before any data is written.
 *
 * @param msg the message
 */
class NotEnoughSpaceException(msg: String) extends Exception(msg) {
  def this(required: Int, avail: Int) = this(s"Cannot put $required bytes: writeAvail = $avail")
  def this(nValues: Int, required: Int, avail: Int) =
    this(s"Cannot put $nValues value(s) ($required bytes): writeAvail = $avail")
}

/**
 * Thrown when there isn't enough data to get. It is thrown before any data is read.
 *
 * @param msg the message
 */
class NotEnoughDataException(msg: String) extends Exception(msg) {
  def this(required: Int, avail: Int) = this(s"Cannot get $required bytes: readAvail = $avail")
  def this(nValues: Int, required: Int, avail: Int) =
    this(s"Cannot get $nValues value(s) ($required bytes): readAvail = $avail")
}

/**
 * Thrown when a `put` operation couldn't complete as expected. It is thrown when there isn't
 * enough space and some data has already been written.
 *
 * @param msg the message
 */
class IncompletePutException(msg: String) extends Exception(msg) {
  def this(nValues: Int, v: String = "value") = this(s"Tried to put $nValues $v(s) but couldn't finish")
  def this(expected: Int, actual: Int, v: String = "value") =
    this(s"Tried to put $expected ${v}s, actually put $actual")
}

/**
 * Thrown when a `get` operation couldn't complete as expected. It is thrown when there isn't
 * enough data to retrive and some data has already been read.
 *
 * @param msg the message
 */
class IncompleteGetException(msg: String) extends Exception(msg) {
  def this(nValues: Int, v: String = "value") = this(s"Tried to get $nValues $v(s) but couldn't finish")
  def this(expected: Int, actual: Int, v: String = "value") =
    this(s"Tried to get $expected ${v}s, actually got $actual")}

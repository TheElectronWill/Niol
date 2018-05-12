package com.electronwill.niol

/**
 * @author TheElectronWill
 */
package object buffer {
  def printBuffer(buff: NiolBuffer): Unit = {
    buff match {
      case r: RandomAccessBuffer =>
        println(
          s"readPos: ${r.readPos}, writePos: ${r.writePos}, " +
            s"readLimit: ${r.readLimit}, writeLimit: ${r.writeLimit}, " +
            s"readAvail: ${r.readAvail}, writeAvail: ${r.writeAvail}")
      case _ =>
        println(s"readAvail: ${buff.readAvail}, writeAvail: ${buff.writeAvail}")
    }
  }
}

package com.electronwill

/**
 * @author TheElectronWill
 */
package object niol {
	def printBuffer(buff: NiolBuffer): Unit = {
		println(s"readPos: ${buff.readPos}, writePos: ${buff.writePos}, " +
			s"readLimit: ${buff.readLimit}, writeLimit: ${buff.writeLimit}, " +
			s"readAvail: ${buff.readAvail}, writeAvail: ${buff.writeAvail}")
	}
}
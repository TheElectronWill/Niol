package com.electronwill.niol

/**
 * @author TheElectronWill
 */
package object buffer {
	def printBuffer(buff: NiolBuffer): Unit = {
		println(s"readPos: ${buff.readPos}, writePos: ${buff.writePos}, " +
			s"readLimit: ${buff.readLimit}, writeLimit: ${buff.writeLimit}, " +
			s"readAvail: ${buff.readAvail}, writeAvail: ${buff.writeAvail}")
	}
}
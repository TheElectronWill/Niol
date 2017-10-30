package com.electronwill.niol.network;

/**
 * Represents the state of a ClientAttach's input buffer. There are two possible states:
 * <ol>
 * <li>READ_HEADER: The next thing to read is the header of the next packet.</li>
 * <li>READ_DATA: The next thing to read is the data of the current packet.</li>
 * </ol>
 * A new ClientAttach is in state READ_HEADER. Once the first header has been read, it switches
 * to state READ_DATA and reads the packet's data. Then it returns to state READ_HEADER, and the
 * cycle continues.
 *
 * @author TheElectronWill
 */
enum InputState {
	READ_HEADER, READ_DATA
}
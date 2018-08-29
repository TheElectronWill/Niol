package com.electronwill.niol.network.tcp;

/**
 * The state of an {@link HAttach} input. There are two possible states:
 * <ol>
 * <li>READ_HEADER: The next thing to read is the header of the next packet.</li>
 * <li>READ_DATA: The next thing to read is the data of the current packet.</li>
 * </ol>
 * A new HAttach is in state READ_HEADER. Once the first header has been read, it switches to
 * READ_DATA and reads the packet's data. Then it returns to state READ_HEADER, and the
 * cycle continues.
 *
 * @author TheElectronWill
 */
enum HInputState {
  READ_HEADER, READ_DATA
}

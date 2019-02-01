package com.electronwill.niol.buffer.storage

/**
 * Thrown when a StagedPools object cannot return a result because there is no stage that
 * corresponds to the requested capacity.
 *
 * @param msg explanation message
 */
class NoCorrespondingStageException(msg: String) extends Exception(msg) {}

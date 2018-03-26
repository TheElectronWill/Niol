package com.electronwill.niol.network.tcp

import java.nio.channels.{SelectionKey, Selector, ServerSocketChannel}

import com.electronwill.niol.buffer.provider.BufferProvider

/**
 * Contains the informations attached to a ServerSocketChannel registered to an [[ScalableSelector]].
 *
 * @param s
 * @param l
 * @param ssc
 * @param preTransformReadSize
 * @param packetBufferBaseSize
 * @param readBufferProvider
 * @param postTransformBufferProvider
 * @tparam A
 */
final class ServerChannelInfos[A](private[tcp] val s: Selector,
								  private[tcp] val l: TcpListener[A],
								  private[tcp] val ssc: ServerSocketChannel,
								  private[tcp] val preTransformReadSize: Int,
								  private[tcp] val packetBufferBaseSize: Int,
								  private[tcp] val readBufferProvider: BufferProvider,
								  private[tcp] val postTransformBufferProvider: BufferProvider) {
	private[tcp] val skey: SelectionKey = {
		ssc.register(s, SelectionKey.OP_ACCEPT, this)
	}
}

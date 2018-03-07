package com.electronwill.niol.network

import java.nio.channels.{SelectionKey, Selector, ServerSocketChannel}

import com.electronwill.niol.buffer.provider.BufferProvider

/**
 * Contains the informations attached to a ServerSocketChannel registered to an [[ScalableSelector]].
 */
final class ServerChannelInfos[A](private[tcp] val skey: SelectionKey,
								  private[tcp] val s: Selector,
								  private[tcp] val l: TcpListener[A],
								  private[tcp] val ssc: ServerSocketChannel,
								  private[tcp] val baseBufferSize: Int,
								  private[tcp] val bufferProvider: BufferProvider) {}

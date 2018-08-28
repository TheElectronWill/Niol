package com.electronwill.niol.network.tcp

import java.nio.channels.SelectionKey.OP_ACCEPT
import java.nio.channels.{SelectionKey, Selector, ServerSocketChannel}

/**
 * Contains the informations attached to a ServerSocketChannel that is registered to
 * a [[ScalableSelector]].
 *
 * @see [[ScalableSelector]]
 * @constructor Creates a ServerChannelInfos and registers the channel with the given Selector.
 * @param serverChannel            the NIO server channel
 * @param selector       thr NIO Selector
 * @param bufferSettings the settings to apply to the channel's clients
 * @param listener       the TcpListener assigned to the channel
 * @tparam A
 */
final class ServerChannelInfos[A <: ClientAttach[A]](
    private[tcp] val serverChannel: ServerSocketChannel,
    private[tcp] val selector: Selector,
    private[tcp] val bufferSettings: BufferSettings,
    private[tcp] val listener: TcpListener[A]) {
  private[tcp] val selectionKey: SelectionKey = serverChannel.register(selector, OP_ACCEPT, this)
}

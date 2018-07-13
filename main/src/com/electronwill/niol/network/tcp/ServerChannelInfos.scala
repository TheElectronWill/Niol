package com.electronwill.niol.network.tcp

import java.nio.channels.{SelectionKey, Selector, ServerSocketChannel}

import com.electronwill.niol.buffer.provider.BufferProvider

/**
 * Contains the informations attached to a ServerSocketChannel that is registered to a [[ScalableSelector]].
 *
 * @see [[ScalableSelector]]
 * @param s                           the NIO Selector of the channel
 * @param l                           the TcpListener assigned to the channel
 * @param ssc                         the channel
 * @param preTransformReadSize        the size of the read buffer, if there is a data transformation
 * @param packetBufferBaseSize        the size of the packet buffer which, if there is no transformation, is also the read buffer
 * @param readBufferProvider          the provider of the read buffer
 * @param postTransformBufferProvider the provider of the packet buffer, if there is a data transformation
 * @tparam A
 */
final class ServerChannelInfos[A <: ClientAttach](
    s: Selector,
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

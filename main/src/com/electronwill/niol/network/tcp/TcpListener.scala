package com.electronwill.niol.network.tcp

import java.nio.channels.{SelectionKey, SocketChannel}

trait TcpListener[A <: ClientAttach[A]] {

  /**
   * Called when a new TCP client connects to the server. Returns an object ClientAttach that
   * is uniquely associated to this precise client.
   *
   * @param serverInfos   informations about the ServerSocketChannel that accepted the connection
   * @param clientChannel the client's SocketChannel
   * @param selectionKey  the client's SelectionKey
   * @return a ClientAttach object associated to this client
   */
  def onAccept(
    serverInfos: ServerChannelInfos[A],
    clientChannel: SocketChannel,
    selectionKey: SelectionKey): A

  /**
   * Called when a client disconnects from the server.
   *
   * @param clientAttach the ClientAttach object associated to this client
   */
  def onDisconnect(clientAttach: A): Unit
}

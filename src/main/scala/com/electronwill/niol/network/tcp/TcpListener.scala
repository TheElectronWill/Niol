package com.electronwill.niol.network.tcp

import java.nio.channels.SocketChannel

trait TcpListener[A <: ClientAttach] {
  /**
   * Called when a new TCP client connects to the server. Returns an object ClientAttach that
   * is uniquely associated to this precise client.
   *
   * @param clientChannel the client's SocketChannel
   * @param serverChannel informations about the ServerSocketChannel that accepted the connection
   * @return a ClientAttach object associated to this client
   */
  def onAccept(clientChannel: SocketChannel, serverChannel: ServerChannelInfos[A]): A

  /**
   * Called when a client disconnects from the server.
   *
   * @param clientAttach the ClientAttach object associated to this client
   */
  def onDisconnect(clientAttach: A): Unit
}

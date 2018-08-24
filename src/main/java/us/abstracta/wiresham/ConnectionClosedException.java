package us.abstracta.wiresham;

import java.io.IOException;

/**
 * Exception thrown when a client has unexpectedly closed connection with the server.
 */
public class ConnectionClosedException extends IOException {

  private final Packet discardedPacket;

  public ConnectionClosedException(Packet discardedPacket) {
    super("Connection closed by remote end while waiting for packet");
    this.discardedPacket = discardedPacket;
  }

  public Packet getDiscardedPacket() {
    return discardedPacket;
  }
  
}

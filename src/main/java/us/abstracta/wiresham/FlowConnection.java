package us.abstracta.wiresham;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowConnection {

  private static final Logger LOG = LoggerFactory.getLogger(FlowConnection.class);

  private final Socket socket;
  private final ByteBuffer readBuffer;

  public FlowConnection(Socket socket, int readBufferSize) {
    this.socket = socket;
    this.readBuffer = ByteBuffer.allocate(readBufferSize);
    this.readBuffer.limit(0);
  }

  public void write(byte[] data) throws IOException {
    socket.getOutputStream().write(data);
  }

  public ByteBuffer read() throws IOException {
    if (!readBuffer.hasRemaining()) {
      LOG.trace("reading from socket");
      int count = socket.getInputStream().read(readBuffer.array(), readBuffer.position(),
          readBuffer.capacity() - readBuffer.position());
      if (count == -1) {
        throw new ConnectionClosedException(
            Packet.fromBytes(readBuffer.array(), 0, readBuffer.position()));
      }
      readBuffer.limit(readBuffer.position() + count);
      if (LOG.isTraceEnabled()) {
        LOG.trace("read from socket: {}",
            Packet.fromBytes(readBuffer.array(), readBuffer.position(), count));
      }
    }
    return readBuffer;
  }

  public void close() throws IOException {
    if (socket.isClosed()) {
      return;
    }
    socket.close();
  }

  public int getPort() {
    return socket.getLocalPort();
  }
}

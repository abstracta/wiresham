package us.abstracta.wiresham;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Drives the flow of a connection according to a configured flow.
 */
public class ConnectionFlowDriver implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(ConnectionFlowDriver.class);

  private final Socket socket;
  private final Queue<PacketStep> flowSteps;
  private final ByteBuffer readBuffer;
  private volatile boolean closed;

  public ConnectionFlowDriver(Socket socket, int readBufferSize, Flow flow) {
    this.socket = socket;
    this.flowSteps = new LinkedList<>(flow.getSteps());
    readBuffer = ByteBuffer.allocate(readBufferSize);
    readBuffer.limit(0);
  }

  @Override
  public void run() {
    MDC.put("connectionId", getId());
    try {
      LOG.info("starting new flow ...");
      while (!flowSteps.isEmpty()) {
        PacketStep step = flowSteps.poll();
        step.process(this);
      }
      LOG.info("flow completed!");
    } catch (ConnectionClosedException e) {
      LOG.info("Connection closed by client while waiting for client packet");
      if (e.getDiscardedPacket().getBytes().length > 0) {
        LOG.debug("Discarding client packet {}", e.getDiscardedPacket(), e);
      }
    } catch (IOException e) {
      if (closed) {
        LOG.trace("Received expected exception when server socket has been closed", e);
      } else {
        LOG.error("Problem while processing requests from client. Closing connection.", e);
      }
    } catch (InterruptedException e) {
      LOG.trace("The thread has been interrupted", e);
      Thread.currentThread().interrupt();
    } finally {
      try {
        close();
      } catch (IOException e) {
        LOG.error("Problem when releasing client connection socket", e);
      }
      MDC.clear();
    }
  }

  public String getId() {
    return socket.getInetAddress().toString() + ":" + socket.getPort();
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
    if (closed) {
      return;
    }
    closed = true;
    socket.close();
  }

}

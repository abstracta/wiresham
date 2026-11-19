package us.abstracta.wiresham;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step in a flow which waits to receive a packet.
 */
public class ReceivePacketStep extends PacketStep {

  private static final Logger LOG = LoggerFactory.getLogger(ReceivePacketStep.class);
  private static final int MAX_IGNORED_BYTES_PER_LOG = 1024;

  private PacketPattern pattern;

  public ReceivePacketStep() {
  }

  public ReceivePacketStep(String data) {
    this(data, 0);
  }

  public ReceivePacketStep(String data, int port) {
    super(port);
    this.pattern = PacketPattern.parse(data);
  }

  @Override
  public String getData() {
    return pattern != null ? pattern.toString() : null;
  }

  /**
   * Sets the content of the packet to wait for.
   *
   * @param data hex dump of the expected packet, which may include {@code .} for any byte,
   * {@code .{n}} for any n bytes and {@code (?<name>...)} to capture the received bytes with a
   * name.
   */
  @Override
  public void setData(String data) {
    this.pattern = PacketPattern.parse(data);
  }

  Set<String> captureNames() {
    return pattern != null ? pattern.getCaptureNames() : Collections.emptySet();
  }

  boolean usesWildcards() {
    return pattern != null && !pattern.isLiteral();
  }

  int expectedLength() {
    return pattern != null ? pattern.length() : 0;
  }

  @Override
  public void process(FlowConnection flowConnection) throws IOException {
    process(flowConnection, new FlowContext());
  }

  @Override
  public void process(FlowConnection flowConnection, FlowContext context) throws IOException {
    LOG.debug("Waiting for {}", pattern);
    int size = pattern.length();
    if (size == 0) {
      return;
    }
    /*
     The last received bytes are kept in a circular buffer with the size of the expected packet,
     which is compared with the expected packet each time a byte is received. This allows finding
     the expected packet at any position of the received bytes.
     */
    byte[] window = new byte[size];
    // ignored bytes are only collected for logging them
    ByteArrayOutputStream ignored = LOG.isTraceEnabled() ? new ByteArrayOutputStream() : null;
    long receivedCount = 0;
    int pos = 0;
    while (receivedCount < size || !pattern.matches(window, pos)) {
      byte received = readByte(flowConnection, window, receivedCount, ignored);
      if (ignored != null && receivedCount >= size) {
        // the oldest byte is discarded since it is not part of the expected packet
        ignored.write(window[pos]);
        if (ignored.size() == MAX_IGNORED_BYTES_PER_LOG) {
          logIgnored(ignored);
        }
      }
      window[pos] = received;
      receivedCount++;
      pos = (int) (receivedCount % size);
    }
    logIgnored(ignored);
    if (pattern.isLiteral()) {
      LOG.debug("received expected {}", pattern);
    } else {
      LOG.debug("received {} matching expected {}", PacketPattern.copy(window, pos, size),
          pattern);
    }
    for (Map.Entry<String, Packet> capture : pattern.capture(window, pos).entrySet()) {
      LOG.debug("captured {} as {}", capture.getValue(), capture.getKey());
      context.capture(capture.getKey(), capture.getValue());
    }
  }

  private void logIgnored(ByteArrayOutputStream ignored) {
    if (ignored == null || ignored.size() == 0) {
      return;
    }
    LOG.trace("ignoring received {} while waiting for {}",
        Packet.fromBytes(ignored.toByteArray(), 0, ignored.size()), pattern);
    ignored.reset();
  }

  private byte readByte(FlowConnection flowConnection, byte[] window, long receivedCount,
      ByteArrayOutputStream ignored) throws IOException {
    ByteBuffer readBuffer;
    try {
      readBuffer = flowConnection.read();
    } catch (ConnectionClosedException e) {
      logIgnored(ignored);
      // the bytes which may be the start of the expected packet are reported as discarded
      int pendingCount = (int) Math.min(receivedCount, window.length);
      throw new ConnectionClosedException(
          PacketPattern.copy(window, receivedCount - pendingCount, pendingCount));
    }
    byte ret = readBuffer.get();
    if (!readBuffer.hasRemaining()) {
      // leave the buffer empty, so next read from the connection uses all of its capacity
      readBuffer.clear();
      readBuffer.limit(0);
    }
    return ret;
  }

  @Override
  public String toString() {
    return "client: " + pattern;
  }

}

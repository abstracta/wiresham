package us.abstracta.wiresham;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
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

  @Override
  public void setData(String data) {
    this.pattern = PacketPattern.parse(data);
  }

  @Override
  protected void checkVariables(Set<String> definedVariables) {
    definedVariables.addAll(pattern.getCaptureNames());
  }

  @Override
  protected int getExpectedPacketLength() {
    return pattern.length();
  }

  @Override
  protected PacketStep reversed(int port) {
    if (!pattern.isLiteral()) {
      throw new IllegalArgumentException(
          "Steps using wildcards or captures can't be used in client mode");
    }
    return new SendPacketStep(getData(), 0, port);
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
    pattern.capture(window, pos).forEach((name, value) -> {
      LOG.debug("captured {} as {}", value, name);
      context.setVariableValue(name, value);
    });
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

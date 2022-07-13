package us.abstracta.wiresham;

import java.io.IOException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step in a flow which sends a packet.
 */
public class SendPacketStep extends PacketStep {

  private static final Logger LOG = LoggerFactory.getLogger(SendPacketStep.class);

  private long delayMillis;
  private int port = 0;
  public SendPacketStep() {
  }

  public SendPacketStep(String hexDump, long delayMillis) {
    super(hexDump);
    this.delayMillis = delayMillis;
  }
  public SendPacketStep(String hexDump, long delayMillis, int port) {
    super(hexDump);
    this.delayMillis = delayMillis;
    this.port = port;
  }

  //Wrapping port in order to be nullable when not set
  public Integer getPort() {
    return port == 0 ? null : port;
  }

  public void setPort(int port) {
    this.port = port;
  }
  public long getDelayMillis() {
    return delayMillis;
  }

  public void setDelayMillis(long delayMillis) {
    this.delayMillis = delayMillis;
  }

  @Override
  public void process(ConnectionFlowDriver connectionDriver)
      throws IOException, InterruptedException {
    LOG.debug("sending {} with {} millis delay", data, delayMillis);
    if (delayMillis > 0) {
      Thread.sleep(delayMillis);
    }
    connectionDriver.write(data.getBytes());
  }

  @Override
  public String toString() {
    return String.format("server: %s, delayMillis: %d, port: %d", data, delayMillis, port);
  }
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    SendPacketStep that = (SendPacketStep) o;
    return port == that.port && delayMillis == that.delayMillis;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), port, delayMillis);
  }
}

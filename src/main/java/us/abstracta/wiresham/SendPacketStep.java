package us.abstracta.wiresham;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step in a flow which sends a packet.
 */
public class SendPacketStep extends PacketStep {

  private static final Logger LOG = LoggerFactory.getLogger(SendPacketStep.class);

  private long delayMillis;

  public SendPacketStep() {
  }

  public SendPacketStep(String hexDump, long delayMillis) {
    super(hexDump);
    this.delayMillis = delayMillis;
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
    return "server: " + data;
  }

}

package us.abstracta.wiresham;

import java.io.IOException;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step in a flow which sends a packet.
 */
public class SendPacketStep extends PacketStep {

  private static final Logger LOG = LoggerFactory.getLogger(SendPacketStep.class);

  private long delayMillis;
  // no initializer since it is set by parent constructor
  private PacketTemplate template;

  public SendPacketStep() {
  }

  public SendPacketStep(String hexDump, long delayMillis) {
    super(hexDump);
    this.delayMillis = delayMillis;
  }

  public SendPacketStep(String hexDump, long delayMillis, int port) {
    super(hexDump, port);
    this.delayMillis = delayMillis;
    this.port = port;
  }

  public long getDelayMillis() {
    return delayMillis;
  }

  public void setDelayMillis(long delayMillis) {
    this.delayMillis = delayMillis;
  }

  @Override
  public String getData() {
    return template != null ? template.toString() : null;
  }

  /**
   * Sets the content of the packet to send.
   *
   * @param data hex dump of the packet, which may include <code>${name}</code> references to
   * values captured by previous steps.
   */
  @Override
  public void setData(String data) {
    this.template = PacketTemplate.parse(data);
  }

  Set<String> references() {
    return template != null ? template.getReferences() : Collections.emptySet();
  }

  @Override
  public void process(FlowConnection connectionDriver)
      throws IOException, InterruptedException {
    process(connectionDriver, new FlowContext());
  }

  @Override
  public void process(FlowConnection connectionDriver, FlowContext context)
      throws IOException, InterruptedException {
    LOG.debug("sending {} with {} millis delay", template, delayMillis);
    if (delayMillis > 0) {
      Thread.sleep(delayMillis);
    }
    connectionDriver.write(template.render(context));
  }

  @Override
  public String toString() {
    return String.format("server: %s, delayMillis: %d, port: %d", template, delayMillis, port);
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

package us.abstracta.wiresham;

import java.io.IOException;
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
  private PacketTemplate template;

  public SendPacketStep() {
  }

  public SendPacketStep(String data, long delayMillis) {
    this(data, delayMillis, 0);
  }

  public SendPacketStep(String data, long delayMillis, int port) {
    super(port);
    this.template = PacketTemplate.parse(data);
    this.delayMillis = delayMillis;
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

  @Override
  public void setData(String data) {
    this.template = PacketTemplate.parse(data);
  }

  @Override
  protected void checkVariables(Set<String> definedVariables) {
    template.getReferences().stream()
        .filter(reference -> !definedVariables.contains(reference))
        .findFirst()
        .ifPresent(reference -> {
          throw new IllegalArgumentException("uses ${" + reference
              + "}, which is not captured by any previous !client step");
        });
  }

  @Override
  protected PacketStep reversed(int port) {
    if (!template.isLiteral()) {
      throw new IllegalArgumentException(
          "Steps using captured values can't be used in client mode");
    }
    return new ReceivePacketStep(getData(), port);
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

package us.abstracta.wiresham;

import java.io.IOException;
import java.util.Objects;

/**
 * A step in a flow to be executed for a given packet.
 */
public abstract class PacketStep {

  protected Packet data;

  protected PacketStep() {}

  protected PacketStep(String data) {
    this.data = Packet.fromHexDump(data);
  }

  public String getData() {
    return data.toString();
  }

  public void setData(String data) {
    this.data = Packet.fromHexDump(data);
  }

  public abstract void process(ClientConnection clientConnection)
      throws IOException, InterruptedException;

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PacketStep that = (PacketStep) o;
    return data.equals(that.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(data);
  }

}

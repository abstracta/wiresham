package us.abstracta.wiresham;

import java.io.IOException;
import java.util.Objects;

/**
 * A step in a flow to be executed for a given packet.
 */
public abstract class PacketStep {

  //Public due to deserialization
  public int port;

  protected PacketStep() {
  }

  protected PacketStep(int port) {
    this.port = port;
  }

  /**
   * Gets the content of the packet as specified in the flow.
   *
   * @return the content of the packet, or null if it has not been set.
   */
  public abstract String getData();

  public abstract void setData(String data);

  public Integer getPort() {
    return port == 0 ? null : port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public abstract void process(FlowConnection flowConnection)
      throws IOException, InterruptedException;

  /**
   * Processes the step using the state of the execution of the flow.
   *
   * @param flowConnection connection to use for sending or receiving the packet.
   * @param context state of the execution of the flow, which includes the values captured by
   * previous steps. Steps add to it the values they capture.
   * @throws IOException when there is some problem with the connection.
   * @throws InterruptedException when the thread is interrupted while processing the step.
   */
  public void process(FlowConnection flowConnection, FlowContext context)
      throws IOException, InterruptedException {
    process(flowConnection);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PacketStep that = (PacketStep) o;
    return Objects.equals(getData(), that.getData());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getData());
  }

}

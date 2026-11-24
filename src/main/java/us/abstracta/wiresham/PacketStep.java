package us.abstracta.wiresham;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

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

  public abstract String getData();

  public abstract void setData(String data);

  public Integer getPort() {
    return port == 0 ? null : port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public abstract void process(FlowConnection flowConnection, FlowContext context)
      throws IOException, InterruptedException;

  /**
   * Checks the variables used by the step and registers the ones it defines.
   *
   * @param definedVariables names of the variables defined by previous steps of the flow.
   * @throws IllegalArgumentException when the step uses a variable which is not defined.
   */
  protected void checkVariables(Set<String> definedVariables) {
  }

  protected int getExpectedPacketLength() {
    return 0;
  }

  protected PacketStep reversed(int port) {
    throw new UnsupportedOperationException(
        getClass().getSimpleName() + " steps can't be used in client mode");
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

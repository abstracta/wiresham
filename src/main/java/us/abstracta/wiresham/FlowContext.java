package us.abstracta.wiresham;

import java.util.HashMap;
import java.util.Map;

/**
 * State of an execution of a flow, shared by all the steps and connections of the execution.
 *
 * <p>It keeps the values captured from received packets, so they can be used in packets sent by
 * following steps.
 */
public class FlowContext {

  private final Map<String, Packet> captures = new HashMap<>();

  /**
   * Keeps a value captured from a received packet, replacing any previous value with same name.
   *
   * @param name name of the capture.
   * @param value captured bytes.
   */
  public void capture(String name, Packet value) {
    captures.put(name, value);
  }

  /**
   * Gets the last value captured with a given name.
   *
   * @param name name of the capture.
   * @return the captured bytes, or null if no value has been captured with the name.
   */
  public Packet getCaptured(String name) {
    return captures.get(name);
  }

  @Override
  public String toString() {
    return captures.toString();
  }

}

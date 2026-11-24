package us.abstracta.wiresham;

import java.util.HashMap;
import java.util.Map;

/**
 * State of an execution of a flow, shared by all the steps and connections of the execution.
 */
public class FlowContext {

  private final Map<String, Packet> variables = new HashMap<>();

  public void setVariableValue(String name, Packet value) {
    variables.put(name, value);
  }

  public Packet getVariableValue(String name) {
    return variables.get(name);
  }

}

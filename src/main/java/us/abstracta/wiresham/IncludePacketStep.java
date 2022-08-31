package us.abstracta.wiresham;

import java.util.Collections;
import java.util.List;

public class IncludePacketStep implements FlowStep {

  private String id;

  // Constructor left for serialization/deserialization proposes 
  public IncludePacketStep() {
  }

  // Constructor left for serialization/deserialization proposes 
  public IncludePacketStep(String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  @Override
  public List<Integer> getPorts() {
    return Collections.emptyList();
  }
}

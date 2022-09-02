package us.abstracta.wiresham;

import java.util.Collections;
import java.util.List;

public class IncludeFlowStep implements FlowStep {

  private String id;

  // Constructor left for serialization/deserialization proposes 
  public IncludeFlowStep() {
  }

  // Constructor left for serialization/deserialization proposes 
  public IncludeFlowStep(String id) {
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

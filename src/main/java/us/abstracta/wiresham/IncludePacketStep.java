package us.abstracta.wiresham;

public class IncludePacketStep extends PacketStep {

  private String id;

  // Constructor left for serialization/deserialization proposes 
  public IncludePacketStep() {
  }

  // Constructor left for serialization/deserialization proposes 
  public IncludePacketStep(String id) {
    this.id = id;
  }

  @Override
  public void process(FlowConnection flowConnection) {

  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }
}

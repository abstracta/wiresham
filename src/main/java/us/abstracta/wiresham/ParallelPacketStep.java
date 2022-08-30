package us.abstracta.wiresham;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ParallelPacketStep extends PacketStep {

  private List<List<PacketStep>> parallelSteps;

  public ParallelPacketStep() {
    this.parallelSteps = new ArrayList<>();
  }

  public ParallelPacketStep(List<List<PacketStep>> parallelSteps) {
    this.parallelSteps = parallelSteps;
  }

  public List<List<PacketStep>> getParallelSteps() {
    return parallelSteps;
  }

  public void setParallelSteps(
      List<List<PacketStep>> parallelSteps) {
    this.parallelSteps = parallelSteps;
  }

  @Override
  public void process(FlowConnection flowConnection) throws IOException, InterruptedException {

  }

  public List<Integer> getPorts() {
    return parallelSteps.stream()
        .flatMap(Collection::stream)
        .map(PacketStep::getPort)
        .distinct()
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ParallelPacketStep that = (ParallelPacketStep) o;
    return Objects.equals(parallelSteps, that.parallelSteps);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), parallelSteps);
  }
}

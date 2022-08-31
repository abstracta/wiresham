package us.abstracta.wiresham;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ParallelPacketStep implements FlowStep {

  private List<List<FlowStep>> parallelSteps;

  public ParallelPacketStep() {
    this.parallelSteps = new ArrayList<>();
  }

  public ParallelPacketStep(List<List<FlowStep>> parallelSteps) {
    this.parallelSteps = parallelSteps;
  }

  public List<List<FlowStep>> getParallelSteps() {
    return parallelSteps;
  }

  public void setParallelSteps(
      List<List<FlowStep>> parallelSteps) {
    this.parallelSteps = parallelSteps;
  }

  public void addParallelStep(List<FlowStep> flowSteps) {
    this.parallelSteps.add(flowSteps);
  }

  @Override
  public List<Integer> getPorts() {
    return parallelSteps.stream()
        .flatMap(Collection::stream)
        .map(FlowStep::getPorts)
        .flatMap(Collection::stream)
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

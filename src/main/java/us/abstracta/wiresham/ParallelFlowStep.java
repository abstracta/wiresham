package us.abstracta.wiresham;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ParallelFlowStep implements FlowStep {

  private List<List<FlowStep>> parallelSteps;

  public ParallelFlowStep() {
    this.parallelSteps = new ArrayList<>();
  }

  public ParallelFlowStep(List<List<FlowStep>> parallelSteps) {
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
    ParallelFlowStep that = (ParallelFlowStep) o;
    return Objects.equals(parallelSteps, that.parallelSteps);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), parallelSteps);
  }

  @Override
  public String toString() {
    return "ParallelPacketStep{" +
        "parallelSteps=" + parallelSteps.stream()
        .map(ps -> String.format("[%s]",
            ps.stream()
                .map(FlowStep::toString)
                .collect(Collectors.joining(","))))
        .collect(Collectors.joining(",")) +
        '}';
  }
}

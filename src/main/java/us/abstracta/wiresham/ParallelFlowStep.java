package us.abstracta.wiresham;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ParallelFlowStep implements FlowStep {

  private List<List<FlowStep>> forks;

  public ParallelFlowStep() {
    this.forks = new ArrayList<>();
  }

  public ParallelFlowStep(List<List<FlowStep>> parallelSteps) {
    this.forks = parallelSteps;
  }

  public List<List<FlowStep>> getForks() {
    return forks;
  }

  public void setForks(
      List<List<FlowStep>> forks) {
    this.forks = forks;
  }

  public void addParallelStep(List<FlowStep> flowSteps) {
    this.forks.add(flowSteps);
  }

  @Override
  public List<Integer> getPorts() {
    return forks.stream()
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
    return Objects.equals(forks, that.forks);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), forks);
  }

  @Override
  public String toString() {
    return "ParallelPacketStep{" +
        "parallelSteps=" + forks.stream()
        .map(ps -> String.format("[%s]",
            ps.stream()
                .map(FlowStep::toString)
                .collect(Collectors.joining(","))))
        .collect(Collectors.joining(",")) +
        '}';
  }
}

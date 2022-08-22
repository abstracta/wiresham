package us.abstracta.wiresham;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ParallelPacketStep extends PacketStep {

  private List<List<PacketStep>> parallelSteps;

  public ParallelPacketStep(List<List<PacketStep>> parallelSteps) {

  }

  @Override
  public void process(FlowConnection flowConnection) throws IOException, InterruptedException {

  }

  public List<List<PacketStep>> getParallelSteps() {
    return parallelSteps;
  }

  public List<Integer> getPorts() {
    return parallelSteps.stream()
        .flatMap(Collection::stream)
        .filter(p -> p instanceof SendPacketStep)
        .map(PacketStep::getPort)
        .distinct()
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }
}

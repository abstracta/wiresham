package us.abstracta.wiresham;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

public interface FlowConnectionProvider {

  FlowConnection get(int port) throws ExecutionException, InterruptedException, IOException;

  void init(List<Integer> ports, FlowConnection flowConnection);

  default void assignFlowConnection(int port, FlowConnection flowConnection) {
  }

  default boolean requiresFlowConnection(int port) {
    return false;
  }

  void closeConnections() throws IOException;
}

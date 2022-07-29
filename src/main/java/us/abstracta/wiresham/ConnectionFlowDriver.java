package us.abstracta.wiresham;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Drives the flow of a connection according to a configured flow.
 */
public class ConnectionFlowDriver implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(ConnectionFlowDriver.class);

  private final FlowConnectionProvider connectionProvider;
  private final Queue<PacketStep> flowSteps;
  private final int portArgument;

  public ConnectionFlowDriver(FlowConnectionProvider connectionProvider,
      Flow flow, int portArgument) {
    this.portArgument = portArgument;
    this.flowSteps = new LinkedList<>(flow.getSteps());
    this.connectionProvider = connectionProvider;
  }

  @Override
  public void run() {
    try {
      PacketStep first = flowSteps.peek();
      int previousPort = (first == null || first.getPort() == null)
          ? portArgument : first.getPort();
      LOG.info("starting new flow on {}", previousPort);
      while (!flowSteps.isEmpty()) {
        PacketStep step = flowSteps.poll();
        if (step.getPort() != null && step.getPort() != previousPort) {
          LOG.info("changing to connections on port {}", step.getPort());
          previousPort = step.getPort();
        }
        FlowConnection flowConnection = connectionProvider.get(previousPort);
        step.process(flowConnection);
      }
      LOG.info("flow completed!");
    } catch (ConnectionClosedException e) {
      LOG.info("Connection closed by client while waiting for client packet");
      if (e.getDiscardedPacket().getBytes().length > 0) {
        LOG.debug("Discarding client packet {}", e.getDiscardedPacket(), e);
      }
    } catch (IOException e) {
      if (e.getMessage().contains("Socket is closed")) {
        LOG.trace("Received expected exception when server socket has been closed", e);
      } else {
        LOG.error("Problem while processing requests from client. Closing connection.", e);
      }
    } catch (InterruptedException e) {
      LOG.trace("The thread has been interrupted", e);
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      LOG.error("Problem while waiting for socket to be created", e);
    } finally {
      try {
        closeFlowConnections();
      } catch (IOException e) {
        LOG.error("Problem while releasing sockets", e);
      }
      MDC.clear();
    }
  }

  public FlowConnectionProvider getConnectionProvider() {
    return connectionProvider;
  }

  public void closeFlowConnections() throws IOException {
    connectionProvider.closeConnections();
  }
}

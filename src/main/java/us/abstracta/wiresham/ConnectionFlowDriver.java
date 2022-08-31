package us.abstracta.wiresham;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Drives the flow of a connection according to a configured flow.
 */
public class ConnectionFlowDriver implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(ConnectionFlowDriver.class);

  private final FlowConnectionProvider connectionProvider;
  private final Queue<FlowStep> flowSteps;
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
      FlowStep first = flowSteps.peek();
      int previousPort = (first == null || first.getPorts().isEmpty())
          ? portArgument : first.getPorts().get(0);
      LOG.info("starting new flow on {}", previousPort);
      processSteps(flowSteps, previousPort, connectionProvider);
      LOG.info("flow completed!");
    } catch (IOException | InterruptedException | ExecutionException e) {
      handleProcessStepExceptions(e);
    } finally {
      try {
        closeFlowConnections();
      } catch (IOException e) {
        LOG.error("Problem while releasing sockets", e);
      }
      MDC.clear();
    }
  }

  private static void processSteps(Queue<FlowStep> flowSteps, int previousPort,
      FlowConnectionProvider connectionProvider)
      throws ExecutionException, InterruptedException, IOException {
    while (!flowSteps.isEmpty()) {
      FlowStep step = flowSteps.poll();
      if (step instanceof ParallelPacketStep) {
        LOG.info("starting parallel execution");
        processParallelSteps((ParallelPacketStep) step, previousPort, connectionProvider);
        continue;
      }
      PacketStep packetStep = (PacketStep) step;
      if (!packetStep.getPorts().isEmpty() && packetStep.getPorts().get(0) != previousPort) {
        LOG.info("changing to connections on port {}", packetStep.getPorts().get(0));
        previousPort = packetStep.getPorts().get(0);
      }
      FlowConnection flowConnection = connectionProvider.get(previousPort);
      packetStep.process(flowConnection);
    }
  }

  private static void processParallelSteps(ParallelPacketStep step, int previousPort,
      FlowConnectionProvider connectionProvider) {
    List<List<FlowStep>> parallelSteps = step.getParallelSteps();
    ExecutorService parallelStepsExecutor = Executors.newFixedThreadPool(parallelSteps.size());
    List<Future<?>> parallelFutures = new ArrayList<>();
    for (List<FlowStep> parallelStep : parallelSteps) {
      Queue<FlowStep> steps = new LinkedList<>(parallelStep);
      parallelFutures.add(parallelStepsExecutor.submit(() -> {
        try {
          processSteps(steps, previousPort, connectionProvider);
        } catch (ExecutionException | InterruptedException | IOException e) {
          handleProcessStepExceptions(e);
        }
      }));
    }
    joinParallelExecutions(parallelStepsExecutor, parallelFutures);
  }

  private static void handleProcessStepExceptions(Exception e) {
    if (e.getClass().equals(IOException.class)) {
      if (e.getMessage().contains("Socket is closed")) {
        LOG.trace("Received expected exception when server socket has been closed", e);
      } else {
        LOG.error("Problem while processing requests from client. Closing connection.", e);
      }
    } else if (e.getClass().equals(InterruptedException.class)) {
      LOG.trace("The thread has been interrupted", e);
      Thread.currentThread().interrupt();
    } else if (e.getClass().equals(ExecutionException.class)) {
      LOG.error("Problem while waiting for socket to be created", e);
    } else if (e.getClass().equals(ConnectionClosedException.class)) {
      LOG.info("Connection closed by client while waiting for client packet");
      ConnectionClosedException ex = (ConnectionClosedException) e;
      if (ex.getDiscardedPacket().getBytes().length > 0) {
        LOG.debug("Discarding client packet {}", ex.getDiscardedPacket(), e);
      }
    }
  }

  private static void joinParallelExecutions(ExecutorService parallelStepsExecutor,
      List<Future<?>> parallelFutures) {
    parallelStepsExecutor.shutdown();
    parallelFutures.forEach(future -> {
      try {
        future.get();
      } catch (InterruptedException e) {
        LOG.warn("Parallel steps where interrupted", e);
      } catch (ExecutionException e) {
        LOG.trace("Unexpected exception when waiting for parallel threads to end", e);
      }
    });
  }

  public FlowConnectionProvider getConnectionProvider() {
    return connectionProvider;
  }

  public void closeFlowConnections() throws IOException {
    connectionProvider.closeConnections();
  }
}

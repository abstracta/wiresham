package us.abstracta.wiresham;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Allows to create a virtual service (a mock) from an actual service traffic dump.
 * <p>
 * This is useful for testing clients and interactions which depend on a not always available
 * environment, either due to cost, resiliency, or other potential concerns.
 */
public class VirtualTcpService {

  public static final int DEFAULT_READ_BUFFER_SIZE = 2048;
  public static final int DEFAULT_MAX_CONNECTION_COUNT = 1;
  public static final int DYNAMIC_PORT = 0;
  public static final int CLOSE_SOCKETS_TIMEOUT_MILLIS = 10000;

  private static final Logger LOG = LoggerFactory.getLogger(VirtualTcpService.class);

  private int portArgument = DYNAMIC_PORT;
  private Flow flow;
  private boolean sslEnabled;
  private SSLContext sslContext;
  private int readBufferSize = DEFAULT_READ_BUFFER_SIZE;
  private int maxConnections = DEFAULT_MAX_CONNECTION_COUNT;
  private boolean stopped = false;
  private final Set<ConnectionFlowDriver> connectionDrivers = new HashSet<>();
  private ExecutorService clientExecutorService;
  private ExecutorService portExecutorService;

  public void setPortArgument(int portArgument) {
    this.portArgument = portArgument;
  }

  public void setFlow(Flow flow) {
    this.flow = flow;
    Optional<PacketStep> bigPacketStep = flow.getSteps().stream()
        .filter(s -> s instanceof ReceivePacketStep && s.data.getBytes().length > readBufferSize)
        .findAny();
    if (bigPacketStep.isPresent()) {
      throw new IllegalArgumentException(String.format(
          "Read buffer size of %d bytes is not enough for receiving expected packet from client "
              + "with %s", readBufferSize, bigPacketStep.get().data));
    }
  }

  /**
   * @deprecated use {@link #setSslContext(SSLContext)} instead, potentially using {@link
   * SSLContext#getDefault} as parameter.
   */
  @Deprecated
  public void setSslEnabled(boolean sslEnabled) {
    this.sslEnabled = sslEnabled;
  }

  public void setSslContext(SSLContext sslContext) {
    this.sslContext = sslContext;
  }

  public void setReadBufferSize(int readBufferSize) {
    this.readBufferSize = readBufferSize;
  }

  public void setMaxConnections(int maxConnections) {
    this.maxConnections = maxConnections;
  }

  public void start() throws IOException {
    stopped = false;
    int portCount = flow.getPortCount();
    portExecutorService = Executors.newFixedThreadPool(portCount == 0 ? 1 : portCount);
    clientExecutorService = Executors.newFixedThreadPool(maxConnections);
    startServerPorts();
  }

  public void startServerPorts() throws IOException {
    for (Integer port : getPorts()) {
      ServerSocket serverSocket = buildSocket(port);
      LOG.info("Waiting for connections on {}", port);
      portExecutorService.execute(() -> {
        while (!stopped) {
          try {
            assignFlowConnectionToConnectionDriver(port,
                new FlowConnection(serverSocket.accept(), readBufferSize));
          } catch (IOException e) {
            handleSocketIOException(e);
          }
        }
      });
    }
  }

  private List<Integer> getPorts() {
    return flow.getPorts().isEmpty()
        ? Collections.singletonList(portArgument) : flow.getPorts();
  }

  private ServerSocket buildSocket(int port) throws IOException {
    if (sslContext != null) {
      return sslContext.getServerSocketFactory().createServerSocket(port);
    }
    return new ServerSocket(port);
  }

  private synchronized void assignFlowConnectionToConnectionDriver(Integer port,
      FlowConnection flowConnection) {
    Optional<FlowConnectionProvider> first = connectionDrivers.stream()
        .map(ConnectionFlowDriver::getConnectionProvider)
        .filter(f -> f.requiresFlowConnection(port))
        .findFirst();

    if (first.isPresent()) {
      first.get().assignFlowConnection(port, flowConnection);
      return;
    }
    FlowConnectionProvider connectionProvider = buildFlowConnectionProvider();
    connectionProvider.init(flow.getPorts(), flowConnection);
    addClient(new ConnectionFlowDriver(connectionProvider, flow, portArgument));
  }

  private synchronized void addClient(ConnectionFlowDriver connectionDriver) {
    if (stopped) {
      try {
        connectionDriver.closeFlowConnections();
      } catch (IOException e) {
        LOG.error("Error occurred while closing socket connections");
      }
      return;
    }
    connectionDrivers.add(connectionDriver);
    clientExecutorService.submit(() -> {
      connectionDriver.run();
      removeClient(connectionDriver);
    });
  }

  private synchronized void removeClient(ConnectionFlowDriver connectionDriver) {
    connectionDrivers.remove(connectionDriver);
  }

  private void handleSocketIOException(IOException e) {
    if (stopped) {
      LOG.trace("Received expected exception when server socket has been closed", e);
    } else {
      LOG.error("Problem waiting for client connection. Keep waiting.", e);
    }
  }

  public void stop(long timeoutMillis) throws InterruptedException {
    synchronized (this) {
      stopped = true;
      connectionDrivers.forEach(c -> {
        try {
          c.closeFlowConnections();
        } catch (IOException e) {
          LOG.error("Problem closing connection ", e);
        }
      });
    }
    clientExecutorService.shutdown();
    if (!clientExecutorService.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)) {
      clientExecutorService.shutdownNow();
    }
  }

  private FlowConnectionProvider buildFlowConnectionProvider() {
    return new FlowConnectionProvider() {
      public final Map<Integer, CompletableFuture<FlowConnection>> map = new ConcurrentHashMap<>();

      @Override
      public FlowConnection get(int port) throws ExecutionException, InterruptedException {
        return map.get(port).get();
      }

      @Override
      public void init(List<Integer> ports, FlowConnection flowConnection) {
        map.clear();
        for (Integer port : ports) {
          map.put(port, new CompletableFuture<>());
        }
        CompletableFuture<FlowConnection> completedFuture = new CompletableFuture<>();
        completedFuture.complete(flowConnection);
        map.put(flowConnection.getPort(), completedFuture);
      }

      @Override
      public void assignFlowConnection(int port, FlowConnection flowConnection) {
        map.get(port).complete(flowConnection);
      }

      @Override
      public boolean requiresFlowConnection(int port) {
        return !map.get(port).isDone();
      }

      @Override
      public void closeConnections() throws IOException {
        for (CompletableFuture<FlowConnection> value : map.values()) {
          try {
            value.cancel(false);
            value.get(CLOSE_SOCKETS_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS).close();
          } catch (InterruptedException | ExecutionException | TimeoutException e) {
            // Exceptions are not expected to be thrown since we are canceling all futures before
            // getting them. Therefore, task won't be; running, interrupted or timeout.
            throw new RuntimeException(e);
          } catch (CancellationException e) {
            LOG.error("Connection canceled since service stopped", e);
          }
        }
        map.clear();
      }
    };
  }
}

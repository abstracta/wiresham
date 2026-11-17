package us.abstracta.wiresham;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
  private volatile Flow flow;
  private boolean sslEnabled;
  private SSLContext sslContext;
  private int readBufferSize = DEFAULT_READ_BUFFER_SIZE;
  private int maxConnections = DEFAULT_MAX_CONNECTION_COUNT;
  private volatile boolean stopped = false;
  private final Set<ConnectionFlowDriver> connectionDrivers = new HashSet<>();
  private final List<ServerSocket> serverSockets = new ArrayList<>();
  private ExecutorService clientExecutorService;
  private ExecutorService portExecutorService;

  public void setPortArgument(int portArgument) {
    this.portArgument = portArgument;
  }

  public void setFlow(Flow flow) {
    this.flow = Objects.requireNonNull(flow, "flow must not be null");
  }

  /**
   * Gets the port where the service is listening for connections.
   * <p>
   * This is particularly useful when the service is started with {@link #DYNAMIC_PORT}.
   *
   * @return the first port bound by the service.
   * @throws IllegalStateException when the service has not been started.
   */
  public synchronized int getPort() {
    if (serverSockets.isEmpty()) {
      throw new IllegalStateException("The service is not started");
    }
    return serverSockets.get(0).getLocalPort();
  }

  /**
   * Gets all the ports where the service is listening for connections.
   *
   * @return the ports bound by the service, or an empty list when the service is not started.
   */
  public synchronized List<Integer> getPorts() {
    List<Integer> ports = new ArrayList<>();
    for (ServerSocket serverSocket : serverSockets) {
      ports.add(serverSocket.getLocalPort());
    }
    return ports;
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
    try {
      startServerPorts();
    } catch (IOException | RuntimeException e) {
      // avoid leaving ports bound and threads running when some port can't be bound
      try {
        stop(0);
      } catch (InterruptedException interruption) {
        Thread.currentThread().interrupt();
      }
      throw e;
    }
  }

  public void startServerPorts() throws IOException {
    for (Integer configuredPort : getConfiguredPorts()) {
      ServerSocket serverSocket = buildSocket(configuredPort);
      synchronized (this) {
        serverSockets.add(serverSocket);
      }
      int port = serverSocket.getLocalPort();
      LOG.info("Waiting for connections on {}", port);
      portExecutorService.execute(() -> {
        while (!stopped && !serverSocket.isClosed()) {
          try {
            // buffer size is calculated for each connection since the flow may be reloaded
            Socket socket = serverSocket.accept();
            assignFlowConnectionToConnectionDriver(port,
                new FlowConnection(socket, flow.getRequiredReadBufferSize(readBufferSize)));
          } catch (IOException e) {
            handleSocketIOException(e);
          }
        }
      });
    }
  }

  private List<Integer> getConfiguredPorts() {
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
    addClient(new ConnectionFlowDriver(connectionProvider, flow, port));
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
      try {
        connectionDriver.run();
      } finally {
        removeClient(connectionDriver);
      }
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
      for (ServerSocket serverSocket : serverSockets) {
        try {
          serverSocket.close();
        } catch (IOException e) {
          LOG.error("Problem closing server socket on port {}", serverSocket.getLocalPort(), e);
        }
      }
      serverSockets.clear();
      connectionDrivers.forEach(c -> {
        try {
          c.closeFlowConnections();
        } catch (IOException e) {
          LOG.error("Problem closing connection ", e);
        }
      });
      connectionDrivers.clear();
    }
    if (portExecutorService != null) {
      portExecutorService.shutdown();
    }
    if (clientExecutorService == null) {
      return;
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
      public FlowConnection get(int port)
          throws ExecutionException, InterruptedException, IOException {
        CompletableFuture<FlowConnection> connection = map.get(port);
        if (connection == null) {
          throw new IOException("Connection on port " + port + " is closed");
        }
        return connection.get();
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
        CompletableFuture<FlowConnection> connection = map.get(port);
        if (connection != null) {
          connection.complete(flowConnection);
        }
      }

      @Override
      public boolean requiresFlowConnection(int port) {
        CompletableFuture<FlowConnection> connection = map.get(port);
        return connection != null && !connection.isDone();
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
            LOG.trace("Connection canceled since service stopped", e);
          }
        }
        map.clear();
      }
    };
  }
}

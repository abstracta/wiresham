package us.abstracta.wiresham;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLServerSocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class allows to create a virtual service (a mock) from an actual service traffic dump.
 * <p>
 * This is useful for testing clients and interactions which depend on a not always available
 * environment, either due to cost, resiliency, or other potential concerns.
 */
public class VirtualTcpService implements Runnable {

  public static final int DEFAULT_READ_BUFFER_SIZE = 2048;
  public static final int DEFAULT_MAX_CONNECTION_COUNT = 1;

  private static final int DYNAMIC_PORT = 0;
  private static final Logger LOG = LoggerFactory.getLogger(VirtualTcpService.class);

  private final int port;
  private final boolean sslEnabled;
  private final int readBufferSize;
  private final int maxConnections;
  private Flow flow;
  private boolean stopped = false;
  private ArrayList<ClientConnection> clientConnections = new ArrayList<>();
  private ExecutorService serverExecutorService;
  private ExecutorService clientExecutorService;
  private ServerSocket server;

  public VirtualTcpService(int port, boolean sslEnabled, int readBufferSize, int maxConnections) {
    this.port = port;
    this.sslEnabled = sslEnabled;
    this.readBufferSize = readBufferSize;
    this.maxConnections = maxConnections;
  }

  public VirtualTcpService(boolean sslEnabled) {
    this(DYNAMIC_PORT, sslEnabled, DEFAULT_READ_BUFFER_SIZE, DEFAULT_MAX_CONNECTION_COUNT);
  }

  public VirtualTcpService(int port) {
    this(port, false, DEFAULT_READ_BUFFER_SIZE, DEFAULT_MAX_CONNECTION_COUNT);
  }

  public VirtualTcpService() {
    this(false);
  }

  public int getPort() {
    return server.getLocalPort();
  }

  public void setFlow(Flow flow) {
    this.flow = flow;
    Optional<PacketStep> bigPacketStep = flow.getSteps().stream()
        .filter(s -> s instanceof ClientPacketStep && s.data.getBytes().length > readBufferSize)
        .findAny();
    if (bigPacketStep.isPresent()) {
      throw new IllegalArgumentException(String.format(
          "Read buffer size of %d bytes is not enough for receiving expected packet from client "
              + "with %s", readBufferSize, bigPacketStep.get().data));
    }
  }

  public void start() throws IOException {
    stopped = false;
    serverExecutorService = Executors.newSingleThreadExecutor();
    clientExecutorService = Executors.newFixedThreadPool(maxConnections);
    if (sslEnabled) {
      server = SSLServerSocketFactory.getDefault().createServerSocket(port);
    } else {
      server = new ServerSocket(port);
    }
    serverExecutorService.submit(this);
  }

  @Override
  public void run() {
    LOG.debug("Starting server on {} with flow: {}", server.getLocalPort(), flow);
    LOG.info("Waiting for connections on {}", server.getLocalPort());
    while (!stopped) {
      try {
        addClient(new ClientConnection(this, server.accept(), readBufferSize, flow));
      } catch (IOException e) {
        if (stopped) {
          LOG.trace("Received expected exception when server socket has been closed", e);
        } else {
          LOG.error("Problem waiting for client connection. Keep waiting.", e);
        }
      }
    }
  }

  private synchronized void addClient(ClientConnection clientConnection) throws IOException {
    if (stopped) {
      clientConnection.close();
      return;
    }
    clientConnections.add(clientConnection);
    clientExecutorService.submit(clientConnection);
  }

  public synchronized void removeClient(ClientConnection clientConnection) {
    clientConnections.remove(clientConnection);
  }

  public void stop(long timeoutMillis) throws IOException, InterruptedException {
    synchronized (this) {
      stopped = true;
      server.close();
      clientConnections.forEach(c -> {
        try {
          c.close();
        } catch (IOException e) {
          LOG.error("Problem closing connection {}", c.getId(), e);
        }
      });
    }
    clientExecutorService.shutdown();
    clientExecutorService.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS);
    serverExecutorService.shutdownNow();
    if (!serverExecutorService.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)) {
      LOG.warn("Server thread didn't stop after {} millis", timeoutMillis);
    }
  }

}

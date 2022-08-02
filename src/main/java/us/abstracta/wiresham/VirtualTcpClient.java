package us.abstracta.wiresham;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Allows to create a virtual client (a mock) from an actual traffic dump.
 * <p>
 * This is useful for testing servers that require very specific client interaction and when is not
 * easy to get access to such clients (for re generating the traffic).
 */
public class VirtualTcpClient {

  private static final Logger LOG = LoggerFactory.getLogger(VirtualTcpClient.class);
  private static final int DEFAULT_READ_BUFFER_SIZE = 2048;

  private Flow flow;
  private String host;
  private int port;
  private int readBufferSize = DEFAULT_READ_BUFFER_SIZE;
  private ExecutorService executorService;
  private ConnectionFlowDriver connection;
  private SSLContext sslContext;

  public void setFlow(Flow flow) {
    this.flow = flow;
  }

  public void setServerAddress(String serverAddress) {
    int portSeparatorPos = serverAddress.lastIndexOf(":");
    this.host = serverAddress.substring(0, portSeparatorPos);
    this.port = Integer.parseInt(serverAddress.substring(portSeparatorPos + 1));
  }

  public void setSslContext(SSLContext sslContext) {
    this.sslContext = sslContext;
  }

  public void setReadBufferSize(int readBufferSize) {
    this.readBufferSize = readBufferSize;
  }

  private Socket buildSocket(int port)
      throws IOException {
    if (sslContext != null) {
      return sslContext.getSocketFactory().createSocket(host, port);
    } else {
      return new Socket(host, port);
    }
  }

  public void run() {
    executorService = Executors.newSingleThreadExecutor();
    connection = new ConnectionFlowDriver(buildFlowConnectionProvider(), flow, port);
    executorService.submit(connection);
  }

  private FlowConnectionProvider buildFlowConnectionProvider() {
    return new FlowConnectionProvider() {

      public final Map<Integer, FlowConnection> map = new ConcurrentHashMap<>();

      @Override
      public FlowConnection get(int port) throws IOException {
        if (map.get(port) == null) {
          map.put(port, new FlowConnection(buildSocket(port), readBufferSize));
        }
        return map.get(port);
      }

      @Override
      public void init(List<Integer> ports, FlowConnection flowConnection) {
        map.put(flowConnection.getPort(), flowConnection);
      }

      @Override
      public void closeConnections() throws IOException {
        for (FlowConnection value : map.values()) {
          value.close();
        }
      }
    };
  }

  public void stop(long timeoutMillis) throws InterruptedException, IOException {
    connection.closeFlowConnections();
    executorService.shutdown();
    executorService.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS);
    executorService.shutdownNow();
    if (!executorService.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)) {
      LOG.warn("Client didn't stop after {} millis", timeoutMillis);
    }
  }

}

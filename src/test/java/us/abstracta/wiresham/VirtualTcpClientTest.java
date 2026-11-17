package us.abstracta.wiresham;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import us.abstracta.wiresham.SimpleFlow.FlowBuilder;

public class VirtualTcpClientTest {

  private static final Logger LOG = LoggerFactory.getLogger(VirtualTcpClientTest.class);
  private static final ExecutorService SERVER_EXECUTOR = Executors.newSingleThreadExecutor();
  private static final long TIMEOUT_MILLIS = 5000;

  private final VirtualTcpClient client = new VirtualTcpClient();
  private ServerSocket serverSocket;
  private PlainTextSocket socket;

  @BeforeEach
  public void setUp() throws Exception {
    client.setFlow(SimpleFlow.getFlow().reversed());
    serverSocket = new ServerSocket(0);
    startConnection();
  }

  private void startConnection() throws Exception {
    CompletableFuture<PlainTextSocket> socketFuture = new CompletableFuture<>();
    SERVER_EXECUTOR.submit(() -> {
      try {
        socketFuture.complete(new PlainTextSocket(serverSocket.accept(), TIMEOUT_MILLIS));
      } catch (IOException e) {
        LOG.warn("Problem while attending client requests", e);
      }
    });
    client.setServerAddress("localhost:" + serverSocket.getLocalPort());
    client.run();
    socket = socketFuture.get();
  }

  @AfterEach
  public void tearDown() throws Exception {
    client.stop(TIMEOUT_MILLIS);
    serverSocket.close();
  }

  @AfterAll
  public static void setupClass() {
    SERVER_EXECUTOR.shutdownNow();
  }

  @Test
  public void shouldGetExpectedRequestWhenSendExpectedMessage() throws Exception {
    socket.send(SimpleFlow.SERVER_WELCOME_MESSAGE);
    socket.awaitReceive(SimpleFlow.CLIENT_REQUEST);
  }

  @Test
  public void shouldGetNoRequestWhenSendExpectedMessage() throws Exception {
    socket.send(SimpleFlow.UNEXPECTED_MESSAGE);
    assertThrows(TimeoutException.class, () -> socket.awaitReceiveAnything());
  }

  @Test
  public void shouldConnectToEveryPortWhenFlowWithMultiplePorts() throws Exception {
    client.stop(TIMEOUT_MILLIS);
    try (ServerSocket secondServerSocket = new ServerSocket(0)) {
      serverSocket.setSoTimeout((int) TIMEOUT_MILLIS);
      secondServerSocket.setSoTimeout((int) TIMEOUT_MILLIS);
      Flow flow = new FlowBuilder()
          .withServerPacket(SimpleFlow.SERVER_WELCOME_MESSAGE, serverSocket.getLocalPort())
          .withClientPacket(SimpleFlow.CLIENT_REQUEST)
          .withServerPacket(SimpleFlow.SERVER_RESPONSE, secondServerSocket.getLocalPort())
          .withClientPacket(SimpleFlow.CLIENT_REQUEST)
          .build();
      client.setFlow(flow.reversed());
      client.run();
      try (PlainTextSocket first = new PlainTextSocket(serverSocket.accept(), TIMEOUT_MILLIS)) {
        first.send(SimpleFlow.SERVER_WELCOME_MESSAGE);
        first.awaitReceive(SimpleFlow.CLIENT_REQUEST);
        first.send(SimpleFlow.SERVER_RESPONSE);
        try (PlainTextSocket second = new PlainTextSocket(secondServerSocket.accept(),
            TIMEOUT_MILLIS)) {
          second.send(SimpleFlow.SERVER_RESPONSE);
          second.awaitReceive(SimpleFlow.CLIENT_REQUEST);
        }
      }
    }
  }

  @Test
  public void shouldGetExpectedRequestWhenSendExpectedMessageThroughSsl() throws Exception {
    client.stop(TIMEOUT_MILLIS);
    serverSocket.close();
    SSLContext sslContext = SslContextFactory.buildSslContext();
    client.setSslContext(sslContext);
    serverSocket = sslContext.getServerSocketFactory().createServerSocket(0);
    startConnection();
    socket.send(SimpleFlow.SERVER_WELCOME_MESSAGE);
    socket.awaitReceive(SimpleFlow.CLIENT_REQUEST);
  }

}

package us.abstracta.wiresham;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import us.abstracta.wiresham.SimpleFlow.FlowBuilder;

public class VirtualTcpServiceTest {

  private static final long TIMEOUT_MILLIS = 5000;

  private final VirtualTcpService service = new VirtualTcpService();
  private PlainTextSocket mainClientSocket;
  private PlainTextSocket subordinateClientSocket;

  @BeforeEach
  public void setUp() throws Exception {
    service.setFlow(SimpleFlow.getFlow());
    int availablePort = getAvailablePort();
    service.setPortArgument(availablePort);
    service.start();
    mainClientSocket = new PlainTextSocket(new Socket("localhost", availablePort), TIMEOUT_MILLIS);
  }

  public int getAvailablePort() throws IOException {
    ServerSocket exploitedSocket = new ServerSocket(0);
    int port = exploitedSocket.getLocalPort();
    exploitedSocket.close();
    return port;
  }

  @AfterEach
  public void tearDown() throws Exception {
    mainClientSocket.close();
    service.stop(TIMEOUT_MILLIS);
  }

  @Test
  public void shouldGetExpectedResponseWhenConnect() throws Exception {
    mainClientSocket.awaitReceive(SimpleFlow.SERVER_WELCOME_MESSAGE);
  }

  @Test
  public void shouldGetExpectedResponseWhenSendExpectedInput() throws Exception {
    mainClientSocket.awaitReceive(SimpleFlow.SERVER_WELCOME_MESSAGE);
    mainClientSocket.send(SimpleFlow.CLIENT_REQUEST);
    mainClientSocket.awaitReceive(SimpleFlow.SERVER_RESPONSE);
  }

  @Test
  public void shouldGetNoResponseWhenSendUnexpectedInput() throws Exception {
    mainClientSocket.awaitReceive(SimpleFlow.SERVER_WELCOME_MESSAGE);
    mainClientSocket.send(SimpleFlow.UNEXPECTED_MESSAGE);
    assertThrows(TimeoutException.class, () -> mainClientSocket.awaitReceiveAnything());
  }

  @Test
  public void shouldGetExpectedResponseWhenSendExpectedInputAfterUnexpectedOne()
      throws Exception {
    mainClientSocket.awaitReceive(SimpleFlow.SERVER_WELCOME_MESSAGE);
    mainClientSocket.send(SimpleFlow.UNEXPECTED_MESSAGE);
    mainClientSocket.send(SimpleFlow.CLIENT_REQUEST);
    mainClientSocket.awaitReceive(SimpleFlow.SERVER_RESPONSE);
  }

  @Test
  public void shouldGetExpectedResponseWhenConnectSsl() throws Exception {
    mainClientSocket.close();
    service.stop(TIMEOUT_MILLIS);
    SSLContext sslContext = SslContextFactory.buildSslContext();
    service.setSslContext(sslContext);
    int availablePort = getAvailablePort();
    service.setPortArgument(availablePort);
    service.start();
    mainClientSocket = new PlainTextSocket(
        sslContext.getSocketFactory().createSocket("localhost", availablePort),
        TIMEOUT_MILLIS);
    mainClientSocket.awaitReceive(SimpleFlow.SERVER_WELCOME_MESSAGE);
  }

  @Test
  public void shouldGetExpectedResponseWhenValidInputsInMultiplePort() throws Exception {
    int firstAvailablePort = getAvailablePort();
    int secondAvailablePort = getAvailablePort();
    Flow flow = new FlowBuilder()
        .withServerPacket(SimpleFlow.SERVER_WELCOME_MESSAGE, firstAvailablePort)
        .withClientPacket(SimpleFlow.CLIENT_REQUEST)
        .withServerPacket(SimpleFlow.SERVER_RESPONSE)
        .withServerPacket(SimpleFlow.SERVER_WELCOME_MESSAGE, secondAvailablePort)
        .withClientPacket(SimpleFlow.CLIENT_REQUEST)
        .build();
    setupForMultiplePortService(firstAvailablePort, secondAvailablePort, flow);
    mainClientSocket.awaitReceive(SimpleFlow.SERVER_WELCOME_MESSAGE);
    mainClientSocket.send(SimpleFlow.CLIENT_REQUEST);
    mainClientSocket.awaitReceive(SimpleFlow.SERVER_RESPONSE);
    subordinateClientSocket.awaitReceive(SimpleFlow.SERVER_WELCOME_MESSAGE);
    subordinateClientSocket.send(SimpleFlow.CLIENT_REQUEST);
  }

  private void setupForMultiplePortService(int firstAvailablePort, int secondAvailablePort,
      Flow flow)
      throws IOException, InterruptedException {
    mainClientSocket.close();
    service.stop(TIMEOUT_MILLIS);
    service.setFlow(flow);
    service.start();
    mainClientSocket = new PlainTextSocket(new Socket("localhost", firstAvailablePort),
        TIMEOUT_MILLIS);
    subordinateClientSocket = new PlainTextSocket(new Socket("localhost", secondAvailablePort),
        TIMEOUT_MILLIS);
  }


  @Test
  public void shouldGetNoResponseUsingMultiplePortWhenClientMismatchedTurn() throws Exception {
    int firstAvailablePort = getAvailablePort();
    int secondAvailablePort = getAvailablePort();
    Flow flow = new FlowBuilder()
        .withServerPacket(SimpleFlow.SERVER_WELCOME_MESSAGE, firstAvailablePort)
        .withClientPacket(SimpleFlow.CLIENT_REQUEST)
        .withServerPacket(SimpleFlow.SERVER_WELCOME_MESSAGE, secondAvailablePort)
        .build();
    setupForMultiplePortService(firstAvailablePort, secondAvailablePort, flow);
    assertThrows(TimeoutException.class,
        () -> subordinateClientSocket.awaitReceive(SimpleFlow.SERVER_WELCOME_MESSAGE));
  }

  @Test
  public void shouldGetExceptedResponsesWhenUsingParallel() throws Exception {
    int firstAvailablePort = getAvailablePort();
    int secondAvailablePort = getAvailablePort();
    Flow flow = new FlowBuilder()
        .withServerPacket(SimpleFlow.SERVER_WELCOME_MESSAGE, firstAvailablePort)
        .withClientPacket(SimpleFlow.CLIENT_REQUEST)
        .withParallelPacket(buildParallelStep(firstAvailablePort, secondAvailablePort))
        .withClientPacket(SimpleFlow.CLIENT_GOODBYE)
        .build();
    setupForMultiplePortService(firstAvailablePort, secondAvailablePort, flow);
    mainClientSocket.awaitReceive(SimpleFlow.SERVER_WELCOME_MESSAGE);
    mainClientSocket.send(SimpleFlow.CLIENT_REQUEST);
    validateParallelSteps();
    subordinateClientSocket.send(SimpleFlow.CLIENT_GOODBYE);
  }

  private void validateParallelSteps() throws InterruptedException {
    ExecutorService firstParallelFlow = Executors.newSingleThreadExecutor();
    ExecutorService secondParallelFlow = Executors.newSingleThreadExecutor();
    firstParallelFlow.submit(() -> {
      try {
        mainClientSocket.awaitReceive(SimpleFlow.SERVER_RESPONSE);
        mainClientSocket.send(SimpleFlow.CLIENT_REQUEST);
        mainClientSocket.awaitReceive(SimpleFlow.SERVER_GOODBYE);
      } catch (InterruptedException | TimeoutException | IOException e) {
        throw new RuntimeException(e);
      }
    });
    secondParallelFlow.submit(() -> {
      try {
        subordinateClientSocket.awaitReceive(SimpleFlow.SERVER_WELCOME_MESSAGE);
        subordinateClientSocket.send(SimpleFlow.CLIENT_REQUEST);
      } catch (InterruptedException | TimeoutException | IOException e) {
        throw new RuntimeException(e);
      }
    });
    firstParallelFlow.shutdown();
    secondParallelFlow.shutdown();
    assertTrue(firstParallelFlow.awaitTermination(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
    assertTrue(secondParallelFlow.awaitTermination(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
  }

  private List<List<FlowStep>> buildParallelStep(int firstAvailablePort,
      int secondAvailablePort) {
    return Arrays.asList(
        new FlowBuilder()
            .withServerPacket(SimpleFlow.SERVER_RESPONSE, firstAvailablePort)
            .withClientPacket(SimpleFlow.CLIENT_REQUEST)
            .withServerPacket(SimpleFlow.SERVER_GOODBYE).build().steps,
        new FlowBuilder()
            .withServerPacket(SimpleFlow.SERVER_WELCOME_MESSAGE, secondAvailablePort)
            .withClientPacket(SimpleFlow.CLIENT_REQUEST).build().steps
    );
  }

}

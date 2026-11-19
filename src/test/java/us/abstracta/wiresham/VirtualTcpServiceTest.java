package us.abstracta.wiresham;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.io.BaseEncoding;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
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
  public void shouldReleasePortWhenStop() throws Exception {
    int port = service.getPort();
    mainClientSocket.close();
    service.stop(TIMEOUT_MILLIS);
    assertThrows(IOException.class, () -> new Socket("localhost", port).close());
    new ServerSocket(port).close();
  }

  @Test
  public void shouldGetExpectedResponseWhenRestartOnSamePort() throws Exception {
    int port = service.getPort();
    mainClientSocket.close();
    service.stop(TIMEOUT_MILLIS);
    service.start();
    mainClientSocket = new PlainTextSocket(new Socket("localhost", port), TIMEOUT_MILLIS);
    mainClientSocket.awaitReceive(SimpleFlow.SERVER_WELCOME_MESSAGE);
  }

  @Test
  public void shouldGetExpectedResponseWhenStartWithDynamicPort() throws Exception {
    mainClientSocket.close();
    service.stop(TIMEOUT_MILLIS);
    service.setPortArgument(VirtualTcpService.DYNAMIC_PORT);
    service.start();
    assertEquals(Collections.singletonList(service.getPort()), service.getPorts());
    mainClientSocket = new PlainTextSocket(new Socket("localhost", service.getPort()),
        TIMEOUT_MILLIS);
    mainClientSocket.awaitReceive(SimpleFlow.SERVER_WELCOME_MESSAGE);
    mainClientSocket.send(SimpleFlow.CLIENT_REQUEST);
    mainClientSocket.awaitReceive(SimpleFlow.SERVER_RESPONSE);
  }

  @Test
  public void shouldThrowIllegalStateWhenGetPortOfStoppedService() throws Exception {
    mainClientSocket.close();
    service.stop(TIMEOUT_MILLIS);
    assertThrows(IllegalStateException.class, service::getPort);
  }

  @Test
  public void shouldGetExpectedResponseWhenExpectedPacketIsBiggerThanReadBuffer()
      throws Exception {
    mainClientSocket.close();
    service.stop(TIMEOUT_MILLIS);
    StringBuilder bigRequest = new StringBuilder();
    for (int i = 0; i < VirtualTcpService.DEFAULT_READ_BUFFER_SIZE + 500; i++) {
      bigRequest.append((char) ('a' + i % 26));
    }
    service.setFlow(new FlowBuilder()
        .withClientPacket(bigRequest.toString())
        .withServerPacket(SimpleFlow.SERVER_RESPONSE)
        .build());
    service.start();
    mainClientSocket = new PlainTextSocket(new Socket("localhost", service.getPort()),
        TIMEOUT_MILLIS);
    mainClientSocket.send(bigRequest.toString());
    mainClientSocket.awaitReceive(SimpleFlow.SERVER_RESPONSE);
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
  public void shouldGetCapturedValueWhenSendInputMatchingPatternWithCapture() throws Exception {
    restartWithFlow(buildLoginFlow());
    mainClientSocket.send("LOGIN john!");
    mainClientSocket.awaitReceive("OK john");
  }

  private Flow buildLoginFlow() {
    return new Flow(Arrays.asList(
        new ReceivePacketStep(hex("LOGIN ") + "(?<user>.{4})."),
        new SendPacketStep(hex("OK ") + "${user}", 0)));
  }

  private static String hex(String text) {
    return BaseEncoding.base16().encode(text.getBytes(StandardCharsets.UTF_8));
  }

  private void restartWithFlow(Flow flow) throws Exception {
    mainClientSocket.close();
    service.stop(TIMEOUT_MILLIS);
    service.setFlow(flow);
    service.start();
    mainClientSocket = connect();
  }

  private PlainTextSocket connect() throws IOException {
    return new PlainTextSocket(new Socket("localhost", service.getPort()), TIMEOUT_MILLIS);
  }

  @Test
  public void shouldGetCapturedValueWhenPatternIsSplitInSeveralPacketsAfterUnexpectedInput()
      throws Exception {
    restartWithFlow(buildLoginFlow());
    mainClientSocket.send("LOGOUT LOG");
    mainClientSocket.send("IN ma");
    mainClientSocket.send("ry?");
    mainClientSocket.awaitReceive("OK mary");
  }

  @Test
  public void shouldGetValueOfEachConnectionWhenSeveralConnectionsUseSameCapture()
      throws Exception {
    restartWithFlow(buildLoginFlow());
    mainClientSocket.send("LOGIN john.");
    mainClientSocket.awaitReceive("OK john");
    try (PlainTextSocket otherClientSocket = connect()) {
      otherClientSocket.send("LOGIN anna.");
      otherClientSocket.awaitReceive("OK anna");
    }
  }

  @Test
  public void shouldGetNoResponseWhenSendInputNotMatchingPatternWithWildcards()
      throws Exception {
    restartWithFlow(buildLoginFlow());
    mainClientSocket.send("LOGOUT john!");
    assertThrows(TimeoutException.class, () -> mainClientSocket.awaitReceiveAnything());
  }

  @Test
  public void shouldGetExpectedResponseWhenExpectedInputOverlapsWithPreviousPartialMatch()
      throws Exception {
    restartWithFlow(new Flow(Arrays.asList(
        new ReceivePacketStep(hex("AAB")),
        new SendPacketStep(hex("OK"), 0),
        new ReceivePacketStep(hex("C") + "." + hex("E")),
        new SendPacketStep(hex("END"), 0))));
    mainClientSocket.send("AAABCDE");
    mainClientSocket.awaitReceive("OK");
    mainClientSocket.awaitReceive("END");
  }

}

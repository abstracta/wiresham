package us.abstracta.wiresham;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.Socket;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class VirtualTcpServiceTest {

  private static final long TIMEOUT_MILLIS = 5000;

  private final VirtualTcpService service = new VirtualTcpService();
  private PlainTextSocket clientSocket;

  @BeforeEach
  public void setUp() throws Exception {
    service.setFlow(SimpleFlow.getFlow());
    service.start();
    clientSocket = new PlainTextSocket(new Socket("localhost", service.getPort()), TIMEOUT_MILLIS);
  }

  @AfterEach
  public void tearDown() throws Exception {
    clientSocket.close();
    service.stop(TIMEOUT_MILLIS);
  }

  @Test
  public void shouldGetExpectedResponseWhenConnect() throws Exception {
    clientSocket.awaitReceive(SimpleFlow.SERVER_WELCOME_MESSAGE);
  }

  @Test
  public void shouldGetExpectedResponseWhenSendExpectedInput() throws Exception {
    clientSocket.awaitReceive(SimpleFlow.SERVER_WELCOME_MESSAGE);
    clientSocket.send(SimpleFlow.CLIENT_REQUEST);
    clientSocket.awaitReceive(SimpleFlow.SERVER_RESPONSE);
  }

  @Test
  public void shouldGetNoResponseWhenSendUnexpectedInput() throws Exception {
    clientSocket.awaitReceive(SimpleFlow.SERVER_WELCOME_MESSAGE);
    clientSocket.send(SimpleFlow.UNEXPECTED_MESSAGE);
    assertThrows(TimeoutException.class, () -> clientSocket.awaitReceiveAnything());
  }

  @Test
  public void shouldGetExpectedResponseWhenSendExpectedInputAfterUnexpectedOne()
      throws Exception {
    clientSocket.awaitReceive(SimpleFlow.SERVER_WELCOME_MESSAGE);
    clientSocket.send(SimpleFlow.UNEXPECTED_MESSAGE);
    clientSocket.send(SimpleFlow.CLIENT_REQUEST);
    clientSocket.awaitReceive(SimpleFlow.SERVER_RESPONSE);
  }

  @Test
  public void shouldGetExpectedResponseWhenConnectSsl() throws Exception {
    clientSocket.close();
    service.stop(TIMEOUT_MILLIS);
    SSLContext sslSontext = SslContextFactory.buildSslContext();
    service.setSslContext(sslSontext);
    service.start();
    clientSocket = new PlainTextSocket(
        sslSontext.getSocketFactory().createSocket("localhost", service.getPort()),
        TIMEOUT_MILLIS);
    clientSocket.awaitReceive(SimpleFlow.SERVER_WELCOME_MESSAGE);
  }

}

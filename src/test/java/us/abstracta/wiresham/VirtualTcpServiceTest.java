package us.abstracta.wiresham;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.base.Charsets;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VirtualTcpServiceTest {

  private static final Logger LOG = LoggerFactory.getLogger(VirtualTcpServiceTest.class);

  private static final long TIMEOUT_MILLIS = 10000;

  private VirtualTcpService service = new VirtualTcpService();

  private Socket clientSocket;

  @BeforeEach
  public void setUp() throws Exception {
    service.setFlow(loadFlow());
    service.start();
    clientSocket = new Socket("localhost", service.getPort());
  }

  private Flow loadFlow() throws FileNotFoundException {
    return Flow.fromYml(new File(getResourceFilePath("/simple.yaml")));
  }

  private String getResourceFilePath(String resourcePath) {
    return getClass().getResource(resourcePath).getFile();
  }

  @AfterEach
  public void tearDown() throws Exception {
    clientSocket.close();
    service.stop(TIMEOUT_MILLIS);
  }

  @Test
  public void shouldGetExpectedResponseWhenConnect() {
    waitReceived("Hello");
  }

  private void waitReceived(String message) {
    assertTimeout(Duration.ofMillis(TIMEOUT_MILLIS), () -> {
      InputStream input = clientSocket.getInputStream();
      byte[] buffer = new byte[message.length()];
      int offset = 0;
      while (!message.equals(new String(buffer, Charsets.UTF_8))) {
        offset += input.read(buffer, offset, message.length() - offset);
        LOG.debug("Received so far: {}", new String(buffer, Charsets.UTF_8));
      }
    });
  }

  @Test
  public void shouldGetExpectedResponseWhenSendExpectedInput() throws Exception {
    waitReceived("Hello");
    send("Hello, I'm John");
    waitReceived("Hello John");
  }

  private void send(String message) throws IOException {
    clientSocket.getOutputStream().write(message.getBytes(Charsets.UTF_8));
  }

  @Test
  public void shouldGetNoResponseWhenSendUnexpectedInput() throws Exception {
    waitReceived("Hello");
    send("What's up!");
    Future<Integer> receivedResponse = Executors.newSingleThreadExecutor()
        .submit(() -> clientSocket.getInputStream().read());
    assertThrows(TimeoutException.class, () -> receivedResponse.get(3, TimeUnit.SECONDS));
  }

  @Test
  public void shouldGetNoExpectedResponseWhenSendExpectedInputAfterUnexpectedOne()
      throws Exception {
    waitReceived("Hello");
    send("What's up!");
    send("Hello, I'm John");
    waitReceived("Hello John");
  }

  @Test
  public void shouldGetExpectedResponseWhenConnectSsl() throws Exception {
    clientSocket.close();
    service.stop(TIMEOUT_MILLIS);
    System.setProperty("javax.net.ssl.keyStore", getResourceFilePath("/keystore.jks"));
    System.setProperty("javax.net.ssl.keyStorePassword", "changeit");
    service.setSslEnabled(true);
    service.start();
    clientSocket = buildSslSocket("localhost", service.getPort());
    waitReceived("Hello");
  }

  @Test
  public void shouldReadWiresharkJsonAndParseServerPort() throws Exception {
    Flow flow = Flow.fromWiresharkJsonDump(new File(getResourceFilePath("/serverOnLocalPort3469.json")),
            "127.0.0.1:3469");
    assertTrue(flow.getSteps().get(0) instanceof ClientPacketStep, "First flow element should be from client");
    assertTrue(flow.getSteps().get(1) instanceof ServerPacketStep, "Second flow element should be from server");
    assertTrue(flow.getSteps().get(2) instanceof ClientPacketStep, "Third flow element should be from client");
    assertTrue(flow.getSteps().get(3) instanceof ServerPacketStep, "Fourth flow element should be from server");
  }

  @Test
  public void shouldReadWiresharkJsonAndParseServerIp() throws Exception {
    Flow flow = Flow.fromWiresharkJsonDump(new File(getResourceFilePath("/serverOnLocalPort3469.json")),
            "127.0.0.1");
    assertTrue(flow.getSteps().get(0) instanceof ServerPacketStep, "First flow element should be from client");
    assertTrue(flow.getSteps().get(1) instanceof ServerPacketStep, "Second flow element should be from server");
    assertTrue(flow.getSteps().get(2) instanceof ServerPacketStep, "Third flow element should be from client");
    assertTrue(flow.getSteps().get(3) instanceof ServerPacketStep, "Fourth flow element should be from server");
  }

  private Socket buildSslSocket(String host, int port)
      throws GeneralSecurityException, IOException {
    SSLContext sslContext = SSLContext.getInstance("TLS");
    TrustManager trustManager = new X509TrustManager() {

      public java.security.cert.X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
      }

      public void checkClientTrusted(
          java.security.cert.X509Certificate[] certs, String authType) {
      }

      public void checkServerTrusted(
          java.security.cert.X509Certificate[] certs, String authType) {
      }
    };
    sslContext.init(null, new TrustManager[]{trustManager},
        new SecureRandom());
    return sslContext.getSocketFactory().createSocket(host, port);
  }

}

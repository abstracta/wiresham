package us.abstracta.wiresham;

import com.google.common.base.Charsets;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlainTextSocket implements Closeable {

  private static final Logger LOG = LoggerFactory.getLogger(PlainTextSocket.class);
  private final Socket socket;
  private final long receiveTimeoutMillis;

  protected PlainTextSocket(Socket socket, long timeoutMillis) {
    this.socket = socket;
    receiveTimeoutMillis = timeoutMillis;
  }

  public void awaitReceiveAnything() throws InterruptedException, IOException, TimeoutException {
    awaitReceive(s -> !s.equals("\0"), 1);
  }

  public void awaitReceive(String message)
      throws InterruptedException, TimeoutException, IOException {
    awaitReceive(message::equals, message.length());
  }

  private void awaitReceive(Predicate<String> condition, int bufferSize)
      throws InterruptedException, TimeoutException, IOException {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<?> received = executor.submit(() -> {
      InputStream input = socket.getInputStream();
      byte[] buffer = new byte[bufferSize];
      int offset = 0;
      while (!condition.test(new String(buffer, Charsets.UTF_8))) {
        offset += input.read(buffer, offset, bufferSize - offset);
        LOG.debug("Received so far: {}", new String(buffer, Charsets.UTF_8));
      }
      return null;
    });
    try {
      received.get(receiveTimeoutMillis, TimeUnit.MILLISECONDS);
    } catch (ExecutionException e) {
      unwrapExecutionException(e);
    }
  }

  private void unwrapExecutionException(ExecutionException e) throws IOException {
    if (e.getCause() instanceof IOException) {
      throw (IOException) e.getCause();
    } else if (e.getCause() instanceof RuntimeException) {
      throw (RuntimeException) e.getCause();
    } else {
      throw new RuntimeException(e.getCause());
    }
  }

  public void send(String message) throws IOException {
    socket.getOutputStream().write(message.getBytes(Charsets.UTF_8));
  }

  @Override
  public void close() throws IOException {
    socket.close();
  }

}

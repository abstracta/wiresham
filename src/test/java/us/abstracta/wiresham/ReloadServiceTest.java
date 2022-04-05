package us.abstracta.wiresham;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.assertj.core.util.Files;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ReloadServiceTest {

  private static final String SERVER_PACKET = "- !server {data: C3E4D9C9D6E2C9E3E8, delayMillis: 50}";
  private static final String CLIENT_PACKET = "- !client {data: E3C5E2E36DD7C1C3D2C5E3}";
  private static final int TIMEOUT = 20000;

  @Mock
  private VirtualTcpService service;
  private ReloadService reloadService;
  private File configFile;

  @BeforeEach
  public void setup() throws IOException {
    MockitoAnnotations.initMocks(this);
    setupConfigFile();
    this.reloadService = new ReloadService(service, configFile);
  }

  private void setupConfigFile() throws IOException {
    configFile = Files.newTemporaryFile();
    writeInConfigFile(SERVER_PACKET, CLIENT_PACKET);
  }

  private void writeInConfigFile(String... lines) throws IOException {
    java.nio.file.Files.write(configFile.toPath(), Arrays.asList(lines),
        StandardCharsets.UTF_8);
  }

  @Test
  public void shouldNotifyStopTcpServiceWhenDumpModified() throws Exception {
    startReloadService();
    Await.untilCondition(() -> reloadService.isReloadServiceActive());
    writeInConfigFile(SERVER_PACKET);
    verify(service, timeout(TIMEOUT)).stop(anyLong());
  }

  @Test
  public void shouldNotifyStartTcpServiceWhenDumpModified() throws Exception {
    startReloadService();
    Await.untilCondition(() -> reloadService.isReloadServiceActive());
    writeInConfigFile(SERVER_PACKET);
    verify(service, timeout(TIMEOUT)).stop(anyLong());
  }

  private void startReloadService() {
    reloadService.startIfRequested(true);
  }

  public static class Await {

    private static final int SLEEP_INTERVAL = 10;
    private static final long DEFAULT_TIMEOUT = TIMEOUT;
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();

    public static void untilCondition(Supplier<Boolean> condition)
        throws ExecutionException, InterruptedException, TimeoutException {
      Callable<Supplier<Boolean>> callable = () -> {
        while (!condition.get()) {
          Thread.sleep(SLEEP_INTERVAL);
        }
        return condition;
      };
      performAwait(callable);
    }

    private static void performAwait(Callable<Supplier<Boolean>> task)
        throws ExecutionException, InterruptedException, TimeoutException {
      Future<Supplier<Boolean>> futureResult = EXECUTOR_SERVICE.submit(task);
      try {
        futureResult.get(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS);
      } catch (TimeoutException ex) {
        futureResult.cancel(true);
        throw new TimeoutException("Time elapsed and condition was not met");
      }
    }
  }
}

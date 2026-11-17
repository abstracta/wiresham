package us.abstracta.wiresham;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReloadService implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(ReloadService.class);
  private final VirtualTcpService service;
  private final ExecutorService reloadExecutorService =
      Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
          .setNameFormat("Auto-Reload-Service-%d").build());
  private final File configFile;
  private final Supplier<Flow> loadFlowProvider;

  public ReloadService(VirtualTcpService service, File configFile,
      Supplier<Flow> loadFlowProvider) {
    this.service = service;
    this.configFile = configFile;
    this.loadFlowProvider = loadFlowProvider;
  }

  @Override
  public void run() {
    try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
      registerWatchService(watchService);
      while (true) {
        WatchKey take = processTake(watchService);
        for (WatchEvent<?> pollEvent : take.pollEvents()) {
          processEvent(pollEvent);
        }
        take.reset();
      }
    } catch (IOException e) {
      LOG.error("Error while retrieving watch service", e);
    } catch (InterruptedException e) {
      LOG.trace("Reload service stopped", e);
      Thread.currentThread().interrupt();
    }
  }

  private void registerWatchService(WatchService watchService) {
    Path watchServicePath = configFile.toPath().toAbsolutePath().getParent();
    try {
      watchServicePath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
    } catch (IOException e) {
      LOG.error("Error while registering watch service to file {}",
          configFile.getAbsolutePath(), e);
    }
  }

  private WatchKey processTake(WatchService watchService) throws InterruptedException {
    WatchKey take = watchService.take();
    if (take != null && !take.isValid()) {
      LOG.error("File {} deleted or corrupted, check file integrity",
          configFile.getAbsolutePath());
    }
    return take;
  }

  private void processEvent(WatchEvent<?> pollEvent) {
    if (pollEvent.kind().equals(StandardWatchEventKinds.ENTRY_MODIFY)
        && pollEvent.context() instanceof Path) {

      // the event context is the path of the modified file relative to the watched directory
      Path modified = (Path) pollEvent.context();
      if (modified.getFileName().toString().equals(configFile.getName())) {
        reloadFlow();
      }
    }
  }

  private void reloadFlow() {
    try {
      Flow flow = loadFlowProvider.get();
      if (flow == null) {
        LOG.error("Could not reload {}. Keeping previous flow.", configFile.getAbsolutePath());
        return;
      }
      service.setFlow(flow);
      LOG.info("File was modified, new connections will now use last changes");
    } catch (RuntimeException e) {
      LOG.error("Could not reload {}. Keeping previous flow.", configFile.getAbsolutePath(), e);
    }
  }

  public void start() {
    reloadExecutorService.submit(this);
  }

  public void stop() throws InterruptedException {
    if (reloadExecutorService.isShutdown() && reloadExecutorService.isTerminated()) {
      return;
    }
    reloadExecutorService.shutdownNow();
    reloadExecutorService.awaitTermination(VirtualTcpServiceMain.STOP_TIMEOUT_MILLIS,
        TimeUnit.MILLISECONDS);
  }

}

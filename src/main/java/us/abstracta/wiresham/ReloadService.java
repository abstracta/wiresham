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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReloadService implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(ReloadService.class);
  private final VirtualTcpService service;
  private final ExecutorService reloadExecutorService =
      Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
          .setNameFormat("Auto-Reload-Service-%d").build());
  private final File configFile;
  private final String serverAddress;
  private final String pcapFilters;

  public ReloadService(VirtualTcpService service, File configFile, String serverAddress,
      String pcapFilters) {
    this.service = service;
    this.configFile = configFile;
    this.serverAddress = serverAddress;
    this.pcapFilters = pcapFilters;
  }

  @Override
  public void run() {
    WatchService watchService = getWatchService();
    registerWatchService(watchService);
    while (true) {
      WatchKey take = processTake(watchService);
      for (WatchEvent<?> pollEvent : take.pollEvents()) {
        processEvent(pollEvent);
      }
      take.reset();
    }
  }

  private WatchService getWatchService() {
    WatchService watchService = null;
    try {
      watchService = FileSystems.getDefault().newWatchService();
    } catch (IOException e) {
      LOG.error("Error while retrieving watch service", e);
    }
    return watchService;
  }

  private void registerWatchService(WatchService watchService) {
    Path watchServicePath = configFile.toPath().toAbsolutePath().getParent();
    try {
      watchServicePath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
    } catch (IOException e) {
      LOG.error("Error while registering watch service to file {}", configFile.getAbsolutePath(),
          e);
    }
  }

  private WatchKey processTake(WatchService watchService) {
    WatchKey take = null;
    try {
      take = watchService.take();
    } catch (InterruptedException e) {
      LOG.error("Error while waiting for WatchService event key", e);
    }
    if (take != null && !take.isValid()) {
      LOG.error("File {} deleted or corrupted, check file integrity", configFile.getAbsolutePath());
    }
    return take;
  }

  private void processEvent(WatchEvent<?> pollEvent) {
    if (pollEvent.kind().equals(StandardWatchEventKinds.ENTRY_MODIFY)
        && pollEvent.context() instanceof Path) {

      Path p = (Path) pollEvent.context();
      if (configFile.toPath().equals(p)) {
        LOG.info("File was modified, new connections will now use last changes");
        try {
          service.setFlow(loadFlow(p.toFile(), serverAddress, pcapFilters));
        } catch (IOException e) {
          LOG.error("Error when loading modified file, check integrity", e);
        }

      }
    }
  }

  private Flow loadFlow(File configFile, String serverAddress, String pcapFilter)
      throws IOException {
    if (serverAddress == null) {
      return Flow.fromYml(configFile);
    }
    if (configFile.getName().toLowerCase().endsWith(".json")) {
      return Flow.fromWiresharkJsonDump(configFile, serverAddress);
    } else {
      return Flow.fromPcap(configFile, serverAddress, pcapFilter);
    }
  }

  public void start() {
    reloadExecutorService.submit(this);
  }

  public void stop() throws InterruptedException {
    if (reloadExecutorService.isShutdown() && reloadExecutorService.isTerminated()) {
      return;
    }
    reloadExecutorService.shutdown();
    reloadExecutorService.awaitTermination(VirtualTcpServiceMain.STOP_TIMEOUT_MILLIS,
        TimeUnit.MILLISECONDS);
  }

}

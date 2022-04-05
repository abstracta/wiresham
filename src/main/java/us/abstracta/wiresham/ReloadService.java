package us.abstracta.wiresham;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReloadService implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(ReloadService.class);
  private final VirtualTcpService service;
  private final ExecutorService reloadExecutorService =
      Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
          .setNameFormat("Auto-Reload-Service-%d").build());
  private final File configFile;
  private boolean registered;


  public ReloadService(VirtualTcpService service, File configFile) {
    this.service = service;
    this.configFile = configFile;
  }

  @Override
  public void run() {
    Path watchServicePath = getAbsolutFlowPath();
    WatchService watchService;
    try {
      watchService = FileSystems.getDefault().newWatchService();
      watchServicePath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
    } catch (IOException e) {
      LOG.error("Error while initializing Reload Service", e);
      return;
    }
    registered = true;
    while (true) {
      try {
        WatchKey take = watchService.take();

        if (!take.isValid()) {
          LOG.error("File {} deleted or corrupted, check file integrity",
              Paths.get(watchServicePath.toString(), configFile.getName()));
        }
        for (WatchEvent<?> pollEvent : take.pollEvents()) {
          if (pollEvent.kind().equals(StandardWatchEventKinds.ENTRY_MODIFY)
              && pollEvent.context() instanceof Path) {

            Path p = (Path) pollEvent.context();
            if (Paths.get(watchServicePath.toString(), configFile.getName())
                .equals(Paths.get(watchServicePath.toString(), p.toString()))) {
              LOG.info("Opened file was modified, flow restarted with new changes");
              service.stop(10000);
              service.start();
            }
          }
        }
        take.reset();
      } catch (InterruptedException e) {
        LOG.error("Error while waiting for WatchService event", e);
      } catch (IOException e) {
        LOG.error("Error when restarting service using Auto Reload");
      }
    }
  }

  public void startIfRequested(boolean requested) {
    if (!requested) {
      return;
    }
    reloadExecutorService.submit(this);
  }

  public void stop() {
    if (reloadExecutorService.isShutdown() && reloadExecutorService.isTerminated()) {
      return;
    }
    reloadExecutorService.shutdownNow();
    registered = false;
  }

  public Path getAbsolutFlowPath() {
    return Paths.get(configFile.getPath()).toAbsolutePath().getParent();
  }

  @VisibleForTesting
  public boolean isReloadServiceActive() {
    return registered;
  }
}

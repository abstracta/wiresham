package us.abstracta.wiresham;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.assertj.core.util.Files;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ReloadServiceTest {

  private static final int TIMEOUT_MILLIS = 20000;

  @Mock
  private VirtualTcpService service;
  private ReloadService reloadService;
  private FileMock configFile;
  private CountDownLatch registerWatchServiceLock;

  @BeforeEach
  public void setup() throws IOException {
    MockitoAnnotations.initMocks(this);
    registerWatchServiceLock = new CountDownLatch(1);
    setupConfigFile();
    this.reloadService = new ReloadService(service, configFile, null, null);
  }

  private void setupConfigFile() throws IOException {
    File file = Files.newTemporaryFile();
    configFile = new FileMock(file.getName(),
        registerWatchServiceLock);
    writeInConfigFile(Collections.singletonList("TEST"));
  }

  private void writeInConfigFile(List<String> lines) throws IOException {
    java.nio.file.Files.write(configFile.originalPath(), lines,
        StandardCharsets.UTF_8);
  }

  @Test
  public void shouldUpdateServiceFlowWhenFlowFileModified() throws Exception {
    reloadService.start();
    boolean await = registerWatchServiceLock.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    assertThat(await).isTrue();
    writeInConfigFile(getAllSimpleFlowLines());
    verify(service, timeout(TIMEOUT_MILLIS)).setFlow(SimpleFlow.getFlow());
  }

  private List<String> getAllSimpleFlowLines() throws FileNotFoundException {
    return SimpleFlow.getFlow().getSteps().stream()
        .map(p -> "- !" + (p instanceof ReceivePacketStep ? "client" : "server") + " {data: "
            + p.data + "}")
        .collect(Collectors.toList());
  }

  private static class FileMock extends File {

    private final CountDownLatch lock;

    public FileMock(String pathname, CountDownLatch lock) {
      super(pathname);
      this.lock = lock;
    }

    @Override
    public PathMock toPath() {
      return new PathMock(super.toPath(), lock);
    }

    public Path originalPath() {
      return super.toPath();
    }
  }

  /*
  Wrap class for Path in order to get notified when watch service is actually registered.
  Since the registration of the watch service is sync, we need to make sure that at the moment of
   modifying a file, this one is already registered in watch service. 
   */
  private static class PathMock implements Path {

    private final Path path;
    private final CountDownLatch lock;

    public PathMock(Path path, CountDownLatch lock) {
      this.path = path;
      this.lock = lock;
    }

    @Override
    public FileSystem getFileSystem() {
      return path.getFileSystem();
    }

    @Override
    public boolean isAbsolute() {
      return path.isAbsolute();
    }

    @Override
    public Path getRoot() {
      return path.getRoot();
    }

    @Override
    public Path getFileName() {
      return path.getFileName();
    }

    @Override
    public Path getParent() {
      return new PathMock(path.getParent(), lock);
    }

    @Override
    public int getNameCount() {
      return path.getNameCount();
    }

    @Override
    public Path getName(int index) {
      return path.getName(index);
    }

    @Override
    public Path subpath(int beginIndex, int endIndex) {
      return path.subpath(beginIndex, endIndex);
    }

    @Override
    public boolean startsWith(Path other) {
      return path.startsWith(other);
    }

    @Override
    public boolean endsWith(Path other) {
      return path.endsWith(other);
    }

    @Override
    public Path normalize() {
      return path.normalize();
    }

    @Override
    public Path resolve(Path other) {
      return path.resolve(other);
    }

    @Override
    public Path relativize(Path other) {
      return path.relativize(other);
    }

    @Override
    public URI toUri() {
      return path.toUri();
    }

    @Override
    public Path toAbsolutePath() {
      return new PathMock(path.toAbsolutePath(), lock);
    }

    @Override
    public Path toRealPath(LinkOption... options) throws IOException {
      return path.toRealPath(options);
    }

    @Override
    public WatchKey register(WatchService watcher, Kind<?>[] events, Modifier... modifiers)
        throws IOException {
      return path.register(watcher, events, modifiers);
    }

    @Override
    public int compareTo(Path other) {
      return path.compareTo(other);
    }

    @Override
    public Iterator<Path> iterator() {
      return path.iterator();
    }

    @Override
    public boolean startsWith(String other) {
      return path.startsWith(other);
    }

    @Override
    public boolean endsWith(String other) {
      return path.endsWith(other);
    }

    @Override
    public Path resolve(String other) {
      return path.resolve(other);
    }

    @Override
    public Path resolveSibling(Path other) {
      return path.resolveSibling(other);
    }

    @Override
    public Path resolveSibling(String other) {
      return path.resolveSibling(other);
    }

    @Override
    public File toFile() {
      return path.toFile();
    }

    @Override
    public WatchKey register(WatchService watcher, Kind<?>... events) throws IOException {
      WatchKey key = path.register(watcher, events);
      lock.countDown();
      return key;
    }

    @Override
    public boolean equals(Object obj) {
      return path.equals(obj);
    }
  }

}

package us.abstracta.wiresham;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.base.Throwables;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.LoggerFactory;

/**
 * Main class for standalone command line handling for {@link VirtualTcpService} and conversion of
 * Wireshark JSON dump files.
 *
 * @see VirtualTcpService
 */
public class VirtualTcpServiceMain {

  public static final long STOP_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(10);

  @Option(name = "-p", aliases = "--port", metaVar = "port",
      usage = "Port to receive connections to the virtual service")
  private int port;

  @Option(name = "-t", aliases = "--target-server-address", metaVar = "targetAddress",
      usage = "Address where to send packets when acting as virtual client")
  private String targetAddress;

  @Option(name = "-b", aliases = "--read-buffer-size-bytes", metaVar = "bytes count", usage =
      "Size (in bytes) of buffer used to receive packets from client. Default value: "
          + VirtualTcpService.DEFAULT_READ_BUFFER_SIZE)
  private int readBufferSize = VirtualTcpService.DEFAULT_READ_BUFFER_SIZE;

  @Option(name = "-c", aliases = "--max-concurrent-connections", metaVar = "connection count",
      usage = "Maximum number of concurrent client connections to attend. Default value: "
          + VirtualTcpService.DEFAULT_MAX_CONNECTION_COUNT)
  private int maxConnectionCount = VirtualTcpService.DEFAULT_MAX_CONNECTION_COUNT;

  @Option(name = "-s", aliases = "--ssl-enabled",
      usage = "Specifies if the server should start with SSL protocol support. When this "
          + "option is specified. Use standard JSSE properties like javax.net.ssl.keyStore and "
          + "javax.net.ssl.keyStorePassword to tune the configuration.")
  private boolean sslEnabled;

  @Option(name = "-a", aliases = "--server-address", metaVar = "ip:port",
      usage = "When using a Wireshark generated JSON dump or PCAP file, this parameter specifies "
          + "the IP address (and optionally the port, when server and port are in same ip) which "
          + "identifies the service to be virtualized")
  private String serverAddress;

  @Option(name = "-f", aliases = "--pcap-filter-expression", metaVar = "expression",
      usage = "Expression used to filter packets from a PCAP file. Eg: 'port 23'")
  private String pcapFilter;

  @Option(name = "-d", aliases = "--dump-file", metaVar = ".yml file",
      usage = "File path to dump loaded flow config. The virtual service will not be started when "
          + "this option is specified. This option makes sense when a Wireshark JSON file is used "
          + "for config to dump a simplified and smaller file and then manually tune it if needed")
  private File dumpFile;

  @Option(name = "-v", aliases = "--verbose", usage = "Logs debug messages")
  private boolean verbose;

  @Option(name = "-vv", aliases = "--super-verbose", usage = "Logs trace messages")
  private boolean superVerbose;

  @Option(name = "-h", aliases = "--help", usage = "Show usage information", help = true)
  private boolean displayHelp;

  @Option(name = "-n", aliases = "--no-auto-reload", usage =
      "Disables auto reload. By default the config file is watched for changes, and when it "
          + "changes all new connections use the new version of the file. Established "
          + "connections keep using the previous version and don't suffer any interruption.")
  private boolean autoReloadDisabled;

  @Option(name = "-r", aliases = "--auto-reload", usage =
      "Deprecated: auto reload is enabled by default and this option has no effect. Use "
          + "--no-auto-reload to disable it.")
  private boolean autoReload;

  @Argument(metaVar = "config file", required = true,
      usage = "Configuration file from where to read packets information")
  private File configFile;

  private Flow loadFlow() throws IOException {
    if (!configFile.isFile()) {
      throw new FileNotFoundException("File " + configFile + " does not exist");
    }
    if (serverAddress == null) {
      return Flow.fromYml(configFile);
    } else if (configFile.getName().toLowerCase().endsWith(".json")) {
      return Flow.fromWiresharkJsonDump(configFile, serverAddress);
    } else {
      return Flow.fromPcap(configFile, serverAddress, pcapFilter);
    }
  }

  private Flow reloadFlow() {
    try {
      return loadFlow();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    VirtualTcpServiceMain main = new VirtualTcpServiceMain();
    CmdLineParser parser = new CmdLineParser(main);
    try {
      parser.parseArgument(args);
      if (main.isDisplayHelp()) {
        printHelp(parser, System.out);
      } else {
        main.run();
      }
    } catch (CmdLineException e) {
      System.err.println(e.getMessage());
      printHelp(parser, System.err);
      System.exit(1);
    } catch (FlowLoadException e) {
      System.err.println(e.getMessage());
      System.exit(1);
    }
  }

  private boolean isDisplayHelp() {
    return displayHelp;
  }

  private static void printHelp(CmdLineParser parser, PrintStream printStream) {
    String command = "java -jar wiresham-standalone.jar";
    printStream.println(command + " [options...] <config file>");
    parser.printUsage(printStream);
    printStream.println();
    printStream.println("  Examples: \n"
        + command + " -p 2324 login-invalid-creds.yml\n"
        + command + " -p 2324 -a 0.0.0.0 login-invalid-creds-wireshark.json\n"
        + command + " -p 2324 -a 0.0.0.0 login-invalid-creds.pcap\n"
        + command + " -p 2324 -a 0.0.0.0 -f \"port 23\" login-invalid-creds.pcap\n"
        + command + " -d login-invalid-creds.yml -a 0.0.0.0 login-invalid-creds-wireshark.json\n"
        + command + " -t 127.0.0.1:2324 login-invalid-creds.yml");
  }

  private static class FlowLoadException extends RuntimeException {

    private FlowLoadException(String message, Throwable cause) {
      super(message, cause);
    }

  }

  private void run() throws IOException, InterruptedException {
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    root.setLevel(superVerbose ? Level.TRACE : verbose ? Level.DEBUG : Level.INFO);
    boolean clientMode = dumpFile == null && targetAddress != null;
    Flow flow;
    try {
      flow = clientMode ? loadFlow().reversed() : loadFlow();
    } catch (IOException | RuntimeException e) {
      // the root cause is included since it is the one which details problems in steps data
      Throwable rootCause = Throwables.getRootCause(e);
      throw new FlowLoadException("Could not load flow from " + configFile + ": " + e.getMessage()
          + (rootCause == e ? "" : "\nCaused by: " + rootCause.getMessage()), e);
    }
    if (autoReload) {
      System.err.println("Option --auto-reload is deprecated and has no effect: auto reload is "
          + "enabled by default. Use --no-auto-reload to disable it.");
    }
    if (dumpFile != null) {
      flow.saveYml(dumpFile);
    } else {
      if (clientMode) {
        runVirtualClient(flow);
      } else {
        runVirtualService(flow);
      }
    }
  }

  private void runVirtualClient(Flow flow) {
    VirtualTcpClient client = new VirtualTcpClient();
    client.setServerAddress(targetAddress);
    client.setReadBufferSize(readBufferSize);
    if (sslEnabled) {
      try {
        client.setSslContext(SSLContext.getDefault());
      } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException(e);
      }
    }
    client.setFlow(flow);
    client.run();
  }

  private void runVirtualService(Flow flow) throws IOException, InterruptedException {
    VirtualTcpService service = new VirtualTcpService();
    service.setPortArgument(port);
    if (sslEnabled) {
      try {
        service.setSslContext(SSLContext.getDefault());
      } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException(e);
      }
    }
    service.setReadBufferSize(readBufferSize);
    service.setMaxConnections(maxConnectionCount);
    service.setFlow(flow);
    ReloadService reloadService = null;
    if (!autoReloadDisabled) {
      reloadService = new ReloadService(service, configFile, this::reloadFlow);
      reloadService.start();
    }
    service.start();
    try {
      while (true) {
        synchronized (this) {
          this.wait();
        }
      }
    } catch (InterruptedException e) {
      service.stop(STOP_TIMEOUT_MILLIS);
      if (reloadService != null) {
        reloadService.stop();
      }
      Thread.currentThread().interrupt();
    }
  }

}

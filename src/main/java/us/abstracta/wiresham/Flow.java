package us.abstracta.wiresham;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import com.google.common.net.InetAddresses;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.pcap4j.core.BpfProgram.BpfCompileMode;
import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.Pcaps;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.IpV6Packet;
import org.pcap4j.packet.TcpPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

/**
 * A series of steps which drive the communication with a client.
 */
public class Flow {

  private static final Logger LOG = LoggerFactory.getLogger(Flow.class);

  private static final Map<String, Class<?>> YAML_TAGS = ImmutableMap.<String, Class<?>>builder()
      .put("!server", SendPacketStep.class)
      .put("!client", ReceivePacketStep.class)
      .build();

  private static final JsonPointer WIRESHARK_LAYERS_PATH = JsonPointer.valueOf("/_source/layers");
  private static final JsonPointer WIRESHARK_TCP_PAYLOAD_PATH = JsonPointer
      .valueOf("/tcp/tcp.payload");
  private static final JsonPointer WIRESHARK_SOURCE_IP_PATH = JsonPointer.valueOf("/ip/ip.src");
  private static final JsonPointer WIRESHARK_SOURCE_IPV6_PATH = JsonPointer
      .valueOf("/ipv6/ipv6.src");
  private static final JsonPointer WIRESHARK_SOURCE_PORT_PATH = JsonPointer
      .valueOf("/tcp/tcp.srcport");
  private static final JsonPointer WIRESHARK_DESTINE_PORT_PATH = JsonPointer
      .valueOf("/tcp/tcp.dstport");
  private static final JsonPointer WIRESHARK_TIME_DELTA_PATH = JsonPointer
      .valueOf("/frame/frame.time_delta_displayed");
  private static final String IP_PORT_SEPARATOR = ":";

  private final List<PacketStep> steps;

  @VisibleForTesting
  public Flow(List<PacketStep> steps) {
    checkVariables(steps);
    this.steps = steps;
  }

  private static void checkVariables(List<PacketStep> steps) {
    Set<String> definedVariables = new HashSet<>();
    for (int i = 0; i < steps.size(); i++) {
      try {
        steps.get(i).checkVariables(definedVariables);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Flow step number " + (i + 1) + " " + e.getMessage());
      }
    }
  }

  public List<PacketStep> getSteps() {
    return steps;
  }

  @Override
  public String toString() {
    return steps.toString();
  }

  public static Flow fromWiresharkJsonDump(File file, String serverAddress)
      throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode json = mapper.readTree(file);
    List<PacketStep> steps = new ArrayList<>();
    Set<String> sourceAddresses = new LinkedHashSet<>();
    for (JsonNode packet : json) {
      JsonNode layers = packet.at(WIRESHARK_LAYERS_PATH);
      String payload = layers.at(WIRESHARK_TCP_PAYLOAD_PATH).asText();
      if (payload.isEmpty()) {
        continue;
      }
      String sourceIp = layers.at(WIRESHARK_SOURCE_IP_PATH).asText();
      if (sourceIp.isEmpty()) {
        sourceIp = layers.at(WIRESHARK_SOURCE_IPV6_PATH).asText();
      }
      String sourcePort = layers.at(WIRESHARK_SOURCE_PORT_PATH).asText();
      sourceAddresses.add(formatAddress(sourceIp, sourcePort));
      String hexDump = payload.replace(":", "");
      if (isServerAddress(sourceIp, sourcePort, serverAddress)) {
        long timeDeltaMillis = parseSecondsAsMillis(
            layers.at(WIRESHARK_TIME_DELTA_PATH).asText());
        steps.add(new SendPacketStep(hexDump, timeDeltaMillis, Integer.parseInt(sourcePort)));
      } else {
        steps.add(new ReceivePacketStep(hexDump,
            Integer.parseInt(layers.at(WIRESHARK_DESTINE_PORT_PATH).asText())));
      }
    }
    warnIfNoServerSteps(steps, file, serverAddress, sourceAddresses);
    return new Flow(steps);
  }

  private static String formatAddress(String ip, String port) {
    return (ip.contains(IP_PORT_SEPARATOR) ? "[" + ip + "]" : ip) + IP_PORT_SEPARATOR + port;
  }

  private static long parseSecondsAsMillis(String seconds) {
    return seconds.isEmpty() ? 0 : new BigDecimal(seconds).movePointRight(3).longValue();
  }

  /*
   Supported formats for server address are: ip, ipv4:port and [ipv6]:port.
   */
  private static boolean isServerAddress(String sourceIp, String sourcePort, String serverAddress) {
    String serverIp = serverAddress;
    String serverPort = null;
    int portSeparatorPos = serverAddress.lastIndexOf(IP_PORT_SEPARATOR);
    if (serverAddress.startsWith("[") && serverAddress.contains("]")) {
      int ipEndPos = serverAddress.indexOf(']');
      serverIp = serverAddress.substring(1, ipEndPos);
      serverPort = portSeparatorPos > ipEndPos ? serverAddress.substring(portSeparatorPos + 1)
          : null;
    } else if (portSeparatorPos >= 0
        && portSeparatorPos == serverAddress.indexOf(IP_PORT_SEPARATOR)) {
      serverIp = serverAddress.substring(0, portSeparatorPos);
      serverPort = serverAddress.substring(portSeparatorPos + 1);
    }
    return isSameIp(serverIp, sourceIp) && (serverPort == null || serverPort.equals(sourcePort));
  }

  private static boolean isSameIp(String ip, String otherIp) {
    if (ip.equals(otherIp)) {
      return true;
    }
    // the same IPv6 address may be written in different ways (eg: ::1 and 0:0:0:0:0:0:0:1)
    return InetAddresses.isInetAddress(ip) && InetAddresses.isInetAddress(otherIp)
        && InetAddresses.forString(ip).equals(InetAddresses.forString(otherIp));
  }

  private static void warnIfNoServerSteps(List<PacketStep> steps, File file, String serverAddress,
      Set<String> sourceAddresses) {
    if (steps.stream().noneMatch(s -> s instanceof SendPacketStep)) {
      LOG.warn("No packet in {} is sent from server address {}, so every packet is considered "
              + "as sent by the client. Packets with data in the file are sent from: {}",
          file, serverAddress, sourceAddresses);
    }
  }

  public static Flow fromPcap(File file, String serverAddress, String filter) throws IOException {
    List<PacketStep> steps = new ArrayList<>();
    Set<String> sourceAddresses = new LinkedHashSet<>();
    try (PcapHandle pcap = Pcaps.openOffline(file.getAbsolutePath())) {
      if (filter != null) {
        pcap.setFilter(filter, BpfCompileMode.OPTIMIZE);
      }
      long lastTimeMillis = 0;
      // we can't use getNextPacket and do a while != null due to https://github.com/kaitoy/pcap4j/issues/13
      // so we use getNextPacketEx which throws an EOFException when reached end of file
      while (true) {
        org.pcap4j.packet.Packet p = pcap.getNextPacketEx();
        TcpPacket tcpPacket = p.get(TcpPacket.class);
        if (tcpPacket == null) {
          continue;
        }
        // pcap4j only builds TCP packets on top of IP ones, so there is always a source IP
        String sourceIp = getSourceIp(p);
        org.pcap4j.packet.Packet payload = tcpPacket.getPayload();
        if (payload == null) {
          continue;
        }
        int sourcePort = tcpPacket.getHeader().getSrcPort().valueAsInt();
        sourceAddresses.add(formatAddress(sourceIp, String.valueOf(sourcePort)));
        String hexDump = BaseEncoding.base16().encode(payload.getRawData());
        long timeMillis = pcap.getTimestamp().getTime();
        long timeDeltaMillis = lastTimeMillis > 0 ? timeMillis - lastTimeMillis : 0;
        lastTimeMillis = timeMillis;
        steps.add(isServerAddress(sourceIp, String.valueOf(sourcePort), serverAddress)
            ? new SendPacketStep(hexDump, timeDeltaMillis, sourcePort)
            : new ReceivePacketStep(hexDump, tcpPacket.getHeader().getDstPort().valueAsInt()));
      }
    } catch (EOFException e) {
      //just ignore if we reached end of file.
    } catch (TimeoutException | PcapNativeException | NotOpenException e) {
      throw new IOException("Problem reading file " + file, e);
    }
    warnIfNoServerSteps(steps, file, serverAddress, sourceAddresses);
    return new Flow(steps);
  }

  private static String getSourceIp(org.pcap4j.packet.Packet packet) {
    IpV4Packet ipV4Packet = packet.get(IpV4Packet.class);
    if (ipV4Packet != null) {
      return ipV4Packet.getHeader().getSrcAddr().getHostAddress();
    }
    IpV6Packet ipV6Packet = packet.get(IpV6Packet.class);
    return ipV6Packet != null ? ipV6Packet.getHeader().getSrcAddr().getHostAddress() : null;
  }

  public static Flow fromYml(File ymlFile) throws FileNotFoundException {
    try (InputStream stream = new FileInputStream(ymlFile)) {
      return fromYmlStream(stream);
    } catch (FileNotFoundException e) {
      throw e;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static Flow fromYmlStream(InputStream stream) {
    Object loaded = new Yaml(buildYamlConstructor()).load(stream);
    if (!(loaded instanceof List)) {
      throw new IllegalArgumentException(
          "A flow has to be a list of !server and !client steps");
    }
    List<PacketStep> steps = new ArrayList<>();
    for (Object step : (List<?>) loaded) {
      if (!(step instanceof PacketStep)) {
        throw new IllegalArgumentException(
            "Flow steps have to be tagged with !server or !client, but got: " + step);
      }
      if (((PacketStep) step).getData() == null) {
        throw new IllegalArgumentException(
            "Flow step number " + (steps.size() + 1) + " has no data");
      }
      steps.add((PacketStep) step);
    }
    return new Flow(steps);
  }

  private static Constructor buildYamlConstructor() {
    // SnakeYAML default loader options reject global tags (like !!java.net.URL), so only the
    // types registered here can be instantiated from a flow file.
    LoaderOptions options = new LoaderOptions();
    // flows generated from big captures can easily exceed the default limit of 3MB
    options.setCodePointLimit(Integer.MAX_VALUE);
    Constructor constructor = new Constructor(options);
    YAML_TAGS
        .forEach((tag, clazz) -> constructor.addTypeDescription(new TypeDescription(clazz, tag)));
    return constructor;
  }

  public void saveYml(File ymlFile) throws IOException {
    try (Writer writer = new OutputStreamWriter(Files.newOutputStream(ymlFile.toPath()),
        StandardCharsets.UTF_8)) {
      new Yaml(buildYamlRepresenter()).dump(steps, writer);
    }
  }

  private static Representer buildYamlRepresenter() {
    Representer representer = new Representer(new DumperOptions()) {
      private int previousPort = 0;

      @Override
      protected NodeTuple representJavaBeanProperty(Object javaBean, Property property,
          Object propertyValue, Tag customTag) {
        if (property.getType() == long.class && (long) propertyValue == 0) {
          return null;
        } else if (property.getType() == int.class) {
          if ((int) propertyValue == 0 || previousPort == (int) propertyValue) {
            return null;
          }
          previousPort = (int) propertyValue;
        }
        return super.representJavaBeanProperty(javaBean, property, propertyValue, customTag);
      }
    };

    YAML_TAGS.forEach((tag, clazz) -> representer.addClassTag(clazz, new Tag(tag)));
    return representer;
  }

  /**
   * Builds the flow as seen from the other end of the connection: sent packets become expected
   * ones and vice versa.
   * <p>
   * Ports are kept only when the flow involves more than one port, so that a single port flow can
   * be replayed against any target port. Delays are not kept, since they are recorded for server
   * packets only.
   *
   * @return the reversed flow.
   * @throws IllegalArgumentException when the flow uses wildcards, captures or references, since
   * there is no way of knowing what to send for them.
   */
  public Flow reversed() {
    boolean multiplePorts = steps.stream()
        .map(PacketStep::getPort)
        .filter(Objects::nonNull)
        .distinct()
        .count() > 1;
    return new Flow(steps.stream()
        .map(s -> s.reversed(multiplePorts && s.getPort() != null ? s.getPort() : 0))
        .collect(Collectors.toList()));
  }

  /**
   * Gets the read buffer size needed to receive every expected packet of the flow.
   *
   * @param minimumSize the size to use when no expected packet is bigger than it.
   * @return the biggest value between the given size and the size of the biggest expected packet.
   */
  public int getRequiredReadBufferSize(int minimumSize) {
    return steps.stream()
        .mapToInt(PacketStep::getExpectedPacketLength)
        .reduce(minimumSize, Math::max);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Flow flow = (Flow) o;
    return Objects.equals(steps, flow.steps);
  }

  @Override
  public int hashCode() {
    return Objects.hash(steps);
  }

  public int getPortCount() {
    return getPorts().size();
  }

  public List<Integer> getPorts() {
    return steps.stream()
        .filter(p -> p instanceof SendPacketStep)
        .map(PacketStep::getPort)
        .distinct()
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }
}

package us.abstracta.wiresham;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.pcap4j.core.BpfProgram.BpfCompileMode;
import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.Pcaps;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.TcpPacket;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

/**
 * A series of steps which drive the communication with a client.
 */
public class Flow {

  private static final Map<String, Class<?>> YAML_TAGS = ImmutableMap.<String, Class<?>>builder()
      .put("!server", SendPacketStep.class)
      .put("!client", ReceivePacketStep.class)
      .put("!include", IncludePacketStep.class)
      .build();

  private static final JsonPointer WIRESHARK_LAYERS_PATH = JsonPointer.valueOf("/_source/layers");
  private static final JsonPointer WIRESHARK_TCP_PAYLOAD_PATH = JsonPointer
      .valueOf("/tcp/tcp.payload");
  private static final JsonPointer WIRESHARK_SOURCE_IP_PATH = JsonPointer.valueOf("/ip/ip.src");
  private static final JsonPointer WIRESHARK_SOURCE_PORT_PATH = JsonPointer
      .valueOf("/tcp/tcp.srcport");
  private static final JsonPointer WIRESHARK_DESTINE_PORT_PATH = JsonPointer
      .valueOf("/tcp/tcp.dstport");
  private static final JsonPointer WIRESHARK_TIME_DELTA_PATH = JsonPointer
      .valueOf("/frame/frame.time_delta_displayed");
  private static final String IP_PORT_SEPARATOR = ":";

  public List<PacketStep> steps;
  public String id;

  public Flow() {
  }

  @VisibleForTesting
  public Flow(List<PacketStep> steps) {
    this.steps = steps;
  }

  public Flow(List<PacketStep> steps, String id) {
    this(steps);
    this.id = id;
  }

  public void setSteps(List<PacketStep> steps) {
    this.steps = steps;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
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
    return new Flow(StreamSupport.stream(json.spliterator(), false)
        .filter(packet -> !packet.at(WIRESHARK_LAYERS_PATH).at(WIRESHARK_TCP_PAYLOAD_PATH).asText()
            .isEmpty())
        .map(packet -> {
          JsonNode layers = packet.at(WIRESHARK_LAYERS_PATH);
          String sourceIp = layers.at(WIRESHARK_SOURCE_IP_PATH).asText();
          String sourcePort = layers.at(WIRESHARK_SOURCE_PORT_PATH).asText();
          String hexDump = layers.at(WIRESHARK_TCP_PAYLOAD_PATH).asText()
              .replace(":", "");
          long timeDeltaMillis =
              Long.parseLong(layers.at(WIRESHARK_TIME_DELTA_PATH).asText().replace(".", ""))
                  / 1000000;
          return isServerAddress(sourceIp, sourcePort, serverAddress) ? new SendPacketStep(
              hexDump, timeDeltaMillis, Integer.parseInt(sourcePort))
              : new ReceivePacketStep(hexDump,
                  Integer.parseInt(layers.at(WIRESHARK_DESTINE_PORT_PATH).asText()));
        })
        .collect(Collectors.toList()));
  }

  private static boolean isServerAddress(String sourceIp, String sourcePort, String serverAddress) {
    return serverAddress.contains(IP_PORT_SEPARATOR)
        && (sourceIp + IP_PORT_SEPARATOR + sourcePort).equals(serverAddress)
        || serverAddress.equals(sourceIp);
  }

  public static Flow fromPcap(File file, String serverAddress, String filter) throws IOException {
    List<PacketStep> steps = new ArrayList<>();
    try (PcapHandle pcap = Pcaps.openOffline(file.getAbsolutePath())) {
      if (filter != null) {
        pcap.setFilter(filter, BpfCompileMode.OPTIMIZE);
      }
      long lastTimeMillis = 0;
      // we can't use getNextPacket and do a while != null due to https://github.com/kaitoy/pcap4j/issues/13
      // so we use getNextPacketEx which throws an EOFException when reached end of file
      while (true) {
        org.pcap4j.packet.Packet p = pcap.getNextPacketEx();
        if (!p.contains(TcpPacket.class)) {
          continue;
        }
        org.pcap4j.packet.Packet payload = p.get(TcpPacket.class).getPayload();
        if (payload == null) {
          continue;
        }
        IpV4Packet ipV4Packet = p.get(IpV4Packet.class);
        String sourceIp = ipV4Packet.getHeader().getSrcAddr().getHostAddress();
        int sourcePort = ipV4Packet.getPayload().get(TcpPacket.class).getHeader().getSrcPort()
            .valueAsInt();
        String hexDump = BaseEncoding.base16().encode(payload.getRawData());
        long timeMillis = pcap.getTimestamp().getTime();
        long timeDeltaMillis = lastTimeMillis > 0 ? timeMillis - lastTimeMillis : 0;
        lastTimeMillis = timeMillis;
        steps.add(isServerAddress(sourceIp, String.valueOf(sourcePort), serverAddress)
            ? new SendPacketStep(hexDump, timeDeltaMillis, sourcePort)
            : new ReceivePacketStep(hexDump,
                ipV4Packet.getPayload().get(TcpPacket.class).getHeader().getDstPort()
                    .valueAsInt()));
      }
    } catch (EOFException e) {
      //just ignore if we reached end of file.
    } catch (TimeoutException | PcapNativeException | NotOpenException e) {
      throw new IOException("Problem reading file " + file, e);
    }
    return new Flow(steps);
  }

  public static Flow fromYml(File ymlFile) throws FileNotFoundException {
    List<Flow> flows = StreamSupport.stream(
            new Yaml(buildYamlConstructor()).loadAll(new FileInputStream(ymlFile))
                .spliterator(), false)
        .map(f -> (Flow) f)
        .collect(Collectors.toList());
    return new Flow(recursivelySolveSteps(flows.get(0), flows));
  }

  private static List<PacketStep> recursivelySolveSteps(Flow flow, List<Flow> flows) {
    List<PacketStep> steps = new ArrayList<>();
    int lastPort = 0;
    for (PacketStep step : flow.steps) {
      if (step instanceof IncludePacketStep) {
        steps.addAll(
            recursivelySolveSteps(findFlow(((IncludePacketStep) step).getId(), flows), flows));
        continue;
      }
      if (step.getPort() != null) {
        lastPort = step.getPort();
      } else {
        step.setPort(lastPort);
      }
      steps.add(step);
    }
    return steps;
  }

  private static Flow findFlow(String id, List<Flow> flows) {
    return flows.stream()
        .filter(f -> id.equals(f.getId()))
        .findFirst()
        .orElseGet(() -> flows.get(Integer.parseInt(id)));
  }

  public static Flow fromYmlStream(InputStream stream) {
    List<PacketStep> packets = new Yaml(buildYamlConstructor())
        .load(stream);
    return new Flow(packets);
  }

  private static Constructor buildYamlConstructor() {
    FlowConstructor constructor = new FlowConstructor();
    YAML_TAGS
        .forEach((tag, clazz) -> constructor.addTypeDescription(new TypeDescription(clazz, tag)));
    return constructor;
  }

  public void saveYml(File ymlFile) throws IOException {
    new Yaml(buildYamlRepresenter())
        .dump(steps, new FileWriter(ymlFile));
  }

  private static Representer buildYamlRepresenter() {
    Representer representer = new Representer() {
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

  public Flow reversed() {
    return new Flow(steps.stream()
        .map(s -> s instanceof SendPacketStep ? new ReceivePacketStep(s.data.toString())
            : new SendPacketStep(s.data.toString(), 0))
        .collect(Collectors.toList()));
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
        .map(packetStep -> {
          if (packetStep instanceof SendPacketStep) {
            return Collections.singletonList(packetStep.getPort());
          } else if (packetStep instanceof ParallelPacketStep) {
            return ((ParallelPacketStep) packetStep).getPorts();
          }
          return null;
        })
        .filter(Objects::nonNull)
        .flatMap(Collection::stream)
        .distinct()
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  public static class FlowConstructor extends Constructor {

    private int flowIndex = 0;

    @Override
    protected Object constructObject(Node node) {
      Object o = super.constructObject(node);
      if (o instanceof List) {
        return new Flow((List<PacketStep>) o);
      } else if (o instanceof Map) {
        Map<String, Object> map = (Map<String, Object>) o;
        String id = (String) map.get("id");
        Flow flow = (Flow) map.get("steps");
        flow.setId(id != null ? id : String.valueOf(++flowIndex));
        return flow;
      }
      return o;
    }

  }
}

package us.abstracta.wiresham;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
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

  private static final Map<String, Class> YAML_TAGS = ImmutableMap.<String, Class>builder()
      .put("!server", ServerPacketStep.class)
      .put("!client", ClientPacketStep.class)
      .build();

  private static final JsonPointer WIRESHARK_LAYERS_PATH = JsonPointer.valueOf("/_source/layers");
  private static final JsonPointer WIRESHARK_TCP_PAYLOAD_PATH = JsonPointer
      .valueOf("/tcp/tcp.payload");
  private static final JsonPointer WIRESHARK_IP_PATH = JsonPointer.valueOf("/ip/ip.src");
  private static final JsonPointer WIRESHARK_TIME_DELTA_PATH = JsonPointer
      .valueOf("/frame/frame.time_delta_displayed");

  private final List<PacketStep> steps;

  private Flow(List<PacketStep> steps) {
    this.steps = steps;
  }

  public List<PacketStep> getSteps() {
    return steps;
  }

  @Override
  public String toString() {
    return steps.toString();
  }

  public static Flow fromWiresharkJsonDump(File wiresharkJsonDumpFile, String serverAddress)
      throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode json = mapper.readTree(wiresharkJsonDumpFile);
    return new Flow(StreamSupport.stream(json.spliterator(), false)
        .filter(packet -> !packet.at(WIRESHARK_LAYERS_PATH).at(WIRESHARK_TCP_PAYLOAD_PATH).asText()
            .isEmpty())
        .map(packet -> {
          JsonNode layers = packet.at(WIRESHARK_LAYERS_PATH);
          String ipSource = layers.at(WIRESHARK_IP_PATH).asText();
          String hexDump = layers.at(WIRESHARK_TCP_PAYLOAD_PATH).asText()
              .replace(":", "");
          long timeDeltaMillis =
              Long.valueOf(layers.at(WIRESHARK_TIME_DELTA_PATH).asText().replace(".", ""))
                  / 1000000;
          return serverAddress.equals(ipSource) ? new ServerPacketStep(hexDump, timeDeltaMillis)
              : new ClientPacketStep(hexDump);
        })
        .collect(Collectors.toList()));
  }

  @SuppressWarnings("unchecked")
  public static Flow fromYml(File ymlFile) throws FileNotFoundException {
    List<PacketStep> packets = (List<PacketStep>) new Yaml(buildYamlConstructor())
        .load(new FileInputStream(ymlFile));
    return new Flow(packets);
  }

  @SuppressWarnings("unchecked")
  public static Flow fromYmlStream(InputStream stream) {
    List<PacketStep> packets = (List<PacketStep>) new Yaml(buildYamlConstructor())
        .load(stream);
    return new Flow(packets);
  }

  @SuppressWarnings("unchecked")
  private static Constructor buildYamlConstructor() {
    Constructor constructor = new Constructor();
    YAML_TAGS
        .forEach((tag, clazz) -> constructor.addTypeDescription(new TypeDescription(clazz, tag)));
    return constructor;
  }

  public void saveYml(File ymlFile) throws IOException {
    new Yaml(buildYamlRepresenter())
        .dump(steps, new FileWriter(ymlFile));
  }

  @SuppressWarnings("unchecked")
  private static Representer buildYamlRepresenter() {
    Representer representer = new Representer() {
      @Override
      protected NodeTuple representJavaBeanProperty(Object javaBean, Property property,
          Object propertyValue, Tag customTag) {
        if (property.getType() == long.class && (long) propertyValue == 0) {
          return null;
        } else {
          return super.representJavaBeanProperty(javaBean, property, propertyValue, customTag);
        }
      }
    };

    YAML_TAGS.forEach((tag, clazz) -> representer.addClassTag(clazz, new Tag(tag)));
    return representer;
  }

}

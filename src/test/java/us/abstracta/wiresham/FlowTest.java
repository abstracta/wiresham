package us.abstracta.wiresham;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FlowTest {

  private static final String CLIENT_DATA = "43485F4643457C31307C0D0A";
  private static final String SERVER_DATA = "5245535F4643457C547C332E302E3135352E313731FF";
  private static final int SERVER_PORT = 3469;

  @TempDir
  Path tempDir;

  @Test
  public void shouldGetServerAndClientStepsWhenLoadWiresharkWithSameServerAndClientIp()
      throws IOException {
    Flow flow = Flow.fromWiresharkJsonDump(TestResource.getResourceFile("/serverOnLocalPort.json"),
        "127.0.0.1:" + SERVER_PORT);
    assertThat(flow.getSteps()).isEqualTo(Arrays.asList(
        new ReceivePacketStep(CLIENT_DATA, SERVER_PORT),
        new SendPacketStep(SERVER_DATA, 25, SERVER_PORT)));
  }

  @Test
  public void shouldGetOnlyClientStepsWhenLoadWiresharkWithUnknownServerAddress()
      throws IOException {
    Flow flow = Flow.fromWiresharkJsonDump(TestResource.getResourceFile("/serverOnLocalPort.json"),
        "10.9.9.9");
    assertThat(flow.getSteps()).allMatch(s -> s instanceof ReceivePacketStep);
    assertThat(flow.getPorts()).isEmpty();
  }

  @Test
  public void shouldGetServerAndClientStepsWhenLoadWiresharkWithIpV6Addresses()
      throws IOException {
    File dump = writeFile("ipv6.json", "["
        + buildIpV6Packet("::2", "50000", "2323", "48:69", "0.000000000") + ","
        + buildIpV6Packet("::1", "2323", "50000", "4f:4b", "0.250000") + "]");
    Flow expected = new Flow(Arrays.asList(
        new ReceivePacketStep("4869", 2323),
        new SendPacketStep("4F4B", 250, 2323)));
    assertThat(Flow.fromWiresharkJsonDump(dump, "::1").getSteps())
        .isEqualTo(expected.getSteps());
    assertThat(Flow.fromWiresharkJsonDump(dump, "[::1]:2323").getSteps())
        .isEqualTo(expected.getSteps());
    assertThat(Flow.fromWiresharkJsonDump(dump, "0:0:0:0:0:0:0:1").getSteps())
        .isEqualTo(expected.getSteps());
    assertThat(Flow.fromWiresharkJsonDump(dump, "[::1]:9999").getSteps())
        .allMatch(s -> s instanceof ReceivePacketStep);
  }

  private String buildIpV6Packet(String sourceIp, String sourcePort, String destinationPort,
      String payload, String timeDelta) {
    return "{\"_source\":{\"layers\":{"
        + "\"frame\":{\"frame.time_delta_displayed\":\"" + timeDelta + "\"},"
        + "\"ipv6\":{\"ipv6.src\":\"" + sourceIp + "\"},"
        + "\"tcp\":{\"tcp.srcport\":\"" + sourcePort + "\",\"tcp.dstport\":\"" + destinationPort
        + "\",\"tcp.payload\":\"" + payload + "\"}}}}";
  }

  private File writeFile(String name, String content) throws IOException {
    Path path = tempDir.resolve(name);
    Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    return path.toFile();
  }

  @Test
  public void shouldKeepPortsWhenReverseFlowWithMultiplePorts() {
    Flow flow = loadYaml("- !server {data: FFFF, delayMillis: 10, port: 2324}\n"
        + "- !client {data: FFFF}\n"
        + "- !server {data: FFFF, port: 2325}\n"
        + "- !client {data: FFFF}\n");
    assertThat(flow.reversed().getSteps().stream()
        .map(PacketStep::getPort)
        .collect(Collectors.toList()))
        .containsExactly(2324, null, 2325, null);
  }

  private Flow loadYaml(String yaml) {
    return Flow.fromYmlStream(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  public void shouldDropPortsWhenReverseFlowWithSinglePort() {
    Flow flow = loadYaml("- !server {data: FFFF, port: 2324}\n"
        + "- !client {data: FFFF}\n");
    assertThat(flow.reversed().getSteps())
        .allMatch(s -> s.getPort() == null);
  }

  @Test
  public void shouldRejectFlowWhenLoadYamlWithUnexpectedType() {
    assertThatThrownBy(() -> loadYaml("- !!java.net.URL [\"http://localhost/\"]\n"
        + "- !server {data: FF}\n"))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  public void shouldRejectFlowWhenLoadYamlWhichIsNotAListOfSteps() {
    assertThatThrownBy(() -> loadYaml("data: FF\n"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldRejectFlowWhenLoadYamlWithStepWithoutData() {
    assertThatThrownBy(() -> loadYaml("- !server {delayMillis: 10}\n"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldLoadFlowWhenYamlIsBiggerThanDefaultYamlLimit() {
    StringBuilder data = new StringBuilder();
    for (int i = 0; i < 4 * 1024 * 1024; i++) {
      data.append('A');
    }
    Flow flow = loadYaml("- !server {data: " + data + "}\n");
    assertThat(flow.getSteps().get(0).getData()).hasSize(4 * 1024 * 1024);
  }

  @Test
  public void shouldGetSameFlowWhenSaveAndLoadYaml() throws IOException {
    Flow flow = SimpleFlow.getFlow();
    File saved = tempDir.resolve("saved.yaml").toFile();
    flow.saveYml(saved);
    assertThat(Flow.fromYml(saved)).isEqualTo(flow);
  }

  @Test
  public void shouldKeepExpressionsWhenLoadYamlWithWildcardsCapturesAndReferences() {
    Flow flow = loadYaml("- !client {data: '4c4f(?<user>.{8}).'}\n"
        + "- !server {data: '4f4b${user}', delayMillis: 20}\n"
        + "- !client {data: 51..}\n");
    assertThat(flow.getSteps().stream()
        .map(PacketStep::getData)
        .collect(Collectors.toList()))
        .containsExactly("4C4F(?<user>.{8}).", "4F4B${user}", "51..");
  }

  @Test
  public void shouldGetSameFlowWhenSaveAndLoadYamlWithWildcardsCapturesAndReferences()
      throws IOException {
    Flow flow = new Flow(Arrays.asList(
        new ReceivePacketStep("4C4F(?<user>.{8}).", SERVER_PORT),
        new SendPacketStep("4F4B${user}", 20),
        new ReceivePacketStep(".{2}51")));
    File saved = tempDir.resolve("expressions.yaml").toFile();
    flow.saveYml(saved);
    assertThat(Flow.fromYml(saved)).isEqualTo(flow);
    assertThat(new String(Files.readAllBytes(saved.toPath()), StandardCharsets.UTF_8))
        .contains("data: '4C4F(?<user>.{8}).'");
  }

  @Test
  public void shouldRejectFlowWhenLoadYamlWithVariableLengthWildcard() {
    assertThatThrownBy(() -> loadYaml("- !client {data: 4C.*}\n"))
        .isInstanceOf(RuntimeException.class)
        .hasStackTraceContaining("Unsupported '*' at position 3");
  }

  @Test
  public void shouldRejectFlowWhenServerStepUsesValueNotCapturedByPreviousStep() {
    assertThatThrownBy(() -> loadYaml("- !server {data: '4F4B${user}'}\n"
        + "- !client {data: '4C4F(?<user>.{8})'}\n"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("${user}");
  }

  @Test
  public void shouldRejectReversedFlowWhenFlowUsesWildcards() {
    Flow flow = loadYaml("- !client {data: 4C.}\n"
        + "- !server {data: 4F4B}\n");
    assertThatThrownBy(flow::reversed)
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldUseExpectedPacketSizeWhenGetRequiredReadBufferSizeWithWildcards() {
    Flow flow = loadYaml("- !client {data: '4C(?<id>.{4000})'}\n");
    assertThat(flow.getRequiredReadBufferSize(2048)).isEqualTo(4001);
  }

}

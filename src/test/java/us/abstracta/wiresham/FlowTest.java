package us.abstracta.wiresham;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import us.abstracta.wiresham.SimpleFlow.FlowBuilder;

public class FlowTest {

  @Test
  public void shouldGetServerAndClientStepsWhenLoadWiresharkWithSameServerAndClientIp()
      throws IOException {
    Flow flow = Flow.fromWiresharkJsonDump(TestResource.getResourceFile("/serverOnLocalPort.json"),
        "127.0.0.1:3469");
    assertEquals(flow.getSteps(), Arrays.asList(
        new ReceivePacketStep("43485F4643457C31307C0D0A"),
        new SendPacketStep("5245535F4643457C547C332E302E3135352E313731FF", 0)
    ));
  }

  @Test
  public void shouldGetServerAndClientStepsWhenLoadMultiplePortYamlWithInclude()
      throws FileNotFoundException {
    Flow flow = Flow.fromYml(TestResource.getResourceFile("/multiple-port.yaml"));
    Flow expected = new FlowBuilder()
        .withServerPacket(SimpleFlow.SERVER_WELCOME_MESSAGE, 23)
        .withClientPacket(SimpleFlow.CLIENT_REQUEST)
        .withServerPacket(SimpleFlow.SERVER_RESPONSE, 24)
        .withClientPacket(SimpleFlow.CLIENT_GOODBYE)
        .withServerPacket(SimpleFlow.SERVER_GOODBYE, 23)
        .withServerPacket(SimpleFlow.SERVER_GOODBYE, 25)
        .build();
    assertEquals(flow, expected);
  }
}

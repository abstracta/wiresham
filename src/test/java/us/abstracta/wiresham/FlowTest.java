package us.abstracta.wiresham;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

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

}

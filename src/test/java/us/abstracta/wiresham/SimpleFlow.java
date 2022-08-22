package us.abstracta.wiresham;

import com.google.common.base.Charsets;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public abstract class SimpleFlow {

  public static final String SERVER_WELCOME_MESSAGE = "Hello";
  public static final String CLIENT_REQUEST = "Hello, I'm John";
  public static final String SERVER_RESPONSE = "Hello John";
  public static final String UNEXPECTED_MESSAGE = "What's up!";
  public static final String CLIENT_GOODBYE = "Bye";
  public static final String SERVER_GOODBYE = "Bye John";

  private SimpleFlow() {
  }

  public static Flow getFlow() throws FileNotFoundException {
    return Flow.fromYml(TestResource.getResourceFile("/simple.yaml"));
  }

  public static class FlowBuilder {

    private final List<PacketStep> steps = new ArrayList<>();

    public FlowBuilder withServerPacket(String data, int port) {
      steps.add(new SendPacketStep(encodeTextToHex(data), 0, port));
      return this;
    }

    public FlowBuilder withServerPacket(String data) {
      steps.add(new SendPacketStep(encodeTextToHex(data), 0));
      return this;
    }

    public FlowBuilder withClientPacket(String data) {
      steps.add(new ReceivePacketStep(encodeTextToHex(data)));
      return this;
    }

    private String encodeTextToHex(String text) {
      byte[] bytes = text.getBytes(Charsets.UTF_8);
      return IntStream.range(0, bytes.length)
          .mapToObj(i -> Integer.toHexString(bytes[i]))
          .collect(Collectors.joining());
    }

    public Flow build() {
      return new Flow(steps);
    }
  }
}

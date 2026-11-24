package us.abstracta.wiresham;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class PacketTemplateTest {

  @Test
  public void shouldGetSameBytesWhenRenderTemplateWithOnlyHexBytes() {
    assertThat(PacketTemplate.parse("0aFF10").render(new FlowContext()))
        .isEqualTo(Packet.fromHexDump("0AFF10").getBytes());
  }

  @Test
  public void shouldGetCapturedValuesWhenRenderTemplateWithReferences() {
    FlowContext context = new FlowContext();
    context.setVariableValue("id", Packet.fromHexDump("AABB"));
    context.setVariableValue("kind", Packet.fromHexDump("0C"));
    assertThat(PacketTemplate.parse("${id}01${kind}${id}ff").render(context))
        .isEqualTo(Packet.fromHexDump("AABB010CAABBFF").getBytes());
  }

  @Test
  public void shouldFailWhenRenderTemplateWithoutCapturedValue() {
    PacketTemplate template = PacketTemplate.parse("01${id}");
    assertThatThrownBy(() -> template.render(new FlowContext()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("${id}");
  }

  @Test
  public void shouldRejectExpressionWhenUsingUnsupportedConstructs() {
    for (String expression : Arrays.asList("4C.", "4C.{2}", "(?<a>4C)", "${a", "${}", "${1a}",
        "${a b}", "$a", "{{a}}", "4", "4G", "4C 4D", "４Ｃ")) {
      assertThatThrownBy(() -> PacketTemplate.parse(expression))
          .as(expression)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("at position");
    }
  }

}

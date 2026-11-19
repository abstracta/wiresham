package us.abstracta.wiresham;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class PacketTemplateTest {

  @Test
  public void shouldGetSameBytesWhenRenderTemplateWithOnlyHexBytes() {
    PacketTemplate template = PacketTemplate.parse("0aFF10");
    assertThat(template.isLiteral()).isTrue();
    assertThat(template.toString()).isEqualTo("0AFF10");
    assertThat(template.render(new FlowContext()))
        .isEqualTo(Packet.fromHexDump("0AFF10").getBytes());
  }

  @Test
  public void shouldGetCapturedValuesWhenRenderTemplateWithReferences() {
    PacketTemplate template = PacketTemplate.parse("${id}01${kind}${id}ff");
    assertThat(template.isLiteral()).isFalse();
    assertThat(template.getReferences()).containsExactly("id", "kind");
    assertThat(template.toString()).isEqualTo("${id}01${kind}${id}FF");
    FlowContext context = new FlowContext();
    context.capture("id", Packet.fromHexDump("AABB"));
    context.capture("kind", Packet.fromHexDump("0C"));
    assertThat(template.render(context))
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
        "${a b}", "$a", "{{a}}", "4", "4G", "4C 4D")) {
      assertThatThrownBy(() -> PacketTemplate.parse(expression))
          .as(expression)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("at position");
    }
  }

}

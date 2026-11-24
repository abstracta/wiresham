package us.abstracta.wiresham;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class PacketPatternTest {

  @Test
  public void shouldMatchOnlySameBytesWhenPatternHasOnlyHexBytes() {
    PacketPattern pattern = PacketPattern.parse("0aFF10");
    assertThat(pattern.matches(bytes(0x0A, 0xFF, 0x10), 0)).isTrue();
    assertThat(pattern.matches(bytes(0x0A, 0xFF, 0x11), 0)).isFalse();
  }

  private static byte[] bytes(int... values) {
    byte[] ret = new byte[values.length];
    for (int i = 0; i < values.length; i++) {
      ret[i] = (byte) values[i];
    }
    return ret;
  }

  @Test
  public void shouldMatchAnyValueInWildcardBytesWhenPatternHasWildcards() {
    PacketPattern pattern = PacketPattern.parse("01..{2}02");
    assertThat(pattern.matches(bytes(1, 0, 0xFF, 0x7F, 2), 0)).isTrue();
    assertThat(pattern.matches(bytes(1, 0, 0, 0, 3), 0)).isFalse();
  }

  @Test
  public void shouldMatchBytesInReceptionOrderWhenBufferIsCircular() {
    PacketPattern pattern = PacketPattern.parse("0102.04");
    // oldest byte is at position 2, so the received bytes are 01 02 03 04
    assertThat(pattern.matches(bytes(3, 4, 1, 2), 2)).isTrue();
    assertThat(pattern.matches(bytes(3, 4, 1, 2), 0)).isFalse();
  }

  @Test
  public void shouldGetCapturedBytesWhenPatternHasCaptures() {
    PacketPattern pattern = PacketPattern.parse("01(?<id>.{2})(?<kind>0A.)FF");
    // oldest byte is at position 2, so the received bytes are 01 AA BB 0A 0B FF
    assertThat(pattern.capture(bytes(0x0B, 0xFF, 0x01, 0xAA, 0xBB, 0x0A), 2))
        .isEqualTo(ImmutableMap.of(
            "id", Packet.fromHexDump("AABB"),
            "kind", Packet.fromHexDump("0A0B")));
  }

  @Test
  public void shouldRejectExpressionWhenUsingUnsupportedConstructs() {
    for (String expression : Arrays.asList("4C*", "4C+", "4C?", ".*", ".+", ".{2,4}", ".{2,}",
        ".{0}", ".{}", ".{a}", ".{2", ".{16777217}", ".{999999999}", ".{9999999999}", "4C{2}",
        "4C|4D", "[4C4D]", "(4C)", "(?:4C)", "4C)", "(?<a>4C", "(?<a>)", "(?<a>(?<b>4C))",
        "(?<a>4C)(?<a>4D)", "(?<1a>4C)", "(?<>4C)", "(?<a_b>4C)", "4", "4C4", "4G", "4C 4D",
        "${a}", "\\x4C", "４Ｃ", "٤C")) {
      assertThatThrownBy(() -> PacketPattern.parse(expression))
          .as(expression)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("at position");
    }
  }

}

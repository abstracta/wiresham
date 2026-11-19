package us.abstracta.wiresham;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Content of a packet to send, which may include values captured from received packets.
 *
 * <p>It is a sequence of hex bytes and <code>${name}</code> references to values captured by
 * previous steps.
 */
final class PacketTemplate {

  private static final String REFERENCE_START = "${";

  private final String expression;
  // each part is either a byte[] with literal bytes or a String with the name of a capture
  private final List<Object> parts;

  private PacketTemplate(String expression, List<Object> parts) {
    this.expression = expression;
    this.parts = parts;
  }

  static PacketTemplate parse(String text) {
    StringBuilder normalized = new StringBuilder();
    List<Object> parts = new ArrayList<>();
    ByteArrayOutputStream literal = new ByteArrayOutputStream();
    int pos = 0;
    while (pos < text.length()) {
      if (text.startsWith(REFERENCE_START, pos)) {
        int end = text.indexOf('}', pos);
        String name = end == -1 ? "" : text.substring(pos + REFERENCE_START.length(), end);
        if (!PacketPattern.CAPTURE_NAME.matcher(name).matches()) {
          throw buildException("Invalid reference", pos, text);
        }
        if (literal.size() > 0) {
          parts.add(literal.toByteArray());
          literal.reset();
        }
        parts.add(name);
        normalized.append(REFERENCE_START).append(name).append('}');
        pos = end + 1;
      } else if (PacketPattern.isHexDigit(text.charAt(pos))) {
        if (pos + 1 >= text.length() || !PacketPattern.isHexDigit(text.charAt(pos + 1))) {
          throw buildException("Incomplete hex byte", pos, text);
        }
        literal.write(PacketPattern.parseHexByte(text, pos));
        normalized.append(text.substring(pos, pos + 2).toUpperCase());
        pos += 2;
      } else {
        throw buildException("Unsupported '" + text.charAt(pos) + "'", pos, text);
      }
    }
    if (literal.size() > 0) {
      parts.add(literal.toByteArray());
    }
    return new PacketTemplate(normalized.toString(), parts);
  }

  private static IllegalArgumentException buildException(String problem, int position,
      String text) {
    return new IllegalArgumentException(problem + " at position " + position + " of '"
        + PacketPattern.abbreviate(text) + "'. Only hex bytes and '${name}' are supported.");
  }

  boolean isLiteral() {
    return getReferences().isEmpty();
  }

  Set<String> getReferences() {
    Set<String> ret = new LinkedHashSet<>();
    for (Object part : parts) {
      if (part instanceof String) {
        ret.add((String) part);
      }
    }
    return ret;
  }

  byte[] render(FlowContext context) {
    ByteArrayOutputStream ret = new ByteArrayOutputStream();
    for (Object part : parts) {
      byte[] bytes;
      if (part instanceof String) {
        Packet value = context.getCaptured((String) part);
        if (value == null) {
          throw new IllegalStateException("No value has been captured for '${" + part + "}'");
        }
        bytes = value.getBytes();
      } else {
        bytes = (byte[]) part;
      }
      ret.write(bytes, 0, bytes.length);
    }
    return ret.toByteArray();
  }

  @Override
  public String toString() {
    return expression;
  }

}

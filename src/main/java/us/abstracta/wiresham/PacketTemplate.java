package us.abstracta.wiresham;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Content of a packet to send, which may include values captured from received packets.
 *
 * <p>It is a sequence of hex bytes and {@code ${name}} references to values captured by
 * previous steps.
 */
final class PacketTemplate {

  private static final String REFERENCE_START = "${";
  private static final String SUPPORTED_SYNTAX = "Only hex bytes and '${name}' are supported.";

  private final String expression;
  private final List<Part> parts;

  private PacketTemplate(String expression, List<Part> parts) {
    this.expression = expression;
    this.parts = parts;
  }

  static PacketTemplate parse(String text) {
    StringBuilder normalized = new StringBuilder();
    List<Part> parts = new ArrayList<>();
    ByteArrayOutputStream literal = new ByteArrayOutputStream();
    int pos = 0;
    while (pos < text.length()) {
      if (text.startsWith(REFERENCE_START, pos)) {
        int end = text.indexOf('}', pos);
        String name = end == -1 ? "" : text.substring(pos + REFERENCE_START.length(), end);
        if (!PacketExpressions.CAPTURE_NAME.matcher(name).matches()) {
          throw buildException("Invalid reference", pos, text);
        }
        if (literal.size() > 0) {
          parts.add(Part.literal(literal.toByteArray()));
          literal.reset();
        }
        parts.add(Part.reference(name));
        normalized.append(REFERENCE_START).append(name).append('}');
        pos = end + 1;
      } else if (PacketExpressions.isHexDigit(text.charAt(pos))) {
        if (!PacketExpressions.isHexByte(text, pos)) {
          throw buildException("Incomplete hex byte", pos, text);
        }
        literal.write(PacketExpressions.parseHexByte(text, pos));
        normalized.append(text.substring(pos, pos + 2).toUpperCase());
        pos += 2;
      } else {
        throw buildException("Unsupported '" + text.charAt(pos) + "'", pos, text);
      }
    }
    if (literal.size() > 0) {
      parts.add(Part.literal(literal.toByteArray()));
    }
    return new PacketTemplate(normalized.toString(), parts);
  }

  private static IllegalArgumentException buildException(String problem, int position,
      String text) {
    return PacketExpressions.buildException(problem, position, text, SUPPORTED_SYNTAX);
  }

  boolean isLiteral() {
    return getReferences().isEmpty();
  }

  Set<String> getReferences() {
    Set<String> ret = new LinkedHashSet<>();
    for (Part part : parts) {
      if (part.reference != null) {
        ret.add(part.reference);
      }
    }
    return ret;
  }

  byte[] render(FlowContext context) {
    ByteArrayOutputStream ret = new ByteArrayOutputStream();
    for (Part part : parts) {
      byte[] bytes = part.resolve(context);
      ret.write(bytes, 0, bytes.length);
    }
    return ret.toByteArray();
  }

  /**
   * Part of a template, which is either literal bytes or a reference to a captured value.
   */
  private static final class Part {

    private final byte[] bytes;
    private final String reference;

    private Part(byte[] bytes, String reference) {
      this.bytes = bytes;
      this.reference = reference;
    }

    private static Part literal(byte[] bytes) {
      return new Part(bytes, null);
    }

    private static Part reference(String name) {
      return new Part(null, name);
    }

    private byte[] resolve(FlowContext context) {
      if (reference == null) {
        return bytes;
      }
      Packet value = context.getCaptured(reference);
      if (value == null) {
        throw new IllegalStateException("No value has been captured for '${" + reference + "}'");
      }
      return value.getBytes();
    }

  }

  @Override
  public String toString() {
    return expression;
  }

}

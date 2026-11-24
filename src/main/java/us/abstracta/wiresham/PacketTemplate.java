package us.abstracta.wiresham;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Content of a packet to send, which may include values captured from received packets.
 *
 * <p>It is a sequence of hex bytes and {@code ${name}} references to values captured by
 * previous steps.
 */
final class PacketTemplate {

  private final String expression;
  private final List<Part> parts;

  private PacketTemplate(String expression, List<Part> parts) {
    this.expression = expression;
    this.parts = parts;
  }

  static PacketTemplate parse(String expression) {
    return new Parser(expression).parse();
  }

  boolean isLiteral() {
    return getReferences().isEmpty();
  }

  Set<String> getReferences() {
    return parts.stream()
        .map(p -> p.reference)
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  byte[] render(FlowContext context) {
    ByteArrayOutputStream ret = new ByteArrayOutputStream();
    for (Part part : parts) {
      byte[] bytes = part.resolve(context);
      ret.write(bytes, 0, bytes.length);
    }
    return ret.toByteArray();
  }

  @Override
  public String toString() {
    return expression;
  }

  private static final class Part {

    private final byte[] literal;
    private final String reference;

    private Part(byte[] literal, String reference) {
      this.literal = literal;
      this.reference = reference;
    }

    private byte[] resolve(FlowContext context) {
      if (reference == null) {
        return literal;
      }
      Packet value = context.getVariableValue(reference);
      if (value == null) {
        throw new IllegalStateException("No value has been captured for '${" + reference + "}'");
      }
      return value.getBytes();
    }

  }

  private static final class Parser extends PacketParser {

    private static final String REFERENCE_PREFIX = "${";

    private final List<Part> parts = new ArrayList<>();
    private final ByteArrayOutputStream literal = new ByteArrayOutputStream();

    private Parser(String text) {
      super(text, "Only hex bytes and '${name}' are supported.");
    }

    private PacketTemplate parse() {
      while (hasNext()) {
        if (nextIs(REFERENCE_PREFIX)) {
          String name = parseVariableName(REFERENCE_PREFIX, '}');
          addLiteralPart();
          parts.add(new Part(null, name));
        } else {
          literal.write(parseHexByte());
        }
      }
      addLiteralPart();
      return new PacketTemplate(normalized.toString(), parts);
    }

    private void addLiteralPart() {
      if (literal.size() > 0) {
        parts.add(new Part(literal.toByteArray(), null));
        literal.reset();
      }
    }

  }

}

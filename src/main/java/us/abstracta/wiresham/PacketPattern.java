package us.abstracta.wiresham;

import java.io.ByteArrayOutputStream;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Expected content of a packet, which may include bytes with any value and named captures.
 *
 * <p>It uses a subset of regular expressions syntax where the unit is the byte: a hex pair is a
 * literal byte, {@code .} is any byte, {@code .{n}} is any n bytes and {@code (?<name>...)}
 * captures the matched bytes with the given name. Only fixed length constructs are supported.
 */
final class PacketPattern {

  private final String expression;
  private final byte[] bytes;
  private final BitSet wildcards;
  private final Map<String, int[]> captureRanges;

  private PacketPattern(String expression, byte[] bytes, BitSet wildcards,
      Map<String, int[]> captureRanges) {
    this.expression = expression;
    this.bytes = bytes;
    this.wildcards = wildcards;
    this.captureRanges = captureRanges;
  }

  static PacketPattern parse(String expression) {
    return new Parser(expression).parse();
  }

  int length() {
    return bytes.length;
  }

  boolean isLiteral() {
    return wildcards.isEmpty() && captureRanges.isEmpty();
  }

  Set<String> getCaptureNames() {
    return captureRanges.keySet();
  }

  boolean matches(byte[] circularBuffer, int oldestBytePos) {
    int bufferPos = oldestBytePos;
    for (int i = 0; i < bytes.length; i++) {
      if (circularBuffer[bufferPos] != bytes[i] && !wildcards.get(i)) {
        return false;
      }
      bufferPos++;
      if (bufferPos == circularBuffer.length) {
        bufferPos = 0;
      }
    }
    return true;
  }

  Map<String, Packet> capture(byte[] circularBuffer, int oldestBytePos) {
    return captureRanges.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey,
            e -> copy(circularBuffer, oldestBytePos + e.getValue()[0], e.getValue()[1])));
  }

  static Packet copy(byte[] circularBuffer, long start, int length) {
    byte[] ret = new byte[length];
    for (int i = 0; i < length; i++) {
      ret[i] = circularBuffer[(int) ((start + i) % circularBuffer.length)];
    }
    return Packet.fromBytes(ret, 0, length);
  }

  @Override
  public String toString() {
    return expression;
  }

  private static final class Parser extends PacketParser {

    private static final String CAPTURE_PREFIX = "(?<";
    private static final Pattern REPETITION = Pattern.compile("[1-9][0-9]{0,8}");
    // avoids running out of memory due to a typo in a flow
    private static final int MAX_REPETITION = 16 * 1024 * 1024;

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final BitSet wildcards = new BitSet();
    private final Map<String, int[]> captureRanges = new LinkedHashMap<>();

    private Parser(String text) {
      super(text, "Only hex bytes, '.', '.{n}' and '(?<name>...)' with hex bytes and '.' inside "
          + "are supported.");
    }

    private PacketPattern parse() {
      while (hasNext()) {
        if (nextIs(CAPTURE_PREFIX)) {
          parseCapture();
        } else {
          parseByte();
        }
      }
      return new PacketPattern(normalized.toString(), bytes.toByteArray(), wildcards,
          captureRanges);
    }

    private void parseCapture() {
      int startPos = pos;
      String name = parseVariableName(CAPTURE_PREFIX, '>');
      if (captureRanges.containsKey(name)) {
        throw buildException("Capture '" + name + "' is defined more than once", startPos);
      }
      int startByte = bytes.size();
      while (hasNext() && !nextIs(")")) {
        parseByte();
      }
      if (!hasNext()) {
        throw buildException("Capture '" + name + "' is not closed", startPos);
      }
      if (bytes.size() == startByte) {
        throw buildException("Capture '" + name + "' is empty", startPos);
      }
      pos++;
      normalized.append(')');
      captureRanges.put(name, new int[]{startByte, bytes.size() - startByte});
    }

    private void parseByte() {
      if (nextIs(".")) {
        pos++;
        normalized.append('.');
        int count = parseRepetition();
        wildcards.set(bytes.size(), bytes.size() + count);
        bytes.write(new byte[count], 0, count);
      } else {
        bytes.write(parseHexByte());
      }
    }

    private int parseRepetition() {
      if (!nextIs("{")) {
        return 1;
      }
      int end = text.indexOf('}', pos);
      String count = end == -1 ? "" : text.substring(pos + 1, end);
      if (!REPETITION.matcher(count).matches()) {
        throw buildException("Unsupported repetition", pos);
      }
      int ret = Integer.parseInt(count);
      if (ret > MAX_REPETITION) {
        throw buildException("Repetition bigger than the maximum of " + MAX_REPETITION, pos);
      }
      pos = end + 1;
      normalized.append('{').append(count).append('}');
      return ret;
    }

  }

}

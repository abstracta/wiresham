package us.abstracta.wiresham;

import java.io.ByteArrayOutputStream;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Expected content of a packet, which may include bytes with any value and named captures.
 *
 * <p>It uses a subset of regular expressions syntax where the unit is the byte: an hex pair is a
 * literal byte, <code>.</code> is any byte, <code>.{n}</code> is any n bytes and
 * <code>(?&lt;name&gt;...)</code> captures the matched bytes with the given name. Only fixed
 * length constructs are supported.
 */
final class PacketPattern {

  static final Pattern CAPTURE_NAME = Pattern.compile("[a-zA-Z][a-zA-Z0-9]*");
  private static final String CAPTURE_START = "(?<";
  private static final Pattern REPETITION = Pattern.compile("[1-9][0-9]{0,8}");

  private final String expression;
  private final byte[] bytes;
  private final BitSet wildcards;
  private final Map<String, int[]> captures;

  private PacketPattern(String expression, byte[] bytes, BitSet wildcards,
      Map<String, int[]> captures) {
    this.expression = expression;
    this.bytes = bytes;
    this.wildcards = wildcards;
    this.captures = captures;
  }

  static PacketPattern parse(String expression) {
    return new Parser(expression).parse();
  }

  static boolean isHexDigit(char c) {
    return Character.digit(c, 16) != -1;
  }

  static int parseHexByte(String text, int pos) {
    return (Character.digit(text.charAt(pos), 16) << 4) + Character.digit(text.charAt(pos + 1), 16);
  }

  int length() {
    return bytes.length;
  }

  boolean isLiteral() {
    return wildcards.isEmpty() && captures.isEmpty();
  }

  Set<String> getCaptureNames() {
    return captures.keySet();
  }

  /**
   * Checks if the bytes of a circular buffer match the pattern.
   *
   * @param window circular buffer with as many bytes as the length of the pattern.
   * @param start position of the oldest byte in the buffer.
   * @return true if the bytes match the pattern, false otherwise.
   */
  boolean matches(byte[] window, int start) {
    int windowPos = start;
    for (int i = 0; i < bytes.length; i++) {
      if (window[windowPos] != bytes[i] && !wildcards.get(i)) {
        return false;
      }
      windowPos++;
      if (windowPos == window.length) {
        windowPos = 0;
      }
    }
    return true;
  }

  /**
   * Gets the captured values from the bytes of a circular buffer which matches the pattern.
   *
   * @param window circular buffer with as many bytes as the length of the pattern.
   * @param start position of the oldest byte in the buffer.
   * @return the captured values by capture name.
   */
  Map<String, Packet> capture(byte[] window, int start) {
    Map<String, Packet> ret = new LinkedHashMap<>();
    captures.forEach((name, range) -> ret.put(name, copy(window, start + range[0], range[1])));
    return ret;
  }

  static Packet copy(byte[] window, long start, int length) {
    byte[] ret = new byte[length];
    for (int i = 0; i < length; i++) {
      ret[i] = window[(int) ((start + i) % window.length)];
    }
    return Packet.fromBytes(ret, 0, length);
  }

  @Override
  public String toString() {
    return expression;
  }

  private static final class Parser {

    private final String text;
    private final StringBuilder normalized = new StringBuilder();
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final BitSet wildcards = new BitSet();
    private final Map<String, int[]> captures = new LinkedHashMap<>();
    private int pos;

    private Parser(String text) {
      this.text = text;
    }

    private PacketPattern parse() {
      while (pos < text.length()) {
        if (text.startsWith(CAPTURE_START, pos)) {
          parseCapture();
        } else {
          parseByte();
        }
      }
      return new PacketPattern(normalized.toString(), bytes.toByteArray(), wildcards, captures);
    }

    private void parseCapture() {
      int startPos = pos;
      int nameEnd = text.indexOf('>', pos);
      String name = nameEnd == -1 ? "" : text.substring(pos + CAPTURE_START.length(), nameEnd);
      if (!CAPTURE_NAME.matcher(name).matches()) {
        throw buildException("Invalid capture name", startPos);
      }
      if (captures.containsKey(name)) {
        throw buildException("Capture '" + name + "' is defined more than once", startPos);
      }
      pos = nameEnd + 1;
      normalized.append(CAPTURE_START).append(name).append('>');
      int startByte = bytes.size();
      while (pos < text.length() && text.charAt(pos) != ')') {
        parseByte();
      }
      if (pos == text.length()) {
        throw buildException("Capture '" + name + "' is not closed", startPos);
      }
      if (bytes.size() == startByte) {
        throw buildException("Capture '" + name + "' is empty", startPos);
      }
      pos++;
      normalized.append(')');
      captures.put(name, new int[]{startByte, bytes.size() - startByte});
    }

    private void parseByte() {
      char c = text.charAt(pos);
      if (c == '.') {
        pos++;
        normalized.append('.');
        int count = parseRepetition();
        wildcards.set(bytes.size(), bytes.size() + count);
        bytes.write(new byte[count], 0, count);
      } else if (isHexDigit(c)) {
        if (pos + 1 >= text.length() || !isHexDigit(text.charAt(pos + 1))) {
          throw buildException("Incomplete hex byte", pos);
        }
        bytes.write(parseHexByte(text, pos));
        normalized.append(text.substring(pos, pos + 2).toUpperCase());
        pos += 2;
      } else {
        throw buildException("Unsupported '" + c + "'", pos);
      }
    }

    private int parseRepetition() {
      if (pos == text.length() || text.charAt(pos) != '{') {
        return 1;
      }
      int end = text.indexOf('}', pos);
      String count = end == -1 ? "" : text.substring(pos + 1, end);
      if (!REPETITION.matcher(count).matches()) {
        throw buildException("Unsupported repetition", pos);
      }
      pos = end + 1;
      normalized.append('{').append(count).append('}');
      return Integer.parseInt(count);
    }

    private IllegalArgumentException buildException(String problem, int position) {
      return new IllegalArgumentException(problem + " at position " + position + " of '"
          + abbreviate(text) + "'. Only hex bytes, '.', '.{n}' and '(?<name>...)' with hex bytes"
          + " and '.' inside are supported.");
    }

  }

  static String abbreviate(String text) {
    int maxLength = 80;
    return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
  }

}

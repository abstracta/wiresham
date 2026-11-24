package us.abstracta.wiresham;

import java.util.regex.Pattern;

/**
 * Base logic for parsing the expressions used in the data of flow steps.
 */
abstract class PacketParser {

  private static final Pattern VARIABLE_NAME = Pattern.compile("[a-zA-Z][a-zA-Z0-9]*");
  private static final int MAX_DISPLAY_LENGTH = 80;

  protected final String text;
  protected final StringBuilder normalized = new StringBuilder();
  protected int pos;
  private final String supportedSyntax;

  protected PacketParser(String text, String supportedSyntax) {
    this.text = text;
    this.supportedSyntax = supportedSyntax;
  }

  protected boolean hasNext() {
    return pos < text.length();
  }

  protected boolean nextIs(String prefix) {
    return text.startsWith(prefix, pos);
  }

  protected int parseHexByte() {
    if (!isHexDigit(pos)) {
      throw buildException("Unsupported '" + text.charAt(pos) + "'", pos);
    }
    if (!isHexDigit(pos + 1)) {
      throw buildException("Incomplete hex byte", pos);
    }
    String hexByte = text.substring(pos, pos + 2).toUpperCase();
    normalized.append(hexByte);
    pos += 2;
    return Integer.parseInt(hexByte, 16);
  }

  // Character.digit is not used since it also accepts digits which are not ASCII
  private boolean isHexDigit(int position) {
    if (position >= text.length()) {
      return false;
    }
    char c = text.charAt(position);
    return c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
  }

  protected String parseVariableName(String prefix, char suffix) {
    int end = text.indexOf(suffix, pos);
    String name = end == -1 ? "" : text.substring(pos + prefix.length(), end);
    if (!VARIABLE_NAME.matcher(name).matches()) {
      throw buildException("Invalid name", pos);
    }
    normalized.append(prefix).append(name).append(suffix);
    pos = end + 1;
    return name;
  }

  protected IllegalArgumentException buildException(String problem, int position) {
    String displayText = text.length() <= MAX_DISPLAY_LENGTH ? text
        : text.substring(0, MAX_DISPLAY_LENGTH) + "...";
    return new IllegalArgumentException(problem + " at position " + position + " of '"
        + displayText + "'. " + supportedSyntax);
  }

}

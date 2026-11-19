package us.abstracta.wiresham;

import java.util.regex.Pattern;

/**
 * Parsing utilities shared by the expressions used in the data of flow steps.
 */
final class PacketExpressions {

  static final Pattern CAPTURE_NAME = Pattern.compile("[a-zA-Z][a-zA-Z0-9]*");
  private static final int MAX_DISPLAY_LENGTH = 80;

  private PacketExpressions() {
  }

  static boolean isHexDigit(char c) {
    return Character.digit(c, 16) != -1;
  }

  static boolean isHexByte(String text, int pos) {
    return pos + 1 < text.length() && isHexDigit(text.charAt(pos))
        && isHexDigit(text.charAt(pos + 1));
  }

  static int parseHexByte(String text, int pos) {
    return (Character.digit(text.charAt(pos), 16) << 4)
        + Character.digit(text.charAt(pos + 1), 16);
  }

  static IllegalArgumentException buildException(String problem, int position, String text,
      String supportedSyntax) {
    String displayText = text.length() <= MAX_DISPLAY_LENGTH ? text
        : text.substring(0, MAX_DISPLAY_LENGTH) + "...";
    return new IllegalArgumentException(problem + " at position " + position + " of '"
        + displayText + "'. " + supportedSyntax);
  }

}

package us.abstracta.wiresham;

import com.google.common.io.BaseEncoding;
import java.util.Arrays;

/**
 * Packet exchanged between the server and the client.
 */
public class Packet {

  private final byte[] bytes;

  private Packet(byte[] bytes) {
    this.bytes = bytes;
  }

  public String toString() {
    return BaseEncoding.base16().encode(bytes);
  }

  public static Packet fromHexDump(String hexDump) {
    return new Packet(BaseEncoding.base16().decode(hexDump.toUpperCase()));
  }

  public static Packet fromBytes(byte[] bytes, int offset, int length) {
    return new Packet(Arrays.copyOfRange(bytes, offset, offset + length));
  }

  public byte[] getBytes() {
    return bytes;
  }

}

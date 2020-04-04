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

  public static Packet fromHexDump(String hexDump) {
    return new Packet(BaseEncoding.base16().decode(hexDump.toUpperCase()));
  }

  public static Packet fromBytes(byte[] bytes, int offset, int length) {
    return new Packet(Arrays.copyOfRange(bytes, offset, offset + length));
  }

  public byte[] getBytes() {
    return bytes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Packet packet = (Packet) o;
    return Arrays.equals(bytes, packet.bytes);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(bytes);
  }

  public String toString() {
    return BaseEncoding.base16().encode(bytes);
  }

}

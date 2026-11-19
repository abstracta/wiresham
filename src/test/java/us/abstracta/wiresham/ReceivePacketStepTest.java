package us.abstracta.wiresham;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.io.BaseEncoding;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

public class ReceivePacketStepTest {

  private static final int READ_BUFFER_SIZE = 8;

  @Test
  public void shouldLeaveFollowingBytesWhenSeveralPacketsAreReceivedTogether() throws Exception {
    FlowConnection connection = buildConnection(READ_BUFFER_SIZE, "HelloWorld!");
    new ReceivePacketStep(hex("Hello")).process(connection);
    new ReceivePacketStep(hex("World")).process(connection);
    assertThat(readPending(connection)).isEqualTo("!");
  }

  private static String hex(String text) {
    return BaseEncoding.base16().encode(text.getBytes(StandardCharsets.ISO_8859_1));
  }

  private static FlowConnection buildConnection(int readBufferSize, String... receivedChunks)
      throws IOException {
    byte[][] chunks = Arrays.stream(receivedChunks)
        .map(c -> c.getBytes(StandardCharsets.ISO_8859_1))
        .toArray(byte[][]::new);
    return buildConnection(readBufferSize, chunks);
  }

  private static FlowConnection buildConnection(int readBufferSize, byte[]... receivedChunks)
      throws IOException {
    Socket socket = mock(Socket.class);
    when(socket.getInputStream()).thenReturn(new ChunkedInputStream(receivedChunks));
    return new FlowConnection(socket, readBufferSize);
  }

  /*
   Returns the bytes in the same chunks they would be received from the network, to check that
   the processing does not depend on how the bytes are split.
   */
  private static class ChunkedInputStream extends InputStream {

    private final byte[][] chunks;
    private int chunkIndex;
    private int chunkPos;

    private ChunkedInputStream(byte[][] chunks) {
      this.chunks = chunks;
    }

    @Override
    public int read() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int read(byte[] buffer, int offset, int length) {
      if (length == 0) {
        throw new IllegalStateException("Reading without space in the buffer");
      }
      while (chunkIndex < chunks.length && chunkPos == chunks[chunkIndex].length) {
        chunkIndex++;
        chunkPos = 0;
      }
      if (chunkIndex == chunks.length) {
        return -1;
      }
      int count = Math.min(length, chunks[chunkIndex].length - chunkPos);
      System.arraycopy(chunks[chunkIndex], chunkPos, buffer, offset, count);
      chunkPos += count;
      return count;
    }

  }

  private static String readPending(FlowConnection connection) throws IOException {
    return new String(readPendingBytes(connection), StandardCharsets.ISO_8859_1);
  }

  private static byte[] readPendingBytes(FlowConnection connection) throws IOException {
    ByteArrayOutputStream ret = new ByteArrayOutputStream();
    try {
      while (true) {
        ByteBuffer buffer = connection.read();
        ret.write(buffer.get());
        if (!buffer.hasRemaining()) {
          buffer.clear();
          buffer.limit(0);
        }
      }
    } catch (ConnectionClosedException e) {
      return ret.toByteArray();
    }
  }

  @Test
  public void shouldFindPacketWhenItIsSplitInSeveralReads() throws Exception {
    FlowConnection connection = buildConnection(READ_BUFFER_SIZE, "He", "l", "loWor", "ld");
    new ReceivePacketStep(hex("HelloWorld")).process(connection);
    assertThat(readPending(connection)).isEmpty();
  }

  @Test
  public void shouldFindPacketWhenPreviousUnexpectedBytesAreBiggerThanReadBuffer()
      throws Exception {
    FlowConnection connection = buildConnection(READ_BUFFER_SIZE,
        "0123456789012345678901234567890123456789Hello!");
    new ReceivePacketStep(hex("Hello")).process(connection);
    assertThat(readPending(connection)).isEqualTo("!");
  }

  @Test
  public void shouldFindPacketWhenItHasSameSizeAsReadBuffer() throws Exception {
    FlowConnection connection = buildConnection(READ_BUFFER_SIZE, "12345678", "12345678", "9");
    new ReceivePacketStep(hex("12345678")).process(connection);
    new ReceivePacketStep(hex("12345678")).process(connection);
    assertThat(readPending(connection)).isEqualTo("9");
  }

  @Test
  public void shouldFindPacketWhenItIsBiggerThanReadBuffer() throws Exception {
    FlowConnection connection = buildConnection(READ_BUFFER_SIZE, "xx0123456789", "ABCDEFyy");
    new ReceivePacketStep(hex("0123456789ABCDEF")).process(connection);
    assertThat(readPending(connection)).isEqualTo("yy");
  }

  @Test
  public void shouldFindPacketWhenItStartsInsidePreviousPartialMatch() throws Exception {
    FlowConnection connection = buildConnection(READ_BUFFER_SIZE, "abababc", "d");
    new ReceivePacketStep(hex("ababc")).process(connection);
    assertThat(readPending(connection)).isEqualTo("d");
  }

  @Test
  public void shouldGetFirstMatchWhenPatternMatchesAtSeveralPositions() throws Exception {
    FlowConnection connection = buildConnection(READ_BUFFER_SIZE, "id=1;id=2;");
    FlowContext context = new FlowContext();
    new ReceivePacketStep(hex("id=") + "(?<id>.)" + hex(";")).process(connection, context);
    assertThat(context.getCaptured("id")).isEqualTo(Packet.fromHexDump(hex("1")));
    assertThat(readPending(connection)).isEqualTo("id=2;");
  }

  @Test
  public void shouldReportPendingBytesWhenConnectionIsClosedBeforeReceivingPacket()
      throws Exception {
    FlowConnection connection = buildConnection(READ_BUFFER_SIZE, "0123456789Hel");
    assertThatThrownBy(() -> new ReceivePacketStep(hex("Hello")).process(connection))
        .isInstanceOfSatisfying(ConnectionClosedException.class,
            e -> assertThat(e.getDiscardedPacket()).isEqualTo(Packet.fromHexDump(hex("89Hel"))));
  }

  @Test
  public void shouldReportReceivedBytesWhenConnectionIsClosedBeforeReceivingPacketSize()
      throws Exception {
    FlowConnection connection = buildConnection(READ_BUFFER_SIZE, "Hel");
    assertThatThrownBy(() -> new ReceivePacketStep(hex("Hello")).process(connection))
        .isInstanceOfSatisfying(ConnectionClosedException.class,
            e -> assertThat(e.getDiscardedPacket()).isEqualTo(Packet.fromHexDump(hex("Hel"))));
  }

  @Test
  public void shouldNotReadFromConnectionWhenPacketIsEmpty() throws Exception {
    FlowConnection connection = buildConnection(READ_BUFFER_SIZE, "Hello");
    new ReceivePacketStep("").process(connection);
    assertThat(readPending(connection)).isEqualTo("Hello");
  }

  @Test
  public void shouldFindPacketInReasonableTimeWhenContentIsRepetitive() {
    int size = 64 * 1024;
    byte[] expected = new byte[size];
    expected[size - 1] = 1;
    byte[] received = new byte[size + 200];
    received[received.length - 1] = 1;
    assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
      FlowConnection connection = buildConnection(2048, received);
      new ReceivePacketStep(BaseEncoding.base16().encode(expected)).process(connection);
      assertThat(readPendingBytes(connection)).isEmpty();
    });
  }

  /*
   Compares the step against a simple search over all the received bytes, using random packets
   (with and without wildcards and captures), received bytes, splits of the received bytes and
   read buffer sizes. A small set of byte values is used to get frequent partial matches. The
   seed is fixed, so failures can be reproduced.
   */
  @Test
  public void shouldBehaveAsSimpleSearchWhenProcessingRandomPacketsAndReceivedBytes()
      throws Exception {
    Random random = new Random(20261119);
    for (int i = 0; i < 20000; i++) {
      RandomCase testCase = new RandomCase(random);
      try {
        testCase.check();
      } catch (AssertionError | Exception e) {
        throw new AssertionError("Failed case " + i + ": " + testCase, e);
      }
    }
  }

  private static class RandomCase {

    private static final int BYTE_VALUES = 3;

    private final byte[] expected;
    private final boolean[] wildcards;
    private final int captureStart;
    private final int captureLength;
    private final byte[] received;
    private final byte[][] chunks;
    private final int readBufferSize;

    private RandomCase(Random random) {
      expected = randomBytes(random, 1 + random.nextInt(6));
      wildcards = new boolean[expected.length];
      boolean useWildcards = random.nextBoolean();
      for (int i = 0; i < wildcards.length; i++) {
        wildcards[i] = useWildcards && random.nextInt(3) == 0;
      }
      captureLength = random.nextBoolean() ? 1 + random.nextInt(expected.length) : 0;
      captureStart = random.nextInt(expected.length - captureLength + 1);
      received = buildReceived(random);
      chunks = split(random, received);
      readBufferSize = 1 + random.nextInt(9);
    }

    private static byte[] randomBytes(Random random, int length) {
      byte[] ret = new byte[length];
      for (int i = 0; i < length; i++) {
        ret[i] = (byte) random.nextInt(BYTE_VALUES);
      }
      return ret;
    }

    private byte[] buildReceived(Random random) {
      ByteArrayOutputStream ret = new ByteArrayOutputStream();
      byte[] prefix = randomBytes(random, random.nextInt(30));
      ret.write(prefix, 0, prefix.length);
      // only some cases include the expected packet for sure, others may include it by chance
      if (random.nextInt(4) != 0) {
        ret.write(expected, 0, expected.length);
      }
      byte[] suffix = randomBytes(random, random.nextInt(10));
      ret.write(suffix, 0, suffix.length);
      return ret.toByteArray();
    }

    private static byte[][] split(Random random, byte[] bytes) {
      byte[][] ret = new byte[bytes.length + 1][];
      int count = 0;
      int pos = 0;
      while (pos < bytes.length) {
        // empty chunks are not generated since a socket never returns 0 bytes
        int length = Math.min(1 + random.nextInt(8), bytes.length - pos);
        ret[count++] = Arrays.copyOfRange(bytes, pos, pos + length);
        pos += length;
      }
      return Arrays.copyOf(ret, count);
    }

    private void check() throws IOException {
      FlowConnection connection = buildConnection(readBufferSize, chunks);
      FlowContext context = new FlowContext();
      ReceivePacketStep step = new ReceivePacketStep(buildExpression());
      int matchPos = findFirstMatch();
      if (matchPos == -1) {
        assertThatThrownBy(() -> step.process(connection, context))
            .isInstanceOf(ConnectionClosedException.class);
        return;
      }
      step.process(connection, context);
      int matchEnd = matchPos + expected.length;
      assertThat(readPendingBytes(connection))
          .isEqualTo(Arrays.copyOfRange(received, matchEnd, received.length));
      if (captureLength > 0) {
        int start = matchPos + captureStart;
        assertThat(context.getCaptured("value").getBytes())
            .isEqualTo(Arrays.copyOfRange(received, start, start + captureLength));
      }
    }

    private String buildExpression() {
      StringBuilder ret = new StringBuilder();
      for (int i = 0; i < expected.length; i++) {
        if (captureLength > 0 && i == captureStart) {
          ret.append("(?<value>");
        }
        ret.append(wildcards[i] ? "." : String.format("%02X", expected[i]));
        if (captureLength > 0 && i == captureStart + captureLength - 1) {
          ret.append(")");
        }
      }
      return ret.toString();
    }

    private int findFirstMatch() {
      for (int pos = 0; pos + expected.length <= received.length; pos++) {
        boolean matches = true;
        for (int i = 0; i < expected.length && matches; i++) {
          matches = wildcards[i] || received[pos + i] == expected[i];
        }
        if (matches) {
          return pos;
        }
      }
      return -1;
    }

    @Override
    public String toString() {
      return "expected=" + buildExpression()
          + ", received=" + BaseEncoding.base16().encode(received)
          + ", chunkSizes=" + Arrays.toString(Arrays.stream(chunks).mapToInt(c -> c.length)
          .toArray())
          + ", readBufferSize=" + readBufferSize;
    }

  }

}

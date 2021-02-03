package us.abstracta.wiresham;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step in a flow which waits to receive a packet.
 */
public class ReceivePacketStep extends PacketStep {

  private static final Logger LOG = LoggerFactory.getLogger(ReceivePacketStep.class);

  public ReceivePacketStep() {
  }

  public ReceivePacketStep(String hexDump) {
    super(hexDump);
  }

  @Override
  public void process(ConnectionFlowDriver connectionDriver) throws IOException {
    ByteBuffer dataBuffer = ByteBuffer.wrap(data.getBytes());
    LOG.debug("Waiting for {}", data);
    boolean receivedExpected = false;
    while (!receivedExpected) {
      ByteBuffer readBuffer = connectionDriver.read();
      int foundPos = findDataInBuffer(dataBuffer, readBuffer);
      if (foundPos != -1) {
        if (foundPos != 0 && LOG.isTraceEnabled()) {
          LOG.trace("ignoring received {}  before expected",
              Packet.fromBytes(readBuffer.array(), 0, foundPos));
        }
        LOG.debug("received expected {}", data);
        readBuffer.compact();
        readBuffer.flip();
        receivedExpected = true;
      } else if (readBuffer.limit() == readBuffer.capacity()) {
        int markedPosition = getMarkedPosition(readBuffer);
        if (markedPosition != -1) {
          int relativePos = readBuffer.position() - markedPosition + 1;
          readBuffer.compact();
          readBuffer.flip();
          readBuffer.position(1);
          readBuffer.mark();
          readBuffer.position(relativePos);
        } else {
          if (LOG.isTraceEnabled()) {
            LOG.trace("ignoring received {} while waiting for {}",
                Packet.fromBytes(readBuffer.array(), 0, readBuffer.limit()), data);
          }
          readBuffer.clear();
          readBuffer.limit(0);
        }
      }
    }
  }

  private int findDataInBuffer(ByteBuffer dataBuffer, ByteBuffer readBuffer) {
    if (LOG.isTraceEnabled()) {
      LOG.trace("Searching for {} from {} in {} from {}", data, dataBuffer.position(),
          Packet.fromBytes(readBuffer.array(), 0, readBuffer.limit()), readBuffer.position());
    }
    while (dataBuffer.hasRemaining() && readBuffer.hasRemaining()) {
      if (readBuffer.get() != dataBuffer.get()) {
        if (dataBuffer.position() != 1) {
          LOG.trace("Finish match at {} and {}", readBuffer.position() - 1,
              dataBuffer.position() - 1);
          readBuffer.reset();
        }
        dataBuffer.rewind();
      } else if (dataBuffer.position() == 1) {
        readBuffer.mark();
        LOG.trace("Start match at {}", readBuffer.position() - 1);
      }
    }
    if (!dataBuffer.hasRemaining()) {
      return getMarkedPosition(readBuffer) - 1;
    } else {
      return -1;
    }
  }

  private int getMarkedPosition(ByteBuffer readBuffer) {
    int position = readBuffer.position();
    readBuffer.reset();
    int markedPosition = readBuffer.position();
    readBuffer.position(position);
    return markedPosition;
  }

  @Override
  public String toString() {
    return "client: " + data;
  }

}

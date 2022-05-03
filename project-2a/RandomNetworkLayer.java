// =============================================================================
// IMPORTS

import java.util.Iterator;
import java.util.Queue;
import java.util.Random;
// =============================================================================

// =============================================================================
/**
 * @file RandomNetworkLayer.java
 * @author Scott F. Kaplan (sfkaplan@cs.amherst.edu)
 * @date April 2022
 *     <p>A network layer that perform routing via random link selection.
 */
public class RandomNetworkLayer extends NetworkLayer {
  // =============================================================================

  // =========================================================================
  // PUBLIC METHODS
  // =========================================================================

  // =========================================================================
  /** Default constructor. Set up the random number generator. */
  public RandomNetworkLayer() {

    random = new Random();
  } // RandomNetworkLayer ()
  // =========================================================================

  // =========================================================================
  /**
   * Create a single packet containing the given data, with header that marks the source and
   * destination hosts.
   *
   * @param destination The address to which this packet is sent.
   * @param data The data to send.
   * @return the sequence of bytes that comprises the packet.
   */
  protected byte[] createPacket(int destination, byte[] data) {

    // COMPLETE ME
    byte[] packet = new byte[bytesPerHeader + data.length];
    byte[] packetLengthBytes = intToBytes(packet.length);
    byte[] sourceBytes = intToBytes(address);
    byte[] destinationBytes = intToBytes(destination);
    copyInto(packet, lengthOffset, packetLengthBytes);
    copyInto(packet, sourceOffset, sourceBytes);
    copyInto(packet, destinationOffset, destinationBytes);
    copyInto(packet, bytesPerHeader, data);
    if (debug) {
      System.out.println("RandomNetworkLayer.createPacket(): " + bytesToString(packet));
    }
    return packet;
  } // createPacket ()
  // =========================================================================

  // =========================================================================
  /**
   * Randomly choose the link through which to send a packet given its destination.
   *
   * @param destination The address to which this packet is being sent.
   */
  protected DataLinkLayer route(int destination) {

    // COMPLETE ME
    int index = random.nextInt(dataLinkLayers.size());
    Iterator<DataLinkLayer> it = dataLinkLayers.values().iterator();
    for (int i = 0; i < index; i++) {
      it.next();
    }
    DataLinkLayer dataLink = it.next();
    if (debug) {
      System.out.println("RandomNetworkLayer.route(): " + dataLink);
    }
    return dataLink;
  } // route ()
  // =========================================================================

  // =========================================================================
  /**
   * Examine a buffer to see if it's data can be extracted as a packet; if so, do it, and return the
   * packet whole.
   *
   * @param buffer The receive-buffer to be examined.
   * @return the packet extracted packet if a whole one is present in the buffer; <code>null</code>
   *     otherwise.
   */
  protected byte[] extractPacket(Queue<Byte> buffer) {

    // COMPLETE ME
    if (buffer.size() >= bytesPerHeader) {
      byte[] packetLengthBytes = new byte[Integer.BYTES];
      Iterator<Byte> it = buffer.iterator();
      for (int i = lengthOffset; i < sourceOffset; i++) {
        packetLengthBytes[i] = it.next();
      }
      int packetLength = bytesToInt(packetLengthBytes);

      if (buffer.size() >= packetLength) {
        byte[] packet = new byte[packetLength];
        for (int i = 0; i < packetLength; i++) {
          packet[i] = buffer.remove();
        }
        if (debug) {
          System.out.println("RandomNetworkLayer.extractPacket(): " + bytesToString(packet));
        }
        return packet;
      }
    }
    return null;
  } // extractPacket ()
  // =========================================================================

  // =========================================================================
  /**
   * Given a received packet, process it. If the destination for the packet is this host, then
   * deliver its data to the client layer. If the destination is another host, route and send the
   * packet.
   *
   * @param packet The received packet to process.
   * @see createPacket
   */
  protected void processPacket(byte[] packet) {

    // COMPLETE ME
    byte[] destinationBytes = new byte[Integer.BYTES];
    copyFrom(destinationBytes, packet, destinationOffset);
    int destination = bytesToInt(destinationBytes);
    if (destination == address) {
      byte[] data = new byte[packet.length - bytesPerHeader];
      copyFrom(data, packet, bytesPerHeader);
      if (debug) {
        System.out.println("RandomNetworkLayer.processPacket(): " + bytesToString(data));
      }
      client.receive(data);
    } else {
      DataLinkLayer dataLink = route(destination);
      dataLink.send(packet);
    }
  } // processPacket ()
  // =========================================================================

  // =========================================================================
  // INSTANCE DATA MEMBERS

  /** The random source for selecting routes. */
  private Random random;
  // =========================================================================

  // =========================================================================
  // CLASS DATA MEMBERS

  /** The offset into the header for the length. */
  public static final int lengthOffset = 0;

  /** The offset into the header for the source address. */
  public static final int sourceOffset = lengthOffset + Integer.BYTES;

  /** The offset into the header for the destination address. */
  public static final int destinationOffset = sourceOffset + Integer.BYTES;

  /** How many total bytes per header. */
  public static final int bytesPerHeader = destinationOffset + Integer.BYTES;

  /** Whether to emit debugging information. */
  public static final boolean debug = false;
  // =========================================================================

  // =============================================================================
} // class RandomNetworkLayer
// =============================================================================

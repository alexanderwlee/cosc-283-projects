// =============================================================================
// IMPORTS

import java.util.LinkedList;
// =============================================================================
import java.util.Queue;

// =============================================================================
/**
 * A single host, comprising a single network stack, connected to a medium.
 *
 * @file Host.java
 * @author Scott F. Kaplan (sfkaplan@cs.amherst.edu)
 * @date April 2022
 */
public class Host implements Runnable {
  // =============================================================================

  // =========================================================================
  // PUBLIC METHODS
  // =========================================================================

  // =========================================================================
  /**
   * Create a new host, attaching it to a network layer. The data link layers will be attached to
   * the host's network layer later, via calls to <code>attach()</code>.
   *
   * @param hostname The name of this host, used as a unique ID.
   * @param networkLayerType The type of network layer to use.
   * @see attach()
   */
  public Host(String hostname, String networkLayerType) {

    this.hostname = hostname;
    this.networkLayer = NetworkLayer.create(networkLayerType, this);
    this.buffer = new LinkedList<Byte>();
  } // Host ()
  // =========================================================================

  // =========================================================================
  /** */
  public void attach(DataLinkLayer dataLinkLayer, String remoteHostname) {

    networkLayer.attach(dataLinkLayer, remoteHostname);
  } // attach ()
  // =========================================================================

  // =========================================================================
  /** Begin this host as an independent thread. The event loop in its network layer is started. */
  @Override
  public void run() {

    networkLayer.go();
  } // run ()
  // =========================================================================

  // =========================================================================
  /** End the event loop in the network layer, thus ending this host's thread. */
  public void stop() {

    networkLayer.stop();
  } // stop ()
  // =========================================================================

  // =========================================================================
  /**
   * Send a sequence of bytes.
   *
   * @param destination The name of the host for which this data is destined.
   * @param data The sequence of bytes to send.
   */
  public void send(String destination, byte[] data) {

    networkLayer.send(destination, data);
  } // send ()
  // =========================================================================

  // =========================================================================
  /**
   * Receive bytes from the lower layer. Buffer those until they are retrieved.
   *
   * @param data The data received and to be buffered.
   */
  public void receive(byte[] data) {

    // Add the bytes into the buffer.
    for (int i = 0; i < data.length; i += 1) {
      buffer.add(data[i]);
    }
  } // receive ()
  // =========================================================================

  // =========================================================================
  /**
   * Retrieve and return any bytes that have been received and buffered.
   *
   * @return the buffered bytes.
   */
  public byte[] retrieve() {

    // Remove the bytes from the buffer, adding them to a newly formed array
    // to be returned.
    byte[] received = new byte[buffer.size()];
    for (int i = 0; i < received.length; i += 1) {
      received[i] = buffer.remove();
    }

    return received;
  } // retrieve ()
  // =========================================================================

  // =========================================================================
  /**
   * Provide the name of this host.
   *
   * @return The hostname.
   */
  public String getHostname() {

    return hostname;
  } // getHostname ()
  // =========================================================================

  // =========================================================================
  // DATA MEMBERS

  /** The name of this host. */
  private String hostname;

  /** The data link layer in this host's network stack. */
  private NetworkLayer networkLayer;

  /** The buffered bytes received via the network stack. */
  private Queue<Byte> buffer;

  /** Whether to emit debugging information. */
  private static final boolean debug = false;
  // =========================================================================

  // =============================================================================
} // class Host
// =============================================================================

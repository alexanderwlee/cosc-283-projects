// =============================================================================
// IMPORTS

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
// =============================================================================

// =============================================================================
/**
 * The entry point of the simulator. Based on command-line arguments, it creates the layers to
 * connect multiple simulated hosts, and then transmits data (read from a file) from one host to
 * another.
 *
 * @file Simulator.java
 * @author Scott F. Kaplan (sfkaplan@cs.amherst.edu)
 * @date April 2022, August 2017, September 2004
 */
public class Simulator {
  // =============================================================================

  // =========================================================================
  /**
   * The entry point. Interpret the command-line arguments, aborting if they are invalid. Set up the
   * layers and start the simulation.
   *
   * @param args The command-line arguments.
   */
  public static void main(String[] args) {

    // Check the number of arguments passed.
    if (args.length != 7) {

      System.err.print(
          "Usage: java Simulator <medium type>\n"
              + "                      <data link layer type>\n"
              + "                      <network layer type>\n"
              + "                      <links file>\n"
              + "                      <source host>\n"
              + "                      <destination host>\n"
              + "                      <transmission data file>\n");
      System.exit(1);
    }

    // Assign names to the arguments.
    String mediumType = args[0];
    String dataLinkLayerType = args[1];
    String networkLayerType = args[2];
    String linksPath = args[3];
    String sourceHost = args[4];
    String destinationHost = args[5];
    String transmissionPath = args[6];

    // Create the network of hosts and described by the links file.
    Map<String, Host> hosts = construct(linksPath, mediumType, dataLinkLayerType, networkLayerType);

    // Read the contents of the data to be transmitted into a buffer.
    byte[] dataToTransmit = readFile(transmissionPath);

    // Perform the simulation!
    simulate(hosts, sourceHost, destinationHost, dataToTransmit);
    System.exit(0);
  } // main
  // =========================================================================

  // =========================================================================
  /**
   * Read in a file of links -- connections between hosts. From that, create the hosts (including
   * all of their layers) and the links (and their related layers within the hosts).
   *
   * @param linksPath The pathname of the file containing a list of links between hosts.
   * @param mediumType The kind of medium used to connect each pair of hosts.
   * @param dataLinkLayerType The kind of data link layer to manage each link.
   * @param networkLayerType The kind of network layer to manage this host.
   * @return a map of hostnames to constructed <code>Host</code> objects that comprise the network
   *     described by the links file.
   * @throws RuntimeException if the file cannot be found or contains errors.
   */
  protected static Map<String, Host> construct(
      String linksPath, String mediumType, String dataLinkLayerType, String networkLayerType) {

    // Open the file to create a scanner to read the records.
    Scanner s = null;
    try {
      s = new Scanner(new File(linksPath));
    } catch (FileNotFoundException e) {
      throw new RuntimeException("No such file: " + linksPath);
    }

    // Create the map of hostnames to Hosts.
    Map<String, Host> hosts = new HashMap<String, Host>();

    // Read in the links as triplets of "host host weight", updating the
    // collection of hosts and links with each.
    int linkCount = 0;
    while (s.hasNext()) {

      linkCount += 1;
      String hostnameA = s.next();
      if (!s.hasNext()) {
        throw new RuntimeException("Incomplete link record #" + linkCount);
      }
      String hostnameB = s.next();
      if (!s.hasNextInt()) {
        throw new RuntimeException("Incomplete link record #" + linkCount);
      }
      int weight = s.nextInt();

      // Grab the hosts.  If either hostname doesn't exist, create it.
      if (!hosts.containsKey(hostnameA)) {
        hosts.put(hostnameA, new Host(hostnameA, networkLayerType));
      }
      if (!hosts.containsKey(hostnameB)) {
        hosts.put(hostnameB, new Host(hostnameB, networkLayerType));
      }
      Host hostA = hosts.get(hostnameA);
      Host hostB = hosts.get(hostnameB);

      // Create a new medium for the link...
      Medium medium = Medium.create(mediumType);

      // ...and create the physical layers and data link layers for each
      // host, connecting them to each other, and registering them with
      // the medium itself.
      PhysicalLayer physicalLayerA = new PhysicalLayer(medium);
      PhysicalLayer physicalLayerB = new PhysicalLayer(medium);
      DataLinkLayer dataLinkLayerA = DataLinkLayer.create(dataLinkLayerType, physicalLayerA);
      DataLinkLayer dataLinkLayerB = DataLinkLayer.create(dataLinkLayerType, physicalLayerB);

      // Attach these data link layers to each host, registering that the
      // link leads to the other host.
      hostA.attach(dataLinkLayerA, hostnameB);
      hostB.attach(dataLinkLayerB, hostnameA);

      if (debug) {
        System.out.printf("Simulator.construct(): Attached %s and %s\n", hostnameA, hostnameB);
      }
    }

    return hosts;
  } // construct ()
  // =========================================================================

  // =========================================================================
  /**
   * Read the whole contents of a given file, returning it in a byte array.
   *
   * @param path The pathname of the file whose data to read.
   * @return a buffer contain the complete contents of the file.
   */
  private static byte[] readFile(String path) {

    // Does the path name a readable file?
    File file = new File(path);
    if (!file.canRead()) {
      throw new RuntimeException(path + " is not a readable file");
    }

    // Read the entire file.
    if (file.length() > Integer.MAX_VALUE) {
      throw new RuntimeException(path + " is too large a file");
    }
    int length = (int) file.length();
    byte[] buffer = new byte[length];
    try {
      FileInputStream input = new FileInputStream(file);
      input.read(buffer);
    } catch (FileNotFoundException e) {
      throw new RuntimeException("Unexpected file-not-found for " + path);
    } catch (IOException e) {
      throw new RuntimeException("Unexpected failure in reading " + path);
    }

    return buffer;
  } // readFile()
  // =========================================================================

  // =========================================================================
  /**
   * Perform the simulation, having the sender transmit the given data to the receiver. Verify that
   * the receiver fully receives the complete and correct data.
   *
   * @param hosts The map of hostnames to Hosts in the network.
   * @param senderName The sending hostname.
   * @param receiverName The receiving host.
   * @param data The data to be sent.
   * @throws RuntimeException if the sender or receiver hostnames don't exist.
   */
  private static void simulate(
      Map<String, Host> hosts, String senderName, String receiverName, byte[] data) {

    // Create the hosts as independent threads to perform communications.
    for (Host host : hosts.values()) {
      new Thread(host).start();
    }

    // Provide the data to send to the sender.
    Host sender = hosts.get(senderName);
    if (sender == null) {
      throw new RuntimeException("Simulator.simulate(): Invalid sender hostname: " + senderName);
    }
    sender.send(receiverName, data);

    // Wait until we think the receiver has received.
    Host receiver = hosts.get(receiverName);
    if (receiver == null) {
      throw new RuntimeException(
          "Simulator.simulate(): Invalid recevier hostname: " + receiverName);
    }
    System.out.printf("Press enter to receive: ");
    try {
      System.in.read();
    } catch (IOException e) {
    }
    byte[] received = receiver.retrieve();

    System.out.println("Transmission received:  " + new String(received));
    if (Arrays.equals(data, received)) {
      System.out.println("Transmission match");
    } else {
      System.out.println("Transmission mismatch");
      System.out.printf("\tsent length = %d\treceived length = %d\n", data.length, received.length);
    }

    receiver.stop();
    sender.stop();
  } // simulate()
  // =========================================================================

  // =========================================================================
  // CLASS DATA MEMBERS

  /** Whether to emit debugging information. */
  public static final boolean debug = true;
  // =========================================================================

  // =============================================================================
} // class Simulator
// =============================================================================

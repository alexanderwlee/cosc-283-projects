// =============================================================================
// IMPORTS

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
// =============================================================================



// =============================================================================
/**
 * @file   NetworkLayer.java
 * @author Scott F. Kaplan (sfkaplan@cs.amherst.edu)
 * @date   April 2022
 *
 * A network layer accepts a string of bytes, divides it into packets that
 * contain headers addressing each packet to a destination host, and then
 * chooses a data link layer to which to pass the packet for transmission to a
 * neighboring host.
 *
 * When it receives a packet, it examines the header to determine whether this
 * host is the destination.  If so, it delivers the packet's data to the client;
 * if not, it forwards the packet towards its destination.
 */
public abstract class NetworkLayer {
// =============================================================================



    // =========================================================================
    // PUBLIC METHODS
    // =========================================================================



    // =========================================================================
    /**
     * Create the requested network layer type and return it.
     *
     * @param  type          The subclass of which to create an instance.
     * @param  host          The host for which this layer is communicating.
     * @return The newly created network layer.
     * @throws RuntimeException if the given type is not a valid subclass.
     */
    public static NetworkLayer create (String type, Host host) {

	// Look up the class by name.
	String   className         = type + "NetworkLayer";
	Class<?> networkLayerClass = null;
	try {
	    networkLayerClass = Class.forName(className);
	} catch (ClassNotFoundException e) {
	    throw new RuntimeException("Unknown network layer subclass " +
				       className);
	}

	// Make one of these objects, and then see if it really is a
	// NetworkLayer subclass.
	Object o = null;
	try {
	    o = networkLayerClass.getDeclaredConstructor().newInstance();
	} catch (NoSuchMethodException e) {
	    throw new RuntimeException("Could not call constructor for " + className);
	} catch (InstantiationException e) {
	    throw new RuntimeException("Could not instantiate " + className);
	} catch (IllegalAccessException e) {
	    throw new RuntimeException("Could not access " + className);
	} catch (InvocationTargetException e) {
	    throw new RuntimeException("Could not invoke target on " + className);
	}
	NetworkLayer networkLayer = null;
	try {
	    networkLayer = (NetworkLayer)o;
	} catch (ClassCastException e) {
	    throw new RuntimeException(className +
				       " is not a subclass of NetworkLayer");
	}

	// Register this new network layer with its host.  The data link layers
	// will be connected later through calls to `attach()`.
	networkLayer.register(host);
	return networkLayer;

    } // create ()
    // =========================================================================



    // =========================================================================
    /**
     * Default constructor.  Set up the buffers for sending and receiving.
     */
    public NetworkLayer () {

	// Initialize an empty collection of data link layers.
	dataLinkLayers = new HashMap<Integer, DataLinkLayer>();
	receiveBuffers = new HashMap<DataLinkLayer, Queue<Byte>>();

    } // NetworkLayer ()
    // =========================================================================
    



    // =========================================================================
    /**
     * Allow a host to register as the client of this network layer.
     *
     * @param client The host client of this network layer.
     */
    public void register (Host client) {

	// Is there already a client registered?
	if (this.client != null) {
	    throw new RuntimeException("Attempt to double-register");
	}

	// Hold a pointer to the client.
	this.client  = client;

	// Determine my address based on the hostname.
	this.address = client.getHostname().hashCode();

    } // register ()
    // =========================================================================



    // =========================================================================
    /**
     * Given a link that connects this host to another, attach its data link
     * layer to this network layer.
     *
     * @param dataLinkLayer The data link layer controlling the link.
     * @param hostname      The name of the remote host to which this link is
     *                      connected.
     * @throws RuntimeException if the hostname or the data link layer is a
     *                          duplicate (or <code>null</code>).
     */
    public void attach (DataLinkLayer dataLinkLayer, String remoteHostname) {

	// Make sure the arguments aren't null.
	if (remoteHostname == null || dataLinkLayer == null) {
	    throw new RuntimeException("Cannot attach with null arguments");
	}

	// Determine the address of the host at the other end of this link...
	int remoteAddress = remoteHostname.hashCode();

	// .. and add it to our collection data links, ensuring that it's not a
	// duplicate.
	if (dataLinkLayers.containsKey(remoteAddress)) {
	    throw new RuntimeException("Cannot attach duplicate host " +
				       remoteHostname);
	}
	if (dataLinkLayers.containsValue(dataLinkLayer)) {
	    throw new RuntimeException("Cannot attach duplicate data link");
	}
	dataLinkLayers.put(remoteAddress, dataLinkLayer);

	// Create a queue for any data received on this link.
	receiveBuffers.put(dataLinkLayer, new LinkedList<Byte>());

	// Register this network layer as the client of this data link layer.
	dataLinkLayer.register(this);
	
    } // attach ()
    // =========================================================================
    


    // =========================================================================
    /**
     * The event loop.  If there is buffered data to send, construct a packet
     * and pass it to the data link layer for transmission; if data has been
     * delivered from a data link layer, process it.
     */
    public void go () {

        // Event loop.
        doEventLoop = true;
        while (doEventLoop) {

	    // Check each link for activity, each in turn.
	    for (DataLinkLayer dataLinkLayer : dataLinkLayers.values()) {

		// Let the data link layer send or receive as needed.
		dataLinkLayer.checkEvents();

		// Has data been received and buffered on this link?
		// If so, determine if it contains a packet for processing.
		Queue<Byte> buffer = receiveBuffers.get(dataLinkLayer);
		if (buffer.size() > 0) {
		    byte[] packet = extractPacket(buffer);
		    if (packet != null) {
			processPacket(packet);
		    }
		}
		
	    }

        } // Event loop

    } // go ()
    // =========================================================================



    // =========================================================================
    /**
     * End the event loop.
     */
    public void stop () {

        doEventLoop = false;

    } // stop ()
    // =========================================================================
    


    // =========================================================================
    /**
     * Send a sequence of bytes through this layer.  Expected to be called by
     * the client.  Packets are constructed and then sent via whichever data
     * link the is chosen by the routing method.
     *
     * @param destination The name of the destination host.
     * @param data        The sequence of bytes to send.
     */
    public void send (String destination, byte[] data) {

	// Determine the address of the destination.
	int destinationAddress = destination.hashCode();
	
	// Loop through the data in packet-size chunks.
	int numPackets = ((data.length / MAX_PACKET_SIZE) +
			  (data.length % MAX_PACKET_SIZE == 0 ? 0 : 1));
	for (int i = 0; i < numPackets; i += 1) {

	    // Grab the next packet-worth of data a make of packet of it.
	    int    start      = i * MAX_PACKET_SIZE;
	    int    end        = Math.min((i + 1) * MAX_PACKET_SIZE,
					 data.length);
	    byte[] packetData = Arrays.copyOfRange(data, start, end);
	    byte[] packet     = createPacket(destinationAddress, packetData);

	    // Choose the data link layer through which to route.
	    DataLinkLayer dataLink = route(destinationAddress);

	    // Send the packet.
	    dataLink.send(packet);

	    if (debug) {
		System.err.printf("Address %d sent packet:\n\t%s\n",
				  address,
				  bytesToString(packet));
	    }
	    
	}
	
    } // send ()
    // =========================================================================



    // =========================================================================
    /**
     * Create a single packet containing the given data, with header that marks
     * the source and destination hosts.
     *
     * @param destination The address to which this packet is sent.
     * @param data        The data to send.
     * @return the sequence of bytes that comprises the packet.
     */
    abstract protected byte[] createPacket (int destination, byte[] data);
    // =========================================================================



    // =========================================================================
    /**
     * Choose the link through which to send a packet given its destination.
     *
     * @param destination The address to which this packet is being sent.
     */
    abstract protected DataLinkLayer route (int destination);
    // =========================================================================



    // =========================================================================
    /**
     * Examine a buffer to see if it's data can be extracted as a packet.
     *
     * @param buffer The receive-buffer to be examined.
     * @return the packet extracted packet if a whole one is present in the
     *         buffer; <code>null</code> otherwise.
     */
    abstract protected byte[] extractPacket (Queue<Byte> buffer);
    // =========================================================================



    // =========================================================================
    /**
     * Given a received packet, process it.  If the destination for the packet
     * is this host, then deliver its data to the client layer.  If the
     * destination is another host, route and send the packet.
     *
     * @param packet The received packet to process.
     */
    abstract protected void processPacket (byte[] packet);
    // =========================================================================
    


    // =========================================================================
    /**
     * Receive bytes from a data link layer, buffering them for processing.
     *
     * @param dataLink The link from which this data was received.
     * @param data     The data received.
     */
    public void receive (DataLinkLayer dataLink, byte[] data) {

	Queue<Byte> buffer = receiveBuffers.get(dataLink);

	for (int i = 0; i < data.length; i += 1) {
	    buffer.add(data[i]);
	}

	if (debug) {
	    System.err.printf("Address %d received bytes:\n\t%s\n",
			      address,
			      bytesToString(data));
	}

    } // receive ()
    // =========================================================================



    // =========================================================================
    /**
     * Copy bytes into a longer array from a shorter one, copying the entire
     * contents of the shorter array.
     *
     * @param destination The longer array into which to copy.
     * @param offset      The starting index within the longer array.
     * @param source      The shorter array from which to copy.
     */
    public static void copyInto (byte[] destination, int offset, byte[] source) {

	for (int i = 0; i < source.length; i += 1) {
	    destination[i + offset] = source[i];
	}
	
    } // copyInto ()
    // =========================================================================



    // =========================================================================
    /**
     * Copy bytes from a longer array into a shorter one, copying into the
     * entire space of the shorter one.
     *
     * @param destination The shorter array into which to copy.
     * @param source      The longer array from which to copy.
     * @param offset      The starting index within the longer array.
     */
    public static void copyFrom (byte[] destination, byte[] source, int offset) {

	for (int i = 0; i < destination.length; i += 1) {
	    destination[i] = source[i + offset];
	}
	
    } // copyFrom ()
    // =========================================================================



    // =========================================================================
    /**
     * Convert an int into an array of bytes.
     *
     * @param data The int value to convert.
     * @return an array of the bytes taken from the int.
     */
    public static byte[] intToBytes (int data) {
	
	return new byte[] {
	    (byte)((data >> 24) & 0xff),
	    (byte)((data >> 16) & 0xff),
	    (byte)((data >> 8) & 0xff),
	    (byte)((data >> 0) & 0xff)
	};
	
    } // intToBytes ()
    // =========================================================================

    

    // =========================================================================
    /**
     * Convert an array of bytes into an int.
     *
     * @param data The array of bytes to convert into an int value.
     * @return the converted int value.
     * @throws RuntimeException if the data array is <code>null</code> or not of
     *                          the correct length to contain an integer's bytes.
     */
    public static int bytesToInt (byte[] data) {

	if (data == null || data.length != Integer.BYTES) {
	    throw new RuntimeException("Invalid data array to convert to bytes");
	}
	
	return (int)(data[0] << 24 |
		     data[1] << 16 |
		     data[2] << 8  |
		     data[3]);
	
    } // bytesToInt ()
    // =========================================================================



    // =========================================================================
    /**
     * Provide the network address of this host.
     *
     * @return the network address.
     */
    public int getAddress () {

	return address;

    } // getAddress () 
    // =========================================================================



    // =========================================================================
    public static String bytesToString (byte[] data) {

	String s = "{ ";
	for (int i = 0; i < data.length; i += 1) {
	    s += data[i] + (i < data.length - 1 ? ", " : " }\n");
	}

	return s;
	
    } // bytesToString ()
    // =========================================================================
    


    // =========================================================================
    // INSTANCE DATA MEMBERS

    /** The host that is using this layer. */
    protected Host                             client;

    /** The address of this host on the network. */
    protected int                              address;

    /** The data link layers used by this layer, keyed by host address. */
    protected Map<Integer, DataLinkLayer>      dataLinkLayers;

    /** The buffers of received data, organized per data link. */
    protected Map<DataLinkLayer, Queue<Byte> > receiveBuffers;

    /** Whether to continue the event loop. */
    protected boolean                          doEventLoop;
    // =========================================================================



    // =========================================================================
    // CLASS DATA MEMBERS

    /** The maximum number of original data bytes that a frame may contain. */
    public static final int     MAX_PACKET_SIZE = 32;

    /** Whether to emit debugging information. */
    public static final boolean debug           = false;
   // =========================================================================


    
// =============================================================================
} // class NetworkLayer
// =============================================================================

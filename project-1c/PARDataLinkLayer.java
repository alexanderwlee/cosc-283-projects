// =============================================================================
// IMPORTS

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
// =============================================================================

// =============================================================================
/**
 * @file PARDataLinkLayer.java
 * @author Alexander Lee (awlee22@amherst.edu)
 * @date March 2020
 *     <p>A data link layer that uses start/stop tags and byte packing to frame the data, and that
 *     performs error management with a parity bit. It employs no flow control via positive
 *     acknowledgment with retransmission (i.e., stop-and-wait).
 */
public class PARDataLinkLayer extends DataLinkLayer {
  // =============================================================================

  // =========================================================================
  /**
   * Embed a raw sequence of bytes into a framed sequence.
   *
   * @param data The raw sequence of bytes to be framed.
   * @return A complete frame.
   */
  protected Queue<Byte> createFrame(Queue<Byte> data) {

    // Begin with the start tag.
    Queue<Byte> framingData = new LinkedList<Byte>();
    framingData.add(startTag);

    // Add each byte of original data.
    for (byte currentByte : data) {

      // If the current data byte is itself a metadata tag, then precede
      // it with an escape tag.
      if ((currentByte == startTag)
          || (currentByte == stopTag)
          || (currentByte == escapeTag)
          || (currentByte == ackTag)) {

        framingData.add(escapeTag);
      }

      // Add the data byte itself.
      framingData.add(currentByte);
    }

    // Add frame number.
    framingData.add(sendFrameNum);
    data.add(sendFrameNum);

    // Calculate the parity.
    byte parity = calculateParity(data);

    // Add the parity byte.
    framingData.add(parity);

    // End with a stop tag.
    framingData.add(stopTag);

    return framingData;
  } // createFrame ()
  // =========================================================================

  // =========================================================================
  /**
   * Determine whether the received, buffered data constitutes a complete frame. If so, then remove
   * the framing metadata and return the original data. Note that any data preceding an escaped
   * start tag is assumed to be part of a damaged frame, and is thus discarded.
   *
   * @return If the buffer contains a complete frame, the extracted, original data; <code>null
   *     </code> otherwise.
   */
  protected Queue<Byte> processFrame() {

    // Search for a start tag.  Discard anything prior to it.
    boolean startTagFound = false;
    Iterator<Byte> i = receiveBuffer.iterator();
    while (!startTagFound && i.hasNext()) {
      byte current = i.next();
      if (current != startTag) {
        i.remove();
      } else {
        startTagFound = true;
      }
    }

    // If there is no start tag, then there is no frame.
    if (!startTagFound) {
      return null;
    }

    // Try to extract data while waiting for an unescaped stop tag.
    int index = 1;
    LinkedList<Byte> extractedBytes = new LinkedList<Byte>();
    boolean stopTagFound = false;
    while (!stopTagFound && i.hasNext()) {

      // Grab the next byte.  If it is...
      //   (a) An escape tag: Skip over it and grab what follows as
      //                      literal data.
      //   (b) A stop tag:    Remove all processed bytes from the buffer and
      //                      end extraction.
      //   (c) A start tag:   All that precedes is damaged, so remove it
      //                      from the buffer and restart extraction.
      //   (d) Otherwise:     Take it as literal data.
      byte current = i.next();
      index += 1;
      if (current == escapeTag) {
        if (i.hasNext()) {
          current = i.next();
          index += 1;
          extractedBytes.add(current);
        } else {
          // An escape was the last byte available, so this is not a
          // complete frame.
          return null;
        }
      } else if (current == stopTag) {
        cleanBufferUpTo(index);
        stopTagFound = true;
      } else if (current == startTag) {
        cleanBufferUpTo(index - 1);
        index = 1;
        extractedBytes = new LinkedList<Byte>();
      } else {
        extractedBytes.add(current);
      }
    }

    // If there is no stop tag, then the frame is incomplete.
    if (!stopTagFound) {
      return null;
    }

    if (debug) {
      System.out.println("PARDataLinkLayer.processFrame(): Got whole frame!");
    }

    // If received an acknowledgement frame.
    if (extractedBytes.size() == 1 && extractedBytes.peek() == ackTag) {
      System.out.println(client + ": Acknowledgement received");
      // Record layer is no long waiting for an acknowledgement.
      waitingForAck = false;
      // Move to next frame number.
      sendFrameNum = getNextFrameNum(sendFrameNum);
      // Return null since we don't do anything with the acknowledgement.
      return null;
    }

    // The final byte inside the frame is the parity.  Compare it to a
    // recalculation.
    byte receivedParity = extractedBytes.remove(extractedBytes.size() - 1);
    byte calculatedParity = calculateParity(extractedBytes);
    if (receivedParity != calculatedParity) {
      System.out.printf("PARDataLinkLayer.processFrame():\tDamaged frame\n");
      return null;
    }

    // Save actual received frame number.
    actualReceivedFrameNum = extractedBytes.removeLast();

    return extractedBytes;
  } // processFrame ()
  // =========================================================================

  // =========================================================================
  /**
   * Extract the next frame-worth of data from the sending buffer, frame it, and then send it.
   *
   * @return the frame of bytes transmitted.
   */
  @Override
  protected Queue<Byte> sendNextFrame() {

    // If waiting for an acknowledgement.
    if (waitingForAck) {
      // Don't send next frame and return null.
      return null;
    }

    // Call parent method.
    return super.sendNextFrame();
  } // sendNextFrame ()
  // =========================================================================

  // =========================================================================
  /**
   * After sending a frame, do any bookkeeping (e.g., buffer the frame in case a resend is
   * required).
   *
   * @param frame The framed data that was transmitted.
   */
  protected void finishFrameSend(Queue<Byte> frame) {

    // COMPLETE ME WITH FLOW CONTROL

    // Clear buffer.
    resendBuffer.clear();
    // Copy contents of frame into buffer.
    for (byte b : frame) {
      resendBuffer.add(b);
    }
    // Record layer is now waiting for an acknowledgement.
    waitingForAck = true;
    // Record the time.
    sentTime = System.currentTimeMillis();
    System.out.println(client + ": Sending frame " + sendFrameNum);
  } // finishFrameSend ()
  // =========================================================================

  // =========================================================================
  /**
   * After receiving a frame, do any bookkeeping (e.g., deliver the frame to the client, if
   * appropriate) and responding (e.g., send an acknowledgment).
   *
   * @param frame The frame of bytes received.
   */
  protected void finishFrameReceive(Queue<Byte> frame) {

    // COMPLETE ME WITH FLOW CONTROL
    // If the actual received frame number is equal to the expected.
    if (actualReceivedFrameNum == expectedReceivedFrameNum) {
      // Deliver frame to the client.
      byte[] deliverable = new byte[frame.size()];
      for (int i = 0; i < deliverable.length; i += 1) {
        deliverable[i] = frame.remove();
      }

      client.receive(deliverable);

      // Move to next frame number.
      expectedReceivedFrameNum = getNextFrameNum(expectedReceivedFrameNum);
    } else {
      System.out.println(client + ": Received unexpected frame number");
    }

    // Create and send an acknowledgement.
    Queue<Byte> ackFrame = new LinkedList<Byte>();
    ackFrame.add(startTag);
    ackFrame.add(ackTag);
    ackFrame.add(stopTag);
    transmit(ackFrame);
    System.out.println(client + ": Acknowledgment sent");
  } // finishFrameReceive ()
  // =========================================================================

  // =========================================================================
  /**
   * Determine whether a timeout should occur and be processed. This method is called regularly in
   * the event loop, and should check whether too much time has passed since some kind of response
   * is expected.
   */
  protected void checkTimeout() {

    // COMPLETE ME WITH FLOW CONTROL
    // If waiting for an acknowledgement.
    if (waitingForAck) {
      // If reached timeout.
      if (System.currentTimeMillis() - sentTime > 100) {
        // Resend.
        System.out.println(client + ": Timeout...resending buffer");
        sentTime = System.currentTimeMillis();
        transmit(resendBuffer);
      }
    }
  } // checkTimeout ()
  // =========================================================================

  // =========================================================================
  /** Get next frame number. */
  private byte getNextFrameNum(byte currentFrameNum) {
    return (byte) (currentFrameNum ^ 1);
  } // getNextFrameNum ()
  // =========================================================================

  // =========================================================================
  /**
   * For a sequence of bytes, determine its parity.
   *
   * @param data The sequence of bytes over which to calculate.
   * @return <code>1</code> if the parity is odd; <code>0</code> if the parity is even.
   */
  private byte calculateParity(Queue<Byte> data) {

    int parity = 0;
    for (byte b : data) {
      for (int j = 0; j < Byte.SIZE; j += 1) {
        if (((1 << j) & b) != 0) {
          parity ^= 1;
        }
      }
    }

    return (byte) parity;
  } // calculateParity ()
  // =========================================================================

  // =========================================================================
  /**
   * Remove a leading number of elements from the receive buffer.
   *
   * @param index The index of the position up to which the bytes are to be removed.
   */
  private void cleanBufferUpTo(int index) {

    for (int i = 0; i < index; i += 1) {
      receiveBuffer.remove();
    }
  } // cleanBufferUpTo ()
  // =========================================================================

  // =========================================================================
  // DATA MEMBERS

  /** The start tag. */
  private final byte startTag = (byte) '{';

  /** The stop tag. */
  private final byte stopTag = (byte) '}';

  /** The escape tag. */
  private final byte escapeTag = (byte) '\\';

  /** The acknowledgement tag. */
  private final byte ackTag = (byte) '@';

  /** The send frame number. */
  private byte sendFrameNum = 0;

  /** The receive frame number to expect. */
  private byte expectedReceivedFrameNum = 0;

  /** The actual received frame number. */
  private byte actualReceivedFrameNum;

  /** Whether the layer is waiting for an acknowledgement of the frame. */
  private boolean waitingForAck = false;

  /** The time the layer sent the frame. */
  private long sentTime;

  /** The buffer of data that might need to be resent. */
  private Queue<Byte> resendBuffer = new LinkedList<Byte>();
  // =========================================================================

  // =============================================================================
} // class ParityDataLinkLayer
// =============================================================================

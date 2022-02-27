/*
 * @file ParityDataLinkLayer.java
 * @author Alexander Lee (awlee22@amherst.edu)
 * @data February 2022
 */

import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

/**
 * A data link layer that uses start/stop tags and byte packing to frame the data, and that performs
 * error management.
 */
public class ParityDataLinkLayer extends DataLinkLayer {

  // The start tag, stop tag, and the escape tag.
  private final byte startTag = (byte) '{';
  private final byte stopTag = (byte) '}';
  private final byte escapeTag = (byte) '\\';

  // The maximum size (in bytes) of smaller frames.
  private final int maxFrameSize = 8;

  /**
   * Embed a raw sequence of bytes into multiple smaller frames with at most 8 bytes of data and a
   * parity bit.
   *
   * @param data The raw sequence of bytes to be framed.
   * @return A complete frame with multiple smaller frames.
   */
  protected byte[] createFrame(byte[] data) {

    Queue<Byte> framingData = new LinkedList<Byte>();

    // Parity of the smaller data frame of at most 8 bytes.
    byte parity = 0;

    for (int i = 0; i < data.length; i++) {
      // If we are at the start of the smaller frame.
      if (i % maxFrameSize == 0) {
        framingData.add(startTag);
        parity = 0;
      }

      // If the current data byte is itself a metadata tag, then precede
      // it with an escape tag.
      byte currentByte = data[i];
      if ((currentByte == startTag) || (currentByte == stopTag) || (currentByte == escapeTag)) {
        framingData.add(escapeTag);
      }

      // Add the data byte itself.
      framingData.add(currentByte);

      // Update parity with the parity of the current byte.
      parity ^= getByteParity(currentByte);

      // If we are at the end of the smaller frame.
      if ((i % maxFrameSize == maxFrameSize - 1) || (i == data.length - 1)) {
        framingData.add(parity);
        framingData.add(stopTag);
      }
    }

    // Convert to the desired byte array.
    byte[] framedData = new byte[framingData.size()];
    Iterator<Byte> i = framingData.iterator();
    int j = 0;
    while (i.hasNext()) {
      framedData[j++] = i.next();
    }

    return framedData;
  }

  /**
   * Determine whether the received, buffered data constitutes a complete frame. If so, then remove
   * the framing metadata and return the original data. Note that any data preceding an escaped
   * start tag is assumed to be part of a damaged frame, and is thus discarded. Also determine
   * whether the data has been corrupted.
   *
   * @return If the buffer contains a complete and uncorrupted frame, the extracted, original data;
   *     <code>null
   *     </code> otherwise.
   */
  protected byte[] processFrame() {

    // Search for a start tag.  Discard anything prior to it.
    boolean startTagFound = false;
    Iterator<Byte> i = byteBuffer.iterator();
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
    Deque<Byte> extractedBytes = new LinkedList<Byte>();
    boolean stopTagFound = false;
    while (!stopTagFound && i.hasNext()) {

      // Grab the next byte.  If it is...
      //   (a) An escape tag: Skip over it and grab what follows as
      //                      literal data.
      //   (b) A stop tag:    Remove all processed bytes from the buffer and
      //                      end extraction. Also determine whether the extracted data is
      //                      uncorrupted.
      //   (c) A start tag:   All that precedes is damaged, so remove it
      //                      from the buffer and restart extraction.
      //   (d) Otherwise:     Take it as literal data.
      byte current = i.next();
      if (current == escapeTag) {
        if (i.hasNext()) {
          current = i.next();
          extractedBytes.add(current);
        } else {
          // An escape was the last byte available, so this is not a
          // complete frame.
          if (debug) {
            System.out.println("Escape was the last byte available");
          }
          return null;
        }
      } else if (current == stopTag) {
        cleanBufferUpTo(i);
        stopTagFound = true;
        byte expectedParity = extractedBytes.removeLast();
        byte actualParity = 0;
        for (byte b : extractedBytes) {
          actualParity ^= getByteParity(b);
        }
        if (expectedParity != actualParity) {
          byte[] extractedData = new byte[extractedBytes.size()];
          int j = 0;
          for (byte b : extractedBytes) {
            extractedData[j] = b;
            j++;
          }
          System.err.println("Error detected");
          System.err.println("Corrupted data: " + new String(extractedData));
          System.err.println("Not delivering data to host");
          return null;
        }
      } else if (current == startTag) {
        cleanBufferUpTo(i);
        extractedBytes = new LinkedList<Byte>();
        if (debug) {
          System.out.println("Current byte is start tag");
        }
      } else {
        extractedBytes.add(current);
      }
    }

    // If there is no stop tag, then the frame is incomplete.
    if (!stopTagFound) {
      return null;
    }

    // Convert to the desired byte array.
    if (debug) {
      System.out.println("ParityDataLinkLayer.processFrame(): Got whole frame!");
    }
    byte[] extractedData = new byte[extractedBytes.size()];
    int j = 0;
    i = extractedBytes.iterator();
    while (i.hasNext()) {
      extractedData[j] = i.next();
      if (debug) {
        System.out.printf(
            "ParityDataLinkLayer.processFrame():\tbyte[%d] = %c\n", j, extractedData[j]);
      }
      j += 1;
    }

    return extractedData;
  }

  private int getByteParity(byte currentByte) {
    byte parity = 0;
    for (int bitPos = 0; bitPos < DataLinkLayer.BITS_PER_BYTE; bitPos++) {
      int currentBit = (currentByte >> bitPos) & 1;
      parity ^= currentBit;
    }
    return parity;
  }

  private void cleanBufferUpTo(Iterator<Byte> end) {
    Iterator<Byte> i = byteBuffer.iterator();
    while (i.hasNext() && i != end) {
      i.next();
      i.remove();
    }
  }
}

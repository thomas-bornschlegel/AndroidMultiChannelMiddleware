package de.lmu.mcm.helper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Helper class to support reading of data for the Bluetooth and the Wifi interface.
 * 
 * @author Thomas Bornschlegel
 * 
 */
public class InputStreamHelper {

    private final String TAG;
    private byte[] buffer;

    public InputStreamHelper(String tagPrefic) {
        this.TAG = tagPrefic + " InputStreamHelper";
    }

    private boolean endOfStreamReached = false;
    // We assume that messages are not larger than this constant. Otherwise we get an out of memory error. In future
    // implementations it could help to use a different data structure that does not imply such limitations.
    // For example we could write the InputStream directly to a FileOutputstream so that the received data is cached in
    // a file on disk.
    private int maxSize = 8000;

    /**
     * Reads the next bytes from an InputStream which contains data that is formed as specified in the middleware
     * definition. The first four bytes contain an Integer describing the message length indicator (MLI). The fifth byte
     * contains the message length as a Byte. Everything afterwards contains the content in raw bytes. The content has
     * to have exactly the size of the MLI.
     * */
    public byte[] readNextBytes(InputStream in) {

        try {
            buffer = new byte[4];
            int read = in.read(buffer, 0, 4);

            if (read == 4) {
                ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
                int lengthIndicator = byteBuffer.getInt();

                if (lengthIndicator > maxSize) {
                    LogHelper.getInstance().e(TAG,
                            "Read message of size " + lengthIndicator + " is larger than the constant " + maxSize);

                    // Flush the input stream:
                    // buffer = new byte[1024];
                    // int bytesRead = 4;
                    // while (in.read() != -1) {
                    // bytesRead++;
                    // }
                    // LogHelper.getInstance().e(TAG, "Read " + bytesRead + " bytes of junk bytes");

                    // // / XXX REMOVE REMOVE REMOVE
                    // buffer = Arrays.copyOf(buffer, 134);
                    // int readBytes = 0;
                    // for (int i = 4; readBytes != -1 && i < buffer.length; i++) {
                    // readBytes = in.read();
                    // if (readBytes != -1) {
                    // buffer[i] = (byte) readBytes;
                    // }
                    // }
                    // ByteConverter.printBytes("Read count: " + readBytes, buffer);

                    return null;
                }

                if (lengthIndicator > 0) {
                    // We add one because we also read the message type
                    int completeMessageLength = lengthIndicator + 1;
                    // And we also store the length indicator to have a complete formatting
                    completeMessageLength += 4;
                    buffer = Arrays.copyOf(buffer, completeMessageLength);
                    // We already read the length indicator so we substract it:
                    int bytesToRead = completeMessageLength - 4;
                    // Read message type and content
                    int readBytes = in.read(buffer, 4, bytesToRead);

                    if (readBytes != bytesToRead) {
                        LogHelper.getInstance().e(
                                TAG,
                                (read + 4) + " bytes read but we expected " + completeMessageLength
                                        + " => wrong length indicator!");
                    } else {
                        LogHelper.getInstance().d(TAG, "correctly read " + completeMessageLength + " bytes");
                    }

                    return buffer;
                } else {
                }
            } else if (read <= 0) {
                LogHelper.getInstance().d(TAG, "0 bytes read => End of stream reached");
                endOfStreamReached = true;
            } else if (read > 4) {
                LogHelper.getInstance().e(TAG, read + " bytes read but we expected 4 => something went wrong!");
            }
        } catch (IOException e) {
            if (e.getMessage().contains("closed")) {
                LogHelper.getInstance().e(TAG, "Connection closed => End of stream reached");
                endOfStreamReached = true;
            } else if (e.getMessage().contains("abort")) {
                LogHelper.getInstance().e(TAG, "Connection aborted => End of stream reached");
                endOfStreamReached = true;
            } else {
                LogHelper.getInstance().e(TAG, "Other IOException while trying to read input stream", e);
                endOfStreamReached = true;
            }
        } catch (Exception e) {
            LogHelper.getInstance().e(TAG, "Exception while trying to read input stream", e);
        }

        return null;
    }

    /**
     * @return true if the end of the stream was reached.
     * */
    public boolean isEndOfStreamReached() {
        return endOfStreamReached;
    }

}

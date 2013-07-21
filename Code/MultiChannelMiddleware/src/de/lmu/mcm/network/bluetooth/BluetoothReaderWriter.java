package de.lmu.mcm.network.bluetooth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import de.lmu.mcm.helper.InputStreamHelper;
import de.lmu.mcm.helper.LogHelper;

/**
 * Manages reading and writing to a bluetooth socket.
 * 
 * @author Thomas Bornschlegel
 * 
 */
public class BluetoothReaderWriter {

    private final String TAG = "Bluetooth Reader/Writer ";
    private BluetoothSocket socket = null;
    private BluetoothDevice device = null;
    private InputStream in;
    private OutputStream out;
    private InputStreamHelper inHelper;
    private BluetoothCommunicator communicator;
    private boolean isEndOfStreamReached = false;

    public BluetoothReaderWriter(BluetoothCommunicator communicator, BluetoothSocket socket) throws IOException {
        super();
        this.socket = socket;
        device = socket.getRemoteDevice();

        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();

        inHelper = new InputStreamHelper("Bluetooth ");
        this.communicator = communicator;
    }

    /**
     * @return the device that is associated with the bluetooth socket
     */
    public BluetoothDevice getRemoteDevice() {
        return device;
    }

    /**
     * Reads the next message from the connected socket and blocks until the message is read.
     * 
     * @return the message that was read from the connected socket or null if something went wrong.
     * */
    public byte[] readBlocking() {
        try {
            LogHelper.getInstance().d(TAG, "Waiting for message...");
            if (isConnected() && in != null) {
                byte[] readBytes = inHelper.readNextBytes(in);
                if (inHelper.isEndOfStreamReached()) {
                    LogHelper.getInstance().d(TAG, "End of bluetooth stream reached.");
                    isEndOfStreamReached = true;
                    communicator.onConnectionLost();
                }
                LogHelper.getInstance().d(TAG, "Read new message.");
                return readBytes;
            } else {
                LogHelper.getInstance().e(TAG, "Could not read message as no connection exists");
            }
        } catch (Exception e) {
            if (e != null && e.getMessage() != null && e.getMessage().equals("Software caused connection abort")
                    && communicator != null) {
                // TODO find better solution?
                communicator.onConnectionLost();
            } else {
                LogHelper.getInstance().e(TAG, "Error while trying to read message.", e);
            }
        }
        return null;
    }

    /**
     * Writes the next message from the connected socket and blocks until the message was written.
     * 
     * @return true if the message was written successfully.
     * */
    public boolean writeBlocking(byte[] message) {

        try {
            if (isConnected() && out != null) {
                out.write(message);
                out.flush();
                LogHelper.getInstance().d(TAG, "Wrote message.");
                return true;
            } else {
                LogHelper.getInstance().e(TAG, "Could not write message as no connection exists");
            }
        } catch (Exception e) {
            LogHelper.getInstance().e(TAG, "Error while trying to write message.", e);

        }

        return false;
    }

    /**
     * Closes the socket which terminates the connection.
     * */
    public void closeConnection() {
        if (out != null) {
            try {
                out.close();
                LogHelper.getInstance().d(TAG, "Closed Bluetooth output stream");
            } catch (Exception e) {
                LogHelper.getInstance().e(TAG, "Error closing Bluetooth output stream", e);
            }
        }
        if (socket != null) {
            try {
                socket.close();
                LogHelper.getInstance().d(TAG, "Closed Bluetooth socket");
            } catch (Exception e) {
                LogHelper.getInstance().e(TAG, "Error closing Bluetooth socket", e);
            }
        }
    }

    /**
     * @return true if the socket is connected
     */
    public boolean isConnected() {
        if (socket == null) {
            return false;
        }
        try {
            return socket.isConnected();
        } catch (NoSuchMethodError e) {
            // Strange error that occured on the Nexus One with Cyanogen Mode Android 2.3.7
        }
        // Weak check to return at least something when the above method fails.
        return in != null && out != null;
    }

    /**
     * @return true if the end of the inputstream of the socket is reached
     */
    public boolean isEndOfStreamReached() {
        return isEndOfStreamReached;
    }

}

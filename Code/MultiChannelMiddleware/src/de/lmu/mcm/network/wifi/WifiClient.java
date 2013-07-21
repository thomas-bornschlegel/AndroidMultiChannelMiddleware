package de.lmu.mcm.network.wifi;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

import de.lmu.mcm.helper.LogHelper;
import de.lmu.mcm.network.MultiNetworkAddress;

/**
 * Wifi Client class.
 * 
 * @author Thomas Bornschlegel
 * 
 */
public class WifiClient extends AbstractWifiReaderWriter {

    private String TAG = "WIFI Client ";
    private boolean isCurrentlyConnecting = false;
    private boolean isCanceled = false;

    /**
     * Connects to the given address.
     * 
     * @param address
     *            the address of the server
     * @param timeoutInSeconds
     *            describes how long is waited to connect to the server, use 0 for no timeout
     * @param maxRetries
     *            the maximum number of retries if the connection failed.
     * @param port
     *            the port on which the server waits for the client
     * @return true if the connection was established
     * */
    public boolean connectToServerBlocking(MultiNetworkAddress address, int timeOutInSeconds,
            int maxConnectionAttempts, int port) {
        isCurrentlyConnecting = true;
        while (maxConnectionAttempts > 0 && !isCanceled) {
            if (singleConnectionAttempt(address, timeOutInSeconds, port)) {
                isCurrentlyConnecting = false;
                return true;
            }
            maxConnectionAttempts--;
        }
        LogHelper.getInstance().e(
                TAG,
                maxConnectionAttempts + " connection attempts to server failed: " + address.getIpAddress() + ":"
                        + address.getIpPort());
        isCurrentlyConnecting = false;
        return false;
    }

    /**
     * Performs a single connection attempt to the given address.
     * 
     */
    private boolean singleConnectionAttempt(MultiNetworkAddress address, int timeOutInSeconds, int port) {
        InetAddress ip = address.getIpAddress();
        try {
            Socket newSocket = new Socket();
            SocketAddress socketAddress = new InetSocketAddress(ip, port);
            newSocket.connect(socketAddress, timeOutInSeconds * 1000);
            LogHelper.getInstance().d(TAG, "Successfully connected to " + ip + ":" + port);

            onConnected(newSocket);

            return true;
        } catch (Exception e) {
            LogHelper.getInstance().e(TAG, "Error trying to connect to server " + ip + ":" + port, e);
        }
        return false;
    }

    /**
     * Cancels the current connection.
     * 
     * @return true if the connection could be canceled.
     * */
    public boolean disconnectAndTerminate() {
        return closeSockets(null);
    }

    /**
     * Reads a message from the server. Blocking call.
     * 
     * @return the read message from the connected socket or null if something went wrong.
     * */
    public byte[] readBlocking() {
        return super.readBlocking();
    }

    /**
     * Writes a message to the server. Blocking call.
     * 
     * @return true if the message was written successfully.
     * */
    public boolean writeBlocking(byte[] message) {
        return super.writeBlocking(message);
    }

    /**
     * @return true if we are currently trying to connect
     */
    public boolean isCurrentlyConnecting() {
        return isCurrentlyConnecting;
    }

    /**
     * @param isCanceled
     *            if this is true we will not try to connect to the server any longer
     * */
    public void setCanceled(boolean isCanceled) {
        this.isCanceled = isCanceled;
    }

}

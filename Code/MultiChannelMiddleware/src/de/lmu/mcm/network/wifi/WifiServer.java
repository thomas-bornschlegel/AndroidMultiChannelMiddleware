package de.lmu.mcm.network.wifi;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import de.lmu.mcm.helper.InterfaceAvailabilityChecker;
import de.lmu.mcm.helper.LogHelper;

/**
 * WifiServer class.
 * 
 * @author Thomas Bornschlegel
 * 
 */
public class WifiServer extends AbstractWifiReaderWriter {

    private String TAG = "WIFI Server ";
    private ServerSocket serverSocket;
    private boolean isCurrentlyWaitingForClientToConnect = false;
    private JmdnsHelper jmdns;

    /**
     * Waits for a client to connect on the given port
     * 
     * @param port
     *            the port on which we wait for the client
     * @param timeoutInSeconds
     *            describes how long is waited for the client to connect, use 0 for no timeout
     * @param maxRetries
     *            the maximum number of retries if the connection failed.
     * 
     * @return the socket of the client which connected, or null if something went wrong
     * */
    public Socket waitForClientToConnectBlocking(int port, int timeoutInSeconds, int maxRetries) {
        while (maxRetries > 0) {

            try {
                isCurrentlyWaitingForClientToConnect = true;
                serverSocket = new ServerSocket(port);
                serverSocket.setSoTimeout(timeoutInSeconds * 1000);
                Socket newSocket = serverSocket.accept();
                onConnected(newSocket);
                LogHelper.getInstance().d(
                        TAG,
                        "Client connected successfully " + outgoingSocket.getInetAddress() + ":"
                                + outgoingSocket.getPort());
                maxRetries = 0;
                return outgoingSocket;
            } catch (SocketTimeoutException timeout) {
                LogHelper.getInstance().e(TAG,
                        "Client did not connect within " + timeoutInSeconds + " seconds. Timeout.");
                isCurrentlyWaitingForClientToConnect = false;
            } catch (BindException e) {
                int oldPort = port;
                // Address already in use
                port = InterfaceAvailabilityChecker.getRandomPortNumber();
                LogHelper.getInstance()
                        .e(TAG, "Port " + oldPort + " was already in use. Trying again on port: " + port);
                maxRetries--;
                if (jmdns != null) {
                    jmdns.restartAdvertisingOnNewPort(port);
                }
            } catch (IOException e) {
                LogHelper.getInstance().e(TAG, "Error while waiting for client to connect on port " + port, e);
                isCurrentlyWaitingForClientToConnect = false;
                maxRetries--;
            }
        }
        return null;
    }

    /**
     * Reads a message from the client. Blocking call.
     * 
     * @return the read message from the connected socket or null if something went wrong.
     * */
    public byte[] readBlocking() {
        return super.readBlocking();
    }

    /**
     * Writes a message to the client. Blocking call.
     * 
     * @return true if the message was written successfully.
     * */
    public boolean writeBlocking(byte[] message) {
        return super.writeBlocking(message);
    }

    /**
     * Cancels the current connection.
     * 
     * @return true if the connection could be canceled.
     * */
    public boolean disconnectAndTerminate() {
        return closeSockets(serverSocket);
    }

    /**
     * @return true if we are currently waiting for a client to connect
     */
    public boolean isCurrentlyWaitingForClientToConnect() {
        return isCurrentlyWaitingForClientToConnect;
    }

    /**
     * Use this method if you are the server and if you want to update the service discovery messages for the case, that
     * a connection fails and we change the port. If this method was called with a valid {@link JmdnsHelper} this helper
     * will be used to update the mDNS messages.
     * 
     * @param jmdns
     */
    public void setJmdns(JmdnsHelper jmdns) {
        this.jmdns = jmdns;
    }

}

package de.lmu.mcm.network.wifi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import de.lmu.mcm.helper.InputStreamHelper;
import de.lmu.mcm.helper.LogHelper;
import de.lmu.mcm.network.MultiNetworkAddress;

/**
 * Abstract class that provides methods for reading and writing from network sockets.
 * 
 * @author Thomas Bornschlegel
 * 
 */
public abstract class AbstractWifiReaderWriter {

    protected String TAG = "WIFI AbstractWifi ";

    protected boolean isCanceled = false;

    private InputStream in;
    private OutputStream out;
    protected Socket outgoingSocket = null;
    private InputStreamHelper inHelper = new InputStreamHelper("WIFI ");
    private boolean isEndOfStreamReached = false;

    /**
     * Call this method after you set up a connection to a new socket.
     * 
     * @param newSocket
     *            the connected socket
     */
    public void onConnected(Socket newSocket) throws IOException {
        in = newSocket.getInputStream();
        out = newSocket.getOutputStream();
        outgoingSocket = newSocket;
    }

    /**
     * @return true if a connection exists
     */
    public boolean isConnected() {
        if (outgoingSocket == null) {
            return false;
        }
        return !outgoingSocket.isClosed() && outgoingSocket.isConnected();
    }

    /**
     * @param isCanceled
     *            if this is true reading and writing is canceled.
     */
    public void setCanceled(boolean isCanceled) {
        this.isCanceled = isCanceled;
    }

    /**
     * Reads blocking from the socket which was set up in {@link #onConnected(Socket)}.
     * 
     * @return the read message from the connected socket or null if something went wrong.
     * */
    public byte[] readBlocking() {
        try {
            if (isConnected() && in != null) {
                byte[] readBytes = inHelper.readNextBytes(in);
                if (inHelper.isEndOfStreamReached()) {
                    isEndOfStreamReached = true;
                    return null;
                }
                LogHelper.getInstance().d(TAG, "Read new message.");
                return readBytes;
            } else {
                LogHelper.getInstance().e(TAG, "Could not read message as no connection exists");
            }
        } catch (Exception e) {
            LogHelper.getInstance().e(TAG, "Error while trying to read message.", e);
        }
        return null;
    }

    /**
     * Writes blocking to the socket that was set up in {@link #onConnected(Socket)}.
     * 
     * @param message
     *            the bytes that should be sent
     * @return true if the message was written successfully.
     * */
    public boolean writeBlocking(byte[] message) {

        try {
            if (isConnected() && out != null) {
                out.write(message);
                out.flush();
                LogHelper.getInstance().d(TAG, "Wrote message");
                InetAddress address = outgoingSocket.getInetAddress();
                int port = outgoingSocket.getPort();
                MultiNetworkAddress multiAddress = new MultiNetworkAddress();
                multiAddress.setIpAddress(address);
                multiAddress.setIpPort(port);

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
     * Closes the socket that was set up in {@link #onConnected(Socket)} and optionally a server socket.
     * 
     * @param serverSocket
     *            optional, can be null. A server socket that should be closed.
     * */
    public boolean closeSockets(ServerSocket serverSocket) {
        try {
            if (in != null) {
                in.close();
                LogHelper.getInstance().d(TAG, "Closed input stream from socket successfully.");
            }
            if (out != null) {
                out.close();
                LogHelper.getInstance().d(TAG, "Closed output stream to socket successfully.");
            }
            if (outgoingSocket != null) {
                // outgoingSocket.getInputStream().close();
                // outgoingSocket.getOutputStream().close();
                outgoingSocket.close();
                LogHelper.getInstance().d(TAG, "Closed remote socket successfully.");
            }
            if (serverSocket != null) {
                serverSocket.close();
                LogHelper.getInstance().d(TAG, "Closed own server socket successfully.");
            }
            return true;
        } catch (IOException e) {
            LogHelper.getInstance().e(TAG, "Error while closing socket.", e);
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Implement this method and call {@link #closeSockets(ServerSocket)} in it. This method should cancel the
     * connection.
     * */
    public abstract boolean disconnectAndTerminate();

    /**
     * @return the remote socket
     */
    public Socket getRemoteSocket() {
        return outgoingSocket;
    }

    /**
     * @return true if the end of the input stream was reached.
     */
    public boolean isEndOfStreamReached() {
        return isEndOfStreamReached;
    }

}

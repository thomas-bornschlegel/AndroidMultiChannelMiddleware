package de.lmu.mcm.network.wifi;

import java.net.InetAddress;
import java.net.Socket;

import android.app.Activity;
import android.content.Intent;
import de.lmu.mcm.helper.CancelableThread;
import de.lmu.mcm.helper.InterfaceAvailabilityChecker;
import de.lmu.mcm.helper.LogHelper;
import de.lmu.mcm.network.AbstractCommunicationModule;
import de.lmu.mcm.network.Enums;
import de.lmu.mcm.network.Enums.InterfaceIdentifier;
import de.lmu.mcm.network.Enums.MessageOrigin;
import de.lmu.mcm.network.Enums.Role;
import de.lmu.mcm.network.MultiNetworkAddress;
import de.lmu.mcm.network.NetworkDaemon;
import de.lmu.mcm.network.ProtocolMessage;
import de.lmu.mcm.network.ServiceDescription;

/**
 * Class that provides wifi communication methods.
 * 
 * @author Thomas Bornschlegel
 * 
 */
public class WifiCommunicator extends AbstractCommunicationModule {

    private String TAG = "WIFI ";
    // Objects that facilitate network communication:
    private JmdnsHelper jmdnsHelper = null;
    private WifiClient client = null;
    private WifiServer server = null;

    // Used to setup the connection:
    private ServiceDescription serviceDescription;
    private boolean isClient;
    private int maxRetries;
    private int timeoutInSeconds;
    private int serverPort;
    private InterfaceAvailabilityChecker availChecker = new InterfaceAvailabilityChecker();

    // Threads for async execution of tasks:
    private ServiceDiscoveryAdvertisingThread serviceDiscoveryAdvertisingThread;
    private ServiceDiscoveryListenerThread serviceDiscoveryListenerThread;
    private WaitForClientConnectionThread waitForClientConnectionThread;
    private ConnectToServerThread connectToServerThread;
    private WriteThread writeThread;
    private ReadThread readThread;
    private boolean isCanceled = false;

    public WifiCommunicator(Activity activity, NetworkDaemon daemon) {
        super(activity, daemon);
    }

    @Override
    public Enums.InterfaceIdentifier getInterfaceName() {
        return Enums.InterfaceIdentifier.WIFI;
    }

    @Override
    public void destroy(Activity activity) {
        isCanceled = true;
        terminateClientOrServer();
        cancelAllThreads();
        stopJmdns();
        this.serviceDescription = null;
        if (jmdnsHelper != null) {
            jmdnsHelper.closeJmDNS();
            jmdnsHelper = null;
        }
        notifyDaemonInterfaceDestroyed();
    }

    @Override
    public void setupConnection(final Activity activity, final ServiceDescription serviceDescription) {
        MultiNetworkAddress address = null;
        if (serviceDescription != null && serviceDescription.getAddressOfServer() != null) {
            address = serviceDescription.getAddressOfServer();
        } else {
            LogHelper.getInstance().e(TAG, "No server address given!");
        }

        if (availChecker.isInternetAccessAvailable(activity) && !isCanceled) {

            this.serviceDescription = serviceDescription;
            if (serviceDescription != null) {
                maxRetries = serviceDescription.getMaxConnectionAttemptsWifi();
                if (address != null) {
                    this.serverPort = address.getIpPort();
                }
            }
            if (serverPort == -1 || serverPort == 0) {
                serverPort = InterfaceAvailabilityChecker.getRandomPortNumber();
            }
            boolean useSD = serviceDescription.isUseServiceDiscoveryForWifi();
            // TODO find better solution:
            InterfaceAvailabilityChecker avail = new InterfaceAvailabilityChecker();
            boolean wifiOn = avail.isInterfaceEnabled(activity, InterfaceIdentifier.WIFI);
            useSD = useSD && wifiOn;

            maxRetries = serviceDescription.getMaxConnectionAttemptsWifi();
            timeoutInSeconds = serviceDescription.getTimeOutWifiInSeconds();
            isClient = serviceDescription.getRole() == Role.CLIENT;

            if (isClient) {
                client = new WifiClient();
                // CLIENT
                if (useSD) {
                    cancelThread(serviceDiscoveryListenerThread);
                    serviceDiscoveryListenerThread = new ServiceDiscoveryListenerThread(activity, serviceDescription);
                    serviceDiscoveryListenerThread.start();
                } else {
                    cancelThread(connectToServerThread);
                    connectToServerThread = new ConnectToServerThread(serviceDescription.getAddressOfServer(),
                            maxRetries, timeoutInSeconds, serverPort);
                    connectToServerThread.start();
                }

            } else {
                // SERVER
                server = new WifiServer();
                if (useSD) {
                    cancelThread(serviceDiscoveryAdvertisingThread);
                    serviceDiscoveryAdvertisingThread = new ServiceDiscoveryAdvertisingThread(activity,
                            serviceDescription);
                    serviceDiscoveryAdvertisingThread.start();
                } else {
                    cancelThread(waitForClientConnectionThread);
                    waitForClientConnectionThread = new WaitForClientConnectionThread();
                    waitForClientConnectionThread.start();
                }
            }
        } else {
            notifyDaemonConnectionSetupFailed(address);
            LogHelper.getInstance().e(TAG, "Can not setup connection: Not connected!");
        }

    }

    @Override
    public boolean sendData(Activity activity, byte[] data) {
        if (isReadyToExchangeData()) {
            cancelThread(writeThread);
            writeThread = new WriteThread(data);
            writeThread.start();
            return true;
        } else {
            LogHelper.getInstance().e(TAG, "Cannot send data because not connection is set up");
            notifyDaemonAboutSentData(null, false);
            return false;
        }
    }

    @Override
    public boolean listenForMessages(Activity activity) {
        cancelThread(readThread);
        readThread = new ReadThread();
        readThread.start();
        return true;
    }

    /**
     * Called from {@link JmdnsHelper} when a new service is discovered. Starts the connection establishement if we are
     * not already connected.
     * */
    public void onServiceDiscovered(ServiceDescription serviceDescription) {
        if (isCanceled) {
            LogHelper.getInstance().d(TAG,
                    "Did not start a connection to new service because the interface was already canceled.");
        } else if (client != null && !client.isConnected() && !client.isCurrentlyConnecting()) {
            LogHelper.getInstance().d(TAG, "Found service description, trying to connect...");
            cancelThread(connectToServerThread);
            connectToServerThread = new ConnectToServerThread(serviceDescription.getAddressOfServer(), maxRetries,
                    timeoutInSeconds, serviceDescription.getAddressOfServer().getIpPort());
            connectToServerThread.start();
        } else {
            LogHelper.getInstance().d(TAG,
                    "Did not start a connection to new service because another connection is established already");
        }
    }

    /**
     * Thread that waits for a client to connect.
     * 
     */
    public class WaitForClientConnectionThread extends CancelableThread {

        private Socket clientSocket;

        @Override
        public void run() {
            LogHelper.getInstance().d(TAG, "Waiting for client to connect...");
            LogHelper.getInstance().startToMeasureTime(TAG, 532);
            if (isCanceled) {
                LogHelper.getInstance().d(TAG,
                        "Did not wait for the client to connect because the interface was already canceled.");
            } else if (server != null && !server.isConnected() && !server.isCurrentlyWaitingForClientToConnect()) {
                clientSocket = server.waitForClientToConnectBlocking(serverPort, timeoutInSeconds, maxRetries);
                if (clientSocket != null && clientSocket.isConnected()) {
                    MultiNetworkAddress multiAddress = new MultiNetworkAddress();
                    multiAddress.setIpAddress(clientSocket.getInetAddress());
                    multiAddress.setIpPort(clientSocket.getPort());
                    LogHelper.getInstance().d(TAG,
                            "Client connected! " + multiAddress.getIpAddress() + ":" + multiAddress.getIpPort());
                    listenForMessages(null);
                    notifyDaemonConnectionIsSetUp(multiAddress);
                } else {
                    LogHelper.getInstance().e(TAG, "Client did not connect.");
                    notifyDaemonConnectionSetupFailed(null);
                }

            } else {
                LogHelper.getInstance().e(TAG, "Already have client connection or are already waiting.");
            }
            LogHelper.getInstance().stopToMeasureTime(TAG, 532);
        }

        public void cancel() {
            if (clientSocket != null) {
                try {
                    clientSocket.close();
                } catch (Exception e) {
                    LogHelper.getInstance().e(TAG, "Error closing client socket.");
                }
            }
            hardCancel();
        }

    };

    /**
     * Thread which starts to advertise the service with {@link JmdnsHelper}.
     * */
    public class ServiceDiscoveryAdvertisingThread extends CancelableThread {

        private Activity activity;
        private ServiceDescription sd;

        /**
         * @param activity
         * @param serviceDescription
         *            the service description that should be advertised
         */
        public ServiceDiscoveryAdvertisingThread(Activity activity, ServiceDescription serviceDescription) {
            this.activity = activity;
            this.sd = serviceDescription;
        }

        @Override
        public void run() {
            LogHelper.getInstance().startToMeasureTime(TAG, 3201612);
            jmdnsHelper = new JmdnsHelper(activity, WifiCommunicator.this);

            jmdnsHelper.advertiseMulticastService(sd.getName(), serverPort, sd.getUuid().toString(),
                    sd.getDescription());

            if (server == null) {
                server = new WifiServer();
            }
            server.setJmdns(jmdnsHelper);
            cancelThread(waitForClientConnectionThread);
            if (!isCanceled) {
                waitForClientConnectionThread = new WaitForClientConnectionThread();
                waitForClientConnectionThread.start();
            }
            LogHelper.getInstance().stopToMeasureTime(TAG, 3201612);
        }

        @Override
        public void cancel() {
            hardCancel();
        }
    }

    /**
     * Thread that starts listening for service discovery records.
     * */
    public class ServiceDiscoveryListenerThread extends CancelableThread {

        private Activity activity;
        private ServiceDescription serviceDescription;
        private boolean canceled = false;

        /**
         * @param activity
         * @param serviceDescription
         *            the service description that should match the advertised service
         */
        public ServiceDiscoveryListenerThread(Activity activity, ServiceDescription serviceDescription) {
            this.activity = activity;
            this.serviceDescription = serviceDescription;
        }

        @Override
        public void run() {
            jmdnsHelper = new JmdnsHelper(activity, WifiCommunicator.this);
            // This method calls onServiceDiscovered if it finds the server
            jmdnsHelper.listenForMulticastServices(serviceDescription);
            int intervalInSeconds = 5;
            while (!canceled && jmdnsHelper != null && jmdnsHelper.queryForService() && !isCanceled) {
                try {
                    LogHelper.getInstance().i(TAG,
                            "Sleeping " + intervalInSeconds + " seconds before querying for DNS services again...");
                    Thread.sleep(intervalInSeconds * 1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void cancel() {
            canceled = true;
            hardCancel();
        }
    }

    /**
     * Thread that starts to connect to a server.
     * */
    public class ConnectToServerThread extends CancelableThread {

        private MultiNetworkAddress address;
        private int maxRetries = serviceDescription.getMaxConnectionAttemptsWifi();
        private int timeoutInSeconds = serviceDescription.getTimeOutWifiInSeconds();
        private int serverPort;

        /**
         * @param address
         *            the address of the server
         * @param maxRetries
         *            the maximum number of retries for connecting
         * @param timeoutInSeconds
         *            the timeout for each retry, 0 for no timeout
         * @param serverPort
         *            the port of the server
         */
        public ConnectToServerThread(MultiNetworkAddress address, int maxRetries, int timeoutInSeconds, int serverPort) {
            this.address = address;
            this.maxRetries = maxRetries;
            this.timeoutInSeconds = timeoutInSeconds;
            this.serverPort = serverPort;
        }

        @Override
        public void run() {
            LogHelper.getInstance().d(TAG,
                    "Trying to connect to server " + address.getIpAddress().getHostAddress() + " port " + serverPort);
            if (isCanceled) {
                LogHelper.getInstance().d(TAG, "Did not connect to server because the interface was already canceled.");
            } else if (client != null && !client.isConnected() && !client.isCurrentlyConnecting()) {
                boolean connected = client.connectToServerBlocking(address, timeoutInSeconds, maxRetries, serverPort);
                if (client != null && connected) {
                    Socket socket = client.getRemoteSocket();
                    MultiNetworkAddress address = new MultiNetworkAddress();
                    address.setIpAddress(socket.getInetAddress());
                    address.setIpPort(socket.getPort());
                    listenForMessages(null);
                    notifyDaemonConnectionIsSetUp(address);
                    jmdnsHelper.stopDiscoveringServices();
                } else {
                    LogHelper.getInstance().e(TAG, "Could not connect to server");
                    notifyDaemonConnectionSetupFailed(serviceDescription.getAddressOfServer());
                    if (client != null) {
                        client.setCanceled(true);
                        client = null;
                    }
                    client = new WifiClient();
                }
            } else {
                LogHelper.getInstance().d(TAG, "Client is already connected");
            }
        }

        public void cancel() {
            if (client != null) {
                client.setCanceled(true);
            }
            hardCancel();
        }

    }

    /**
     * Thread to write data to the connected client/server.
     */
    public class WriteThread extends CancelableThread {

        private boolean isServer;
        private byte[] message;

        /**
         * @param message
         *            the message to write
         */
        public WriteThread(byte[] message) {
            this.message = message;
        }

        @Override
        public void run() {
            isServer = serviceDescription.getRole() == Role.SERVER;
            String role = isServer ? "server" : "client";
            boolean success = false;
            if (serviceDescription.getRole() == Role.SERVER) {
                success = server.writeBlocking(message);
            } else {
                success = client.writeBlocking(message);
            }
            if (success) {
                LogHelper.getInstance().d(TAG, "Wrote message as  " + role + ": " + message);
                try {
                    MultiNetworkAddress address = new MultiNetworkAddress();
                    Socket socket = null;
                    if (serviceDescription.getRole() == Role.SERVER) {
                        socket = server.getRemoteSocket();
                    } else {
                        socket = client.getRemoteSocket();
                    }
                    address.setIpAddress(socket.getInetAddress());
                    address.setIpPort(socket.getPort());
                    ProtocolMessage protocolMessage = new ProtocolMessage(MessageOrigin.SELF, address, message);
                    notifyDaemonAboutSentData(protocolMessage, true);
                } catch (Exception e) {
                    // Just in case the socket is closed while we try to notify the daemon
                    LogHelper.getInstance().e(TAG, "Error trying to notify daemon that message was written.", e);
                    notifyDaemonAboutSentData(new ProtocolMessage(MessageOrigin.SELF, message), false);
                }
            } else {
                LogHelper.getInstance().e(TAG, "Could not write message as  " + role + ": " + message);
                notifyDaemonAboutSentData(new ProtocolMessage(MessageOrigin.SELF, message), false);
            }
        }

        public void cancel() {
            hardCancel();
        }
    }

    /**
     * Thread to read data from the connected client/server.
     */
    public class ReadThread extends CancelableThread {

        boolean listenForMessages = true;

        @Override
        public void run() {
            while (listenForMessages && !isCanceled) {
                boolean isServer = serviceDescription.getRole() == Role.SERVER;
                String role = isServer ? "server" : "client";
                byte[] readMsg = null;
                InetAddress remoteAddress = null;
                int port = -1;

                AbstractWifiReaderWriter reader = null;
                if (isServer) {
                    reader = server;
                } else {
                    reader = client;
                }
                if (reader != null) {
                    readMsg = reader.readBlocking();
                    if (reader != null && reader.getRemoteSocket() != null && reader.getRemoteSocket().isConnected()) {
                        remoteAddress = reader.getRemoteSocket().getInetAddress();
                        port = reader.getRemoteSocket().getPort();
                    }
                    if (!reader.isConnected()) {
                        listenForMessages = false;
                        LogHelper.getInstance().e(TAG, "Stopping to listen for messages because no connection exists");
                    } else if (reader.isEndOfStreamReached()) {
                        listenForMessages = false;
                        LogHelper.getInstance().e(TAG,
                                "Stopping to listen for messages because end of stream is reached");
                    }
                }
                if (readMsg != null) {
                    LogHelper.getInstance().d(TAG, "Read message as  " + role + ": " + readMsg);
                    MultiNetworkAddress address = new MultiNetworkAddress();
                    address.setIpAddress(remoteAddress);
                    address.setIpPort(port);
                    ProtocolMessage protocolMessage = new ProtocolMessage(MessageOrigin.REMOTE, address, readMsg);
                    notifyDaemonAboutReceivedData(protocolMessage);
                } else {
                    LogHelper.getInstance().e(TAG, "Could not read message as  " + role);
                }
            }
        }

        public void cancel() {
            listenForMessages = false;
            hardCancel();
        }
    }

    @Override
    public boolean onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        return false;
    }

    @Override
    public boolean isReadyToExchangeData() {
        if (this.serviceDescription == null) {
            return false;
        }
        if (isClient) {
            return client != null && client.isConnected();
        } else {
            return server != null && server.isConnected();
        }
    }

    @Override
    public void stopCurrentConnection(Activity activity) {
        cancelAllThreads();
        stopJmdns();
        terminateClientOrServer();
        this.serviceDescription = null;
        notifyDaemonConnectionTerminated();
    }

    /**
     * Terminates the current connection to the server/client.
     * */
    private void terminateClientOrServer() {
        if (isClient) {
            if (client != null) {
                client.disconnectAndTerminate();
                client = null;
            }
        } else {
            if (server != null) {
                server.disconnectAndTerminate();
                server = null;
            }
        }
    }

    /**
     * Stops the service advertising/listening on the {@link JmdnsHelper}.
     */
    private void stopJmdns() {
        if (jmdnsHelper != null) {
            if (isClient) {
                jmdnsHelper.stopDiscoveringServices();
            } else {
                jmdnsHelper.stopAdvertisingServices();
            }
        }
    }

    /**
     * Cancels all threads that are currently running.
     * */
    private void cancelAllThreads() {
        cancelThread(serviceDiscoveryAdvertisingThread);
        cancelThread(serviceDiscoveryListenerThread);
        cancelThread(waitForClientConnectionThread);
        cancelThread(connectToServerThread);
        cancelThread(writeThread);
        cancelThread(readThread);
    }

    @Override
    public void onResume(Activity activity) {
        // Nothing to do here
    }

    @Override
    public void onNewIntent(Activity activity, Intent data) {
        // Nothing to do here
    }

    @Override
    public void onPause(Activity activity) {
        // Nothing to do here
    }

    public boolean isCanceled() {
        return isCanceled;
    }
}

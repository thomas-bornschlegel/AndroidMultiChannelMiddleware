package de.lmu.mcm.network.bluetooth;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.ParcelUuid;
import android.util.Log;
import de.lmu.mcm.helper.ByteConverter;
import de.lmu.mcm.helper.CancelableThread;
import de.lmu.mcm.helper.LogHelper;
import de.lmu.mcm.network.AbstractCommunicationModule;
import de.lmu.mcm.network.Enums;
import de.lmu.mcm.network.Enums.MessageOrigin;
import de.lmu.mcm.network.Enums.Role;
import de.lmu.mcm.network.MultiNetworkAddress;
import de.lmu.mcm.network.NetworkDaemon;
import de.lmu.mcm.network.ProtocolMessage;
import de.lmu.mcm.network.ServiceDescription;
import de.lmu.mcm.network.bluetooth.BroadcastReceiverBluetoothDiscovery.BluetoothDiscoveryListener;

/**
 * The interface that manages communication via bluetooth.
 * 
 * @author Thomas Bornschlegel
 * 
 */
public class BluetoothCommunicator extends AbstractCommunicationModule implements BluetoothDiscoveryListener {

    // Based on the Bluetooth Chat App from Googles examples:
    // https://android.googlesource.com/platform/development/+/master/samples/BluetoothChat/

    private static final String TAG = "Bluetooth ";
    private BluetoothAdapter adapter = null;
    private Activity activity;
    private boolean isCanceled = false;

    // Variables and threads to establish a connection:
    private WaitForClientConnectionThread waitForClientConnectionThread;
    private ConnectToServerThread connectToServerThread;
    private ServiceDescription serviceDescription;

    // Variables and threads to read and write data:
    private BluetoothReaderWriter readerWriter;
    private ReadThread readThread;
    private WriteThread writeThread;

    // The BroadcastReceiver that listens for discovered devices
    private BroadcastReceiverBluetoothDiscovery broadcastReceiverDiscovery = null;
    private boolean isBroadcastReceiverRegistered = false;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0; // we're doing nothing
    public static final int STATE_LISTEN = 1; // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3; // now connected to a remote device

    // Actual state of the connection:
    private int connectionState;

    public BluetoothCommunicator(Activity activity, NetworkDaemon daemon) {
        super(activity, daemon);
        adapter = BluetoothAdapter.getDefaultAdapter();
    }

    @Override
    public Enums.InterfaceIdentifier getInterfaceName() {
        return Enums.InterfaceIdentifier.BLUETOOTH;
    }

    @Override
    public synchronized void destroy(Activity activity) {
        LogHelper.getInstance().d(TAG, "Starting to destroy bluetooth interface...");
        isCanceled = true;
        adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            adapter.cancelDiscovery();
        }

        closeActiveSocket();
        cancelThread(writeThread);
        cancelThread(readThread);

        if (waitForClientConnectionThread != null && waitForClientConnectionThread.getSocket() != null) {
            try {
                waitForClientConnectionThread.getSocket().close();
            } catch (Exception e) {
                LogHelper.getInstance().e(TAG, "Tried to close bluetooth server socket..", e);
            }
        }

        if (connectToServerThread != null && connectToServerThread.getSocket() != null) {
            try {
                connectToServerThread.getSocket().close();
            } catch (Exception e) {
                LogHelper.getInstance().e(TAG, "Tried to close bluetooth socket as client.", e);
            }
        }

        cancelThread(waitForClientConnectionThread);
        cancelThread(connectToServerThread);

        setState(STATE_NONE);

        unregisterReceiver(activity);
        LogHelper.getInstance().d(TAG, "Destroyed bluetooth interface!");

    }

    @Override
    public boolean onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        return false;
    }

    @Override
    public boolean sendData(Activity activity, byte[] data) {
        if (getConnectionState() == STATE_CONNECTED) {
            cancelThread(writeThread);
            writeThread = new WriteThread(data);
            writeThread.start();
            return true;
        } else {
            LogHelper.getInstance().e(TAG,
                    "Did not send data because no connection was established. State: " + getConnectionState());
            notifyDaemonAboutSentData(null, false);
            return false;
        }
    }

    @Override
    public boolean listenForMessages(Activity activity) {
        if (isReadyToExchangeData()) {
            return startListeningForMessages();
        } else {
            LogHelper.getInstance().e(TAG, "Tried to read messages but no connection exists.");
            return false;
        }
    }

    @Override
    public void stopCurrentConnection(Activity activity) {
        LogHelper.getInstance().d(TAG, "Stopping current connection");
        closeActiveSocket();
        cancelThread(readThread);
        cancelThread(writeThread);
    }

    @Override
    public boolean isReadyToExchangeData() {
        return getConnectionState() == STATE_CONNECTED;
    }

    @Override
    public synchronized void setupConnection(Activity activity, ServiceDescription serviceDescription) {
        this.serviceDescription = serviceDescription;
        if (activity != null) {
            this.activity = activity;
        }

        if (serviceDescription.getRole() == Role.CLIENT) {
            if (activity != null) {
                registerBroadcastReceivers(activity);
            }
            // Search for matching devices.
            // A connection will be established in callbacks from the discovery:
            doDiscovery();
        } else {
            // Make device discoverable, so that the service can be retrieved
            askUserToMakeBluetoothDiscoverable(activity, serviceDescription.getTimeOutBluetoothInSeconds());
            // Wait for device to connect
            cancelThread(waitForClientConnectionThread);
            waitForClientConnectionThread = new WaitForClientConnectionThread(serviceDescription);
            if (waitForClientConnectionThread != null) {
                setState(STATE_LISTEN);
                waitForClientConnectionThread.start();
            }
        }

    }

    /**
     * Starts the thread {@link ReadThread} to read new messages in an asynchronous way.
     * */
    private boolean startListeningForMessages() {
        if (getConnectionState() == STATE_CONNECTED) {
            cancelThread(readThread);
            readThread = new ReadThread();
            readThread.start();
            return true;
        } else {
            LogHelper.getInstance().e(TAG, "Did not read data because no connection was established");
            return false;
        }
    }

    /**
     * Closes all active sockets.
     * */
    private void closeActiveSocket() {
        LogHelper.getInstance().d(TAG, "Closing active sockets");
        if (readerWriter != null) {
            readerWriter.closeConnection();
        }
        setState(STATE_NONE);
    }

    /**
     * Initializes a connection to the given device by starting the {@link ConnectToServerThread}.
     * 
     * @param device
     *            the device to connect to
     * */
    public void initializeAsyncConnectionToDevice(BluetoothDevice device) {
        LogHelper.getInstance().d(TAG, "Connecting to: " + device.getAddress());

        // Cancel any thread attempting to make a connection
        if (getConnectionState() == STATE_CONNECTING) {
            cancelThread(connectToServerThread);
        }

        // Cancel any thread currently running a connection
        cancelThread(readThread);
        cancelThread(writeThread);
        closeActiveSocket();

        // Start the thread to connect with the given device
        connectToServerThread = new ConnectToServerThread(device);
        connectToServerThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * Start the {@link ConnectedThread} that manages the Bluetooth connection.
     * 
     * @param socket
     *            The BluetoothSocket on which the connection was made
     * @param device
     *            The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {

        LogHelper.getInstance().d(TAG, "Reading connection to device: " + device.getName());
        // Cancel the thread that completed the connection
        cancelThread(connectToServerThread);

        // Cancel any thread currently running a connection
        cancelThread(readThread);
        cancelThread(writeThread);

        // Cancel the accept thread because we only want to connect to one
        // device
        cancelThread(waitForClientConnectionThread);

        MultiNetworkAddress address = new MultiNetworkAddress();
        address.setBluetoothAddressFromString(device.getAddress());
        // Initialize the reader/writer
        try {
            readerWriter = new BluetoothReaderWriter(this, socket);
            LogHelper.getInstance().d(TAG, "Connected to device: " + device.getName());
            setState(STATE_CONNECTED);
            notifyDaemonConnectionIsSetUp(address);
            listenForMessages(null);
        } catch (IOException e) {
            notifyDaemonConnectionSetupFailed(address);
            LogHelper.getInstance().e(TAG, "Could not create bluetooth reader/writer", e);
        }
    }

    /**
     * This thread runs while listening for incoming connections. It behaves like a server-side client. It runs until a
     * connection is accepted (or until cancelled). If the connection fails we wait 5 seconds and try again until a
     * connection is established or until the maximum connection attempts are reached
     */
    private class WaitForClientConnectionThread extends CancelableThread {
        // The local server socket
        private final BluetoothServerSocket serverSocket;
        private int timeoutInSeconds = 0;
        private int maxRetries = 1;
        private boolean isCanceled = false;

        public WaitForClientConnectionThread(ServiceDescription service) {
            BluetoothServerSocket tmp = null;
            timeoutInSeconds = service.getTimeOutBluetoothInSeconds();
            maxRetries = service.getMaxConnectionAttemptsBluetooth();
            // Create a new listening server socket
            try {
                // 1. Secure socket (with pairing)
                // tmp = adapter.listenUsingRfcommWithServiceRecord(NAME_SECURE, MY_UUID_SECURE);
                // 2. Insecure socket (without pairing)
                LogHelper.getInstance().d(TAG,
                        "Starting to advertise bluetooth service with UUID: " + service.getUuid());
                tmp = adapter.listenUsingInsecureRfcommWithServiceRecord(service.getName(), service.getUuid());
            } catch (IOException e) {
                LogHelper.getInstance().e(TAG, "Socket listen() failed", e);
            }
            if (tmp != null) {
                LogHelper.getInstance().d(TAG, "Successfully created bluetooth server socket.");
            } else {
                LogHelper.getInstance().e(TAG, "Error creating bluetooth server socket!");
            }
            serverSocket = tmp;
        }

        public void run() {
            LogHelper.getInstance().i(TAG, "BEGIN waitForClientThread");

            setName("AcceptThread");

            BluetoothSocket socket = null;

            // Listen to the server socket as long as we're not connected
            while (!BluetoothCommunicator.this.isCanceled && !isCanceled && serverSocket != null
                    && getConnectionState() != STATE_CONNECTED && maxRetries > 0) {
                maxRetries--;
                try {
                    LogHelper.getInstance().d(TAG, "Waiting " + timeoutInSeconds + " seconds for client to connect...");
                    LogHelper.getInstance().startToMeasureTime(TAG, 3200925);
                    if (timeoutInSeconds == 0) {
                        socket = serverSocket.accept();
                    } else {
                        int timeoutInMs = timeoutInSeconds * 1000;
                        socket = serverSocket.accept(timeoutInMs);
                    }
                    LogHelper.getInstance().stopToMeasureTime(TAG, 3200925);
                    Log.d(TAG, "Done waiting");
                } catch (IOException e) {
                    LogHelper.getInstance().e(TAG, "Socket accept() failed", e);
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized (BluetoothCommunicator.this) {
                        switch (getConnectionState()) {
                        case STATE_LISTEN:
                        case STATE_CONNECTING:
                            // Situation normal. Start the connected thread.
                            maxRetries = 0;
                            connected(socket, socket.getRemoteDevice());
                            break;
                        case STATE_NONE:
                        case STATE_CONNECTED:
                            // Either not ready or already connected. Terminate
                            // new socket.
                            LogHelper.getInstance().e(TAG, "Already connected");
                            try {
                                socket.close();
                            } catch (IOException e) {
                                LogHelper.getInstance().e(TAG, "Could not close unwanted socket", e);
                            }
                            break;
                        }
                    }
                } else {
                    LogHelper.getInstance().e(TAG, "No connection established");
                    if (!isCanceled && socket == null || !isConnected(socket)) {
                        int seconds = 5;
                        LogHelper.getInstance().e(TAG,
                                "Could not connect with client. Sleeping " + seconds + " seconds");
                        try {
                            Thread.sleep(seconds * 1000);
                        } catch (InterruptedException e) {
                            LogHelper.getInstance().e(
                                    TAG,
                                    "Error while pausing " + seconds
                                            + " seconds between connection attempts in WaitForClientConnectionThread.",
                                    e);
                        }
                    }
                }
            }

        }

        public BluetoothServerSocket getSocket() {
            return serverSocket;
        }

        public void cancel() {
            LogHelper.getInstance().d(TAG, "Canceling WaitForClientConnectionThread");
            try {
                isCanceled = true;
                if (serverSocket != null) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                LogHelper.getInstance().e(TAG, "Socket close() of server failed", e);
            }
            hardCancel();
        }
    }

    /**
     * This thread runs while attempting to make an outgoing connection with a device. If the connection fails we start
     * discovery again.
     */
    private class ConnectToServerThread extends CancelableThread {
        private BluetoothSocket socket;
        private final BluetoothDevice device;
        private boolean isCanceled = false;

        public ConnectToServerThread(BluetoothDevice device) {
            this.device = device;
        }

        public void run() {

            LogHelper.getInstance().i(TAG, "BEGIN connectThread");
            setName("ConnectThread");

            // Always cancel discovery because it will slow down a connection
            adapter.cancelDiscovery();

            while (!BluetoothCommunicator.this.isCanceled && !isCanceled && !isConnected(socket)) {

                // Make a connection to the BluetoothSocket
                try {

                    // Since API Level 10 (Android 2.3.3) the insecureConnection is available:
                    // http://developer.android.com/about/versions/android-2.3.3.html
                    // It allows the pairing of two devices without user interaction. See also:
                    // http://stackoverflow.com/questions/5885438/bluetooth-pairing-without-user-confirmation

                    // 1. Insecure connection (without pairing)
                    socket = device.createInsecureRfcommSocketToServiceRecord(serviceDescription.getUuid());

                    // 2. Secure connection (with pairing)
                    // tmp = device.createRfcommSocketToServiceRecord(MY_UUID_SECURE);

                    // 3. To connect to the serial port
                    // UUID id =
                    // UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
                    // tmp = device.createRfcommSocketToServiceRecord(id);
                    // ID für Serial Port
                    // http://developer.android.com/reference/android/bluetooth/BluetoothDevice.html#createRfcommSocketToServiceRecord(java.util.UUID)

                    // 4. To connect with a fixed port and without SDP (this method is not always present)
                    // Method m = device.getClass().getMethod("createRfcommSocket", new Class[] { int.class });
                    // tmp = (BluetoothSocket) m.invoke(device, 1);

                    socket.connect();

                } catch (IOException e) {
                    // Close the socket
                    try {
                        socket.close();
                    } catch (IOException e2) {
                        LogHelper.getInstance().e(TAG, "unable to close() socket during connection failure", e2);
                    }
                    LogHelper.getInstance().e(TAG, "Connection failed.", e);
                    notifyDaemonConnectionSetupFailed(null);
                    // return;
                }

                if (!isConnected(socket) && !isCanceled) {
                    int seconds = 5;
                    LogHelper.getInstance().e(TAG, "Could not connect with server. Sleeping " + seconds + " seconds");
                    try {
                        Thread.sleep(seconds * 1000);
                    } catch (InterruptedException e) {
                        LogHelper.getInstance().e(
                                TAG,
                                "Error while pausing " + seconds
                                        + " seconds between connection attempts in ConnectToServerThread.", e);
                    }
                    setState(STATE_NONE);
                    doDiscovery();
                    return;
                }
            }

            // Reset the ConnectThread because we're done
            synchronized (BluetoothCommunicator.this) {
                connectToServerThread = null;
            }

            MultiNetworkAddress address = new MultiNetworkAddress();
            address.setBluetoothDevice(socket.getRemoteDevice());

            // Start the connected thread
            connected(socket, device);

        }

        public BluetoothSocket getSocket() {
            return socket;
        }

        public void cancel() {
            LogHelper.getInstance().d(TAG, "Canceling ConnectToServerThread");
            try {
                isCanceled = true;
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException e) {
                LogHelper.getInstance().e(TAG, "close() of socket failed", e);
            }
            hardCancel();
        }
    }

    /**
     * Helper method to check if the BluetoothSocket is still connected. For devices that run with an api version >= 14
     * we can simply return the result of {@link android.bluetooth.BluetoothSocket#isConnected()
     * BluetoothSocket.isConnected}. For lower apis we can only check if the socket is not null.
     * 
     * @param socket
     *            the socket that should be checked
     * @return true if the socket is connected
     * */
    @SuppressLint("NewApi")
    private boolean isConnected(BluetoothSocket socket) {
        int apiVersion = android.os.Build.VERSION.SDK_INT;
        if (apiVersion >= 14) {
            return socket != null && socket.isConnected();
        } else {
            return socket != null;
        }
    }

    /**
     * Thread that reads from the connected socket.
     */
    private class ReadThread extends CancelableThread {

        private boolean isCanceled = false;

        @Override
        public void run() {
            LogHelper.getInstance().d(TAG, "Started reading bluetooth stream");

            while (!isCanceled && readerWriter != null && readerWriter.isConnected()
                    && !readerWriter.isEndOfStreamReached()) {
                byte[] msg = readerWriter.readBlocking();
                if (msg != null) {
                    if (isCanceled) {
                        LogHelper.getInstance().d(TAG,
                                "Read a message but the thread was canceled, we return it anyway.");
                    }
                    MultiNetworkAddress address = new MultiNetworkAddress();
                    address.setBluetoothDevice(readerWriter.getRemoteDevice());
                    ProtocolMessage protocolMessage = new ProtocolMessage(MessageOrigin.REMOTE, address, msg);
                    notifyDaemonAboutReceivedData(protocolMessage);
                } else {
                    LogHelper.getInstance().e(TAG, "Read message was null");
                    // notifyDaemonAboutReceivedData(null);
                }
            }
            LogHelper.getInstance().d(TAG, "Finished reading bluetooth stream");
        }

        public void cancel() {
            LogHelper.getInstance().d(TAG, "Canceling ReadThread");
            isCanceled = true;
            hardCancel();
        }
    }

    /**
     * Thread that writes to the connected socket.
     */
    private class WriteThread extends CancelableThread {

        private byte[] message;
        private boolean isCanceled = false;

        public WriteThread(byte[] message) {
            this.message = message;
        }

        public void run() {
            if (!isCanceled && readerWriter != null) {
                boolean written = readerWriter.writeBlocking(message);
                MultiNetworkAddress address = new MultiNetworkAddress();
                address.setBluetoothDevice(readerWriter.getRemoteDevice());
                ProtocolMessage protocolMessage = new ProtocolMessage(MessageOrigin.SELF, address, message);
                notifyDaemonAboutSentData(protocolMessage, written);
            }
        }

        public void cancel() {
            LogHelper.getInstance().d(TAG, "Canceling WriteThread");
            isCanceled = true;
            hardCancel();
        }
    }

    /**
     * Set the current state of the bluetooth connection
     * 
     * @param state
     *            An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        LogHelper.getInstance().d(TAG, "setState() " + connectionState + " -> " + state);
        connectionState = state;
    }

    /**
     * Connects to the given device if it either advertises a service with the UUID of the service description or if it
     * has the bluetooth address of the service description and advertises an according service.
     * 
     * @param device
     *            the discovered device
     * @param uuids
     *            the uuids that the discovered device advertises (can be null)
     * @param btAddressToFind
     *            the address of the bluetooth device to connect to (can be null)
     */
    private void connectToDeviceIfItMatchesService(BluetoothDevice device, List<ParcelUuid> uuids,
            byte[] btAddressToFind) {
        boolean foundDevice = false;
        if (btAddressToFind != null) {
            String deviceFoundAsString = device.getAddress().replace(":", "");
            byte[] deviceFound = ByteConverter.hexStringToByteArray(deviceFoundAsString);
            if (Arrays.equals(btAddressToFind, deviceFound)) {
                LogHelper.getInstance().d(TAG, "Found matching device.");
                foundDevice = true;
            } else {
                LogHelper.getInstance().e(TAG,
                        "Found device is not the same as the one specified in the service description.");
            }
        } else {
            LogHelper.getInstance().e(TAG, "No bluetooth Address given in service description.");
        }

        // Second approach with uuids.
        // XXX This does not work because android does not return any uuids!!
        if (serviceDescription != null && uuids != null) {
            String serviceUUID = serviceDescription.getUuid().toString();

            for (ParcelUuid uuid : uuids) {
                String currentUUID = uuid.getUuid().toString();
                if (currentUUID.equals(serviceUUID)) {
                    LogHelper.getInstance().d(TAG,
                            "UUID: " + currentUUID + " of device: " + device.getName() + " matched!");
                    foundDevice = true;
                    break;
                } else {
                    LogHelper.getInstance().d(TAG,
                            "UUID: " + currentUUID + " of device: " + device.getName() + " did not match.");
                }
            }
        }
        if (foundDevice) {
            LogHelper.getInstance().d(TAG, "Found matching device to connect: " + device.getAddress());
            if (isCanceled) {
                LogHelper.getInstance().d(TAG, "Did not connect to device because we are already canceled.");
            } else if (getConnectionState() != STATE_CONNECTING && getConnectionState() != STATE_CONNECTED) {
                initializeAsyncConnectionToDevice(device);
            } else {
                LogHelper.getInstance().e(TAG,
                        "Did not connect to the found device as we are already connected or connecting.");
            }
        } else {
            LogHelper.getInstance().e(TAG, "Device did not match: " + device.getAddress());
        }
    }

    /**
     * Called when the connection of the device was lost. Tries to reestablish the connection.
     */
    public void onConnectionLost() {
        closeActiveSocket();
        cancelThread(readThread);
        cancelThread(writeThread);
        LogHelper.getInstance().e(TAG, "Connection lost. Trying to reestablish connection...");
        setupConnection(activity, serviceDescription);
        notifyDaemonConnectionTerminated();
    }

    /**
     * Asks the user to make the device discoverable. This is necessary when the device acts as the server and needs to
     * be discovered by other devices.
     * 
     * @param activity
     *            the activity performing the request
     * @param seconds
     *            how many seconds the device should be discoverable
     */
    public void askUserToMakeBluetoothDiscoverable(Activity activity, int seconds) {
        if (isCanceled) {
            // Do nothing
        } else if (activity != null) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, seconds);
            activity.startActivity(discoverableIntent);
        } else {
            LogHelper.getInstance().e(TAG,
                    "Could not ask user to make bluetooth discoverable because the activity was null");
        }

        // Notizen zu verschiedenen Sichtbarkeitsmodi:

        // Inquiry Scan On:
        // Gerät ist für andere sichtbar (discoverable). Ohne Page Scan lässt es aber keine Verbindungen zu

        // Page Scan On:
        // Gerät ist nicht sichtbar, lässt aber Verbindungsversuche von Geräten zu, die seine Adresse kennen

        // Sind beide Scans aus, kann das Gerät trotzdem outgoing connections herstellen.

    }

    /**
     * Starts device discover with the BluetoothAdapter. This is draining a lot of battery!
     */
    public void doDiscovery() {
        LogHelper.getInstance().d(TAG, "Starting bluetooth discovery.");

        // If we're already discovering, stop it
        if (adapter.isDiscovering()) {
            LogHelper.getInstance().d(TAG, "Canceling earlier call for discovery...");
            adapter.cancelDiscovery();
        }

        // Request discover from BluetoothAdapter
        boolean success = adapter.startDiscovery();
        LogHelper.getInstance().d(TAG, "Started discovery: " + success);
    }

    /**
     * Registers the broadcast receivers for finding devices and services (services only with api versions >= 15). If
     * the receivers are already registered this method has no effect.
     * 
     * @param activity
     */
    @SuppressLint("InlinedApi")
    public synchronized void registerBroadcastReceivers(Activity activity) {
        if (isCanceled) {
            // Do nothing
            LogHelper.getInstance().d(TAG, "Already canceled. Did not register receivers.");
        } else if (!isBroadcastReceiverRegistered) {
            int apiVersion = android.os.Build.VERSION.SDK_INT;
            broadcastReceiverDiscovery = new BroadcastReceiverBluetoothDiscovery(this);

            IntentFilter filter = new IntentFilter();
            // Register for broadcasts when a device is discovered
            filter.addAction(BluetoothDevice.ACTION_FOUND);
            // Register for broadcasts when discovery has finished
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            if (apiVersion >= 15) {
                // Register for broadcasts when new UUIDs are found (works only in APIs > 14)
                filter.addAction(BluetoothDevice.ACTION_UUID);
            }
            activity.registerReceiver(broadcastReceiverDiscovery, filter);
            LogHelper.getInstance().d(TAG, "Registered Receiver");
            isBroadcastReceiverRegistered = true;
        } else {
            LogHelper.getInstance().e(TAG, "Receiver already registered!");
        }
    }

    /**
     * Unregisters the broadcast receivers (if there are any registered).
     * 
     * @param activity
     */
    public synchronized void unregisterReceiver(Activity activity) {
        if (isBroadcastReceiverRegistered) {
            try {
                activity.unregisterReceiver(broadcastReceiverDiscovery);
                broadcastReceiverDiscovery = null;
                isBroadcastReceiverRegistered = false;
                LogHelper.getInstance().d(TAG, "Unregistered Receiver");
            } catch (Exception e) {
                if (e.getMessage().contains("Receiver not registered")) {
                    LogHelper.getInstance().e(TAG, "Receiver was not registered before!");
                } else {
                    LogHelper.getInstance().e(TAG, "Unknown error while unregistering receiver.", e);
                }
            }
        } else {
            LogHelper.getInstance().e(TAG, "Receiver already unregistered!");
        }
    }

    /**
     * Called when the input stream is finished. Notifies the DAEMON that the connectino was terminated.
     */
    public void onReadingInputStreamFinished() {
        notifyDaemonConnectionTerminated();
    }

    /**
     * Called when a new device is discovered. Initiates a connection to the device if it matches the service.
     * */
    @Override
    public void onNewDeviceDiscovered(BluetoothDevice device) {
        connectToDeviceIfItMatchesService(device, null, getAddressOfDeviceToFind(serviceDescription));
    }

    /**
     * Called when UUIDs for a device are found. Initiates a connection to the device if it matches the service.
     * */
    @Override
    public void onUuidsFetched(BluetoothDevice device, List<ParcelUuid> uuids) {
        connectToDeviceIfItMatchesService(device, uuids, getAddressOfDeviceToFind(serviceDescription));
    }

    /**
     * Gets the address of the device of the service description.
     * 
     * @return the address of the bluetooth device of the service description
     */
    private byte[] getAddressOfDeviceToFind(ServiceDescription serviceDescription) {
        byte[] btAddressToFind = null;
        if (serviceDescription != null && serviceDescription.getAddressOfServer() != null
                && serviceDescription.getAddressOfServer().getBluetoothAddressAsByte() != null) {
            btAddressToFind = serviceDescription.getAddressOfServer().getBluetoothAddressAsByte();
        }
        return btAddressToFind;
    }

    /**
     * Called when device discovery was finished.
     * */
    @Override
    public void onDiscoveryFinished() {
        // Do nothing
        if (getConnectionState() != STATE_CONNECTED && getConnectionState() != STATE_CONNECTING) {
            LogHelper.getInstance().e(TAG, "Discovery finished but no connection is currently setup => Discover again");
            doDiscovery();
        } else {
            LogHelper.getInstance().d(TAG, "Discovery finished and connection in progress or already set up.");
        }
    }

    @Override
    public void onResume(Activity activity) {
        this.activity = activity;
        registerBroadcastReceivers(activity);
        // if (getConnectionState() == STATE_NONE && !isCanceled && serviceDescription != null) {
        // setupConnection(activity, this.serviceDescription);
        // }
    }

    @Override
    public void onNewIntent(Activity activity, Intent data) {
        // Nothing to do here
    }

    @Override
    public void onPause(Activity activity) {
        this.activity = null;
        unregisterReceiver(activity);
    }

    public int getConnectionState() {
        return connectionState;
    }

}

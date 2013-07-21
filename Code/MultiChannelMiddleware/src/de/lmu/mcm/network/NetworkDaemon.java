package de.lmu.mcm.network;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import de.lmu.mcm.helper.InterfaceAvailabilityChecker;
import de.lmu.mcm.helper.LogHelper;
import de.lmu.mcm.helper.PrefsHelper;
import de.lmu.mcm.network.Enums.InterfaceIdentifier;
import de.lmu.mcm.network.barcode.BarcodeCommunicator;
import de.lmu.mcm.network.bluetooth.BluetoothCommunicator;
import de.lmu.mcm.network.nfc.NfcCommunicator;
import de.lmu.mcm.network.sms.SmsCommunicator;
import de.lmu.mcm.network.wifi.WifiCommunicator;
import de.lmu.mcm.security.MessageEncryptionHandler;
import de.lmu.mcm.security.byteproto.BasicMessage;

/**
 * The central class that handles the communication and passes on results to the listener.
 * 
 * @author Thomas Bornschlegel
 * 
 */
public class NetworkDaemon {

    private static final String TAG = "DAEMON";
    private boolean initialized = false;
    private Context context;
    private List<CommonInterface> communicationLinks;
    private DaemonListener listener;
    public final static String EXTRA_LAST_SELECTED_INTERFACE = "last selected interface";
    private MessageEncryptionHandler messagePreparer = new MessageEncryptionHandler();

    public NetworkDaemon(Context context) {
        this.context = context;
    }

    /**
     * Sends data on the given interface asynchronously. When the call is finished the callback
     * {@link #onDataSent(InterfaceIdentifier, ProtocolMessage)} gets executed.
     * */
    public void sendData(BasicMessage message, byte messageType, InterfaceIdentifier interfaceName, Activity activity)
            throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException,
            BadPaddingException, InvalidAlgorithmParameterException {
        checkInitialized(activity);
        String uuidString = PrefsHelper.getIdOfCommunicationPartner(activity);
        UUID uuid = null;
        if (uuidString != null) {
            uuid = UUID.fromString(uuidString);
        } else {
            LogHelper.getInstance().d(TAG,
                    "UUID of communication partner was null. This is ok for public key exchange messages.");
        }
        byte[] dataToSend = messagePreparer.prepareMessageForSending(activity, uuid, message, messageType);
        getInterface(interfaceName).sendData(activity, dataToSend);
        LogHelper.getInstance().d(TAG, "Sending data via interface: " + interfaceName);
    }

    /**
     * Waits for data on the given interface asynchronously. When the call is finished the callback
     * {@link #onDataReceived(InterfaceIdentifier, ProtocolMessage)} is executed.
     * */
    public void waitForData(InterfaceIdentifier interfaceName, Activity activity) {
        checkInitialized(activity);
        getInterface(interfaceName).listenForMessages(activity);
        LogHelper.getInstance().d(TAG, "Waiting for data on interface: " + interfaceName);
    }

    /**
     * Establishes a connection to the given service asynchronously.
     * */
    public void establishConnection(Activity activity, ServiceDescription serviceDescription) {
        checkInitialized(activity);
        LogHelper.getInstance().d(TAG, "Trying to establish connection for service: " + serviceDescription);
        for (CommonInterface comInterface : communicationLinks) {
            comInterface.setupConnection(activity, serviceDescription);
        }
    }

    /**
     * Checks if all enabled interfaces are initialized and initializes them if this is not the case.
     * */
    public void checkInitialized(Activity activity) {
        if (!initialized) {
            communicationLinks = new ArrayList<CommonInterface>();
            InterfaceAvailabilityChecker availChecker = new InterfaceAvailabilityChecker();
            List<InterfaceIdentifier> enabledInterfaces = availChecker.getEnabledInterfaces(activity);
            for (InterfaceIdentifier comInterface : enabledInterfaces) {
                switch (comInterface) {
                case ARBITRARY:
                    break;
                case BARCODES:
                    if (getInterface(InterfaceIdentifier.BARCODES) == null)
                        communicationLinks.add(new BarcodeCommunicator(activity, this));
                    break;
                case BLUETOOTH:
                    if (getInterface(InterfaceIdentifier.BLUETOOTH) == null)
                        communicationLinks.add(new BluetoothCommunicator(activity, this));
                    break;
                case MOBILE_INTERNET:
                    if (getInterface(InterfaceIdentifier.MOBILE_INTERNET) == null
                            && getInterface(InterfaceIdentifier.WIFI) == null) {
                        // This is not accurate. But as both of these interfaces are IP-based they are regarded as the
                        // same.
                        communicationLinks.add(new WifiCommunicator(activity, this));
                    }
                    break;
                case NFC:
                    if (getInterface(InterfaceIdentifier.NFC) == null)
                        communicationLinks.add(new NfcCommunicator(activity, this));
                    break;
                case SMS:
                    if (getInterface(InterfaceIdentifier.SMS) == null)
                        communicationLinks.add(new SmsCommunicator(activity, this));
                    break;
                case WIFI:
                    if (getInterface(InterfaceIdentifier.MOBILE_INTERNET) == null
                            && getInterface(InterfaceIdentifier.WIFI) == null) {
                        communicationLinks.add(new WifiCommunicator(activity, this));
                    }
                    break;
                default:
                    break;

                }
                LogHelper.getInstance().d(TAG, "Interface initialized: " + comInterface);
            }
            initialized = true;
        }
    }

    /**
     * Callback method that is invoked by the given interface when the connection was closed.
     * */
    public void onInterfaceConnectionClosed(InterfaceIdentifier interfaceName) {
        LogHelper.getInstance().d(TAG, "Interface " + interfaceName + " closed connection");
        if (listener != null) {
            listener.onInterfaceConnectionClosed(interfaceName);
        }
    }

    /**
     * Callback method that is invoked by the given interface when the interface was destroyed.
     * */
    public void onInterfaceDestroyed(InterfaceIdentifier interfaceName) {
        LogHelper.getInstance().d(TAG, "Interface " + interfaceName + " destroyed");
        if (listener != null) {
            listener.onInterfaceDestroyed(interfaceName);
        }
    }

    /**
     * Callback method that is invoked by the given interface when the connection could not be set up.
     * */
    public void onConnectionSetupFailed(InterfaceIdentifier interfaceName, MultiNetworkAddress address) {
        LogHelper.getInstance().d(TAG, "Interface " + interfaceName + " could not set up connection");
        if (listener != null) {
            listener.onConnectionSetupFailed(interfaceName, address);
        }
    }

    /**
     * Callback method that is invoked by the given interface when the connection was set up.
     * */
    public void onConnectionIsSetUp(InterfaceIdentifier interfaceName, MultiNetworkAddress address) {
        LogHelper.getInstance()
                .d(TAG, "Interface " + interfaceName + " is ready to exchange messages with: " + address);
        if (listener != null) {
            listener.onConnectionIsSetUp(interfaceName, address);
        }
    }

    /**
     * Callback method that is invoked by the given interface when new data was received.
     * 
     * @param message
     *            the received data
     * @param interfaceName
     *            the name of the interface that received the data
     * */
    public void onDataReceived(InterfaceIdentifier interfaceName, ProtocolMessage message) {
        LogHelper.getInstance().d(TAG, "Received bytes via " + interfaceName);

        if (context == null) {
            LogHelper.getInstance().e(TAG, "Could not handle received message because the context is null!");
        } else {
            String uuidString = PrefsHelper.getIdOfCommunicationPartner(context);
            UUID uuid = null;
            if (uuidString != null) {
                uuid = UUID.fromString(uuidString);
            } else {
                LogHelper.getInstance().d(TAG,
                        "No UUID of communication partner present. This is ok for public key exchange messages.");
            }
            try {
                BasicMessage receivedMsg = messagePreparer.extractReceivedMessage(context, uuid,
                        message.getRawMessageInBytes());
                if (listener != null && receivedMsg != null) {
                    listener.onDataReceived(interfaceName, receivedMsg);
                }
            } catch (Exception e) {
                LogHelper.getInstance().e(TAG, "Error while trying to extract message from interface " + interfaceName);
            }
        }
    }

    /**
     * Callback method that is invoked by the given interface when new data was sent.
     * 
     * @param message
     *            the sent data
     * @param interfaceName
     *            the name of the interface that sent the data
     * */
    public void onDataSent(InterfaceIdentifier interfaceName, ProtocolMessage message) {
        LogHelper.getInstance().d(TAG, "Sent bytes via " + interfaceName);
        if (listener != null) {
            listener.onDataSent(interfaceName, message);
        }
    }

    /**
     * @param interfaceName
     *            the name of the interface
     * @return the interface with the given name
     * */
    private CommonInterface getInterface(Enums.InterfaceIdentifier interfaceName) {
        for (CommonInterface comInterface : communicationLinks) {
            if (comInterface.getInterfaceName().equals(interfaceName)) {
                return comInterface;
            }
        }
        return null;
    }

    /**
     * Closes the given activity and launches the new activity. Connections that were established during the lifecycle
     * of the old activity will still be present in the new activity.
     * 
     * @param activityToDestroy
     *            the activity to destroy
     * @param classOfNewActivity
     *            the new activity to launch
     * @param lastSelectedInterface
     *            the last selected interface so that it can be set again in the new activity
     */
    public synchronized void destroyActivityAndLaunchNew(Activity activityToDestroy, Class<?> classOfNewActivity,
            InterfaceIdentifier lastSelectedInterface) {
        // destroyInterfaces(activityToDestroy);
        Intent i = new Intent(activityToDestroy, classOfNewActivity);
        i.putExtra(EXTRA_LAST_SELECTED_INTERFACE, lastSelectedInterface);
        activityToDestroy.startActivity(i);
        activityToDestroy.finish();
    }

    /**
     * Call this in the onDestroy-method of your activity. If you want to launch another activity after closing yours,
     * consider using {@link #destroyActivityAndLaunchNew(Activity, Class)} and use this only for the case that the
     * activity was closed without starting a new one.
     * */
    public synchronized void destroyInterfaces(Activity activity) {
        if (initialized) {
            for (CommonInterface comInterface : communicationLinks) {
                if (comInterface != null) {
                    comInterface.onPause(activity);
                    comInterface.destroy(activity);
                    LogHelper.getInstance().d(TAG, "Destroyed interface: " + comInterface.getInterfaceName());
                    comInterface = null;
                }
            }
        }
        communicationLinks.clear();
        communicationLinks = null;
        initialized = false;
        LogHelper.getInstance().d(TAG, "Finished destroying interfaces");
    }

    /**
     * Call this to stop all current connections. Not used at the moment.
     * */
    public void stopConnectionsOfAllInterfaces(Activity activity) {
        if (initialized) {
            for (CommonInterface comInterface : communicationLinks) {
                if (comInterface != null) {
                    comInterface.stopCurrentConnection(activity);
                }
            }
        }
    }

    /**
     * Call this in the onActivityResult method of your activity.
     * 
     * @return true if the activities result was handled by a communication interface.
     * */
    public boolean onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        checkInitialized(activity);
        for (CommonInterface comInterface : communicationLinks) {
            if (comInterface.onActivityResult(activity, requestCode, resultCode, data)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Call this in the onResume method of your activity.
     * */
    public void onResume(Activity activity) {
        checkInitialized(activity);
        for (CommonInterface comInterface : communicationLinks) {
            comInterface.onResume(activity);
        }
    }

    /**
     * Call this in the onPause method of your activity.
     * */
    public void onPause(Activity activity) {
        checkInitialized(activity);
        for (CommonInterface comInterface : communicationLinks) {
            comInterface.onPause(activity);
        }
    }

    /**
     * Call this in the onNewIntent method of your activity.
     * */
    public void onNewIntent(Activity activity, Intent data) {
        LogHelper.getInstance().d(TAG, "onNewIntent in DAEMON called");
        checkInitialized(activity);
        for (CommonInterface comInterface : communicationLinks) {
            comInterface.onNewIntent(activity, data);
        }
    }

    /**
     * Set the listener (an Activity implementing {@link de.lmu.mcm.network.DaemonListener}) that should receive
     * callbacks from the daemon.
     * */
    public void setListener(DaemonListener listener) {
        this.listener = listener;
        if (listener == null) {
            LogHelper.getInstance().d(TAG, "Set DaemonListener to NULL");
        } else {
            LogHelper.getInstance().d(TAG, "Set DaemonListener: " + listener.getClass().getSimpleName());
        }

    }
}

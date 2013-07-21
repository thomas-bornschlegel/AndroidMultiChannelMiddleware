package de.lmu.mcm.helper;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.nfc.NfcAdapter;
import android.os.AsyncTask;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import de.lmu.mcm.network.Enums;

/**
 * Checks which communication channels are available on the device. Enables inactive devices or asks the user to do so.
 * 
 * @author Thomas Bornschlegel
 * 
 */
public class InterfaceAvailabilityChecker {

    private static final String TAG = "IntAct";
    private static final int REQUEST_ENABLE_BT = 101;
    private static final int REQUEST_ENABLE_NFC = 102;
    private static final int REQUEST_ENABLE_MOBILE_INET = 103;
    private static final int REQUEST_ENABLE_WIFI = 104;
    private List<Enums.InterfaceIdentifier> listOfPendingActivationRequests;
    private boolean waitingForInterfaceActivationByUser = false;
    private OnInterfacesActivatedListener onInterfacesActivatedListener;
    private boolean atLeastOneInterfaceActivated = false;

    public InterfaceAvailabilityChecker() {
        listOfPendingActivationRequests = new ArrayList<Enums.InterfaceIdentifier>();
        waitingForInterfaceActivationByUser = false;
    }

    // We do not use Enums.InterfaceIdentifier.MOBILE_INTERNET here because it does not provide service discovery and we
    // have no mechanism employed to hand over the servers IP and port without service discovery.
    public final Enums.InterfaceIdentifier[] arrayOfAllInterfaces = { Enums.InterfaceIdentifier.BARCODES,
            Enums.InterfaceIdentifier.BLUETOOTH, Enums.InterfaceIdentifier.NFC, Enums.InterfaceIdentifier.SMS,
            Enums.InterfaceIdentifier.WIFI };

    /**
     * Tries to activate all given interfaces or asks the user to do so.
     * 
     * @param activity
     *            the activity that performs the request
     * @param listOfSupportedInterfaces
     *            the interfaces that should be activated
     * 
     * */
    public void activateInterfaces(Activity activity, List<Enums.InterfaceIdentifier> listOfSupportedInterfaces) {

        LogHelper.getInstance().d(TAG, "Checking if supported interfaces are enabled...");

        // Check if interfaces are enabled:
        List<Enums.InterfaceIdentifier> disabledInterfaces = new ArrayList<Enums.InterfaceIdentifier>();
        for (Enums.InterfaceIdentifier singleInterface : listOfSupportedInterfaces) {
            if (!isInterfaceEnabled(activity, singleInterface)) {
                disabledInterfaces.add(singleInterface);
            }
        }

        if (disabledInterfaces.contains(Enums.InterfaceIdentifier.BLUETOOTH)
                || disabledInterfaces.contains(Enums.InterfaceIdentifier.MOBILE_INTERNET)
                || disabledInterfaces.contains(Enums.InterfaceIdentifier.NFC)) {
            waitingForInterfaceActivationByUser = true;
        }

        atLeastOneInterfaceActivated = waitingForInterfaceActivationByUser;
        LogHelper.getInstance().d(TAG, "Activating disabled interfaces...");

        // Activate supported interfaces:
        for (Enums.InterfaceIdentifier singleInterface : disabledInterfaces) {
            activateInterface(activity, singleInterface);
        }

        if (!waitingForInterfaceActivationByUser) {
            checkInterfacesAgainAfterEnabling(activity);
        } else {
            LogHelper.getInstance().d(TAG,
                    "Postponing check if interfaces are activated, as we wait for user interaction");
        }

    }

    /**
     * Gets all supported interfaces. These are the interfaces that are supported by the hardware of the device.
     * 
     * @param activity
     *            the activity that performs the request
     * 
     * @return a list of all supported interfaces
     * 
     * */
    public List<Enums.InterfaceIdentifier> getSupportedInterfaces(Activity activity) {
        List<Enums.InterfaceIdentifier> listOfSupportedInterfaces = new ArrayList<Enums.InterfaceIdentifier>();
        for (Enums.InterfaceIdentifier singleInterface : arrayOfAllInterfaces) {
            boolean supported = isInterfaceSupportedByDevice(activity, singleInterface);
            if (supported) {
                listOfSupportedInterfaces.add(singleInterface);
            }
        }
        return listOfSupportedInterfaces;
    }

    /**
     * Gets all enabled interfaces. These are the interfaces that are supported by the hardware of the device AND that
     * are enabled.
     * 
     * @param activity
     *            the activity that performs the request
     * 
     * @return a list of all enabled interfaces
     * 
     * */
    public List<Enums.InterfaceIdentifier> getEnabledInterfaces(Activity activity) {
        List<Enums.InterfaceIdentifier> listOfEnabledInterfaces = new ArrayList<Enums.InterfaceIdentifier>();
        for (Enums.InterfaceIdentifier singleInterface : getSupportedInterfaces(activity)) {
            if (isInterfaceEnabled(activity, singleInterface)) {
                listOfEnabledInterfaces.add(singleInterface);
            }
        }
        return listOfEnabledInterfaces;
    }

    /**
     * Called after activating an interface to check if its now really enabled.
     * 
     * @param activity
     *            the activity that performs the request
     * 
     * */
    private void checkInterfacesAgainAfterEnabling(final Activity activity) {

        new AsyncTask<Void, Void, Void>() {

            protected Void doInBackground(Void... arg0) {

                if (atLeastOneInterfaceActivated) {
                    int secondsToSleep = 5;
                    LogHelper.getInstance().d(TAG, "Given interfaces " + secondsToSleep + " seconds to activate...");

                    try {
                        Thread.sleep(secondsToSleep * 1000);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }

                LogHelper.getInstance().d(TAG, "Checking if all interfaces are enabled now...");

                boolean allFine = true;
                List<Enums.InterfaceIdentifier> listOfSupportedInterfaces = getSupportedInterfaces(activity);
                for (Enums.InterfaceIdentifier singleInterface : listOfSupportedInterfaces) {
                    if (!isInterfaceEnabled(activity, singleInterface)) {
                        allFine = false;
                        LogHelper.getInstance().e(TAG, "Interface is still not enabled: " + singleInterface);
                    }
                }
                LogHelper.getInstance().d(TAG, "Result of activating supported interfaces: " + allFine);

                if (onInterfacesActivatedListener != null) {
                    onInterfacesActivatedListener.onInterfacesActivated();
                }
                return null;
            }
        }.execute();
    }

    /**
     * @param context
     *            the context
     * @param interfaceName
     *            the name of the interface that should be checked.
     * 
     * @return true if the given interface is supported by the device
     * */
    public boolean isInterfaceSupportedByDevice(Context context, Enums.InterfaceIdentifier interfaceName) {
        boolean canRead = doesInterfaceSupportReading(context, interfaceName);
        boolean canWrite = doesInterfaceSupportWriting(context, interfaceName);
        LogHelper.getInstance().d(TAG,
                "Interface " + interfaceName + " is supported by device: " + (canRead || canWrite));
        return canRead || canWrite;
    }

    /**
     * @param context
     *            the context
     * @param interfaceName
     *            the name of the interface that should be checked.
     * 
     * @return true if the given interface can communicate a message to another device
     * */
    private boolean doesInterfaceSupportWriting(Context context, Enums.InterfaceIdentifier interfaceName) {
        PackageManager pm = context.getPackageManager();
        switch (interfaceName) {
        case BARCODES:
            // We assume that every device has a display and therefore always return true.
            // TODO do devices exist that do not have a display? If so, we would have to add a check here.
            return true;
        case BLUETOOTH:
            return pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
        case MOBILE_INTERNET:
            if (!hasSimCard(context) || !canAccessMobileData(context)) {
                return false;
            }
            ConnectivityManager connec = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            for (NetworkInfo info : connec.getAllNetworkInfo()) {
                if (info.getType() == ConnectivityManager.TYPE_MOBILE) {
                    return true;
                }
            }
            // TODO This could be helpful
            // http://stackoverflow.com/questions/2802472/detect-network-connection-type-on-android
            return false;
        case NFC:
            return pm.hasSystemFeature(PackageManager.FEATURE_NFC);
        case SMS:
            return hasSimCard(context);
        case WIFI:
            return pm.hasSystemFeature(PackageManager.FEATURE_WIFI);
        default:
            break;
        }
        return false;
    }

    /**
     * @param context
     *            the context
     * @param interfaceName
     *            the name of the interface that should be checked.
     * 
     * @return true if the given interface can read a message from another device
     * */
    private boolean doesInterfaceSupportReading(Context context, Enums.InterfaceIdentifier interfaceName) {
        if (interfaceName == Enums.InterfaceIdentifier.BARCODES) {
            PackageManager pm = context.getPackageManager();
            return pm.hasSystemFeature(PackageManager.FEATURE_CAMERA);
        } else {
            return doesInterfaceSupportWriting(context, interfaceName);
        }
    }

    /**
     * @return true if the device has a SIM card.
     * */
    private boolean hasSimCard(Context context) {
        TelephonyManager telephonyMgr = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String telephoneNumber = telephonyMgr.getLine1Number();
        return telephoneNumber != null && !telephoneNumber.equals("");
    }

    /**
     * @return true if the device has the hardware to access the mobile internet
     * */
    private boolean canAccessMobileData(Context context) {
        TelephonyManager telephonyMgr = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return telephonyMgr.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE;
    }

    /**
     * @param context
     *            the context
     * @param interfaceName
     *            the name of the interface that should be checked.
     * 
     * @return true if the given interface is enabled
     * */
    public boolean isInterfaceEnabled(Context context, Enums.InterfaceIdentifier interfaceName) {
        boolean isEnabled = false;

        switch (interfaceName) {
        case BARCODES:
            // We assume that the display and the camera are always available if they exist in the device.
            // There exists no known user setting to disable a camera/display.
            isEnabled = true;
            break;
        case BLUETOOTH:
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            if (btAdapter != null) {
                isEnabled = btAdapter.isEnabled();
            }
            break;
        case MOBILE_INTERNET:

            boolean airplaneMode = false;
            if (android.os.Build.VERSION.SDK_INT < 17) {
                airplaneMode = Settings.System
                        .getInt(context.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) != 0;
            } else {
                airplaneMode = Settings.System
                        .getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
            }

            if (airplaneMode) {
                break;
            }

            // 1. Check:
            ConnectivityManager connec = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            for (NetworkInfo info : connec.getAllNetworkInfo()) {
                if (info.getType() == ConnectivityManager.TYPE_MOBILE && info.isConnected()) {
                    LogHelper.getInstance().d(TAG, "First approach used to check mobile internet.");
                    isEnabled = true;
                }
            }

            if (!isEnabled) {
                // 2. possible check:
                // http://stackoverflow.com/questions/8224097/how-to-check-if-mobile-network-is-enabled-disabled
                try {
                    Class<?> c = Class.forName(connec.getClass().getName());
                    Method m = c.getDeclaredMethod("getMobileDataEnabled");
                    m.setAccessible(true);
                    boolean result = (Boolean) m.invoke(connec);
                    if (result) {
                        LogHelper.getInstance().d(TAG, "Second approach used to check mobile internet.");
                        isEnabled = true;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            break;
        case NFC:
            NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(context);
            if (nfcAdapter != null) {
                isEnabled = nfcAdapter.isEnabled();
            }

            break;
        case SMS:
            isEnabled = hasSimCard(context);
            break;
        case WIFI:
            WifiManager wifiMgr = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifiMgr != null) {
                isEnabled = wifiMgr.isWifiEnabled();
            }
            break;
        case ARBITRARY:
            break;
        default:
            break;
        }

        LogHelper.getInstance().d(TAG, interfaceName + " is enabled: " + isEnabled);
        return isEnabled;
    }

    /**
     * 
     * Checks if bluetooth is in discoverable mode. (Not used at the moment)
     * 
     * @return true if bluetooth is discoverable
     * */
    public boolean isBluetoothDiscoverable() {
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter != null) {
            return btAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
        }
        return false;
    }

    /**
     * 
     * Tries to activity the given interface.
     * 
     * Note that the activity that uses this class has to call {@link #onActivityResult(Context, int, int, Intent)} in
     * its own onActivityResult method! Some interfaces like Wifi can be changed directly. Others like Bluetooth, Mobile
     * Internet and NFC show a dialog to the user and require her to change the setting.
     * 
     * Also if the user changed the state of the network it may take some seconds until this is registered by the
     * system. Thus wait a few seconds before calling
     * {@link #isInterfaceEnabled(Context, de.lmu.mcm.network.Enums.InterfaceIdentifier)} again.
     * 
     * @param activity
     *            the activity that performs the request
     * @param interfaceName
     *            the name of the interface to activate
     * 
     * 
     * */
    private void activateInterface(Activity activity, Enums.InterfaceIdentifier interfaceName) {
        switch (interfaceName) {
        case BARCODES:
            // Nothing to do here
            break;
        case BLUETOOTH:
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            listOfPendingActivationRequests.remove(Enums.InterfaceIdentifier.BLUETOOTH);
            listOfPendingActivationRequests.add(Enums.InterfaceIdentifier.BLUETOOTH);
            break;
        case MOBILE_INTERNET:
            final Intent enableMobileInetIntent = new Intent(Settings.ACTION_DATA_ROAMING_SETTINGS);
            enableMobileInetIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
            activity.startActivityForResult(enableMobileInetIntent, REQUEST_ENABLE_MOBILE_INET);
            listOfPendingActivationRequests.remove(Enums.InterfaceIdentifier.MOBILE_INTERNET);
            listOfPendingActivationRequests.add(Enums.InterfaceIdentifier.MOBILE_INTERNET);
            break;
        case NFC:
            Intent enableNfcIntent = new Intent();
            if (android.os.Build.VERSION.SDK_INT >= 16) {
                enableNfcIntent = new Intent(android.provider.Settings.ACTION_NFC_SETTINGS);
            } else {
                enableNfcIntent = new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS);
            }
            activity.startActivityForResult(enableNfcIntent, REQUEST_ENABLE_NFC);

            listOfPendingActivationRequests.remove(Enums.InterfaceIdentifier.NFC);
            listOfPendingActivationRequests.add(Enums.InterfaceIdentifier.NFC);
            break;
        case SMS:
            // Nothing to do here
            break;
        case WIFI:
            WifiManager wifiMgr = (WifiManager) activity.getSystemService(Context.WIFI_SERVICE);
            if (wifiMgr != null) {
                wifiMgr.setWifiEnabled(true);
                LogHelper.getInstance().d(TAG, "Activated wifi interface");
            } else {
                LogHelper.getInstance().e(TAG, "Could not activate wifi interface");
            }
            break;
        }
    }

    /**
     * Call this method in the onActivityResult-method of your activity. This method is necessary to handle activation
     * requests that require user interaction.
     * 
     * @param activity
     *            the activity that calls this method in its own onActivityResult-method
     * 
     * @param requestCode
     *            the requestCode from the onActivityResult-method of the according activity
     * @param resultCode
     *            the resultCode from the onActivityResult-method of the according activity
     * @param data
     *            the data from the onActivityResult-method of the according activity
     * 
     * 
     * @return true if the result was handled, false otherwise
     * */
    public boolean onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
        case REQUEST_ENABLE_BT:
            if (resultCode == Activity.RESULT_OK) {
                LogHelper.getInstance().d(TAG, "User enabled bluetooth.");
            } else {
                LogHelper.getInstance().e(TAG, "Bluetooth not enabled.");
            }
            removePendingActivationRequest(activity, Enums.InterfaceIdentifier.BLUETOOTH);
            return true;
        case REQUEST_ENABLE_MOBILE_INET:
            removePendingActivationRequest(activity, Enums.InterfaceIdentifier.MOBILE_INTERNET);
            return true;
        case REQUEST_ENABLE_NFC:
            removePendingActivationRequest(activity, Enums.InterfaceIdentifier.NFC);
            return true;
        case REQUEST_ENABLE_WIFI:
            removePendingActivationRequest(activity, Enums.InterfaceIdentifier.WIFI);
            return true;
        }
        return false;
    }

    /**
     * Removes pending activation requests for the given interface.
     * 
     * @param activity
     *            the activity that calls this method in its own onActivityResult-method
     * @param interfaceName
     *            the name of the interface for which an activation request is pending
     * */
    private void removePendingActivationRequest(Activity activity, Enums.InterfaceIdentifier interfaceName) {
        listOfPendingActivationRequests.remove(interfaceName);
        if (listOfPendingActivationRequests.isEmpty()) {
            LogHelper.getInstance().d(TAG, "All user request for activating interfaces finished.");
            waitingForInterfaceActivationByUser = false;
            checkInterfacesAgainAfterEnabling(activity);
        } else {
            LogHelper.getInstance().d(TAG,
                    "User request for activating " + interfaceName + " finished, but there are others pending.");
        }
    }

    /**
     * (Not used at the moment).
     * 
     * @return true if an interface with internet connection is active
     * 
     * */
    public boolean isInternetAccessAvailable(Context context) {
        ConnectivityManager connec = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        for (NetworkInfo info : connec.getAllNetworkInfo()) {
            if (info.getState() == NetworkInfo.State.CONNECTED) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the IP address of the wifi adapter
     */
    public static InetAddress getOwnIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());

            for (NetworkInterface intf : interfaces) {
                // At the moment we only consider WIFI interfaces.
                // To use mobile Internet uncomment the if-clause:
                if (intf.getDisplayName().contains("wlan") || intf.getDisplayName().contains("wifi")) {
                    String ipAsString = getIpAddress(intf);
                    if (ipAsString != null) {
                        return InetAddress.getByName(ipAsString);
                    }
                }
            }
        } catch (Exception ex) {

        }
        return null;
    }

    /**
     * @param intf
     *            the network interface to check
     * @return the IP address of the given NetworkInterface
     * */
    private static String getIpAddress(NetworkInterface intf) {
        List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
        for (InetAddress addr : addrs) {
            if (!addr.isLoopbackAddress()) {
                String sAddr = addr.getHostAddress().toUpperCase();
                return sAddr;
                // XXX is the following code useful/necessary?
                // Source: http://stackoverflow.com/a/13007325

                // boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
                // if (isIPv4) {
                // return sAddr;
                // } else {
                // int delim = sAddr.indexOf('%'); // drop ip6 port suffix
                // return delim < 0 ? sAddr : sAddr.substring(0, delim);
                // }
            }
        }
        return null;
    }

    /**
     * @return a random port number in the range of 36002-36411
     * */
    public static int getRandomPortNumber() {
        // valid ports: from 1024 to 49151
        // Reserved Ports: https://www.iana.org/assignments/service-names-port-numbers/service-names-port-numbers.txt
        // Example of unassigned ports: 36002-36411
        int portFrom = 36002;
        int portTo = 36411;
        return (int) (Math.random() * (portTo - portFrom) + portFrom);
    }

    /**
     * Interface that enables communication with {@link InterfaceAvailabilityChecker}
     * */
    public interface OnInterfacesActivatedListener {
        public void onInterfacesActivated();
    }

    /**
     * Sets a listener that receives callbacks from this class.
     * 
     * @param onInterfacesActivatedListener
     *            the listener
     * */
    public void setOnInterfacesActivatedListener(OnInterfacesActivatedListener onInterfacesActivatedListener) {
        this.onInterfacesActivatedListener = onInterfacesActivatedListener;
    }
}

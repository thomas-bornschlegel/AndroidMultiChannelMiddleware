package de.lmu.mcm.network.bluetooth;

import java.util.ArrayList;
import java.util.List;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelUuid;
import android.os.Parcelable;
import de.lmu.mcm.helper.LogHelper;

/**
 * BroadcastReceiver to handle bluetooth device discovery and to manage bluetooth service discovery. Note that searching
 * for bluetooth sdp services is only supported since API version 15 and does never return valid results. See the
 * comments in this class for further explanation.
 * 
 * <br>
 * <br>
 * 
 * A class that uses this BroadcastReceiver must register it with the following IntentFilters:
 * BluetoothDevice.ACTION_FOUND, BluetoothAdapter.ACTION_DISCOVERY_FINISHED, and BluetoothDevice.ACTION_UUID
 * 
 * */
public class BroadcastReceiverBluetoothDiscovery extends BroadcastReceiver {

    private final String TAG = "Bluetooth BroadcastReceiver ";
    private BluetoothDiscoveryListener listener;
    private List<BluetoothDevice> devicesWithUuids = new ArrayList<BluetoothDevice>();

    public BroadcastReceiverBluetoothDiscovery(BluetoothDiscoveryListener listener) {
        this.listener = listener;
        // Cache the devices that are already paired:
        // Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
    }

    /**
     * This interface is used to handle results from the {@link BroadcastReceiverBluetoothDiscovery}.
     * */
    public interface BluetoothDiscoveryListener {
        public void onNewDeviceDiscovered(BluetoothDevice device);

        public void onUuidsFetched(BluetoothDevice device, List<ParcelUuid> uuids);

        public void onDiscoveryFinished();
    }

    /**
     * Handles new intents that are sent to this receiver. These intents include discovered devices and (on devices with
     * API level >= 15) also the services that the devices advertise.
     * */
    @SuppressLint("NewApi")
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        int apiVersion = android.os.Build.VERSION.SDK_INT;

        // When discovery finds a device
        if (BluetoothDevice.ACTION_FOUND.equals(action)) {
            LogHelper.getInstance().d(TAG, "Found device");
            // Get the BluetoothDevice object from the Intent
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            LogHelper.getInstance().d(TAG, "Retrieved device info");

            if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                LogHelper.getInstance().d(TAG, "Found new device: " + device.getName() + "\n" + device.getAddress());
            } else {
                LogHelper.getInstance().d(TAG,
                        "Found already known device: " + device.getName() + "\n" + device.getAddress());
            }
            if (listener != null) {
                listener.onNewDeviceDiscovered(device);
            }
            if (apiVersion >= 15) {
                // The method to fetch UUIDs is only supported since ICE_CREAM_SANDWICH_MR1
                boolean fetchingUuids = device.fetchUuidsWithSdp();
                if (fetchingUuids) {
                    LogHelper.getInstance().d(TAG, "Started fetching UUIDs");
                } else {
                    LogHelper.getInstance().e(TAG, "Could NOT start fetching UUIDs");
                }
            } else {
                LogHelper.getInstance().d(TAG,
                        "Did not start to fetch SDP UUIDs because your device api " + apiVersion + " is smaller 15");
            }

        } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
            LogHelper.getInstance().d(TAG, "Discovery finished");

            if (apiVersion >= 15) {
                for (BluetoothDevice device : devicesWithUuids) {

                    // The method to get the fetched UUIDs is only supported since ICE_CREAM_SANDWICH_MR1

                    // XXX Does mostly return null => The android bluetooth SDP does not be working properly:
                    Parcelable[] uuidArray = device.getUuids();
                    // This also returns null:
                    // intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
                    // Other people seem to have the same problem:
                    // http://stackoverflow.com/questions/14812326/android-bluetooth-get-uuids-of-discovered-devices

                    if (uuidArray != null) {
                        LogHelper.getInstance().d(TAG,
                                "Found " + uuidArray.length + " UUIDs for device " + device.getAddress());
                        List<ParcelUuid> list = new ArrayList<ParcelUuid>();
                        for (Parcelable uuid : uuidArray) {
                            if (uuid instanceof ParcelUuid) {
                                ParcelUuid castedUuid = (ParcelUuid) uuid;
                                list.add(castedUuid);
                            } else {
                                LogHelper.getInstance().e(TAG, "Object is not a ParcelUuid: " + uuid.toString());
                            }
                        }
                        if (listener != null && list.size() > 0) {
                            listener.onUuidsFetched(device, list);
                        }
                    } else {
                        LogHelper.getInstance().e(TAG,
                                "UUIDs for device " + device.getAddress() + " could not be received");
                    }
                }
            } else {
                LogHelper.getInstance().e(TAG,
                        "Could not check UUIDs of devices because your API " + apiVersion + " is lower than 15.");
            }
            if (listener != null) {
                listener.onDiscoveryFinished();
            }
        } else if (BluetoothDevice.ACTION_UUID.equals(action)) {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            LogHelper.getInstance().d(
                    TAG,
                    "Finished looking for new UUIDs for device: " + device.getName() + " address: "
                            + device.getAddress());
            if (!devicesWithUuids.contains(device)) {
                devicesWithUuids.add(device);
            }
        }
    }
}

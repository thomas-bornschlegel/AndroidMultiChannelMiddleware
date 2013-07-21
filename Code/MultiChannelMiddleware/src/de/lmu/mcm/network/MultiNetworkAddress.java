package de.lmu.mcm.network;

import java.io.Serializable;
import java.net.InetAddress;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import de.lmu.mcm.helper.ByteConverter;

/**
 * An object that encapsulates addresses for internet, bluetooth and sms interfaces.
 * 
 * @author Thomas Bornschlegel
 * 
 */
public class MultiNetworkAddress implements Serializable {

    private final String TAG = MultiNetworkAddress.class.getSimpleName() + " ";
    private static final long serialVersionUID = 2378214524542014208L;
    private String smsAddress = null;
    private byte[] bluetoothAddressAsByte = null;
    private String bluetoothAddress = null;
    private BluetoothDevice bluetoothDevice;
    private String deviceId = null;
    private InetAddress ipAddress = null;
    private int ipPort = -1;

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getBluetoothAddressAsString() {
        return bluetoothAddress;
    }

    public void setBluetoothAddressFromString(String bluetoothAddress) {
        this.bluetoothAddress = bluetoothAddress;
    }

    public byte[] getBluetoothAddressAsByte() {
        if (bluetoothAddressAsByte == null && bluetoothAddress != null) {
            String deviceFoundAsString = bluetoothAddress.replace(":", "");
            return ByteConverter.hexStringToByteArray(deviceFoundAsString);
        }
        return bluetoothAddressAsByte;
    }

    public void setBluetoothAddressAsByte(byte[] bluetoothAddressAsByte) {
        this.bluetoothAddressAsByte = bluetoothAddressAsByte;
    }

    @SuppressLint("NewApi")
    public BluetoothDevice getBluetoothDevice(BluetoothAdapter adapter) {
        if (bluetoothDevice != null) {
            return bluetoothDevice;
        }
        if (getBluetoothAddressAsString() != null && adapter != null) {
            return adapter.getRemoteDevice(getBluetoothAddressAsString());
        }
        int apiVersion = android.os.Build.VERSION.SDK_INT;
        if (getBluetoothAddressAsByte() != null && adapter != null) {
            if (apiVersion >= 16) {
                return adapter.getRemoteDevice(getBluetoothAddressAsByte());
            } else {
                String bluetoothAddress = getBluetoothAddressFromString(getBluetoothAddressAsByte());
                return adapter.getRemoteDevice(bluetoothAddress);
            }
        }
        return null;
    }

    public static String getBluetoothAddressFromString(byte[] address) {
        // Source taken from BluetoothAdapter's method "getRemoteDevice(byte[] address)".
        // Source code available here:
        // https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/bluetooth/BluetoothAdapter.java

        return String.format("%02X:%02X:%02X:%02X:%02X:%02X", address[0], address[1], address[2], address[3],
                address[4], address[5]);
    }

    public void setBluetoothDevice(BluetoothDevice bluetoothDevice) {
        bluetoothAddress = bluetoothDevice.getAddress();
        this.bluetoothDevice = bluetoothDevice;
    }

    public String getSmsAddress() {
        return smsAddress;
    }

    public void setSmsAddress(String smsAddress) {
        this.smsAddress = smsAddress;
    }

    public InetAddress getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(InetAddress ipAddress) {
        this.ipAddress = ipAddress;
    }

    public int getIpPort() {
        return ipPort;
    }

    public void setIpPort(int ipPort) {
        this.ipPort = ipPort;
    }

    @Override
    public String toString() {
        return "MultiNetworkAddress [smsAddress=" + smsAddress + ", bluetoothAddress=" + bluetoothAddress
                + ", deviceId=" + deviceId + ", ipAddress=" + ipAddress + ", ipPort=" + ipPort + "]";
    }

    /**
     * Checks if two addresses are the same. (Not used at the moment).
     * 
     * @return true if the other MultiNetworkAddress has at least one address in common with this address.
     * */
    public boolean sharesSameAddress(MultiNetworkAddress otherAddr) {

        if (checkEqualWithNullCheck(otherAddr.getIpAddress(), ipAddress)
                || checkEqualWithNullCheck(otherAddr.getBluetoothAddressAsString(), bluetoothAddress)
                || checkEqualWithNullCheck(otherAddr.getSmsAddress(), smsAddress)) {
            return true;
        }
        return false;
    }

    private boolean checkEqualWithNullCheck(Object a, Object b) {
        if (a == null && b != null) {
            return false;
        }
        if (a != null && b == null) {
            return false;
        }
        if (a == null && b == null) {
            return true;
        }
        return a.equals(b);
    }

}
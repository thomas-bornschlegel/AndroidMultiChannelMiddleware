package de.lmu.mcm.helper;

import java.util.UUID;

import android.app.Activity;
import de.lmu.mcm.network.Enums.Role;
import de.lmu.mcm.network.MultiNetworkAddress;
import de.lmu.mcm.network.ServiceDescription;

public class TestSetup {

    /**
     * @param activity
     *            the calling activity
     * 
     * @return the service description for the test setup
     * 
     * */
    public static ServiceDescription getServiceDescription(Activity activity) {

        String remoteIdString = PrefsHelper.getIdOfCommunicationPartner(activity);
        UUID ownId = UUID.fromString(PrefsHelper.getOwnId(activity));
        UUID remoteId = UUID.fromString(remoteIdString);

        // Setup for wifi
        MultiNetworkAddress address = new MultiNetworkAddress();
        address.setIpPort(InterfaceAvailabilityChecker.getRandomPortNumber());
        address.setIpAddress(InterfaceAvailabilityChecker.getOwnIpAddress());
        // Setup for bluetooth
        byte[] bluetoothAddress = PrefsHelper.getBluetoothAddressForUser(activity, remoteIdString);
        address.setBluetoothAddressAsByte(bluetoothAddress);
        // For setting up SMS
        String mobileNumber = PrefsHelper.getMobileNumberFromUserId(activity, remoteIdString);
        if (mobileNumber != null) {
            address.setSmsAddress(mobileNumber);
        }

        ServiceDescription service = null;
        // Setup role (Server or client)
        boolean isServer = ownId.compareTo(remoteId) > 0;
        Role role = null;
        if (isServer) {
            role = Role.SERVER;
            service = new ServiceDescription(ownId, "LMU service", "A service description for lmu service", address);
        } else {
            role = Role.CLIENT;
            service = new ServiceDescription(remoteId, "LMU service", "A service description for lmu service", address);
            // service.setAddressOfServer(null);
        }
        // Additional paramters
        service.setTimeOutBluetoothInSeconds(30);
        service.setMaxConnectionAttemptsWifi(50);
        service.setMaxConnectionAttemptsBluetooth(50);
        service.setRole(role);
        service.setUseServiceDiscoveryForWifi(true);

        return service;
    }
}

package de.lmu.mcm.network;

import java.util.UUID;

import de.lmu.mcm.network.Enums.Role;

/**
 * 
 * Describes a service.
 * 
 * @author Thomas Bornschlegel
 * 
 */
public class ServiceDescription {

    private UUID uuid;
    private String name;
    private String description;
    private MultiNetworkAddress addressOfServer;
    private Role role;

    private boolean useServiceDiscoveryForWifi = false;
    private int timeOutBluetoothInSeconds = 300;
    private int timeOutWifiInSeconds = 60;
    private int maxConnectionAttemptsWifi = 3;
    private int maxConnectionAttemptsBluetooth = 3;

    public ServiceDescription(UUID uuid, String name, String description, MultiNetworkAddress addressOfServer) {
        this.uuid = uuid;
        this.description = description;
        this.name = name;
        this.addressOfServer = addressOfServer;
        this.addressOfServer.setDeviceId(uuid.toString());
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public MultiNetworkAddress getAddressOfServer() {
        return addressOfServer;
    }

    public int getTimeOutWifiInSeconds() {
        return timeOutWifiInSeconds;
    }

    public int getMaxConnectionAttemptsWifi() {
        return maxConnectionAttemptsWifi;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Role getRole() {
        return role;
    }

    public void setAddressOfServer(MultiNetworkAddress address) {
        this.addressOfServer = address;
    }

    public int getMaxConnectionAttemptsBluetooth() {
        return maxConnectionAttemptsBluetooth;
    }

    public void setMaxConnectionAttemptsBluetooth(int maxConnectionAttemptsBluetooth) {
        this.maxConnectionAttemptsBluetooth = maxConnectionAttemptsBluetooth;
    }

    /**
     * @return true if the other service has the same uuid as this service
     * */
    @Override
    public boolean equals(Object o) {
        if (o instanceof ServiceDescription) {
            ServiceDescription otherService = (ServiceDescription) o;
            return otherService.getUuid().toString().equals(uuid.toString());
        }
        return false;
    }

    /**
     * 0 For no timeout
     * */
    public void setTimeOutWifiInSeconds(int timeOutWifiInSeconds) {
        this.timeOutWifiInSeconds = timeOutWifiInSeconds;
    }

    public void setMaxConnectionAttemptsWifi(int maxConnectionAttemptsWifi) {
        this.maxConnectionAttemptsWifi = maxConnectionAttemptsWifi;
    }

    public boolean isUseServiceDiscoveryForWifi() {
        return useServiceDiscoveryForWifi;
    }

    public void setUseServiceDiscoveryForWifi(boolean useServiceDiscoveryForWifi) {
        this.useServiceDiscoveryForWifi = useServiceDiscoveryForWifi;
    }

    public int getTimeOutBluetoothInSeconds() {
        return timeOutBluetoothInSeconds;
    }

    /**
     * 0 For no timeout
     * */
    public void setTimeOutBluetoothInSeconds(int timeOutBluetoothInSeconds) {
        this.timeOutBluetoothInSeconds = timeOutBluetoothInSeconds;
    }

    @Override
    public String toString() {
        return "ServiceDescription [" + (uuid != null ? "uuid=" + uuid + ", " : "")
                + (name != null ? "name=" + name + ", " : "")
                + (description != null ? "description=" + description + ", " : "")
                + (addressOfServer != null ? "addressOfServer=" + addressOfServer + ", " : "")
                + (role != null ? "role=" + role + ", " : "") + "useServiceDiscoveryForWifi="
                + useServiceDiscoveryForWifi + ", timeOutBluetoothInSeconds=" + timeOutBluetoothInSeconds
                + ", timeOutWifiInSeconds=" + timeOutWifiInSeconds + ", maxConnectionAttemptsWifi="
                + maxConnectionAttemptsWifi + ", maxConnectionAttemptsBluetooth=" + maxConnectionAttemptsBluetooth
                + "]";
    }

}
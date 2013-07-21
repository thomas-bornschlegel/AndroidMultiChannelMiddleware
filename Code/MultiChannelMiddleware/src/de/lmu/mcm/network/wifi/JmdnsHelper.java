package de.lmu.mcm.network.wifi;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import de.lmu.mcm.helper.ByteConverter;
import de.lmu.mcm.helper.LogHelper;
import de.lmu.mcm.network.MultiNetworkAddress;
import de.lmu.mcm.network.ServiceDescription;

/**
 * Helper class to use Jmdns with Android.
 * 
 * @author Thomas Bornschlegel
 * 
 */
public class JmdnsHelper {

    private final String TAG = "WIFI Jmdns ";
    // Constants for the mDNS message types and parameters
    private final String KEY_DESCRIPTION = "description";
    private final String KEY_UUID = "uuid";
    private final String KEY_NAME = "name";
    private final String SERVICE_TYPE = "_lmu-mw._tcp.local.";
    private final String SERVICE_NAME = "uuid broadcaster";

    // For using JmDNS
    private JmDNS jmdns = null;
    private List<JmdnsServiceWrapper> servicesToFind = new ArrayList<JmdnsServiceWrapper>();
    private MulticastLock lock;
    private WifiCommunicator wifiCommunicator;

    // For clean termination
    private boolean advertisingStopped = false;
    private boolean isDiscovering = false;

    // To cache the service information the following variables are introduced. Used to reset the advertising if the
    // server thread should change the port during connection establishment:
    private String advertisingName;
    private String advertisingUuid;
    private String advertisingDescription;

    public JmdnsHelper(Context context, WifiCommunicator wifiCommunicator) {
        this.wifiCommunicator = wifiCommunicator;
        initJmdns(context);
    }

    /**
     * Initializes Jmdns.
     * */
    private boolean initJmdns(Context context) {
        // Partly based on
        // https://github.com/twitwi/AndroidDnssdDemo/blob/master/AndroidDnssdDiscoveryEclipse/src/com/heeere/android/dnssdtuto/DnssdDiscovery.java
        LogHelper.getInstance().d(TAG, "Initializing Jmdns...");
        LogHelper.getInstance().startToMeasureTime(TAG, 18032013);

        WifiManager wifiMgr = (WifiManager) context.getSystemService(android.content.Context.WIFI_SERVICE);
        lock = wifiMgr.createMulticastLock("multicastlock_lmu");
        lock.setReferenceCounted(true);
        lock.acquire();
        try {
            jmdns = JmDNS.create();
            LogHelper.getInstance().d(TAG, "Initialized Jmdns!");
            LogHelper.getInstance().stopToMeasureTime(TAG, 18032013);
            return jmdns != null;
        } catch (Exception e) {
            LogHelper.getInstance().e(TAG, "An error occured while trying to initialize JmDNS", e);
        }
        LogHelper.getInstance().stopToMeasureTime(TAG, 18032013);
        return false;
    }

    /**
     * Starts to listen for services that are advertised over mDNS and that match the given service info. A service is
     * considered as a match, when the DNS TXT record with key "uuid" is present and if the corresponding value is the
     * same as the UUID of the service.
     * */
    public void listenForMulticastServices(final ServiceDescription serviceToListenTo) {
        // TODO what happens if this method is called multiple times?
        // Each call generates a new listener. But each listener has the same type.
        // Do all listeners handle a new found service, or only one?

        ServiceListener listener = new ServiceListener() {

            @Override
            public void serviceResolved(ServiceEvent ev) {

                LogHelper.getInstance().d(TAG, "Found service: " + ev.getInfo().getQualifiedName());
                // Extract service name, description and uuid
                byte[] textBytes = ev.getInfo().getTextBytes();
                List<String> keyValuePairs = ByteConverter.decodeTextEntries(textBytes);
                String description = ByteConverter.getValueForKey(KEY_DESCRIPTION, keyValuePairs);
                String uuid = ByteConverter.getValueForKey(KEY_UUID, keyValuePairs);
                String name = ByteConverter.getValueForKey(KEY_NAME, keyValuePairs);
                LogHelper.getInstance().d(TAG,
                        "Service UUID: " + uuid + ", Name: " + name + ", Description: " + description);
                // String nameEvent = ev.getInfo().getName();
                // Obtain address of the sender of the service message
                MultiNetworkAddress address = new MultiNetworkAddress();
                address.setIpPort(ev.getInfo().getPort());
                for (InetAddress inetAddress : ev.getInfo().getInetAddresses()) {
                    if (inetAddress != null) {
                        address.setIpAddress(inetAddress);
                        LogHelper.getInstance().d(TAG,
                                "Found address of server: " + inetAddress + " port: " + address.getIpPort());
                        break;
                    }
                }
                ServiceDescription service = new ServiceDescription(UUID.fromString(uuid), description, name, address);

                if (service.equals(serviceToListenTo)) {
                    notifyServiceFound(address, service);
                    // stopDiscoveringServices();
                } else {
                    LogHelper.getInstance().d(TAG, "Parsed service description did not match: " + service);
                }

            }

            private void notifyServiceFound(MultiNetworkAddress address, ServiceDescription service) {
                LogHelper.getInstance().d(TAG, "Found matching service: " + service);
                if (address.getIpAddress() == null) {
                    LogHelper.getInstance().e(TAG, "Could not find address of server");
                }

                // Notify wifi communicator about the new service
                if (wifiCommunicator != null) {
                    wifiCommunicator.onServiceDiscovered(service);
                } else {
                    LogHelper.getInstance().e(TAG, "Wifi Communicator null!");
                }
            }

            @Override
            public void serviceRemoved(ServiceEvent ev) {
                LogHelper.getInstance().d(TAG, "Remove service: " + ev.getInfo().getQualifiedName());
            }

            @Override
            public void serviceAdded(ServiceEvent event) {
                // Required to force serviceResolved to be called again (after the first search)
                jmdns.requestServiceInfo(event.getType(), event.getName(), 1);
            }
        };

        servicesToFind.add(new JmdnsServiceWrapper(listener, SERVICE_TYPE));
        jmdns.addServiceListener(SERVICE_TYPE, listener);
        jmdns.requestServiceInfo(SERVICE_TYPE, SERVICE_NAME);
        isDiscovering = true;

        queryForService();
    }

    /**
     * Use this method to refresh the service list.
     * 
     * @return true if the query was performed, false if the query was dropped because the service was found already, or
     *         because we are not listening for services
     * */
    public boolean queryForService() {
        if (isDiscovering) {
            LogHelper.getInstance().d(TAG, "Requesting DNS Records...");
            jmdns.requestServiceInfo(SERVICE_TYPE, SERVICE_NAME);
            return true;
        } else {
            LogHelper.getInstance().e(TAG, "Currently not discovering, cannot query for servies");
        }
        return false;
    }

    /**
     * Advertises a new service using mDNS.
     * 
     * @param name
     *            the name of the service. Can be changed by jmdns to make it unique! So only rely on the uuid when
     *            searching for services!.
     * @param port
     *            the port of the server that is listening for clients
     * @param uuid
     *            the uuid of the service
     * @param description
     *            the description of the service
     * @return
     */
    public boolean advertiseMulticastService(String name, int port, String uuid, String description) {
        LogHelper.getInstance().d(TAG, "Starting to advertise service on wifi interface...");
        LogHelper.getInstance().startToMeasureTime(TAG, 3181831);
        try {
            byte[] uuidByte = ByteConverter.encodeKeyValuePair(KEY_UUID, uuid);
            byte[] descriptionByte = ByteConverter.encodeKeyValuePair(KEY_DESCRIPTION, description);
            byte[] nameByte = ByteConverter.encodeKeyValuePair(KEY_NAME, name);
            byte[] text = ByteConverter.combineMultipleByteArrays(uuidByte, descriptionByte, nameByte);

            int weight = 0;
            int prio = 1;

            ServiceInfo serviceInfo = ServiceInfo.create(SERVICE_TYPE, SERVICE_NAME, port, weight, prio, text);
            // XXX TODO Fix this
            // serviceInfo = ServiceInfo.create(SERVICE_TYPE, SERVICE_NAME, 0, "plain test service from android");

            if (jmdns == null) {
                LogHelper.getInstance().e(TAG, "Could not advertise service becaus jmdns was null.");
                return false;
            }

            jmdns.registerService(serviceInfo);
            LogHelper.getInstance().d(TAG, "Service is now being advertised on wifi interface on port " + port + "!");
            LogHelper.getInstance().stopToMeasureTime(TAG, 3181831);
            advertisingStopped = false;

            this.advertisingName = name;
            this.advertisingUuid = uuid;
            this.advertisingDescription = description;

            return true;
        } catch (Exception e) {
            LogHelper.getInstance().e(TAG, "An error occured while trying to advertise service on JMDNS.", e);
        }
        LogHelper.getInstance().stopToMeasureTime(TAG, 3181831);
        return false;
    }

    /**
     * Stops the current advertising that was started with
     * {@link #advertiseMulticastService(String, int, String, String)} and restarts it on a new port.
     * 
     * @param newPort
     */
    public void restartAdvertisingOnNewPort(int newPort) {
        if (wifiCommunicator != null && wifiCommunicator.isCanceled()) {
            // Do nothing
        } else if (advertisingName != null) {
            stopAdvertisingServices();
            advertiseMulticastService(advertisingName, newPort, advertisingUuid, advertisingDescription);
        } else {
            LogHelper.getInstance().e(TAG,
                    "Could not restart Jmdns service advertisment because it was not started before");
        }
    }

    /**
     * To be called when the interface is destroyed. Stops to advertise services and stops listening for services.
     * */
    public void closeJmDNS() {
        lock.release();

        if (!advertisingStopped) {
            stopAdvertisingServices();
        }

        if (isDiscovering) {
            stopDiscoveringServices();
        }

        if (jmdns != null) {

            try {
                jmdns.close();
            } catch (IOException e) {
                LogHelper.getInstance().e(TAG, "Error while trying to close jmdns.", e);
            }
            jmdns = null;
        }

    }

    /**
     * Stops advertising a service.
     * */
    public void stopAdvertisingServices() {
        if (jmdns != null) {
            jmdns.unregisterAllServices();
        }
        advertisingStopped = true;
    }

    /**
     * Stops discovering services.
     * */
    public void stopDiscoveringServices() {
        if (jmdns != null) {
            if (servicesToFind != null) {
                for (JmdnsServiceWrapper service : servicesToFind) {
                    jmdns.removeServiceListener(service.getType(), service.getListener());
                }
                servicesToFind.clear();
            }
        }
        isDiscovering = false;
    }

    /**
     * Wrapper class that maintains a ServiceListener and its type.
     * */
    private class JmdnsServiceWrapper {
        private ServiceListener listener;
        private String type;

        public JmdnsServiceWrapper(ServiceListener listener, String type) {
            super();
            this.listener = listener;
            this.type = type;
        }

        public ServiceListener getListener() {
            return listener;
        }

        public String getType() {
            return type;
        }

    }

}

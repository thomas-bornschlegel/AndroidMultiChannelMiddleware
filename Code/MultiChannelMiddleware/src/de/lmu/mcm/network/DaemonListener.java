package de.lmu.mcm.network;

import de.lmu.mcm.network.Enums.InterfaceIdentifier;
import de.lmu.mcm.security.byteproto.BasicMessage;

/**
 * A listener class to receive callbacks from the daemon in an activity.
 * 
 * @author Thomas Bornschlegel
 * 
 */
public interface DaemonListener {

    /**
     * Called when a connection was closed by the given interface.
     * */
    public void onInterfaceConnectionClosed(InterfaceIdentifier interfaceName);

    /**
     * Called when the given interface was destroyed.
     * */
    public void onInterfaceDestroyed(InterfaceIdentifier interfaceName);

    /**
     * Called when a connection setup failed in the given interface.
     * */
    public void onConnectionSetupFailed(InterfaceIdentifier interfaceName, MultiNetworkAddress address);

    /**
     * Called when a connection was set up by the given interface.
     * */
    public void onConnectionIsSetUp(InterfaceIdentifier interfaceName, MultiNetworkAddress address);

    /**
     * Called when new data was received by the given interface.
     * 
     * @param interfaceName
     *            the name of the interface
     * @param message
     *            the message or null if the message was not well formed
     */
    public void onDataReceived(InterfaceIdentifier interfaceName, BasicMessage message);

    /**
     * Called when new data was sent by the given interface.
     * 
     * @param interfaceName
     *            the name of the interface
     * @param message
     *            the message or null if the message was not well formed
     */
    public void onDataSent(InterfaceIdentifier interfaceName, ProtocolMessage message);

}

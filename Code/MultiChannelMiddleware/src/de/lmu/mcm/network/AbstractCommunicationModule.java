package de.lmu.mcm.network;

import android.app.Activity;
import de.lmu.mcm.helper.CancelableThread;

/**
 * Basic class for a communication interface. Provides common methods to communicate with the network daemon. Also
 * implements {@link CommunicationInterface} so that the {@link de.lmu.mm.network.NetworkDaemon NetworkDaemon} can
 * interact with communication channels through this interface.
 * */
/**
 * @author Thomas Bornschlegel
 * 
 */
public abstract class AbstractCommunicationModule implements CommonInterface {

    protected String TAG = "NOT INITIALIZED";
    private NetworkDaemon daemon;

    public AbstractCommunicationModule(Activity activity, NetworkDaemon daemon) {
        TAG = this.getClass().getSimpleName();
        this.daemon = daemon;
    }

    /**
     * 
     * Notifies the {@link de.lmu.mcm.network.NetworkDaemon NetworkDaemon} that a connection setup failed.
     * 
     * @param address
     *            the address to which the connection could not be set up.
     */
    protected void notifyDaemonConnectionSetupFailed(MultiNetworkAddress remoteAddress) {
        if (daemon != null) {
            daemon.onConnectionSetupFailed(getInterfaceName(), remoteAddress);
        }
    }

    /**
     * Notifies the {@link de.lmu.mcm.network.NetworkDaemon NetworkDaemon} that a connection was set up.
     * 
     * @param address
     *            the address to which the connection was set up.
     */
    protected void notifyDaemonConnectionIsSetUp(MultiNetworkAddress remoteAddress) {
        if (daemon != null) {
            daemon.onConnectionIsSetUp(getInterfaceName(), remoteAddress);
        }
    }

    /**
     * Notifies the {@link de.lmu.mcm.network.NetworkDaemon NetworkDaemon} that data was sent.
     * 
     * @param message
     *            the message that was sent by this interface.
     * @param success
     *            true if the message could be sent, false otherwise
     * */
    protected void notifyDaemonAboutSentData(ProtocolMessage message, boolean success) {
        if (daemon != null) {
            daemon.onDataSent(getInterfaceName(), message);
        }
    }

    /**
     * Notifies the {@link de.lmu.mcm.network.NetworkDaemon NetworkDaemon} that data was received.
     * 
     * @param message
     *            the message that was received by this interface.
     */
    protected void notifyDaemonAboutReceivedData(ProtocolMessage message) {
        if (daemon != null) {
            daemon.onDataReceived(getInterfaceName(), message);
        }
    }

    /**
     * Notifies the {@link de.lmu.mcm.network.NetworkDaemon NetworkDaemon} that the connection was terminated.
     */
    protected void notifyDaemonConnectionTerminated() {
        if (daemon != null) {
            daemon.onInterfaceConnectionClosed(getInterfaceName());
        }
    }

    /**
     * Notifies the {@link de.lmu.mcm.network.NetworkDaemon NetworkDaemon} that the interface was destroyed.
     */
    protected void notifyDaemonInterfaceDestroyed() {
        if (daemon != null) {
            daemon.onInterfaceDestroyed(getInterfaceName());
        }
    }

    /**
     * Convenience method. Cancels the given thread if it is not null.
     * 
     * @param thread
     *            the thread to be terminated.
     * */
    protected void cancelThread(CancelableThread thread) {
        if (thread != null) {
            thread.cancel();
            thread = null;
        }
    }

}

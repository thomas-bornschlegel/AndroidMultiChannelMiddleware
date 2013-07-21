package de.lmu.mcm.network;

import android.app.Activity;
import android.content.Intent;
import de.lmu.mcm.network.Enums.InterfaceIdentifier;

/**
 * Interface to provide a simple way to communicate with the {@link de.lmu.mcm.network.NetworkDaemon NetworkDaemon}.
 * 
 * @author Thomas Bornschlegel
 * 
 */
public interface CommonInterface {

    /**
     * Destroys this communication interface.
     * */
    public abstract void destroy(Activity activity);

    /**
     * Sets up a connection
     * 
     * This is only neccessary for connection oriented interfaces (Wifi, Bluetooth). It is further used for SMS to set
     * up the number to which data is sent. If a connection was set up, the interface should start listening for
     * messages with {@link #listenForMessages(Activity)}.
     * 
     * @param activity
     * @param serviceDescription
     *            the description for the service to which a connection should be set up
     */
    public abstract void setupConnection(Activity activity, ServiceDescription serviceDescription);

    /**
     * Stops the current connection.
     * 
     * @param activity
     */
    public abstract void stopCurrentConnection(Activity activity);

    /**
     * Sends some data.
     * 
     * @return true if the call succeeded and the CommunicationModule is sending the message.
     */
    public abstract boolean sendData(Activity activity, byte[] data);

    /**
     * Listens for new messages.
     * 
     * @return true if the call succeeded and the CommunicationModule is now waiting for a message.
     * */
    public abstract boolean listenForMessages(Activity activity);

    /**
     * Checks if this interface is ready to exchange data. It would be more accurately to use two methods here:
     * isReadyToRead, is readyToWrite. But for the first version this will do.
     * 
     * @return true if this interface is ready to exchange data
     * */
    public abstract boolean isReadyToExchangeData();

    /**
     * @return the name of this interface.
     */
    public abstract InterfaceIdentifier getInterfaceName();

    /**
     * Call this method in the onActivityResult-method of your activity. This is only necessary for some interfaces
     * (Barcodes) that depend on results from activities.
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
     * @return true if the result was handled by the interface
     * */
    public abstract boolean onActivityResult(Activity activity, int requestCode, int resultCode, Intent data);

    /**
     * Call this method in the onResume method of your activty. This is only necessary for some interfaces (Barcodes,
     * NFC, SMS) that depend on this android lifecycle method.
     * 
     * */
    public abstract void onResume(Activity activity);

    /**
     * Call this method in your onNewIntent method of your activity. This is only necessary for some interfaces (NFC)
     * that depend on this android lifecycle method
     * 
     * @param activity
     *            the activity that calls this method
     * @param data
     *            the intent from the onNewIntent-method of the calling activity
     */
    public abstract void onNewIntent(Activity activity, Intent data);

    /**
     * Call this method in your onPause method of your activity. This is only necessary for some interfaces (Barcodes,
     * NFC, SMS) that depend on this android lifecycle method
     * 
     * */
    public abstract void onPause(Activity activity);
}

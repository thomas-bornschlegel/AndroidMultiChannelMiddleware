package de.lmu.mcm.network.sms;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.SmsManager;
import de.lmu.mcm.helper.ByteConverter;
import de.lmu.mcm.helper.LogHelper;
import de.lmu.mcm.network.AbstractCommunicationModule;
import de.lmu.mcm.network.Enums;
import de.lmu.mcm.network.MultiNetworkAddress;
import de.lmu.mcm.network.NetworkDaemon;
import de.lmu.mcm.network.ProtocolMessage;
import de.lmu.mcm.network.ServiceDescription;

public class SmsCommunicator extends AbstractCommunicationModule {

    private BroadcastReceiverSmsRead receiverSmsRead;
    private BroadcastReceiverSmsSent receiverSmsSent;
    private IntentFilter filterRead = BroadcastReceiverSmsRead.getIntentFilter();
    private IntentFilter filterSent = BroadcastReceiverSmsSent.getIntentFilter();
    private boolean receiversRegistered = false;
    private static final String TAG = "SMS";

    private MultiNetworkAddress serviceAddress = null;
    private ServiceDescription serviceDescription;

    public SmsCommunicator(Activity activity, NetworkDaemon daemon) {
        super(activity, daemon);
        receiverSmsRead = new BroadcastReceiverSmsRead(this);
        activity.registerReceiver(receiverSmsRead, filterRead);

        receiverSmsSent = new BroadcastReceiverSmsSent(this);
        activity.registerReceiver(receiverSmsSent, filterSent);
    }

    @Override
    public Enums.InterfaceIdentifier getInterfaceName() {
        return Enums.InterfaceIdentifier.SMS;
    }

    @Override
    public void destroy(Activity activity) {
        unregisterReceivers(activity);
    }

    @Override
    public void setupConnection(Activity activity, ServiceDescription serviceDescription) {
        this.serviceDescription = serviceDescription;
        MultiNetworkAddress address = serviceDescription.getAddressOfServer();
        if (address.getSmsAddress() != null) {
            this.serviceAddress = address;
            LogHelper.getInstance().d(
                    TAG,
                    "Will send SMS to this address: " + address.getSmsAddress() + " (ID: " + address.getDeviceId()
                            + ")");
            notifyDaemonConnectionIsSetUp(serviceAddress);
        } else {
            notifyDaemonConnectionSetupFailed(address);
            LogHelper.getInstance().v(TAG,
                    "No SMS address given for ID: " + address.getDeviceId() + ". Cannot send any SMSs to this device.");
        }
    }

    @Override
    public boolean sendData(Activity activity, byte[] data) {
        if (isReadyToExchangeData()) {
            SmsManager smsManager = SmsManager.getDefault();
            String base64Encoded = ByteConverter.encodeAsBase64String(data);

            // To receive a notification when the SMS was sent:
            Intent intent = new Intent(BroadcastReceiverSmsSent.ACTION_SMS_SENT);
            intent.putExtra(BroadcastReceiverSmsSent.EXTRA_MSG_CONTENT, data);
            intent.putExtra(BroadcastReceiverSmsSent.EXTRA_SENDER_ADDRESS, serviceAddress);
            PendingIntent sentIntent = PendingIntent.getBroadcast(activity, 0, intent,
                    PendingIntent.FLAG_CANCEL_CURRENT);

            // To receive a notification when the SMS was delivered:
            intent = new Intent(BroadcastReceiverSmsSent.ACTION_SMS_DELIVERED);
            intent.putExtra(BroadcastReceiverSmsSent.EXTRA_MSG_CONTENT, data);
            PendingIntent deliveredIntent = PendingIntent.getBroadcast(activity, 0, intent,
                    PendingIntent.FLAG_CANCEL_CURRENT);

            smsManager
                    .sendTextMessage(serviceAddress.getSmsAddress(), null, base64Encoded, sentIntent, deliveredIntent);

            return true;
        } else {
            LogHelper
                    .getInstance()
                    .e(TAG,
                            "No number for sending SMS given. Please call setupConnection first, or wait for a message containing the address to send to.");
        }
        return false;
    }

    /**
     * Sends the given data as a binary SMS. Based on
     * https://code.google.com/p/krvarma-android-samples/source/browse/trunk
     * /SMSDemo/src/com/varma/samples/smsdemo/MainActivity.java
     * 
     * THIS METHOD IS NOT USED BECAUSE ANROID DOES NOT PROVIDE MULTIPART BINARY MESSAGES!
     * 
     * @param data
     *            the data to send
     * 
     * */
    @Deprecated
    public boolean sendDataBinary(Activity activity, byte[] data) {
        if (isReadyToExchangeData()) {

            SmsManager smsManager = SmsManager.getDefault();

            // To receive a notification when the SMS was sent:
            Intent intent = new Intent(BroadcastReceiverSmsSent.ACTION_SMS_SENT);
            intent.putExtra(BroadcastReceiverSmsSent.EXTRA_MSG_CONTENT, data);
            intent.putExtra(BroadcastReceiverSmsSent.EXTRA_SENDER_ADDRESS, serviceAddress);
            PendingIntent sentIntent = PendingIntent.getBroadcast(activity, 0, intent,
                    PendingIntent.FLAG_CANCEL_CURRENT);

            // To receive a notification when the SMS was delivered:
            intent = new Intent(BroadcastReceiverSmsSent.ACTION_SMS_DELIVERED);
            intent.putExtra(BroadcastReceiverSmsSent.EXTRA_MSG_CONTENT, data);
            PendingIntent deliveredIntent = PendingIntent.getBroadcast(activity, 0, intent,
                    PendingIntent.FLAG_CANCEL_CURRENT);

            smsManager.sendDataMessage(serviceAddress.getSmsAddress(), null, (short) 8091, data, sentIntent,
                    deliveredIntent);

            return true;
        } else {
            LogHelper
                    .getInstance()
                    .e(TAG,
                            "No number for sending SMS given. Please call setupConnection first, or wait for a message containing the address to send to.");
        }
        return false;
    }

    @Override
    public boolean onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        return false;
    }

    @Override
    public boolean listenForMessages(Activity activity) {
        // Nothing to do here, as we wait for messages anyway with the broadcastReceivers
        return true;
    }

    @Override
    public boolean isReadyToExchangeData() {
        if (serviceDescription == null || (serviceAddress == null)) {
            return false;
        }
        return true;
    }

    public void onDataSent(ProtocolMessage protocolMsg) {
        notifyDaemonAboutSentData(protocolMsg, true);
    }

    public void onDataReceived(ProtocolMessage protocolMsg) {
        // if (serviceDescription.getRole() == Role.SERVER) {
        // serviceAddress = protocolMsg.getAddress();
        // }
        notifyDaemonAboutReceivedData(protocolMsg);
    }

    @Override
    public void stopCurrentConnection(Activity activity) {
        serviceDescription = null;
        serviceAddress = null;
    }

    @Override
    public void onResume(Activity activity) {
        registerReceivers(activity);
    }

    @Override
    public void onNewIntent(Activity activity, Intent data) {
        // Nothing to do here
    }

    @Override
    public void onPause(Activity activity) {
        unregisterReceivers(activity);
    }

    private void registerReceivers(Context context) {
        if (!receiversRegistered) {
            if (receiverSmsRead != null) {
                context.registerReceiver(receiverSmsRead, filterRead);
                LogHelper.getInstance().d(TAG, "Registered BroadcastReceiver to read SMS");
            }
            if (receiverSmsSent != null) {
                context.registerReceiver(receiverSmsSent, filterSent);
                LogHelper.getInstance().d(TAG, "Registered BroadcastReceiver to get notifications about sent SMS");
            }
            receiversRegistered = true;
        } else {
            LogHelper.getInstance().e(TAG, "BroadcastReceivers for SMS were already registered!");
        }
    }

    private void unregisterReceivers(Context context) {
        if (receiversRegistered) {
            if (receiverSmsRead != null) {
                context.unregisterReceiver(receiverSmsRead);
                LogHelper.getInstance().d(TAG, "Unregistered BroadcastReceiver to read SMS");
            }
            if (receiverSmsSent != null) {
                context.unregisterReceiver(receiverSmsSent);
                LogHelper.getInstance().d(TAG, "Unregistered BroadcastReceiver to get notifications about sent SMS");
            }
            receiversRegistered = false;
        } else {
            LogHelper.getInstance().e(TAG, "BroadcastReceivers for SMS were already unregistered!");
        }
    }

}
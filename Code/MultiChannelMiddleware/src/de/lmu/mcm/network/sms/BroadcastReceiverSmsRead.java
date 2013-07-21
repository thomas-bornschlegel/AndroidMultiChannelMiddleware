package de.lmu.mcm.network.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.telephony.SmsMessage;
import de.lmu.mcm.helper.ByteConverter;
import de.lmu.mcm.helper.LogHelper;
import de.lmu.mcm.network.MultiNetworkAddress;
import de.lmu.mcm.network.ProtocolMessage;
import de.lmu.mcm.network.Enums.MessageOrigin;

public class BroadcastReceiverSmsRead extends BroadcastReceiver {

    private SmsCommunicator smsCommunication;
    protected static final String ACTION_SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";
    private static IntentFilter filter = new IntentFilter(ACTION_SMS_RECEIVED);
    private static final String TAG = "SMS";

    public BroadcastReceiverSmsRead(SmsCommunicator smsCommunication) {
        this.smsCommunication = smsCommunication;
    }

    public static IntentFilter getIntentFilter() {
        return filter;
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        Bundle bundle = intent.getExtras();
        SmsMessage[] msgs = null;
        String logMessage = "";
        if (bundle != null) {
            Object[] pdu = (Object[]) bundle.get("pdus");
            msgs = new SmsMessage[pdu.length];
            String sender = null;
            StringBuilder content = new StringBuilder();
            for (int i = 0; i < msgs.length; i++) {
                msgs[i] = SmsMessage.createFromPdu((byte[]) pdu[i]);
                sender = msgs[i].getOriginatingAddress();
                // Concatenate multiple SMS into a single message:
                content.append(msgs[i].getMessageBody().toString());
                logMessage += "SMS received from " + sender + " :" + content + "\n";
            }
            String result = content.toString();
            byte[] resultInBytes = ByteConverter.decodeBase64String(result);
            MultiNetworkAddress address = new MultiNetworkAddress();
            address.setSmsAddress(sender);
            LogHelper.getInstance().d(TAG, "Received SMS: " + logMessage);
            if (smsCommunication != null) {
                ProtocolMessage protocolMsg = new ProtocolMessage(MessageOrigin.REMOTE, address, resultInBytes);
                smsCommunication.onDataReceived(protocolMsg);
            }
        }

    }

    /**
     * Receives a binary SMS.
     * 
     * THIS METHOD IS NOT USED, AS ANROID DOES NOT PROVIDE MULTIPART BINARY MESSAGES!
     * 
     * Based on:
     * https://code.google.com/p/krvarma-android-samples/source/browse/trunk/SMSDemo/src/com/varma/samples/smsdemo
     * /BinarySMSReceiver.java
     * */
    @Deprecated
    public void onReceiveBinarySMS(Context context, Intent intent) {

        Bundle bundle = intent.getExtras();
        SmsMessage[] msgs = null;
        String logMessage = "";
        if (bundle != null) {
            Object[] pdu = (Object[]) bundle.get("pdus");
            msgs = new SmsMessage[pdu.length];
            String sender = null;
            byte[] data = null;
            for (int i = 0; i < msgs.length; i++) {
                msgs[i] = SmsMessage.createFromPdu((byte[]) pdu[i]);
                sender = msgs[i].getOriginatingAddress();
                byte[] tmp = msgs[i].getUserData();
                // Concatenate multiple SMS into a single message:
                data = ByteConverter.combineMultipleByteArrays(data, tmp);
                logMessage += "Parsed " + (i + 1) + " SMS from " + sender;
            }
            MultiNetworkAddress address = new MultiNetworkAddress();
            address.setSmsAddress(sender);
            LogHelper.getInstance().d(TAG, "Received SMS: " + logMessage);
            if (smsCommunication != null) {
                ProtocolMessage protocolMsg = new ProtocolMessage(MessageOrigin.REMOTE, address, data);
                smsCommunication.onDataReceived(protocolMsg);
            }
        }

    }
}

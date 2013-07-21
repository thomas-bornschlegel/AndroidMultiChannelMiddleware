package de.lmu.mcm.network.sms;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import de.lmu.mcm.helper.LogHelper;
import de.lmu.mcm.network.MultiNetworkAddress;
import de.lmu.mcm.network.ProtocolMessage;
import de.lmu.mcm.network.Enums.MessageOrigin;

public class BroadcastReceiverSmsSent extends BroadcastReceiver {

    public static final String ACTION_SMS_SENT = "action sms sent";
    public static final String ACTION_SMS_DELIVERED = "action sms delivered";
    public static final String EXTRA_MSG_CONTENT = "msg content";
    public static final String EXTRA_SENDER_ADDRESS = "sender number";
    private static final String TAG = "SMS";

    private static IntentFilter filter;
    private SmsCommunicator smsCommunication;

    public BroadcastReceiverSmsSent(SmsCommunicator smsCommunication) {
        this.smsCommunication = smsCommunication;
    }

    public static IntentFilter getIntentFilter() {
        if (filter == null) {
            filter = new IntentFilter();
            filter.addAction(ACTION_SMS_SENT);
            filter.addAction(ACTION_SMS_DELIVERED);
        }
        return filter;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        LogHelper.getInstance().d(TAG, "Received SMS delivery or sent intent: " + intent.getAction());

        Bundle bundle = intent.getExtras();
        byte[] msg = bundle.getByteArray(EXTRA_MSG_CONTENT);
        MultiNetworkAddress senderAddress = (MultiNetworkAddress) bundle.getSerializable(EXTRA_SENDER_ADDRESS);
        int resultCode = getResultCode();
        String result = "";

        if (intent.getAction().equals(ACTION_SMS_SENT)) {
            boolean error = true;
            switch (resultCode) {
            case Activity.RESULT_OK:
                result = "SMS sent successfully";
                if (smsCommunication != null) {
                    ProtocolMessage protocolMsg = new ProtocolMessage(MessageOrigin.SELF, senderAddress, msg);
                    smsCommunication.onDataSent(protocolMsg);
                }
                error = false;
                break;
            default:
            case android.telephony.SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                Object errorCode = bundle.get("errorCode");
                result = "SMS NOT SENT! Generic error ";
                if (errorCode != null) {
                    result += errorCode;
                }
                break;
            case android.telephony.SmsManager.RESULT_ERROR_RADIO_OFF:
                result = "SMS NOT SENT! Radio was turned off.";
                break;
            case android.telephony.SmsManager.RESULT_ERROR_NULL_PDU:
                result = "SMS NOT SENT! Null PDU.";
                break;
            case android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE:
                result = "SMS NOT SENT! No servcie.";
                break;
            }

            if (error) {
                LogHelper.getInstance().e(TAG, result);
            } else {
                LogHelper.getInstance().d(TAG, result);
            }

        } else if (intent.getAction().equals(ACTION_SMS_DELIVERED)) {
            // TODO do we need this?
            Object pdu = bundle.get("pdu");
            LogHelper.getInstance().d(TAG, "SMS delivered");
            if (pdu != null) {
                LogHelper.getInstance().d(TAG, "SMS Content: " + pdu);
            }
        }

    }
}

package de.lmu.mcm.activity;

import java.io.Serializable;
import java.util.UUID;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import de.lmu.mcm.helper.LogHelper;
import de.lmu.mcm.helper.PrefsHelper;
import de.lmu.mcm.helper.TestSetup;
import de.lmu.mcm.network.DaemonListener;
import de.lmu.mcm.network.Enums.InterfaceIdentifier;
import de.lmu.mcm.network.MultiNetworkAddress;
import de.lmu.mcm.network.NetworkDaemon;
import de.lmu.mcm.network.ProtocolMessage;
import de.lmu.mcm.network.ServiceDescription;
import de.lmu.mcm.security.MessageEncryptionHandler;
import de.lmu.mcm.security.byteproto.BasicMessage;
import de.lmu.mcm.security.byteproto.CustomMessage;
import de.lmu.mm.R;

/**
 * Activity that displays the chat interface and provides an encrypted chat over all supported interfaces.
 * 
 * @author Thomas Bornschlegel
 * 
 */
public class ChatActivity extends AbstractMultiChannelActivity implements DaemonListener {

    private EditText editTextInput;
    private TextView textViewSelectedInterface;
    private final int handlerCodeClearInput = 8349;
    private ServiceDescription service = null;
    private final static String TAG = "ChatActivity";
    private MessageEncryptionHandler encryptionHandler = new MessageEncryptionHandler();
    private byte messageType = 6;

    private Handler handler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == handlerCodeClearInput) {
                editTextInput.setText("");
            }
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        LogHelper.getInstance().i(TAG, "Starting ChatActivity");
        initializeViews();
        editTextInput = (EditText) findViewById(R.id.editTextChatInput);
        textViewSelectedInterface = (TextView) findViewById(R.id.textViewSendVia);
        textViewSelectedInterface.setText(getResources().getString(R.string.no_interface_selected));
        linearLayoutInterfaceSelection.setVisibility(View.GONE);
        getDaemon().checkInitialized(this);
        initializeListOfAvailableInterfaces();
        getDaemon().setListener(this);
        service = TestSetup.getServiceDescription(this);
        // getDaemon().establishConnection(this, service);

        // if (service.getRole() == Role.SERVER) {
        // displayNewLogMessage("Trying to establish connection as server");
        // } else {
        // displayNewLogMessage("Trying to establish connection as client");
        // }
        // getDaemon().establishConnection(this, service);

        Serializable tmpInterface = getIntent().getExtras()
                .getSerializable(NetworkDaemon.EXTRA_LAST_SELECTED_INTERFACE);
        if (tmpInterface != null) {
            selectedInterface = (InterfaceIdentifier) getIntent().getExtras().getSerializable(
                    NetworkDaemon.EXTRA_LAST_SELECTED_INTERFACE);
        }

    }

    public void onChangeInterfaceClicked(View v) {
        if (linearLayoutInterfaceSelection.getVisibility() == View.VISIBLE) {
            findViewById(R.id.linearLayoutInterfaceSelector).setVisibility(View.GONE);
        } else {
            initializeListOfAvailableInterfaces();
            findViewById(R.id.linearLayoutInterfaceSelector).setVisibility(View.VISIBLE);
        }
    }

    public void onScanBarcodeClicked(View v) {
        Toast.makeText(this, R.string.toast_barcode_scan_initiated, Toast.LENGTH_SHORT).show();
        getDaemon().waitForData(InterfaceIdentifier.BARCODES, this);
    }

    public void onSendButtonClicked(View v) {
        String msg = editTextInput.getText().toString();
        if (msg != null && !msg.equals("")) {
            CustomMessage message = new CustomMessage(msg.getBytes());
            try {
                sendDataWithSelectedInterface(message, messageType);
            } catch (Exception e) {
                LogHelper.getInstance().e(TAG, "Error while trying to send message", e);
                displayNewLogMessage("Could not send message");
            }
        }
    }

    @Override
    public void onInterfaceChanged(String selectedInterfaceName) {
        if (selectedInterfaceName != null) {
            String msg = getResources().getString(R.string.send_via) + selectedInterfaceName;
            textViewSelectedInterface.setText(msg);
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        }

        linearLayoutInterfaceSelection.setVisibility(View.GONE);
    }

    @Override
    public void onInterfaceConnectionClosed(InterfaceIdentifier interfaceName) {
        displayNewLogMessage("Interface connection lost: " + interfaceName);
    }

    @Override
    public void onInterfaceDestroyed(InterfaceIdentifier interfaceName) {
        displayNewLogMessage("Interface destroyed: " + interfaceName);
    }

    @Override
    public void onConnectionSetupFailed(InterfaceIdentifier interfaceName, MultiNetworkAddress address) {
        displayNewLogMessage("Connection setup failed: " + interfaceName);
    }

    @Override
    public void onConnectionIsSetUp(InterfaceIdentifier interfaceName, MultiNetworkAddress address) {
        displayNewLogMessage("Connection setup: " + interfaceName);
    }

    @Override
    public void onDataReceived(InterfaceIdentifier interfaceName, BasicMessage message) {
        handleNewMessage(interfaceName, message, true);
    }

    @Override
    public void onDataSent(InterfaceIdentifier interfaceName, ProtocolMessage message) {
        if (message != null) {
            String uuidString = PrefsHelper.getOwnId(this);
            UUID uuid = UUID.fromString(uuidString);
            BasicMessage extractedMessage;
            try {
                extractedMessage = encryptionHandler.extractReceivedMessage(this, uuid, message.getRawMessageInBytes());
                handleNewMessage(interfaceName, extractedMessage, false);
            } catch (Exception e) {
                // This should never happen:
                displayNewLogMessage("Message was sent via " + interfaceName + " but it could not be parsed");
                LogHelper.getInstance().e(TAG, "Message was sent via " + interfaceName + " but it could not be parsed",
                        e);
            }
        } else {
            displayNewLogMessage("Could not send message via " + interfaceName + ". No connection established yet?");
        }
    }

    /**
     * Checks if the message is well formed and displays it in the listview.
     * */
    private void handleNewMessage(InterfaceIdentifier interfaceName, BasicMessage extractedMsg, boolean isRemoteMessage) {
        try {
            String toDisplay = null;
            if (extractedMsg == null) {
                toDisplay = "MESSAGE WAS NULL!";
            } else {
                toDisplay = new String(extractedMsg.getMessageContentAsBytes());
            }
            if (isRemoteMessage) {
                displayNewRemoteMessage(toDisplay + " via " + interfaceName);
            } else {
                displaySentMessage(toDisplay + " via " + interfaceName);
                handler.sendEmptyMessage(handlerCodeClearInput);
            }

        } catch (Exception e) {
            LogHelper.getInstance().e(TAG, "Error while trying to read own message");
            displayNewLogMessage("Error while trying to read own message");
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (getDaemon().onActivityResult(this, requestCode, resultCode, data)) {
            // Result was handled by one of the daemons interfaces
        }
    }

}
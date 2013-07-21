package de.lmu.mcm.activity;

import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import javax.crypto.NoSuchPaddingException;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import de.lmu.mcm.helper.LogHelper;
import de.lmu.mcm.helper.PrefsHelper;
import de.lmu.mcm.helper.TestSetup;
import de.lmu.mcm.network.Enums.HandshakeNextAction;
import de.lmu.mcm.network.Enums.HandshakeRole;
import de.lmu.mcm.network.Enums.InterfaceIdentifier;
import de.lmu.mcm.network.Enums.Role;
import de.lmu.mcm.network.NetworkDaemon;
import de.lmu.mcm.network.ProtocolMessage;
import de.lmu.mcm.network.ServiceDescription;
import de.lmu.mcm.security.HandshakeStateHolder;
import de.lmu.mcm.security.byteproto.BasicMessage;
import de.lmu.mm.R;

/**
 * Activity to perform the 3-way-handshake and to exchange the symmetric key.
 * 
 * @author Thomas Bornschlegel
 * 
 */
public class HandshakeActivity extends AbstractMultiChannelActivity {

    private Button buttonSendReceive;
    private Button buttonContinue;
    private TextView textViewHandshakeStep;
    private TextView textViewSelectedInterface;
    private HandshakeStateHolder handshakeHelper;
    private NetworkDaemon daemon;
    private final static String TAG = "HandshakeActivity";
    private int handlerCodeToggleButtons = 45945602;
    private BasicMessage lastSentMessage;
    private byte messageTypeOfLastSentMessage;

    private Handler handlerForButtonToggle = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == handlerCodeToggleButtons) {
                if (handshakeHelper != null) {
                    HandshakeNextAction nextAction = handshakeHelper.getNextAction();
                    if (nextAction == HandshakeNextAction.SEND) {
                        buttonSendReceive.setEnabled(true);
                        buttonSendReceive.setText(R.string.button_handshake_send);
                    } else {
                        buttonSendReceive.setEnabled(true);
                        buttonSendReceive.setText(R.string.button_handshake_receive);
                    }

                    if (handshakeHelper.isHandshakeComplete()) {
                        buttonContinue.setEnabled(true);
                    }

                    int step = handshakeHelper.getCurrentStep();
                    String handshakeStep = step < 5 ? String.valueOf(handshakeHelper.getCurrentStep())
                            : "Handshake complete!";
                    textViewHandshakeStep.setText(handshakeStep);
                    if (step >= 5) {
                        Toast.makeText(getApplicationContext(), "Handshake completed!", Toast.LENGTH_LONG).show();
                    }
                }
            }
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_handshake);
        LogHelper.getInstance().i(TAG, "Starting HandshakeActivity");
        initializeViews();
        textViewSelectedInterface = (TextView) findViewById(R.id.textViewSendVia);
        textViewSelectedInterface.setText(getResources().getString(R.string.no_interface_selected));
        buttonSendReceive = (Button) findViewById(R.id.buttonHandshake);
        buttonContinue = (Button) findViewById(R.id.buttonContinue);
        textViewHandshakeStep = (TextView) findViewById(R.id.textViewHandshakeCurrentMessage);

        UUID uuidRemote = UUID.fromString(PrefsHelper.getIdOfCommunicationPartner(getApplicationContext()));
        UUID uuidOwn = UUID.fromString(PrefsHelper.getOwnId(getApplicationContext()));

        try {
            boolean isServer = uuidOwn.compareTo(uuidRemote) > 0;
            HandshakeRole role = isServer ? HandshakeRole.A : HandshakeRole.B;
            if (role == HandshakeRole.A) {
                handshakeHelper = new HandshakeStateHolder(uuidOwn, uuidRemote, role);
            } else {
                handshakeHelper = new HandshakeStateHolder(uuidRemote, uuidOwn, role);
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        }

        if (handshakeHelper == null) {
            Toast.makeText(getApplicationContext(), R.string.error_handshake_init, Toast.LENGTH_LONG).show();
        } else {
            App app = (App) getApplication();
            daemon = app.getNetworkDaemon();
            daemon.checkInitialized(this);

            toggleButtons();
        }

        initializeListOfAvailableInterfaces();

        ServiceDescription service = TestSetup.getServiceDescription(this);
        if (service.getRole() == Role.SERVER) {
            displayNewLogMessage("Trying to establish connection as server");
        } else {
            displayNewLogMessage("Trying to establish connection as client");
            buttonContinue.setEnabled(false);
        }
        displayNewLogMessage("Own UUID: " + PrefsHelper.getOwnId(this));
        getDaemon().establishConnection(this, service);

    }

    public void onChangeInterfaceClicked(View v) {
        if (linearLayoutInterfaceSelection.getVisibility() == View.VISIBLE) {
            findViewById(R.id.linearLayoutInterfaceSelector).setVisibility(View.GONE);
        } else {
            initializeListOfAvailableInterfaces();
            findViewById(R.id.linearLayoutInterfaceSelector).setVisibility(View.VISIBLE);
        }
    }

    private void toggleButtons() {
        handlerForButtonToggle.sendEmptyMessage(handlerCodeToggleButtons);
    }

    public void onScanBarcodeClicked(View v) {
        Toast.makeText(this, R.string.toast_barcode_scan_initiated, Toast.LENGTH_SHORT).show();
        getDaemon().waitForData(InterfaceIdentifier.BARCODES, this);
    }

    public void onButtonSendClicked(View v) {
        if (buttonSendReceive.isEnabled() && handshakeHelper != null
                && handshakeHelper.getNextAction() == HandshakeNextAction.SEND) {
            BasicMessage msg = handshakeHelper.getNextMessageToSend(getApplicationContext());
            if (msg != null) {
                lastSentMessage = msg;
                messageTypeOfLastSentMessage = handshakeHelper.getMessageTypeForMessageToSend();
                sendDataWithSelectedInterface(msg, handshakeHelper.getMessageTypeForMessageToSend());
            } else {
                displayNewLogMessage("Error preparing next handshake message");
            }
        } else if (lastSentMessage != null) {
            sendDataWithSelectedInterface(lastSentMessage, messageTypeOfLastSentMessage);
        }
    }

    public void onButtonContinueClicked(View v) {
        if (buttonContinue.isEnabled()) {
            startNewActivity(ChatActivity.class);
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
    public void onDataReceived(InterfaceIdentifier interfaceName, BasicMessage message) {
        int currentStep = handshakeHelper.getCurrentStep();
        displayNewRemoteMessage("Received handshake message via " + interfaceName);
        try {
            boolean success = handshakeHelper.processReceivedMessage(getApplicationContext(), message);
            displayNewLogMessage("Parsed handshake message " + currentStep + " with result=" + success);
        } catch (Exception e) {
            displayNewLogMessage("Error while parsing handshake message " + currentStep);
        }
        toggleButtons();
    }

    @Override
    public void onDataSent(InterfaceIdentifier interfaceName, ProtocolMessage message) {
        int currentStep = handshakeHelper.getCurrentStep();
        currentStep--;
        if (message != null) {
            displaySentMessage("Sent handshake message " + currentStep + " via " + interfaceName);
            toggleButtons();
        } else {
            displayNewLogMessage("Could not send handshake message " + currentStep + " via " + interfaceName
                    + ". No connection established yet?");
        }
    }

}

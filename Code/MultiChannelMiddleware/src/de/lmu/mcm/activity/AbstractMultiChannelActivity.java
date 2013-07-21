package de.lmu.mcm.activity;

import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.Toast;
import de.lmu.mcm.helper.InterfaceAvailabilityChecker;
import de.lmu.mcm.helper.LogHelper;
import de.lmu.mcm.network.DaemonListener;
import de.lmu.mcm.network.Enums.InterfaceIdentifier;
import de.lmu.mcm.network.MultiNetworkAddress;
import de.lmu.mcm.network.NetworkDaemon;
import de.lmu.mcm.network.ProtocolMessage;
import de.lmu.mcm.security.byteproto.BasicMessage;
import de.lmu.mm.R;

/**
 * An abstract activity that should be used to create new Activities that communicate with the NetworkDaemon. An
 * activity that extends this class must have a listView with the id: listViewChat and a RadioButtonGroup for the
 * interface selection. See {@link #initializeViews()} for details.
 * 
 * @author Thomas Bornschlegel
 * 
 */
public abstract class AbstractMultiChannelActivity extends Activity implements DaemonListener {

    private ArrayAdapter<String> messageArrayAdapter;
    protected LinearLayout linearLayoutInterfaceSelection;
    private int messageNumber = 1;
    private InterfaceAvailabilityChecker availChecker = new InterfaceAvailabilityChecker();
    protected InterfaceIdentifier selectedInterface = null;
    private final int handlerCodeNewMsg = 235235;
    private boolean newActivityLaunched = false;
    public final String EXTRA_SELECTED_INTERFACE = "selected interface";

    private Handler handler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == handlerCodeNewMsg) {
                String str = (String) msg.obj;
                messageArrayAdapter.add(str);
                // To append messages to the front of the list use:
                // messageArrayAdapter.insert(str, 0);
            }
        }

    };

    /**
     * Calls the normal onCreate method. Checks if the NetworkDaemon is initialized and initializes it if necessary.
     * MAKE SURE TO CALL {@link #initializeViews()} IN YOUR CUSTOM ACTIVITY HERE!
     * */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getDaemon().checkInitialized(this);
        getDaemon().setListener(this);
    };

    @Override
    protected void onResume() {
        super.onResume();
        getDaemon().onResume(this);
    }

    @Override
    protected void onPause() {
        super.onResume();
        getDaemon().onPause(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        getDaemon().onNewIntent(this, intent);
    }

    /**
     * Use this method if you want to start a new activity but want to keep the current state of the NetworkDaemon.
     * */
    protected void startNewActivity(Class<?> classOfNewActivity) {
        getDaemon().setListener(null);
        newActivityLaunched = true;
        getDaemon().destroyActivityAndLaunchNew(this, classOfNewActivity, selectedInterface);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!newActivityLaunched) {
            LogHelper.getInstance().d(this.getClass().getSimpleName(), "onDestroy called => Destroying DAEMON");
            getDaemon().setListener(null);
            getDaemon().destroyInterfaces(this);
        } else {
            LogHelper.getInstance().d(this.getClass().getSimpleName(),
                    "onDestroy called but DAEMON is kept alive because we launched a new activity");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        getDaemon().onActivityResult(this, requestCode, resultCode, data);
    }

    protected NetworkDaemon getDaemon() {
        App app = (App) getApplication();
        return app.getNetworkDaemon();
    }

    private void displayMessage(String msg) {
        Message m = new Message();
        m.what = handlerCodeNewMsg;
        m.obj = msg;
        handler.sendMessage(m);
    }

    /**
     * Use this method to display a new log message. The message will be inserted in the ListView with the prefix "LOG".
     * It hands over the call to update the UI to a handler so that we will not get any threading issues.
     * */
    protected void displayNewLogMessage(String msg) {
        displayMessage("LOG: " + msg);
    }

    /**
     * Use this method to display a newly received message. The message will be inserted in the ListView with the prefix
     * "IN". It hands over the call to update the UI to a handler so that we will not get any threading issues.
     * */
    protected void displayNewRemoteMessage(String msg) {
        displayMessage(messageNumber++ + " IN: " + msg);
    }

    /**
     * Use this method to display a sent message. The message will be inserted in the ListView with the prefix "OUT". It
     * hands over the call to update the UI to a handler so that we will not get any threading issues.
     * */
    protected void displaySentMessage(String msg) {
        displayMessage(messageNumber++ + " OUT: " + msg);
    }

    /**
     * This call should be called prior to sending data on the selected interface. It checks if the interface is
     * initialized and notifies the user if this is not the case.
     * */
    public boolean isInterfaceSetUp() {
        if (selectedInterface == null) {
            Toast.makeText(this, R.string.no_interface_selected, Toast.LENGTH_SHORT).show();
            return false;
        } else if (!isInterfaceReady(selectedInterface)) {
            Toast.makeText(this, R.string.interface_not_supported, Toast.LENGTH_SHORT).show();
            return false;
        } else {
            return true;
        }
    }

    /**
     * Sends the given data with the currently selected interface. Checks if an interface is slected and if it is set up
     * properly.
     * 
     * @param data
     *            the data in raw bytes.
     * */
    public void sendDataWithSelectedInterface(BasicMessage message, byte messageType) {
        if (message == null) {
            LogHelper.getInstance().e("AbstractUI", "Did not execute send call because data was null!");
        } else if (!isInterfaceSetUp()) {
            LogHelper.getInstance().e("AbstractUI",
                    "Did not execute send call because no interface selected or interface not ready!");
        } else if (getDaemon() == null) {
            LogHelper.getInstance().e("AbstractUI", "Did not execute send call because DAEMON was null!");
        } else {
            try {
                getDaemon().sendData(message, messageType, selectedInterface, this);
            } catch (Exception e) {
                displayNewLogMessage("Could NOT send message via " + selectedInterface);
                LogHelper.getInstance().e(
                        "AbstractUI",
                        "Could not send message because an exception of type " + e.getClass().getSimpleName()
                                + " occured.", e);
            }
        }
    }

    /**
     * Changes the interface.
     * */
    public void onRadioButtonClicked(View v) {
        RadioButton button = (RadioButton) v;
        boolean checked = button.isChecked();

        String label = null;

        switch (v.getId()) {
        case R.id.radioBarcodes:
            if (checked) {
                label = button.getText().toString();
                selectedInterface = InterfaceIdentifier.BARCODES;
            }
            break;
        case R.id.radioBluetooth:
            if (checked) {
                label = button.getText().toString();
                selectedInterface = InterfaceIdentifier.BLUETOOTH;
            }
            break;
        case R.id.radioIpBased:
            if (checked) {
                label = button.getText().toString();
                // This is not accurate, as this would imply that we only send via WIFI.
                // Instead if WIFI is not activated we try to send via mobile internet.
                selectedInterface = InterfaceIdentifier.WIFI;
            }
            break;
        case R.id.radioNfc:
            if (checked) {
                label = button.getText().toString();
                selectedInterface = InterfaceIdentifier.NFC;
            }
            break;
        case R.id.radioSms:
            if (checked) {
                label = button.getText().toString();
                selectedInterface = InterfaceIdentifier.SMS;
            }
            break;
        }

        if (selectedInterface != InterfaceIdentifier.BARCODES) {
            // getDaemon().waitForData(selectedInterface, this);
        }

        onInterfaceChanged(label);
    }

    /**
     * Message to notify the UI which interface is currently selected.
     * */
    public abstract void onInterfaceChanged(String selectedInterfaceName);

    /**
     * Initializes a list of all available interfaces.
     * */
    protected void initializeListOfAvailableInterfaces() {
        if (linearLayoutInterfaceSelection.getVisibility() == View.VISIBLE) {
            findViewById(R.id.linearLayoutInterfaceSelector).setVisibility(View.GONE);
        } else {
            findViewById(R.id.radioBarcodes).setEnabled(false);
            findViewById(R.id.radioBluetooth).setEnabled(false);
            findViewById(R.id.radioIpBased).setEnabled(false);
            findViewById(R.id.radioNfc).setEnabled(false);
            findViewById(R.id.radioSms).setEnabled(false);

            List<InterfaceIdentifier> enabledInterfaces = availChecker.getEnabledInterfaces(this);
            for (InterfaceIdentifier comInterface : enabledInterfaces) {
                switch (comInterface) {
                case ARBITRARY:
                    break;
                case BARCODES:
                    findViewById(R.id.radioBarcodes).setEnabled(true);
                    break;
                case BLUETOOTH:
                    findViewById(R.id.radioBluetooth).setEnabled(true);
                    break;
                case MOBILE_INTERNET:
                    findViewById(R.id.radioIpBased).setEnabled(true);
                    break;
                case NFC:
                    findViewById(R.id.radioNfc).setEnabled(true);
                    break;
                case SMS:
                    findViewById(R.id.radioSms).setEnabled(true);
                    break;
                case WIFI:
                    findViewById(R.id.radioIpBased).setEnabled(true);
                    break;
                default:
                    break;

                }
            }
        }
    }

    /**
     * This method has to be called in onCreate of the extending activity!!! It can not be called in this class
     * directly, as the views are only present after the layout (which is different for each Activity) was set.
     * Initializes the listView and the interfaceSelector.
     * */
    protected void initializeViews() {
        linearLayoutInterfaceSelection = (LinearLayout) findViewById(R.id.linearLayoutInterfaceSelector);

        messageArrayAdapter = new ArrayAdapter<String>(this, R.layout.row_chatmessage);
        ListView listViewMessages = (ListView) findViewById(R.id.listViewChat);
        listViewMessages.setAdapter(messageArrayAdapter);
    }

    private boolean isInterfaceReady(InterfaceIdentifier interfaceName) {
        if (availChecker.isInterfaceSupportedByDevice(this, interfaceName)) {
            return availChecker.isInterfaceEnabled(this, interfaceName);
        }
        return false;
    }

    @Override
    public void onDataReceived(InterfaceIdentifier interfaceName, BasicMessage message) {
        displayNewRemoteMessage("Received message");
    }

    @Override
    public void onDataSent(InterfaceIdentifier interfaceName, ProtocolMessage message) {
        if (message != null) {
            displaySentMessage("Sent message");
        } else {
            displayNewLogMessage("Could not send message via " + interfaceName + ". No connection established yet?");
        }
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

}
package de.lmu.mcm.activity;

import java.security.PublicKey;
import java.util.List;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import de.lmu.mcm.helper.ByteConverter;
import de.lmu.mcm.helper.InterfaceAvailabilityChecker;
import de.lmu.mcm.helper.LogHelper;
import de.lmu.mcm.helper.PrefsHelper;
import de.lmu.mcm.network.DaemonListener;
import de.lmu.mcm.network.Enums.InterfaceIdentifier;
import de.lmu.mcm.network.MultiNetworkAddress;
import de.lmu.mcm.network.ProtocolMessage;
import de.lmu.mcm.security.KeyHolder;
import de.lmu.mcm.security.byteproto.BasicMessage;
import de.lmu.mcm.security.byteproto.PublicKeyExchangeMessage;
import de.lmu.mm.R;

/**
 * Activity that is used to exchange the public keys of the users.
 * 
 * @author Thomas Bornschlegel
 * 
 */
public class ShareKeyActivity extends AbstractMultiChannelActivity implements DaemonListener {

    private KeyHolder keyHolder;
    private Button buttonContinue;
    private final static String TAG = "ShareKeys";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share_key);
        LogHelper.getInstance().i(TAG, "Starting ShareKeyActivity");
        keyHolder = KeyHolder.getInstance();
        buttonContinue = (Button) findViewById(R.id.buttonContinue);
        buttonContinue.setEnabled(false);
        initializeViews();
        getDaemon().checkInitialized(this);
        getDaemon().setListener(this);

        InterfaceAvailabilityChecker availChecker = new InterfaceAvailabilityChecker();
        List<InterfaceIdentifier> enabledInterfaces = availChecker.getEnabledInterfaces(this);
        for (InterfaceIdentifier comInterface : enabledInterfaces) {
            if (comInterface == InterfaceIdentifier.NFC) {
                findViewById(R.id.radioNfc).setEnabled(true);
            } else if (comInterface == InterfaceIdentifier.BARCODES) {
                findViewById(R.id.radioBarcodes).setEnabled(true);
            }
        }
    }

    public void onShareKeyButtonClicked(View v) {
        // if (isAbleToSend()) {
        // byte[] ownPublicKey = keyHolder.getSavedKeyPair(this).getPublic().getEncoded();
        // Toast.makeText(this, "Length: " + ownPublicKey.length, Toast.LENGTH_LONG).show();
        // daemon.sendData(ownPublicKey, InterfaceIdentifier.BARCODES, this);
        // }

        PrefsHelper.generateOwnIdIfNotPresent(this);
        UUID ownId = UUID.fromString(PrefsHelper.getOwnId(this));
        long telephoneNumber = 0;
        try {
            String telephoneNumberString = PrefsHelper.getOwnTelephoneNumber(this);
            if (telephoneNumberString != null) {
                LogHelper.getInstance().d(TAG, "Input for phone number conversation: " + telephoneNumberString);
                telephoneNumberString.substring(2, telephoneNumberString.length());
                telephoneNumber = Long.valueOf(telephoneNumberString);
                LogHelper.getInstance().d(
                        TAG,
                        "Normalized telephone number as long: " + telephoneNumber + " (before: "
                                + telephoneNumberString);
            } else {
                LogHelper.getInstance().e(TAG, "Error initializing phone number, sending no number");
            }
        } catch (Exception e) {
            LogHelper.getInstance().e(TAG, "Error initializing phone number, sending no number");
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        byte[] bluetoothAddress = null;
        if (adapter != null) {
            String bluetoothAddressString = adapter.getAddress();
            LogHelper.getInstance().d(TAG, "Found bluetooth address: " + bluetoothAddressString);
            bluetoothAddressString = bluetoothAddressString.replace(":", "");
            bluetoothAddress = ByteConverter.hexStringToByteArray(bluetoothAddressString);
        }

        keyHolder.makeSureOwnKeyPairIsAvailable(this);
        PublicKey publicKey = KeyHolder.getInstance().getSavedKeyPair(this).getPublic();

        PublicKeyExchangeMessage message = new PublicKeyExchangeMessage(ownId, telephoneNumber, bluetoothAddress,
                publicKey);

        try {
            sendDataWithSelectedInterface(message, (byte) 0);
        } catch (Exception e) {
            displayNewLogMessage("Error while trying to send public key");
            e.printStackTrace();
        }
    }

    @Override
    public void onDataReceived(InterfaceIdentifier interfaceName, BasicMessage receivedMsg) {
        displayNewLogMessage("Received new message...");
        try {
            if (receivedMsg instanceof PublicKeyExchangeMessage) {
                displayNewLogMessage("Message is a well formed public key exchange message:");
                PublicKeyExchangeMessage pubKeyMsg = (PublicKeyExchangeMessage) receivedMsg;
                UUID remoteUUID = pubKeyMsg.getUuidOfKeyOwner();
                PublicKey key = pubKeyMsg.getAsymmetricKey();
                KeyHolder.getInstance().storePublicKeyOfOtherUser(this, remoteUUID, key);
                displayNewLogMessage("Users UUID = " + remoteUUID);
                byte[] bluetoothAddress = pubKeyMsg.getBluetoothDeviceAddress();
                LogHelper.getInstance().d(TAG, "Found phone number: " + pubKeyMsg.getTelephoneNumber());
                if (pubKeyMsg.getTelephoneNumber() != 0) {
                    String mobile = "00" + pubKeyMsg.getTelephoneNumber();
                    PrefsHelper.storeMobileNumberToUserIdMapping(this, mobile, remoteUUID);
                    displayNewLogMessage("Mobile number = " + mobile);
                }
                PrefsHelper.storeIdOfCommunicationPartner(remoteUUID, this);
                if (bluetoothAddress != null) {
                    PrefsHelper.storeBluetoothAddressForUser(this, bluetoothAddress, remoteUUID);
                    try {
                        String str = MultiNetworkAddress.getBluetoothAddressFromString(bluetoothAddress);
                        displayNewLogMessage("Bluetooth address = " + str);
                    } catch (Exception e) {
                    }
                }
                buttonContinue.setEnabled(true);
            } else {
                displayNewLogMessage("Message was not well formed");
            }
        } catch (Exception e) {
            displayNewLogMessage("Error while parsing received message");
            e.printStackTrace();
        }
    }

    @Override
    public void onDataSent(InterfaceIdentifier interfaceName, ProtocolMessage message) {
        displayNewLogMessage("Data sent successfully via " + interfaceName);
    }

    public void onReceiveButtonClicked(View v) {
        if (isInterfaceSetUp()) {
            getDaemon().waitForData(selectedInterface, this);
        }
    }

    public void onButtonContinueClicked(View v) {
        if (buttonContinue.isEnabled()) {
            // HandshakeActivity.class;
            startNewActivity(HandshakeActivity.class);
        }
    }

    @Override
    public void onInterfaceChanged(String selectedInterfaceName) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onInterfaceConnectionClosed(InterfaceIdentifier interfaceName) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onInterfaceDestroyed(InterfaceIdentifier interfaceName) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onConnectionSetupFailed(InterfaceIdentifier interfaceName, MultiNetworkAddress address) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onConnectionIsSetUp(InterfaceIdentifier interfaceName, MultiNetworkAddress address) {
        // TODO Auto-generated method stub

    }

}

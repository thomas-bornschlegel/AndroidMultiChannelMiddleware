package de.lmu.mcm.network.nfc;

import java.nio.ByteBuffer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.CreateNdefMessageCallback;
import android.nfc.NfcAdapter.OnNdefPushCompleteCallback;
import android.nfc.NfcEvent;
import android.nfc.tech.Ndef;
import android.os.Parcelable;
import android.os.PatternMatcher;
import de.lmu.mcm.helper.LogHelper;
import de.lmu.mcm.network.AbstractCommunicationModule;
import de.lmu.mcm.network.Enums;
import de.lmu.mcm.network.Enums.MessageOrigin;
import de.lmu.mcm.network.NetworkDaemon;
import de.lmu.mcm.network.ProtocolMessage;
import de.lmu.mcm.network.ServiceDescription;

/**
 * The CommunicationInterface for NFC.
 * 
 * */
public class NfcCommunicator extends AbstractCommunicationModule implements CreateNdefMessageCallback,
        OnNdefPushCompleteCallback {
    /*
     * Helpful resources which are used in parts in this class:
     * 
     * Googles NFC Example: https://android.googlesource.com/platform/development/+/master/samples/ApiDemos/src
     * /com/example/android/apis/nfc/ForegroundDispatch.java
     * 
     * and
     * 
     * http://developer.android.com/guide/topics/connectivity/nfc/nfc.html
     * 
     * and
     * 
     * https://code.google.com/p/ndef-tools-for-android/source/browse/#git%2Fndeftools-boilerplate%2Fsrc%2Forg
     * %2Fndeftools%2Fboilerplate
     */
    private static final String TAG = "NFC";

    private NfcAdapter adapter;
    private PendingIntent mPendingIntent;
    private IntentFilter[] mFilters;
    private String[][] mTechLists;
    private boolean isReadingData = false;
    private boolean isSendingData = false;
    private byte[] dataToSend = null;
    private ServiceDescription serviceDescription;
    // Caches the current activity. As we unregister this variable in onPause and reregister it in onResume it should
    // always contain a fresh constant of the current activity. This also guarantees that this variable is set to null
    // if the activity looses focus.
    private Activity activity;

    // String that define the custom external type:
    private final String extRecordDomain = "de.lmu";// "de.lmu";
    private final String extRecordType = "mcm";// "lmu-mw";

    @Override
    public Enums.InterfaceIdentifier getInterfaceName() {
        return Enums.InterfaceIdentifier.NFC;
    }

    @SuppressLint("NewApi")
    public NfcCommunicator(Activity activity, NetworkDaemon daemon) {
        super(activity, daemon);

        IntentFilter filter = new IntentFilter();
        filter.addAction(NfcAdapter.ACTION_TAG_DISCOVERED);
        filter.addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
        filter.addAction(NfcAdapter.ACTION_TECH_DISCOVERED);
        // Filter for the custom external type as specified in
        // http://developer.android.com/guide/topics/connectivity/nfc/nfc.html#ext-type
        // (This does not seem work as it does not exclude NDEF messages with a different type)
        filter.addDataScheme("vnd.android.nfc");
        filter.addDataAuthority("ext", null);
        filter.addDataPath(extRecordDomain + ":" + extRecordType, PatternMatcher.PATTERN_PREFIX);
        mFilters = new IntentFilter[] { filter };

        // Only consider NDEF-Tags
        mTechLists = new String[][] { new String[] { Ndef.class.getName() } };

        setupActivityBasedVariables(activity);
    }

    /**
     * Sets up variables that require an activity object.
     */
    @SuppressLint("NewApi")
    private void setupActivityBasedVariables(Activity activity) {
        adapter = NfcAdapter.getDefaultAdapter(activity);

        // Create a generic PendingIntent that will be deliver to this activity. The NFC stack
        // will fill in the intent with the details of the discovered tag before delivering to
        // this activity.
        mPendingIntent = PendingIntent.getActivity(activity, 0,
                new Intent(activity, activity.getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

        int apiVersion = android.os.Build.VERSION.SDK_INT;
        if (apiVersion >= 14) {
            // Without this call the app sends messages even though nothing was typed in the chat field.
            // The content of the message is "play.google.com/store/apps/details?id=de.lmu.mm&featrue=beam".
            // It opens the play store and searches for the app
            // By disabling pushing in the beginning we surpress this call:
            adapter.setNdefPushMessageCallback(null, activity);
            adapter.setOnNdefPushCompleteCallback(null, activity);
        } else {
            LogHelper.getInstance().e(
                    TAG,
                    "Cannot setup NdefPushMessageCallback because the api of your device " + apiVersion
                            + " is smaller 14");
        }
    }

    @Override
    public void setupConnection(Activity activity, ServiceDescription serviceDescription) {
        this.serviceDescription = serviceDescription;
        notifyDaemonConnectionIsSetUp(serviceDescription.getAddressOfServer());
    }

    @Override
    public boolean listenForMessages(Activity activity) {
        startReading(activity);
        return true;
    }

    @Override
    public boolean isReadyToExchangeData() {
        return adapter != null;
    }

    @Override
    public void stopCurrentConnection(Activity activity) {
        if (activity != null && !activity.isFinishing()) {
            stopReading(activity);
            stopSendingData(activity);
        }
    }

    @Override
    public void onResume(Activity activity) {
        this.activity = activity;
        setupActivityBasedVariables(activity);
        startReading(activity);

        // Process read tag
        Intent data = activity.getIntent();
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(data.getAction())
                || NfcAdapter.ACTION_TAG_DISCOVERED.equals(data.getAction())
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(data.getAction())) {
            LogHelper.getInstance().d(TAG, "Processing NFC Intent");
            processNfcIntent(activity, data, data.getAction());
        }
    }

    @Override
    public void onNewIntent(Activity activity, Intent intent) {
        LogHelper.getInstance().i(TAG, "Discovered tag with intent: " + intent);
        activity.setIntent(intent);
    }

    @Override
    public void onPause(Activity activity) {
        // Pause reading:
        stopReading(activity);
        this.activity = null;
    }

    @Override
    public void destroy(Activity activity) {
        stopSendingData(activity);
        // Stop reading is called in onPause already.
        // But we leave it here to make sure that it is definitely called:
        stopReading(activity);
    }

    // -------------------------------------------------
    // START read data
    // -------------------------------------------------

    /**
     * Puts the given activity in foreground dispatch mode so that it can read NFC events.
     * */
    @SuppressLint("NewApi")
    private void startReading(Activity activity) {
        if (isReadingData) {
            LogHelper.getInstance().e(TAG, "Already reading!");
        } else {
            adapter = NfcAdapter.getDefaultAdapter(activity);
            LogHelper.getInstance().d(TAG, "Started reading");
            adapter.enableForegroundDispatch(activity, mPendingIntent, mFilters, mTechLists);
            isReadingData = true;
        }
    }

    /**
     * Stops foreground dispatch mode for the given activity, so that it stops to read NFC events.
     * */
    private void stopReading(Activity activity) {
        if (isReadingData) {
            adapter = NfcAdapter.getDefaultAdapter(activity);
            LogHelper.getInstance().d(TAG, "Stopped reading");
            adapter.disableForegroundDispatch(activity);
            isReadingData = false;
        } else {
            LogHelper.getInstance().e(TAG, "Stopped reading was already called");
        }
    }

    /**
     * Processes an intent that contains NFC data. Notifies the daemon about the received data.
     * */
    public void processNfcIntent(Activity activity, Intent intent, String action) {

        // This method is based on
        // https://code.google.com/p/ndef-tools-for-android/source/browse/ndeftools-util/src/org/ndeftools/util/activity/NfcReaderActivity.java

        LogHelper.getInstance().d(TAG, "nfcIntentDetected: " + action);

        Parcelable[] messages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        if (messages != null) {
            NdefMessage[] ndefMessages = new NdefMessage[messages.length];
            for (int i = 0; i < messages.length; i++) {
                ndefMessages[i] = (NdefMessage) messages[i];
            }

            if (ndefMessages.length > 0) {

                LogHelper.getInstance().d(TAG, "Starting to read NDEF");
                ByteBuffer b = null;
                for (int i = 0; i < messages.length; i++) {
                    NdefMessage ndefMessage = (NdefMessage) messages[i];

                    for (NdefRecord ndefRecord : ndefMessage.getRecords()) {
                        byte[] payload = ndefRecord.getPayload();
                        try {
                            if (b == null) {
                                b = ByteBuffer.wrap(payload);
                                int lengthIndicator = b.getInt();
                                LogHelper.getInstance().d(TAG, "NDEF contains " + lengthIndicator + " bytes");
                                // We add 4+1 bytes because the length indicator consumes 4 bytes and the message code 1
                                // byte:
                                lengthIndicator += 4 + 1;

                                b = ByteBuffer.allocate(lengthIndicator);
                            }
                            b.put(payload);
                        } catch (Exception e) {
                            LogHelper.getInstance().e(TAG, "Error while trying to parse NDEF", e);
                        }

                    }
                }
                if (b != null) {
                    LogHelper.getInstance().d(TAG, "Finished reading NDEF");
                    ProtocolMessage protoMsg = new ProtocolMessage(MessageOrigin.REMOTE, b.array());
                    notifyDaemonAboutReceivedData(protoMsg);
                } else {
                    LogHelper.getInstance().e(TAG, "Could not read NDEF message");
                }
            }
        }

        // intent.removeExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        // activity.setIntent(intent);
    }

    // -------------------------------------------------
    // END read data
    // -------------------------------------------------

    // -------------------------------------------------
    // START send data
    // -------------------------------------------------

    /**
     * Caches the data which will be sent when a device comes in range. Also sets up the NdefPushMessageCallback that
     * initiates the sending of NFC data when two devices are held together.
     * */
    @SuppressLint("NewApi")
    @Override
    public boolean sendData(Activity activity, byte[] data) {

        int apiVersion = android.os.Build.VERSION.SDK_INT;
        if (apiVersion >= 14) {
            this.dataToSend = data;

            NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(activity);
            if (!isSendingData && nfcAdapter != null) {
                // Define message to be sent:
                nfcAdapter.setNdefPushMessageCallback(this, activity);

                // Wait for callback after sent completed:
                nfcAdapter.setOnNdefPushCompleteCallback(this, activity);

                isSendingData = true;
            }
            this.activity = activity;
            LogHelper.getInstance().d(TAG, "Setup writing.");
        } else {
            LogHelper.getInstance().e(TAG,
                    "No effect of sendData because of low api (" + apiVersion + "). API >= 14 required.");
        }

        return true;
    }

    /**
     * Creates the NdefMessage that consists of an external NDEF record with the data to send. This method is called by
     * the system when a device comes in range. If data was set up earlier with {@link #sendData(Activity, byte[])} this
     * data is sent.
     * */
    @SuppressLint("NewApi")
    public NdefMessage createNdefMessage(NfcEvent event) {
        if (dataToSend != null) {
            LogHelper.getInstance().d(TAG, "Creating NDEF message...");

            int apiVersion = android.os.Build.VERSION.SDK_INT;
            NdefRecord externalRecord = null;
            if (android.os.Build.VERSION.SDK_INT >= 16) {
                LogHelper.getInstance().d(TAG, "Creating external record (new version with API " + apiVersion + ")");
                externalRecord = NdefRecord.createExternal(extRecordDomain, extRecordType, dataToSend);
            } else {
                LogHelper.getInstance().d(TAG, "Creating external record (old version with API " + apiVersion + ")");
                String domainAndType = extRecordDomain + ":" + extRecordType;
                externalRecord = new NdefRecord(NdefRecord.TNF_EXTERNAL_TYPE, domainAndType.getBytes(), new byte[0],
                        dataToSend);
            }

            NdefMessage extMsg = new NdefMessage(new NdefRecord[] { externalRecord });

            return extMsg;
        } else {
            LogHelper.getInstance().e(TAG, "No NFC message provided");
            // TODO Do not cache activity, find better solution
            stopSendingData(activity);
            return null;
        }
    }

    /**
     * Called when the data was sent successfully. This call will erase the cached data to send. As we already sent it,
     * we do not have to send it again.
     * */
    @Override
    public void onNdefPushComplete(NfcEvent arg0) {
        LogHelper.getInstance().d(TAG, "Finished writing NDEF message");
        if (activity != null) {
            ProtocolMessage protoMessage = new ProtocolMessage(MessageOrigin.SELF, dataToSend);
            notifyDaemonAboutSentData(protoMessage, true);
            stopSendingData(activity);
        } else {
            LogHelper.getInstance().e(TAG, "Could not stopSending because activity was null");
        }
    }

    /**
     * Stops sending of the data and removes the cached data that was set in {@link #sendData(Activity, byte[])}.
     * */
    @SuppressLint("NewApi")
    protected void stopSendingData(Activity activity) {
        int apiVersion = android.os.Build.VERSION.SDK_INT;
        if (android.os.Build.VERSION.SDK_INT >= 14) {
            if (activity != null) {
                adapter = NfcAdapter.getDefaultAdapter(activity);
                if (isSendingData && adapter != null && activity != null && !activity.isDestroyed()) {
                    adapter.setNdefPushMessageCallback(null, activity);
                    adapter.setOnNdefPushCompleteCallback(null, activity);
                    isSendingData = false;
                }
                dataToSend = null;
                LogHelper.getInstance().d(TAG, "Stopping to write.");
            }
        } else {
            LogHelper.getInstance().e(TAG,
                    "No effect of stopSendData because of low api (" + apiVersion + "). API >= 14 required.");
        }
    }

    // -------------------------------------------------
    // END send data
    // -------------------------------------------------

    @Override
    public boolean onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        return false;
    }

}

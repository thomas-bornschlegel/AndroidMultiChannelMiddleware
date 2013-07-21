package de.lmu.mcm.network.barcode;

import android.app.Activity;
import android.content.Intent;

import com.google.zxing.client.android.integration.IntentIntegrator;
import com.google.zxing.client.android.integration.IntentResult;

import de.lmu.mcm.helper.ByteConverter;
import de.lmu.mcm.helper.LogHelper;
import de.lmu.mcm.network.AbstractCommunicationModule;
import de.lmu.mcm.network.Enums;
import de.lmu.mcm.network.NetworkDaemon;
import de.lmu.mcm.network.ProtocolMessage;
import de.lmu.mcm.network.ServiceDescription;
import de.lmu.mcm.network.Enums.MessageOrigin;

/**
 * Communication interface to send and receive barcodes.
 * 
 * @author Thomas Bornschlegel
 * 
 */
public class BarcodeCommunicator extends AbstractCommunicationModule {

    private ServiceDescription service;
    private final static String EXTRA_BARCODE_HANDLED = "barcode was handled before";

    public BarcodeCommunicator(Activity activity, NetworkDaemon daemon) {
        super(activity, daemon);
    }

    @Override
    public Enums.InterfaceIdentifier getInterfaceName() {
        return Enums.InterfaceIdentifier.BARCODES;
    }

    @Override
    public void destroy(Activity activity) {
        // Nothing to destroy
    }

    @Override
    public boolean isReadyToExchangeData() {
        return service != null;
    }

    @Override
    public boolean sendData(Activity activity, byte[] data) {
        // At the moment we send data encoded in base64. To send the bytes directly we would have to do something
        // like this:
        // https://groups.google.com/forum/?fromgroups=#!topic/zxing/Tb2GtUdUph4
        String dataAsBase64 = ByteConverter.encodeAsBase64String(data);
        shareBarcode(activity, dataAsBase64);
        ProtocolMessage protocolMessage = new ProtocolMessage(MessageOrigin.SELF, data);
        notifyDaemonAboutSentData(protocolMessage, true);
        return true;
    }

    @Override
    public void setupConnection(Activity activity, ServiceDescription serviceDescription) {
        this.service = serviceDescription;
        notifyDaemonConnectionIsSetUp(serviceDescription.getAddressOfServer());
    }

    @Override
    public boolean onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        if (requestCode == IntentIntegrator.REQUEST_CODE_SCAN_CODE && data != null) {
            LogHelper.getInstance().d(TAG, "Parsing barcode...");
            boolean barcodeHandled = data.getBooleanExtra(EXTRA_BARCODE_HANDLED, false);
            if (barcodeHandled) {
                LogHelper.getInstance().d(TAG, "Barcode was parsed before => do nothing");
                return false;
            } else {
                LogHelper.getInstance().d(TAG, "Barcode is new");
            }

            IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
            if (scanResult != null) {
                // handle scan result
                String result = scanResult.getContents();
                // byte[] resultInBytes = result.getBytes();
                byte[] resultInBytes = ByteConverter.decodeBase64String(result);
                // Check valid format?
                // String format = scanResult.getFormatName();
                // BarcodeFormat validFormats;

                // Add extra to prevent barcode from being parsed again.
                // (Without this, each barcode is parsed two times)
                data.putExtra(EXTRA_BARCODE_HANDLED, true);
                activity.setIntent(data);

                if (service != null) {
                    ProtocolMessage protocolMessage = new ProtocolMessage(MessageOrigin.REMOTE, resultInBytes);
                    notifyDaemonAboutReceivedData(protocolMessage);
                } else {
                    notifyDaemonAboutReceivedData(new ProtocolMessage(MessageOrigin.REMOTE, resultInBytes));
                }
            }
            return true;
            // } else if (requestCode == IntentIntegrator.REQUEST_CODE_SHARE_TEXT) {
            // LogHelper.getInstance().d(TAG, "Parsing sent barcode");
            // String msg = data.getStringExtra("ENCODE_DATA");
            // byte[] resultInBytes = ByteConverter.decodeBase64(msg.getBytes());
            // if (msg == null) {
            // msg = "ERROR RETRIEVING SENT MESSAGE";
            // } else {
            // ProtocolMessage protocolMessage = new ProtocolMessage(MessageOrigin.SELF, service.getAddressOfServer(),
            // resultInBytes);
            // notifyDaemonAboutSentData(protocolMessage, true);
            // return true;
            // }
        }
        return false;
    }

    @Override
    public boolean listenForMessages(Activity activity) {
        initiateBarcodeScan(activity);
        return true;
    }

    /**
     * Shares the given text with a barcode.
     * */
    private void shareBarcode(Activity activity, String text) {
        IntentIntegrator zxingIntegrator = new IntentIntegrator(activity);
        zxingIntegrator.shareText(text);
    }

    /**
     * Initiates a barcode scan.
     * */
    private void initiateBarcodeScan(Activity activity) {
        IntentIntegrator zxingIntegrator = new IntentIntegrator(activity);
        zxingIntegrator.initiateScan();
    }

    @Override
    public void stopCurrentConnection(Activity activity) {
        this.service = null;
        notifyDaemonConnectionTerminated();
    }

    @Override
    public void onResume(Activity activity) {
        // Nothing to do here
    }

    @Override
    public void onNewIntent(Activity activity, Intent data) {
        // Nothing to do here
    }

    @Override
    public void onPause(Activity activity) {
        // Nothing to do here
    }

}

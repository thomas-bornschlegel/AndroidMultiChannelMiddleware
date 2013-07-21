package de.lmu.mcm.activity;

import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import de.lmu.mcm.helper.InterfaceAvailabilityChecker;
import de.lmu.mcm.helper.LogHelper;
import de.lmu.mcm.helper.PrefsHelper;
import de.lmu.mcm.helper.InterfaceAvailabilityChecker.OnInterfacesActivatedListener;
import de.lmu.mcm.helper.LogHelper.LogListener;
import de.lmu.mcm.network.Enums.InterfaceIdentifier;
import de.lmu.mcm.security.KeyHolder;
import de.lmu.mm.R;

/**
 * Class that checks if all available interfaces are enabled.
 * 
 * @author Thomas Bornschlegel
 * 
 */
public class InterfaceActivatorActivity extends Activity implements LogListener, OnInterfacesActivatedListener {

    private final String TAG = "InterfaceActivatorActivity ";
    private TextView textViewLog;
    private InterfaceAvailabilityChecker availChecker;
    private Button button;
    private int handlerCodeActivateButton = 4343;
    private int handlerCodeInterfacesActivated = 7832;

    private Handler handler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == handlerCodeInterfacesActivated) {
                Toast.makeText(InterfaceActivatorActivity.this, "Done checking interfaces!", Toast.LENGTH_SHORT).show();
            } else if (msg.what == handlerCodeActivateButton) {
                button.setEnabled(true);
            } else {
                String str = msg.obj.toString();
                updateTextView(str);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_interface_enabler);
        LogHelper.getInstance().i(TAG, "Starting InterfaceActivatorActivity");
        button = (Button) findViewById(R.id.buttonLaunchMain);
        handler.sendEmptyMessageDelayed(handlerCodeActivateButton, 10000);
        textViewLog = (TextView) findViewById(R.id.textViewLog);
        LogHelper.getInstance().setLogListener(this);
        boolean publicKeyInitialized = KeyHolder.getInstance().makeSureOwnKeyPairIsAvailable(this);
        PrefsHelper.generateOwnIdIfNotPresent(this);
        if (!publicKeyInitialized) {
            Toast.makeText(this, R.string.error_initializing_rsa, Toast.LENGTH_LONG).show();
            finish();
        }

        Toast.makeText(this, "Checking if interfaces are enabled...", Toast.LENGTH_SHORT).show();
        availChecker = new InterfaceAvailabilityChecker();
        availChecker.setOnInterfacesActivatedListener(InterfaceActivatorActivity.this);
        List<InterfaceIdentifier> supportedInterfaces = availChecker
                .getSupportedInterfaces(InterfaceActivatorActivity.this);
        availChecker.activateInterfaces(InterfaceActivatorActivity.this, supportedInterfaces);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (availChecker.onActivityResult(this, requestCode, resultCode, data)) {
            // nothing to do here, the request was handled in availChecker
        }
    }

    @Override
    public void onNewLogMessage(String logTag, String msg) {
        String newMessage = logTag + ": " + msg;

        Message m = new Message();
        m.obj = newMessage;
        handler.sendMessage(m);

    }

    private void updateTextView(String newMessage) {
        if (textViewLog != null) {
            String txt = newMessage + "\n" + textViewLog.getText().toString();
            int maxChars = 5000;
            if (txt.length() > maxChars) {
                txt = txt.substring(0, maxChars);
            }
            textViewLog.setText(txt);
        }
    }

    @Override
    public void onInterfacesActivated() {
        LogHelper.getInstance().d(TAG, "Done checking interfaces!");
        handler.sendEmptyMessage(handlerCodeInterfacesActivated);
        handler.sendEmptyMessage(handlerCodeActivateButton);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (availChecker != null) {
            availChecker.setOnInterfacesActivatedListener(null);
            availChecker = null;
        }
    }

    public void onButtonLaunchMainClick(View v) {
        Button button = (Button) findViewById(R.id.buttonLaunchMain);
        if (button != null && button.isEnabled()) {
            LogHelper.getInstance().setLogListener(null);
            Intent i = new Intent(this, ShareKeyActivity.class);

            startActivity(i);
            finish();
        }
    }

}

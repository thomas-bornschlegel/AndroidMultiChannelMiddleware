package de.lmu.mcm.activity;

import android.app.Application;
import de.lmu.mcm.network.NetworkDaemon;
import de.lmu.mcm.security.KeyHolder;

/**
 * 
 * App object of this project. This object is created only once per app session and stores singletons of the
 * NetworkDaemon and the KeyHolder.
 * 
 * @author Thomas Bornschlegel
 * 
 */
public class App extends Application {

    // Keep singleton instance of daemon in the Application object, so that it will not be erased by garbage collection.
    private NetworkDaemon daemon;
    // Also keep the keyHolder, which can be accessed by calling getInstance
    private KeyHolder keyHolder = KeyHolder.getInstance();

    /**
     * @return a singleton instance of the NetworkDaemon. Use this getter in your activities to receive always the same
     *         daemon.
     * */
    public NetworkDaemon getNetworkDaemon() {
        if (daemon == null) {
            daemon = new NetworkDaemon(getApplicationContext());
        }
        return daemon;
    }
}
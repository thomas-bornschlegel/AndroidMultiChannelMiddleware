package de.lmu.mcm.helper;

import java.util.ArrayList;
import java.util.List;

import android.util.Log;

/**
 * 
 * Provides methods to control the logging. Also contains constants that enable global disabeling/enabling of the
 * logging.
 * 
 * @author Thomas Bornschlegel
 * 
 */
public class LogHelper {

    private final String LOG_PREFIX = "MobileMiddleware ";
    // Set this to false to disable logging
    private boolean loggingEnabled = true;
    private static LogHelper logHelperInstance = null;
    private LogListener logListener = null;
    private List<TimeStampWithIdentifier> timeStamps = new ArrayList<TimeStampWithIdentifier>();

    private LogHelper() {
    }

    public interface LogListener {
        public void onNewLogMessage(String logTag, String msg);
    }

    public static LogHelper getInstance() {
        if (logHelperInstance == null) {
            logHelperInstance = new LogHelper();
        }
        return logHelperInstance;
    }

    public void setLogListener(LogListener listener) {
        this.logListener = listener;
    }

    public void d(String logTag, String msg) {
        if (loggingEnabled) {
            Log.d(LOG_PREFIX + logTag, msg);
            sendMessageToListener(logTag, msg);
        }
    }

    public void v(String logTag, String msg) {
        if (loggingEnabled) {
            Log.v(LOG_PREFIX + logTag, msg);
            sendMessageToListener(logTag, msg);
        }
    }

    public void e(String logTag, String msg) {
        if (loggingEnabled) {
            Log.e(LOG_PREFIX + logTag, msg);
            sendMessageToListener(logTag, msg);
        }
    }

    public void e(String logTag, String msg, Throwable error) {
        if (loggingEnabled) {
            Log.e(LOG_PREFIX + logTag, msg, error);
            sendMessageToListener(logTag, msg);
        }
    }

    public void i(String logTag, String msg) {
        if (loggingEnabled) {
            Log.i(LOG_PREFIX + logTag, msg);
            sendMessageToListener(logTag, msg);
        }
    }

    private void sendMessageToListener(String logTag, String msg) {
        if (logListener != null) {
            logListener.onNewLogMessage(logTag, msg);
        }
    }

    /**
     * Starts to measure the time between two calls. Use the same identifier with
     * {@link #stopToMeasureTime(String, int)} to stop measuring time.
     * 
     * @param logTag
     *            the tag to use for the Log
     * @param identifier
     *            the identifier for this measurement.
     * 
     * */
    public synchronized void startToMeasureTime(String logTag, int identifier) {
        // Do not allow duplicates. Remove existing timestamp if it already exists.
        TimeStampWithIdentifier existingTimeStamp = findTimeStamp(identifier);
        if (existingTimeStamp != null) {
            timeStamps.remove(existingTimeStamp);
        }

        d(logTag, "Starting to measure time (ID=" + identifier + ")");
        TimeStampWithIdentifier tsWithId = new TimeStampWithIdentifier();
        tsWithId.identifier = identifier;
        tsWithId.timeStamp = System.currentTimeMillis();
        timeStamps.add(tsWithId);
    }

    /**
     * Stops to measure the time between two calls. Use the same identifier with
     * {@link #startToMeasureTime(String, int)} to stop measuring time.
     * 
     * @param logTag
     *            the tag to use for the Log
     * @param identifier
     *            the identifier for this measurement.
     * 
     * */
    public synchronized void stopToMeasureTime(String logTag, int identifier) {
        long time = System.currentTimeMillis();
        TimeStampWithIdentifier matchingTimeStamp = findTimeStamp(identifier);
        if (matchingTimeStamp != null) {
            time -= matchingTimeStamp.timeStamp;
            d(logTag, "Measured " + time + " milliseconds (ID=" + identifier + ") ");
            timeStamps.remove(matchingTimeStamp);
        }

    }

    /**
     * Finds a timestamp for the given identifier
     * 
     * @param identifier
     *            the according identifier
     * @return the start time for the given identifier
     * */
    private synchronized TimeStampWithIdentifier findTimeStamp(int identifier) {
        TimeStampWithIdentifier matchingTimeStamp = null;
        for (TimeStampWithIdentifier tsWithId : timeStamps) {
            if (tsWithId.identifier == identifier) {
                matchingTimeStamp = tsWithId;
                break;
            }
        }
        return matchingTimeStamp;
    }

    private class TimeStampWithIdentifier {
        public int identifier;
        public long timeStamp;
    }

}

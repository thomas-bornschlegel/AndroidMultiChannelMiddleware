package de.lmu.mcm.helper;

/**
 * This class is a convenience method that helps to create Threads that can be easily canceled.
 * 
 * @author Thomas Bornschlegel
 * 
 */
public abstract class CancelableThread extends Thread {

    /**
     * If possible cancel any running tasks here (e.g. by setting a boolean which exits a while clause). If this is not
     * possible just call {@link #hardCancel()}.
     * */
    public abstract void cancel();

    /**
     * Cancels the thread by interrupting it. Only use this method if {@link #cancel()} has no effect.
     * */
    public void hardCancel() {
        if (isAlive()) {
            try {
                interrupt();
            } catch (Exception e) {
                LogHelper.getInstance().e("CancelableThread", "Could not interrupt thread.", e);
            }
        }
    }
}
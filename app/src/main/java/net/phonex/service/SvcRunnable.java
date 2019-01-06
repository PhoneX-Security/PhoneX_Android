package net.phonex.service;

import net.phonex.util.Log;

/**
 * Abstract runnable task for executor.
 *
 * @author ph4r05
 */
public abstract class SvcRunnable implements Runnable {
    private static final String TAG = "SvcTask";
    protected final String name;

    public SvcRunnable(String name) {
        this.name = name;
    }

    public SvcRunnable() {
        this.name = "base";
    }

    protected abstract void doRun() throws XService.SameThreadException;

    public void run() {
        try {
            doRun();
        } catch (XService.SameThreadException e) {
            Log.e(TAG, "Not done from same thread");
        }
    }

    @Override
    public String toString() {
        return "S[" + name + " | " + super.toString() + "]";
    }

    public String getName() {
        return name;
    }
}

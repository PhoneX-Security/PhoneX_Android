package net.phonex.service;

import net.phonex.util.Log;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Abstract runnable task for executor with return value.
 */
public abstract class SvcReturnRunnable extends SvcRunnable {
    private static final String TAG = "SvcReturnTask";
    private Semaphore runSemaphore;
    private Object resultObject;
    private boolean waitInfinitely = false;

    public SvcReturnRunnable() {
        super("Ret");
        runSemaphore = new Semaphore(0);
    }

    public SvcReturnRunnable(String name) {
        super(name);
        runSemaphore = new Semaphore(0);
    }

    public Object getResult() {
        try {
            boolean acquired = false;
            int counter = 3;
            while (counter > 0) {
                acquired = runSemaphore.tryAcquire(10, TimeUnit.SECONDS);
                if (acquired) {
                    break;
                }

                counter -= waitInfinitely ? 0 : 1;
                Log.wf(TAG, "Cannot acquire semaphore for task=[%s] counter=%s", toString(), counter);
            }

            // If it was not possible to get the semaphore.
            if (!acquired) {
                Log.ef(TAG, "Probable deadlock detected, task=%s", toString());
                throw new RuntimeException("Probable deadlock detected, task=" + toString());
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Semaphore waiting interrupted", e);
        }
        return resultObject;
    }

    private void setResult(Object obj) {
        resultObject = obj;
        runSemaphore.release();
    }

    protected abstract Object runWithReturn() throws XService.SameThreadException;

    @Override
    public void doRun() throws XService.SameThreadException {
        Object returnObj = null;
        try {
            returnObj = runWithReturn();
        } catch(Exception ex){
            Log.e(TAG, "Exception when executing async task", ex);
        } finally {
            setResult(returnObj);
        }
    }

    public boolean isWaitInfinitely() {
        return waitInfinitely;
    }

    public SvcReturnRunnable setWaitInfinitely(boolean waitInfinitely) {
        this.waitInfinitely = waitInfinitely;
        return this;
    }
}

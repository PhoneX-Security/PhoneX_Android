package net.phonex.service.runEngine;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import net.phonex.util.Log;

/**
 * Handler is processing a timer task in the executorThread.
 * @author ph4r05
 */
public class AndroidTimerJobHandler extends Handler {
    private static final String TAG = "AndroidTimerJobHandler";

    AndroidTimerJobHandler(Looper looper) {
        super(looper);
    }

    public void execute(Runnable task) {
        Message.obtain(this, 0, task).sendToTarget();
    }

    @Override
    public void handleMessage(Message msg) {
        if (msg.obj instanceof Runnable) {
            final Runnable task = (Runnable) msg.obj;

            try {
                task.run();
            } catch (Throwable t) {
                Log.ef(TAG, t, "exception in timer: %s", task);
            }
        } else {
            Log.wf(TAG, "Unknown message: %s", msg);
        }
    }
}

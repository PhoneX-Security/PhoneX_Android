package net.phonex.service;

import android.os.Debug;
import android.os.Handler;
import android.os.Message;

import net.phonex.BuildConfig;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;

import java.lang.ref.WeakReference;

/**
 * Executes immediate tasks in a single executorThread.
 * Hold/release wake lock for running tasks
 * Created by ph4r05 on 7/28/14.
 */
public class XServiceHandler extends Handler {
    private static final String TAG="SvcExecuor";
    WeakReference<XService> handlerService;

    XServiceHandler(XService s) {
        super(XService.createLooper());
        handlerService = new WeakReference<XService>(s);
    }

    public void execute(Runnable task) {
        XService s = handlerService.get();
        if(s != null) {
            s.getWakeLock().lock(task);
        }
        Log.vf(TAG, "Schedule @ executor: %s", task);
        Message.obtain(this, 0, task).sendToTarget();
    }

    @Override
    public void handleMessage(Message msg) {
        if (msg.obj instanceof Runnable) {
            Log.vf(TAG, "<executor: %s>", msg.obj);
            executeInternal((Runnable) msg.obj);
            Log.vf(TAG, "</executor: %s>", msg.obj);
        } else {
            Log.wf(TAG, "can't handle msg: %s", msg);
        }
    }

    private void executeInternal(Runnable task) {
        // Some anti-debugging tricks.
        antiDebugCheck();

        try {
            task.run();
        } catch (Throwable t) {
            Log.ef(TAG, t, "run task: %s", task);
        } finally {

            XService s = handlerService.get();
            if(s != null) {
                s.getWakeLock().unlock(task);
            } else {
                Log.wf(TAG, "No wakelock release, task=%s", task);
            }
        }
    }

    /**
     * Some basic anti-debugging ticks.
     */
    static void antiDebugCheck(){
        if (detectDebugger()){
            MiscUtils.reportExceptionToAcra(new SecurityException("Debugger"));
            if (!BuildConfig.DEBUG){
                throw new RuntimeException();
            }
        }
    }

    /**
     * Detects if debugger is currently connected.
     * @return
     */
    static boolean detectDebugger(){
        return Debug.isDebuggerConnected();
    }

}

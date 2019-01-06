package net.phonex.service.runEngine;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import net.phonex.util.Log;

import java.lang.ref.WeakReference;

/**
 * Created by miroc on 10.6.15.
 */
public class FastAlarmHandler extends Handler{
    private static final String TAG = "FastAlarmHandler";
    public static final int TYPE_FAST_ALARM = 1;

    private WeakReference<AndroidTimers> androidTimers;

    public FastAlarmHandler(AndroidTimers androidTimers, Looper looper) {
        super(looper);
        this.androidTimers = new WeakReference<>(androidTimers);
    }

    @Override
    public void handleMessage(Message msg) {
        if (msg.what == TYPE_FAST_ALARM) {

            AndroidTimers androidTimers = this.androidTimers.get();
            if (androidTimers != null){
                Bundle data = msg.getData();

                int entryId = data.getInt(AndroidTimers.EXTRA_TIMER_ENTRY_ID);
                int entry = data.getInt(AndroidTimers.EXTRA_TIMER_ENTRY_ADDR);
                long scheduled = data.getLong(AndroidTimers.EXTRA_TIMER_ENTRY_SCHEDULED);
                long firstTime = data.getLong(AndroidTimers.EXTRA_TIMER_ENTRY_FIRSTIME);

                Log.v(TAG, String.format("Received alarm for timer %s [%s | 0x%s]",
                        entryId, entry, Integer.toHexString(entry)));

                androidTimers.alarmFired(entry, entryId, scheduled, firstTime);
            }

        } else {
            Log.wf(TAG, "can't handle msg: %s", msg);
        }
    }
}
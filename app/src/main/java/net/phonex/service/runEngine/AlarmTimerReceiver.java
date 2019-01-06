package net.phonex.service.runEngine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import net.phonex.util.Log;

import java.lang.ref.WeakReference;

/**
 * Separate receiver for the alarm timers.
 * Extracted here since broadcast receivers are non-obfuscated...
 * <p/>
 * Created by ph4r05 on 7/28/14.
 */
public class AlarmTimerReceiver extends BroadcastReceiver {
    private static final String TAG = "AlarmRecv";
    private final WeakReference<AndroidTimers> awr;

    public AlarmTimerReceiver(AndroidTimers timerManager) {
        this.awr = new WeakReference<AndroidTimers>(timerManager);
    }

    /**
     * onReceive event on intents. Mainly devised to receive intents from Android Alarm()
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!AndroidTimers.TIMER_ACTION.equalsIgnoreCase(intent.getAction())) {
            return;
        }

        final AndroidTimers atim = awr.get();
        if (atim == null) {
            Log.w(TAG, "Timer received, master dead, doing nothing");
            return;
        }

        final int entryId = intent.getIntExtra(AndroidTimers.EXTRA_TIMER_ENTRY_ID, -1);
        final int entry = intent.getIntExtra(AndroidTimers.EXTRA_TIMER_ENTRY_ADDR, 0);
        final long scheduled = intent.getLongExtra(AndroidTimers.EXTRA_TIMER_ENTRY_SCHEDULED, 0l);
        final long firstime = intent.getLongExtra(AndroidTimers.EXTRA_TIMER_ENTRY_FIRSTIME, 0l);

        Log.v(TAG, String.format("Received alarm for timer %s [%s | 0x%s] Uri=%s",
                entryId, entry, Integer.toHexString(entry), intent.getDataString()));

        atim.alarmFired(entry, entryId, scheduled, firstime);
    }
}

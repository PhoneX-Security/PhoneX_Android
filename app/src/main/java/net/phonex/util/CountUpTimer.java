package net.phonex.util;

import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

/**
 * Modification of Android CountDownTimer
 * Created by miroc on 20.10.15.
 */
public abstract class CountUpTimer {
    private static final String TAG = "CountUpTimer";

    /**
     * Millis since epoch when alarm should stop.
     */
//    private final long mMillisInFuture;

    /**
     * The interval in millis that the user receives callbacks
     */
    private final long mCountdownInterval;

//    private long mStopTimeInFuture;

    /**
     * boolean representing if the timer was cancelled
     */
    private boolean mCancelled = false;
    private boolean mStarted = false;
    private long mStartTime;


    public CountUpTimer(long countDownInterval) {
//        mMillisInFuture = millisInFuture;
        mCountdownInterval = countDownInterval;
    }

    /**
     * Cancel the countdown.
     */
    public synchronized final void stop() {
        mCancelled = true;
        mStarted = false;
        mHandler.removeMessages(MSG);
        onStop(SystemClock.elapsedRealtime() - mStartTime);
    }

    public synchronized final CountUpTimer start(long startTime) {
        // timer can be mStarted only once
        if (mStarted){
            Log.vf(TAG, "start; already started");
            return this;
        }

        Log.vf(TAG, "start; starting with time=%d", startTime);
        mStartTime = startTime;//SystemClock.elapsedRealtime();
//        mStopTimeInFuture = SystemClock.elapsedRealtime() + mMillisInFuture;
        mHandler.sendMessage(mHandler.obtainMessage(MSG));
        mStarted = true;
        return this;
    }


    public abstract void onTick(long elapsedTimeMillis);
    public abstract void onStop(long elapsedTimeMillis);

    private static final int MSG = 1;


    // handles counting down
    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {

            synchronized (CountUpTimer.this) {
                if (mCancelled) {
                    return;
                }

//                final long millisLeft = mStopTimeInFuture - SystemClock.elapsedRealtime();
//
//                if (millisLeft <= 0) {
//                    onFinish();
//                } else if (millisLeft < mCountdownInterval) {
//                     no tick, just delay until done
//                    sendMessageDelayed(obtainMessage(MSG), millisLeft);
//                } else {
                    long lastTickStart = SystemClock.elapsedRealtime();
                    onTick(lastTickStart - mStartTime);

                    // take into account user's onTick taking time to execute
                    long delay = lastTickStart + mCountdownInterval - SystemClock.elapsedRealtime();

                    // special case: user's onTick took more than interval to
                    // complete, skip to next interval
                    while (delay < 0) delay += mCountdownInterval;

                    sendMessageDelayed(obtainMessage(MSG), delay);
//                }
            }
        }
    };
}
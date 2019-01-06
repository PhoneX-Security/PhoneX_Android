package net.phonex.ui.lock.activity;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import net.phonex.PhoneX;
import net.phonex.ui.lock.util.PinHelper;
import net.phonex.util.Log;
import net.phonex.util.analytics.AnalyticsReporter;

/**
 * Created by miroc on 25.2.15.
 */
public abstract class LockActionBarActivity extends AppCompatActivity {
    private volatile boolean _isRunning;
    private Thread pinThread;

    protected Tracker analyticsTracker;
    protected AnalyticsReporter analyticsReporter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Obtain the shared Tracker instance.
        PhoneX application = (PhoneX) getApplication();
        analyticsTracker = application.getDefaultTracker();
        analyticsReporter = AnalyticsReporter.from(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String name = activityAnalyticsName();
        Log.i(name, "Setting screen name: " + name);
        analyticsTracker.setScreenName(name);
        analyticsTracker.send(new HitBuilders.ScreenViewBuilder().build());
    }

    abstract protected String activityAnalyticsName();

    @Override
    protected void onStart() {
        super.onStart();
        this._isRunning = true;
        PinHelper.tick(this);
        pinThread = new Thread(() -> {
            while(_isRunning){
                if (!_isRunning){
                    break;
                }
                PinHelper.tick(LockActionBarActivity.this);

                try {
                    Thread.sleep(PinHelper.PIN_TICK_TIME);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });

        pinThread.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        this._isRunning = false;
        PinHelper.tick(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this._isRunning = false;
    }

    public AnalyticsReporter getAnalyticsReporter() {
        return analyticsReporter;
    }
}

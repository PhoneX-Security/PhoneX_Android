package net.phonex.ui.lock.activity;

import android.os.Bundle;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import net.phonex.PhoneX;
import net.phonex.ui.customViews.AppCompatPreferenceActivity;
import net.phonex.ui.lock.util.PinHelper;
import net.phonex.util.Log;

/**
 * @author ph4r05
 *
 */
public abstract class LockPreferenceActivity extends AppCompatPreferenceActivity {
    private volatile boolean _isRunning;
    private Thread pinThread;

    protected Tracker analyticsTracker;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Obtain the shared Tracker instance.
        PhoneX application = (PhoneX) getApplication();
        analyticsTracker = application.getDefaultTracker();
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
        pinThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while(_isRunning){
                    if (!_isRunning){
                        break;
                    }

                    PinHelper.tick(LockPreferenceActivity.this);

                    try {
                        Thread.sleep(PinHelper.PIN_TICK_TIME);
                    } catch (InterruptedException e) {
                        break;
                    }
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
}

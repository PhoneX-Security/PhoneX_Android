package net.phonex;

import android.app.Activity;
import android.app.Application.ActivityLifecycleCallbacks;
import android.os.Bundle;
import android.view.WindowManager;

import net.phonex.annotations.PinUnprotected;
import net.phonex.pref.PhonexConfig;
import net.phonex.pref.PreferencesManager;
import net.phonex.ui.lock.PinActivity;
import net.phonex.ui.lock.util.PinHelper;
import net.phonex.util.Log;

/**
 * Created by miroc on 10.12.14.
 */
public class AppLifeCycleListener implements ActivityLifecycleCallbacks {
    private static final String TAG = "AppLifeCycleListener";

    @Override
    public void onActivityCreated(Activity activity, Bundle bundle) {
        Log.inf(TAG, "onCreated: %s", activity.getLocalClassName());
    }

    @Override
    public void onActivityStarted(Activity activity) {
        Log.inf(TAG, "onStarted: %s", activity.getLocalClassName());
        PinHelper.lockIfNoRecentTick(activity);
    }

    @Override
    public void onActivityResumed(Activity activity) {
        Log.inf(TAG, "onResumed: %s", activity.getLocalClassName());
        setScreenshotBlocking(activity);
        checkPinLocking(activity);
    }

    @Override
    public void onActivityPaused(Activity activity) {
        Log.inf(TAG, "onPaused: %s", activity.getLocalClassName());
    }

    @Override
    public void onActivityStopped(Activity activity) {
        Log.inf(TAG, "onStopped: %s", activity.getLocalClassName());
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        Log.inf(TAG, "onSaveInstanceState: %s", activity.getLocalClassName());
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        Log.inf(TAG, "onDestroyed: %s", activity.getLocalClassName());
    }

    private void checkPinLocking(Activity activity){
        // Decide if activity is pin protected according to the PinUnprotected annotation
        boolean isPinProtected = activity.getClass().getAnnotation(PinUnprotected.class) == null;
        if (!isPinProtected || !PinHelper.isEnabled(activity)){
            return;
        }

        boolean isLocked =  PinHelper.isLocked(activity);
        if (isLocked){
            Log.inf(TAG, "Displaying PinLock");
            PinActivity.startPinActivity(activity);
        }

//        Log.df(TAG, "checkPinLocking: Activity [%s], locked [%s]", activity.getLocalClassName(), String.valueOf(isLocked));
    }

    private void setScreenshotBlocking(Activity activity){
        try {
            if (activity!=null){
                PreferencesManager prefs = new PreferencesManager(activity.getApplicationContext());
                boolean blockScreenshot = prefs.getBoolean(PhonexConfig.PHONEX_BLOCK_SCREENSHOTS);

                if (activity.getWindow()!=null){
                    if (blockScreenshot){
                        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
                    } else {
                        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                    }
                }
            }
        } catch(Exception e){
            Log.e(TAG, "Cannot disable screenshots.", e);
        }
    }
}

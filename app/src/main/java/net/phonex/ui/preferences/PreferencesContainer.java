package net.phonex.ui.preferences;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;

import com.google.android.gms.analytics.GoogleAnalytics;

import net.phonex.PhonexSettings;
import net.phonex.pub.a.Compatibility;
import net.phonex.util.Log;
import net.phonex.util.analytics.AnalyticsReporter;
import net.phonex.util.analytics.AppEvents;

public class PreferencesContainer extends BasePreferences {
    private static final String TAG = "PreferencesContainer";

    private int getPreferenceType() {
        return getIntent().getIntExtra(PreferenceManager.EXTRA_PREFERENCE_GROUP, 0);
    }

    @Override
    protected int getXmlPreferences() {
        return PreferenceManager.getXmlResourceForType(getPreferenceType());
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(PreferenceManager.getTitleResourceForType(getPreferenceType()));
    }

    @Override
    protected void onResume() {
        final Activity act = this.getParent();
        if (act!=null) {
            PhonexSettings.loadDefaultLanguage(act);
        }

        super.onResume();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // directly react on preference changes
        if (key.equals(PreferenceManager.SEC_GOOGLE_ANALYTICS_ENABLE)){
            boolean enableGoogleAnalytics = sharedPreferences.getBoolean(key, false);
            Log.df(TAG, "onSharedPreferenceChanged; key=%s, value=%s", key, enableGoogleAnalytics);
            if (!enableGoogleAnalytics){
                AnalyticsReporter.from(this).event(AppEvents.GOOGLE_ANALYTICS_DISABLE);
            }
            GoogleAnalytics.getInstance(getApplicationContext()).setAppOptOut(!enableGoogleAnalytics);
        }
        super.onSharedPreferenceChanged(sharedPreferences, key);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == Compatibility.getHomeMenuId()) {
            finish();
            return true;
        }
        return false;
    }

    @Override
    protected void postPrefsBuild() {
        super.postPrefsBuild();
        PreferenceManager.postPrefsGroupBuild(this, this, getPreferenceType());
        
    }

    @Override
    protected void updateDescriptions() {
        PreferenceManager.updateDescriptionForType(this, this, getPreferenceType());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        PreferenceManager.onActivityResult(requestCode, resultCode, data, this);
    }

    @Override
    protected String activityAnalyticsName() {
        return this.getClass().getSimpleName();
    }
}

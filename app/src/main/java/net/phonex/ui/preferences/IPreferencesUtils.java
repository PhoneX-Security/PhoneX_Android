package net.phonex.ui.preferences;

import android.preference.Preference;
import android.preference.PreferenceScreen;

/**
 * Preference manipulation interface.
 */
public interface IPreferencesUtils {
    void hidePrefEntry(String parent, String fieldName);
    PreferenceScreen getPreferenceScreen();
    Preference findPreference(CharSequence prefName);
    void setStringEntrySummary(String key);
    void setPreferenceScreenType(String key, int type);
    void setPreferenceScreenSub(String key, Class<?> activityClass, Class<?> fragmentClass, int type);
    void recreatePrefs();

    void enablePrefEntry(String parent, String fieldName, boolean enable);
    void setClickListenerForPrefEntry(String parent, String fieldName, Preference.OnPreferenceClickListener clickListener);
}

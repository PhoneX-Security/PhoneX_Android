package net.phonex.ui.preferences;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import net.phonex.PhonexSettings;
import net.phonex.R;
import net.phonex.ui.lock.activity.LockPreferenceActivity;
import net.phonex.util.Log;

public abstract class BasePreferences extends LockPreferenceActivity implements
        OnSharedPreferenceChangeListener, IPreferencesUtils {
    private static final String TAG="BasePreferences";

    /**
     * Get the xml resource for this screen
     *
     * @return the resource identifier
     */
    protected abstract int getXmlPreferences();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prePrefsBuild();
        addPreferencesFromResource(getXmlPreferences());
        postPrefsBuild();
    }

    // solution to add Toolbar
    // https://stackoverflow.com/questions/17849193/how-to-add-action-bar-from-support-library-into-preferenceactivity
    // unfortunately doesn't work for nested preference screens
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        LinearLayout root = (LinearLayout)findViewById(android.R.id.list).getParent().getParent().getParent();
        Toolbar bar = (Toolbar) LayoutInflater.from(this).inflate(R.layout.toolbar, root, false);
        bar.setTitle(getTitle());
        root.addView(bar, 0); // insert at top

//        root.setBackgroundDrawable(getResources().getDrawable(R.drawable.app_layered_background));
        bar.setNavigationOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        updateDescriptions();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        updateDescriptions();
    }

    /**
     * Process update of description of each preference field
     */
    protected abstract void updateDescriptions();

    /**
     * Event called before XML is loaded.
     */
    protected void prePrefsBuild() {
    }

    /**
     * Event called after XML is loaded.
     */
    protected void postPrefsBuild() {
    }

    // Utilities for update Descriptions
    /**
     * Get field summary if nothing set. By default it will try to add _summary
     * to name of the current field
     * 
     * @param field_name Name of the current field
     * @return Translated summary for this field
     */
    protected String getDefaultFieldSummary(String field_name) {
        try {
            String keyid = R.string.class.getField(field_name + "_summary").get(null).toString();
            return getString(Integer.parseInt(keyid));
        } catch (SecurityException e) {
            // Nothing to do : desc is null
        } catch (NoSuchFieldException e) {
            // Nothing to do : desc is null
        } catch (IllegalArgumentException e) {
            // Nothing to do : desc is null
        } catch (IllegalAccessException e) {
            // Nothing to do : desc is null
        }

        return "";
    }

    /**
     * Set summary of a standard string field If empty will display the default
     * summary Else it displays the preference value
     * 
     * @param fieldName the preference key name
     */
    public void setStringEntrySummary(String fieldName) {
        PreferenceScreen pfs = getPreferenceScreen();
        SharedPreferences sp = pfs.getSharedPreferences();
        Preference pref = pfs.findPreference(fieldName);

        String val = sp.getString(fieldName, null);
        if (TextUtils.isEmpty(val)) {
            val = getDefaultFieldSummary(fieldName);
        }
        setPreferenceSummary(pref, val);
    }

    /**
     * Set summary of a password field If empty will display default summary If
     * password will display a * char for each letter of password
     * 
     * @param fieldName the preference key name
     */
    public void setPasswordFieldSummary(String fieldName) {
        PreferenceScreen pfs = getPreferenceScreen();
        SharedPreferences sp = pfs.getSharedPreferences();
        Preference pref = pfs.findPreference(fieldName);

        String val = sp.getString(fieldName, null);

        if (TextUtils.isEmpty(val)) {
            val = getDefaultFieldSummary(fieldName);
        } else {
            val = val.replaceAll(".", "*");
        }
        setPreferenceSummary(pref, val);
    }

    /**
     * Set summary of a list field.
     * If field is empty, it displays default field summary.
     * If there is one item selected, item's name is set.
     * 
     * @param fieldName the preference key name
     */
    public void setListFieldSummary(String fieldName) {
        PreferenceScreen pfs = getPreferenceScreen();
        ListPreference pref = (ListPreference) pfs.findPreference(fieldName);

        CharSequence val = pref.getEntry();
        if (TextUtils.isEmpty(val)) {
            val = getDefaultFieldSummary(fieldName);
        }
        setPreferenceSummary(pref, val);
    }

    /**
     * Safe setSummary on a Preference object that make sure that the preference
     * exists before doing anything
     * 
     * @param pref the preference to change summary of
     * @param val the string to set as preference summary
     */
    protected void setPreferenceSummary(Preference pref, CharSequence val) {
        if (pref != null) {
            pref.setSummary(val);
        }
    }

    /**
     * Hide a preference from the screen.
     * 
     * @param parent the parent group preference if any, leave null if preference is a root pref
     * @param fieldName the preference key name to hide
     */
    @Override
    public void hidePrefEntry(String parent, String fieldName) {
        PreferenceScreen pfs = getPreferenceScreen();
        PreferenceGroup parentPref = pfs;
        if (parent != null) {
            parentPref = (PreferenceGroup) pfs.findPreference(parent);
        }

        Preference toRemovePref = pfs.findPreference(fieldName);
        if (toRemovePref != null && parentPref != null) {
            parentPref.removePreference(toRemovePref);
        } else {
            Log.wf(TAG, "hidePrefEntry; Preference field not found, parent=[%s], name=[%s]", parent, fieldName);
        }
    }

    @Override
    public void enablePrefEntry(String parent, String fieldName, boolean enabled) {
        PreferenceScreen pfs = getPreferenceScreen();
        PreferenceGroup parentPref = pfs;
        if (parent != null) {
            parentPref = (PreferenceGroup) pfs.findPreference(parent);
        }

        Preference preference = pfs.findPreference(fieldName);
        if (preference != null && parentPref != null) {
            preference.setEnabled(enabled);
        } else {
            Log.wf(TAG, "enablePrefEntry; Preference field not found, parent=[%s], name=[%s]", parent, fieldName);
        }
    }

    @Override
    public void setClickListenerForPrefEntry(String parent, String fieldName, Preference.OnPreferenceClickListener clickListener) {
        PreferenceScreen pfs = getPreferenceScreen();
        PreferenceGroup parentPref = pfs;
        if (parent != null) {
            parentPref = (PreferenceGroup) pfs.findPreference(parent);
        }

        Preference preference = pfs.findPreference(fieldName);
        if (preference != null && parentPref != null) {
            preference.setOnPreferenceClickListener(clickListener);
        } else {
            Log.wf(TAG, "enablePrefEntry; Preference field not found, parent=[%s], name=[%s]", parent, fieldName);
        }
    }


    @Override
    public void setPreferenceScreenType(String key, int type) {
        setPreferenceScreenType(this.getClass(), key, type);
    }

    @Override
    public void setPreferenceScreenSub(String key, Class<?> activityClass, Class<?> fragmentClass, int type) {
        setPreferenceScreenType(activityClass, key, type);
    }
    
    private void setPreferenceScreenType(Class<?> classObj, String key, int type) {
        Preference pf = findPreference(key);
        Intent it = new Intent(this, classObj);
        it.putExtra(PreferenceManager.EXTRA_PREFERENCE_GROUP, type);
        pf.setIntent(it);
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
    	// This pref activity does not include any fragment
    	return false;
    }

    @Override
    public void recreatePrefs() {
        //recreate();
        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        PhonexSettings.loadDefaultLanguage(this);
    }
}

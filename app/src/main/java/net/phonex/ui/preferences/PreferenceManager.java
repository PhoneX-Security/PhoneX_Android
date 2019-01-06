package net.phonex.ui.preferences;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.view.Menu;
import android.view.MenuItem;

import net.phonex.PhonexSettings;
import net.phonex.R;
import net.phonex.core.Intents;
import net.phonex.pref.PhonexConfig;
import net.phonex.pref.PreferencesManager;
import net.phonex.ui.lock.PinActivity;
import net.phonex.ui.lock.util.PinHelper;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;

import java.util.Locale;

public class PreferenceManager {
    private static final String TAG = "PrefManager";
    private static final String MEDIA_AUDIO_VOLUME_KEY = "audio_volume";
    private static final String MEDIA_AUDIO_QUALITY_KEY = "audio_quality";
    private static final String MEDIA_BAND_TYPE_KEY = "band_types";
    private static final String MEDIA_CODEC_LIST_KEY = "codecs_list";
    private static final String MEDIA_CODEC_EXTRA_KEY = "codecs_extra_settings";
    private static final String MEDIA_MISC_KEY = "misc";
    private static final String MEDIA_AUDIO_PROBLEMS_KEY = "audio_troubleshooting";

    private static final String CON_SECURE_TRANSPORT_KEY = "secure_transport";
    private static final String CON_NAT_TRAVERSAL_KEY = "nat_traversal";
    private static final String CON_TRANSPORT_KEY = "transport";
    private static final String CON_SIP_PROTOCOL_KEY = "sip_protocol";
    private static final String CON_PERFS_KEY = "perfs";
    private static final String CON_DEBUGGING_KEY = "debugging";
    private static final String UI_ADVANCED = "advanced_ui";
    private static final String SEC_SIP_SIGNATURE_KEY = "sip_signature";
    private static final String SEC_PIN_LOCK_KEY = "pin_lock";
    private static final String SEC_CALLS_KEY = "security_calls";
    private static final String SEC_UPDATES = "sip_updates_section";
    public static final String SEC_GOOGLE_ANALYTICS_ENABLE = "google_analytics.enable";

    public final static String EXTRA_PREFERENCE_GROUP = "preference_group";
    public final static int GROUP_MEDIA = 10;
    public final static int GROUP_MEDIA_BAND = 11;
    public final static int GROUP_MEDIA_PROBLEMS = 12;
    public final static int GROUP_CONNECTIVITY = 20;
    public final static int GROUP_UI = 60;
    public final static int GROUP_SECURITY = 70;
    public final static int GROUP_SECURITY_SIP_SIGNATURE = 80;
    public final static int GROUP_SECURITY_PIN_LOCK = 81;

    /**
     * When changing Pin, this code is used when calling startActivityForResult
     */
    public static final int REQUEST_CODE_REMOVE_PIN = 1;
    
    /**
     * Get the xml res for preference screen building.
     * @param t The preference screen type
     * @return the int res for xml
     */
    public static int getXmlResourceForType(int t) {
        switch(t) {
            case GROUP_MEDIA:
                return R.xml.prefs_media;
            case GROUP_MEDIA_BAND:
                return R.xml.prefs_media_band_types;
            case GROUP_MEDIA_PROBLEMS:
                return R.xml.prefs_media_troubleshoot;
            case GROUP_CONNECTIVITY:
                return R.xml.prefs_network;
            case GROUP_UI:
                return R.xml.prefs_ui;
            case GROUP_SECURITY:
                return R.xml.prefs_security;
            case GROUP_SECURITY_SIP_SIGNATURE:
            	return R.xml.prefs_security_sip_signature;
            case GROUP_SECURITY_PIN_LOCK:
                return R.xml.prefs_security_pin_lock;
        }
        return 0;
    }
    
    /**
     * Get the title int resource string for the type of preference.
     * @param t The preference screen type
     * @return the int res for title
     */
    public static int getTitleResourceForType(int t) {
        switch(t) {
            case GROUP_MEDIA:
                return R.string.prefs_media;
            case GROUP_MEDIA_BAND:
                return R.string.codecs_band_types;
            case GROUP_MEDIA_PROBLEMS:
                return R.string.audio_troubleshooting;
            case GROUP_CONNECTIVITY:
                return R.string.prefs_connectivity;
            case GROUP_UI:
                return R.string.prefs_ui;
            case GROUP_SECURITY:
                return R.string.security;
            case GROUP_SECURITY_SIP_SIGNATURE:
            	return R.string.sip_signature;
            case GROUP_SECURITY_PIN_LOCK:
                return R.string.pin_lock;
        }
        return 0;
    }

    public static void postPrefsGroupBuild(final Activity activity, final IPreferencesUtils pfh, int t) {
        PreferencesManager pfw = new PreferencesManager(activity);
        
        switch (t) {
            case GROUP_MEDIA: {
                
                // Expert mode
                if(!pfw.isDeveloperActive()) {
                    pfh.hidePrefEntry(MEDIA_AUDIO_QUALITY_KEY, PhonexConfig.ECHO_CANCELLATION);
                    pfh.hidePrefEntry(MEDIA_AUDIO_QUALITY_KEY, PhonexConfig.ECHO_CANCELLATION_MODE);
                    pfh.hidePrefEntry(MEDIA_AUDIO_QUALITY_KEY, PhonexConfig.ECHO_CANCELLATION_TAIL_LEN);
                    pfh.hidePrefEntry(MEDIA_AUDIO_QUALITY_KEY, PhonexConfig.MEDIA_QUALITY);
                    pfh.hidePrefEntry(MEDIA_AUDIO_QUALITY_KEY, PhonexConfig.ENABLE_NOISE_SUPPRESSION);
                    pfh.hidePrefEntry(MEDIA_AUDIO_QUALITY_KEY, PhonexConfig.AUDIO_FRAME_PTIME);
                    pfh.hidePrefEntry(MEDIA_AUDIO_QUALITY_KEY, PhonexConfig.HAS_IO_QUEUE);

                    pfh.hidePrefEntry(MEDIA_AUDIO_VOLUME_KEY, PhonexConfig.SOUND_BT_MIC_VOLUME);
                    pfh.hidePrefEntry(MEDIA_AUDIO_VOLUME_KEY, PhonexConfig.SOUND_BT_SPEAKER_VOLUME);
                    pfh.hidePrefEntry(MEDIA_AUDIO_VOLUME_KEY, PhonexConfig.USE_SOFT_VOLUME);

                    pfh.hidePrefEntry(MEDIA_MISC_KEY, PhonexConfig.AUTO_CONNECT_SPEAKER);

                    pfh.hidePrefEntry(null, MEDIA_CODEC_EXTRA_KEY);
                    pfh.hidePrefEntry(null, MEDIA_CODEC_LIST_KEY);
                    pfh.hidePrefEntry(null, MEDIA_BAND_TYPE_KEY);
                    pfh.hidePrefEntry(null, MEDIA_AUDIO_PROBLEMS_KEY);
                    pfh.hidePrefEntry(null, PhonexConfig.MEDIA_THREAD_COUNT);
                }else {
                    // Bind only if not removed
                    pfh.setPreferenceScreenType(MEDIA_AUDIO_PROBLEMS_KEY, GROUP_MEDIA_PROBLEMS);
                    pfh.setPreferenceScreenType(MEDIA_BAND_TYPE_KEY, GROUP_MEDIA_BAND);
                    
                    // Sub activity intent for codecs
                    // Disabled, has to be refactored.
//                    Preference pf = pfh.findPreference(MEDIA_CODEC_LIST_KEY);
//                    Intent it = new Intent(ctxt, CodecsActivity.class);
//                    pf.setIntent(it);
                }

                break;
            }
            
            case GROUP_MEDIA_PROBLEMS: {
                break;
            }

            case GROUP_CONNECTIVITY: {
                if(!pfw.isDeveloperActive() || PhonexSettings.restrictSettings()) {
                    pfh.hidePrefEntry(CON_NAT_TRAVERSAL_KEY, PhonexConfig.ENABLE_TURN);
                    pfh.hidePrefEntry(CON_NAT_TRAVERSAL_KEY, PhonexConfig.TURN_SERVER);
                    pfh.hidePrefEntry(CON_NAT_TRAVERSAL_KEY, PhonexConfig.TURN_USERNAME);
                    pfh.hidePrefEntry(CON_NAT_TRAVERSAL_KEY, PhonexConfig.TURN_PASSWORD);
                    pfh.hidePrefEntry(CON_NAT_TRAVERSAL_KEY, PhonexConfig.ENABLE_STUN2);
                    
                    pfh.hidePrefEntry(CON_TRANSPORT_KEY, PhonexConfig.ENABLE_TCP);
                    pfh.hidePrefEntry(CON_TRANSPORT_KEY, PhonexConfig.ENABLE_UDP);
                    pfh.hidePrefEntry(CON_TRANSPORT_KEY, PhonexConfig.DISABLE_TCP_SWITCH);
                    pfh.hidePrefEntry(CON_TRANSPORT_KEY, PhonexConfig.PJ_TCP_PORT);
                    pfh.hidePrefEntry(CON_TRANSPORT_KEY, PhonexConfig.PJ_UDP_PORT);
                    pfh.hidePrefEntry(CON_TRANSPORT_KEY, PhonexConfig.PJ_RTP_PORT);
                    pfh.hidePrefEntry(CON_TRANSPORT_KEY, PhonexConfig.USE_IPV6);
                    pfh.hidePrefEntry(CON_TRANSPORT_KEY, PhonexConfig.OVERRIDE_NAMESERVER);
                    pfh.hidePrefEntry(CON_TRANSPORT_KEY, PhonexConfig.FORCE_NO_UPDATE);
                    
                    pfh.hidePrefEntry(CON_TRANSPORT_KEY, PhonexConfig.ENABLE_QOS);
                    pfh.hidePrefEntry(CON_TRANSPORT_KEY, PhonexConfig.DSCP_VAL);
                    pfh.hidePrefEntry(CON_TRANSPORT_KEY, PhonexConfig.USER_AGENT);
                    pfh.hidePrefEntry(CON_TRANSPORT_KEY, PhonexConfig.NETWORK_ROUTES_POLLING);
                    
                    pfh.hidePrefEntry(CON_TRANSPORT_KEY, PhonexConfig.ENABLE_DNS_SRV);
                    pfh.hidePrefEntry(CON_TRANSPORT_KEY, PhonexConfig.USE_COMPACT_FORM);

                    pfh.hidePrefEntry("for_incoming", PhonexConfig.USE_ANYWAY_IN);
                    pfh.hidePrefEntry("for_outgoing", PhonexConfig.USE_ANYWAY_OUT);
                    
                    pfh.hidePrefEntry(null, CON_SIP_PROTOCOL_KEY);
                    pfh.hidePrefEntry(null, CON_PERFS_KEY);
                    pfh.hidePrefEntry(null, CON_DEBUGGING_KEY);
                    pfh.hidePrefEntry(null, PhonexConfig.NETWORK_WATCHDOG);
                }
                
                if (PhonexSettings.restrictSettings()){
                	pfh.hidePrefEntry(null, CON_SECURE_TRANSPORT_KEY);
                	pfh.hidePrefEntry(null, CON_NAT_TRAVERSAL_KEY);
                	pfh.hidePrefEntry(null, CON_DEBUGGING_KEY);
                	pfh.hidePrefEntry(CON_NAT_TRAVERSAL_KEY, PhonexConfig.ENABLE_ICE);
                	pfh.hidePrefEntry(CON_NAT_TRAVERSAL_KEY, PhonexConfig.ENABLE_STUN);
                	pfh.hidePrefEntry(CON_NAT_TRAVERSAL_KEY, PhonexConfig.STUN_SERVER);
                	pfh.hidePrefEntry(CON_TRANSPORT_KEY, PhonexConfig.ENABLE_TLS);
                }
                
                break;
            }
            case GROUP_UI: {

                if(!pfw.isDeveloperActive()) {
                    pfh.hidePrefEntry(null, UI_ADVANCED);
                }
                
                if(PhonexSettings.restrictSettings()){
                	pfh.hidePrefEntry(UI_ADVANCED, PhonexConfig.LOG_LEVEL);
                	pfh.hidePrefEntry(UI_ADVANCED, PhonexConfig.LOG_TO_FILE);
                }
                
                break;
            }
            case GROUP_SECURITY: {
                pfh.setPreferenceScreenType(SEC_SIP_SIGNATURE_KEY, GROUP_SECURITY_SIP_SIGNATURE);
                pfh.setPreferenceScreenType(SEC_PIN_LOCK_KEY, GROUP_SECURITY_PIN_LOCK);

                // anyone is able to change google analytics settings
//                pfh.enablePrefEntry(null, SEC_GOOGLE_ANALYTICS_ENABLE, false);

                if(!pfw.isDeveloperActive()) {
                    pfh.hidePrefEntry(null, SEC_CALLS_KEY);
                }

                if (!PhonexSettings.enableUpdateCheck()){
                	pfh.hidePrefEntry(null, SEC_UPDATES);
                }
                break;
            }

            case GROUP_SECURITY_PIN_LOCK: {
                setupPinLock(activity, pfh);
                break;
            }
            
            default:
                break;
        }

        // Register a listener for a language change to update setting.
        checkLanguageChange(activity, pfh, t);
        // Check if logger option was changed.
        checkLoggerChange(activity, pfh, t);
    }

    /**
     * Checks the language change. If the current screen contains language options, registers
     * to its change and acts accordingly, i.e., sets locale and restarts activity.
     * @param ctxt
     * @param pfh
     * @param t
     */
    private static void checkLanguageChange(final Context ctxt, final IPreferencesUtils pfh, int t){
        if (t != GROUP_UI) {
            return;
        }

        final Preference langPref = pfh.findPreference(PhonexConfig.LANGUAGE);
        if (langPref==null){
            Log.w(TAG, "Language option not found");
            return;
        }

        // Set on change listener so if prefs got changed the activity gets re-created.
        langPref.setOnPreferenceChangeListener((preference, newValue) -> {
            try {
                final String lang = (String) newValue;
                PhonexSettings.storeDefaultLanguage(ctxt.getApplicationContext(), lang);
                Locale newLocale = PhonexSettings.getLocale(lang);
                PhonexSettings.setLocale(newLocale, ctxt.getApplicationContext());

                Log.inf(TAG, "Language has changed: %s, newLocale=%s", newValue, newLocale);
                pfh.recreatePrefs();
                return true;
            } catch(Exception ex){
                Log.e(TAG, "Exception in handling lanuage change", ex);
            }

            return false;
        });
    }

    /**
     * Checks if the logger option was chosen and if yes, immediatelly enables/disables logging
     * to an external file.
     *
     * @param ctxt
     * @param pfh
     * @param t
     */
    private static void checkLoggerChange(final Context ctxt, final IPreferencesUtils pfh, int t){
        if (t != GROUP_UI) {
            return;
        }

        final Preference logPref = pfh.findPreference(PhonexConfig.LOG_TO_FILE);
        if (logPref ==null){
            Log.w(TAG, "Log option not found");
            return;
        }

        // Set on change listener so if prefs got changed the activity gets re-created.
        logPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                try {
                    final boolean enableLogger = (Boolean) newValue;
                    Log.inf(TAG, "Log settings has changed: %s, enableLogging=%s", newValue, enableLogger);

                    Intent intent = new Intent(Intents.ACTION_LOGGER_CHANGE);
                    intent.putExtra(Intents.EXTRA_LOGGER_ENABLE, enableLogger);
                    MiscUtils.sendBroadcast(ctxt, intent);

                    return true;
                } catch (Exception ex) {
                    Log.e(TAG, "Exception in handling logger change", ex);
                }

                return false;
            }
        });
    }

    public static void updateDescriptionForType(Context ctxt, IPreferencesUtils pfh, int t) {
        switch (t) {
            case GROUP_MEDIA:
                break;
            case GROUP_CONNECTIVITY:
                pfh.setStringEntrySummary(PhonexConfig.STUN_SERVER);
                break;
        }
    }

    public static void onMainPrefsPrepareOptionMenu(Menu menu, Context ctxt, PreferencesManager prefsWrapper) {
        // Allow developer preferences only if we are in developer edition.
        final MenuItem develItem = menu.findItem(R.id.developer);
        if (develItem!=null){
            if (PhonexSettings.debuggingRelease()){
                develItem.setVisible(true);
                develItem.setTitle(prefsWrapper.isDeveloperActive() ? R.string.normal_preferences : R.string.devel_preferences);
            } else {
                develItem.setVisible(false);
            }

        }

        //menu.findItem(R.id.audio_test).setVisible(prefsWrapper.isDeveloperActive());
    }

    private static void setupPinLock(final Activity activity, final IPreferencesUtils pfh){
        final PinPreferenceFields ppf = new PinPreferenceFields(pfh);

        if (!PinHelper.hasPinSaved(activity) && ppf.enablePinPref.isChecked()){
            Log.wf(TAG, "Security preferences: PIN is not set but PIN preference checkbox is enabled, disabling");
            ppf.enablePinPref.setChecked(false);
        }

        ppf.enablePinOptions(ppf.enablePinPref.isChecked());

        ppf.enablePinPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                boolean pinEnabled = newValue.toString().equals("true");
                if (pinEnabled) {
                    ppf.enablePinOptions(true);

                    if (!PinHelper.hasPinSaved(activity)) {
                        PinActivity.startPinActivity(activity);
                    }
                } else {
                    PinActivity.startPinActivityToRemovePin(activity, REQUEST_CODE_REMOVE_PIN);
                    return false; // do not uncheck the checkbox immediately, wait for PIN activity result
                }
                return true;
            }
        });
    }

    /**
     * Callback called from owning activity
     */
    public static void onActivityResult(int requestCode, int resultCode, Intent data, final IPreferencesUtils pfh) {
        Log.vf(TAG, "onActivityResult; requestCode [%d]; resultCode[%d], intent [%s]", requestCode, resultCode, data==null ? "null" : data.toString());
        if (requestCode == REQUEST_CODE_REMOVE_PIN){
            if (resultCode == Activity.RESULT_OK){
                PinPreferenceFields ppf = new PinPreferenceFields(pfh);
                ppf.enablePinPref.setChecked(false);
                ppf.enablePinOptions(false);
            }
        }
    }

    private static class PinPreferenceFields {
        final CheckBoxPreference enablePinPref;
        final Preference changePinPref;
        final Preference pinlockTimer;
        final Preference resetPin;

        PinPreferenceFields(final IPreferencesUtils pfh) {
            enablePinPref = (CheckBoxPreference) pfh.findPreference(PhonexConfig.PIN_LOCK_ENABLE);
            changePinPref = pfh.findPreference(PhonexConfig.PIN_LOCK_CHANGE_PIN);
            pinlockTimer = pfh.findPreference(PhonexConfig.PIN_LOCK_TIMER);
            resetPin = pfh.findPreference(PhonexConfig.PIN_LOCK_RESET_PIN);
        }

        void enablePinOptions(boolean enable){
            changePinPref.setEnabled(enable);
            pinlockTimer.setEnabled(enable);
            resetPin.setEnabled(enable);
        }
    }
}

package net.phonex.license;

import android.content.ContentValues;

import net.phonex.core.Intents;
import net.phonex.pref.PhonexConfig;
import net.phonex.db.entity.SipProfile;
import net.phonex.pref.PreferencesConnector;
import net.phonex.service.XService;
import net.phonex.util.Log;
import net.phonex.util.Registerable;

/**
 * Shows and schedules notifications prior license expiration to inform user
 * Created by miroc on 18.5.15.
 */
public class LicenseExpirationCheckManager implements Registerable {
    private static final String TAG = "LicenseExpirationCheckManager";

    // real
    public static final int WEEK = 1000*60*60*24*7;
    public static final int DAY = 1000*60*60*24;
    public static final int DRIFT = 1000*10;
    // test
//    private static final int WEEK = 1000*60* 3; // in test scenario, week takes 3 min
//    private static final int DAY = 1000*60*1; // in test scenario, day takes 1 min
//    private static final int DRIFT = 1;

    private XService svc;
    private PreferencesConnector pref;

    public LicenseExpirationCheckManager(XService svc) {
        this.svc = svc;
        pref = new PreferencesConnector(svc);
    }

    private void updateExpirationInformation(){
        SipProfile profile = SipProfile.getCurrentProfile(svc);
        if (profile == null){
            Log.wf(TAG, "updateExpirationInformation; profile is null, not updating");
            return;
        }

        boolean isExpired = profile.getLicenseInformation().timeToExpiration() <= 0;
        Log.vf(TAG, "updateExpirationInformation; isExpired [%s]", isExpired);

        ContentValues cv = new ContentValues();
        cv.put(SipProfile.FIELD_LICENSE_EXPIRED, isExpired);
        SipProfile.updateProfile(svc.getContentResolver(), profile.getId(), cv);
    }

    public static WarningType getWarningType(LicenseInformation licInf){
        if (licInf.timeToExpiration() <= 0){
            return WarningType.EXPIRED;
        } else if (licInf.timeToExpiration() <= DAY){
            return WarningType.DAY_TO_EXPIRE;
        } else if (licInf.timeToExpiration() > DAY && licInf.timeToExpiration() <= WEEK ) {
            return WarningType.WEEK_TO_EXPIRE;
        } else {
            return WarningType.MORE_THAN_WEEK;
        }
    }

    public void makeExpirationCheck(boolean afterLogin){
        SipProfile profile = SipProfile.getCurrentProfile(svc);
        if (profile == null){
            Log.wf(TAG, "makeExpirationCheck; account is null, not continuing.");
            return;
        }

        if (!afterLogin){
            // update profile as expired if necessary
            updateExpirationInformation();
        }

        Log.vf(TAG, "makeExpirationCheck; sipProfile [%s]", profile.getSip());
        LicenseInformation licInf = profile.getLicenseInformation();

        WarningType warningType = getWarningType(licInf);

        switch (warningType){
            case EXPIRED:
                Log.df(TAG, "branch 1");
                // there is a dialog window shown after login when license is expired, therefore do not show notification in such case
                if (!afterLogin){
                    showNotificationIfNotShownYet(PhonexConfig.TRIAL_EXPIRED_WARNING_SHOWN, WarningType.EXPIRED);
                }
                break;
            case DAY_TO_EXPIRE:
                Log.df(TAG, "branch 2");
                showNotificationIfNotShownYet(PhonexConfig.TRIAL_DAY_TO_EXPIRE_WARNING_SHOWN, WarningType.DAY_TO_EXPIRE);
                scheduleAlarm(licInf.timeToExpiration());
                break;
            case WEEK_TO_EXPIRE:
                Log.df(TAG, "branch 3");
                showNotificationIfNotShownYet(PhonexConfig.TRIAL_WEEK_TO_EXPIRE_WARNING_SHOWN, WarningType.WEEK_TO_EXPIRE);
                scheduleAlarm(licInf.timeToExpiration());
                break;
            case MORE_THAN_WEEK:
                Log.df(TAG, "branch 4");
                scheduleAlarm(licInf.timeToExpiration());
                break;
        }
    }

    private void scheduleAlarm(long timeToExpiration) {
        Log.vf(TAG, "scheduleAlarm; timeToExpiration [%d]", timeToExpiration);

        long timeToFire;
        if (timeToExpiration <= 0){
            Log.inf(TAG, "scheduleAlarm; license has already expired, do not schedule anything");
            return;
        } else if (timeToExpiration < DAY) {
            timeToFire = timeToExpiration + DRIFT;
        } else if (timeToExpiration < WEEK){
            timeToFire = (timeToExpiration - DAY) + DRIFT;
        } else {
            timeToFire = (timeToExpiration - WEEK) + DRIFT;
        }

        Log.inf(TAG, "Setting Alarm [%s] in [%d] ms", Intents.ALARM_LICENSE_EXPIRATION_CHECK, timeToFire);
        svc.setAlarm(Intents.ALARM_LICENSE_EXPIRATION_CHECK, timeToFire);
    }

    @Override
    public void register() {
    }

    @Override
    public void unregister() {
    }

    private boolean showNotificationIfNotShownYet(String warningPreferencesFlag, WarningType type){
        if (!isWarningFlag(warningPreferencesFlag)) {
            Log.inf(TAG, "showNotificationIfNotShownYet; displaying [%s] type [%s]", warningPreferencesFlag, type);
            setWarningFlag(warningPreferencesFlag);

            svc.getNotificationManager().notifyLicenseExpiration(type);
            return true;
        } else {
            Log.inf(TAG, "showNotificationIfNotShownYet; [%s] type [%s] warning already shown", warningPreferencesFlag, type);
            return false;
        }
    }

    public void removeWarningFlags(){
        pref.setBoolean(PhonexConfig.TRIAL_EXPIRED_WARNING_SHOWN, false);
        pref.setBoolean(PhonexConfig.TRIAL_WEEK_TO_EXPIRE_WARNING_SHOWN, false);
        pref.setBoolean(PhonexConfig.TRIAL_DAY_TO_EXPIRE_WARNING_SHOWN, false);
    }

    private void setWarningFlag(String flag){
        pref.setBoolean(flag, true);
    }

    private boolean isWarningFlag(String flag){
        return pref.getBoolean(flag, false);
    }

}

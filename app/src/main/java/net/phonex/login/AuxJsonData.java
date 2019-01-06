package net.phonex.login;

import android.content.ContentValues;
import android.content.Context;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.phonex.accounting.AccountingPermissionUpdater;
import net.phonex.pref.PhonexConfig;
import net.phonex.db.entity.SipProfile;
import net.phonex.db.entity.TrialEventLog;
import net.phonex.pref.PreferencesManager;
import net.phonex.soap.jsonEntities.Event;
import net.phonex.soap.jsonEntities.Evtlog;
import net.phonex.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Data contained in subscribers table, received during login or when self-checking user's account
 * Contains:
 * - currentUsagePolicies
 * - turnPassword
 * - list of support accounts
 * - trialEventLogin (deprecated, replaced by policies)
 * - accountSettings (recoveryEmail)
 * Created by miroc on 9.10.15.
 */
public final class AuxJsonData {
    private static final String TAG = "AuxJsonData";
    private String currentUsagePolicy;
    private String turnPassword;
    private String recoveryEmail;
    private String[] supportAccounts;
    private Evtlog trialEventLog;

    public static AuxJsonData fromAuxJsonString(String auxJson){
        return new AuxJsonData(auxJson);
    }

    public String getTurnPassword() {
        return turnPassword;
    }

    private AuxJsonData(String auxJson){
        Log.vf(TAG, "AuxJsonData; data=%s", auxJson);
        // Parse trial event data
        Gson gson = new Gson();
        JsonParser jsonParser = new JsonParser();
        JsonElement jsonElement = jsonParser.parse(auxJson);
        if (jsonElement != null && !jsonElement.isJsonNull()){
            trialEventLog = gson.fromJson(jsonElement.getAsJsonObject().get("evtlog"), Evtlog.class);
            supportAccounts = gson.fromJson(jsonElement.getAsJsonObject().get("support_contacts"), String[].class);

            // Parse TURN password, save to database.
            JsonElement turnPwdEl = jsonElement.getAsJsonObject().get("turnPwd");
            if (turnPwdEl != null){
                turnPassword = turnPwdEl.getAsString();
            }

            // {"accountSettings":{"recoveryEmail":"blabla"}}
            JsonElement accountSettingsEl = jsonElement.getAsJsonObject().get("accountSettings");
            if (accountSettingsEl != null){
                processAccountSettingsEl(accountSettingsEl);
            }

            // Usage policies
            JsonElement currentPolicyEl = jsonElement.getAsJsonObject().get("currentPolicy");
            if (currentPolicyEl != null){
                currentUsagePolicy = currentPolicyEl.toString();
            }
        }
    }

    private void processAccountSettingsEl(JsonElement accountSettingsEl){
        JsonObject obj = accountSettingsEl.getAsJsonObject();
        JsonElement recoveryEmailEl = obj.get("recoveryEmail");
        if(recoveryEmailEl != null){
            recoveryEmail = recoveryEmailEl.getAsString();
        }
    }

    /**
     * Save all data to local database and profile (required to do during login)
     * @param context
     */
    public void saveAllData(Context context){
        saveTrialEventData(context);
        saveSupportAccounts(context);
        saveAccountSettings(context);
        processAppServerPolicy(context);
    }

    private void saveAccountSettings(Context context) {
        if (recoveryEmail != null){
            ContentValues cv = new ContentValues();
            cv.put(SipProfile.FIELD_RECOVERY_EMAIL, recoveryEmail);
            int updatedCount = SipProfile.updateProfile(context.getContentResolver(), SipProfile.getCurrentProfile(context).getId(), cv);
            Log.df(TAG, "saveAccountSettings, updated count=%d", updatedCount);
        }
    }

    /**
     * Process json policy - this may be initiated during login or when license is changed
     * @param context
     * @return true if policy was updated, false otherwise
     */
    public boolean processAppServerPolicy(Context context){
        Log.vf(TAG, "processAppServerPolicy, policy=[%s]", currentUsagePolicy);
        if (TextUtils.isEmpty(currentUsagePolicy)){
            return false;
        }

        try {
            AccountingPermissionUpdater permissionManager = new AccountingPermissionUpdater(context);
            return permissionManager.updatePermissionsFromPolicy(new JSONObject(currentUsagePolicy));
        } catch (JSONException e) {
            Log.ef(TAG, e, "error during parsing json policies");
        }
        return false;
    }


    private void saveSupportAccounts(Context context) {
        if (supportAccounts == null || supportAccounts.length == 0){
            return;
        }
        PreferencesManager prefs = new PreferencesManager(context);
        // take first only
        prefs.setString(PhonexConfig.SIP_SUPPORT_ACCOUNT, supportAccounts[0]);
    }

    private void saveTrialEventData(Context context) {
        if (trialEventLog == null){
            return;
        }

        ContentValues[] cvs = new ContentValues[trialEventLog.getEvents().size()];
        for(int i=0; i<trialEventLog.getEvents().size(); i++){
            Event event = trialEventLog.getEvents().get(i);
            ContentValues cv = new ContentValues();
            cv.put(TrialEventLog.FIELD_TYPE, event.getType());
            cv.put(TrialEventLog.FIELD_DATE, event.getDate());
            cvs[i] = cv;
        }

        // recreate everything
        context.getContentResolver().delete(TrialEventLog.URI, null, null);
        Log.df(TAG, "%d events in trial event log", trialEventLog.getEvents().size());
        if (trialEventLog.getEvents().size() > 0) {
            context.getContentResolver().bulkInsert(TrialEventLog.URI, cvs);
        }
    }
}

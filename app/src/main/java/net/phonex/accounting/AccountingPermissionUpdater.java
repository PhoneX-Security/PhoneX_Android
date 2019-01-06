package net.phonex.accounting;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;

import net.phonex.accounting.entities.AccountingPermissionId;
import net.phonex.core.Constants;
import net.phonex.db.entity.AccountingPermission;
import net.phonex.db.entity.SipProfile;
import net.phonex.util.Log;
import net.phonex.util.guava.Lists;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Accounting permissions functions for updating database state when logging or processing permissions from server
 * Created by miroc on 8.10.15.
 */
public class AccountingPermissionUpdater {
    private static final String TAG = "AccountingPermissionUpdater";
    private final Context context;

    public AccountingPermissionUpdater(Context context) {
        this.context = context;
    }

    /**
     * Process "current_policy" permission field  (json format) from Subscriber table
     * @param currentPolicy
     */
    public boolean updatePermissionsFromPolicy(JSONObject currentPolicy){
        Log.v(TAG, "updatePermissionsFromPolicy");
        boolean updated = false;
        try {
            if (!currentPolicy.has("timestamp")){
                Log.wf(TAG, "No timestamp found in currentPolicy");
                return false;
            }

            Date timestamp = new Date(currentPolicy.getLong("timestamp") * 1000);
            SipProfile profile = SipProfile.getCurrentProfile(context);
            Log.inf(TAG, "Checking policy timestamps (new=%s, old=%s)", timestamp, profile.getCurrentPolicyDate());
            if (!timestamp.after(profile.getCurrentPolicyDate())){
                Log.inf(TAG, "Policy timestamp is not newer than stored current date, not updating");
                return false;
            }
            Log.inf(TAG, "Policy timestamp is newer, updating");

            List<AccountingPermission> policyPermissions = Lists.newArrayList();
            if (currentPolicy.has("subscriptions")){
                policyPermissions.addAll(
                        jsonPermissionsToEntities(currentPolicy.getJSONArray("subscriptions"), true)
                );
                updated = true;
            }

            if (currentPolicy.has("consumables")){
                policyPermissions.addAll(
                        jsonPermissionsToEntities(currentPolicy.getJSONArray("consumables"), false)
                );
                updated = true;
            }
            updatePermissionsDefinitions(policyPermissions);

            ContentValues cv = new ContentValues();
            cv.put(SipProfile.FIELD_CURRENT_POLICY_TIMESTAMP, timestamp.getTime());
            SipProfile.updateProfile(context.getContentResolver(), profile.getId(), cv);

        } catch (Exception e){
            Log.ef(TAG, e, "Exception in parsing permissions.");
        }
        return updated;
    }

    private List<AccountingPermission> jsonPermissionsToEntities(JSONArray entities, boolean isSubscriptions) throws JSONException {
        List<AccountingPermission> list = new ArrayList<>();

        for (int i=0; i<entities.length(); i++){

            AccountingPermission accPerm = jsonToPermissionEntity(entities.getJSONObject(i));
            accPerm.setSubscription(isSubscriptions ? 1 : 0);
            list.add(accPerm);
        }
        return list;
    }

    /**
     * Updates permissions definitions from current policy loaded from the account info.
     */
    private void updatePermissionsDefinitions(List<AccountingPermission> policyPermissions){
        Log.vf(TAG, "updatePermissionsDefinitions");

        if(policyPermissions == null || policyPermissions.size() == 0){
            Log.df(TAG, "updatePermissionsDefinitions; no permissions to update");
            return;
        }

        Set<AccountingPermissionId> permIdsUpdated = new HashSet<>();
        Set<AccountingPermissionId> permIdsInserted = new HashSet<>();
        Set<AccountingPermissionId> permIdsDeleted = new HashSet<>();

        Map<AccountingPermissionId, AccountingPermission> permMap = new HashMap<>();

        Set<AccountingPermissionId> permIdsToInsert = new HashSet<>();
        Set<AccountingPermissionId> policyPermIds = new HashSet<>();


        // Iterate permissions loaded from policy
        for(AccountingPermission perm : policyPermissions){
            AccountingPermissionId key = AccountingPermissionId.from(perm);
            permIdsToInsert.add(key);
            permMap.put(key, perm);
            policyPermIds.add(key);
        }

        // Iterate permissions loaded from database
//        List<AccountingPermission> dbPermissions = AccountingPermission.getAllByCompositeId(context.getContentResolver(), Lists.newArrayList(policyPermIds));
        ArrayList<ContentProviderOperation> operations = new ArrayList<>();

        List<AccountingPermission> dbPermissions = AccountingPermission.getAllByCompositeId(context.getContentResolver(), null);
        for(AccountingPermission dbPerm : dbPermissions){
            AccountingPermissionId dbPermId = AccountingPermissionId.from(dbPerm);
            permIdsToInsert.remove(dbPermId);

            if (!policyPermIds.contains(dbPermId)){
                // this perm id is not contained in the policy, delete and try the next one
//                String selection = String.format("%s=?", AccountingPermission.FIELD_ID);
                int deleted = context.getContentResolver().delete(ContentUris.withAppendedId(AccountingPermission.ID_URI_BASE, dbPerm.getId()), null, null);
                if (deleted > 0){
                    permIdsDeleted.add(dbPermId);
                }
                continue;
            }

            // Update existing record with current values defined by policy that are not changed locally.
//            try {
            AccountingPermission serverPerm = permMap.get(dbPermId);
            dbPerm.setValue(serverPerm.getValue());
            dbPerm.setValidFrom(serverPerm.getValidFrom());
            dbPerm.setValidTo(serverPerm.getValidTo());
            dbPerm.setSubscription(serverPerm.getSubscription());
            dbPerm.setName(serverPerm.getName());

            String selection = String.format("%s=?", AccountingPermission.FIELD_ID);

            ContentProviderOperation operation = ContentProviderOperation.newUpdate(AccountingPermission.URI)
                    .withValues(dbPerm.getDbContentValues())
                    .withSelection(selection, new String[]{String.valueOf(dbPerm.getId())})
                    .build();
            operations.add(operation);
            permIdsUpdated.add(dbPermId);

//                int changed = context.getContentResolver().update(AccountingPermission.URI,
//                        dbPerm.getDbContentValues(), selection, );
//                Log.wf(TAG, "updatePermissionsDefinitions; AccountingPermission.URI update");
//                if (changed > 0){

//                }
//            } catch(Exception e){
//                Log.ef(TAG, e, "Could not update permission");
//            }
        }

        // Execute update operations
        try {
            ContentProviderResult[] contentProviderResults = context.getContentResolver().applyBatch(Constants.AUTHORITY, operations);
            if (contentProviderResults != null){
                for (int i = 0;  i < contentProviderResults.length; i++){
                    Log.df(TAG, "mass update results = %s", contentProviderResults[i]);
                }
            }
        } catch (RemoteException | OperationApplicationException e) {
            Log.ef(TAG, e, "Unable to do massive update");
        }


        // All records were updated, now insert new ones, both local and remote views.
        for(AccountingPermissionId curId : permIdsToInsert){
            // Remote view.
            try {
                AccountingPermission serverPerm = permMap.get(curId);
                serverPerm.setLocalView(0);
                serverPerm.setSpent(0);

                Log.wf(TAG, "updatePermissionsDefinitions; AccountingPermission.URI insert");
                Uri uri = context.getContentResolver().insert(AccountingPermission.URI, serverPerm.getDbContentValues());
                if(uri != null){
                    permIdsInserted.add(curId);
                }
            } catch(Exception e){
                Log.ef(TAG, e, "Could not insert permission to db");
            }

            // Local view.
            try {
                AccountingPermission serverPerm = permMap.get(curId);
                serverPerm.setLocalView(1);
                serverPerm.setSpent(0);

                Log.wf(TAG, "updatePermissionsDefinitions; AccountingPermission.URI insert2");
                Uri uri = context.getContentResolver().insert(AccountingPermission.URI, serverPerm.getDbContentValues());
                if(uri != null){
                    permIdsInserted.add(curId);
                }
            } catch(Exception e){
                Log.ef(TAG, e, "Could not insert permission to database");
            }
        }

        // post processing
        onServerPolicyUpdate(permIdsInserted, permIdsUpdated, permIdsDeleted);
    }

    // Load existing from database, update existing, insert new.
    // Trigger server change if any. Recalculate local view?
    public void storeCounterPermissions(List<AccountingPermission> permissions) {
        Log.vf(TAG, "storeCounterPermissions");
        Log.vf(TAG, "printing server permissions");
        PermissionLimits.printPerms(permissions);

        if(permissions == null || permissions.size() == 0){
            Log.df(TAG, "storeCounterPermissions; no permissions to update");
            return;
        }

        Set<AccountingPermissionId> permIdsUpdated = new HashSet<>();
        Set<AccountingPermissionId> permIdsInserted = new HashSet<>();
        Map<AccountingPermissionId, AccountingPermission> permMap = new HashMap<>();
        Set<AccountingPermissionId> permIdsToInsert = new HashSet<>();
        Set<AccountingPermissionId> permIdsToDelete = new HashSet<>();

        for(AccountingPermission perm : permissions){
            AccountingPermissionId key = AccountingPermissionId.from(perm);
            permIdsToInsert.add(key);
            permMap.put(key, perm);
        }

        // Load all permissions from database
        List<AccountingPermission> localPermissions = AccountingPermission.getAllByCompositeId(context.getContentResolver(), new ArrayList<>(permMap.keySet()));
        Log.vf(TAG, "printing local permissions");
        PermissionLimits.printPerms(localPermissions);
        for(AccountingPermission localPermission : localPermissions){
            AccountingPermissionId curId = AccountingPermissionId.from(localPermission);
            permIdsToInsert.remove(curId);

            try {
                AccountingPermission serverPerm = permMap.get(curId);
                String selection = String.format("%s=?", AccountingPermission.FIELD_ID);
                String[] selArgs = new String[]{String.valueOf(localPermission.getId())};

//                String selection = String.format("%s=? AND %s=? AND %s=?", AccountingPermission.FIELD_LOCAL_VIEW, AccountingPermission.FIELD_LIC_ID, AccountingPermission.FIELD_PERM_ID);
//                String[] selArgs = new String[]{String.valueOf(localPermission.getLocalView()), String.valueOf(serverPerm.getLicId()), String.valueOf(serverPerm.getPermId())};

                int changed = 0;
                if (localPermission.getLocalView() == 0){
                    // for server view, update
                    changed = context.getContentResolver().update(AccountingPermission.URI, serverPerm.getDbContentValues(), selection, selArgs);
                } else {
                    // for local view, check if server spent value is bigger and if, update
                    if (serverPerm.getSpent() > localPermission.getSpent()){
                        ContentValues cv = new ContentValues();
                        cv.put(AccountingPermission.FIELD_SPENT, serverPerm.getSpent());
                        changed = context.getContentResolver().update(AccountingPermission.URI, cv, selection, selArgs);
                    }
                }
                if (changed > 0){
                    permIdsUpdated.add(curId);
                }
            } catch (Exception e){
                Log.ef(TAG, e, "storeCounterPermissions; could not update permission %s", curId);
            }
        }

        // All records were updated, now insert new ones. -- TODO why?
        for(AccountingPermissionId curId : permIdsToInsert){
            AccountingPermission serverPerm = permMap.get(curId);
            serverPerm.setLocalView(0);
            Uri insert = context.getContentResolver().insert(AccountingPermission.URI, serverPerm.getDbContentValues());
            Log.wf(TAG, "storeCounterPermissions; AccountingPermission.URI insert");
            if (insert != null){
                permIdsInserted.add(curId);
            }
        }

        // post processing
        onServerPolicyUpdate(permIdsInserted, permIdsUpdated, permIdsToDelete);
    }

    private void onServerPolicyUpdate(Set<AccountingPermissionId> inserted, Set<AccountingPermissionId> updated, Set<AccountingPermissionId> deleted) {
        Log.vf(TAG, "onServerPolicyUpdate; insertedCount=%d, updatedCount=%d, deletedCount=%d", inserted.size(), updated.size(), deleted.size());

    }

    /**
     * {"license_id":"3551","permission_id":"1","permission":"outgoing_calls_seconds","value":"600","starts_at":1443657600,"expires_at":1446422399}
     */
    private static AccountingPermission jsonToPermissionEntity(JSONObject json) throws JSONException {

        AccountingPermission accPerm = new AccountingPermission();
        if (json.has("value")){
            accPerm.setValue(json.getLong("value"));
        }
        if (json.has("permission_id")){
            accPerm.setPermId(json.getLong("permission_id"));
        }
        if (json.has("license_id")){
            accPerm.setLicId(json.getLong("license_id"));
        }
        if (json.has("permission")){
            accPerm.setName(json.getString("permission"));
        }
        if (json.has("starts_at")){
            // value is in seconds
            accPerm.setValidFrom(new Date(json.getLong("starts_at") * 1000));
        }
        if (json.has("expires_at")){
            // value is in seconds
            accPerm.setValidTo(new Date(json.getLong("expires_at") * 1000));
        } else {
            // in case of consumable, no expiration is set, set up the maximum validation
            accPerm.setValidTo(new Date(Long.MAX_VALUE));
        }

        return accPerm;
    }

}

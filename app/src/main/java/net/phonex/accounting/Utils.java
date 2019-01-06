package net.phonex.accounting;

import net.phonex.db.entity.AccountingPermission;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by miroc on 16.11.15.
 */
public class Utils {
    public static AccountingPermission counterPermissionToEntity(JSONObject json) throws JSONException {
        AccountingPermission accPerm = new AccountingPermission();
        if (json.has("vol")) {
            accPerm.setSpent(json.getLong("vol"));
        }
        if (json.has("permId")) {
            accPerm.setPermId(json.getLong("permId"));
        }
        if (json.has("licId")) {
            accPerm.setLicId(json.getLong("licId"));
        }
        if (json.has("acount")) {
            accPerm.setAggregationCount(json.getLong("acount"));
        }
        if (json.has("aidFst")) {
            accPerm.setActionIdFirst(json.getLong("aidFst"));
        }
        if (json.has("ctrFst")) {
            accPerm.setActionCtrFirst(json.getLong("ctrFst"));
        }
        if (json.has("aidLst")) {
            accPerm.setActionIdLast(json.getLong("aidLst"));
        }
        if (json.has("ctrLst")) {
            accPerm.setActionCtrLast(json.getLong("aidLst"));
        }
        //dcreated and dmodif are skipped, not used at the moment
        return accPerm;
    }
}

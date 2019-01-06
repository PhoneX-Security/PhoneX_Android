package net.phonex.accounting;

import android.content.Context;

import net.phonex.db.entity.AccountingLog;
import net.phonex.db.entity.AccountingPermission;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.soap.RunnableWithException;
import net.phonex.soap.SOAPHelper;
import net.phonex.soap.entities.AccountingSaveRequest;
import net.phonex.soap.entities.AccountingSaveResponse;
import net.phonex.util.Log;

import org.jivesoftware.smack.util.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by miroc on 8.10.15.
 */
public class AccountingLogUpdaterCall implements RunnableWithException {
    private static final String TAG = "AccountingLogUpdaterCall";

    private SOAPHelper soap;
    private Exception thrownException = null;
    private Context context;
    private String xmppResourceString;

    public AccountingLogUpdaterCall(Context context, String xmppResourceString) {
        soap = new SOAPHelper(context);
        this.context = context;
        this.xmppResourceString = xmppResourceString;
    }

    /**
     *  {"astore":{
     *     "res":"abcdef123",
     *      "permissions":1,      (optional, if set to 1 AFFECTED permissions are returned)
     *      "aggregate":1,        (optional, if set to 1 AFFECTED aggregate records are returned)
     *      "records":[
     *          {"type":"c.os", "aid":1443185424488, "ctr":1, "vol": "120", "ref":"ed4b607e48009a34d0b79fe70f521cde"},
     *          {"type":"c.os", "aid":1443185524488, "ctr":2, "vol": "10", "perm":{"licId":123, "permId":1}},
     *          {"type":"m.om", "aid":1443185624488, "ctr":3, "vol": "120", "perm":{"licId":123, "permId":2}},
     *          {"type":"m.om", "aid":1443185724488, "ctr":4, "vol": "10", "ag":1, "aidbeg":1443185724488},
     *          {"type":"f.id", "aid":1443185824488, "ctr":5, "vol": "1"}
     *     ]
     * }}
     */
    private void prepareRequest(AccountingSaveRequest request) throws JSONException {
        JSONObject astore = new JSONObject();
        astore.put("res", xmppResourceString);
        astore.put("permissions", 1);

        JSONArray records = new JSONArray();

        String sortOrder = AccountingLog.FIELD_ACTION_ID + ", " + AccountingLog.FIELD_ACTION_COUNTER;
        List<AccountingLog> logs = AccountingLog.getAll(context.getContentResolver(), null, sortOrder);
        for (AccountingLog log : logs){
            JSONObject record = new JSONObject();
            record.put("type", log.getType());
            record.put("aid", log.getActionId());
            record.put("ctr", log.getActionCounter());
            record.put("vol", log.getAmount());

            if (log.getPermId() != null && log.getLicId() != null){
                JSONObject permObj = new JSONObject();
                permObj.put("permId", (long) log.getPermId());
                permObj.put("licId", (long) log.getLicId());
                record.put("perm", permObj);
            }

            if (StringUtils.isNotEmpty(log.getAref())){
                record.put("ref", log.getAref());
            }

            records.put(record);
        }
        astore.put("records", records);

        JSONObject json = new JSONObject();
        json.put("astore", astore);

        String respBody = json.toString();
        Log.inf(TAG, "requestBody=[%s]", respBody);
        request.setRequestBody(respBody);
    }

    /**
     *  "astore":{
     *          "topaid":1443185824488,
     *          "topctr":5,
     *          "permissions:"[
     *              {permission_1}, {permission_2}, ..., {permission_m}
     *          ],
     *          "aggregate":[
     *              {ag_1,} {ag_2}, ..., {ag_n}
     *          ]
     *     }
     */
    private void processResponse(AccountingSaveResponse response) throws JSONException {
        String jsonResponse = response.getResponseBody();
        Log.inf(TAG, "response body = [%s]", jsonResponse);
        JSONObject json = new JSONObject(jsonResponse);

        JSONObject store = json.getJSONObject("astore");
        // 1. Topaid + topctr present -> delete old log records as it was transmitted.
        if (store.has("topaid") && store.has("topctr")){
            long topaid = store.getLong("topaid");
            long topctr = store.getLong("topctr");
            Log.vf(TAG, "processResponse; topaid=%d, topctr=%d", topaid, topctr);
            AccountingLog.deleteRecordsOlderThan(topaid, topctr, context.getContentResolver());
        }

        // 2. Process permission dump, store to server view. Update / insert.
        if (store.has("permissions")){
            JSONArray permissions = store.getJSONArray("permissions");
            List<AccountingPermission> accPermList = new ArrayList<>();
            for (int i=0; i<permissions.length(); i++){
                accPermList.add(
                        Utils.counterPermissionToEntity(permissions.getJSONObject(i))
                );
            }

            AccountingPermissionUpdater permissionManager = new AccountingPermissionUpdater(context);
            permissionManager.storeCounterPermissions(accPermList);
        }
    }

    @Override
    public void run() {
        Exception toThrow = null;

        try {
            soap.init();

            Log.i(TAG, "Updating pairing request(s)");

            AccountingSaveRequest request = new AccountingSaveRequest();
            prepareRequest(request);

            SoapSerializationEnvelope envelope = soap.reqisterRequest(request, AccountingSaveResponse.class);
            AccountingSaveResponse response = (AccountingSaveResponse) soap.makeSoapRequest(envelope, "accountingLogUpdater");

            if (response == null){
                Log.w(TAG, "Empty response from server");
                return;
            }

            if (response.getErrCode() != 0) {
                Log.wf(TAG, "PairingRequestUpdateResponse finished with error code %d", response.getErrCode());
                return;
            }

            processResponse(response);

        } catch (Exception e) {
            Log.ef(TAG, e, "Exception");
            toThrow = e;
        }
        // if any exception was thrown
        thrownException = toThrow;
    }


    public Exception getThrownException() {
        return thrownException;
    }
}

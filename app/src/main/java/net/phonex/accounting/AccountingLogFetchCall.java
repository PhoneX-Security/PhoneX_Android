package net.phonex.accounting;

import android.content.Context;

import net.phonex.db.entity.AccountingPermission;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.soap.RunnableWithException;
import net.phonex.soap.SOAPHelper;
import net.phonex.soap.entities.AccountingFetchRequest;
import net.phonex.soap.entities.AccountingFetchResponse;
import net.phonex.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by miroc on 8.10.15.
 */
public class AccountingLogFetchCall implements RunnableWithException {
    private static final String TAG = "AccountingLogFetchCall";

    private SOAPHelper soap;
    private Exception thrownException = null;
    private Context context;
    private String xmppResourceString;

    public AccountingLogFetchCall(Context context, String xmppResourceString) {
        soap = new SOAPHelper(context);
        this.context = context;
        this.xmppResourceString = xmppResourceString;
    }


    /**
     * {"areq:"{
     *      "res":"abcdef123",
     *      "ares":["abcdef123"], (optional)
     *      "type":"aaggregate",  (optional, default is agregate. or: "arecords"),
     *      "timefrom":12335522,  (optional)
     *      "timeto":215241234,   (optional)
     *      "atype":["c.os"]      (optional)
     * }}
     * @param request
     * @throws JSONException
     */
    private void prepareRequest(AccountingFetchRequest request) throws JSONException {
        JSONObject areq = new JSONObject();
        areq.put("res", xmppResourceString);
        areq.put("permissions", 1);

        JSONObject json = new JSONObject();
        json.put("areq", areq);

        String respBody = json.toString();
        Log.inf(TAG, "requestBody=[%s]", respBody);
        request.setRequestBody(respBody);
    }

    /**
     * Response:{"afetch":{
     *      records:[{
     *          "res":"abcdef123",              (resource)
     *          "type": "c.os",
     *          "akey": "ar3jdDJ2910sa212d==",  (24char len, base64-encoded, aggregation bucket key)
     *          "dcreated": 1443472673397,      (date created, UTC milli)
     *          "dmodif": 1443472673397,        (date modified, UTC milli)
     *          "vol": 35,                      (aggregated value of the counter for given user:type)
     *          "aidFst": 1443472670397,        (first accounting log ID in this aggregate record)
     *          "ctrFst": 1,                    (first accounting log counter in this aggregate record)
     *          "aidLst": 1443472672397         (last accounting log ID in this aggregate record)
     *          "ctrLst": 13,                   (last accounting log counter in this aggregate record)
     *          "aperiod": 3600000,             (aggregation period of this record, in milliseconds)
     *          "acount": 5,                    (number of logs aggregated in this record)
     *          "astart": 144347260000          (start of the aggregation interval)
     *          "aref": "ed4b607e48009a34d0b79fe70f521cde"  (optional: reference of the accounting ID, if applicable)
     *      }, {rec_2},{rec_3},...,{rec_n}]
     *      permissions:[{
     *          "id": 123,              (internal DB ID)
     *          "permId": 1,            (permission ID)
     *          "licId": 133            (license ID)
     *          "name": "outgoing_calls_seconds"
     *          "akey": "ajkasb901ns02==" (aggregation key, can be ignored)
     *          "dcreated": 1443472673397,
     *          "dmodif": 1443472673397,
     *          "vol": 320,             (current value of the permission counter)
     *          "aidFst": 1443472670397,
     *          "ctrFst": 1,
     *          "aidLst": 1443472672397,
     *          "ctrLst": 13,
     *          "acount": 5             (number of log records aggregated in this permission counter)
     *      }, {permission_2}, {permission_3}, ..., {permission_m}]
     * }}
     * @param response
     * @throws JSONException
     */
    private void processResponse(AccountingFetchResponse response) throws JSONException {
        String jsonResponse = response.getResponseBody();
        Log.inf(TAG, "response body = [%s]", jsonResponse);
        JSONObject json = new JSONObject(jsonResponse);
        JSONObject afetch = json.getJSONObject("afetch");


        // 2. Process permission dump, store to server view. Update / insert.
        if (afetch.has("permissions")){
            JSONArray permissions = afetch.getJSONArray("permissions");
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

            AccountingFetchRequest request = new AccountingFetchRequest();
            prepareRequest(request);

            SoapSerializationEnvelope envelope = soap.reqisterRequest(request, AccountingFetchResponse.class);
            AccountingFetchResponse response = (AccountingFetchResponse) soap.makeSoapRequest(envelope, "accountingLogFetch");

            if (response == null){
                Log.w(TAG, "Empty response from server");
                return;
            }

            if (response.getErrCode() != 0) {
                Log.wf(TAG, "response finished with error code %d", response.getErrCode());
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

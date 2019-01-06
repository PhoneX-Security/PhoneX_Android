package net.phonex.soap.accountSettings;

import android.content.Context;

import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.soap.RunnableWithException;
import net.phonex.soap.SOAPHelper;
import net.phonex.soap.entities.AccountSettingsUpdateV1Request;
import net.phonex.soap.entities.AccountSettingsUpdateV1Response;
import net.phonex.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Soap call to update account settings on server
 * Implement getSettingsUpdateJson() method and set values to be updated in user's settings during the call
 * Created by miroc on 8.10.15.
 */
public abstract class AccountSettingsUpdateCall implements RunnableWithException {
    private static final String TAG = "AccountSettingsUpdateCall";

    private SOAPHelper soap;
    private Exception thrownException = null;

    public AccountSettingsUpdateCall(Context context) {
        soap = new SOAPHelper(context);
    }

    /**
     * Example of Json
     * {"settingsUpdate":{
     * "loggedIn":false,
     * "mutePush": 1452093836069,
     * "muteSound": 1452093836069,
     * "recoveryEmail": "test@gmail.com"
     * }}
     * @param request
     * @throws JSONException
     */
    private void prepareRequest(AccountSettingsUpdateV1Request request) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("settingsUpdate", getSettingsUpdateJson());

        String reqBody = json.toString();
        Log.inf(TAG, "requestBody=[%s]", reqBody);
        request.setRequestBody(reqBody);
    }

    protected abstract JSONObject getSettingsUpdateJson() throws JSONException;

    /**
     * @param response
     * @throws JSONException
     */
    private void processResponse(AccountSettingsUpdateV1Response response) throws JSONException {
        String jsonResponse = response.getResponseBody();
        Log.inf(TAG, "response body = [%s]", jsonResponse);
        // response body is empty at the moment
    }

    @Override
    public void run() {
        Exception toThrow = null;

        try {
            soap.init();

            Log.i(TAG, "Updating settings account");

            AccountSettingsUpdateV1Request request = new AccountSettingsUpdateV1Request();
            prepareRequest(request);

            SoapSerializationEnvelope envelope = soap.reqisterRequest(request, AccountSettingsUpdateV1Response.class);
            AccountSettingsUpdateV1Response response = (AccountSettingsUpdateV1Response) soap.makeSoapRequest(envelope, "accountSettingsUpdateCall");

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
        thrownException = toThrow;
    }

    public Exception getThrownException() {
        return thrownException;
    }
}

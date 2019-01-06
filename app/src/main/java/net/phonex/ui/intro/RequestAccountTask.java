package net.phonex.ui.intro;

import android.app.FragmentManager;
import android.content.Context;
import android.text.TextUtils;

import net.phonex.R;
import net.phonex.soap.BaseAsyncProgress;
import net.phonex.soap.BaseAsyncTask;
import net.phonex.ui.dialogs.ProgressDialogFragment;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.crypto.CertificatesAndKeys;
import net.phonex.util.crypto.MessageDigest;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

/**
* Created by miroc on 17.3.15.
*/
public class RequestAccountTask extends BaseAsyncTask<Void> {
    public static final String TAG = "RequestAccountTask";
    public static final String CHARS = "abcdefghjklmnpqrstuvwxyz23456789";

    public static final String SERVER_URL = "https://system.phone-x.net";
    public static final String CREATE_TRIAL_ACCOUNT_URL = SERVER_URL + "/account/trial";
    public static final String CREATE_BUSINESS_ACCOUNT_URL = SERVER_URL + "/account/business-account";

    private static final String REQUEST_VERSION = "1";
    private JSONObject response = null;
    private RequestTrialEventListener listener;
    private SSLContext sslContext;
    private String captcha;
    private String username;
    private String businessCode;

    private Type type = Type.TRIAL;

    /**
     * Pre-check of the code to verify parity before we do network operation
     * @param code
     * @return
     */
    public static boolean parityCheck(String code){
        final int modulo = 29;
        final int codeLength = 9;

        if (TextUtils.isEmpty(code) || code.length() != codeLength){
            return false;
        }

        int sum = 0;
        for (int i = 0; i < code.length(); i++){
            int pos = code.length() - i - 1;
            int value = CHARS.indexOf(code.charAt(pos));
            if (value == -1){
                return false;
            }

            sum += value * (i+1); // multiply by weight and add to sum
        }

        return sum % modulo == 0;
    }

    public interface RequestTrialEventListener{
        void reloadCaptcha();
        void onReceivedResponse(JSONObject response);
        void onError(String localizedErrorMessage);
    }

    public void setListener(RequestTrialEventListener listener) {
        this.listener = listener;
    }

    public RequestAccountTask(Context context, FragmentManager fragmentManager, ProgressDialogFragment mFragment) {
        super(context, fragmentManager, mFragment);
    }

    public RequestAccountTask(Context context) {
        super(context);
    }

    public void setSslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    /**
     * When setting up this task, call either this method or setBusinessCodeAccountParameters() to correctly initialize task parameters
     * @param captcha
     * @param username
     */
    public void setTrialAccountParameters(String captcha, String username){
        this.captcha = captcha;
        this.username = username;
        type = Type.TRIAL;
    }

    public void setBusinessCodeAccountParameters(String captcha, String username, String code){
        this.captcha = captcha;
        this.username = username;
        this.businessCode = code;
        type = Type.BUSINESS_CODE;
    }

    @Override
    protected Exception doInBackground(Void... params) {
        Log.df(TAG, "running RequestAccountTask");
        try {
            publishProgress(new BaseAsyncProgress(getContext().getString(R.string.trial_fragment_message)).setIndeterminate(Boolean.TRUE));
            makeRequest();
            return null;
        } catch (Exception ex){
            return ex;
        }
    }

    @Override
    protected void onPostExecute(Exception ex) {
        super.onPostExecute(ex);
        closeFragment(ex);

        if (ex==null){
            Log.df(TAG, "Request for account was made and we successfully received a response: %s", response.toString());
            if (listener!=null){
                listener.onReceivedResponse(response);
            }
        } else {
            Log.e(TAG, "Exception: request for account failed.", ex);
            if (listener != null){
                listener.reloadCaptcha();
                listener.onError(getContext().getString(R.string.p_problem_nonspecific));
            }
        }
    }


    private void makeRequest() throws Exception {
        Log.vf(TAG, "Verifying captcha [text=%s]", captcha);

        // setup connection using POST
        // URL depends on business businessCode presence
        HttpsURLConnection connection = prepareConnection(type == Type.TRIAL ? CREATE_TRIAL_ACCOUNT_URL : CREATE_BUSINESS_ACCOUNT_URL);
        Log.v(TAG, "HTTPS connection created");

        // send data to the server
        DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
        outputStream.writeBytes(prepareParametersForPost());
        outputStream.flush();
        Log.v(TAG, "Upload output stream flushed");

        // Responses from the server (businessCode and message)
        int responseCode = connection.getResponseCode();
        String responseMsg = connection.getResponseMessage();
        Log.vf(TAG, "Server Response Code: %s", responseCode);
        Log.vf(TAG, "Server Response Message: %s", responseMsg);

        // check http response businessCode
        switch (responseCode) {
            case 200:
            case 201:

                String responseText = readResponse(connection.getInputStream());
                try {
                    response = new JSONObject(responseText);
                } catch (JSONException ex){
                    Log.ef(TAG, "JSONParser error: enable to parse the text [text=%s].", responseText);
                    throw ex;
                }
                break;

            default:
                Log.ef(TAG, "Bad response businessCode [%d] when verifying account request", responseCode);
                throw new Exception("There was an error during communication with the server");
        }
        connection.disconnect();
    }

    private String prepareParametersForPost() throws Exception {
        // we rather send hashed IMEI - see PHON-260
        String imei = CertificatesAndKeys.getDeviceId(getContext());
        String hashedImei = MessageDigest.hashHexSha256(imei.getBytes());

        Map<String, String> m = new HashMap<>();
        m.put("captcha", captcha);
        m.put("imei", hashedImei);
        m.put("username", username);
        m.put("version", REQUEST_VERSION);
        if (type == Type.BUSINESS_CODE){
            m.put("bcode", businessCode);
        }

        List<NameValuePair> parameters = MiscUtils.makeNameValuePairs(m);

        String params = URLEncodedUtils.format(parameters, "utf-8");
        Log.vf(TAG, "Parameters for POST: %s", params);
        return params;
    }

    private String readResponse(InputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        return builder.toString();
    }

    private HttpsURLConnection prepareConnection(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setSSLSocketFactory(sslContext.getSocketFactory());
        conn.setRequestMethod("POST");

        conn.setUseCaches(false);
        conn.setDoInput(true);
        conn.setDoOutput(true);

        return conn;
    }

    public static enum Type {
        BUSINESS_CODE,
        TRIAL
    }
}

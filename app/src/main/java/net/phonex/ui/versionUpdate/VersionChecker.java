package net.phonex.ui.versionUpdate;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.text.TextUtils;

import net.phonex.pref.PreferencesConnector;
import net.phonex.ui.dialogs.NewVersionDialogFragment;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;

/**
 * PhoneX update checker (for lazy people who do not update app)
 * Created by miroc on 23.1.15.
 */
public class VersionChecker {
    private static final String TAG = "VersionChecker";

    private static final String URL_PHONEX_VERSION_CHECK = "https://www.phone-x.net/en/ajax/version";
    private static final String URL_CHECK_NEWEST_VERSION_CODE = URL_PHONEX_VERSION_CHECK + "?action=getNewestVersion";
    private static final String URL_CHECK_VERSION = URL_PHONEX_VERSION_CHECK + "?action=getVersion";

    private static final String PARAMETER_TYPE = "android";

    // Required preferences settings
    public static final String PREF_LAST_CHECK_TIMESTAMP = "net.phonex.ui.versionUpdate.last_check_timestamp";
    public static final String PREF_IGNORE_VERSION_CODE = "net.phonex.ui.versionUpdate.ignore_version_code";
    public static final String PREF_UPDATE_NOW_TIMESTAMP = "net.phonex.ui.versionUpdate.update_now_timestamp";
    public static final String PREF_RELEASE_NOTES_SHOWN = "net.phonex.ui.versionUpdate.release_notes_shown";

    private static final long CHECK_THRESHOLD = 1000*60*60*24; // 24 hours
//    private static final long CHECK_THRESHOLD = 1000*30; // 30s
    private static final long SHOW_NEWS_THRESHOLD = 1000*60*15; // 15mins

    private Activity activity;
    private Integer versionCode;
    private String versionName;
    private PreferencesConnector preferencesConnector;

    public VersionChecker(Activity activity) {
        this.activity = activity;
        try {
            PackageInfo packageInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            this.versionCode = packageInfo.versionCode;
            this.versionName = packageInfo.versionName;

        } catch (PackageManager.NameNotFoundException e) {
            Log.ef(TAG, e, "VersionChecker<init>; No such package found [%s]", activity.getPackageName());
        }
        preferencesConnector = new PreferencesConnector(activity);
    }

    public void checkVersion(){
        if (!showWhatsNewInCurrentVersion()){
            checkNewVersion();
        }
    }

    private void checkNewVersion() {
        if (versionCode == null){
            return;
        }
        long lastCheckTime = Long.parseLong(preferencesConnector.getString(PREF_LAST_CHECK_TIMESTAMP, "0"));
        Log.vf(TAG, "checkNewVersion; lastCheckTime [%d]", lastCheckTime);

        if ((System.currentTimeMillis() - lastCheckTime) > CHECK_THRESHOLD){
            Log.vf(TAG, "checkNewVersion; last check is older than threshold, initiating check task");

            preferencesConnector.setString(PREF_LAST_CHECK_TIMESTAMP, String.valueOf(System.currentTimeMillis()));

            try {
                new CheckNewVersionTask().execute();
            } catch (Exception e) {
                Log.ef(TAG, e, "Cannot initiate new check task");
            }
        }
    }

    /**
     *
     * @return true if release notes haven't been shown for current version yet (in this case, checkNewVersion() should NOT follow up)
     * otherwise false, which means checkNewVersion() should follow up
     */
    public boolean showWhatsNewInCurrentVersion(){

        boolean notesAlreadyShown = preferencesConnector.getBoolean(PREF_RELEASE_NOTES_SHOWN, false);
        Log.vf(TAG, "showWhatsNewInCurrentVersion; notesAlreadyShown [%s]", notesAlreadyShown);

        // 1. show release notes only once
        if (notesAlreadyShown){
            return false;
        }

        // 2. remember that news were shown + plus reset checkNewVersion counter
        preferencesConnector.setBoolean(PREF_RELEASE_NOTES_SHOWN, true);
        preferencesConnector.setString(PREF_LAST_CHECK_TIMESTAMP, String.valueOf(System.currentTimeMillis()));

        // 3. special case - if update happens within SHOW_NEWS_THRESHOLD from PREF_UPDATE_NOW_TIMESTAMP click, skip showing release notes (user has seen it while ago)
        long updateNowClickTimestamp = Long.parseLong(preferencesConnector.getString(PREF_UPDATE_NOW_TIMESTAMP, "0"));
        if ((System.currentTimeMillis() - updateNowClickTimestamp) <= SHOW_NEWS_THRESHOLD){
            Log.inf(TAG, "showWhatsNewInCurrentVersion; do not show release notes, app presumably updated manually by 'Update now'");
            return true; // true = do not check new version after
        }

        // 4. retrieve and show news
        try {
            new GetVersionInfoTask(versionCode, Locale.getDefault().getLanguage()).setAfterUpdate(true).execute();
        } catch (Exception e) {
            Log.ef(TAG, e, "showWhatsNewInCurrentVersion; cannot retrieve version [%d] information", versionCode);
        } finally {
            return true;
        }
    }

    private void onVersionInfoRetrieved(JSONObject responseJson, boolean afterUpdate){
        int newVersionCode;
        String newVersionName;
        String newReleaseNotes;
        boolean availableAtMarket;

        try {
            newVersionCode = responseJson.getInt("versionCode");
            newVersionName = responseJson.getString("versionName");
            newReleaseNotes = responseJson.getString("releaseNotes");
            availableAtMarket = responseJson.getBoolean("availableAtMarket");

        } catch (JSONException e) {
            Log.ef(TAG, e, "onVersionInfoRetrieved; Error retrieving values from JSON");
            return;
        }

        if (!availableAtMarket){
            Log.wf(TAG, "onVersionInfoRetrieved; version %d is still not available on the market, delaying update info dialog", newVersionCode);
            // Delay next check to CHECK_THRESHOLD/2
            preferencesConnector.setString(PREF_LAST_CHECK_TIMESTAMP, String.valueOf(System.currentTimeMillis() - CHECK_THRESHOLD/2));
            return;
        }

        if(activity != null && !activity.isFinishing()){
            FragmentManager fragmentManager = activity.getFragmentManager();
            if (fragmentManager==null){
                return;
            }

            NewVersionDialogFragment dialogFragment;
            if (afterUpdate){
                if (TextUtils.isEmpty(newReleaseNotes)){
                    Log.inf(TAG, "onVersionInfoRetrieved; after update - release notes are empty, probably bugfix, do not show update notification");
                    return;
                }

                dialogFragment = NewVersionDialogFragment.afterUpdateInstance(newVersionCode, newVersionName, newReleaseNotes);
            } else {
                dialogFragment = NewVersionDialogFragment.updateAvailableInstance(newVersionCode, newVersionName, newReleaseNotes);

            }
            dialogFragment.show(fragmentManager, "NewVersionDialogFragment");
        }
    }

    private HttpURLConnection prepareConnection(String pageUrl) throws IOException {
        URL url = new URL(pageUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");

        conn.setUseCaches(false);
        conn.setDoInput(true);
        conn.setDoOutput(true);

        return conn;
    }

    /**
     * Make sure response code in JSON is valid (returns true), otherwise false (and logs errors)
     */
    private boolean checkResponseCode(JSONObject response){
        try {
            // HTML like response codes
            int code = response.getInt("responseCode");
            Log.inf(TAG, "Response code received: %d", code);
            switch (code){
                case 200:
                    return true;
                case 401:
                    JSONArray jsonArray = response.getJSONArray("fields");
                    Log.ef(TAG, "Error 401: missing request fields [%s]", jsonArray);
                    break;
                case 402:
                    Log.e(TAG, "Error 402: Requested app version does not exist");
                    break;
                default:
                    Log.ef(TAG, "Error: %d: unknown error", code);
            }
        } catch (JSONException e) {
            Log.ef(TAG, e, "Failed to parse JSON response [%s]", response);
        }
        return false;
    }

    private int getIgnoredVersionCode(){
        return preferencesConnector.getInteger(PREF_IGNORE_VERSION_CODE, -1);
    }

    private String prepareParametersForPost(List<NameValuePair> parameters) {
        String params = URLEncodedUtils.format(parameters, "utf-8");
        Log.vf(TAG, "Parameters for POST: %s", params);
        return params;
    }

    private class CheckNewVersionTask extends ContactServerTask{
        public CheckNewVersionTask() throws Exception {
            super(URL_CHECK_NEWEST_VERSION_CODE, MiscUtils.makeNameValuePairs("type", PARAMETER_TYPE));
        }

        @Override
        protected void onPostExecute(Exception e) {
            if (e != null || !checkResponseCode(responseJson)){
                return;
            }
            try {
                int newestVersionCode = responseJson.getInt("versionCode");
                Log.vf(TAG,"CheckNewVersionTask; Retrieved version code [%d]; current version code [%d]", newestVersionCode, versionCode);
                if (newestVersionCode > versionCode){
                    Log.inf(TAG, "Newer version exists, get info");

                    if (getIgnoredVersionCode() == newestVersionCode){
                        Log.inf(TAG, "This version is ignored, do not update");
                        return;
                    }

                    new GetVersionInfoTask(newestVersionCode, Locale.getDefault().getLanguage()).execute();
                }
            } catch (JSONException e1) {
                Log.ef(TAG, e1, "Error parsing version code");
            } catch (Exception e1) {
                Log.ef(TAG, e1, "Error initializing getVersionInfo task");
            }
        }
    }

    private class GetVersionInfoTask extends ContactServerTask{
        private boolean afterUpdate = false;
        public GetVersionInfoTask(int versionCode, String locale) throws Exception {
            super(URL_CHECK_VERSION,
                    MiscUtils.makeNameValuePairs(
                            "versionCode", String.valueOf(versionCode),
                            "type", PARAMETER_TYPE,
                            "locale", locale
                    ));
        }

        public GetVersionInfoTask setAfterUpdate(boolean afterUpdate) {
            this.afterUpdate = afterUpdate;
            return this;
        }

        @Override
        protected void onPostExecute(Exception e) {
            if (e != null || !checkResponseCode(responseJson)){
                return;
            }
            Log.inf(TAG, "GetVersionInfoTask; Correct response received [%s]", responseJson);
            onVersionInfoRetrieved(responseJson, afterUpdate);
        }
    }

    /**
     * Make one HTTP request abstract task and parse JSON response
     */
    private abstract class ContactServerTask extends AsyncTask<Void, Void, Exception>{
        protected final String url;
        protected final List<NameValuePair> parameters;
        protected Integer responseCode;
        protected String responseMsg;
        protected JSONObject responseJson = null;

        protected ContactServerTask(String url, List<NameValuePair> postParameters) {
            this.url = url;
            this.parameters = postParameters;
        }

        @Override
        protected Exception doInBackground(Void... params) {
            HttpURLConnection connection = null;
            try {
                Log.vf(TAG, "HTTP connection created; URL [%s]", url);
                connection = prepareConnection(url);

                // send data to the server
                DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
                outputStream.writeBytes(prepareParametersForPost(parameters));
                outputStream.flush();
                Log.vf(TAG, "Upload output stream flushed; params [%s]", parameters);

                // Responses from the server (code and message)
                responseCode = connection.getResponseCode();
                responseMsg = connection.getResponseMessage();
                Log.vf(TAG, "Server Response Code: %s", responseCode);
                Log.vf(TAG, "Server Response Message: %s", responseMsg);

                // check http response code
                switch (responseCode) {
                    case 200:
                    case 201:

                        String responseText = readResponse(connection.getInputStream());
                        try {
                            responseJson = new JSONObject(responseText);
                        } catch (JSONException ex){
                            Log.ef(TAG, "JSONParser error: enable to parse the text [text=%s].", responseText);
                            throw ex;
                        }
                        break;

                    default:
                        Log.ef(TAG, "Bad response code [%d] when verifying trial request, url=%s", responseCode, url);
                        throw new Exception("There was an error during communication with the server");
                }

            } catch (Exception e) {
                return e;
            } finally {
                if (connection!=null){
                    connection.disconnect();
                }
            }

            return null;
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
    }
}

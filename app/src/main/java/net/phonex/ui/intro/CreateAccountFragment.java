package net.phonex.ui.intro;

import android.app.Activity;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import net.phonex.R;
import net.phonex.core.SipUri;
import net.phonex.events.NetworkStateReceiver;
import net.phonex.pub.a.Compatibility;
import net.phonex.rest.TLSUtils;
import net.phonex.ui.dialogs.AlertDialogFragment;
import net.phonex.ui.dialogs.ProgressDialogFragment;
import net.phonex.ui.interfaces.OnAccountCreatedListener;
import net.phonex.ui.interfaces.OnConnectivityChanged;
import net.phonex.util.LayoutUtils;
import net.phonex.util.Log;
import net.phonex.util.analytics.AnalyticsReporter;
import net.phonex.util.analytics.AppButtons;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

import butterknife.ButterKnife;
import butterknife.InjectView;

public class CreateAccountFragment extends Fragment implements OnConnectivityChanged, RequestAccountTask.RequestTrialEventListener {
    private static final String TAG = "CreateAccountFragment";

    public static final int RESPONSE_OK = 200;
    public static final int RESPONSE_ERR_MISSING_FIELDS = 400;
    public static final int RESPONSE_ERR_BAD_CAPTCHA = 401;
    public static final int RESPONSE_ERR_TRIAL_ALREADY_CREATED = 402;
    public static final int RESPONSE_ERR_EXISTING_USERNAME = 404;
    public static final int RESPONSE_ERR_USERNAME_BAD_FORMAT = 405;
    // business code related
    public static final int RESPONSE_ERR_BAD_BUSINESS_CODE = 406;
    public static final int RESPONSE_ERR_ALREADY_USED_BUSINESS_CODE = 407;
    public static final int RESPONSE_ERR_INVALID_VERSION = 408;
    public static final int RESP_ERR_EXPIRED_BUSINESS_CODE = 409;


    public static final String REGEX_USERNAME = "(?i)^[a-z0-9_-]{3,18}$";

    public static final String CAPTCHA_URL = "https://system.phone-x.net/trial/captcha";

    private static final int CAPTCHA_HEIGHT = 80;

    @InjectView(R.id.webCaptcha) ImageView captchaImageView;
    @InjectView(R.id.loading_progress) LinearLayout captchaLoadIndicator;
    @InjectView(R.id.captchaText) EditText captchaEditText;
    @InjectView(R.id.username) EditText usernameEditText;
    @InjectView(R.id.license_code) EditText licenseCodeEditText;

    @InjectView(R.id.add_license_code) CheckBox addLicenseCode;

    @InjectView(R.id.resetButton) ImageButton resetButton;
    @InjectView(R.id.submitButton) Button submitButton;

    @InjectView(R.id.my_toolbar) Toolbar toolbar;

    private Bitmap captcha;

    private SSLContext sslContext;
    private NetworkStateReceiver networkStateReceiver;
    private int captchaHeightInPixels;

    private int mShortAnimationDuration;

    CaptchaLoader captchaLoaderTask = new CaptchaLoader();

    private OnAccountCreatedListener onTrialCreatedListener = null;

    public static CreateAccountFragment newInstance() {
        CreateAccountFragment fragment = new CreateAccountFragment();
        return fragment;
    }

    public CreateAccountFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (activity instanceof OnAccountCreatedListener){
            onTrialCreatedListener = (OnAccountCreatedListener) activity;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        onTrialCreatedListener = null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        networkStateReceiver = new NetworkStateReceiver(this);

        captchaHeightInPixels = LayoutUtils.dp2Pix(getResources(), CAPTCHA_HEIGHT);
        // Retrieve and cache the system's default "short" animation time.
        mShortAnimationDuration = getResources().getInteger(android.R.integer.config_mediumAnimTime);

        try {
            sslContext = TLSUtils.prepareSSLContext(getActivity());
        } catch (Exception e) {
            Log.e(TAG, "Cannot initialize SSLWebContext" , e);
        }
    }

    @Override
    public void onNetworkConnected() {
        if (captcha == null)
        {
            reloadCaptcha();
        }
    }

    @Override
    public void onNetworkDisconnected() {
        // nothing
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_create_trial, container, false);
        ButterKnife.inject(this, view);
        submitButton.setOnClickListener(submitListener);

        toolbar.setTitle(R.string.account_creation);

        // License code
        addLicenseCode.setOnCheckedChangeListener((buttonView, isChecked) -> licenseCodeEditText.setVisibility(isChecked ? View.VISIBLE : View.GONE));

        if (Compatibility.shouldAllowBusinessCodeTextWatcher()){
            licenseCodeEditText.addTextChangedListener(new DashCodeTextWatcher());
        }

        // Manually setting icon + action
        toolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
        toolbar.setNavigationOnClickListener(v -> getFragmentManager().popBackStack());

        resetButton.setOnClickListener(view1 -> {
            AnalyticsReporter.from(this).buttonClick(AppButtons.REFRESH_CAPTCHA);
            reloadCaptcha();

        });

        // restore captcha image
        if (savedInstanceState != null){
            Parcelable bitmapParcelable = savedInstanceState.getParcelable("captcha");
            if (bitmapParcelable != null){
                captcha = (Bitmap) bitmapParcelable;
                onCaptchaLoaded();
            }
        }
        if (captcha == null){
            reloadCaptcha();
        }

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            getActivity().registerReceiver(networkStateReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        } catch (Exception ex) {
            Log.e(TAG, "Cannot hook NetworkStateReceiver", ex);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            getActivity().unregisterReceiver(networkStateReceiver);
        } catch (Exception ex) {
            Log.e(TAG, "Cannot unhook NetworkStateReceiver", ex);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (captcha != null){
            outState.putParcelable("captcha", captcha);
        }
    }

    private boolean validateUsername(String username){
        return Pattern.matches(REGEX_USERNAME, username);
    }

    private View.OnClickListener submitListener = new View.OnClickListener() {
        public void onClick(View v) {


            String enteredCaptcha = captchaEditText.getText().toString();
            String username = usernameEditText.getText().toString().trim().toLowerCase();
            String licenseCode = null;

            if (addLicenseCode.isChecked()){
                licenseCode = licenseCodeEditText.getText().toString().replace("-", "").toLowerCase();

                if (!RequestAccountTask.parityCheck(licenseCode)){
                    licenseCodeEditText.setError(getString(R.string.intro_bcode_non_valid));
                    return;
                }
            }

            Log.df(TAG, "Clicking Submit button, captcha=%s", enteredCaptcha);

            if (!TextUtils.isEmpty(enteredCaptcha)){
                AnalyticsReporter.from(CreateAccountFragment.this).buttonClick(AppButtons.CREATE_ACCOUNT);

                if (!validateUsername(username)){
                    Log.df(TAG, "Username %s is not valid one.", username);
                    usernameEditText.setError(getString(R.string.invalid_username));
                    return;
                }

                LayoutUtils.hideSwKeyboard(getActivity());

                ProgressDialogFragment progressFragment = new ProgressDialogFragment();
                progressFragment.setTitle(getActivity().getString(R.string.trial_fragment_title));

                RequestAccountTask task = new RequestAccountTask(getActivity(), getActivity().getFragmentManager(), progressFragment);
                task.setSslContext(sslContext);
                task.setListener(CreateAccountFragment.this);

                if (licenseCode != null){
                    AnalyticsReporter.from(CreateAccountFragment.this).buttonClick(AppButtons.APPLY_CODE);
                    task.setBusinessCodeAccountParameters(enteredCaptcha, username, licenseCode);
                    task.showFragmentAndExecute();
                } else {
                    task.setTrialAccountParameters(enteredCaptcha, username);
                    task.showFragmentAndExecute();
                }
            }
        }
    };

    public synchronized void reloadCaptcha(){
        if (captchaLoaderTask != null && (captchaLoaderTask.getStatus() == AsyncTask.Status.RUNNING)){
            return;
        }

        captchaEditText.setText("");

        captchaLoadIndicator.setVisibility(View.VISIBLE);
        captchaImageView.setVisibility(View.GONE);

        LayoutUtils.crossfade(captchaImageView, captchaLoadIndicator, mShortAnimationDuration);

        captchaLoaderTask = new CaptchaLoader();
        captchaLoaderTask.execute();
    }

    private void onCaptchaLoaded(){
        captchaImageView.setImageBitmap(captcha);

        LayoutUtils.crossfade(captchaLoadIndicator, captchaImageView, mShortAnimationDuration);
    }

    // Callback after request was made
    public void onReceivedResponse(JSONObject response){
//        {
//            version: 1,
//            responseCode; 200
//            [username: "miroc",
//            sip: "miroc@phone-x.net",
//            password: "aaaaaaaa",
//            expiryDate: "2014-07-24"]
//        }
        boolean isCreated = false;
        try {
            // HTML like response codes
            int code = response.getInt("responseCode");
            Log.inf(TAG, "Response code received: %d", code);
            switch (code){
                case RESPONSE_OK:
                    final String username = response.getString("username");
                    final String sip = response.getString("sip");
                    final String password = response.getString("password");

                    Date expiration = null;
                    if (response.has("expirationTimestamp")){
                        expiration = new Date(response.getLong("expirationTimestamp") * 1000);
                    }

                    isCreated = true;
                    onResponse200(username, sip, password, expiration);

                    break;
                case RESPONSE_ERR_MISSING_FIELDS:
                    Log.e(TAG, "Error 400: Server indicates that it cannot process request - missing/invalid fields");
                    //AlertDialogFragment.alert(getActivity(), R.string.p_error, R.string.p_problem_nonspecific);
                    break;
                case RESPONSE_ERR_BAD_CAPTCHA:
                    Log.e(TAG, "Error 401: Bad captcha");
                    captchaEditText.setError(getActivity().getString(R.string.trial_error_401));
                    captchaEditText.requestFocus();
                    break;
                case RESPONSE_ERR_TRIAL_ALREADY_CREATED:
                    Log.e(TAG, "Error 402: User has already activated Trial account");
                    AlertDialogFragment.alert(getActivity(), R.string.p_problem, R.string.trial_error_402);
                    break;
                case RESPONSE_ERR_EXISTING_USERNAME:
                    Log.e(TAG, "Error 404: Requested username already exists");
                    usernameEditText.setError(getActivity().getString(R.string.trial_error_404));
                    usernameEditText.requestFocus();
                    break;
                case RESPONSE_ERR_USERNAME_BAD_FORMAT:
                    Log.e(TAG, "Error 405: Username bad format");
                    // this shouldn't happen since there is a validation also on client side
                    break;
                case RESPONSE_ERR_BAD_BUSINESS_CODE:
                    Log.e(TAG, "Error 406: Bad business code");
                    AlertDialogFragment
                            .newInstance(getString(R.string.p_problem), getString(R.string.intro_bcode_non_valid))
                            .setNeutralButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    getFragmentManager().popBackStack();
                                }
                            })
                            .show(getFragmentManager(), "alert");

                    break;
                case RESPONSE_ERR_ALREADY_USED_BUSINESS_CODE:
                    Log.e(TAG, "Error 407: Already used business code");
                    AlertDialogFragment
                            .newInstance(getString(R.string.p_problem), getString(R.string.intro_bcode_already_used))
                            .setNeutralButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    getFragmentManager().popBackStack();
                                }
                            })
                            .show(getFragmentManager(), "alert");
                    break;

                case RESPONSE_ERR_INVALID_VERSION:
                    Log.e(TAG, "Error 408: Invalid version");
                    break;
                    // this shouldn't really happen
                case RESP_ERR_EXPIRED_BUSINESS_CODE:
                    Log.e(TAG, "Error 409: Expired business code");
                    AlertDialogFragment
                            .newInstance(getString(R.string.p_problem), getString(R.string.intro_bcode_error_409_expired))
                            .setNeutralButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    getFragmentManager().popBackStack();
                                }
                            })
                            .show(getFragmentManager(), "alert");
                    break;
                default:
                    Log.ef(TAG, "Error: %d: unknown error", code);
                    AlertDialogFragment.alert(getActivity(), R.string.p_problem, R.string.p_problem_bad_response);
                    break;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse JSON response", e);
            AlertDialogFragment.alert(getActivity(), R.string.p_error, R.string.p_problem_bad_response);
        } finally {
            if (!isCreated)
                reloadCaptcha();
        }
    }

    @Override
    public void onError(String localizedErrorMessage) {
        AlertDialogFragment.alert(getActivity(), getString(R.string.p_problem), localizedErrorMessage);
    }

    private void onResponse200(final String username, final String sip, final String password, final Date expiration){

        StringBuilder maskedPass = new StringBuilder(password.substring(0,2));
        for (int i=0; i <password.length()-2; i++){
            maskedPass.append('*');
        }
        Log.inf(TAG, "Trial successfully created [username=%s, sip=%s, password=%s]", username, sip, maskedPass.toString());


        if (getActivity()==null || getActivity().getFragmentManager()==null){
            return;
        }

        String domain = SipUri.parseSipContact(sip).domain;
        SerializableLoginParams loginParams = new SerializableLoginParams(sip, username, password, domain);
        if (expiration != null){
            String formattedTime = (new SimpleDateFormat("HH:mm")).format(expiration);

            DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.LONG, Locale.getDefault());
            String formattedDate = formattedTime + ", " + dateFormat.format(expiration);
            loginParams.setExpiryDate(formattedDate);
        }

        if (onTrialCreatedListener != null){
            onTrialCreatedListener.onAccountCreated(loginParams);
        }
    }

    private class CaptchaLoader extends AsyncTask<Void, Void, Exception> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // display loader
            captchaLoadIndicator.setVisibility(View.VISIBLE);
        }

        @Override
        protected Exception doInBackground(Void... params) {
            try {
                loadCaptcha();
                return null;
            } catch (Exception ex){
                return ex;
            }
        }

        private void loadCaptcha() throws UnrecoverableKeyException, CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
            URL url = new URL(CAPTCHA_URL + "?" + "height=" + captchaHeightInPixels);
            HttpsURLConnection httpsConn = (HttpsURLConnection) url.openConnection();
            httpsConn.setSSLSocketFactory(sslContext.getSocketFactory());

            httpsConn.getDoInput();
            httpsConn.setUseCaches (false);
            httpsConn.connect();

            captcha = BitmapFactory.decodeStream(httpsConn.getInputStream());
        }

        @Override
        protected void onPostExecute(Exception ex) {
            if (ex==null){
                onCaptchaLoaded();
            } else {
                Log.ef(TAG, ex, "Exception: Cannot load captcha image from %s, ex: %s", CAPTCHA_URL, ex);
            }
        }
    }

}

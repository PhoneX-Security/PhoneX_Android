package net.phonex.ui.intro;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import net.phonex.PhonexSettings;
import net.phonex.R;
import net.phonex.core.Intents;
import net.phonex.core.SipUri;
import net.phonex.login.FullLogin;
import net.phonex.pref.PreferencesManager;
import net.phonex.pub.a.Compatibility;
import net.phonex.pub.parcels.GenericTaskProgress;
import net.phonex.rest.KeyServerApi;
import net.phonex.rest.TLSUtils;
import net.phonex.rest.entities.passreset.CodeRecoveryResponse;
import net.phonex.rest.entities.passreset.CodeRecoveryVerificationResponse;
import net.phonex.service.xmpp.XmppManager;
import net.phonex.ui.dialogs.AlertDialogFragment;
import net.phonex.ui.dialogs.GenericProgressDialogFragment;
import net.phonex.ui.interfaces.OnAccountCreatedListener;
import net.phonex.util.Log;
import net.phonex.util.LoginUtils;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import butterknife.ButterKnife;
import butterknife.InjectView;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;


public class ForgotPasswordFragment extends Fragment {
    private static final String TAG = "ForgotPasswordFragment";
    private static final String EXTRA_SIP = "extra_sip";
    private static final String EXTRA_RECOVERY_CODE = "extra_recovery_code";

    private final static String INTENT_URL_CALL_PROGRESS = "net.phonex.ui.intro.ForgotPasswordFragment_URL_CALL_PROGRESS";

    @InjectView(R.id.my_toolbar)
    Toolbar toolbar;
    @InjectView(R.id.login)
    EditText loginInput;
    @InjectView(R.id.send_recovery_code)
    Button sendRecoveryCodeButton;
    @InjectView(R.id.recovery_code)
    EditText recoveryCodeInput;
    @InjectView(R.id.login_with_recovery)
    Button loginWithRecoveryCodeButton;

    private Handler h = new Handler();
    private KeyServerApi keyServerApi;
    private PreferencesManager prefs;

    private OnAccountCreatedListener listener;
    private String argSip;
    private String argRecoveryCode;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (activity instanceof OnAccountCreatedListener){
            listener = (OnAccountCreatedListener) activity;
        } else {
            throw new ClassCastException(activity.toString()
                    + " must implement OnAccountCreatedListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    public static ForgotPasswordFragment newInstance() {
        return new ForgotPasswordFragment();
    }

    /**
     * Pre-filled with sip and recovery code (clicking on a link in e-mail)
     * @param sip
     * @param recoveryCode
     * @return
     */
    public static ForgotPasswordFragment newInstance(String sip, String recoveryCode) {
        ForgotPasswordFragment fr = new ForgotPasswordFragment();
        Bundle args = new Bundle();
        args.putString(EXTRA_SIP, sip);
        args.putString(EXTRA_RECOVERY_CODE, recoveryCode);
        fr.setArguments(args);
        return fr;
    }


    public ForgotPasswordFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        Bundle arguments = getArguments();
        if (arguments != null){
            String argSip = arguments.getString(EXTRA_SIP);
            String argRecoveryCode = arguments.getString(EXTRA_RECOVERY_CODE);
            if (!TextUtils.isEmpty(argSip) && !TextUtils.isEmpty(argRecoveryCode)){
                this.argSip = argSip;
                this.argRecoveryCode = argRecoveryCode;
            }
        }

        prefs = new PreferencesManager(getActivity());

        try {
            keyServerApi = TLSUtils.prepareKeyServerApi(getActivity());
        } catch (UnrecoverableKeyException | CertificateException | NoSuchAlgorithmException | IOException | KeyStoreException e) {
            Log.ef(TAG, e, "Unable to initialize keyServer rest api");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_forgot_password, container, false);
        ButterKnife.inject(this, view);
        toolbar.setTitle(R.string.password_reset);
        // Manually setting icon + action
        toolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
        toolbar.setNavigationOnClickListener(v -> getFragmentManager().popBackStack());

        recoveryCodeInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                loginWithRecoveryCodeButton.setEnabled(s.length() > 0);
            }
        });

        if (Compatibility.shouldAllowBusinessCodeTextWatcher()){
            recoveryCodeInput.addTextChangedListener(new DashCodeTextWatcher());
        }

        sendRecoveryCodeButton.setOnClickListener(v -> {
            if (getActivity() == null) {
                return;
            }
            String login = getLogin();
            if (TextUtils.isEmpty(login)) {
                return;
            }

            String sip = login + "@phone-x.net";
            String xmppResource = XmppManager.getXmppResourceString(getActivity());
            String appVersion = PhonexSettings.getUniversalApplicationDesc(getActivity());

            showProgressDialog();
            h.postDelayed(() -> {
                // AuxJSON has to be there, at least empty string
                keyServerApi.sendRecoveryCode(sip, xmppResource, appVersion, "", new ProcessCodeRecovery());
            }, 500); // delay at least 500 ms, so progress bar doesn't blink
        });

        loginWithRecoveryCodeButton.setOnClickListener(v -> {
            if (getActivity() == null) {
                return;
            }
            String login = getLogin();
            if (TextUtils.isEmpty(login)) {
                Toast.makeText(getActivity(), "Empty username.", Toast.LENGTH_SHORT).show();
            }
            String sip = login + "@phone-x.net";
            initiateLogin(sip, recoveryCodeInput.getText().toString());
        });

        return view;
    }

    private void initiateLogin(String sip, String recoveryCode){
        final String canonicalRc = recoveryCode.replace("-", "").toLowerCase();

        String xmppResource = XmppManager.getXmppResourceString(getActivity());
        String appVersion = PhonexSettings.getUniversalApplicationDesc(getActivity());

        Log.inf(TAG, "Sending recovery code; sip=%s, code=%s", sip, recoveryCode);

        showProgressDialog();
        h.postDelayed(() -> {
            // AuxJSON has to be there, at least empty string
            keyServerApi.verifyRecoveryCode(sip, xmppResource, appVersion, canonicalRc, "", new ProcessCodeRecoveryVerification(sip));
        }, 500); // delay at least 500 ms, so progress bar doesn't blink
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // remembered login
        if (LoginUtils.isLoginRemembered(prefs) && TextUtils.isEmpty(loginInput.getText().toString())){
            loginInput.setHint(R.string.saved_username);
        }

        if (!TextUtils.isEmpty(argRecoveryCode) && !TextUtils.isEmpty(argSip)){
            Log.df(TAG, "Prefilled values from link, sip=%s, recoveryCode=%s", argSip, argRecoveryCode);
            SipUri.ParsedSipContactInfos parsedSip = SipUri.parseSipContact(argSip);
            loginInput.setText(parsedSip.userName);
            if (argRecoveryCode.length() == 9){
                // if standard length, add dashes
                recoveryCodeInput.setText(String.format("%s-%s-%s", argRecoveryCode.substring(0,3), argRecoveryCode.substring(3,6), argRecoveryCode.substring(6)));
            } else {
                recoveryCodeInput.setText(argRecoveryCode);
            }
            recoveryCodeInput.requestFocus();
        }
    }

    private void showProgressDialog(){
        GenericProgressDialogFragment.newInstance(null, INTENT_URL_CALL_PROGRESS, true).show(getFragmentManager(), "tag");
    }

    private void closeProgressDialog(){
        Intent intent = new Intent(INTENT_URL_CALL_PROGRESS);
        intent.putExtra(Intents.EXTRA_GENERIC_PROGRESS, GenericTaskProgress.doneInstance());
        LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(intent);
    }

    private class ProcessCodeRecoveryVerification implements Callback<CodeRecoveryVerificationResponse> {
        private final String sip;

        public ProcessCodeRecoveryVerification(String sip) {
            this.sip = sip;
        }

        @Override
        public void success(CodeRecoveryVerificationResponse codeRecoveryVerificationResponse, Response response) {
            Log.inf(TAG, "ProcessCodeRecoveryVerification; success response=%s", codeRecoveryVerificationResponse);
            closeProgressDialog();
            switch (codeRecoveryVerificationResponse.getStatusCode()) {
                case 0: // success
                    String temporaryPassword = codeRecoveryVerificationResponse.getNewPassword();
                    SerializableLoginParams loginParams = SerializableLoginParams.from(sip, temporaryPassword);
                    if (listener!=null){
                        Log.vf(TAG, "positiveButton; onClick, calling onAccountCreated() with params [%s] ", loginParams);
                        listener.onLoginInitiated(loginParams);
                    } else {
                        Log.wf(TAG, "positiveButton; onClick, listener is null");
                    }

                    break;
                case -3: // empty recovery email - invalid user or no recovery email set
                    AlertDialogFragment.alert(getActivity(), null, getString(R.string.pass_reset_err_no_email_or_user));
                    break;
                case -5: // RecoveryTooOftenIp
                case -4: // RecoveryTooOften
                    AlertDialogFragment.alert(getActivity(), null, getString(R.string.pass_reset_err_verification_too_often));
                    break;
                case -10: // InvalidRecoveryCode
                    AlertDialogFragment.alert(getActivity(), null, getString(R.string.pass_reset_err_invalid_code));
                    break;
                case -1: //general error
                default: // everything else
                    AlertDialogFragment.alert(getActivity(), null, getString(R.string.pass_reset_err_general));
                    break;
            }
        }

        @Override
        public void failure(RetrofitError error) {
            Log.ef(TAG, error, "Retrofit error during process code recovery verification");
            closeProgressDialog();
            AlertDialogFragment.alert(getActivity(), null, getString(R.string.p_problem_nonspecific));
        }
    }

    private class ProcessCodeRecovery implements Callback<CodeRecoveryResponse> {
        @Override
        public void success(CodeRecoveryResponse codeRecoveryResponse, Response response) {
            Log.inf(TAG, "ProcessCodeRecovery; success, response=%s", codeRecoveryResponse);
            closeProgressDialog();

            switch (codeRecoveryResponse.getStatusCode()){
                case 0: // success
                    long validToTimestampMillis = codeRecoveryResponse.getValidTo();
                    long validityDuration = validToTimestampMillis - System.currentTimeMillis();

                    String text = String.format(getString(R.string.pass_reset_success), FullLogin.formatMillisDuration(validityDuration, getActivity().getResources()));
                    AlertDialogFragment.alert(getActivity(), null, text);
                    break;
                case -3: // empty recovery email - invalid user or no recovery email set
                    AlertDialogFragment.alert(getActivity(), null, getString(R.string.pass_reset_err_no_email_or_user));
                    break;
                case -5: // RecoveryTooOftenIp
                case -4: // RecoveryTooOften
                    AlertDialogFragment.alert(getActivity(), null, getString(R.string.pass_reset_err_too_often));
                    break;

                case -1: //general error
                default: // everything else
                    AlertDialogFragment.alert(getActivity(), null, getString(R.string.pass_reset_err_general));
                    break;
            }
        }

        @Override
        public void failure(RetrofitError error) {
            Log.ef(TAG, error, "Retrofit error during process code recovery");
            closeProgressDialog();
            AlertDialogFragment.alert(getActivity(), null, getString(R.string.p_problem_nonspecific));
        }
    }

    private String getLogin(){
        if (LoginUtils.isLoginRemembered(prefs) && TextUtils.isEmpty(loginInput.getText().toString())){
            return LoginUtils.getLastLogin(prefs);
        } else {
            return loginInput.getText().toString().trim();
        }
    }
}
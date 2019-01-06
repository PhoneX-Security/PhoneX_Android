package net.phonex.ui.intro;

import android.app.Fragment;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import net.phonex.PhonexSettings;
import net.phonex.R;
import net.phonex.core.SipUri;
import net.phonex.pref.PreferencesConnector;
import net.phonex.pref.PreferencesManager;
import net.phonex.pub.parcels.CredsData;
import net.phonex.util.LayoutUtils;
import net.phonex.util.Log;
import net.phonex.util.LoginUtils;
import net.phonex.util.analytics.AnalyticsReporter;
import net.phonex.util.analytics.AppButtons;

import butterknife.ButterKnife;
import butterknife.InjectView;

public class IntroSetupFragment extends Fragment{
	
	private static final String THIS_FILE = "IntroSetupFragment";		
	private static final String KEY_LOGIN = "login";
	private static final String KEY_PASSWD = "passwd";
	private static final String KEY_STATE = "state";
	private static final String KEY_SAVED = "saved";
	private static final long FORM_STATE_PRESERVE_TMEOUT = 5000; //1000 * 60 * 5; // hold state of input variables 5 minutes max
	private Bundle savedState = null;
	private long formStateSavedTime = 0;

    @InjectView(R.id.setup_submit) Button submitButton;
    @InjectView(R.id.create_account) Button createAccountButton;
    @InjectView(R.id.password) EditText mPassword;
	@InjectView(R.id.login) EditText mLogin;
	@InjectView(R.id.remember_login) CheckBox mRememberLogin;

    @InjectView(R.id.logo) ImageView logo;

    @InjectView(R.id.text_password_reset) TextView passwordReset;

	private CredsData creds;

    private PreferencesManager prefs;

    private String versionName;

	@Override
    public void onActivityCreated(Bundle savedInstanceState) {
    	super.onActivityCreated(savedInstanceState);
    }

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		Log.vf(THIS_FILE, "onCreateView; savedInstance=%s; this=%s", savedInstanceState, this);
		View view = inflater.inflate(R.layout.fragment_intro_setup, container, false);
        ButterKnife.inject(this, view);

		submitButton.setOnClickListener(v -> onConnectClick());

        createAccountButton.setOnClickListener(v -> {
            AnalyticsReporter.from(getActivity()).buttonClick(AppButtons.NEW_ACCOUNT);
            Fragment newFragment = CreateAccountFragment.newInstance();
            if (getFragmentManager() != null) {
                getFragmentManager().beginTransaction()
                        .replace(R.id.fragment_content, newFragment, IntroActivity.CREATE_TRIAL_FRAGMENT_TAG)
                        .addToBackStack(null)
                        .commit();
            }
        });

        passwordReset.setOnClickListener(v -> {
            Fragment newFragment = ForgotPasswordFragment.newInstance();
            if (getFragmentManager() != null) {
                getFragmentManager().beginTransaction()
                        .replace(R.id.fragment_content, newFragment, IntroActivity.FORGOT_PASSWORD_FRAGMENT_TAG)
                        .addToBackStack(null)
                        .commit();
            }
        });

		//remember login
		prefs = new PreferencesManager(getActivity());
		mPassword.setOnEditorActionListener(mEditorSubmitListener);

		// Add version number
		TextView ver = (TextView) view.findViewById(R.id.phonex_text);
		String phonexDesc = getString(R.string.intro_phonex);
		PackageInfo pinfo = PreferencesConnector.getCurrentPackageInfos(getActivity());
		if(pinfo != null) {
			phonexDesc = String.format(phonexDesc, pinfo.versionName);
            versionName = pinfo.versionName;
		} else {
			phonexDesc = String.format(phonexDesc, "");
		}
        logo.setLongClickable(true);
        logo.setOnLongClickListener(v -> {
            if (versionName != null)
                Toast.makeText(getActivity(), versionName, Toast.LENGTH_SHORT).show();
            return true;
        });

        // On small screens text
        if (ver != null){
            ver.setText(phonexDesc);
        }

		/* If the Fragment was destroyed in between (screen rotation), we need to recover the savedState first */
        /* However, if it was not, it stays in the instance from the last onDestroyView() and we don't want to overwrite it */
        if(savedInstanceState != null && savedState == null)
            savedState = savedInstanceState.getBundle(KEY_STATE);

        // Also check for time limit for form preservation
        if(savedState != null && savedState.containsKey(KEY_SAVED) && (System.currentTimeMillis() - savedState.getLong(KEY_SAVED)) < FORM_STATE_PRESERVE_TMEOUT){
            mLogin.setText(savedState.getCharSequence(KEY_LOGIN));
            mPassword.setText(savedState.getCharSequence(KEY_PASSWD));
            savedState = null;
        } else {
        	setDefaultFormFields();
        }

        // Check if we have some data prepared in activity from auto credentials intent
        checkCreds();
	    return view;
	  }

    /**
	 * Check creds structure for credentials. If view was already prepared and there are some
	 * credentials, not too old (1 minute validity), it is copied to appropriate TextEdit fields
	 * on intro fragment
	 */
	private void checkCreds(){
		if (       mLogin!=null
				&& mPassword!=null
				&& creds!=null
				&& creds.l!=null
				&& creds.l.length()>0
				&& creds.l.length()<33
				&& creds.p!=null
				&& creds.p.length()>0
				&& creds.p.length()<33
				&& System.currentTimeMillis() <= (creds.when + 1000*60)){


        	final String l = creds.l.replaceAll("[^\\x20-\\x7e]", "").replaceAll("\\p{C}", "");
        	final String p = creds.p.replaceAll("[^\\x20-\\x7e]", "").replaceAll("\\p{C}", "");
        	if (!TextUtils.isEmpty(l) && !TextUtils.isEmpty(p)){
        		mLogin.setText(l);
        		mPassword.setText(p);

        		Log.v(THIS_FILE, "Credentials were set from creds structure");
        	}
        }
	}

    @Override
    public void onStart() {
        super.onStart();

        // remembered login
        if (LoginUtils.isLoginRemembered(prefs) && TextUtils.isEmpty(mLogin.getText().toString())){
            mLogin.setHint(R.string.saved_username);
            mPassword.requestFocus();
            mRememberLogin.setChecked(true);
        }
    }

    private void setDefaultFormFields(){
		mPassword.setText("");
    	mLogin.setText("");
	}

    private final OnEditorActionListener mEditorSubmitListener = (v, actionId, event) -> {
        onConnectClick();
        return true;
    };

    private void onConnectClick() {
        LayoutUtils.hideSwKeyboard(getActivity());
        AnalyticsReporter.from(this).buttonClick(AppButtons.LOGIN);
        if (!sanitizeFormParameters()) {
            return;
        }

        final String login = getLogin();
        final String userDomain = "phone-x.net"; // by default
        final String sip = login + "@" + userDomain;
        final String pass = mPassword.getText().toString();

        // If remembered save the user
        LoginUtils.rememberLogin(prefs, mRememberLogin.isChecked(), login);
        if (getActivity()!=null){
            ((IntroActivity) getActivity()).login(sip, pass, userDomain);
        } else {
            Log.wf(THIS_FILE, "onConnectClick; IntroActivity is null, cannot initiate login");
        }
    }

    private String getLogin(){
        if (LoginUtils.isLoginRemembered(prefs) && TextUtils.isEmpty(mLogin.getText().toString())){
            return LoginUtils.getLastLogin(prefs);
        } else {
            return mLogin.getText().toString().trim();
        }
    }

    /**
     * On auth check started, sanitize form parameters.
     */
    private boolean sanitizeFormParameters(){
        // Sanitize username
        String login = mLogin.getText().toString().trim();
        if (!LoginUtils.isLoginRemembered(prefs) || !TextUtils.isEmpty(login)){

            if (TextUtils.isEmpty(login)){
                Toast.makeText(getActivity(), R.string.p_error_empty_login,  Toast.LENGTH_SHORT).show();
                return false;
            }

            // If login name is entered with domain, try to fix it.
            login = login.toLowerCase();
            try {
                SipUri.ParsedSipUriInfos mytsinfo = SipUri.parseSipUri(SipUri.getCanonicalSipContact(login, true));
                if (!TextUtils.isEmpty(mytsinfo.domain)){
                    login = login.replace("@"+mytsinfo.domain, "").trim();
                }
            } catch(Exception ex){
                // Nothing...
            }

            // Update password field - trim spaces. Usually the extra spaces are common source of errors.
            mLogin.setText(login);
        }


        // Sanitize password
        final String passw = mPassword.getText().toString().trim();
        if (TextUtils.isEmpty(passw)){
            Toast.makeText(getActivity(), R.string.p_error_empty_password,  Toast.LENGTH_SHORT).show();
            return false;
        }
        mPassword.setText(passw);

        return true;
    }

	@Override
    public void onDestroyView() {
       super.onDestroyView();
       savedState = saveState();
    }

	private Bundle saveState() { /* called either from onDestroyView() or onSaveInstanceState() */
       Bundle state = new Bundle();
       state.putCharSequence(KEY_LOGIN, mLogin.getText());
       state.putCharSequence(KEY_PASSWD, mPassword.getText());
       state.putLong(KEY_SAVED, System.currentTimeMillis());
       return state;
   }

	@Override
    public void onSaveInstanceState(Bundle outState) {
       super.onSaveInstanceState(outState);
       Log.vf(THIS_FILE, "onSaveInstance; bundle=%s; this=%s", outState, this);
       /* If onDestroyView() is called first, we can use the previously savedState but we can't call saveState() anymore */
       /* If onSaveInstanceState() is called first, we don't have savedState, so we need to call saveState() */
       /* => (?:) operator inevitable! */
       outState.putBundle(KEY_STATE, savedState != null ? savedState : saveState());
   }

	@Override
	public void onPause() {
		Log.vf(THIS_FILE, "onPause; this=%s", this);

		// Store time when this view was paused, to preserve form values just for 5 minutes
		this.formStateSavedTime = System.currentTimeMillis();
		super.onPause();
	}

	@Override
	public void onResume() {
        super.onResume();
		Log.vf(THIS_FILE, "onResume; this=%s", this);

		// Check if onPause was called, time limit for form values preservation
		if (formStateSavedTime != 0 && (System.currentTimeMillis() - formStateSavedTime) > FORM_STATE_PRESERVE_TMEOUT){
			Log.v(THIS_FILE, "Time for preservation form data expired, cleaning...");

			setDefaultFormFields();
		}
	}

	public CredsData getCreds() {
		return creds;
	}

	public void setCreds(CredsData creds) {
		this.creds = creds;
		this.checkCreds();
	}
}
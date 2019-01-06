package net.phonex.ui.preferences;

import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import net.phonex.R;
import net.phonex.core.Intents;
import net.phonex.pref.PhonexConfig;
import net.phonex.db.entity.SipProfile;
import net.phonex.pub.parcels.GenericTaskProgress;
import net.phonex.soap.accountSettings.AccountSettingsUpdateCall;
import net.phonex.soap.accountSettings.SetRecoveryEmailCall;
import net.phonex.ui.dialogs.AlertDialogFragment;
import net.phonex.ui.dialogs.EnterPasswordDialogFragment;
import net.phonex.ui.dialogs.GenericProgressDialogFragment;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.android.StatusbarNotifications;

import butterknife.ButterKnife;
import butterknife.InjectView;

public class ChangeRecoveryEmailFragment extends Fragment{
	private static final String TAG = "ChangeRecoveryEmailFragment";
    private static final java.lang.String INTENT_SOAP_CALL_PROGRESS = ChangeRecoveryEmailFragment.class.getCanonicalName() + "SOAP_CALL_PROGRESS";

    @InjectView(R.id.save_button) Button saveButton;
	@InjectView(R.id.missing_warning) TextView missingWarningText;
	@InjectView(R.id.recovery_email) EditText recoveryEmailInput;

    private SipProfile sipProfile;
    private PasswordEnteredReceiver passwordEnteredReceiver;
    private Handler h;

    public static void showNotificationIfMissing(Context context, SipProfile sipProfile) {
        if (context == null || sipProfile == null){
            Log.wf(TAG, "showNotificationIfMissing, sip profile or context null");
            return;
        }
        boolean noRecoveryEmail = TextUtils.isEmpty(sipProfile.getRecoveryEmail());
        boolean warningShown = PhonexConfig.getBooleanPref(context, PhonexConfig.RECOVERY_EMAIL_MISSING_WARNING_SHOWN, false);
        Log.df(TAG, "showNotificationIfMissing, recoveryEmail=%s, warningShown=%s", sipProfile.getRecoveryEmail(), warningShown);
        if(!warningShown && noRecoveryEmail){
            PhonexConfig.setBooleanPref(context, PhonexConfig.RECOVERY_EMAIL_MISSING_WARNING_SHOWN, true);
            new StatusbarNotifications(context).notifyMissingRecoveryEmail();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        sipProfile = SipProfile.getCurrentProfile(getActivity());
        passwordEnteredReceiver = new PasswordEnteredReceiver();
        h = new Handler();
    }

    @Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_change_recovery_email, container, false);
		ButterKnife.inject(this, view);

        Log.df(TAG, "SipProfile = %s", sipProfile);

        if (TextUtils.isEmpty(sipProfile.getRecoveryEmail())){
            missingWarningText.setVisibility(View.VISIBLE);
        } else {
            recoveryEmailInput.setText(sipProfile.getRecoveryEmail());
            missingWarningText.setVisibility(View.GONE);
        }

        saveButton.setOnClickListener(v -> {
            String newRecoveryEmail = recoveryEmailInput.getText().toString().trim();
            String oldRecoveryEmail = sipProfile.getRecoveryEmail();
            // if empty changed to empty -> do nothing
            if (TextUtils.isEmpty(newRecoveryEmail) && TextUtils.isEmpty(oldRecoveryEmail)) {
                return;
            }

            // allow only empty string (deleting email) or valid email
            if (!MiscUtils.isValidEmailOrEmptyString(newRecoveryEmail)) {
                recoveryEmailInput.setError(getActivity().getString(R.string.invalid_email));
                recoveryEmailInput.requestFocus();
                return;
            }
            if (!newRecoveryEmail.equals("") && newRecoveryEmail.equalsIgnoreCase(oldRecoveryEmail)) {
                recoveryEmailInput.setError(getActivity().getString(R.string.recovery_email_has_not_changed));
                recoveryEmailInput.requestFocus();
                return;
            }

            LocalBroadcastManager.getInstance(getActivity()).registerReceiver(passwordEnteredReceiver, new IntentFilter(EnterPasswordDialogFragment.INTENT_PASS_PROMPT_ENTERED));

            EnterPasswordDialogFragment f = new EnterPasswordDialogFragment();
            f.show(getFragmentManager(), "dialog");

        });

        return view;
    }

    private void onPasswordCorrect(){
        Log.inf(TAG, "correct password entered");
        String newRecoveryEmail = recoveryEmailInput.getText().toString().trim();
        if(!MiscUtils.isValidEmailOrEmptyString(newRecoveryEmail)){
            return;
        }
        showProgressDialog();
        h.postDelayed(() -> {
            new StatusbarNotifications(getActivity()).cancelMissingRecoveryEmailNotif();
            new SaveRecoveryEmailTask().execute(newRecoveryEmail);
        }, 500); // delay at least 500 ms, so progress bar doesn't blink
    }

    private void showProgressDialog(){
        GenericProgressDialogFragment.newInstance(null, INTENT_SOAP_CALL_PROGRESS, true).show(getFragmentManager(), "tag");
    }

    private void closeProgressDialog(){
        Intent intent = new Intent(INTENT_SOAP_CALL_PROGRESS);
        intent.putExtra(Intents.EXTRA_GENERIC_PROGRESS, GenericTaskProgress.doneInstance());
        LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(intent);
    }

    private class SaveRecoveryEmailTask extends AsyncTask<String, Void, Exception> {

        @Override
        protected Exception doInBackground(String... params) {
            Log.df(TAG, "Running SaveRecoveryEmailTask");
            if (sipProfile == null){
                return new IllegalStateException("Sip profile is null, cannot save");
            } else if (getActivity() == null){
                return new IllegalStateException("getActivity is null, cannot save");
            }
            String recoveryEmail = params[0];

            ContentValues cv = new ContentValues();
            cv.put(SipProfile.FIELD_RECOVERY_EMAIL, recoveryEmail);
            int resultCount = SipProfile.updateProfile(getActivity().getContentResolver(), sipProfile.getId(), cv);
            Log.inf(TAG, "Updating local sip profile with recovery email, result count=%d", resultCount);
            SetRecoveryEmailCall call = new SetRecoveryEmailCall(getActivity(), recoveryEmail);
            call.run();
            return call.getThrownException();
        }

        @Override
        protected void onPostExecute(Exception e) {
            if (getActivity() == null){
                return;
            }
            closeProgressDialog();

            if (e == null){
                Toast.makeText(getActivity(), R.string.recovery_email_saved, Toast.LENGTH_SHORT).show();
                getActivity().finish();
                // success
            } else {
                Log.ef(TAG, e, "Error in UpdateAccountWithRecoveryEmail");
                AlertDialogFragment.alert(getActivity(), null, getString(R.string.p_problem_nonspecific));
            }
        }
    }

    private class PasswordEnteredReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            // unregister itself
            LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(this);

            boolean passwordCorrect = intent.getBooleanExtra(EnterPasswordDialogFragment.INTENT_EXTRA_PASS_CORRECT, false);
            if (!passwordCorrect){
                Toast.makeText(getActivity(), R.string.incorrect_password, Toast.LENGTH_LONG).show();
            } else {
                onPasswordCorrect();
            }
        }
    }
}
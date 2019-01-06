package net.phonex.ui.lock;

import android.view.View;

import net.phonex.ui.lock.util.PinHelper;
import net.phonex.ui.lock.view.PinputView;
import net.phonex.ui.lock.view.PinputView.OnCommitListener;

import net.phonex.R;
import net.phonex.util.Log;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;

/**
 * Created by miroc on 10.12.14.
 */
class VerifyPinViewController extends BaseViewController {
    private static final String KEY_INCORRECT_PIN_ATTEMPTS = "com.venmo.pin.incorrect_pin_attempts";
    private static final String TAG = "VerifyPinViewController";

    VerifyPinViewController(PinFragment f, View v) {
        super(f, v);
    }

    @Override
    void initUI() {
        String verify = String.format(
                context.getString(R.string.verify_n_digit_pin));
        headerText.setText(verify);
    }

    @Override
    OnCommitListener provideListener() {
        return new OnCommitListener() {
            @Override
            public void onPinCommit(PinputView view, final String submission) {
                validate(submission);
            }
        };
    }

    protected void validate(final String submission) {
        boolean isValid =  PinHelper.doesMatchPin(context, submission);
        handleValidation(isValid);
    }

    private void handleValidation(boolean isValid) {
        if (isValid) {
            resetIncorrectPinCount();
            mPinFragment.notifyValid();
        } else {
            incrementFailedAttempts();
            pinputView.showErrorAndClear();
        }
    }

    private void resetIncorrectPinCount() {
        getDefaultSharedPreferences(context).edit()
                .putInt(KEY_INCORRECT_PIN_ATTEMPTS, 0)
                .commit();
    }

    private void incrementFailedAttempts() {
        int failedAttempts = getIncorrectPinAttempts() + 1;
        int maxTries = getConfig().maxTries();
        boolean attemptsDepleted = maxTries > 0 && failedAttempts >= maxTries;
        Log.inf(TAG, "Incrementing failed attempts, current count [%d], max tries [%d], depleted [%s]", failedAttempts, maxTries, String.valueOf(attemptsDepleted));
        getDefaultSharedPreferences(context).edit()
                .putInt(KEY_INCORRECT_PIN_ATTEMPTS, attemptsDepleted ? 0 : failedAttempts)
                .commit();

        if (attemptsDepleted){
            mPinFragment.notifyTriesDepleted(maxTries);
        }
    }

    private int getIncorrectPinAttempts() {
        return getDefaultSharedPreferences(context).getInt(KEY_INCORRECT_PIN_ATTEMPTS, 0);
    }
}

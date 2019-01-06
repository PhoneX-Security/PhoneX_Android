package net.phonex.ui.dialogs;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.text.InputType;

import com.afollestad.materialdialogs.MaterialDialog;

import net.phonex.R;
import net.phonex.util.Log;
import net.phonex.util.crypto.CertificatesAndKeys;

/**
 * Dialog fragment requesting user password - result is broadcasted via LocalBroadcastManager
 * Created by miroc on 10.2.15.
 */
public class EnterPasswordDialogFragment extends AlertDialogFragment {
    private static final String TAG = "EnterPasswordDialogFragment";

    public static final String INTENT_PASS_PROMPT_ENTERED = "net.phonex.ui.dialogs.PASS_PROMPT_ENTERED";
    public static final String INTENT_EXTRA_PASS_CORRECT = "extra_pass_correct";

    public Dialog onCreateDialog(Bundle savedInstanceState) {
        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                .title(R.string.pin_reset_enter_pass)
                .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
                .input(null, null, (dialog, input) -> {

                    boolean correctPass = CertificatesAndKeys.verifyUserPass(getActivity(), input.toString());
                    Log.inf(TAG, "EnterPasswordDialogFragment; pass entered, correct=%s", correctPass);
                    Intent intent = new Intent(INTENT_PASS_PROMPT_ENTERED);
                    intent.putExtra(INTENT_EXTRA_PASS_CORRECT, correctPass);
                    LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(intent);

                })
                .positiveText(R.string.save)
                .negativeText(R.string.cancel);
        return builder.build();
    }
}
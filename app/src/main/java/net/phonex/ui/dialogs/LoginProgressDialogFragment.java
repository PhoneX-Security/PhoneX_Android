package net.phonex.ui.dialogs;

import android.content.DialogInterface;
import android.content.Intent;

import net.phonex.core.Intents;
import net.phonex.util.MiscUtils;

/**
 * Created by Matus on 10-Sep-15.
 */
public class LoginProgressDialogFragment extends ProgressDialogFragment {
    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        MiscUtils.sendBroadcast(getActivity(), new Intent(Intents.ACTION_CANCEL_LOGIN));
    }
}

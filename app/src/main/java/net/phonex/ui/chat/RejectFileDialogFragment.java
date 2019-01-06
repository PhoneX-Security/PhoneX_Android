package net.phonex.ui.chat;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import com.afollestad.materialdialogs.AlertDialogWrapper;

import net.phonex.R;
import net.phonex.core.Constants;
import net.phonex.service.messaging.MessageManager;
import net.phonex.ui.dialogs.AlertDialogFragment;
import net.phonex.util.Log;

/**
 * File rejection confirmation dialog.
 */
public class RejectFileDialogFragment extends AlertDialogFragment {
    private static final String TAG = "RejectFileDialogFragment";
    private static final String EXTRA_MSGID = "msgid";
    private Long messageId;

    public static RejectFileDialogFragment newInstance(Long messageId) {
        final RejectFileDialogFragment fr = new RejectFileDialogFragment();
        Bundle args = new Bundle();
        args.putLong(EXTRA_MSGID, messageId);
        fr.setArguments(args);
        return fr;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        messageId = getArguments().getLong(EXTRA_MSGID);
    }

    public Dialog onCreateDialog(Bundle savedInstanceState) {
        LayoutInflater inflater = getActivity().getLayoutInflater();
//        View titleView = initTitleView(inflater, false, getString(R.string.reject_file));
        View bodyView = initBodyView(inflater, getString(R.string.reject_confirm));
//        FrameLayout fl = (FrameLayout) titleView.findViewById(R.id.body);
//        fl.addView(bodyView);

        AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(getActivity());
        builder.setView(bodyView);
        builder.setTitle(getString(R.string.reject_file));
//        builder.setView(titleView);
        builder.setOnKeyListener(this);

        builder.setNegativeButton(getActivity().getString(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dismiss();
            }
        });

        builder.setPositiveButton(getActivity().getString(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    final Context ctxt = getActivity().getApplicationContext();
                    MessageManager.confirmTransfer(ctxt, messageId, false);

                } catch (Throwable t) {
                    Log.ef(TAG, t, "Unable to confirm file rejection for msg: %d", messageId);
                }
            }
        });

        return builder.create();
    }
}

package net.phonex.ui.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;

import net.phonex.R;
import net.phonex.ui.interfaces.OnAccountCreatedListener;
import net.phonex.ui.intro.SerializableLoginParams;
import net.phonex.util.Log;

public class TrialCreatedDialogFragment extends AlertDialogFragment {
    private static final String EXTRA_LOGIN_PARAMS = "extra_login_params";
    private static final String TAG = "TrialCreatedDialogFragment";
    private OnAccountCreatedListener listener;

    public static TrialCreatedDialogFragment newInstance(SerializableLoginParams loginParams) {
        TrialCreatedDialogFragment fragment = new TrialCreatedDialogFragment();

        Bundle args = new Bundle();
        args.putSerializable(EXTRA_LOGIN_PARAMS, loginParams);

    	fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (activity instanceof OnAccountCreatedListener){
            listener = (OnAccountCreatedListener) activity;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    protected View initBodyView(LayoutInflater inflater, CharSequence sip, CharSequence password){
        View bodyView = inflater.inflate(R.layout.trialdialog_body, null);
        TextView sipNameView = (TextView) bodyView.findViewById(R.id.sipName);
        sipNameView.setText(sip);
        TextView passwordTextView = (TextView) bodyView.findViewById(R.id.password);
        passwordTextView.setText(password);
        return bodyView;
    }

    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final SerializableLoginParams loginParams = (SerializableLoginParams) getArguments().getSerializable(EXTRA_LOGIN_PARAMS);
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View bodyView = initBodyView(inflater, loginParams.username, loginParams.password);

        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                .title(R.string.trial_created_title)
                .customView(bodyView, false)
                .positiveText(R.string.trial_continue_login)
                .autoDismiss(false);


        builder.callback(new MaterialDialog.ButtonCallback() {
            @Override
            public void onPositive(MaterialDialog dialog) {
                if (listener!=null){
                    Log.vf(TAG, "positiveButton; onClick, calling onAccountCreated() with params [%s] ", loginParams);
                    listener.onLoginInitiated(loginParams);
                } else {
                    Log.wf(TAG, "positiveButton; onClick, listener is null");
                }
                dialog.dismiss();
            }
        });

        MaterialDialog dialog = builder.build();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnKeyListener(this);
        return dialog;
    }

}

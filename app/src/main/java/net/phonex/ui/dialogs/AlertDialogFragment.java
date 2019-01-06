package net.phonex.ui.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.afollestad.materialdialogs.AlertDialogWrapper;

import net.phonex.R;

/**
 * Base fragment for displaying simple content or requiring action from the user 
 *
 * displays AlertDialog, use instead of direct call
 * use: AlertDialogFragment.newInstance("title", "message").show(fragmentManager, "TAG");
 *
 * alternative easier + safer way:
 * use2 (alert icon): AlertDialogFragment.alert(fragmentActivity, titleId, msgId);
 * use3 (info icon): AlertDialogFragment.info(fragmentActivity, titleId, msgId);
 * clickListener can be added
 * @author miroc
 *
 */
public class AlertDialogFragment extends DialogFragment implements DialogInterface.OnKeyListener {
    private static final String EXTRA_TITLE="title";
    private static final String EXTRA_MESSAGE="message";
    private static final String EXTRA_INFO_ICON="info_icon";

    private TextView messageTextView;
    protected ButtonHandler mNeutralButton;
    protected ButtonHandler mPositiveButton;
    protected ButtonHandler mNegativeButton;

    /**
     * Creates + shows alert
     * Advantage: does null check
     * @param titleResourceId
     * @param msgResourceId
     */
    public static AlertDialogFragment alert(Activity activity, int titleResourceId, int msgResourceId){
        return alert(activity, titleResourceId, msgResourceId, false);
    }

    public static AlertDialogFragment alert(Activity activity, String title, String message){
        return alert(activity, title, message, false);
    }

    public static AlertDialogFragment info(Activity activity, int titleResourceId, int msgResourceId){
        return alert(activity, titleResourceId, msgResourceId, true);
    }

    private static AlertDialogFragment alert(Activity activity, String title, String message, boolean infoIcon){
        FragmentManager fragmentManager = activity.getFragmentManager();
        if (fragmentManager==null){
            return null;
        }
        AlertDialogFragment alertDialogFragment = AlertDialogFragment.newInstance(title, message, infoIcon);
        alertDialogFragment.show(fragmentManager, "alert");
        return alertDialogFragment;

    }

    private static AlertDialogFragment alert(Activity activity, int titleResourceId, int msgResourceId, boolean infoIcon){
        if (activity == null){
            return null;
        }

        String title = activity.getString(titleResourceId);
        String message = activity.getString(msgResourceId);
        return alert(activity, title, message, infoIcon);
    }

    public static AlertDialogFragment newInstance(String title, String message) {
        return newInstance(title, message, false);
    }

    public static AlertDialogFragment newInstance(String title, String message, boolean infoIcon) {
        AlertDialogFragment fragment = new AlertDialogFragment();

        Bundle args = new Bundle();
        if (title != null){
            args.putCharSequence(EXTRA_TITLE, title);
        }
        if (message != null){
            args.putCharSequence(EXTRA_MESSAGE, message);
        }
        args.putBoolean(EXTRA_INFO_ICON, infoIcon);
        fragment.setArguments(args);

        return fragment;
    }

    public void showAllowingStateLoss(FragmentManager manager, String tag) {
        FragmentTransaction ft = manager.beginTransaction();
        ft.add(this, tag);
        ft.commitAllowingStateLoss();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    protected View initBodyView(LayoutInflater inflater, CharSequence message){
        View bodyView = inflater.inflate(R.layout.alertdialog_message, null);
        messageTextView = (TextView) bodyView.findViewById(R.id.messageText);
        messageTextView.setText(message);
        return bodyView;
    }

    public Dialog onCreateDialog(Bundle savedInstanceState) {
        CharSequence title = getArguments().getCharSequence(EXTRA_TITLE);
        CharSequence message = getArguments().getCharSequence(EXTRA_MESSAGE);


        AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(getActivity());
        if (title != null){
            builder.setTitle(title);
        }
        if (message != null){
            builder.setMessage(message);
        }

        addButtons(builder);
        builder.setOnKeyListener(this);
        return builder.create();
    }

    protected AlertDialogWrapper.Builder addButtons(AlertDialogWrapper.Builder builder){
        boolean anyButton = false;

        if (mPositiveButton != null){
            builder.setPositiveButton(mPositiveButton.getText(),mPositiveButton.getClickListener());
            anyButton = true;
        }
        if (mNeutralButton != null){
            builder.setNeutralButton(mNeutralButton.getText(),mNeutralButton.getClickListener());
            anyButton = true;
        }
        if (mNegativeButton != null){
            builder.setNegativeButton(mNegativeButton.getText(),mNegativeButton.getClickListener());
            anyButton = true;
        }

        if(!anyButton){
            builder.setNegativeButton(R.string.ok,new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dismiss();
                }
            });
        }

        return builder;
    }

    @Override
    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // disable the back button
            return true;
        }
        return false;
    }

    public AlertDialogFragment setPositiveButton(CharSequence text, DialogInterface.OnClickListener listener){
        mPositiveButton = new ButtonHandler(text, listener);
        return this;
    }

    public AlertDialogFragment setNeutralButton(CharSequence text, DialogInterface.OnClickListener listener){
        mNeutralButton = new ButtonHandler(text, listener);
        return this;
    }

    public AlertDialogFragment setNegativeButton(CharSequence text, DialogInterface.OnClickListener listener){
        mNegativeButton = new ButtonHandler(text, listener);
        return this;
    }


    @Override
    public void onDestroyView() {
        if (getDialog() != null && getRetainInstance())
            getDialog().setDismissMessage(null);
        super.onDestroyView();
    }

    protected class ButtonHandler{
        public ButtonHandler(CharSequence text, DialogInterface.OnClickListener clickListener) {
            this.text = text;
            this.clickListener = clickListener;
        }

        public CharSequence getText() {
            return text;
        }
        public void setText(CharSequence text) {
            this.text = text;
        }
        public DialogInterface.OnClickListener getClickListener() {
            return clickListener;
        }
        public void setClickListener(DialogInterface.OnClickListener clickListener) {
            this.clickListener = clickListener;
        }
        CharSequence text;
        DialogInterface.OnClickListener clickListener;
    }


}

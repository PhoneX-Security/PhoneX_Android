package net.phonex.ui.call;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;

import net.phonex.R;
import net.phonex.util.MiscUtils;

/**
 * Created by ph4r05 on 8/25/14.
 */
public class SasDialogFragment extends DialogFragment implements DialogInterface.OnKeyListener {
    private static final String THIS_FILE = "SasDialogFragment";

    private DialogInterface.OnClickListener onConfirm;
    private DialogInterface.OnClickListener onReject;
    private ImageView icon;
    private TextView titleTextView;

    private String sasString[];
    private boolean sayFirst=true;

    public SasDialogFragment() {

    }

    @Override
    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return false;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        LayoutInflater inflater = getActivity().getLayoutInflater();

        View bodyView = prepareContainer(inflater, null, savedInstanceState);

        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                .title(R.string.sas_title)
                .customView(bodyView, false)
                .positiveText(R.string.sas_confirm)
                .negativeText(R.string.sas_reject)
                .autoDismiss(false)
                .cancelable(false);
        builder.callback(new MaterialDialog.ButtonCallback() {
            @Override
            public void onPositive(MaterialDialog dialog) {
                onConfirm.onClick(dialog, 0);
            }

            @Override
            public void onNegative(MaterialDialog dialog) {
                onReject.onClick(dialog, 1);
            }
        });
        MaterialDialog dialog = builder.build();

        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    public View prepareContainer(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View v = inflater.inflate(R.layout.sas_dialog, container, false);
        final FrameLayout c1 = (FrameLayout) v.findViewById(R.id.firstContainer);
        final FrameLayout c2 = (FrameLayout) v.findViewById(R.id.secondContainer);
        final Context ctxt = getActivity().getApplicationContext();

        c1.addView(getSasBlock(ctxt, inflater, sayFirst,  sasString[0], sasString[1]));
        c2.addView(getSasBlock(ctxt, inflater, !sayFirst, sasString[2], sasString[3]));

        final TextView txtMore = (TextView) v.findViewById(R.id.txtMore);
        final TextView txtDetail = (TextView) v.findViewById(R.id.txtMoreInfo);

        final LinearLayout expandable = (LinearLayout) v.findViewById(R.id.expandable);
        txtMore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final boolean wasShown = txtDetail.isShown();
                int leftDrawableId = wasShown ? R.drawable.ic_action_navigation_expand : R.drawable.ic_action_navigation_collapse;
                txtMore.setCompoundDrawablesWithIntrinsicBounds(leftDrawableId, 0, 0, 0);
                expandable.setVisibility(wasShown ? View.GONE : View.VISIBLE);
            }
        });

        return v;
    }

    private View getSasBlock(Context ctxt, LayoutInflater inflater, boolean say, String one, String two){
        View v = inflater.inflate(R.layout.sas_block, null);
        LinearLayout root = (LinearLayout) v.findViewById(R.id.sasContainer);
        TextView tv = (TextView) v.findViewById(R.id.sasChallenge);
        FrameLayout fl = (FrameLayout) v.findViewById(R.id.sasPart);

        SasWordLayout swl = getSasPart(ctxt, one, two);

        if (say){
            // Say
//            fl.setBackgroundResource(R.drawable.sas_texture_say);
            fl.setBackgroundColor(getResources().getColor(R.color.phonex_color_accent));
            tv.setText(R.string.sas_say);
        } else {
            // Verify
//            fl.setBackgroundResource(R.drawable.sas_texture_verify);
            fl.setBackgroundResource(R.drawable.sas_texture_verify);
            fl.setBackgroundColor(getResources().getColor(R.color.sas_color_verify_background));
            tv.setText(R.string.sas_verify);
        }

        fl.addView(swl);
        return v;
    }

    private SasWordLayout getSasPart(Context ctxt, String one, String two) {
        SasWordLayout swl = new SasWordLayout(ctxt, null);
        swl.setSas(one, two);
        return swl;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(THIS_FILE, "onCreate");

        // Retain this instance so it isn't destroyed  when changing the orientation
        setRetainInstance(true);
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
    }

    @Override
    public void onResume(){
        super.onResume();

    }

    // This is to work around what is apparently a bug. If you don't have it
    // here the dialog will be dismissed on rotation, so tell it not to dismiss.
    @Override
    public void onDestroyView()
    {
        Log.i(THIS_FILE,"onDestroyView");
        if (getDialog() != null && getRetainInstance())
            getDialog().setDismissMessage(null);
        super.onDestroyView();
    }

    public DialogInterface.OnClickListener getOnConfirm() {
        return onConfirm;
    }

    public void setOnConfirm(DialogInterface.OnClickListener onConfirm) {
        this.onConfirm = onConfirm;
    }

    public DialogInterface.OnClickListener getOnReject() {
        return onReject;
    }

    public void setOnReject(DialogInterface.OnClickListener onReject) {
        this.onReject = onReject;
    }

    public String[] getSasString() {
        return sasString;
    }

    public void setSasString(String[] sasString) {
        this.sasString = sasString;
    }

    public boolean isSayFirst() {
        return sayFirst;
    }

    public void setSayFirst(boolean sayFirst) {
        this.sayFirst = sayFirst;
    }

    /**
     * Set the whole SAS code.
     * Is automatically parsed to the NATO alphabet.
     *
     * @param sas
     */
    public void setSas(String sas){
        if (TextUtils.isEmpty(sas)){
            throw new IllegalArgumentException("Empty sas");
        }

        if (sas.length()<4){
            throw new IllegalArgumentException("SAS is too short");
        }

        sasString = new String[sas.length()];
        for(int i=0; i<4; i++) {
            final char c = sas.charAt(i);
            final String code = MiscUtils.getNatoAlphabet(c);
            sasString[i] = code;
        }
    }
}

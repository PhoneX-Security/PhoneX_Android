package net.phonex.ui.preferences;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.phonex.R;
import net.phonex.ui.lock.PinActivity;
import net.phonex.ui.lock.util.PinHelper;
import net.phonex.util.LayoutUtils;
import net.phonex.util.Log;
import net.phonex.util.crypto.CertificatesAndKeys;

public class PinResetPreference extends DialogPreference{
    private static final String TAG = "PinResetPreference";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private final Context context;
    private TextView labelText;
    private EditText passText;

    public PinResetPreference(Context aContext, AttributeSet attrs) {
        super(aContext, attrs);
        context = aContext;
//        dialogMessage = attrs.getAttributeValue(ANDROID_NS, "text");
    }

    @Override
    protected View onCreateDialogView() {
        LinearLayout.LayoutParams params;
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(6, 6, 6, 6);

        labelText = new TextView(context);
        labelText.setText(context.getString(R.string.pin_reset_enter_pass));
//        labelText.setGravity(Gravity.);
        labelText.setTextSize(18);
        params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        int sideMargin = LayoutUtils.dp2Pix(context.getResources(), 7);
        int pix2 = LayoutUtils.dp2Pix(context.getResources(), 5);
        params.setMargins(sideMargin, pix2, sideMargin, pix2);
        layout.addView(labelText, params);

        passText = new EditText(context);
        passText.setSingleLine();
        passText.setTransformationMethod(PasswordTransformationMethod.getInstance());
        passText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams params1 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(sideMargin, pix2, sideMargin, pix2);
        layout.addView(passText, params1);

        return layout;
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        final Dialog d = getDialog();
        Button pos = ((AlertDialog) d).getButton(DialogInterface.BUTTON_POSITIVE);
        pos.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                if (TextUtils.isEmpty(passText.getText())){
                    return;
                }

                boolean correctPass = CertificatesAndKeys.verifyUserPass(getContext(), passText.getText().toString());
                if (correctPass){
                    Log.inf(TAG, "onClick: correct pass entered, resetting PIN");
                    PinHelper.resetSavedPin(getContext());
                    PinActivity.startPinActivity(getContext());
                    d.dismiss();
                } else {
                    Log.w(TAG, "onClick: incorrect pass entered");
                    showError();
                }
            }
        });
    }

    private void showError(){
        passText.setError(context.getString(R.string.incorrect_password));

    }
}

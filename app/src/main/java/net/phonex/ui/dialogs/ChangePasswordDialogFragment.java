package net.phonex.ui.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.afollestad.materialdialogs.MaterialDialog;

import net.phonex.R;
import net.phonex.soap.PasswordChangeParams;
import net.phonex.util.PasswordValidator;

import java.util.List;

public class ChangePasswordDialogFragment extends AlertDialogFragment{
        private EditText passEditText;
        private EditText passwConfirmEditText;
        private PasswordValidator validator;

        private PasswordChangeParams params;
        private OnPasswordSelected listener;

        public interface OnPasswordSelected {
            void onPasswordSelected(PasswordChangeParams params);
        }

        public static ChangePasswordDialogFragment newInstance(PasswordChangeParams parChange, OnPasswordSelected listener) {
            ChangePasswordDialogFragment fr = new ChangePasswordDialogFragment();
            fr.setRetainInstance(true);
            fr.listener = listener;
            fr.params = parChange;
            return fr;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            validator = PasswordValidator.build(true, 8);
        }

        public Dialog onCreateDialog(Bundle savedInstanceState) {
            LayoutInflater inflater = getActivity().getLayoutInflater();
            View bodyView = inflater.inflate(R.layout.change_pass_dialog_fragment, null);
            passEditText = (EditText) bodyView.findViewById(R.id.password);
            passwConfirmEditText = (EditText) bodyView.findViewById(R.id.passwordConfirmation);

            MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                    .title(R.string.intro_pass_title)
                    .customView(bodyView, false)
                    .autoDismiss(false)
                    .positiveText(R.string.save);

            builder.callback(new MaterialDialog.ButtonCallback() {
                @Override
                public void onPositive(MaterialDialog dialog) {
                    String password = passEditText.getText().toString();
                    String passwordConfirmation = passwConfirmEditText.getText().toString();

                    if (TextUtils.isEmpty(password) || TextUtils.isEmpty(passwordConfirmation)) {
                        return;
                    }

                    List<PasswordValidator.Rule> validatedRules = validator.validate(password);
                    if (validatedRules.size() > 0) {
                        passEditText.requestFocus();
                        // Show first error
                        passEditText.setError(getString(validatedRules.get(0).getErrorStringId()));
                        return;
                    }

                    if (!password.equals(passwordConfirmation)) {
                        passwConfirmEditText.requestFocus();
                        passwConfirmEditText.setError(getString(R.string.pass_validator_confirmed_password_differs));
                        return;
                    }

                    params.setUserNewPass(password);
                    dialog.dismiss();
                    listener.onPasswordSelected(params);
                }
            });
            MaterialDialog dialog = builder.build();
            dialog.setCanceledOnTouchOutside(false);
            dialog.setOnKeyListener(this);
            return dialog;
        }
    }
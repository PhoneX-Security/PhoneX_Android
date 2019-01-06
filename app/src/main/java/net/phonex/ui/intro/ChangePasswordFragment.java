package net.phonex.ui.intro;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import net.phonex.R;
import net.phonex.soap.ChangePasswordTask;
import net.phonex.soap.PasswordChangeParams;
import net.phonex.soap.entities.PasswordChangeV2Response;
import net.phonex.ui.dialogs.AlertDialogFragment;
import net.phonex.ui.dialogs.ProgressDialogFragment;
import net.phonex.ui.interfaces.OnPassChangedListener;
import net.phonex.ui.interfaces.OnPasswordChangeCompleted;
import net.phonex.util.LayoutUtils;
import net.phonex.util.Log;
import net.phonex.util.PasswordValidator;

import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;


public class ChangePasswordFragment extends Fragment implements OnPasswordChangeCompleted {
	private static final String THIS_FILE = "ChangePasswordFragment";
	@InjectView(R.id.change_password_button) Button submitButton;
	@InjectView(R.id.old_password) EditText oldPasswordText;
	@InjectView(R.id.password) EditText passwordText;
	@InjectView(R.id.passwordConfirmation) EditText passwordConfirmedText;

	private PasswordChangeParams params;
	
	private OnPassChangedListener listener;

	private PasswordValidator validator = PasswordValidator.build(true, 8);

	@Override
    public void onAttach(Activity activity) {
      super.onAttach(activity);
      Log.i(THIS_FILE, "onAttach");
      if (activity instanceof OnPassChangedListener) {
    	  listener = (OnPassChangedListener) activity;
      } else {
        throw new ClassCastException(activity.toString()
            + " must implement OnPassChangedListener");
      }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    @Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_change_password, container, false);
		ButterKnife.inject(this, view);
		submitButton.setOnClickListener(mSubmitPassListener);

		// TODO
		// this form is shared with the account setup screen, when old password is not required, therefore hidden
		oldPasswordText.setVisibility(View.VISIBLE);

	    return view;
	  }
	
	@Override
	public void onCreate(Bundle savedInstanceState) {	
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}

    private final View.OnClickListener mSubmitPassListener = new View.OnClickListener() {
        @Override
        public void onClick(final View view) {
            LayoutUtils.hideSwKeyboard(getActivity());

			String password = passwordText.getText().toString();
			String passwordConfirmation = passwordConfirmedText.getText().toString();
			String oldPassword = oldPasswordText.getText().toString();

			if (TextUtils.isEmpty(password) || TextUtils.isEmpty(passwordConfirmation) || TextUtils.isEmpty(oldPassword)) {
				return;
			}

			List<PasswordValidator.Rule> validatedRules = validator.validate(password);
			if (validatedRules.size() > 0) {
				passwordText.requestFocus();
				// Show first error
				passwordText.setError(getString(validatedRules.get(0).getErrorStringId()));
				return;
			}

			if (!password.equals(passwordConfirmation)) {
				passwordConfirmedText.requestFocus();
				passwordConfirmedText.setError(getString(R.string.pass_validator_confirmed_password_differs));
				return;
			}
        	
        	// Start password change task.
			try {
				ChangePasswordTask task = new ChangePasswordTask(ChangePasswordFragment.this);
				task.setContext(getActivity());
				task.setFragmentManager(getFragmentManager());
				
				// TODO
				ProgressDialogFragment fragment = new ProgressDialogFragment();
				fragment.setMessage("...");
				fragment.setTitle(getString(R.string.p_changing_password));
				fragment.setTask(task);	
				fragment.setCheckpointsNumber(ChangePasswordTask.CHECKPOINTS_NUMBER);

				params.setUserOldPass(oldPassword);
            	params.setUserNewPass(passwordText.getText().toString());

            	fragment.show(getFragmentManager(),"");
            	task.execute(params);
            	
			} catch(Exception e){
				Log.w(THIS_FILE, "Exception", e);
			}
        }
    };

    /**
     * Called after password change task finishes.
     */
	@Override
	public void onPasswordChangeCompleted(PasswordChangeV2Response response, PasswordChangeParams params) {
		if (response.getResult() != 1){
			AlertDialogFragment.newInstance(getString(R.string.p_error),getString(R.string.p_password_change_err) ).show(getFragmentManager(), "TAG");
			return;
		} else {

			this.params = params;
            listener.onPassChanged(this.params);
		}
	}

	public PasswordChangeParams getParams() {
		return params;
	}

	public void setParams(PasswordChangeParams params) {
		this.params = params;
	}
}

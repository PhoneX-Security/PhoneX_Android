package net.phonex.ui.lock;

import android.view.View;
import android.widget.Toast;

import net.phonex.ui.lock.util.PinHelper;
import net.phonex.ui.lock.view.PinputView;
import net.phonex.ui.lock.view.PinputView.OnCommitListener;

import net.phonex.R;

/**
 * Created by miroc on 10.12.14.
 */
class ConfirmPinViewController extends BaseViewController {
    private String mTruthString;

    ConfirmPinViewController(PinFragment f, View v, String truth) {
        super(f, v);
        mTruthString = truth;
    }

    @Override
    void initUI() {
        headerText.setText(context.getString(R.string.confirm_n_digit_pin));
    }

    @Override
    OnCommitListener provideListener() {
        return new OnCommitListener() {
            @Override
            public void onPinCommit(PinputView view, String submission) {
                if (submission.equals(mTruthString)) {
                    handleSave(submission);
                    onSaveComplete();
                } else {
                    Toast.makeText(context, context.getString(R.string.pin_mismatch),
                            Toast.LENGTH_SHORT).show();
                    resetToCreate();
                    view.showErrorAndClear();
                }
            }
        };
    }

    private void handleSave(final String pin) {
        Toast.makeText(context, context.getString(R.string.pin_created),
                Toast.LENGTH_SHORT).show();
        PinHelper.savePin(context, pin);
    }

    private void onSaveComplete() {
        pinputView.getText().clear();
        mPinFragment.notifyCreated();
    }

    private void resetToCreate() {
        mPinFragment.setDisplayType(PinFragment.PinDisplayType.CREATE);
        mPinFragment.setViewController(new CreatePinViewController(mPinFragment, rootView));
    }

}

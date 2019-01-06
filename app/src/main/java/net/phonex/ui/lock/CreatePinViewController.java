package net.phonex.ui.lock;

import android.animation.Animator;
import android.view.View;

import net.phonex.ui.lock.view.PinputView;
import net.phonex.ui.lock.view.PinputView.OnCommitListener;

import net.phonex.R;

/**
 * Created by miroc on 10.12.14.
 */
class CreatePinViewController extends BaseViewController {
    CreatePinViewController(PinFragment f, View v) {
        super(f, v);
    }

    @Override
    void initUI() {
        String create = String.format(
                context.getString(R.string.create_n_digit_pin));
        headerText.setText(create);
    }

    @Override
    OnCommitListener provideListener() {
        return new OnCommitListener() {
            @Override
            public void onPinCommit(PinputView view, String submission) {
                Animator a = getOutAndInAnim(pinputView, pinputView);
                mPinFragment.onPinCreationEntered(submission);
                a.start();
            }
        };
    }
}

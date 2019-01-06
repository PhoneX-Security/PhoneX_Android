package net.phonex.ui.lock;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.text.Editable;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;

import net.phonex.R;
import net.phonex.ui.lock.util.VibrationHelper;
import net.phonex.ui.lock.view.PinKeyboardView;
import net.phonex.ui.lock.view.PinputView;
import net.phonex.ui.lock.view.PinputView.OnCommitListener;

import static android.view.MotionEvent.ACTION_DOWN;

/**
 * Created by miroc on 10.12.14.
 */
abstract class BaseViewController {

    protected PinFragment mPinFragment;
    protected Context context;
    protected PinputView pinputView;
    protected PinKeyboardView keyboardView;
    protected TextView headerText;
    protected ProgressBar progressBar;
    protected View rootView;

    /*package*/ BaseViewController(PinFragment f, View v) {
        mPinFragment = f;
        context = f.getActivity();
        rootView = v;
        init();
    }

    private void init() {
        pinputView = (PinputView) rootView.findViewById(R.id.pin_pinputview);
        keyboardView = (PinKeyboardView) rootView.findViewById(R.id.pin_keyboard);
        progressBar = (ProgressBar) rootView.findViewById(R.id.pin_progress_spinner);
        headerText = (TextView) rootView.findViewById(R.id.pin_header_label);
        initKeyboard();
        initUI();
        pinputView.setListener(provideListener());
    }

    final void refresh(View rootView) {
        this.rootView = rootView;
        init();
    }

    abstract void initUI();
    abstract OnCommitListener provideListener();

    private void initKeyboard() {
        keyboardView.setOnKeyboardActionListener(new PinKeyboardView.PinPadActionListener() {
            @Override
            public void onKey(int primaryCode, int[] keyCodes) {
                Editable e = pinputView.getText();
                if (primaryCode == PinKeyboardView.KEYCODE_DELETE) {
                    int len = e.length();
                    if (len == 0) {
                        return;
                    }
                    e.delete(len - 1, e.length());
                } else {
                    pinputView.getText().append((char) primaryCode);
                }
            }
        });
        keyboardView.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == ACTION_DOWN && getConfig().shouldVibrateOnKey()) {
                    VibrationHelper.vibrate(context, getConfig().vibrateDuration());
                }
                return false;
            }
        });
    }

    protected PinFragmentConfiguration getConfig() {
        return mPinFragment.getConfig();
    }

    protected Animator getOutAnim(View v) {
        float start = v.getX();
        float end = -v.getWidth();
        ObjectAnimator out = ObjectAnimator.ofFloat(v, "x", start, end);
        out.setDuration(150);
        out.setInterpolator(new AccelerateInterpolator());
        return out;
    }

    protected Animator getInAnim(View v) {
        float start = rootView.getWidth();
        float end = (start / 2) - (v.getWidth() / 2);
        final ObjectAnimator in = ObjectAnimator.ofFloat(v, "x", start, end);
        in.setDuration(150);
        in.setInterpolator(new DecelerateInterpolator());
        return in;
    }

    protected Animator getOutAndInAnim(final PinputView out, final View in) {
        Animator a = getOutAnim(out);
        a.addListener(new AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {}

            @Override
            public void onAnimationEnd(Animator animation) {
                out.getText().clear();
                in.setVisibility(View.VISIBLE);
                getInAnim(in).start();
            }

            @Override
            public void onAnimationCancel(Animator animation) {}

            @Override
            public void onAnimationRepeat(Animator animation) {}
        });
        return a;
    }

    void resetPinputView(){
        progressBar.setVisibility(View.INVISIBLE);
        float centerPosition = (rootView.getWidth() / 2) - (pinputView.getWidth() / 2);
        pinputView.setX(centerPosition);
        pinputView.getText().clear();
    }
}

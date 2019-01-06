package net.phonex.ui.customViews;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.CheckBox;
import android.widget.Checkable;
import android.widget.LinearLayout;

import net.phonex.R;

/**
 * LinearLayout view that is able being checked (has to contain Checkbox with id checkbox)
 */
public class CheckboxCheckableLinearLayout extends LinearLayout implements Checkable {
    private CheckBox mCheckBox;

    public CheckboxCheckableLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean isChecked() {
        return mCheckBox != null ? mCheckBox.isChecked() : false;
    }

    @Override
    public void setChecked(boolean isChecked) {
        if (mCheckBox != null)
            mCheckBox.setChecked(isChecked);
    }

    @Override
    public void toggle() {
        if (mCheckBox != null)
            mCheckBox.toggle();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mCheckBox = (CheckBox) findViewById(R.id.checkbox);
        mCheckBox.setClickable(false);
    }
}

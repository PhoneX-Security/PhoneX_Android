package net.phonex.ui.customViews;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Checkable;
import android.widget.LinearLayout;

import net.phonex.R;

/**
 * LinearLayout view that is able being checked.
 */
public class CheckableLinearLayout extends LinearLayout implements Checkable {
    private boolean checked = false;

    public CheckableLinearLayout(Context context) {
        super(context);
    }

    public CheckableLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void requestLayout() {
        forceLayout();
    }
    
	@Override
	public boolean isChecked() {
		return checked;
	}
	
	@Override
	public void setChecked(boolean aChecked) {
		if(checked == aChecked) {
			return;
		}

		checked = aChecked;
		setBackgroundResource(checked? R.drawable.abs__list_longpressed_holo : R.drawable.transparent);
	}
	
	@Override
	public void toggle() {
		setChecked(!checked);
	}
}

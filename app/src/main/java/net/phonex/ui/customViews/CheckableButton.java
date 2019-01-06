package net.phonex.ui.customViews;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Button;
import android.widget.Checkable;
import android.widget.ImageButton;

public class CheckableButton extends Button implements Checkable{

	// button may be locked - it doesn't imply any behaviour, it's only helper variable
	private volatile boolean locked = false;

    public boolean isLocked() {
		return locked;
	}

	public void setLocked(boolean locked) {
		this.locked = locked;
	}

	public CheckableButton(Context context) {
        super(context);
    }

    public CheckableButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private static final int[] CheckedStateSet = {android.R.attr.state_checked};

    private boolean mChecked = false;

    public boolean isChecked() {
        return mChecked;
    }

    public void setChecked(boolean b) {
        if (b != mChecked) {
            mChecked = b;
            refreshDrawableState();
        }
    }

    public void toggle() {
        setChecked(!mChecked);
    }

    @Override
    public int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (isChecked()) {
            mergeDrawableStates(drawableState, CheckedStateSet);
        }
        return drawableState;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }
    
    @Override
    public boolean performClick() {
    	toggle();    	
    	return super.performClick();
    }

}

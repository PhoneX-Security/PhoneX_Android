package net.phonex.ui.customViews;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.TextView;

public class FocusTextView extends TextView {

	public FocusTextView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

    @Override
    public boolean isFocused() {
        return true;
    }

	@Override
	protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
	    if(focused) {
            super.onFocusChanged(focused, direction, previouslyFocusedRect);
        }
	}

	@Override
	public void onWindowFocusChanged(boolean focused) {
	    if(focused) {
            super.onWindowFocusChanged(focused);
        }
	}
}

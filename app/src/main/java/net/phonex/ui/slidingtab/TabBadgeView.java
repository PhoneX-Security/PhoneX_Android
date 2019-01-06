package net.phonex.ui.slidingtab;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.RelativeLayout;
import android.widget.TabWidget;

import net.phonex.R;
import net.phonex.viewbadger.BadgeView;

/**
 * Specific badgeview with custom layout parameter
 * Parent has to be relative layout and there should be imageview with id "icon"
 * Created by miroc on 15.9.15.
 */
public class TabBadgeView extends BadgeView{
    public TabBadgeView(Context context) {
        super(context);
    }

    public TabBadgeView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TabBadgeView(Context context, View target) {
        super(context, target);
    }

    public TabBadgeView(Context context, TabWidget target, int index) {
        super(context, target, index);
    }

    public TabBadgeView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public TabBadgeView(Context context, AttributeSet attrs, int defStyle, View target, int tabIndex) {
        super(context, attrs, defStyle, target, tabIndex);
    }

    @Override
    protected void applyLayoutParams(){
        // empty
    }

    @Override
    protected void applyTo(View target) {
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.addRule(RelativeLayout.CENTER_VERTICAL);
        lp.addRule(RelativeLayout.RIGHT_OF, R.id.icon);
        setLayoutParams(lp);

        ViewParent parent = target.getParent();
        RelativeLayout group = (RelativeLayout) parent;
        this.setVisibility(View.GONE);
        group.addView(this);
        group.invalidate();
    }
}

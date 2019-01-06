package net.phonex.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorRes;
import android.support.annotation.DrawableRes;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ListView;
import android.widget.TextView;

import net.phonex.R;
import net.phonex.ui.chat.ScrollPosition;

public class LayoutUtils {
    private static final String TAG = "LayoutUtils";

    public static View createCategoryChild(LayoutInflater inflater, ViewGroup container, String title, String body){
    	View v = inflater.inflate(R.layout.mycategory_child, container, false);
    	TextView t = (TextView) v.findViewById(R.id.title);
    	TextView b = (TextView) v.findViewById(R.id.summary);
    	
    	t.setText(title);
    	b.setText(body);
    	
    	return v;
    }

    public static void hideSwKeyboard(Activity activity){
        // Hide SW keyboard
        if (activity != null){
            try {
                InputMethodManager inputManager = (InputMethodManager)
                        activity.getSystemService(Context.INPUT_METHOD_SERVICE);

                inputManager.hideSoftInputFromWindow(activity.getCurrentFocus().getWindowToken(),
                        InputMethodManager.HIDE_NOT_ALWAYS);
            } catch (Exception e){
                Log.wf(TAG, "Cannot hide SW keyboard");
            }
        }
    }

	/**
	 * converts DP to pixels on a particular screen
	 * useful for setting margin or padding on a View programmatically
	 * taken from http://developer.android.com/guide/practices/screens_support.html#dips-pels
	 * @return
	 */
	public static int dp2Pix(Resources r, int sizeInDp){
		float scale = r.getDisplayMetrics().density;
		int dpAsPixels = (int) (sizeInDp*scale + 0.5f);
		return dpAsPixels;
	}

    /**
     * Recomputes pixel size to DP size.
     *
     * @param r
     * @param sizeInPix
     * @return
     */
    public static int pix2dp(Resources r, int sizeInPix){
        float scale = r.getDisplayMetrics().density;
        int dps = (int) ((sizeInPix - 0.5f) / scale);
        return dps;
    }

    public static void makeViewVisibleAnimation(final View view, long animationDuration){
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);

        // Animate the content view to 100% opacity, and clear any animation
        // listener set on the view.
        view.animate()
                .alpha(1f)
                .setDuration(animationDuration)
                .setListener(null);
    }

    public static void crossfade(final View viewFrom, final View viewTo, Context context ){
        int animDuration = context.getResources().getInteger(android.R.integer.config_mediumAnimTime);
        crossfade(viewFrom, viewTo, animDuration);
    }

    public static void crossfade(final View viewFrom, final View viewTo, long animationDuration){
        viewTo.setAlpha(0f);
        viewTo.setVisibility(View.VISIBLE);

        // Animate the content view to 100% opacity, and clear any animation
        // listener set on the view.
        viewTo.animate()
                .alpha(1f)
                .setDuration(animationDuration)
                .setListener(null);

        // Animate the loading view to 0% opacity. After the animation ends,
        // set its visibility to GONE as an optimization step (it won't
        // participate in layout passes, etc.)
        viewFrom.animate()
                .alpha(0f)
                .setDuration(animationDuration)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        viewFrom.setVisibility(View.GONE);
                    }
                });
    }

    /**
     * Tries to scroll listview to the bottom.
     * @param mList
     * @return
     */
    public static ScrollPosition scrollToBottom(final ListView mList){
        if (mList == null){
            return null;
        }

        ScrollPosition sp = null;
        boolean toBottom = false;
        final int count = mList.getCount();
        try {
            final int lastPos = mList.getLastVisiblePosition() - mList.getFirstVisiblePosition();
            final View w = mList.getChildAt(lastPos);
            if (w != null){
                final int viewHeight = w.getMeasuredHeight();
                final int lstHeight = mList.getMeasuredHeight() - mList.getPaddingTop() - mList.getPaddingBottom();
                final int offset = lstHeight - viewHeight;
                mList.setSelectionFromTop(count - 1, offset);

                toBottom = true;
                sp = new ScrollPosition(count - 1, offset, count);
            }
        } catch(Exception e){
            Log.e(TAG, "Exception in scrolling to bottom", e);
        }

        // Imperfect to bottom scroll.
        if (!toBottom) {
            mList.setSelection(count - 1);
            sp = new ScrollPosition();
            sp.setCount(count);
        }

        return sp;
    }

    public static int getPrimaryTextColor(Context context) {
        TypedValue typedValue = new TypedValue();

        TypedArray a = context.obtainStyledAttributes(typedValue.data, new int[]{android.R.attr.textColorPrimary});
        int color = a.getColor(0, 0);
        a.recycle();
        return color;
    }

    public static int getSecondaryTextColor(Context context) {
        TypedValue typedValue = new TypedValue();
        TypedArray a = context.obtainStyledAttributes(typedValue.data, new int[]{android.R.attr.textColorSecondary});
        int color = a.getColor(0, 0);
        a.recycle();
        return color;
    }

    public static void setColoredText(TextView view, String fulltext, String subtext, int color) {
        view.setText(fulltext, TextView.BufferType.SPANNABLE);
        Spannable str = (Spannable) view.getText();
        int i = fulltext.indexOf(subtext);
        str.setSpan(new ForegroundColorSpan(color), i, i + subtext.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    public static Drawable getTintedDrawable(Resources res,
                                      @DrawableRes int drawableResId, @ColorRes int colorResId) {
        Drawable drawable = res.getDrawable(drawableResId);
        int color = res.getColor(colorResId);
        drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        return drawable;
    }
}

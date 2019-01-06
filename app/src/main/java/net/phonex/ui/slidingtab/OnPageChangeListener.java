package net.phonex.ui.slidingtab;

import android.support.v4.view.ViewPager;

/**
 * Created by miroc on 15.9.15.
 */
public interface OnPageChangeListener extends ViewPager.OnPageChangeListener {
    /**
     * New method to signalize which page was deselected
     * @param position
     */
    void onPageDeselected(int position);
}

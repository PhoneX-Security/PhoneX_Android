package net.phonex.ui.slidingtab;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;

import net.phonex.R;
import net.phonex.db.entity.CallLog;
import net.phonex.pub.a.Compatibility;
import net.phonex.ui.NotificationsFragment;
import net.phonex.ui.PhonexActivity;
import net.phonex.ui.cl.ContactListFragment;
import net.phonex.ui.conversations.ConversationsListFragment;
import net.phonex.util.Log;
import net.phonex.util.android.StatusbarNotifications;

import java.lang.ref.WeakReference;

/**
 * Created by miroc on 14.4.15.
 */
public class MyPagerAdapter extends SmartFragmentPagerAdapter implements IconPagerAdapter, OnPageChangeListener{
    private static final String TAG = "MyPagerAdapter";
    private static final int NUM_ITEMS = 3;
    private WeakReference<Activity> weakActivity;
    private WeakReference<Toolbar> weakToolbar;
    private int[] notificationsCount = new int[] {0,0,0};

    private final int[] TITLES = {
            R.string.contacts_tab_name_text,
            R.string.messages_tab_name_text,
            R.string.notifications_tab_name_text
    };

    public MyPagerAdapter(FragmentManager fragmentManager, Activity activity, Toolbar toolbar) {
        super(fragmentManager);
        weakActivity = new WeakReference<>(activity);
        weakToolbar = new WeakReference<>(toolbar);
    }

    @Override
    public Fragment getItem(int position) {
        switch (position) {
            case 0: // Fragment # 0 - This will show FirstFragment
                return ContactListFragment.newInstance();
            case 1: // Fragment # 0 - This will show FirstFragment different title
                return ConversationsListFragment.newInstance();
            case 2: // Fragment # 1 - This will show SecondFragment
                return NotificationsFragment.newInstance();
            default:
                return null;
        }
    }

    @Override
    public int getCount() {
        return Compatibility.isCallSupported() ? NUM_ITEMS : NUM_ITEMS - 1;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        Activity act = this.weakActivity.get();
        if (act == null){
            return null;
        }

        if (position >= NUM_ITEMS){
            return null;
        } else {
            return act.getString(TITLES[position]);
        }
    }

    @Override
    public int getIconResId(int position) {
        switch (position){
            case 0:
//                return R.drawable.ic_account_box_black_24px;
                return R.drawable.ic_person_black_24px;
            case 1:
                return R.drawable.ic_chat_black_24px;
            case 2:
                return R.drawable.ic_announcement_black_24px;
            default:
                return 0;
        }
    }

    @Override
    public int getNotificationCount(int position) {
        if (position > (notificationsCount.length-1) || position < 0){
            Log.ef(TAG, "getNotificationCount; invalid position %d, returning 0d", position);
            return 0;
        }
        return notificationsCount[position];
    }

    public void setNotificationCount(int position, int notificationCount){
        if (position > (notificationsCount.length-1) || position < 0){
            Log.ef(TAG, "setNotificationCount; invalid position %d", position);
            return;
        }
        notificationsCount[position] = notificationCount;
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

    }

    @Override
    public void onPageSelected(int position) {

        Toolbar toolbar = weakToolbar.get();
        if (toolbar != null){
            toolbar.setTitle(TITLES[position]);
        }

        Activity act = weakActivity.get();
        if (act != null){
            PhonexActivity phonexActivity = (PhonexActivity) act;
            phonexActivity.onTabSwitched(position);
        }

    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }

    @Override
    public void onPageDeselected(int position) {
        if (position == 2){
            // when leaving notifications tab, mark all missed calls as seen, running in a thread not to block UI
            new Thread(() -> {
                Activity act = weakActivity.get();
                if (act != null){
                    StatusbarNotifications statusbarNotifications = new StatusbarNotifications(act);
                    statusbarNotifications.cancelMissedCalls();
                    CallLog.markLogsAsSeen(act.getContentResolver());
                }
            }).start();
        }
    }
}

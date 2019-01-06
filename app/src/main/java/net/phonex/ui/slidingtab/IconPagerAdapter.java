package net.phonex.ui.slidingtab;

/**
 * Created by miroc on 19.2.15.
 */
public interface IconPagerAdapter {
    int getIconResId(int position);

    int getNotificationCount(int position);

    void setNotificationCount(int position, int notificationCount);

    int getCount();
}

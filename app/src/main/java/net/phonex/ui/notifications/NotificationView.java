package net.phonex.ui.notifications;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import net.phonex.R;

/**
 * Created by ph4r05 on 8/23/14.
 */
public class NotificationView {
    public final ImageView icon;
    public final View primaryActionView;
    public final TextView title;
    public final TextView subtitle;
    public final TextView time;

    private NotificationView(
            ImageView icon,
            View primaryActionView,

            TextView title,
            TextView subtitle,
            TextView time)
    {
        this.icon = icon;
        this.primaryActionView = primaryActionView;
        this.title = title;
        this.subtitle = subtitle;
        this.time = time;
    }

    /**
     * Construct view placeholder from the view object,
     * @param view
     * @return
     */
    public static NotificationView fromView(View view) {
        return new NotificationView(
                (ImageView) view.findViewById(R.id.icon),
                view.findViewById(R.id.primary_action_view),
                (TextView) view.findViewById(R.id.title),
                (TextView) view.findViewById(R.id.subtitle),
                (TextView) view.findViewById(R.id.time)
        );
    }
}
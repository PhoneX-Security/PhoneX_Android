package net.phonex.ui.notifications;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

import net.phonex.R;
import net.phonex.db.entity.CallLog;
import net.phonex.ui.NotificationsFragment;
import net.phonex.util.DateUtils;
import net.phonex.util.LayoutUtils;
import net.phonex.util.Log;

/**
 * Adapter class to fill in data for the Contact List.
 */
public class NotificationListAdapter extends CursorAdapter {
	protected static final String THIS_FILE = "NotificationListAdapter";

    private OnNotificationListAction actionListener = null;

    public interface OnNotificationListAction {
        void showNotificationMenu(NotificationWrapper w);
    }

	public NotificationListAdapter(Context context, Cursor c, int flags) {
		super(context, c, flags);
	}
    
    @Override
	public View newView(Context context, Cursor arg1, ViewGroup parent) {
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.notification_row, parent, false);
        findAndCacheViews(view);
        return view;    
	}
    
    @Override
	public void bindView(View view, Context context, Cursor c) {        
    	final NotificationView views = (NotificationView) view.getTag();

        views.primaryActionView.setVisibility(View.VISIBLE);

        // Determine type of the notification here from the cursor.
        final int typeIdx = c.getColumnIndex(NotificationsFragment.FIELD_TYPE);
        if (typeIdx==-1){
            Log.e(THIS_FILE, "Invalid cursor passed");
            return;
        }

        final int cursorType = c.getInt(typeIdx);
        if (cursorType!=NotificationsFragment.CURSOR_ALL_CALLS){
            Log.ef(THIS_FILE, "Cursor type [%d] not implemented", cursorType);
            return;
        }

        CallLog clog = new CallLog(c);
        views.primaryActionView.setTag(new NotificationWrapper(clog, cursorType));

        switch (clog.getType()){
            case android.provider.CallLog.Calls.MISSED_TYPE:
                views.icon.setImageDrawable(LayoutUtils.getTintedDrawable(context.getResources(), R.drawable.ic_call_missed_white_24px, R.color.material_red_500));
                break;
            case android.provider.CallLog.Calls.INCOMING_TYPE:
                views.icon.setImageDrawable(LayoutUtils.getTintedDrawable(context.getResources(), R.drawable.ic_call_received_white_24px, R.color.material_green_500));
                break;
            case android.provider.CallLog.Calls.OUTGOING_TYPE:
                views.icon.setImageDrawable(LayoutUtils.getTintedDrawable(context.getResources(), R.drawable.ic_call_made_white_24px, R.color.material_green_500));
                break;
            default:
                // unknown type, do not display icon
                views.icon.setVisibility(View.INVISIBLE);
                break;
        }


        views.title.setText(clog.getRemoteContactName());
        views.time.setText(DateUtils.relativeTimeFromNow(clog.getCallStart()));
        views.subtitle.setVisibility(View.GONE);

        int secondaryTextColor = LayoutUtils.getSecondaryTextColor(context);
        int primaryTextColor = LayoutUtils.getPrimaryTextColor(context);
        int accentColor = context.getResources().getColor(R.color.phonex_color_accent);
        // highlight unseen missed calls logs
        if (clog.getType() == android.provider.CallLog.Calls.MISSED_TYPE && !clog.isSeenByUser()){
            views.time.setTextColor(accentColor);
            views.title.setTextColor(accentColor);
        } else {
            views.time.setTextColor(secondaryTextColor);
            views.title.setTextColor(primaryTextColor);
        }
	}
	
	private void findAndCacheViews(View view) {
    	NotificationView views = NotificationView.fromView(view);
        views.primaryActionView.setLongClickable(true);
        views.primaryActionView.setOnClickListener(mPrimaryActionListener);
        view.setTag(views);
    }

	/** Listener for the primary action in the list, opens the call details. */
    private final View.OnClickListener mPrimaryActionListener = view -> {
        NotificationWrapper w = (NotificationWrapper) view.getTag();
        if (w.type != NotificationsFragment.CURSOR_ALL_CALLS){
            Log.ef(THIS_FILE, "Unknown tag passed [%s]", w.type);
            return;
        }

        if (actionListener!=null){
            actionListener.showNotificationMenu(w);
        }
    };

    public void setActionListener(OnNotificationListAction actionListener) {
        this.actionListener = actionListener;
    }
}


package net.phonex.ui.conversations;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import net.phonex.R;
import net.phonex.db.entity.SipClist;
import net.phonex.db.entity.SipMessage;
import net.phonex.util.DateUtils;
import net.phonex.util.LayoutUtils;

public class ConversationsListAdapter extends SimpleCursorAdapter {
    private static final String TAG = "ConversationsListAdapter";
    private int secondaryTextColorId;

    public ConversationsListAdapter(Context context, Cursor c) {
        super(context,
                R.layout.chatlist_item,
                c,
                new String[]{SipMessage.FIELD_BODY},
                new int[]{R.id.subject},
                0);
        secondaryTextColorId = LayoutUtils.getSecondaryTextColor(context);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View view = super.newView(context, cursor, parent);
        ViewHolder tagView = new ViewHolder();
        tagView.fromView = (TextView) view.findViewById(R.id.from);
        tagView.dateView = (TextView) view.findViewById(R.id.date);
        tagView.subject = (TextView) view.findViewById(R.id.subject);
        view.setTag(tagView);
        return view;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        super.bindView(view, context, cursor);
        final ViewHolder tagView = (ViewHolder) view.getTag();

        // From SipMessage.THREAD_URI URI, load SipMessage and display name of remote contact for each aggregated thread
        ContentValues args = new ContentValues();
        DatabaseUtils.cursorRowToContentValues(cursor, args);
        SipMessage msg = new SipMessage();
        msg.createFromContentValue(args);

        String remoteDisplayName = args.getAsString(SipClist.FIELD_DISPLAY_NAME);

        String nbr = msg.getFrom();
        String fromFull = msg.getFullFrom();
        String to_number = msg.getTo();
        long date = msg.getDate();

        tagView.isOutgoing = msg.isOutgoing();
        tagView.fromFull = fromFull;
        tagView.to = to_number;
        tagView.from = nbr;
        tagView.position = cursor.getPosition();
        if(remoteDisplayName != null) {
            tagView.displayName = remoteDisplayName;
            tagView.fromView.setText(remoteDisplayName);
        } else {
            tagView.displayName = msg.getRemoteNumber();
            tagView.fromView.setText(tagView.displayName);
        }

        // Set the date/time field by mixing relative and absolute times.
        tagView.dateView.setText(DateUtils.relativeTimeFromNow(date));

        // Color depending on read flag
        int textColorResId = secondaryTextColorId;
        if (!msg.isOutgoing() && !msg.isRead()){
            textColorResId = context.getResources().getColor(R.color.phonex_color_accent);
        }
        tagView.subject.setTextColor(textColorResId);
        tagView.dateView.setTextColor(textColorResId);
    }

    public static final class ViewHolder {
        public TextView fromView;
        public TextView dateView;
        public TextView subject;
        public ImageView icon;
        public int position;
        public String to;
        public String from;
        public String fromFull;
        public String displayName;
        public boolean isOutgoing;

        public String getRemoteNumber() {
            if (isOutgoing)
                return to;
            else
                return from;
        }
    }
}

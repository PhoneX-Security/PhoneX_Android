package net.phonex.ui.broadcast;

import android.content.Context;
import android.database.Cursor;
import android.widget.SimpleCursorAdapter;

import net.phonex.db.entity.SipClist;

/**
 * getItem should retrieve SipClist for this cursor adapter
 * Created by miroc on 24.2.15.
 */
public class SipClistCursorAdapter extends SimpleCursorAdapter {
    public SipClistCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to, int flags) {
        super(context, layout, c, from, to, flags);
    }

    @Override
    public Object getItem(int position) {
        Object item = super.getItem(position);
        if (item == null){
            return null;
        } else {
            return new SipClist((Cursor) item);
        }
    }

    @Override
    public void changeCursor(Cursor newCursor) {
        newCursor.close();
    }
}

package net.phonex.db.entity;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import net.phonex.core.Constants;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;

import java.util.Date;

/**
 * Created by miroc on 4.5.15.
 */
public class TrialEventLog implements Parcelable {
    private static final String TAG = "TrialEventLog";
    private Long id;
    private int type;

    private Date date;

    public static final String TABLE = "trial_event_log"; //TODO: verify
    public static final String FIELD_ID = "_id";
    public static final String FIELD_TYPE = "type";
    public static final String FIELD_DATE = "date";


    // trial event types (each event we want to restrict has a specific type)
    public static final int TYPE_OUTGOING_MESSAGE = 1;

    public static final String[] FULL_PROJECTION = new String[]{
            FIELD_ID, FIELD_TYPE, FIELD_DATE
    };

    public static final Uri URI = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/" + TABLE);

    public static final Uri ID_URI_BASE = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/" + TABLE + "/");


    public static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS "
            + TABLE
            + " ("
            + FIELD_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + FIELD_TYPE + " INTEGER DEFAULT 0, "
            + FIELD_DATE + " INTEGER DEFAULT 0 "
            + ");";


    private final void createFromCursor(Cursor c) {
        int colCount = c.getColumnCount();
        for (int i = 0; i < colCount; i++) {
            final String colname = c.getColumnName(i);
            if (FIELD_ID.equals(colname)) {
                this.id = c.getLong(i);
            } else if (FIELD_TYPE.equals(colname)) {
                this.type = c.getInt(i);
            } else if (FIELD_DATE.equals(colname)) {
                this.date = new Date(c.getLong(i));
            } else {
                Log.wf(TAG, "Unknown column name: " + colname);
            }
        }
    }


    public ContentValues getDbContentValues() {
        ContentValues args = new ContentValues();
        if (this.id != null)
            args.put(FIELD_ID, id);
        args.put(FIELD_TYPE, type);
        if (this.date != null)
            args.put(FIELD_DATE, date.getTime());
        return args;
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeValue(this.id);
        dest.writeInt(this.type);
        dest.writeLong(date != null ? date.getTime() : -1);
    }

    public TrialEventLog() {
    }

    private TrialEventLog(Parcel in) {
        this.id = (Long) in.readValue(Long.class.getClassLoader());
        this.type = in.readInt();
        long tmpDate = in.readLong();
        this.date = tmpDate == -1 ? null : new Date(tmpDate);
    }

    public static final Parcelable.Creator<TrialEventLog> CREATOR = new Parcelable.Creator<TrialEventLog>() {
        public TrialEventLog createFromParcel(Parcel source) {
            return new TrialEventLog(source);
        }

        public TrialEventLog[] newArray(int size) {
            return new TrialEventLog[size];
        }
    };

    public static void logOutgoingMessage(Context context){
        ContentValues cv = new ContentValues();
        cv.put(FIELD_DATE, System.currentTimeMillis());
        cv.put(FIELD_TYPE, TYPE_OUTGOING_MESSAGE);

        context.getContentResolver().insert(URI, cv);
    }

    public static int getOutgoingMessageCount(Context context, int daysCount){
        return getCountPerLastDays(context, TYPE_OUTGOING_MESSAGE, daysCount);
    }

    public static int getCountPerLastDays(Context context, int type, int daysCount){
        long time = System.currentTimeMillis() - (daysCount * 24 * 60 * 60 * 1000);
        // test
//        long time = System.currentTimeMillis() - (1000*45); // 45 seconds

        String[] projection = new String[]{" COUNT(*) as count"};
        String selection = FIELD_TYPE + "=? AND " + FIELD_DATE + " > ?";
        String[] selectionArgs = new String[]{String.valueOf(type), String.valueOf(time)};
        Cursor c = null;
        try {
            c = context.getContentResolver().query(URI, projection, selection, selectionArgs, null);
            if (c == null) {
                return 0;
            } else if (c.moveToFirst()) {
                int count = c.getInt(0);
                return count;
            } else {
                return 0;
            }
        } finally {
            MiscUtils.closeCursorSilently(c);
        }
    }
}


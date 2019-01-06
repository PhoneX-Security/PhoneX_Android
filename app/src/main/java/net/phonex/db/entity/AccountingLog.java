package net.phonex.db.entity;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import net.phonex.core.Constants;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by miroc on 7.10.15.
 */
public class  AccountingLog implements Parcelable {
    public static final String TAG = " AccountingLog";

    // <ATTRIBUTES>
    protected long id;
    protected String type;              // Name of the counter / permission. Compact form.
    protected String rkey;              // Not used. Unique text identifier.
    protected Date dateCreated;         // Record first created date time.
    protected Date dateModified;        // Record last modification date time.
    protected long actionId;          // Milliseconds from UTC when this record was created.
    protected long actionCounter;     // Monotonically increasing counter sequence.  (actionId, actionCounter) is an unique key.
    protected long amount;            // Amount of units consumed from the counter / permission.
    protected int aggregated;        // Number of records aggregated in this record.
    protected String aref;              // Not used.
    protected Long permId;            // Optional. Permission ID to account for this spend record.
    protected Long licId;             // Optional. License ID to account for this spend record.
    // </ATTRIBUTES>

    // incrementally increased value, just to avoid collision on timestamps
    private static AtomicInteger actionIdCounter = new AtomicInteger();

    public static final String TABLE = "AccountingLog";
    public static final String FIELD_ID = "_id";
    public static final String FIELD_TYPE = "type";
    public static final String FIELD_RKEY = "rkey";
    public static final String FIELD_DATE_CREATED = "dateCreated";
    public static final String FIELD_DATE_MODIFIED = "dateModified";
    public static final String FIELD_ACTION_ID = "actionId";
    public static final String FIELD_ACTION_COUNTER = "actionCounter";
    public static final String FIELD_AMOUNT = "amount";
    public static final String FIELD_AGGREGATED = "aggregated";
    public static final String FIELD_AREF = "aref";
    public static final String FIELD_PERM_ID = "permId";
    public static final String FIELD_LIC_ID = "licId";

    public static final Uri URI = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/" + TABLE);

    public static final Uri ID_URI_BASE = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/" + TABLE + "/");


    public static final String[] FULL_PROJECTION = new String[]{
            FIELD_ID, FIELD_TYPE, FIELD_RKEY, FIELD_DATE_CREATED, FIELD_DATE_MODIFIED, FIELD_ACTION_ID, FIELD_ACTION_COUNTER, FIELD_AMOUNT, FIELD_AGGREGATED, FIELD_AREF, FIELD_PERM_ID, FIELD_LIC_ID
    };


    public static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS "
            + TABLE
            + " ("
            + FIELD_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + FIELD_TYPE + " TEXT, "
            + FIELD_RKEY + " TEXT, "
            + FIELD_DATE_CREATED + " INTEGER DEFAULT 0, "
            + FIELD_DATE_MODIFIED + " INTEGER DEFAULT 0, "
            + FIELD_ACTION_ID + " INTEGER DEFAULT 0, "
            + FIELD_ACTION_COUNTER + " INTEGER DEFAULT 0, "
            + FIELD_AMOUNT + " INTEGER DEFAULT 0, "
            + FIELD_AGGREGATED + " INTEGER DEFAULT 0, "
            + FIELD_AREF + " TEXT, "
            + FIELD_PERM_ID + " INTEGER, "
            + FIELD_LIC_ID + " INTEGER "
            + ");";




    private final void createFromCursor(Cursor c) {
        int colCount = c.getColumnCount();
        for (int i = 0; i < colCount; i++) {
            final String colname = c.getColumnName(i);
            if (FIELD_ID.equals(colname)) {
                this.id = c.getLong(i);
            } else if (FIELD_TYPE.equals(colname)) {
                this.type = c.getString(i);
            } else if (FIELD_RKEY.equals(colname)) {
                this.rkey = c.getString(i);
            } else if (FIELD_DATE_CREATED.equals(colname)) {
                this.dateCreated = new Date(c.getLong(i));
            } else if (FIELD_DATE_MODIFIED.equals(colname)) {
                this.dateModified = new Date(c.getLong(i));
            } else if (FIELD_ACTION_ID.equals(colname)) {
                this.actionId = c.getLong(i);
            } else if (FIELD_ACTION_COUNTER.equals(colname)) {
                this.actionCounter = c.getLong(i);
            } else if (FIELD_AMOUNT.equals(colname)) {
                this.amount = c.getLong(i);
            } else if (FIELD_AGGREGATED.equals(colname)) {
                this.aggregated = c.getInt(i);
            } else if (FIELD_AREF.equals(colname)) {
                this.aref = c.getString(i);
            } else if (FIELD_PERM_ID.equals(colname)) {
                this.permId = c.getLong(i);
            } else if (FIELD_LIC_ID.equals(colname)) {
                this.licId = c.getLong(i);
            } else {
                Log.w(TAG, "Unknown column name: " + colname);
            }
        }
    }


    public ContentValues getDbContentValues() {
        ContentValues args = new ContentValues();
//        args.put(FIELD_ID, id);
        if (this.type != null)
            args.put(FIELD_TYPE, type);
        if (this.rkey != null)
            args.put(FIELD_RKEY, rkey);
        if (this.dateCreated != null)
            args.put(FIELD_DATE_CREATED, dateCreated.getTime());
        if (this.dateModified != null)
            args.put(FIELD_DATE_MODIFIED, dateModified.getTime());
        args.put(FIELD_ACTION_ID, actionId);
        args.put(FIELD_ACTION_COUNTER, actionCounter);
        args.put(FIELD_AMOUNT, amount);
        args.put(FIELD_AGGREGATED, aggregated);
        if (this.aref != null)
            args.put(FIELD_AREF, aref);
        if (permId != null)
            args.put(FIELD_PERM_ID, permId);
        if (licId != null)
            args.put(FIELD_LIC_ID, licId);
        return args;
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(this.id);
        dest.writeString(this.type);
        dest.writeString(this.rkey);
        dest.writeLong(dateCreated != null ? dateCreated.getTime() : -1);
        dest.writeLong(dateModified != null ? dateModified.getTime() : -1);
        dest.writeLong(this.actionId);
        dest.writeLong(this.actionCounter);
        dest.writeLong(this.amount);
        dest.writeInt(this.aggregated);
        dest.writeString(this.aref);
        dest.writeLong(this.permId);
        dest.writeLong(this.licId);
    }

    public AccountingLog() {
    }

    public AccountingLog(Cursor c){
        createFromCursor(c);
    }

    private AccountingLog(Parcel in) {
        this.id = in.readLong();
        this.type = in.readString();
        this.rkey = in.readString();
        long tmpDateCreated = in.readLong();
        this.dateCreated = tmpDateCreated == -1 ? null : new Date(tmpDateCreated);
        long tmpDateModified = in.readLong();
        this.dateModified = tmpDateModified == -1 ? null : new Date(tmpDateModified);
        this.actionId = in.readLong();
        this.actionCounter = in.readLong();
        this.amount = in.readLong();
        this.aggregated = in.readInt();
        this.aref = in.readString();
        this.permId = (Long) in.readValue(Integer.class.getClassLoader());
        this.licId = (Long) in.readValue(Integer.class.getClassLoader());
    }

    public static final Parcelable.Creator<AccountingLog> CREATOR = new Parcelable.Creator<AccountingLog>() {
        public AccountingLog createFromParcel(Parcel source) {
            return new AccountingLog(source);
        }

        public AccountingLog[] newArray(int size) {
            return new AccountingLog[size];
        }
    };

    public long getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getRkey() {
        return rkey;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public Date getDateModified() {
        return dateModified;
    }

    public long getActionId() {
        return actionId;
    }

    public long getActionCounter() {
        return actionCounter;
    }

    public long getAmount() {
        return amount;
    }

    public int getAggregated() {
        return aggregated;
    }

    public String getAref() {
        return aref;
    }

    public Long getPermId() {
        return permId;
    }

    public Long getLicId() {
        return licId;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public static List<AccountingLog> getAll(ContentResolver cr, String[] projection, String orderBy) {
        List<AccountingLog> result = new ArrayList<>();

        Cursor c = cr.query(URI, projection, null, null, null);

        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    do {
                        result.add(new AccountingLog(c));
                    } while (c.moveToNext());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error on looping over sip profiles", e);
            } finally {
                MiscUtils.closeCursorSilently(c);
            }
        }

        return result;
    }

    public static void deleteRecordsOlderThan(long actionId, long actionCtr, ContentResolver cr) {
        String where =  FIELD_ACTION_ID + "<? OR (" + FIELD_ACTION_ID + "=? AND " + FIELD_ACTION_COUNTER + "<=? )";
        int deleted = cr.delete(URI, where, new String[]{String.valueOf(actionId), String.valueOf(actionId), String.valueOf(actionCtr)});
        Log.vf(TAG, "deleteRecordsOlderThan; number of records deleted=%d", deleted);
    }

    public static AccountingLog logFromPermission(AccountingPermission permission){
        AccountingLog log = new AccountingLog();

        Date currentDate = new Date();
        Calendar c = Calendar.getInstance();
        c.setTime(currentDate);

        log.type = permission.getName();
        log.dateCreated = currentDate;
        log.dateModified = currentDate;

        // id is given by time in millis
        log.actionId = c.getTimeInMillis();
        log.actionCounter = actionIdCounter.getAndIncrement();
        log.amount = permission.getSpent();
        log.aggregated = 1;
        log.permId = permission.getPermId();
        log.licId = permission.getLicId();
        return log;
    }
}

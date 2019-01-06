package net.phonex.db.entity;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import net.phonex.core.Constants;
import net.phonex.soap.entities.PairingRequestResolutionEnum;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Matus on 25-Aug-15.
 */
public class PairingRequest implements Parcelable {
    private static final String TAG = "PairingRequest";

    private Long id; // id in this DB
    private long serverId; // id from server
    private long tstamp;
    private java.lang.String fromUser;
    private boolean seen = false;
    private PairingRequestResolutionEnum resolution;
    private Long resolutionTstamp;

    public static final String TABLE = "PairingRequest";

    public static final String FIELD_ID = "_id";
    public static final String FIELD_SERVER_ID = "serverId";
    public static final String FIELD_TSTAMP = "tstamp";
    public static final String FIELD_FROM_USER = "fromUser";
    public static final String FIELD_RESOLUTION = "resolution";
    public static final String FIELD_RESOLUTION_TSTAMP = "resolutionTstamp";
    public static final String FIELD_SEEN = "seen";

    /**
     * Base URI for contact content provider.<br/>
     * To append with {@link #FIELD_ID}
     *
     * @see android.content.ContentUris#appendId(android.net.Uri.Builder, long)
     */
    public static final Uri ID_URI_BASE = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/" + TABLE + "/");

    public static final Uri URI = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/" + TABLE);

    public static final String[] ID_PROJECTION = new String[]{
            FIELD_ID, FIELD_SERVER_ID
    };

    public static final String[] FULL_PROJECTION = new String[]{
            FIELD_ID, FIELD_SERVER_ID, FIELD_TSTAMP, FIELD_FROM_USER, FIELD_RESOLUTION, FIELD_RESOLUTION_TSTAMP
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(this.id);
        dest.writeLong(this.serverId);
        dest.writeLong(this.tstamp);
        dest.writeString(this.fromUser);
        dest.writeInt(this.resolution == null ? -1 : this.resolution.ordinal());
        dest.writeValue(this.resolutionTstamp);
        dest.writeByte((byte) (seen ? 0x01 : 0x00));
    }

    public PairingRequest() {
    }

    public PairingRequest(Cursor c){
        createFromCursor(c);
    }

    private PairingRequest(Parcel in) {
        this.id = in.readLong();
        this.serverId = in.readLong();
        this.tstamp = in.readLong();
        this.fromUser = in.readString();
        int tmpResolution = in.readInt();
        this.resolution = tmpResolution == -1 ? null : PairingRequestResolutionEnum.values()[tmpResolution];
        this.resolutionTstamp = (Long) in.readValue(Long.class.getClassLoader());
        this.seen = in.readByte() != 0x00;
    }

    public static final Creator<PairingRequest> CREATOR = new Creator<PairingRequest>() {
        public PairingRequest createFromParcel(Parcel source) {
            return new PairingRequest(source);
        }

        public PairingRequest[] newArray(int size) {
            return new PairingRequest[size];
        }
    };

    public static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS "
            + TABLE
            + " ("
            + FIELD_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + FIELD_SERVER_ID + " INTEGER DEFAULT 0, "
            + FIELD_TSTAMP + " INTEGER DEFAULT 0, "
            + FIELD_SEEN + " BOOLEAN DEFAULT 0,"
            + FIELD_FROM_USER + " TEXT, "
            + FIELD_RESOLUTION + " TEXT, "
            + FIELD_RESOLUTION_TSTAMP + " INTEGER DEFAULT 0 "
            + ");";

    private final void createFromCursor(Cursor c) {
        int colCount = c.getColumnCount();
        for (int i = 0; i < colCount; i++) {
            final String colname = c.getColumnName(i);
            if (FIELD_ID.equals(colname)) {
                this.id = c.getLong(i);
            } else if (FIELD_SERVER_ID.equals(colname)) {
                this.serverId = c.getLong(i);
            } else if (FIELD_TSTAMP.equals(colname)) {
                this.tstamp = c.getLong(i);
            } else if (FIELD_FROM_USER.equals(colname)) {
                this.fromUser = c.getString(i);
            } else if (FIELD_RESOLUTION.equals(colname)) {
                this.resolution = PairingRequestResolutionEnum.fromValue(c.getString(i));
            } else if (FIELD_RESOLUTION_TSTAMP.equals(colname)) {
                this.resolutionTstamp = c.getLong(i);
            } else if (FIELD_SEEN.equals(colname)) {
                this.seen = c.getInt(i) == 1;
            } else {
                Log.w(TAG, "Unknown column name: " + colname);
            }
        }
    }

    public ContentValues getDbContentValues() {
        ContentValues args = new ContentValues();
        if (id != null) {
            args.put(FIELD_ID, id);
        }
        args.put(FIELD_SERVER_ID, serverId);
        args.put(FIELD_TSTAMP, tstamp);
        args.put(FIELD_SEEN, seen);
        if (this.fromUser != null)
            args.put(FIELD_FROM_USER, fromUser);
        if (this.resolution != null)
            args.put(FIELD_RESOLUTION, resolution.value());
        if (this.resolutionTstamp != null)
            args.put(FIELD_RESOLUTION_TSTAMP, resolutionTstamp);
        return args;
    }

    public static int getUnseenCount(ContentResolver cr){
        String selection =  FIELD_RESOLUTION + "=? AND " + FIELD_SEEN + "=?";
        String[] selectionArgs = new String[]{PairingRequestResolutionEnum.NONE.value(), "0"};
        return getCount(cr, selection, selectionArgs);
    }

    public static int getNonResolvedCount(ContentResolver cr){
        String selection = String.format("%s=?", PairingRequest.FIELD_RESOLUTION);
        String[] selectionArgs = new String[]{PairingRequestResolutionEnum.NONE.value()};
        return getCount(cr, selection, selectionArgs);
    }

    public static int getCount(ContentResolver cr, String selection, String[] selectionArgs){
        Cursor c = cr.query(URI, ID_PROJECTION, selection, selectionArgs, null);
        try {
            if (c != null){
                return c.getCount();
            }
        } catch (Exception e){
            Log.ef(TAG, e, "getCount error");
        } finally {
            if (c != null){
                MiscUtils.closeCursorSilently(c);
            }
        }
        return 0;
    }

    public static int update(ContentResolver cr, ContentValues cv){

        try {
            return cr.update(URI, cv, null, null);
        } catch (Exception e){
            Log.ef(TAG, e, "Exception: update");
        }
        return -1;
    }

    public static int delete(ContentResolver cr, long serverId){
        String where = FIELD_SERVER_ID + "=?";
        String[] whereArgs = new String[]{String.valueOf(serverId)};
        try {
            return cr.delete(URI, where, whereArgs);
        } catch (Exception e){
            Log.ef(TAG, e, "Exception: update");
        }
        return -1;
    }

    public static int updateResolution(ContentResolver cr, String fromUser, PairingRequestResolutionEnum resolution){
        try {

            ContentValues cv = new ContentValues();
            cv.put(FIELD_RESOLUTION, resolution.value());
            cv.put(FIELD_RESOLUTION_TSTAMP, System.currentTimeMillis());

            String where = FIELD_FROM_USER + "=?";
            String[] whereArgs = new String[]{fromUser};

            return cr.update(URI, cv, where, whereArgs);

        } catch (Exception e){
            Log.ef(TAG, e, "Exception: Cannot update item fromUser=%s", fromUser);
        }

        return -1;
    }

    public static int updateResolution(ContentResolver cr, long serverId, PairingRequestResolutionEnum resolution){
        try {

            ContentValues cv = new ContentValues();
            cv.put(FIELD_RESOLUTION, resolution.value());
            cv.put(FIELD_RESOLUTION_TSTAMP, System.currentTimeMillis());

            String where = FIELD_SERVER_ID + "=?";
            String[] whereArgs = new String[]{String.valueOf(serverId)};

            return cr.update(URI, cv, where, whereArgs);

        } catch (Exception e){
            Log.ef(TAG, e, "Exception: Cannot update item serverId=%d", serverId);
        }

        return -1;
    }

    public static List<PairingRequest> getAll(ContentResolver cr, String[] projection) {
        List<PairingRequest> result = new ArrayList<>();

        Cursor c = cr.query(URI, projection, null, null, null);

        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    do {
                        result.add(new PairingRequest(c));
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

    public void setServerId(long serverId) {
        this.serverId = serverId;
    }

    public void setTstamp(long tstamp) {
        this.tstamp = tstamp;
    }

    public void setFromUser(String fromUser) {
        this.fromUser = fromUser;
    }

    public void setResolution(PairingRequestResolutionEnum resolution) {
        this.resolution = resolution;
    }

    public void setResolutionTstamp(Long resolutionTstamp) {
        this.resolutionTstamp = resolutionTstamp;
    }

    public boolean isSeen() {
        return seen;
    }

    public Long getId() {
        return id;
    }

    public long getServerId() {
        return serverId;
    }

    public long getTstamp() {
        return tstamp;
    }

    public String getFromUser() {
        return fromUser;
    }

    public PairingRequestResolutionEnum getResolution() {
        return resolution;
    }

    public Long getResolutionTstamp() {
        return resolutionTstamp;
    }
}
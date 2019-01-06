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

/**
 * Created by Matus on 29.6.2015.
 */
public class Thumbnail implements Parcelable {

    public static final String TAG = "Thumbnail";

    // <ATTRIBUTES>
    protected Long id;
    protected String uri;
    protected String sender;
    protected Long messageId;
    protected byte[] thumbnail;
    // </ATTRIBUTES>

    public static final String TABLE = "Thumbnail";

    /**
     * URI for content provider.<br/>
     */
    public static final Uri URI = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/" + TABLE);
    /**
     * Base URI for contact content provider.<br/>
     * To append with {@link #FIELD_ID}
     *
     * @see android.content.ContentUris#appendId(android.net.Uri.Builder, long)
     */
    public static final Uri ID_URI_BASE = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/" + TABLE + "/");

    public static final String FIELD_ID = "_id";
    /**
     * Originally file storage URI was used.
     * However thumbnails will not be reused, therefore they only make sense in context of ReceivedFile.
     * Unfortunately thumbnail is created before ReceivedFile, therefore cannot use ReceivedFile id.
     * Some unique identifier will be used, this may not have valid URI format!
     */
    public static final String FIELD_URI = "uri";
    /**
     * We must keep sender of this thumbnail for efficient deletion of thread.
     */
    public static final String FIELD_SENDER = "sender";
    /**
     * Might as well keep message id for efficient message deletion.
     */
    public static final String FIELD_MESSAGE_ID = "msgId";
    /**
     * The jpeg in raw bytes.
     */
    public static final String FIELD_THUMBNAIL = "thumbnail";


    public Thumbnail() {
    }

    public Thumbnail(Cursor c) {
        createFromCursor(c);
    }

    public static final String[] FULL_PROJECTION = new String[]{
            FIELD_ID, FIELD_URI, FIELD_SENDER, FIELD_MESSAGE_ID, FIELD_THUMBNAIL
    };

    public static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS "
            + TABLE
            + " ("
            + FIELD_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + FIELD_URI + " TEXT, "
            + FIELD_SENDER + " TEXT, "
            + FIELD_MESSAGE_ID + " INTEGER DEFAULT 0, "
            + FIELD_THUMBNAIL + " BLOB "
            + ");";

    private final void createFromCursor(Cursor c) {
        int colCount = c.getColumnCount();
        for (int i = 0; i < colCount; i++) {
            final String colname = c.getColumnName(i);
            if (FIELD_ID.equals(colname)) {
                this.id = c.getLong(i);
            } else if (FIELD_URI.equals(colname)) {
                this.uri = c.getString(i);
            } else if (FIELD_SENDER.equals(colname)) {
                this.sender = c.getString(i);
            } else if (FIELD_MESSAGE_ID.equals(colname)) {
                this.messageId = c.getLong(i);
            } else if (FIELD_THUMBNAIL.equals(colname)) {
                this.thumbnail = c.getBlob(i);
            } else {
                Log.w(TAG, "Unknown column name: " + colname);
            }
        }
    }

    public ContentValues getDbContentValues() {
        ContentValues args = new ContentValues();
        if (this.id != null)
            args.put(FIELD_ID, id);
        if (this.uri != null)
            args.put(FIELD_URI, uri);
        if (this.sender != null)
            args.put(FIELD_SENDER, sender);
        if (this.messageId != null)
            args.put(FIELD_MESSAGE_ID, messageId);
        if (this.thumbnail != null)
            args.put(FIELD_THUMBNAIL, thumbnail);
        return args;
    }

    public static Thumbnail getByUri(ContentResolver cr, String uri){
        Cursor c = null;

        if (uri == null) {
            throw new NullPointerException("uri");
        }

        try {
            c = cr.query(URI, FULL_PROJECTION, String.format("%s=?", FIELD_URI),
                    new String[] {uri}, null);

            if (c == null || !c.moveToFirst()){
                return null;
            }

            return new Thumbnail(c);
        } catch(Exception e){
            Log.e(TAG, "Exception in loading thumbnail by ReceivedFileId", e);
            return null;
        } finally {
            MiscUtils.closeCursorSilently(c);
        }
    }

    public static int deleteByUri(ContentResolver cr, String uri){
        if (uri == null) {
            throw new NullPointerException("uri");
        }

        try {
            return cr.delete(URI, String.format("%s=?", FIELD_URI), new String[]{uri});
        } catch(Exception e){
            Log.e(TAG, "Exception in deleting thumbnail by ReceivedFileId", e);
        }
        return 0;
    }

    public static int getCountByMessageId(ContentResolver cr, Long messageId){
        if (messageId == null) {
            throw new NullPointerException("messageId");
        }
        try {
            Cursor c = cr.query(URI, new String[]{FIELD_ID}, String.format("%s=?", FIELD_MESSAGE_ID), new String[]{messageId.toString()}, null);
            return (c == null) ? 0 : c.getCount();
        } catch(Exception e){
            Log.e(TAG, "Exception in deleting thumbnail by MessageId", e);
        }
        return 0;
    }

    public static int deleteByMessageId(ContentResolver cr, Long messageId){
        if (messageId == null) {
            throw new NullPointerException("messageId");
        }
        try {
            return cr.delete(URI, String.format("%s=?", FIELD_MESSAGE_ID), new String[]{messageId.toString()});
        } catch(Exception e){
            Log.e(TAG, "Exception in deleting thumbnail by MessageId", e);
        }
        return 0;
    }

    public static int deleteBySender(ContentResolver cr, String sender){
        try {
            return cr.delete(URI, String.format("%s=?", FIELD_SENDER), new String[]{sender});
        } catch(Exception e){
            Log.e(TAG, "Exception in deleting thumbnail by Sender", e);
        }
        return 0;
    }

    public Long getId() {
        return id;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public byte[] getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(byte[] thumbnail) {
        this.thumbnail = thumbnail;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeValue(this.id);
        dest.writeValue(this.uri);
        dest.writeString(this.sender);
        dest.writeValue(this.messageId);
        dest.writeByteArray(this.thumbnail);
    }

    private Thumbnail(Parcel in) {
        this.id = (Long) in.readValue(Long.class.getClassLoader());
        this.uri = in.readString();
        this.sender = in.readString();
        this.messageId = (Long) in.readValue(Long.class.getClassLoader());
        this.thumbnail = in.createByteArray();
    }

    public static final Creator<Thumbnail> CREATOR = new Creator<Thumbnail>() {
        public Thumbnail createFromParcel(Parcel source) {
            return new Thumbnail(source);
        }

        public Thumbnail[] newArray(int size) {
            return new Thumbnail[size];
        }
    };
}

package net.phonex.db.entity;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import net.phonex.core.Constants;
import net.phonex.ft.storage.FileStorageUri;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;

import java.io.File;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;


public class ReceivedFile implements Parcelable {

    public final static String THIS_FILE = "ReceivedFile";
    public final static String TABLE_NAME = "ReceivedFile";
    public final static long INVALID_ID = -1;

    // <FIELD_NAMES>
    public static final String FIELD_ID             = "_id";
    public static final String FIELD_FILE_NONCE     = "fileNonce";
    public static final String FIELD_FILENAME       = "filename";
    public static final String FIELD_PATH           = "path";
    public static final String FIELD_SIZE           = "size";
    public static final String FIELD_DATE_RECEIVED  = "dateReceived";

    public static final String FIELD_THUMBNAIL_NAME  = "thumbnailName";
    public static final String FIELD_FILE_HASH       = "fileHash";
    public static final String FIELD_FILE_ORDER      = "fileOrder";
    public static final String FIELD_MSG_ID          = "msgId";
    public static final String FIELD_TRANSFER_ID     = "transferId";
    public static final String FIELD_MIME_TYPE       = "mimeType";
    public static final String FIELD_TITLE           = "title";
    public static final String FIELD_DESC            = "desc";
    public static final String FIELD_RECORD_TYPE     = "recordType";

    public static final String FIELD_STORAGE_URI    = "storageUri";

    public static final int PEX_RECV_FILE_META = 0;
    public static final int PEX_RECV_FILE_FULL = 1;

    // </FIELD_NAMES>

    // <ATTRIBUTES>
    protected Integer id;
    protected String fileNonce;
    protected String filename;
    protected String path;
    protected Long size;
    protected Date dateReceived;
    protected String thumbnailName;
    protected String fileHash;
    protected Integer fileOrder;
    protected Long msgId;      // Message / notification ID associated to this record.
    protected Long transferId; // FileTransfer ID associated to this record.
    protected String mimeType;
    protected String title;
    protected String desc;
    protected Integer recordType;
    protected String storageUri; // since DB version 37
    // </ATTRIBUTES>


    public static final String[] FULL_PROJECTION = new String[]{
            FIELD_ID, FIELD_FILE_NONCE, FIELD_FILENAME, FIELD_PATH, FIELD_SIZE,
            FIELD_DATE_RECEIVED, FIELD_THUMBNAIL_NAME, FIELD_FILE_HASH, FIELD_FILE_ORDER,
            FIELD_MSG_ID, FIELD_TRANSFER_ID, FIELD_MIME_TYPE, FIELD_TITLE, FIELD_DESC,
            FIELD_RECORD_TYPE, FIELD_STORAGE_URI
    };

    public static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS "
            + TABLE_NAME
            + " ("
            + FIELD_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + FIELD_FILE_NONCE + " TEXT, "
            + FIELD_FILENAME + " TEXT, "
            + FIELD_PATH + " TEXT, "
            + FIELD_SIZE + " INTEGER DEFAULT 0, "
            + FIELD_DATE_RECEIVED + " INTEGER DEFAULT 0, "
            + FIELD_THUMBNAIL_NAME + " TEXT, "
            + FIELD_FILE_HASH + " TEXT, "
            + FIELD_FILE_ORDER + " INTEGER DEFAULT 0, "
            + FIELD_MSG_ID + " INTEGER DEFAULT 0, "
            + FIELD_TRANSFER_ID + " INTEGER DEFAULT 0, "
            + FIELD_MIME_TYPE + " TEXT, "
            + FIELD_TITLE + " TEXT, "
            + FIELD_DESC + " TEXT, "
            + FIELD_RECORD_TYPE + " INTEGER DEFAULT 0, "
            + FIELD_STORAGE_URI + " TEXT "
            + ");";

    /**
     * URI for content provider.<br/>
     */
    public static final Uri URI = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/" + TABLE_NAME);

    /**
     * Base URI for contact content provider.<br/>
     * To append with {@link #FIELD_ID}
     * 
     * @see ContentUris#appendId(android.net.Uri.Builder, long)
     */
    public static final Uri ID_URI_BASE = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/" + TABLE_NAME + "/");

    public ReceivedFile() {

    }

    public String getStorageUri() {
        return storageUri;
    }

    public void setStorageUri(String storageUri) {
        this.storageUri = storageUri;
    }

    public void setFromStorageUri(String storageUri) {
        if (storageUri == null) return;
        this.storageUri = storageUri;
        FileStorageUri uri = new FileStorageUri(storageUri);
        this.filename = uri.getFilename();
        this.path = uri.getAbsolutePath();
    }

    /**
     * Construct a ReceivedFile from a cursor retrieved with a
     * {@link ContentProvider} query on {@link #TABLE_NAME}.
     * 
     * @param c the cursor to unpack
     */
    public ReceivedFile(Cursor c) {
        super();
        createFromDb(c);
    }

    /**
     * Create account wrapper with cursor data.
     * 
     * @param c cursor on the database
     */
    private final void createFromDb(Cursor c) {
        this.createFromCursor(c);
    }

    private final void createFromCursor(Cursor c) {
        int colCount = c.getColumnCount();
        for (int i = 0; i < colCount; i++) {
            final String colname = c.getColumnName(i);
            if (FIELD_ID.equals(colname)) {
                this.id = c.getInt(i);
            } else if (FIELD_FILE_NONCE.equals(colname)) {
                this.fileNonce = c.getString(i);
            } else if (FIELD_FILENAME.equals(colname)) {
                this.filename = c.getString(i);
            } else if (FIELD_PATH.equals(colname)) {
                this.path = c.getString(i);
            } else if (FIELD_SIZE.equals(colname)) {
                this.size = c.getLong(i);
            } else if (FIELD_DATE_RECEIVED.equals(colname)) {
                this.dateReceived = new Date(c.getLong(i));
            } else if (FIELD_THUMBNAIL_NAME.equals(colname)) {
                this.thumbnailName = c.getString(i);
            } else if (FIELD_FILE_HASH.equals(colname)) {
                this.fileHash = c.getString(i);
            } else if (FIELD_FILE_ORDER.equals(colname)) {
                this.fileOrder = c.getInt(i);
            } else if (FIELD_MSG_ID.equals(colname)) {
                this.msgId = c.getLong(i);
            } else if (FIELD_TRANSFER_ID.equals(colname)) {
                this.transferId = c.getLong(i);
            } else if (FIELD_MIME_TYPE.equals(colname)) {
                this.mimeType = c.getString(i);
            } else if (FIELD_TITLE.equals(colname)) {
                this.title = c.getString(i);
            } else if (FIELD_DESC.equals(colname)) {
                this.desc = c.getString(i);
            } else if (FIELD_RECORD_TYPE.equals(colname)) {
                this.recordType = c.getInt(i);
            } else if (FIELD_STORAGE_URI.equals(colname)) {
                this.storageUri = c.getString(i);
            } else {
                Log.w(THIS_FILE, "Unknown column name: " + colname);
            }
        }
    }

    public ContentValues getDbContentValues() {
        ContentValues args = new ContentValues();
        if (id!=null && id!=INVALID_ID)
            args.put(FIELD_ID, id);
        if (this.fileNonce != null)
            args.put(FIELD_FILE_NONCE, fileNonce);
        if (this.filename != null)
            args.put(FIELD_FILENAME, filename);
        if (this.path != null)
            args.put(FIELD_PATH, path);
        if (this.size != null)
            args.put(FIELD_SIZE, size);
        if (this.dateReceived != null)
            args.put(FIELD_DATE_RECEIVED, dateReceived.getTime());
        if (this.thumbnailName != null)
            args.put(FIELD_THUMBNAIL_NAME, thumbnailName);
        if (this.fileHash != null)
            args.put(FIELD_FILE_HASH, fileHash);
        if (this.fileOrder != null)
            args.put(FIELD_FILE_ORDER, fileOrder);
        if (this.msgId != null)
            args.put(FIELD_MSG_ID, msgId);
        if (this.transferId != null)
            args.put(FIELD_TRANSFER_ID, transferId);
        if (this.mimeType != null)
            args.put(FIELD_MIME_TYPE, mimeType);
        if (this.title != null)
            args.put(FIELD_TITLE, title);
        if (this.desc != null)
            args.put(FIELD_DESC, desc);
        if (this.recordType != null)
            args.put(FIELD_RECORD_TYPE, recordType);
        if (this.storageUri != null)
            args.put(FIELD_STORAGE_URI, storageUri);
        return args;
    }

    /**
     * Gets the value of the id property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getId() {
        return id;
    }

    /**
     * Sets the value of the id property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setId(Integer value) {
        this.id = value;
    }
    /**
     * Gets the value of the fileNonce property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getFileNonce() {
        return fileNonce;
    }

    /**
     * Sets the value of the fileNonce property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setFileNonce(String value) {
        this.fileNonce = value;
    }
    /**
     * Gets the value of the filename property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getFilename() {
        return filename;
    }

    /**
     * Sets the value of the filename property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setFilename(String value) {
        this.filename = value;
    }
    /**
     * Gets the value of the path property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPath() {
        return path;
    }

    /**
     * Sets the value of the path property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPath(String value) {
        this.path = value;
    }
    /**
     * Gets the value of the size property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getSize() {
        return size;
    }

    /**
     * Sets the value of the size property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setSize(Long value) {
        this.size = value;
    }
    /**
     * Gets the value of the dateReceived property.
     * 
     * @return
     *     possible object is
     *     {@link Date }
     *     
     */
    public Date getDateReceived() {
        return dateReceived;
    }

    /**
     * Sets the value of the dateReceived property.
     * 
     * @param value
     *     allowed object is
     *     {@link Date }
     *     
     */
    public void setDateReceived(Date value) {
        this.dateReceived = value;
    }

    public String getThumbnailName() {
        return thumbnailName;
    }

    public void setThumbnailName(String thumbnailName) {
        this.thumbnailName = thumbnailName;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public Integer getFileOrder() {
        return fileOrder;
    }

    public void setFileOrder(Integer order) {
        this.fileOrder = order;
    }

    public Long getMsgId() {
        return msgId;
    }

    public void setMsgId(Long msgId) {
        this.msgId = msgId;
    }

    public Long getTransferId() {
        return transferId;
    }

    public void setTransferId(Long transferId) {
        this.transferId = transferId;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public Integer getRecordType() {
        return recordType;
    }

    public void setRecordType(Integer recordType) {
        this.recordType = recordType;
    }

    public static List<ReceivedFile> getFilesByMsgId(ContentResolver cr, long msgId){
        Cursor c = null;
        List<ReceivedFile> lst = new LinkedList<>();

        try {
            c = cr.query(URI, FULL_PROJECTION, String.format("%s=?", FIELD_MSG_ID),
                    new String[] {Long.toString(msgId)}, FIELD_FILE_ORDER + " ASC");

            if (c == null){
                return lst;
            }

            for(; c.moveToNext(); ){
                lst.add(new ReceivedFile(c));
            }

        } catch(Exception e){
            Log.e(THIS_FILE, "Exception in loading file", e);
            return null;
        } finally {
            MiscUtils.closeCursorSilently(c);
        }

        return lst;
    }

    public static ReceivedFile getById(ContentResolver cr, long id){
        Cursor c = null;

        try {
            c = cr.query(URI, FULL_PROJECTION, String.format("%s=?", FIELD_ID),
                    new String[] {Long.toString(id)}, null);

            if (c == null || !c.moveToFirst()){
                return null;
            }

            return new ReceivedFile(c);
        } catch(Exception e){
            Log.e(THIS_FILE, "Exception in loading file", e);
            return null;
        } finally {
            MiscUtils.closeCursorSilently(c);
        }
    }

    public static ReceivedFile getByMsgIdAndFileName(ContentResolver cr, Long msgId, String fname){
        Cursor c = null;
        if (msgId == null){
            return null;
        }

        try {
            c = cr.query(URI, FULL_PROJECTION, String.format("%s=? AND %s=?", FIELD_MSG_ID, FIELD_FILENAME),
                    new String[] {msgId.toString(), fname}, null);

            if (c == null || !c.moveToFirst()){
                return null;
            }

            return new ReceivedFile(c);
        } catch(Exception e){
            Log.e(THIS_FILE, "Exception in loading file", e);
            return null;
        } finally {
            MiscUtils.closeCursorSilently(c);
        }
    }

    /**
     * Delete all potential thumbnails associated with the message id.
     * @param cr
     * @param msgId
     * @return
     */
    public static int deleteThumbs(ContentResolver cr, long msgId, File thumbsDir){
        Cursor c = null;
        try {
            c = cr.query(URI, FULL_PROJECTION, FIELD_MSG_ID + "=?", new String[]{Long.toString(msgId)}, null);
            if (c == null){
                return 0;
            }

            int ctr = 0;
            while(c.moveToNext()){
                final ReceivedFile fl = new ReceivedFile(c);
                final String thumbName = fl.getThumbnailName();
                if (MiscUtils.isEmpty(thumbName)){
                    continue;
                }

                try {
                    final File thumbFile = new File(thumbsDir, thumbName);
                    ctr += thumbFile.delete() ? 1 : 0;
                } catch(Exception ex){
                    Log.e(THIS_FILE, "Could not delete thumb file", ex);
                }
            }

            return ctr;
        } catch(Exception e){
            Log.e(THIS_FILE, "Exception in deleting thumbnails", e);
        } finally {
            MiscUtils.closeCursorSilently(c);
        }

        return 0;
    }

    /**
     * Deletes all file records associated to the given DbMessage.
     */
    public static int deleteByDbMessageId(ContentResolver cr, long msgId){
        try {
            return cr.delete(URI, String.format("%s=?", FIELD_MSG_ID), new String[]{String.format("%d", msgId)});
        } catch(Exception e){
            Log.e(THIS_FILE, "Exception in deleting received file", e);
        }

        return 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeValue(this.id);
        dest.writeString(this.fileNonce);
        dest.writeString(this.filename);
        dest.writeString(this.path);
        dest.writeValue(this.size);
        dest.writeLong(dateReceived != null ? dateReceived.getTime() : -1);
        dest.writeString(this.thumbnailName);
        dest.writeString(this.fileHash);
        dest.writeValue(this.fileOrder);
        dest.writeValue(this.msgId);
        dest.writeValue(this.transferId);
        dest.writeString(this.mimeType);
        dest.writeString(this.title);
        dest.writeString(this.desc);
        dest.writeValue(this.recordType);
        dest.writeString(this.storageUri);
    }

    private ReceivedFile(Parcel in) {
        this.id = (Integer) in.readValue(Integer.class.getClassLoader());
        this.fileNonce = in.readString();
        this.filename = in.readString();
        this.path = in.readString();
        this.size = (Long) in.readValue(Long.class.getClassLoader());
        long tmpDateReceived = in.readLong();
        this.dateReceived = tmpDateReceived == -1 ? null : new Date(tmpDateReceived);
        this.thumbnailName = in.readString();
        this.fileHash = in.readString();
        this.fileOrder = (Integer) in.readValue(Integer.class.getClassLoader());
        this.msgId = (Long) in.readValue(Integer.class.getClassLoader());
        this.transferId = (Long) in.readValue(Integer.class.getClassLoader());
        this.mimeType = in.readString();
        this.title = in.readString();
        this.desc = in.readString();
        this.recordType = (Integer) in.readValue(Integer.class.getClassLoader());
        this.storageUri = in.readString();
    }

    public static final Creator<ReceivedFile> CREATOR = new Creator<ReceivedFile>() {
        public ReceivedFile createFromParcel(Parcel source) {
            return new ReceivedFile(source);
        }

        public ReceivedFile[] newArray(int size) {
            return new ReceivedFile[size];
        }
    };

    public boolean isSecureStorage() {
        return storageUri != null && FileStorageUri.STORAGE_SCHEME_SECURE.equals(Uri.parse(storageUri).getScheme());
    }

    public Uri buildUri() {
        if (storageUri != null) {
            return Uri.parse(storageUri);
        }
        Uri.Builder builder = new Uri.Builder();
        builder.scheme(isSecureStorage() ? FileStorageUri.STORAGE_SCHEME_SECURE : FileStorageUri.STORAGE_SCHEME_NORMAL);
        File file = new File(path);
        builder.path(file.getParentFile().getAbsolutePath());
        builder.appendQueryParameter(FileStorageUri.STORAGE_FILENAME, filename);
        builder.appendQueryParameter(FileStorageUri.STORAGE_FILESYSTEM_NAME, filename);
        return builder.build();
    }

}
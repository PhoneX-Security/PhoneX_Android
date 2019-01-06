package net.phonex.db.entity;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import net.phonex.core.Constants;
import net.phonex.ft.DHKeyHelper;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import android.database.Cursor;

import java.io.File;
import java.io.IOException;
import java.util.Date;

/**
 * FileTransfer record, storing transfer state and related information.
 * Created by dusanklinec on 04.03.15.
 */
public class FileTransfer implements Parcelable {
    private static final String TAG = "FileTransfer";
    public static final String TABLE = "FileTransfer";
    public static final String FIELD_ID = "id";
    public static final String FIELD_MESSAGE_ID = "messageId";
    public static final String FIELD_IS_OUTGOING = "isOutgoing";
    public static final String FIELD_NONCE2 = "nonce2";
    public static final String FIELD_NONCE1 = "nonce1";
    public static final String FIELD_NONCEB = "nonceb";
    public static final String FIELD_SALT1 = "salt1";
    public static final String FIELD_SALTB = "saltb";
    public static final String FIELD_C = "c";
    public static final String FIELD_U_KEY_DATA = "uKeyData";
    public static final String FIELD_META_FILE = "metaFile";
    public static final String FIELD_META_HASH = "metaHash";
    public static final String FIELD_META_STATE = "metaState";
    public static final String FIELD_META_SIZE = "metaSize";
    public static final String FIELD_META_PREP_REC = "metaPrepRec";
    public static final String FIELD_PACK_FILE = "packFile";
    public static final String FIELD_PACK_HASH = "packHash";
    public static final String FIELD_PACK_STATE = "packState";
    public static final String FIELD_PACK_SIZE = "packSize";
    public static final String FIELD_PACK_PREP_REC = "packPrepRec";
    public static final String FIELD_META_DATE = "metaDate";
    public static final String FIELD_NUM_OF_FILES = "numOfFiles";
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_DESCR = "descr";
    public static final String FIELD_THUMB_DIR = "thumbDir";
    public static final String FIELD_SHOULD_DELETE_FROM_SERVER = "shouldDeleteFromServer";
    public static final String FIELD_DELETED_FROM_SERVER = "deletedFromServer";
    public static final String FIELD_DATE_CREATED = "dateCreated";
    public static final String FIELD_DATE_FINISHED = "dateFinished";
    public static final String FIELD_STATUS_CODE = "statusCode";

    public static final int FILEDOWN_TYPE_NONE=0;        // Stage not reached yet.
    public static final int FILEDOWN_TYPE_STARTED=1;     // Stage started, not yet finished, maybe interrupted during progress.
    public static final int FILEDOWN_TYPE_DONE=2;        // Stage finished (either finished of skipped).

    public static final String[] FULL_PROJECTION = new String[]{
            FIELD_ID, FIELD_MESSAGE_ID, FIELD_IS_OUTGOING, FIELD_NONCE2, FIELD_NONCE1,
            FIELD_NONCEB, FIELD_SALT1, FIELD_SALTB, FIELD_C, FIELD_U_KEY_DATA,
            FIELD_META_FILE, FIELD_META_HASH, FIELD_META_STATE, FIELD_META_SIZE, FIELD_META_PREP_REC,
            FIELD_PACK_FILE, FIELD_PACK_HASH, FIELD_PACK_STATE, FIELD_PACK_SIZE, FIELD_PACK_PREP_REC,
            FIELD_META_DATE, FIELD_NUM_OF_FILES, FIELD_TITLE, FIELD_DESCR, FIELD_THUMB_DIR,
            FIELD_SHOULD_DELETE_FROM_SERVER, FIELD_DELETED_FROM_SERVER, FIELD_DATE_CREATED,
            FIELD_DATE_FINISHED, FIELD_STATUS_CODE
    };

    public static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS "
            + TABLE
            + " ("
            + FIELD_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + FIELD_MESSAGE_ID + " INTEGER DEFAULT 0, "
            + FIELD_IS_OUTGOING + " INTEGER DEFAULT 0, "
            + FIELD_NONCE2 + " TEXT, "
            + FIELD_NONCE1 + " TEXT, "
            + FIELD_NONCEB + " TEXT, "
            + FIELD_SALT1 + " TEXT, "
            + FIELD_SALTB + " TEXT, "
            + FIELD_C + " TEXT, "
            + FIELD_U_KEY_DATA + " BLOB, "
            + FIELD_META_FILE + " TEXT, "
            + FIELD_META_HASH + " TEXT, "
            + FIELD_META_STATE + " INTEGER DEFAULT 0, "
            + FIELD_META_SIZE + " INTEGER DEFAULT 0, "
            + FIELD_META_PREP_REC + " BLOB, "
            + FIELD_PACK_FILE + " TEXT, "
            + FIELD_PACK_HASH + " TEXT, "
            + FIELD_PACK_STATE + " INTEGER DEFAULT 0, "
            + FIELD_PACK_SIZE + " INTEGER DEFAULT 0, "
            + FIELD_PACK_PREP_REC + " BLOB, "
            + FIELD_META_DATE + " INTEGER DEFAULT 0, "
            + FIELD_NUM_OF_FILES + " INTEGER DEFAULT 0, "
            + FIELD_TITLE + " TEXT, "
            + FIELD_DESCR + " TEXT, "
            + FIELD_THUMB_DIR + " TEXT, "
            + FIELD_SHOULD_DELETE_FROM_SERVER + " INTEGER DEFAULT 0, "
            + FIELD_DELETED_FROM_SERVER + " INTEGER DEFAULT 0, "
            + FIELD_DATE_CREATED + " INTEGER DEFAULT 0, "
            + FIELD_DATE_FINISHED + " INTEGER DEFAULT 0, "
            + FIELD_STATUS_CODE + " INTEGER DEFAULT 0 "
            + ");";

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

    private Long id;
    private Long messageId;
    private Boolean isOutgoing; // boolean redundant informative flag to avoid join.

    private String nonce2;
    private String nonce1;
    private String nonceb;
    private String salt1;
    private String saltb;
    private String c;
    private byte[] uKeyData;   // Required for upload.

    private String metaFile;
    private String metaHash;
    private Integer metaState = FILEDOWN_TYPE_NONE;
    private Long metaSize;
    private byte[] metaPrepRec; // Required for upload.

    private String packFile;
    private String packHash;
    private Integer packState = FILEDOWN_TYPE_NONE;
    private Long packSize;
    private byte[] packPrepRec; // Required for upload

    private Date metaDate;
    private Integer numOfFiles;
    private String title;
    private String descr;

    private String thumbDir;
    private Boolean shouldDeleteFromServer;
    private Boolean deletedFromServer;

    private Date dateCreated;
    private Date dateFinished;
    private Integer statusCode;

    public FileTransfer() {
    }

    private final void createFromCursor(Cursor c) {
        int colCount = c.getColumnCount();
        for (int i = 0; i < colCount; i++) {
            final String colname = c.getColumnName(i);
            if (FIELD_ID.equals(colname)) {
                this.id = c.getLong(i);
            } else if (FIELD_MESSAGE_ID.equals(colname)) {
                this.messageId = c.getLong(i);
            } else if (FIELD_IS_OUTGOING.equals(colname)) {
                this.isOutgoing = (Boolean) (c.getInt(i) == 1);
            } else if (FIELD_NONCE2.equals(colname)) {
                this.nonce2 = c.getString(i);
            } else if (FIELD_NONCE1.equals(colname)) {
                this.nonce1 = c.getString(i);
            } else if (FIELD_NONCEB.equals(colname)) {
                this.nonceb = c.getString(i);
            } else if (FIELD_SALT1.equals(colname)) {
                this.salt1 = c.getString(i);
            } else if (FIELD_SALTB.equals(colname)) {
                this.saltb = c.getString(i);
            } else if (FIELD_C.equals(colname)) {
                this.c = c.getString(i);
            } else if (FIELD_U_KEY_DATA.equals(colname)) {
                this.uKeyData = c.getBlob(i);
            } else if (FIELD_META_FILE.equals(colname)) {
                this.metaFile = c.getString(i);
            } else if (FIELD_META_HASH.equals(colname)) {
                this.metaHash = c.getString(i);
            } else if (FIELD_META_STATE.equals(colname)) {
                this.metaState = c.getInt(i);
            } else if (FIELD_META_SIZE.equals(colname)) {
                this.metaSize = c.getLong(i);
            } else if (FIELD_META_PREP_REC.equals(colname)) {
                this.metaPrepRec = c.getBlob(i);
            } else if (FIELD_PACK_FILE.equals(colname)) {
                this.packFile = c.getString(i);
            } else if (FIELD_PACK_HASH.equals(colname)) {
                this.packHash = c.getString(i);
            } else if (FIELD_PACK_STATE.equals(colname)) {
                this.packState = c.getInt(i);
            } else if (FIELD_PACK_SIZE.equals(colname)) {
                this.packSize = c.getLong(i);
            } else if (FIELD_PACK_PREP_REC.equals(colname)) {
                this.packPrepRec = c.getBlob(i);
            } else if (FIELD_META_DATE.equals(colname)) {
                this.metaDate = new Date(c.getLong(i));
            } else if (FIELD_NUM_OF_FILES.equals(colname)) {
                this.numOfFiles = c.getInt(i);
            } else if (FIELD_TITLE.equals(colname)) {
                this.title = c.getString(i);
            } else if (FIELD_DESCR.equals(colname)) {
                this.descr = c.getString(i);
            } else if (FIELD_THUMB_DIR.equals(colname)) {
                this.thumbDir = c.getString(i);
            } else if (FIELD_SHOULD_DELETE_FROM_SERVER.equals(colname)) {
                this.shouldDeleteFromServer = (Boolean) (c.getInt(i) == 1);
            } else if (FIELD_DELETED_FROM_SERVER.equals(colname)) {
                this.deletedFromServer = (Boolean) (c.getInt(i) == 1);
            } else if (FIELD_DATE_CREATED.equals(colname)) {
                this.dateCreated = new Date(c.getLong(i));
            } else if (FIELD_DATE_FINISHED.equals(colname)) {
                this.dateFinished = new Date(c.getLong(i));
            } else if (FIELD_STATUS_CODE.equals(colname)) {
                this.statusCode = c.getInt(i);
            } else {
                Log.w(TAG, "Unknown column name: " + colname);
            }
        }
    }

    public ContentValues getDbContentValues() {
        ContentValues args = new ContentValues();
        if (this.id != null)
            args.put(FIELD_ID, id);
        if (this.messageId != null)
            args.put(FIELD_MESSAGE_ID, messageId);
        if (this.isOutgoing != null)
            args.put(FIELD_IS_OUTGOING, isOutgoing ? 1 : 0);
        if (this.nonce2 != null)
            args.put(FIELD_NONCE2, nonce2);
        if (this.nonce1 != null)
            args.put(FIELD_NONCE1, nonce1);
        if (this.nonceb != null)
            args.put(FIELD_NONCEB, nonceb);
        if (this.salt1 != null)
            args.put(FIELD_SALT1, salt1);
        if (this.saltb != null)
            args.put(FIELD_SALTB, saltb);
        if (this.c != null)
            args.put(FIELD_C, c);
        if (this.uKeyData != null)
            args.put(FIELD_U_KEY_DATA, uKeyData);
        if (this.metaFile != null)
            args.put(FIELD_META_FILE, metaFile);
        if (this.metaHash != null)
            args.put(FIELD_META_HASH, metaHash);
        if (this.metaState != null)
            args.put(FIELD_META_STATE, metaState);
        if (this.metaSize != null)
            args.put(FIELD_META_SIZE, metaSize);
        if (this.metaPrepRec != null)
            args.put(FIELD_META_PREP_REC, metaPrepRec);
        if (this.packFile != null)
            args.put(FIELD_PACK_FILE, packFile);
        if (this.packHash != null)
            args.put(FIELD_PACK_HASH, packHash);
        if (this.packState != null)
            args.put(FIELD_PACK_STATE, packState);
        if (this.packSize != null)
            args.put(FIELD_PACK_SIZE, packSize);
        if (this.packPrepRec != null)
            args.put(FIELD_PACK_PREP_REC, packPrepRec);
        if (this.metaDate != null)
            args.put(FIELD_META_DATE, metaDate.getTime());
        if (this.numOfFiles != null)
            args.put(FIELD_NUM_OF_FILES, numOfFiles);
        if (this.title != null)
            args.put(FIELD_TITLE, title);
        if (this.descr != null)
            args.put(FIELD_DESCR, descr);
        if (this.thumbDir != null)
            args.put(FIELD_THUMB_DIR, thumbDir);
        if (this.shouldDeleteFromServer != null)
            args.put(FIELD_SHOULD_DELETE_FROM_SERVER, shouldDeleteFromServer ? 1 : 0);
        if (this.deletedFromServer != null)
            args.put(FIELD_DELETED_FROM_SERVER, deletedFromServer ? 1 : 0);
        if (this.dateCreated != null)
            args.put(FIELD_DATE_CREATED, dateCreated.getTime());
        if (this.dateFinished != null)
            args.put(FIELD_DATE_FINISHED, dateFinished.getTime());
        if (this.statusCode != null)
            args.put(FIELD_STATUS_CODE, statusCode);
        return args;
    }

    public FileTransfer(Cursor c){
        createFromCursor(c);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeValue(this.id);
        dest.writeValue(this.messageId);
        dest.writeValue(this.isOutgoing);
        dest.writeString(this.nonce2);
        dest.writeString(this.nonce1);
        dest.writeString(this.nonceb);
        dest.writeString(this.salt1);
        dest.writeString(this.saltb);
        dest.writeString(this.c);
        dest.writeByteArray(this.uKeyData);
        dest.writeString(this.metaFile);
        dest.writeString(this.metaHash);
        dest.writeValue(this.metaState);
        dest.writeValue(this.metaSize);
        dest.writeByteArray(this.metaPrepRec);
        dest.writeString(this.packFile);
        dest.writeString(this.packHash);
        dest.writeValue(this.packState);
        dest.writeValue(this.packSize);
        dest.writeByteArray(this.packPrepRec);
        dest.writeLong(metaDate != null ? metaDate.getTime() : -1);
        dest.writeValue(this.numOfFiles);
        dest.writeString(this.title);
        dest.writeString(this.descr);
        dest.writeString(this.thumbDir);
        dest.writeValue(this.shouldDeleteFromServer);
        dest.writeValue(this.deletedFromServer);
        dest.writeLong(dateCreated != null ? dateCreated.getTime() : -1);
        dest.writeLong(dateFinished != null ? dateFinished.getTime() : -1);
        dest.writeValue(this.statusCode);
    }

    private FileTransfer(Parcel in) {
        this.id = (Long) in.readValue(Long.class.getClassLoader());
        this.messageId = (Long) in.readValue(Long.class.getClassLoader());
        this.isOutgoing = (Boolean) in.readValue(Boolean.class.getClassLoader());
        this.nonce2 = in.readString();
        this.nonce1 = in.readString();
        this.nonceb = in.readString();
        this.salt1 = in.readString();
        this.saltb = in.readString();
        this.c = in.readString();
        this.uKeyData = in.createByteArray();
        this.metaFile = in.readString();
        this.metaHash = in.readString();
        this.metaState = (Integer) in.readValue(Integer.class.getClassLoader());
        this.metaSize = (Long) in.readValue(Long.class.getClassLoader());
        this.metaPrepRec = in.createByteArray();
        this.packFile = in.readString();
        this.packHash = in.readString();
        this.packState = (Integer) in.readValue(Integer.class.getClassLoader());
        this.packSize = (Long) in.readValue(Long.class.getClassLoader());
        this.packPrepRec = in.createByteArray();
        long tmpMetaDate = in.readLong();
        this.metaDate = tmpMetaDate == -1 ? null : new Date(tmpMetaDate);
        this.numOfFiles = (Integer) in.readValue(Integer.class.getClassLoader());
        this.title = in.readString();
        this.descr = in.readString();
        this.thumbDir = in.readString();
        this.shouldDeleteFromServer = (Boolean) in.readValue(Boolean.class.getClassLoader());
        this.deletedFromServer = (Boolean) in.readValue(Boolean.class.getClassLoader());
        long tmpDateCreated = in.readLong();
        this.dateCreated = tmpDateCreated == -1 ? null : new Date(tmpDateCreated);
        long tmpDateFinished = in.readLong();
        this.dateFinished = tmpDateFinished == -1 ? null : new Date(tmpDateFinished);
        this.statusCode = (Integer) in.readValue(Integer.class.getClassLoader());
    }

    public static final Parcelable.Creator<FileTransfer> CREATOR = new Parcelable.Creator<FileTransfer>() {
        public FileTransfer createFromParcel(Parcel source) {
            return new FileTransfer(source);
        }

        public FileTransfer[] newArray(int size) {
            return new FileTransfer[size];
        }
    };

    public static FileTransfer initWithNonce2(String nonce2, long msgId, ContentResolver cr) {
        Cursor c = null;
        try {
            String[] args = null;
            String where  = null;
            if (MiscUtils.isEmpty(nonce2)){
                where = String.format("%s=?", FIELD_MESSAGE_ID);
                args  = new String[] {String.format("%d", msgId)};
            } else {
                where = String.format("%s=? AND %s=?", FIELD_MESSAGE_ID, FIELD_NONCE2);
                args  = new String[] {String.format("%d", msgId), nonce2};
            }

            c = cr.query(URI, FULL_PROJECTION, where, args, null);

            if (c == null || !c.moveToFirst()){
                return null;
            }

            return new FileTransfer(c);
        } catch(Throwable e){
            Log.e(TAG, "Exception in loading file, exception", e);
            return null;
        } finally {
            MiscUtils.closeCursorSilently(c);
        }
    }

    public boolean isMarkedToDeleteFromServer() {
        return shouldDeleteFromServer != null && shouldDeleteFromServer.booleanValue();
    }

    public boolean isMetaDone() {
        return metaState != null && metaState.intValue() == FILEDOWN_TYPE_DONE;
    }

    public boolean isPackDone() {
        return packState != null && packState.intValue() == FILEDOWN_TYPE_DONE;
    }

    public boolean isKeyComputingDone() {
        return (c != null && c.length() > 0) || isMetaDone() || isPackDone();
    }

    public void clearCryptoMaterial() {
        nonce1 = "";
        nonceb = "";
        salt1  = "";
        saltb  = "";
        c      = "";
        uKeyData    = new byte[] {};
        metaPrepRec = new byte[] {};
        packPrepRec = new byte[] {};
    }

    public static int deleteByDbMessageId(long msgId, ContentResolver cr) {
        try {
            return cr.delete(URI, String.format("%s=?", FIELD_MESSAGE_ID), new String[]{String.format("%d", msgId)});
        } catch(Exception e){
            Log.e(TAG, "Exception in deleting transfer file", e);
        }

        return 0;
    }

    public static FileTransfer deleteTempFileByDbMessageId(long msgId, Context ctxt) {
        Cursor c = null;
        try {
            c = ctxt.getContentResolver().query(URI, FULL_PROJECTION, String.format("%s=?", FIELD_MESSAGE_ID),
                    new String[]{String.format("%d", msgId)},
                    null);

            if (c == null || !c.moveToFirst()){
                return null;
            }

            FileTransfer ft = new FileTransfer(c);
            ft.removeTempFiles(ctxt);
            return ft;

        } catch(Exception e){
            Log.e(TAG, "Exception in deleting temp files", e);

        } finally {
            MiscUtils.closeCursorSilently(c);
        }

        return null;
    }

    public static void removeTempFtFile(String path) {
        try {
            if (!MiscUtils.isEmpty(path)) {
                MiscUtils.delete(new File(path));
            }
        } catch(Exception e){
            Log.e(TAG, "Exception in tmpFile removal", e);
        }
    }

    public void removeDownloadFile(Context ctxt, int idx) throws IOException {
        final File file = DHKeyHelper.getFileNameForDownload(ctxt, idx, nonce2);
        if (MiscUtils.fileExistsAndIsAfile(file)){
            Log.vf(TAG, "Deleting download file: %s", file);
            MiscUtils.delete(file);
        }
    }

    public void removeTempFiles(Context ctxt) throws IOException {
        removeTempFtFile(metaFile);
        removeTempFtFile(packFile);
        removeDownloadFile(ctxt, DHKeyHelper.META_IDX);
        removeDownloadFile(ctxt, DHKeyHelper.ARCH_IDX);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public Boolean getIsOutgoing() {
        return isOutgoing;
    }

    public void setIsOutgoing(Boolean isOutgoing) {
        this.isOutgoing = isOutgoing;
    }

    public String getNonce2() {
        return nonce2;
    }

    public void setNonce2(String nonce2) {
        this.nonce2 = nonce2;
    }

    public String getNonce1() {
        return nonce1;
    }

    public void setNonce1(String nonce1) {
        this.nonce1 = nonce1;
    }

    public String getNonceb() {
        return nonceb;
    }

    public void setNonceb(String nonceb) {
        this.nonceb = nonceb;
    }

    public String getSalt1() {
        return salt1;
    }

    public void setSalt1(String salt1) {
        this.salt1 = salt1;
    }

    public String getSaltb() {
        return saltb;
    }

    public void setSaltb(String saltb) {
        this.saltb = saltb;
    }

    public String getC() {
        return c;
    }

    public void setC(String c) {
        this.c = c;
    }

    public byte[] getuKeyData() {
        return uKeyData;
    }

    public void setuKeyData(byte[] uKeyData) {
        this.uKeyData = uKeyData;
    }

    public String getMetaFile() {
        return metaFile;
    }

    public void setMetaFile(String metaFile) {
        this.metaFile = metaFile;
    }

    public String getMetaHash() {
        return metaHash;
    }

    public void setMetaHash(String metaHash) {
        this.metaHash = metaHash;
    }

    public Integer getMetaState() {
        return metaState;
    }

    public void setMetaState(Integer metaState) {
        this.metaState = metaState;
    }

    public Long getMetaSize() {
        return metaSize;
    }

    public void setMetaSize(Long metaSize) {
        this.metaSize = metaSize;
    }

    public byte[] getMetaPrepRec() {
        return metaPrepRec;
    }

    public void setMetaPrepRec(byte[] metaPrepRec) {
        this.metaPrepRec = metaPrepRec;
    }

    public String getPackFile() {
        return packFile;
    }

    public void setPackFile(String packFile) {
        this.packFile = packFile;
    }

    public String getPackHash() {
        return packHash;
    }

    public void setPackHash(String packHash) {
        this.packHash = packHash;
    }

    public Integer getPackState() {
        return packState;
    }

    public void setPackState(Integer packState) {
        this.packState = packState;
    }

    public Long getPackSize() {
        return packSize;
    }

    public void setPackSize(Long packSize) {
        this.packSize = packSize;
    }

    public byte[] getPackPrepRec() {
        return packPrepRec;
    }

    public void setPackPrepRec(byte[] packPrepRec) {
        this.packPrepRec = packPrepRec;
    }

    public Date getMetaDate() {
        return metaDate;
    }

    public void setMetaDate(Date metaDate) {
        this.metaDate = metaDate;
    }

    public Integer getNumOfFiles() {
        return numOfFiles;
    }

    public void setNumOfFiles(Integer numOfFiles) {
        this.numOfFiles = numOfFiles;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescr() {
        return descr;
    }

    public void setDescr(String descr) {
        this.descr = descr;
    }

    public String getThumbDir() {
        return thumbDir;
    }

    public void setThumbDir(String thumbDir) {
        this.thumbDir = thumbDir;
    }

    public Boolean getShouldDeleteFromServer() {
        return shouldDeleteFromServer;
    }

    public void setShouldDeleteFromServer(Boolean shouldDeleteFromServer) {
        this.shouldDeleteFromServer = shouldDeleteFromServer;
    }

    public Boolean getDeletedFromServer() {
        return deletedFromServer;
    }

    public void setDeletedFromServer(Boolean deletedFromServer) {
        this.deletedFromServer = deletedFromServer;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }

    public Date getDateFinished() {
        return dateFinished;
    }

    public void setDateFinished(Date dateFinished) {
        this.dateFinished = dateFinished;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }
}

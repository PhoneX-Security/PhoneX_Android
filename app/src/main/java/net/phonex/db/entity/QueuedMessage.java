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
import net.phonex.util.Log;

import java.util.Arrays;
import java.util.Date;


/**
 * Created by miroc on 13.10.14.
 */
public class QueuedMessage implements Parcelable {

    public final static String THIS_FILE = "QueuedMessage";
    public final static String TABLE_NAME = "message_queue";
    public final static long INVALID_ID = -1;

    // <FIELD_NAMES>
    public static final String FIELD_ID                          = "_id";
    public static final String FIELD_TIME                        = "time";
    public static final String FIELD_FROM                        = "sender";
    public static final String FIELD_TO                          = "receiver";
    public static final String FIELD_IS_OUTGOING                 = "isOutgoing";
    public static final String FIELD_IS_OFFLINE                  = "isOffline";
    public static final String FIELD_IS_PROCESSED                = "isProcessed";
    public static final String FIELD_SEND_ATTEMPT_COUNTER        = "sendAttempts";
    public static final String FIELD_LAST_SEND_CALL              = "lastSendCall";
    public static final String FIELD_SEND_COUNTER                = "sendCounter";
    public static final String FIELD_TRANSPORT_PROTOCOL_TYPE     = "transportProtocolType";
    public static final String FIELD_TRANSPORT_PROTOCOL_VERSION  = "transportProtocolVersion";
    public static final String FIELD_MESSAGE_PROTOCOL_TYPE       = "messageProtocolType";
    public static final String FIELD_MESSAGE_PROTOCOL_SUB_TYPE   = "messageProtocolSubType";
    public static final String FIELD_MESSAGE_PROTOCOL_VERSION    = "messageProtocolVersion";
    public static final String FIELD_MIME_TYPE                   = "mimeType";
    public static final String FIELD_TRANSPORT_PAYLOAD           = "transportPayload";
    public static final String FIELD_ENVELOPE_PAYLOAD            = "envelopePayload";
    public static final String FIELD_FINAL_MESSAGE               = "finalMessage";
    public static final String FIELD_FINAL_MESSAGE_HASH          = "finalMessageHash";
    public static final String FIELD_REFERENCED_ID               = "referencedId";
    public static final String FIELD_RESEND_TIME                 = "resendTime";
    // </FIELD_NAMES>

    // <ATTRIBUTES>
    protected Integer id;
    protected Long time;
    protected String from;
    protected String to;
    protected boolean isOutgoing;
    protected boolean isOffline = false;
    protected boolean isProcessed;
    protected Integer sendCounter;
    protected Integer sendAttemptCounter;
    protected Long lastSendCall;
    protected Integer transportProtocolType;
    protected Integer transportProtocolVersion;
    protected Integer messageProtocolType;
    protected Integer messageProtocolSubType;
    protected Integer messageProtocolVersion;
    protected String mimeType;
    protected byte[] transportPayload;
    protected byte[] envelopePayload;
    protected String finalMessage;
    protected String finalMessageHash;
    protected Integer referencedId;
    protected Long resendTime;
    // </ATTRIBUTES>

    public static final String[] FULL_PROJECTION = new String[] {
            FIELD_ID, FIELD_TIME,
            FIELD_FROM, FIELD_TO,
            FIELD_IS_OUTGOING, FIELD_IS_PROCESSED,
            FIELD_SEND_COUNTER, FIELD_TRANSPORT_PROTOCOL_TYPE,
            FIELD_TRANSPORT_PROTOCOL_VERSION, FIELD_MESSAGE_PROTOCOL_TYPE,FIELD_MESSAGE_PROTOCOL_SUB_TYPE,
            FIELD_MESSAGE_PROTOCOL_VERSION, FIELD_MIME_TYPE,
            FIELD_TRANSPORT_PAYLOAD, FIELD_ENVELOPE_PAYLOAD,
            FIELD_FINAL_MESSAGE, FIELD_FINAL_MESSAGE_HASH,
            FIELD_REFERENCED_ID, FIELD_RESEND_TIME,FIELD_IS_OFFLINE,
            FIELD_SEND_ATTEMPT_COUNTER, FIELD_LAST_SEND_CALL
    };

    public static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS "
            + TABLE_NAME
            + " ("
            + FIELD_ID                          	 + " INTEGER DEFAULT 0 PRIMARY KEY AUTOINCREMENT, "
            + FIELD_TIME                        	 + " INTEGER DEFAULT 0, "
            + FIELD_FROM                        	 + " TEXT, "
            + FIELD_TO                          	 + " TEXT, "
            + FIELD_IS_OUTGOING                 	 + " INTEGER DEFAULT 0, "
            + FIELD_IS_OFFLINE                 	     + " INTEGER DEFAULT 0, "
            + FIELD_IS_PROCESSED                	 + " INTEGER DEFAULT 0, "
            + FIELD_SEND_COUNTER                	 + " INTEGER DEFAULT 0, "
            + FIELD_SEND_ATTEMPT_COUNTER             + " INTEGER DEFAULT 0, "
            + FIELD_LAST_SEND_CALL                	 + " INTEGER DEFAULT 0, "
            + FIELD_TRANSPORT_PROTOCOL_TYPE     	 + " INTEGER DEFAULT 0, "
            + FIELD_TRANSPORT_PROTOCOL_VERSION  	 + " INTEGER DEFAULT 0, "
            + FIELD_MESSAGE_PROTOCOL_TYPE       	 + " INTEGER DEFAULT 0, "
            + FIELD_MESSAGE_PROTOCOL_SUB_TYPE        + " INTEGER DEFAULT 0, "
            + FIELD_MESSAGE_PROTOCOL_VERSION    	 + " INTEGER DEFAULT 0, "
            + FIELD_MIME_TYPE                   	 + " TEXT, "
            + FIELD_TRANSPORT_PAYLOAD           	 + " BLOB, "
            + FIELD_ENVELOPE_PAYLOAD            	 + " BLOB, "
            + FIELD_FINAL_MESSAGE               	 + " TEXT, "
            + FIELD_FINAL_MESSAGE_HASH          	 + " TEXT, "
            + FIELD_REFERENCED_ID               	 + " INTEGER DEFAULT 0, "
            + FIELD_RESEND_TIME                 	 + " INTEGER DEFAULT 0"
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

    public QueuedMessage() {

    }

    /**
     * Construct a QueuedMessage from a cursor retrieved with a
     * {@link ContentProvider} query on {@link #TABLE_NAME}.
     *
     * @param c the cursor to unpack
     */
    public QueuedMessage(Cursor c) {
        super();
        createFromDb(c);
    }

    /**
     * Construct from parcelable <br/>
     * Only used by {@link #CREATOR}
     *
     * @param in parcelable to build from
     */
    public QueuedMessage(Parcel in) {
        id = in.readInt();
        time = in.readLong();
        from = getReadParcelableString(in.readString());
        to = getReadParcelableString(in.readString());
        isOutgoing = (in.readInt() != 0) ? true:false;
        isOffline = (in.readInt() != 0) ? true:false;
        isProcessed = (in.readInt() != 0) ? true:false;
        sendCounter = in.readInt();
        sendAttemptCounter = in.readInt();
        lastSendCall = in.readLong();
        transportProtocolType = in.readInt();
        transportProtocolVersion = in.readInt();
        messageProtocolType = in.readInt();
        messageProtocolSubType = in.readInt();
        messageProtocolVersion = in.readInt();
        mimeType = getReadParcelableString(in.readString());
        in.readByteArray(transportPayload);
        in.readByteArray(envelopePayload);
        finalMessage = getReadParcelableString(in.readString());
        finalMessageHash = getReadParcelableString(in.readString());
        referencedId = in.readInt();
        resendTime = in.readLong();
    }

    /**
     * Parcelable creator. So that it can be passed as an argument of the aidl
     * interface
     */
    public static final Parcelable.Creator<QueuedMessage> CREATOR = new Parcelable.Creator<QueuedMessage>() {
        public QueuedMessage createFromParcel(Parcel in) {
            return new QueuedMessage(in);
        }

        public QueuedMessage[] newArray(int size) {
            return new QueuedMessage[size];
        }
    };

    /**
     * @see Parcelable#describeContents()
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * @see Parcelable#writeToParcel(Parcel, int)
     */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id != null ? id : 0);
        dest.writeLong(time);
        dest.writeString(getWriteParcelableString(from));
        dest.writeString(getWriteParcelableString(to));
        dest.writeInt(isOutgoing ? 1 : 0);
        dest.writeInt(isOffline ? 1 : 0);
        dest.writeInt(isProcessed ? 1 : 0);
        dest.writeInt(sendCounter != null ? sendCounter : 0);
        dest.writeInt(sendAttemptCounter != null ? sendAttemptCounter : 0);
        dest.writeLong(lastSendCall != null ? lastSendCall : 0);
        dest.writeInt(transportProtocolType != null ? transportProtocolType : 0);
        dest.writeInt(transportProtocolVersion != null ? transportProtocolVersion : 0);
        dest.writeInt(messageProtocolType != null ? messageProtocolType : 0);
        dest.writeInt(messageProtocolSubType != null ? messageProtocolSubType : 0);
        dest.writeInt(messageProtocolVersion != null ? messageProtocolVersion : 0);
        dest.writeString(getWriteParcelableString(mimeType));
        dest.writeByteArray(transportPayload != null ? transportPayload : new byte[0]);
        dest.writeByteArray(envelopePayload != null ? envelopePayload : new byte[0]);
        dest.writeString(getWriteParcelableString(finalMessage));
        dest.writeString(getWriteParcelableString(finalMessageHash));
        dest.writeInt(referencedId != null ? referencedId : 0);
        dest.writeLong(resendTime);
    }

    private String getWriteParcelableString(String str) {
        return (str == null) ? "null" : str;
    }

    private String getReadParcelableString(String str) {
        return str.equalsIgnoreCase("null") ? null : str;
    }

    private Date getReadParcelableDate(long lng) {
        return lng==0 ? null : new Date(lng);
    }

    /**
     * Create account wrapper with cursor data.
     *
     * @param c cursor on the database
     */
    private final void createFromDb(Cursor c) {
        this.createFromCursor(c);
    }

    private final void createFromCursor(Cursor c){
        int colCount = c.getColumnCount();
        for(int i=0; i<colCount; i++){
            final String colname = c.getColumnName(i);
            if (FIELD_ID.equals(colname)){
                this.id = c.getInt(i);
            } else if (FIELD_TIME.equals(colname)){
                this.time = c.getLong(i);
            } else if (FIELD_FROM.equals(colname)){
                this.from = c.getString(i);
            } else if (FIELD_TO.equals(colname)){
                this.to = c.getString(i);
            } else if (FIELD_IS_OUTGOING.equals(colname)){
                this.isOutgoing = c.getInt(i) != 0;
            }  else if (FIELD_IS_OFFLINE.equals(colname)){
                this.isOffline = c.getInt(i) != 0;
            } else if (FIELD_IS_PROCESSED.equals(colname)){
                this.isProcessed = c.getInt(i) != 0;
            } else if (FIELD_SEND_COUNTER.equals(colname)){
                this.sendCounter = c.getInt(i);
            } else if (FIELD_SEND_ATTEMPT_COUNTER.equals(colname)){
                this.sendAttemptCounter = c.getInt(i);
            } else if (FIELD_LAST_SEND_CALL.equals(colname)){
                this.lastSendCall = c.getLong(i);
            } else if (FIELD_TRANSPORT_PROTOCOL_TYPE.equals(colname)){
                this.transportProtocolType = c.getInt(i);
            } else if (FIELD_TRANSPORT_PROTOCOL_VERSION.equals(colname)){
                this.transportProtocolVersion = c.getInt(i);
            } else if (FIELD_MESSAGE_PROTOCOL_TYPE.equals(colname)){
                this.messageProtocolType = c.getInt(i);
            } else if (FIELD_MESSAGE_PROTOCOL_SUB_TYPE.equals(colname)){
                this.messageProtocolSubType = c.getInt(i);
            } else if (FIELD_MESSAGE_PROTOCOL_VERSION.equals(colname)){
                this.messageProtocolVersion = c.getInt(i);
            } else if (FIELD_MIME_TYPE.equals(colname)){
                this.mimeType = c.getString(i);
            } else if (FIELD_TRANSPORT_PAYLOAD.equals(colname)){
                this.transportPayload = c.getBlob(i);
            } else if (FIELD_ENVELOPE_PAYLOAD.equals(colname)){
                this.envelopePayload = c.getBlob(i);
            } else if (FIELD_FINAL_MESSAGE.equals(colname)){
                this.finalMessage = c.getString(i);
            } else if (FIELD_FINAL_MESSAGE_HASH.equals(colname)){
                this.finalMessageHash = c.getString(i);
            } else if (FIELD_REFERENCED_ID.equals(colname)){
                this.referencedId = c.getInt(i);
            } else if (FIELD_RESEND_TIME.equals(colname)){
                this.resendTime = c.getLong(i);
            } else {
                Log.w(THIS_FILE, "Unknown column name: " + colname);
            }
        }
    }

    public ContentValues getDbContentValues() {
        ContentValues args = new ContentValues();
        if (id!=null && id!=INVALID_ID) {
            args.put(FIELD_ID, id);
        }
        if (this.time != null)
            args.put(FIELD_TIME, this.time);
        if (this.from != null)
            args.put(FIELD_FROM, this.from);
        if (this.to != null)
            args.put(FIELD_TO, this.to);
        args.put(FIELD_IS_OUTGOING, this.isOutgoing);
        args.put(FIELD_IS_OFFLINE, this.isOffline);
        args.put(FIELD_IS_PROCESSED, this.isProcessed);
        if (this.sendCounter != null)
            args.put(FIELD_SEND_COUNTER, this.sendCounter);
        if (this.sendAttemptCounter != null)
            args.put(FIELD_SEND_ATTEMPT_COUNTER, this.sendAttemptCounter);
        if (this.lastSendCall != null)
            args.put(FIELD_LAST_SEND_CALL, this.lastSendCall);
        if (this.transportProtocolType != null)
            args.put(FIELD_TRANSPORT_PROTOCOL_TYPE, this.transportProtocolType);
        if (this.transportProtocolVersion != null)
            args.put(FIELD_TRANSPORT_PROTOCOL_VERSION, this.transportProtocolVersion);
        if (this.messageProtocolType != null)
            args.put(FIELD_MESSAGE_PROTOCOL_TYPE, this.messageProtocolType);
        if (this.messageProtocolSubType != null)
            args.put(FIELD_MESSAGE_PROTOCOL_SUB_TYPE, this.messageProtocolSubType);
        if (this.messageProtocolVersion != null)
            args.put(FIELD_MESSAGE_PROTOCOL_VERSION, this.messageProtocolVersion);
        if (this.mimeType != null)
            args.put(FIELD_MIME_TYPE, this.mimeType);
        if (this.transportPayload != null)
            args.put(FIELD_TRANSPORT_PAYLOAD, this.transportPayload);
        if (this.envelopePayload != null)
            args.put(FIELD_ENVELOPE_PAYLOAD, this.envelopePayload);
        if (this.finalMessage != null)
            args.put(FIELD_FINAL_MESSAGE, this.finalMessage);
        if (this.finalMessageHash != null)
            args.put(FIELD_FINAL_MESSAGE_HASH, this.finalMessageHash);
        if (this.referencedId != null)
            args.put(FIELD_REFERENCED_ID, this.referencedId);
        if (this.resendTime != null)
            args.put(FIELD_RESEND_TIME, this.resendTime);
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
     * Gets the value of the time property.
     *
     * @return
     *     possible object is
     *     {@link Long }
     *
     */
    public Long getTime() {
        return time;
    }

    /**
     * Sets the value of the time property.
     *
     * @param value
     *     allowed object is
     *     {@link Long }
     *
     */
    public void setTime(Long value) {
        this.time = value;
    }
    /**
     * Gets the value of the from property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getFrom() {
        return from;
    }

    /**
     * Sets the value of the from property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setFrom(String value) {
        this.from = value;
    }
    /**
     * Gets the value of the to property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getTo() {
        return to;
    }

    /**
     * Sets the value of the to property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setTo(String value) {
        this.to = value;
    }
    /**
     * Gets the value of the isOutgoing property.
     *
     * @return
     *     possible object is
     *     {@link boolean }
     *
     */
    public boolean getIsOutgoing() {
        return isOutgoing;
    }

    /**
     * Sets the value of the isOutgoing property.
     *
     * @param value
     *     allowed object is
     *     {@link boolean }
     *
     */
    public void setIsOutgoing(boolean value) {
        this.isOutgoing = value;
    }
    /**
     * Gets the value of the isProcessed property.
     *
     * @return
     *     possible object is
     *     {@link boolean }
     *
     */
    public boolean getIsProcessed() {
        return isProcessed;
    }

    /**
     * Sets the value of the isProcessed property.
     *
     * @param value
     *     allowed object is
     *     {@link boolean }
     *
     */
    public void setIsProcessed(boolean value) {
        this.isProcessed = value;
    }
    /**
     * Gets the value of the sendCounter property.
     *
     * @return
     *     possible object is
     *     {@link Integer }
     *
     */
    public Integer getSendCounter() {
        return sendCounter;
    }

    /**
     * Sets the value of the sendCounter property.
     *
     * @param value
     *     allowed object is
     *     {@link Integer }
     *
     */
    public void setSendCounter(Integer value) {
        this.sendCounter = value;
    }
    /**
     * Gets the value of the transportProtocolType property.
     *
     * @return
     *     possible object is
     *     {@link Integer }
     *
     */
    public Integer getTransportProtocolType() {
        return transportProtocolType;
    }

    /**
     * Sets the value of the transportProtocolType property.
     *
     * @param value
     *     allowed object is
     *     {@link Integer }
     *
     */
    public void setTransportProtocolType(Integer value) {
        this.transportProtocolType = value;
    }
    /**
     * Gets the value of the transportProtocolVersion property.
     *
     * @return
     *     possible object is
     *     {@link Integer }
     *
     */
    public Integer getTransportProtocolVersion() {
        return transportProtocolVersion;
    }

    /**
     * Sets the value of the transportProtocolVersion property.
     *
     * @param value
     *     allowed object is
     *     {@link Integer }
     *
     */
    public void setTransportProtocolVersion(Integer value) {
        this.transportProtocolVersion = value;
    }
    /**
     * Gets the value of the messageProtocolType property.
     *
     * @return
     *     possible object is
     *     {@link Integer }
     *
     */
    public Integer getMessageProtocolType() {
        return messageProtocolType;
    }

    /**
     * Sets the value of the messageProtocolType property.
     *
     * @param value
     *     allowed object is
     *     {@link Integer }
     *
     */
    public void setMessageProtocolType(Integer value) {
        this.messageProtocolType = value;
    }
    /**
     * Gets the value of the messageProtocolVersion property.
     *
     * @return
     *     possible object is
     *     {@link Integer }
     *
     */
    public Integer getMessageProtocolVersion() {
        return messageProtocolVersion;
    }

    /**
     * Sets the value of the messageProtocolVersion property.
     *
     * @param value
     *     allowed object is
     *     {@link Integer }
     *
     */
    public void setMessageProtocolVersion(Integer value) {
        this.messageProtocolVersion = value;
    }
    /**
     * Gets the value of the mimeType property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * Sets the value of the mimeType property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setMimeType(String value) {
        this.mimeType = value;
    }
    /**
     * Gets the value of the transportPayload property.
     *
     * @return
     *     possible object is
     *     {@link byte[] }
     *
     */
    public byte[] getTransportPayload() {
        return transportPayload;
    }

    /**
     * Sets the value of the transportPayload property.
     *
     * @param value
     *     allowed object is
     *     {@link byte[] }
     *
     */
    public void setTransportPayload(byte[] value) {
        this.transportPayload = value;
    }
    /**
     * Gets the value of the envelopePayload property.
     *
     * @return
     *     possible object is
     *     {@link byte[] }
     *
     */
    public byte[] getEnvelopePayload() {
        return envelopePayload;
    }

    /**
     * Sets the value of the envelopePayload property.
     *
     * @param value
     *     allowed object is
     *     {@link byte[] }
     *
     */
    public void setEnvelopePayload(byte[] value) {
        this.envelopePayload = value;
    }
    /**
     * Gets the value of the finalMessage property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getFinalMessage() {
        return finalMessage;
    }

    /**
     * Sets the value of the finalMessage property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setFinalMessage(String value) {
        this.finalMessage = value;
    }
    /**
     * Gets the value of the finalMessageHash property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getFinalMessageHash() {
        return finalMessageHash;
    }

    /**
     * Sets the value of the finalMessageHash property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setFinalMessageHash(String value) {
        this.finalMessageHash = value;
    }
    /**
     * Gets the value of the referencedId property.
     *
     * @return
     *     possible object is
     *     {@link Integer }
     *
     */
    public Integer getReferencedId() {
        return referencedId;
    }

    /**
     * Sets the value of the referencedId property.
     *
     * @param value
     *     allowed object is
     *     {@link Integer }
     *
     */
    public void setReferencedId(Integer value) {
        this.referencedId = value;
    }
    /**
     * Gets the value of the resendTime property.
     *
     * @return
     *     possible object is
     *     {@link Long }
     *
     */
    public Long getResendTime() {
        return resendTime;
    }

    /**
     * Sets the value of the resendTime property.
     *
     * @param value
     *     allowed object is
     *     {@link Long }
     *
     */
    public void setResendTime(Long value) {
        this.resendTime = value;
    }

    public Integer getMessageProtocolSubType() {
        return messageProtocolSubType;
    }

    public void setMessageProtocolSubType(Integer messageProtocolSubType) {
        this.messageProtocolSubType = messageProtocolSubType;
    }

    public boolean isOffline() {
        return isOffline;
    }

    public void setIsOffline(boolean isOffline) {
        this.isOffline = isOffline;
    }

    public boolean isProcessed() {
        return isProcessed;
    }

    public Integer getSendAttemptCounter() {
        return sendAttemptCounter;
    }

    public void setSendAttemptCounter(Integer sendAttemptCounter) {
        this.sendAttemptCounter = sendAttemptCounter;
    }

    public Long getLastSendCall() {
        return lastSendCall;
    }

    public void setLastSendCall(Long lastSendCall) {
        this.lastSendCall = lastSendCall;
    }

    //
    // Non-generated code below
    //



    private static final String TAG = "QueuedMessage";
    public QueuedMessage(String from, String to, boolean isOutgoing) {
        setSendCounter(0);
        setFrom(from);
        setTo(to);
        setIsOutgoing(isOutgoing);
        setTime(System.currentTimeMillis());
        setIsOffline(false);
    }

    public static final String[] SENDING_ACK_PROJECTION = new String[] {
            FIELD_ID, FIELD_TIME,
            FIELD_FROM, FIELD_TO,
            FIELD_IS_OUTGOING, FIELD_IS_PROCESSED,
            FIELD_SEND_COUNTER, FIELD_TRANSPORT_PROTOCOL_TYPE,
            FIELD_TRANSPORT_PROTOCOL_VERSION, FIELD_MESSAGE_PROTOCOL_TYPE,FIELD_MESSAGE_PROTOCOL_SUB_TYPE,
            FIELD_MESSAGE_PROTOCOL_VERSION, FIELD_MIME_TYPE,
            FIELD_TRANSPORT_PAYLOAD,
            FIELD_REFERENCED_ID,
    };

    public String getRemoteContact() {
        if (isOutgoing) {
            return to;
        }else {
            return from;
        }
    }

    // WATCH OUT, this is also changed!
//    public static final String FIELD_FROM = "sender";
//    public static final String FIELD_TO = "receiver";


    public static int updateMessage(ContentResolver cr, long messageId, ContentValues cv){
        return cr.update(URI, cv,
                FIELD_ID + "=?",
                new String[] { String.valueOf(messageId) });
    }

    public static int loadSendCounter(ContentResolver cr, long messageId){
        // load ID here
        Cursor c = cr.query(
                URI,
                new String[] {FIELD_ID, FIELD_SEND_COUNTER},
                FIELD_ID + "=?",
                new String[] { String.valueOf(messageId)},
                null);

        if (c != null) {
            try {
                if (c.moveToFirst()){
                    int counter = c.getInt(c.getColumnIndex(FIELD_SEND_COUNTER));
                    return counter;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error while getting message ID", e);
            } finally {
                c.close();
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return "QueuedMessage{" +
                "id=" + id +
                ", time=" + time +
                ", from='" + from + '\'' +
                ", to='" + to + '\'' +
                ", isOutgoing=" + isOutgoing +
                ", isOffline=" + isOffline +
                ", isProcessed=" + isProcessed +
                ", sendCounter=" + sendCounter +
                ", sendAttemptCounter=" + sendAttemptCounter +
                ", lastSendCall=" + lastSendCall +
                ", transportProtocolType=" + transportProtocolType +
                ", transportProtocolVersion=" + transportProtocolVersion +
                ", messageProtocolType=" + messageProtocolType +
                ", messageProtocolSubType=" + messageProtocolSubType +
                ", messageProtocolVersion=" + messageProtocolVersion +
                ", mimeType='" + mimeType + '\'' +
                ", transportPayload=" + Arrays.toString(transportPayload) +
                ", envelopePayload=" + Arrays.toString(envelopePayload) +
                ", finalMessage='" + finalMessage + '\'' +
                ", finalMessageHash='" + finalMessageHash + '\'' +
                ", referencedId=" + referencedId +
                ", resendTime=" + resendTime +
                '}';
    }

    /**
     * Loads message by ID.
     * Uses file related projection.
     *
     * @param cr
     * @param messageId
     * @return
     */
    public static QueuedMessage getById(ContentResolver cr, long messageId){
        return getById(cr, messageId, QueuedMessage.FULL_PROJECTION);
    }

    /**
     * Loads message by ID.
     * @param cr
     * @param messageId
     * @return
     */
    public static QueuedMessage getById(ContentResolver cr, long messageId, String[] projection){
        Cursor c = cr.query(
                QueuedMessage.URI,
                projection,
                QueuedMessage.FIELD_ID + "=?",
                new String[] { String.valueOf(messageId)},
                null);

        if (c == null){
            return null;
        }

        try {
            if (c.moveToFirst()){
                QueuedMessage msg = new QueuedMessage(c);
                return msg;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error while getting message ID", e);
            return null;

        } finally {
            c.close();
        }

        return null;
    }

    /**
     * When an image of SipMessage is placed in the queue for sending, this methods deletes it (its referenceId must be SipMessage primary key)
     */
    public static int deleteOutgoingSipMessage(ContentResolver cr, String toSip, long sipMessageId){
        try {

            StringBuilder sb = new StringBuilder();
            sb.append(QueuedMessage.FIELD_IS_OUTGOING + "=1 AND ");
            sb.append(QueuedMessage.FIELD_TO + "=? AND ");
            sb.append(QueuedMessage.FIELD_REFERENCED_ID + "=? ");

            String where = sb.toString();
            String[] whereArgs = new String[]{toSip, String.valueOf(sipMessageId)};

            return cr.delete(QueuedMessage.URI, where, whereArgs);
        } catch (Exception ex){
            Log.ef(TAG, ex, "Error deleting SipMessage image from message queue, SipMsg id  [%d]", sipMessageId);
            return 0;
        }
    }


    /**
     * Duplicates are identified by protocol types, from,to, and reference id
     * @param cr
     * @param msg
     * @return
     */
    public static int deleteOutgoingDuplicates(ContentResolver cr, QueuedMessage msg){
        try {
            // If reference id is not set (such as for ACKs), do not bother (receiving multiple ACKs is not `1a problem)
            if (msg.getReferencedId() == null){
                return 0;
            }

            StringBuilder sb = new StringBuilder();
            sb.append(QueuedMessage.FIELD_IS_OUTGOING + "=1 AND ");
            sb.append(QueuedMessage.FIELD_TO + "=? AND ");
            sb.append(QueuedMessage.FIELD_FROM + "=? AND ");
            sb.append(QueuedMessage.FIELD_REFERENCED_ID + "=? AND ");
            sb.append(QueuedMessage.FIELD_MESSAGE_PROTOCOL_TYPE + "=? AND ");
            sb.append(QueuedMessage.FIELD_MESSAGE_PROTOCOL_VERSION + "=?");

            String where = sb.toString();
            String[] whereArgs = new String[]{msg.getTo(),
                    msg.getFrom(),
                    msg.getReferencedId().toString(),
                    msg.getMessageProtocolType().toString(),
                    msg.getMessageProtocolVersion().toString()};

            return cr.delete(QueuedMessage.URI, where, whereArgs);
        } catch (Exception ex){
            Log.ef(TAG, ex, "Error deleting duplicates for message [%s]", msg);
            return 0;
        }
    }


    public static final String NEWEST_MSG_PER_RECIPIENT_NAME = "NEWEST_MSG_PER_RECIPIENT";
    /**
     * Uri for retrieving newest messages grouped by destination sip.
     */
    public static final Uri NEWEST_MSG_PER_RECIPIENT_URI = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/" + NEWEST_MSG_PER_RECIPIENT_NAME);
}

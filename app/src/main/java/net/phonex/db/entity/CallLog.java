package net.phonex.db.entity;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.CallLog.Calls;
import android.text.TextUtils;

import net.phonex.core.SipUri;
import net.phonex.db.scheme.CallLogScheme;
import net.phonex.sip.SipStatusCode;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.sqlcipher.database.SQLiteDatabase;

import java.util.ArrayList;

/**
 * Representation of a call after it is finished.
 *
 * Created by ph4r05 on 8/22/14.
 */
public class CallLog implements Parcelable {
    private static final String TAG = "CallLog";

    /**
     * Unique ID.
     */
    private int id=-1;

    /**
     * The type of the call (incoming, outgoing or missed).
     */
    private int type;

    /**
     * Whether or not the call has been acknowledged
     */
    private boolean isNew;

    /**
     * The duration of the call in seconds
     */
    private long duration;

    /**
     * UTC timestamp of the call start.
     */
    private long callStart;

    /**
     * Account ID (in contact list) of a remote contact.
     */
    private Integer remoteAccountId;

    /**
     * Remote contact as user entered it.
     */
    private String remoteUserEnteredNumber;

    /**
     * Remote contact SIP address.
     */
    private String remoteContactSip;

    /**
     * Remote contact address.
     */
    private String remoteContact;

    /**
     * Remote contact name.
     */
    private String remoteContactName;

    /**
     * Remote number type.
     */
    private Integer numberType;

    /**
     * Remote number label.
     */
    private String numberLabel;

    /**
     * Account ID of a local account that answered/dialed this call.
     */
    private long accountId;

    /**
     * Finihsing status code of the SIP call.
     */
    private int statusCode;

    /**
     * Status text representation of the SIP call.
     */
    private String statusText;

    /**
     * By default user has to see the notification
     */
    private boolean seenByUser = false;

    /**
     * Missed call notification timestamp.
     */
    private Long eventTimestamp;

    /**
     * Missed call notification nonce.
     */
    private Long eventNonce;

    /**
     * SIP Call Id, unique code.
     */
    private String sipCallId;

    public CallLog() {

    }

    /**
     * Construct a ReceivedFile from a cursor retrieved with a
     * {@link android.content.ContentProvider} query on table.
     *
     * @param c the cursor to unpack
     */
    public CallLog(Cursor c) {
        super();
        createFromDb(c);
    }

    /**
     * Create account wrapper with cursor datas.
     *
     * @param c cursor on the database
     */
    private final void createFromDb(Cursor c) {
        this.createFromCursor(c);
    }

    /**
     * OldSchool method of initialization from cursor
     * @param c
     */
    private final void createFromCursor(Cursor c){
        final int colCount = c.getColumnCount();
        for(int i=0; i<colCount; i++){
            final String colname = c.getColumnName(i);
            if (Calls._ID.equals(colname)){
                this.id = c.getInt(i);
            } else if (Calls.DATE.equals(colname)){
                this.callStart = c.getLong(i);
            } else if (Calls.DURATION.equals(colname)){
                this.duration = c.getLong(i);
            } else if (Calls.NEW.equals(colname)){
                this.isNew = c.getInt(i)==1;
            } else if (Calls.NUMBER.equals(colname)){
                this.remoteContact = c.getString(i);
            } else if (Calls.TYPE.equals(colname)){
                this.type = c.getInt(i);
            } else if (Calls.CACHED_NAME.equals(colname)){
                this.remoteUserEnteredNumber = c.getString(i);
            } else if (Calls.CACHED_NUMBER_LABEL.equals(colname)){
                this.numberLabel = c.getString(i);
            } else if (Calls.CACHED_NUMBER_TYPE.equals(colname)){
                this.numberType = c.getInt(i);
            } else if (CallLogScheme.FIELD_REMOTE_ACCOUNT_ID.equals(colname)){
                this.remoteAccountId = c.getInt(i);
            } else if (CallLogScheme.FIELD_REMOTE_ACCOUNT_NAME.equals(colname)){
                this.remoteContactName = c.getString(i);
            } else if (CallLogScheme.FIELD_REMOTE_ACCOUNT_ADDRESS.equals(colname)){
                this.remoteContactSip = c.getString(i);
            } else if (CallLogScheme.FIELD_ACCOUNT_ID.equals(colname)){
                this.accountId = c.getInt(i);
            } else if (CallLogScheme.FIELD_STATUS_CODE.equals(colname)){
                this.statusCode = c.getInt(i);
            } else if (CallLogScheme.FIELD_STATUS_TEXT.equals(colname)){
                this.statusText = c.getString(i);
            } else if (CallLogScheme.FIELD_SEEN_BY_USER.equals(colname)){
                this.seenByUser = c.getInt(i)==1;
            } else if (CallLogScheme.FIELD_EVENT_TIMESTAMP.equals(colname)){
                this.eventTimestamp = c.getLong(i);
            } else if (CallLogScheme.FIELD_EVENT_NONCE.equals(colname)){
                this.eventNonce = c.getLong(i);
            } else if (CallLogScheme.FIELD_SIP_CALL_ID.equals(colname)){
                this.sipCallId = c.getString(i);
            }
        }
    }

    /**
     * Create account wrapper with content values pairs.
     *
     * @param args the content value to unpack.
     */
    public final void createFromContentValue(ContentValues args) {
        Integer tmp_i;
        Long tmp_l;
        String tmp_s;

        // Application specific settings
        tmp_i = args.getAsInteger(Calls._ID);
        if (tmp_i != null) {
            id = tmp_i;
        }
        tmp_l = args.getAsLong(Calls.DATE);
        if (tmp_l != null) {
            callStart = tmp_l;
        }
        tmp_l = args.getAsLong(Calls.DURATION);
        if (tmp_l != null) {
            duration = tmp_l;
        }
        tmp_i = args.getAsInteger(Calls.NEW);
        if (tmp_i != null) {
            isNew = tmp_i==1;
        }
        tmp_s = args.getAsString(Calls.NUMBER);
        if (tmp_s != null) {
            remoteContact = tmp_s;
        }
        tmp_i = args.getAsInteger(Calls.TYPE);
        if(tmp_i != null){
            type = tmp_i;
        }
        tmp_s = args.getAsString(Calls.CACHED_NAME);
        if(tmp_s != null){
            remoteUserEnteredNumber = tmp_s;
        }
        tmp_s = args.getAsString(Calls.CACHED_NUMBER_LABEL);
        if (tmp_s != null) {
            numberLabel = tmp_s;
        }
        tmp_i = args.getAsInteger(Calls.CACHED_NUMBER_TYPE);
        if (tmp_i != null) {
            numberType = tmp_i;
        }
        tmp_i = args.getAsInteger(CallLogScheme.FIELD_REMOTE_ACCOUNT_ID);
        if (tmp_i != null) {
            remoteAccountId = tmp_i;
        }
        tmp_s = args.getAsString(CallLogScheme.FIELD_REMOTE_ACCOUNT_NAME);
        if (tmp_s != null) {
            remoteContactName = tmp_s;
        }
        tmp_s = args.getAsString(CallLogScheme.FIELD_REMOTE_ACCOUNT_ADDRESS);
        if (tmp_s != null) {
            remoteContactSip = tmp_s;
        }
        tmp_i = args.getAsInteger(CallLogScheme.FIELD_ACCOUNT_ID);
        if (tmp_i != null) {
            accountId = tmp_i;
        }
        tmp_i = args.getAsInteger(CallLogScheme.FIELD_STATUS_CODE);
        if (tmp_i != null) {
            statusCode = tmp_i;
        }
        tmp_s = args.getAsString(CallLogScheme.FIELD_STATUS_TEXT);
        if (tmp_s != null) {
            statusText = tmp_s;
        }
        tmp_i = args.getAsInteger(CallLogScheme.FIELD_SEEN_BY_USER);
        if (tmp_i != null) {
            seenByUser = tmp_i==1;
        }
        tmp_l = args.getAsLong(CallLogScheme.FIELD_EVENT_TIMESTAMP);
        if (tmp_l != null) {
            eventTimestamp = tmp_l;
        }
        tmp_l = args.getAsLong(CallLogScheme.FIELD_EVENT_NONCE);
        if (tmp_l != null) {
            eventNonce = tmp_l;
        }
        tmp_s = args.getAsString(CallLogScheme.FIELD_SIP_CALL_ID);
        if (tmp_s != null) {
            sipCallId = tmp_s;
        }
    }

    /**
     * Factory method from defining session.
     * @param context
     * @param call
     * @return
     */
    public static CallLog fromSession(Context context, SipCallSession call) {
        CallLog cli = new CallLog();

        String remoteContact = call.getRemoteContact();
        cli.remoteContact = remoteContact;

        // Extract SIP address from the call.
        String number = SipUri.getSipFromContact(remoteContact);
        cli.remoteContactSip = number;

        // Try to load contactlist details.
        if (!TextUtils.isEmpty(number)) {
            SipClist clist = SipClist.getProfileFromDbSip(context, number);
            String displayName = clist.getDisplayName();
            if (TextUtils.isEmpty(displayName)) {
                displayName = number;
            }

            cli.remoteContactName = displayName;
            cli.remoteAccountId = clist.getId();
        } else {
            cli.remoteContactName = number;
        }

        // Date extract.
        final long callStart = call.getCallStart();
        cli.callStart = (callStart > 0) ? callStart : System.currentTimeMillis();

        int type = android.provider.CallLog.Calls.OUTGOING_TYPE;
        boolean isNew = false;
        if (call.isIncoming()) {
            type = android.provider.CallLog.Calls.MISSED_TYPE;
            isNew = true;

            if (callStart > 0) {
                // Has started on the remote side, so not missed call
                type = android.provider.CallLog.Calls.INCOMING_TYPE;
                isNew = false;

            } else if (call.getLastStatusCode() == SipStatusCode.DECLINE) {
                // We have intentionally declined this call
                type = android.provider.CallLog.Calls.INCOMING_TYPE;
                isNew = false;
            }
        }

        cli.type = type;
        cli.isNew = isNew;
        cli.duration = (callStart > 0) ? (System.currentTimeMillis() - callStart) / 1000 : 0;
        cli.accountId = call.getAccId();
        cli.statusCode = call.getLastStatusCode();
        cli.statusText = call.getLastStatusComment();
        cli.seenByUser = false;
        cli.sipCallId = call.getSipCallId();
        cli.eventNonce = null;
        cli.eventTimestamp = call.getCallStart();
        if (cli.eventTimestamp <= 100){
            cli.eventTimestamp = null;
        }

        return cli;
    }

    /**
     * Converts Call log info to the content values that can be inserted to the database.
     * Creates content values for the Android call log.
     *
     * @param cli
     * @return
     */
    public static ContentValues createAndroidContentValues(CallLog cli) {
        ContentValues cv = new ContentValues();
        cv.put(android.provider.CallLog.Calls.NUMBER, cli.getRemoteContact());
        cv.put(android.provider.CallLog.Calls.DATE, cli.getCallStart());
        cv.put(android.provider.CallLog.Calls.TYPE, cli.getType());
        cv.put(android.provider.CallLog.Calls.NEW, cli.isNew() ? 1 : 0);
        cv.put(android.provider.CallLog.Calls.DURATION, cli.getDuration());
        cv.put(android.provider.CallLog.Calls.CACHED_NAME, cli.getRemoteUserEnteredNumber());
        cv.put(android.provider.CallLog.Calls.CACHED_NUMBER_LABEL, cli.getNumberLabel());
        cv.put(android.provider.CallLog.Calls.CACHED_NUMBER_TYPE, cli.getNumberType());
        return cv;
    }

    /**
     * Converts Call log info to the content values that can be inserted to the database.
     * Extends android call log content values by our extra fields.
     *
     * @param cli
     * @return
     */
    public static ContentValues createContentValues(CallLog cli) {
        ContentValues cv = createAndroidContentValues(cli);
        cv.put(CallLogScheme.FIELD_ACCOUNT_ID, cli.getAccountId());
        cv.put(CallLogScheme.FIELD_STATUS_CODE, cli.getStatusCode());
        cv.put(CallLogScheme.FIELD_STATUS_TEXT, cli.getStatusText());
        cv.put(CallLogScheme.FIELD_REMOTE_ACCOUNT_ID, cli.getRemoteAccountId());
        cv.put(CallLogScheme.FIELD_REMOTE_ACCOUNT_ADDRESS, cli.getRemoteContactSip());
        cv.put(CallLogScheme.FIELD_REMOTE_ACCOUNT_NAME, cli.getRemoteContactName());
        cv.put(CallLogScheme.FIELD_SEEN_BY_USER, cli.isSeenByUser() ? 1 : 0);
        if (cli.getEventTimestamp() != null){
            cv.put(CallLogScheme.FIELD_EVENT_TIMESTAMP, cli.getEventTimestamp());
        }
        if (cli.getEventNonce() != null){
            cv.put(CallLogScheme.FIELD_EVENT_NONCE, cli.getEventNonce());
        }
        if (cli.getSipCallId() != null){
            cv.put(CallLogScheme.FIELD_SIP_CALL_ID, cli.getSipCallId());
        }

        return cv;
    }

    /**
     * Insets current call log information to the database.
     * @param ctxt
     */
    public Uri addToDatabase(Context ctxt){
        final ContentResolver cr = ctxt.getContentResolver();
        try {
            return cr.insert(CallLogScheme.URI, createContentValues(this));
        } catch(Exception ex){
            Log.ef(TAG, ex, "Cannot insert callog info to DB, %s", this);
        }

        return null;
    }

    /**
     * Keeps last 500 records in the call log database.
     * Requires direct access to the database, used in helper.
     * @param db
     */
    public static void pruneRecords(SQLiteDatabase db) {
        db.delete(CallLogScheme.TABLE,
                String.format("%s IN (SELECT %s FROM %s ORDER BY %s LIMIT -1 OFFSET 500)",
                        CallLogScheme.FIELD_ID,
                        CallLogScheme.FIELD_ID,
                        CallLogScheme.TABLE,
                        Calls.DEFAULT_SORT_ORDER),
                null);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public long getCallStart() {
        return callStart;
    }

    public void setCallStart(long callStart) {
        this.callStart = callStart;
    }

    public Integer getRemoteAccountId() {
        return remoteAccountId;
    }

    public void setRemoteAccountId(Integer remoteAccountId) {
        this.remoteAccountId = remoteAccountId;
    }

    public String getRemoteUserEnteredNumber() {
        return remoteUserEnteredNumber;
    }

    public void setRemoteUserEnteredNumber(String remoteUserEnteredNumber) {
        this.remoteUserEnteredNumber = remoteUserEnteredNumber;
    }

    public String getRemoteContactSip() {
        return remoteContactSip;
    }

    public void setRemoteContactSip(String remoteContactSip) {
        this.remoteContactSip = remoteContactSip;
    }

    public String getRemoteContactName() {
        return remoteContactName;
    }

    public void setRemoteContactName(String remoteContactName) {
        this.remoteContactName = remoteContactName;
    }

    public Integer getNumberType() {
        return numberType;
    }

    public void setNumberType(Integer numberType) {
        this.numberType = numberType;
    }

    public String getNumberLabel() {
        return numberLabel;
    }

    public void setNumberLabel(String numberLabel) {
        this.numberLabel = numberLabel;
    }

    public long getAccountId() {
        return accountId;
    }

    public void setAccountId(long accountId) {
        this.accountId = accountId;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getStatusText() {
        return statusText;
    }

    public void setStatusText(String statusText) {
        this.statusText = statusText;
    }

    public String getRemoteContact() {
        return remoteContact;
    }

    public void setRemoteContact(String remoteContact) {
        this.remoteContact = remoteContact;
    }

    public Long getEventTimestamp() {
        return eventTimestamp;
    }

    public void setEventTimestamp(Long eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }

    public Long getEventNonce() {
        return eventNonce;
    }

    public void setEventNonce(Long eventNonce) {
        this.eventNonce = eventNonce;
    }

    public String getSipCallId() {
        return sipCallId;
    }

    public void setSipCallId(String sipCallId) {
        this.sipCallId = sipCallId;
    }

    @Override
    public String toString() {
        return "CallLog{" +
                "id=" + id +
                ", type=" + type +
                ", isNew=" + isNew +
                ", duration=" + duration +
                ", callStart=" + callStart +
                ", remoteAccountId=" + remoteAccountId +
                ", remoteUserEnteredNumber='" + remoteUserEnteredNumber + '\'' +
                ", remoteContactSip='" + remoteContactSip + '\'' +
                ", remoteContact='" + remoteContact + '\'' +
                ", remoteContactName='" + remoteContactName + '\'' +
                ", numberType=" + numberType +
                ", numberLabel='" + numberLabel + '\'' +
                ", accountId=" + accountId +
                ", statusCode=" + statusCode +
                ", statusText='" + statusText + '\'' +
                ", seenByUser=" + seenByUser +
                ", eventTimestamp=" + eventTimestamp +
                ", eventNonce=" + eventNonce +
                ", sipCallId='" + sipCallId + '\'' +
                '}';
    }

    public boolean isSeenByUser() {
        return seenByUser;
    }

    public void setSeenByUser(boolean seenByUser) {
        this.seenByUser = seenByUser;
    }

    public static int getNumberOfUnseenLogs(Context ctxt){
        try {
            String selection = CallLogScheme.FIELD_SEEN_BY_USER + "=0 AND " + CallLogScheme.FIELD_NEW + "=1";
            Cursor countCursor = ctxt.getContentResolver().query(CallLogScheme.URI,
                    new String[]{"count (*) as count"},
                    selection, null, null);

            if (countCursor == null){
                Log.wf(TAG, "Cursor is null!");
                return 0;
            }

            int count = 0;
            if (countCursor.moveToFirst()) {
                count = countCursor.getInt(0);
            }

            MiscUtils.closeCursorSilently(countCursor);
            Log.vf(TAG, "getNumberOfUnseenLogs: number of unseen logs is %d", count);
            return count;
        } catch (Exception ex) {
            Log.e(TAG, "unexpected error in getNumberOfUnseenLogs()", ex);
            return 0;
        }
    }

    public static void markLogsAsSeen(ContentResolver cr){
        try {
            String selection = CallLogScheme.FIELD_SEEN_BY_USER + "=0 AND " + CallLogScheme.FIELD_NEW + "=1";
            ContentValues args = new ContentValues();
            args.put(CallLogScheme.FIELD_SEEN_BY_USER, true);

            int updateCount = cr.update(CallLogScheme.URI, args, selection, null);
            Log.df(TAG, "markLogsAsSeen: Number of logs marked as seen (previously unseen) is %d", updateCount);
        } catch (Exception ex) {
            Log.e(TAG, "unexpected error in markLogsAsSeen()", ex);
        }
    }

    public static CallLog getLogByEventDescription(ContentResolver cr, String from, Long to,
                                                   Long eventTime, Long eventNonce, String callId)
    {
        try {
            ArrayList<String> selectionArgsList = new ArrayList<>();
            String selection = CallLogScheme.FIELD_REMOTE_ACCOUNT_ADDRESS + "=? AND "
                    + CallLogScheme.FIELD_ACCOUNT_ID + "=? AND (0 ";

            selectionArgsList.add(from);
            selectionArgsList.add(to.toString());

            if (eventTime != null && eventTime > 100){
                selection += " OR " + CallLogScheme.FIELD_EVENT_TIMESTAMP + "=? ";
                selectionArgsList.add(eventTime.toString());
            }

            if (callId != null && !MiscUtils.isEmpty(callId)){
                selection += " OR " + CallLogScheme.FIELD_SIP_CALL_ID+ "=? ";
                selectionArgsList.add(callId);
            }

            // Nonce only if callId is empty
            if (eventNonce != null && eventNonce != 0 && MiscUtils.isEmpty(callId)){
                selection += " OR " + CallLogScheme.FIELD_EVENT_NONCE + "=? ";
                selectionArgsList.add(eventNonce.toString());
            }

            selection += " ) ";

            Cursor c = null;
            try {
                c = cr.query(CallLogScheme.URI,
                        CallLogScheme.LIGHT_PROJECTION,
                        selection,
                        selectionArgsList.toArray(new String[selectionArgsList.size()]),
                        null);

                if (c == null || !c.moveToFirst()){
                    return null;
                }

                return new CallLog(c);

            } catch(Exception e){
                Log.ef(TAG, "Exception in call log retrieval", e);

            } finally {
                MiscUtils.closeCursorSilently(c);

            }

        } catch (Exception ex) {
            Log.e(TAG, "unexpected error in markLogsAsSeen()", ex);

        }

        return null;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.id);
        dest.writeInt(this.type);
        dest.writeByte(isNew ? (byte) 1 : (byte) 0);
        dest.writeLong(this.duration);
        dest.writeLong(this.callStart);
        dest.writeValue(this.remoteAccountId);
        dest.writeString(this.remoteUserEnteredNumber);
        dest.writeString(this.remoteContactSip);
        dest.writeString(this.remoteContact);
        dest.writeString(this.remoteContactName);
        dest.writeValue(this.numberType);
        dest.writeString(this.numberLabel);
        dest.writeLong(this.accountId);
        dest.writeInt(this.statusCode);
        dest.writeString(this.statusText);
        dest.writeByte(seenByUser ? (byte) 1 : (byte) 0);
        dest.writeValue(this.eventTimestamp);
        dest.writeValue(this.eventNonce);
        dest.writeString(this.sipCallId);
    }

    private CallLog(Parcel in) {
        this.id = in.readInt();
        this.type = in.readInt();
        this.isNew = in.readByte() != 0;
        this.duration = in.readLong();
        this.callStart = in.readLong();
        this.remoteAccountId = (Integer) in.readValue(Integer.class.getClassLoader());
        this.remoteUserEnteredNumber = in.readString();
        this.remoteContactSip = in.readString();
        this.remoteContact = in.readString();
        this.remoteContactName = in.readString();
        this.numberType = (Integer) in.readValue(Integer.class.getClassLoader());
        this.numberLabel = in.readString();
        this.accountId = in.readLong();
        this.statusCode = in.readInt();
        this.statusText = in.readString();
        this.seenByUser = in.readByte() != 0;
        this.eventTimestamp = (Long)in.readValue(Long.class.getClassLoader());
        this.eventNonce = (Long)in.readValue(Long.class.getClassLoader());
        this.sipCallId = in.readString();
    }

    public static final Parcelable.Creator<CallLog> CREATOR = new Parcelable.Creator<CallLog>() {
        public CallLog createFromParcel(Parcel source) {
            return new CallLog(source);
        }

        public CallLog[] newArray(int size) {
            return new CallLog[size];
        }
    };
}

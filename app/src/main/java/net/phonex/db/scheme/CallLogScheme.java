package net.phonex.db.scheme;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.CallLog;

import net.phonex.core.Constants;

/**
 * Scheme for CallLog.
 *
 * Created by ph4r05 on 8/22/14.
 */
public class CallLogScheme {
    public final static String TABLE = "calllog";
    public static final String FIELD_ID = CallLog.Calls._ID;
    public static final String FIELD_DATE = CallLog.Calls.DATE;
    public static final String FIELD_DURATION = CallLog.Calls.DURATION;
    public static final String FIELD_NEW = CallLog.Calls.NEW;
    public static final String FIELD_NUMBER = CallLog.Calls.NUMBER;
    public static final String FIELD_TYPE = CallLog.Calls.TYPE;
    public static final String FIELD_CACHED_NAME = CallLog.Calls.CACHED_NAME;
    public static final String FIELD_CACHED_NUMBER_LABEL = CallLog.Calls.CACHED_NUMBER_LABEL;
    public static final String FIELD_CACHED_NUMBER_TYPE = CallLog.Calls.CACHED_NUMBER_TYPE;

    public static final String FIELD_REMOTE_ACCOUNT_ID = "remoteId";
    public static final String FIELD_REMOTE_ACCOUNT_NAME = "remoteName";
    public static final String FIELD_REMOTE_ACCOUNT_ADDRESS = "remoteAddress";
    public static final String FIELD_ACCOUNT_ID = "accountId";
    public static final String FIELD_STATUS_CODE = "statusCode";
    public static final String FIELD_STATUS_TEXT = "statusText";
    public static final String FIELD_SEEN_BY_USER = "seenByUser";
    public static final String FIELD_EVENT_TIMESTAMP = "evtTimestamp";
    public static final String FIELD_EVENT_NONCE = "evtNonce";
    public static final String FIELD_SIP_CALL_ID = "sipCallId";

    // SQL Create command for call log table.
    public final static String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS "
            + TABLE
            + " ("
            + FIELD_ID                      + " INTEGER PRIMARY KEY AUTOINCREMENT,"
            + FIELD_DATE                    + " INTEGER,"
            + FIELD_DURATION                + " INTEGER,"
            + FIELD_NEW                     + " INTEGER,"
            + FIELD_NUMBER                  + " TEXT,"
            + FIELD_TYPE                    + " INTEGER,"
            + FIELD_CACHED_NAME             + " TEXT,"
            + FIELD_CACHED_NUMBER_LABEL     + " TEXT,"
            + FIELD_CACHED_NUMBER_TYPE      + " INTEGER,"
            + FIELD_REMOTE_ACCOUNT_ID       + " INTEGER,"
            + FIELD_REMOTE_ACCOUNT_NAME     + " TEXT,"
            + FIELD_REMOTE_ACCOUNT_ADDRESS  + " TEXT,"
            + FIELD_ACCOUNT_ID              + " INTEGER,"
            + FIELD_STATUS_CODE             + " INTEGER,"
            + FIELD_SEEN_BY_USER            + " INTEGER,"
            + FIELD_STATUS_TEXT             + " TEXT,"
            + FIELD_EVENT_TIMESTAMP         + " NUMERIC DEFAULT 0,"
            + FIELD_EVENT_NONCE             + " INTEGER DEFAULT 0,"
            + FIELD_SIP_CALL_ID             + " TEXT"
            + ");";



    public static final String[] FULL_PROJECTION = new String[] {
            FIELD_ID, FIELD_DATE, FIELD_DURATION, FIELD_NEW, FIELD_NUMBER, FIELD_TYPE,
            FIELD_CACHED_NAME, FIELD_CACHED_NUMBER_LABEL, FIELD_CACHED_NUMBER_TYPE, FIELD_REMOTE_ACCOUNT_ID,
            FIELD_REMOTE_ACCOUNT_NAME, FIELD_REMOTE_ACCOUNT_ADDRESS, FIELD_ACCOUNT_ID,
            FIELD_STATUS_CODE, FIELD_STATUS_TEXT, FIELD_SEEN_BY_USER,
            FIELD_EVENT_TIMESTAMP, FIELD_EVENT_NONCE, FIELD_SIP_CALL_ID
    };

    public static final String[] LIGHT_PROJECTION = new String[] {
            FIELD_ID, FIELD_DATE, FIELD_DURATION, FIELD_NEW, FIELD_NUMBER, FIELD_TYPE,
            FIELD_REMOTE_ACCOUNT_ID, FIELD_REMOTE_ACCOUNT_NAME, FIELD_REMOTE_ACCOUNT_ADDRESS,
            FIELD_ACCOUNT_ID, FIELD_SEEN_BY_USER,
            FIELD_EVENT_TIMESTAMP, FIELD_EVENT_NONCE, FIELD_SIP_CALL_ID
    };

    /**
     * Uri for content provider of certificate
     */
    public static final Uri URI = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/" + TABLE);
    /**
     * Base uri for sip message content provider.<br/>
     * To append with {@link #FIELD_ID}
     *
     * @see android.content.ContentUris#appendId(android.net.Uri.Builder, long)
     */
    public static final Uri ID_URI_BASE = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/" + TABLE + "/");

    /**
     * Content type for sip message.
     */
    public static final String CONTENT_TYPE = Constants.BASE_DIR_TYPE + ".calllog";

    /**
     * FileItemInfo type for a sip message.
     */
    public static final String CONTENT_ITEM_TYPE = Constants.BASE_ITEM_TYPE + ".calllog";
}

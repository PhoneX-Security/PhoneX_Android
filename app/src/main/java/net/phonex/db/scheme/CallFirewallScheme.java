package net.phonex.db.scheme;

import android.content.ContentResolver;
import android.net.Uri;

import net.phonex.core.Constants;

/**
 * Created by ph4r05 on 8/23/14.
 */
public class CallFirewallScheme {
    public static final String TABLE = "filter_rules";

    public static final String FIELD_ID = "_id";
    public static final String FIELD_PRIORITY = "priority";
    public static final String FIELD_ACCOUNT_ID = "accountId";
    public static final String FIELD_CRITERIA = "criteria";
    public static final String FIELD_ACTION = "action";
    public static final String DEFAULT_ORDER = FIELD_PRIORITY + " asc";

    /**
     * SQL Create command for filters table.
     */
    public static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS "
            + TABLE
            + " ("
            + FIELD_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
            + FIELD_PRIORITY + " INTEGER,"
            + FIELD_ACCOUNT_ID + " INTEGER,"
            + FIELD_CRITERIA + " TEXT,"
            + FIELD_ACTION + " INTEGER"
            + ");";

    public static final String[] FULL_PROJECTION = {
            FIELD_ID,
            FIELD_PRIORITY,
            FIELD_CRITERIA,
            FIELD_ACTION
    };

    /**
     * Base uri for a specific filter. Should be appended with filter id.
     */
    public static final Uri ID_URI_BASE = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/"
            + TABLE + "/");
    /**
     * Uri for filters provider.
     */
    public static final Uri URI = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/"
            + TABLE);
}

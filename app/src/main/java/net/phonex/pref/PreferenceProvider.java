package net.phonex.pref;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.text.TextUtils;

import net.phonex.util.Log;

/**
 * ContentProvider providing access to preference in a synchronized way
 * Preferred access is via {@PreferenceConnector}
 */
public class PreferenceProvider extends ContentProvider {
    private static final String TAG = "PreferenceProvider";
    private static final int URI_PREFS = 1;
    private static final int URI_PREF_ID = 2;
    private static final int URI_RESET = 3;

    // special PIN lock features that has to be synchronized between processes
    private static final int URI_PIN_LOCK_IF_NO_RECENT_TICK = 4;


    private final static UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        URI_MATCHER.addURI(PhonexConfig.AUTHORITY, PhonexConfig.PREFS_TABLE_NAME, URI_PREFS);
        URI_MATCHER.addURI(PhonexConfig.AUTHORITY, PhonexConfig.PREFS_TABLE_NAME + "/*", URI_PREF_ID);
        URI_MATCHER.addURI(PhonexConfig.AUTHORITY, PhonexConfig.RESET_TABLE_NAME, URI_RESET);
        URI_MATCHER.addURI(PhonexConfig.AUTHORITY, PhonexConfig.PIN_LOCK_IF_NO_RECENT_TICK_TABLE_NAME, URI_PIN_LOCK_IF_NO_RECENT_TICK);
    }

    private PreferencesManager prefs;

    @Override
    public boolean onCreate() {
        prefs = new PreferencesManager(getContext());
        return true;
    }

    /**
     * Return the MIME type for an known URI in the provider.
     */
    @Override
    public String getType(Uri uri) {
        switch (URI_MATCHER.match(uri)) {
            case URI_PREFS:
            case URI_RESET:
            case URI_PIN_LOCK_IF_NO_RECENT_TICK:
                return PhonexConfig.PREF_CONTENT_TYPE;
            case URI_PREF_ID:
                return PhonexConfig.PREF_CONTENT_ITEM_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String order) {
        MatrixCursor resCursor = new MatrixCursor(new String[]{PhonexConfig.FIELD_NAME, PhonexConfig.FIELD_VALUE});
        if (URI_MATCHER.match(uri) != URI_PREF_ID) {
            return resCursor;
        }

        String name = uri.getLastPathSegment();
        Class<?> aClass = null;
        if (TextUtils.isEmpty(selection)) {
            aClass = PreferencesManager.getPreferenceType(name);
        } else {
            try {
                aClass = Class.forName(selection);
            } catch (ClassNotFoundException e) {
                Log.ef(TAG, "Provided element has an unkown type [%s]", name);
            }
        }

        Object value = null;
        if (aClass == String.class) {
            value = prefs.getString(name);
        } else if (aClass == Integer.class) {
            value = prefs.getInteger(name);
        } else if (aClass == Float.class) {
            value = prefs.getFloat(name);
        } else if (aClass == Long.class) {
            value = prefs.getLong(name);
        } else if (aClass == Boolean.class) {
            Boolean v = prefs.getBoolean(name);
            if (v != null) {
                value = v ? 1 : 0;
            } else {
                value = -1;
            }
        }

        if (value != null) {
            resCursor.addRow(new Object[]{name, value});
        } else {
            resCursor = null;
        }

        return resCursor;
    }

    @Override
    public int update(Uri uri, ContentValues cv, String selection, String[] selectionArgs) {
        //Log.df(TAG, "delete; uri [%s], contentValues [%s] selection [%s]", uri, cv, selection);
        int count = 0;
        switch (URI_MATCHER.match(uri)) {
            case URI_PREFS:
                break;

            case URI_PREF_ID:
                String name = uri.getLastPathSegment();
                Class<?> aClass = getClassFromUriOrSelection(name, selection);
//                Class<?> aClass = null;
//                if (TextUtils.isEmpty(selection)) {
//                    aClass = PreferencesManager.getPreferenceType(name);
//                } else {
//                    try {
//                        aClass = Class.forName(selection);
//                    } catch (ClassNotFoundException e) {
//                        Log.e(TAG, "Impossible to retrieve class from selection");
//                    }
//                }
                if (aClass == String.class) {
                    prefs.setString(name, cv.getAsString(PhonexConfig.FIELD_VALUE));
                } else if (aClass == Integer.class) {
                    prefs.setInteger(name, cv.getAsInteger(PhonexConfig.FIELD_VALUE));
                } else if (aClass == Float.class) {
                    prefs.setFloat(name, cv.getAsFloat(PhonexConfig.FIELD_VALUE));
                } else if (aClass == Long.class) {
                    prefs.setLong(name, cv.getAsLong(PhonexConfig.FIELD_VALUE));
                } else if (aClass == Boolean.class) {
                    prefs.setBoolean(name, cv.getAsBoolean(PhonexConfig.FIELD_VALUE));
                }
                count++;
                break;
            case URI_RESET:
                prefs.resetToDefaults();
                break;
            case URI_PIN_LOCK_IF_NO_RECENT_TICK:
                prefs.pinLockIfNecessary();
                // TODO implement
                break;
        }
        return count;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        Log.df(TAG, "delete; uri [%s], selection [%s]", uri, selection);
        int count = 0;
        switch (URI_MATCHER.match(uri)) {
            case URI_PREF_ID:
                String name = uri.getLastPathSegment();
                Class<?> aClass = getClassFromUriOrSelection(name, selection);
                // Only string pref deletion is supported at the moment
                if (aClass == String.class) {
                    prefs.deleteString(name);
                    count ++;
                }
                break;
            default:
                Log.wf(TAG, "invalid call: delete() with URI %s", uri.toString());
                break;
        }
        return count;
    }

    @Override
    public Uri insert(Uri arg0, ContentValues arg1) {
        Log.w(TAG, "invalid call: insert()");
        return null;
    }

    private Class<?> getClassFromUriOrSelection(String lastPathSegment, String selection){
        Class<?> aClass = null;

        if (TextUtils.isEmpty(selection)) {
            aClass = PreferencesManager.getPreferenceType(lastPathSegment);
        } else {
            try {
                aClass = Class.forName(selection);
            } catch (ClassNotFoundException e) {
                Log.e(TAG, "Impossible to retrieve class from selection");
            }
        }
        return aClass;
    }

}

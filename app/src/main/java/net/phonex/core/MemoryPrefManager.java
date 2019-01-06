package net.phonex.core;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;

import net.phonex.db.DBMemoryPrefsProvider;
import net.phonex.pub.parcels.StoredCredentials;
import net.phonex.util.Log;

/**
 * Defines in-memory shared preferences. Helps to use it via ContentProvider exporting those preferences.
 * @author ph4r05
 */
public class MemoryPrefManager {
    private static final String TAG="MemoryPrefsManager";
    public static final String PREFS_TABLE_NAME = "cryptoprefs";
    public static final String CALLSIM_TABLE_NAME = "callsim";

    /**
     * Credentials information about currently logged-in user. 
     * Server as a safe net in case a main application crashes and re-starts itself.
     */
    public static final String CRED_USR_NAME = "cred_user_name";
    public static final String CRED_USR_PASS = "cred_user_pass";
    public static final String CRED_STORAGE_PASS = "cred_storage_pass";
    public static final String CRED_PEM_PASS = "cred_pem_pass";
    public static final String CRED_SET = "cred_set";

    /**
     * Secure clipboard storage in application.
     */
    public static final String CLIPBOARD_MAIN = "clipboard_main";
    public static final String CLIPBOARD_MAIN_DESC = "clipboard_main_desc";
    
    // For Provider
    /**
     * Authority for preference content provider. <br/>
     * Maybe be changed for forked versions of the app.
     */
    public static final String AUTHORITY = "net.phonex.memory.prefs";
    private static final String BASE_DIR_TYPE = "vnd.android.cursor.dir/vnd.phonex";
    private static final String BASE_ITEM_TYPE = "vnd.android.cursor.item/vnd.phonex";
    
    // Preference
    /**
     * Content type for preference provider.
     */
    public static final String PREF_CONTENT_TYPE = BASE_DIR_TYPE + ".pref";
    /**
     * FileItemInfo type for preference provider.
     */
    public static final String PREF_CONTENT_ITEM_TYPE = BASE_ITEM_TYPE + ".pref";

    public static final String CALLSIM_CONTENT_TYPE = BASE_DIR_TYPE + ".snappref";
    public static final String CALLSIM_CONTENT_ITEM_TYPE = BASE_DIR_TYPE + ".snappref";

    /**
     * XMPP push time request information (to drop duplicates)
     */
    public static final String XMPP_PUSH_LAST_CLIST_FETCH_REQUEST = "xmpp_push_last_clist_fetch_request";
    public static final String XMPP_PUSH_LAST_NEW_CERTIFICATE = "xmpp_push_last_new_certificate";
    public static final String XMPP_PUSH_LAST_DH_USE = "xmpp_push_last_dh_use";
    public static final String XMPP_PUSH_LAST_LICENSE_CHECK = "xmpp_push_last_license_check";
    public static final String XMPP_PUSH_LAST_PAIRING_REQUEST = "xmpp_push_last_pairing_request";

    // Flag that application is closing
    public static final String APP_CLOSING = "app_closing";

    /**
     * Uri for preference content provider.<br/>
     * Deeply advised to not use directly
     * 
     * @see #setPreferenceBooleanValue(Context, String, boolean)
     * @see #setPreferenceFloatValue(Context, String, Float)
     * @see #setPreferenceIntegerValue(Context, String, Integer)
     * @see #setPreferenceStringValue(Context, String, String)
     */
    public static final Uri PREF_URI = Uri.parse(ContentResolver.SCHEME_CONTENT + "://" + AUTHORITY + "/"
            + PREFS_TABLE_NAME);

    /**
     * Base uri for a specific preference in the content provider.<br/>
     * Deeply advised to not use directly
     * 
     * @see #setPreferenceBooleanValue(Context, String, boolean)
     * @see #setPreferenceFloatValue(Context, String, Float)
     * @see #setPreferenceIntegerValue(Context, String, Integer)
     * @see #setPreferenceStringValue(Context, String, String)
     */
    public static final Uri PREF_ID_URI_BASE = Uri.parse(ContentResolver.SCHEME_CONTENT + "://" + AUTHORITY + "/"
            + PREFS_TABLE_NAME + "/");

    public static final Uri CALLSIM_URI = Uri.parse(ContentResolver.SCHEME_CONTENT + "://" + AUTHORITY + "/"
            + CALLSIM_TABLE_NAME);
    public static final Uri CALLSIM_ID_URI_BASE = Uri.parse(ContentResolver.SCHEME_CONTENT + "://" + AUTHORITY + "/"
            + CALLSIM_TABLE_NAME + "/");

    /**
     * Content value key for preference name.<br/>
     * It is strongly advised that you do NOT use this directly.
     * 
     * @see #setPreferenceBooleanValue(Context, String, boolean)
     * @see #setPreferenceFloatValue(Context, String, Float)
     * @see #setPreferenceIntegerValue(Context, String, Integer)
     * @see #setPreferenceStringValue(Context, String, String)
     */
    public static final String FIELD_NAME = "name";
    /**
     * Content value key for preference value.<br/>
     * It is strongly advised that you do NOT use this directly.
     * 
     * @see #setPreferenceBooleanValue(Context, String, boolean)
     * @see #setPreferenceFloatValue(Context, String, Float)
     * @see #setPreferenceIntegerValue(Context, String, Integer)
     * @see #setPreferenceStringValue(Context, String, String)
     */
    public static final String FIELD_VALUE = "value";

    private static Uri getPrefUriForKey(String key) {
        return Uri.withAppendedPath(PREF_ID_URI_BASE, key);
    }

    /**
     * Get string configuration value with null default value
     * 
     * @see MemoryPrefManager#getPreferenceStringValue(Context, String, String)
     */
    public static String getPreferenceStringValue(Context ctxt, String key) {
        return getPreferenceStringValue(ctxt, key, null);
    }

    /**
     * Helper method to retrieve a phonex string config value
     * 
     * @param ctxt The context of your app
     * @param key the key for the setting you want to get
     * @param defaultValue the value you want to return if nothing found
     * @return the preference value
     */
    public static String getPreferenceStringValue(Context ctxt, String key, String defaultValue) {
        String value = defaultValue;
        Uri uri = getPrefUriForKey(key);
        Cursor c = ctxt.getContentResolver().query(uri, null, String.class.getName(), null, null);
        if (c != null) {
            c.moveToFirst();
            String strValue = c.getString(1);
            if (strValue != null) {
                value = strValue;
            }
            c.close();
        }
        return value;
    }

    /**
     * Get boolean configuration value with null default value
     * 
     * @see MemoryPrefManager#getPreferenceBooleanValue(Context, String, Boolean)
     */
    public static Boolean getPreferenceBooleanValue(Context ctxt, String key) {
        return getPreferenceBooleanValue(ctxt, key, null);
    }

    /**
     * Helper method to retrieve a phonex boolean config value
     * 
     * @param ctxt The context of your app
     * @param key the key for the setting you want to get
     * @param defaultValue the value you want to return if nothing found
     * @return the preference value
     */
    public static Boolean getPreferenceBooleanValue(Context ctxt, String key, Boolean defaultValue) {
        Boolean value = defaultValue;
        Uri uri = getPrefUriForKey(key);
        Cursor c = ctxt.getContentResolver().query(uri, null, Boolean.class.getName(), null, null);
        if (c != null) {
            c.moveToFirst();
            int intValue = c.getInt(1);
            if (intValue >= 0) {
                value = (intValue == 1);
            }
            c.close();
        }
        return value;
    }

    /**
     * Get float configuration value with null default value
     * 
     * @see MemoryPrefManager#getPreferenceFloatValue(Context, String, Float)
     */
    public static Float getPreferenceFloatValue(Context ctxt, String key) {
        return getPreferenceFloatValue(ctxt, key, null);
    }

    /**
     * Helper method to retrieve a phonex float config value
     * 
     * @param ctxt The context of your app
     * @param key the key for the setting you want to get
     * @param defaultValue the value you want to return if nothing found
     * @return the preference value
     */
    public static Float getPreferenceFloatValue(Context ctxt, String key, Float defaultValue) {
        Float value = defaultValue;
        Uri uri = getPrefUriForKey(key);
        Cursor c = ctxt.getContentResolver().query(uri, null, Float.class.getName(), null, null);
        if (c != null) {
            c.moveToFirst();
            Float fValue = c.getFloat(1);
            if (fValue != null) {
                value = fValue;
            }
            c.close();
        }
        return value;
    }

    /**
     * Get integer configuration value with null default value
     * 
     * @see MemoryPrefManager#getPreferenceIntegerValue(Context, String, Integer)
     */
    public static Integer getPreferenceIntegerValue(Context ctxt, String key) {
        return getPreferenceIntegerValue(ctxt, key, null);
    }

    /**
     * Helper method to retrieve a phonex float config value
     * 
     * @param ctxt The context of your app
     * @param key the key for the setting you want to get
     * @param defaultValue the value you want to return if nothing found
     * @return the preference value
     */
    public static Integer getPreferenceIntegerValue(Context ctxt, String key, Integer defaultValue) {
        Integer value = defaultValue;
        Uri uri = getPrefUriForKey(key);
        Cursor c = ctxt.getContentResolver().query(uri, null, Integer.class.getName(), null, null);
        if (c != null) {
            c.moveToFirst();
            Integer iValue = c.getInt(1);
            if (iValue != null) {
                value = iValue;
            }
            c.close();
        }
        return value;
    }

    /**
     * Helper method to retrieve a phonex long config value
     *
     * @param ctxt The context of your app
     * @param key the key for the setting you want to get
     * @param defaultValue the value you want to return if nothing found
     * @return the preference value
     */
    public static Long getPreferenceLongValue(Context ctxt, String key, Long defaultValue) {
        Long value = defaultValue;
        Uri uri = getPrefUriForKey(key);
        Cursor c = ctxt.getContentResolver().query(uri, null, Long.class.getName(), null, null);
        if (c != null) {
            c.moveToFirst();
            Long iValue = c.getLong(1);
            if (iValue != null) {
                value = iValue;
            }
            c.close();
        }
        return value;
    }

    /**
     * Set the value of a preference string
     * 
     * @param ctxt The context of android app
     * @param key The key config to change
     * @param value The value to set to
     */
    public static void setPreferenceStringValue(Context ctxt, String key, String value) {
        Uri uri = getPrefUriForKey(key);
        ContentValues values = new ContentValues();
        values.put(MemoryPrefManager.FIELD_VALUE, value);
        ctxt.getContentResolver().update(uri, values, String.class.getName(), null);
    }

    public static void deletePreferenceStringValue(Context ctxt, String key) {
        Uri uri = getPrefUriForKey(key);
        ctxt.getContentResolver().delete(uri, String.class.getName(), null);
    }

    /**
     * Set the value of a preference string
     * 
     * @param ctxt The context of android app
     * @param key The key config to change
     * @param value The value to set to
     */
    public static void setPreferenceBooleanValue(Context ctxt, String key, boolean value) {
        Uri uri = getPrefUriForKey(key);
        ContentValues values = new ContentValues();
        values.put(MemoryPrefManager.FIELD_VALUE, value);
        ctxt.getContentResolver().update(uri, values, Boolean.class.getName(), null);
    }



    /**
     * Set the value of a preference string
     * 
     * @param ctxt The context of android app
     * @param key The key config to change
     * @param value The value to set to
     */
    public static void setPreferenceFloatValue(Context ctxt, String key, Float value) {
        Uri uri = getPrefUriForKey(key);
        ContentValues values = new ContentValues();
        values.put(MemoryPrefManager.FIELD_VALUE, value);
        ctxt.getContentResolver().update(uri, values, Float.class.getName(), null);
    }

    /**
     * Set the value of a preference integer
     * 
     * @param ctxt The context of android app
     * @param key The key config to change
     * @param value The value to set to
     */
    public static void setPreferenceIntegerValue(Context ctxt, String key, Integer value) {
        if (value != null) {
            setPreferenceStringValue(ctxt, key, value.toString());
        }
    }

    /**
     * Set the value of a preference long.
     *
     * @param ctxt The context of android app
     * @param key The key config to change
     * @param value The value to set to
     */
    public static void setPreferenceLongValue(Context ctxt, String key, Long value) {
        Uri uri = getPrefUriForKey(key);
        ContentValues values = new ContentValues();
        values.put(MemoryPrefManager.FIELD_VALUE, value);
        ctxt.getContentResolver().update(uri, values, Long.class.getName(), null);
    }
    
    /**
     * Clears stored credentials about currently logged user.
     * @param ctxt
     */
    public static void clearCredentials(Context ctxt){
    	setPreferenceStringValue(ctxt, CRED_PEM_PASS, null);
    	setPreferenceStringValue(ctxt, CRED_STORAGE_PASS, null);
    	setPreferenceStringValue(ctxt, CRED_USR_NAME, null);
    	setPreferenceStringValue(ctxt, CRED_USR_PASS, null);
        setPreferenceStringValue(ctxt, CRED_SET, null);
    }

    public static boolean hasStoredCredentials(Context ctxt){
        // just check whether sip is stored
        return getPreferenceStringValue(ctxt, CRED_USR_NAME) != null;
    }

    
    /**
     * Loads complete credentials data from in-memory database. 
     *  
     * @param ctxt
     * @return
     */
    public static StoredCredentials loadCredentials(Context ctxt){
    	StoredCredentials creds = new StoredCredentials();
    	
    	final String usrName = getPreferenceStringValue(ctxt, CRED_USR_NAME);
    	final String usrPass = getPreferenceStringValue(ctxt, CRED_USR_PASS);
    	final String usrPem = getPreferenceStringValue(ctxt, CRED_PEM_PASS);
    	final String usrStorage = getPreferenceStringValue(ctxt, CRED_STORAGE_PASS);
    	
    	// Reset empty strings to null
    	creds.setUserSip(TextUtils.isEmpty(usrName) ? null : usrName);
    	creds.setUsrPass(TextUtils.isEmpty(usrPass) ? null : usrPass);
    	creds.setUsrPemPass(TextUtils.isEmpty(usrPem) ? null : usrPem);
    	creds.setUsrStoragePass(TextUtils.isEmpty(usrStorage) ? null : usrStorage);
        creds.setSet(getPreferenceBooleanValue(ctxt, CRED_SET, false));
    	
    	return creds;
    }
    
    /**
     * Updates given credentials in database.
     * NullFields are updated.
     * 
     * @param ctxt
     * @param creds
     */
    public static void updateCredentials(Context ctxt, StoredCredentials creds){
    	updateCredentials(ctxt, creds, true);
    }
    
    /**
     * Updates given credentials in database.
     * 
     * @param ctxt
     * @param creds
     * @param updateNull  specifies whether to update also null fields in creds. If false, null wont be stored.
     */
    public static void updateCredentials(Context ctxt, StoredCredentials creds, boolean updateNull){
    	if (creds==null){
    		throw new IllegalArgumentException("StoredCredentials object cannot be null.");
    	}
    	
    	// User name
    	if (updateNull || creds.getUserSip()!=null){
    		setPreferenceStringValue(ctxt, CRED_USR_NAME, creds.getUserSip());
    	}
    	
    	// User pass
    	if (updateNull || creds.getUsrPass()!=null){
    		setPreferenceStringValue(ctxt, CRED_USR_PASS, creds.getUsrPass());
    	}
    	
    	// User pem pass
    	if (updateNull || creds.getUsrPemPass()!=null){
    		setPreferenceStringValue(ctxt, CRED_PEM_PASS, creds.getUsrPemPass());
    	}
    	
    	// User storage pass
    	if (updateNull || creds.getUsrStoragePass()!=null){
    		setPreferenceStringValue(ctxt, CRED_STORAGE_PASS, creds.getUsrStoragePass());
    	}

        // Set
        if (updateNull){
            setPreferenceBooleanValue(ctxt, CRED_SET, creds.isSet());
        }
    }

    /**
     * Sets data to an in-memory encrypted clipboard.
     * @param ctxt
     * @param data
     */
    public static void setClipboard(Context ctxt, String desc, String data){
        setPreferenceStringValue(ctxt, CLIPBOARD_MAIN, data);
        setPreferenceStringValue(ctxt, CLIPBOARD_MAIN_DESC, desc);
    }

    /**
     * Returns string data stored in the main in-memory encrypted clipboard.
     * @param ctxt
     * @return
     */
    public static String getClipboard(Context ctxt){
        return getPreferenceStringValue(ctxt, CLIPBOARD_MAIN, null);
    }

    /**
     * Returns string data stored in the main in-memory encrypted clipboard.
     * @param ctxt
     * @return
     */
    public static String getClipboardDesc(Context ctxt){
        return getPreferenceStringValue(ctxt, CLIPBOARD_MAIN_DESC, null);
    }

    /**
     * Saves an encrypted snapshot of the memory content.
     * Uses ContentProviderClient that is released after snapshot so there is
     * no binding between calling process and the provider.
     *
     * If provider dies and there is a relation, calling process dies also.
     *
     * @param ctxt
     * @param salt
     * @param iv
     */
    public static void saveSnapshot(Context ctxt, String salt, String iv){
        boolean useFallback=true;
        final Uri uri = ContentUris.withAppendedId(CALLSIM_ID_URI_BASE, DBMemoryPrefsProvider.CALL_SNAPSHOT_SAVE);

        // Try to use direct provider so it can be released after snapshot is done.
        try {
            final ContentProviderClient contentProviderClient = ctxt.getContentResolver().acquireContentProviderClient(MemoryPrefManager.AUTHORITY);
            if (contentProviderClient != null) {
                contentProviderClient.query(uri, null, null, new String[]{salt, iv}, null);
                contentProviderClient.release();
                useFallback=false;
            }
        } catch(Exception ex) {
            Log.e(TAG, "Exception in CPL", ex);
        }

        // Fallback method if everything conventional fails.
        if (useFallback){
            Log.d(TAG, "Using fallback method");
            ctxt.getContentResolver().query(uri, null, null, new String[]{salt, iv}, null);
        }
    }

    /**
     * Loads an encrypted snapshot of the memory content.
     * @param ctxt
     * @param salt
     * @param iv
     */
    public static void loadSnapshot(Context ctxt, String salt, String iv){
        Uri uri = ContentUris.withAppendedId(CALLSIM_ID_URI_BASE, DBMemoryPrefsProvider.CALL_SNAPSHOT_LOAD);
        ctxt.getContentResolver().query(uri, null, null, new String[] {salt, iv}, null);
    }

}

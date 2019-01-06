package net.phonex.db;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.text.TextUtils;

import net.phonex.autologin.LoginCredentials;
import net.phonex.autologin.exceptions.PasswordPersisterException;
import net.phonex.autologin.exceptions.ServiceUnavailableException;
import net.phonex.autologinQuick.QuickLoginPersister;
import net.phonex.core.Constants;
import net.phonex.core.Intents;
import net.phonex.db.DBHelper.DatabaseHelper;
import net.phonex.db.entity.AccountingLog;
import net.phonex.db.entity.AccountingPermission;
import net.phonex.db.entity.CallFirewall;
import net.phonex.db.entity.CallLog;
import net.phonex.db.entity.DHOffline;
import net.phonex.db.entity.FileStorage;
import net.phonex.db.entity.FileTransfer;
import net.phonex.db.entity.PairingRequest;
import net.phonex.db.entity.QueuedMessage;
import net.phonex.db.entity.ReceivedFile;
import net.phonex.db.entity.SipClist;
import net.phonex.db.entity.SipMessage;
import net.phonex.db.entity.SipProfile;
import net.phonex.db.entity.SipProfileState;
import net.phonex.db.entity.SipSignatureWarning;
import net.phonex.db.entity.Thumbnail;
import net.phonex.db.entity.TrialEventLog;
import net.phonex.db.entity.UserCertificate;
import net.phonex.db.scheme.CallFirewallScheme;
import net.phonex.db.scheme.CallLogScheme;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.guava.Joiner;
import net.sqlcipher.SQLException;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteQueryBuilder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;


public class DBProvider extends ContentProvider {
	private static final String THIS_FILE = "DBProvider";
	private static final String UNKNOWN_URI_LOG = "Unknown URI ";
	
	// DatabaseHelper, handler DB creation, versioning, opening.
	private DatabaseHelper mOpenHelper;
	
	// Whether to allow bulk insert to use a single transaction.
	private boolean allowBulkInsertInTransaction = true;

    // If currently applying a batch operation.
    private final AtomicBoolean applyingBatch = new AtomicBoolean(false);
    private DBNotifications delayer;
	
	// Mode of operation if database is not connected in time of query execution
	// and connection cannot be made (e.g., password needed).
	private int dbNotConnectedMode = DB_CONNECTION_MODE_RETURN_NULL;
	public static final int DB_CONNECTION_MODE_RETURN_NULL=1;		// Returns null/zero to all data manipulation queries.
	public static final int DB_CONNECTION_MODE_USE_PLAIN=2;		// Use plain SQLCipher database.
	public static final int DB_CONNECTION_MODE_EXCEPTION=3;		// Throws exception in data manipulation queries.
	
	// Encrypted database related
	public static final String SET_KEY_METHOD="setKey";
    public static final String SET_KEY_METHOD_EXTRA_KEY ="key";
    public static final String SET_KEY_METHOD_EXTRA_SIP = "userSip";

	public static final String SET_NEW_KEY_METHOD="reKey";
	public static final String TEST_KEY_METHOD="testKey";
	public static final String SET_DB_ERR_MODE_METHOD="setDBErrorMode";
	public static final String TEST_KEY_RETURN_KEY="testKeyReturn";
	public static final String SET_NEW_KEY_EXTRA_CLOSE_DB="closeDB";
	public static final String CLOSE_DB_METHOD="closeDBMethod";

	// Ids for matcher
    private static final int ACCOUNTS = 1, ACCOUNTS_ID = 2;
    private static final int ACCOUNTS_STATUS = 3, ACCOUNTS_STATUS_ID = 4;
    private static final int CALLLOGS = 5, CALLLOGS_ID = 6;
    private static final int FILTERS = 7, FILTERS_ID = 8;
    private static final int MESSAGES = 9, MESSAGES_ID = 10;
    private static final int THREADS = 11, THREADS_ID = 12;
    private static final int THREADS_ID_ID = 13; //specify both sender and receiver
    private static final int THREADS_UNREAD_COUNT = 14;
    private static final int CLIST = 50, CLIST_ID = 51;
    private static final int CERT = 52, CERT_ID = 53;
    private static final int CLIST_STATE = 54, CLIST_STATE_ID = 55;
    private static final int SIGNATURE_WARNING = 60, SIGNATURE_WARNING_ID = 61;
    private static final int KV_STORAGE = 70, KV_STORAGE_ID = 71;			// In-memory key-value storage for strings.

    private static final int DH_OFFLINE = 90, DH_OFFLINE_ID = 91; // pregenerated DiffieHellman keys    
    private static final int RECEIVED_FILES = 100, RECEIVED_FILES_ID = 101;
    private static final int QUEUED_MESSAGE = 110, QUEUED_MESSAGE_ID = 111;
    private static final int QUEUED_MESSAGE_NEWEST_PER_RECIPIENT = 112;
    private static final int FILE_TRANSFER = 120, FILE_TRANSFER_ID = 121;
    private static final int TRIAL_EVENT_LOG = 130, TRIAL_EVENT_LOG_ID = 131;
    private static final int FILE_STORAGE = 140, FILE_STORAGE_ID = 141;
    private static final int THUMBNAIL = 150, THUMBNAIL_ID = 151;
    private static final int PAIRING_REQUEST = 160, PAIRING_REQUEST_ID = 161;
    private static final int ACCOUNTING_LOG = 170;
    private static final int ACCOUNTING_LOG_ID = 171;
    private static final int ACCOUNTING_PERMISSION = 180;
    private static final int ACCOUNTING_PERMISSION_ID = 181;


    private static final Class<?> CONTENT_RESOLVER_CALL_PARAM_TYPES[] = new Class[] {Uri.class, String.class, String.class, Bundle.class};
    
    // KV URI
    public static final String KV_STORAGE_NAME = "kvstorage";
    public static final Uri KV_STORAGE_URI = Uri.parse(ContentResolver.SCHEME_CONTENT + "://" + Constants.AUTHORITY + "/" + KV_STORAGE_NAME);
    public static final Uri KV_STORAGE_ID_URI_BASE = Uri.parse(ContentResolver.SCHEME_CONTENT + "://" + Constants.AUTHORITY + "/" + KV_STORAGE_NAME + "/");
    
    // Custom permissions check
    public static final int METHOD_CALL = 1;
    public static final int METHOD_QUERY = 2;
    public static final int METHOD_INSERT = 3;
    public static final int METHOD_UPDATE = 4;
    public static final int METHOD_DELETE = 5;

    // Map active account id (id for SQL settings database) with SipProfileState that contains stack id and other status info.
	// In-memory cached "database".
	private final Map<Long, ContentValues> profilesStatus = new HashMap<>();

	
	// Selection statement for message threads.
	private static final String MESSAGES_THREAD_SELECTION = "("+ SipMessage.FIELD_FROM+"=? )"
		    + " OR " +
		    "("+ SipMessage.FIELD_TO+"=? )";
		    
    // Selection statement for conversation between specified subjects.
    private static final String MESSAGES_THREAD_SELECTION_SUBJECTS = "("+ SipMessage.FIELD_FROM+"=? AND "+
		    		SipMessage.FIELD_TO+"=? )"
		        + " OR " +
		        "("+ SipMessage.FIELD_TO+"=? AND "+
		        	SipMessage.FIELD_FROM+"=? )";	

    /**
     * A UriMatcher instance & static initialization.
     */
    private static final UriMatcher URI_MATCHER;
    static {
        // Create and initialize URI matcher.
        // HOWTO: URI nodes may be exact match string, the token "*" that matches any text, or the token "#" that matches only numbers.
        URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

        URI_MATCHER.addURI(Constants.AUTHORITY, SipProfile.ACCOUNTS_TABLE_NAME, ACCOUNTS);
        URI_MATCHER.addURI(Constants.AUTHORITY, SipProfile.ACCOUNTS_TABLE_NAME + "/#", ACCOUNTS_ID);
        URI_MATCHER.addURI(Constants.AUTHORITY, SipProfile.ACCOUNTS_STATUS_TABLE_NAME, ACCOUNTS_STATUS);
        URI_MATCHER.addURI(Constants.AUTHORITY, SipProfile.ACCOUNTS_STATUS_TABLE_NAME + "/#", ACCOUNTS_STATUS_ID);
        URI_MATCHER.addURI(Constants.AUTHORITY, CallLogScheme.TABLE, CALLLOGS);
        URI_MATCHER.addURI(Constants.AUTHORITY, CallLogScheme.TABLE + "/#", CALLLOGS_ID);
        URI_MATCHER.addURI(Constants.AUTHORITY, CallFirewallScheme.TABLE, FILTERS);
        URI_MATCHER.addURI(Constants.AUTHORITY, CallFirewallScheme.TABLE + "/#", FILTERS_ID);
        URI_MATCHER.addURI(Constants.AUTHORITY, SipMessage.TABLE_NAME, MESSAGES);
        URI_MATCHER.addURI(Constants.AUTHORITY, SipMessage.TABLE_NAME + "/#", MESSAGES_ID);
        URI_MATCHER.addURI(Constants.AUTHORITY, SipMessage.THREAD_ALIAS, THREADS);
        URI_MATCHER.addURI(Constants.AUTHORITY, SipMessage.THREAD_ALIAS + "/*", THREADS_ID);
        URI_MATCHER.addURI(Constants.AUTHORITY, SipMessage.THREAD_ALIAS + "/*/*", THREADS_ID_ID);
        URI_MATCHER.addURI(Constants.AUTHORITY, SipMessage.THREAD_UNREAD_COUNT_ALIAS, THREADS_UNREAD_COUNT);

        URI_MATCHER.addURI(Constants.AUTHORITY, SipClist.SIP_CONTACTLIST_TABLE, CLIST);
        URI_MATCHER.addURI(Constants.AUTHORITY, SipClist.SIP_CONTACTLIST_TABLE + "/#", CLIST_ID);
        URI_MATCHER.addURI(Constants.AUTHORITY, SipClist.CLIST_STATUS_TABLE_NAME, CLIST_STATE);
        URI_MATCHER.addURI(Constants.AUTHORITY, SipClist.CLIST_STATUS_TABLE_NAME + "/#", CLIST_STATE_ID);
        URI_MATCHER.addURI(Constants.AUTHORITY, UserCertificate.TABLE, CERT);
        URI_MATCHER.addURI(Constants.AUTHORITY, UserCertificate.TABLE + "/#", CERT_ID);
        URI_MATCHER.addURI(Constants.AUTHORITY, SipSignatureWarning.TABLE, SIGNATURE_WARNING);
        URI_MATCHER.addURI(Constants.AUTHORITY, SipSignatureWarning.TABLE + "/#", SIGNATURE_WARNING_ID);

        URI_MATCHER.addURI(Constants.AUTHORITY, KV_STORAGE_NAME, KV_STORAGE);
        URI_MATCHER.addURI(Constants.AUTHORITY, KV_STORAGE_NAME + "/#", KV_STORAGE_ID);

        URI_MATCHER.addURI(Constants.AUTHORITY, DHOffline.TABLE, DH_OFFLINE);
        URI_MATCHER.addURI(Constants.AUTHORITY, DHOffline.TABLE + "/#", DH_OFFLINE_ID);

        URI_MATCHER.addURI(Constants.AUTHORITY, ReceivedFile.TABLE_NAME, RECEIVED_FILES);
        URI_MATCHER.addURI(Constants.AUTHORITY, ReceivedFile.TABLE_NAME + "/#", RECEIVED_FILES_ID);
        URI_MATCHER.addURI(Constants.AUTHORITY, FileTransfer.TABLE, FILE_TRANSFER);
        URI_MATCHER.addURI(Constants.AUTHORITY, FileTransfer.TABLE + "/#", FILE_TRANSFER_ID);

        URI_MATCHER.addURI(Constants.AUTHORITY, QueuedMessage.TABLE_NAME, QUEUED_MESSAGE);
        URI_MATCHER.addURI(Constants.AUTHORITY, QueuedMessage.TABLE_NAME + "/#", QUEUED_MESSAGE_ID);
        URI_MATCHER.addURI(Constants.AUTHORITY, QueuedMessage.NEWEST_MSG_PER_RECIPIENT_NAME, QUEUED_MESSAGE_NEWEST_PER_RECIPIENT);

        URI_MATCHER.addURI(Constants.AUTHORITY, TrialEventLog.TABLE, TRIAL_EVENT_LOG);
        URI_MATCHER.addURI(Constants.AUTHORITY, TrialEventLog.TABLE+ "/#", TRIAL_EVENT_LOG_ID);
		
        URI_MATCHER.addURI(Constants.AUTHORITY, FileStorage.TABLE, FILE_STORAGE);
        URI_MATCHER.addURI(Constants.AUTHORITY, FileStorage.TABLE + "/#", FILE_STORAGE_ID);

        URI_MATCHER.addURI(Constants.AUTHORITY, Thumbnail.TABLE, THUMBNAIL);
        URI_MATCHER.addURI(Constants.AUTHORITY, Thumbnail.TABLE + "/#", THUMBNAIL_ID);

        URI_MATCHER.addURI(Constants.AUTHORITY, PairingRequest.TABLE, PAIRING_REQUEST);
        URI_MATCHER.addURI(Constants.AUTHORITY, PairingRequest.TABLE + "/#", PAIRING_REQUEST_ID);

        URI_MATCHER.addURI(Constants.AUTHORITY, AccountingLog.TABLE, ACCOUNTING_LOG);
        URI_MATCHER.addURI(Constants.AUTHORITY, AccountingLog.TABLE + "/#", ACCOUNTING_LOG_ID);

        URI_MATCHER.addURI(Constants.AUTHORITY, AccountingPermission.TABLE, ACCOUNTING_PERMISSION);
        URI_MATCHER.addURI(Constants.AUTHORITY, AccountingPermission.TABLE + "/#", ACCOUNTING_PERMISSION_ID);
    }

    /**
     * Returns content type for given database URI.
     */
	@Override
	public String getType(Uri uri) {
		switch (URI_MATCHER.match(uri)) {
            case ACCOUNTS:
                return SipProfile.ACCOUNT_CONTENT_TYPE;
            case ACCOUNTS_ID:
                return SipProfile.ACCOUNT_CONTENT_ITEM_TYPE;
            case ACCOUNTS_STATUS:
            	return SipProfile.ACCOUNT_STATUS_CONTENT_TYPE;
            case ACCOUNTS_STATUS_ID:
            	return SipProfile.ACCOUNT_STATUS_CONTENT_ITEM_TYPE;
            case CALLLOGS :
            	return CallLogScheme.CONTENT_TYPE;
            case CALLLOGS_ID :
            	return CallLogScheme.CONTENT_ITEM_TYPE;
            case FILTERS:
            	return Constants.FILTER_CONTENT_TYPE;
            case FILTERS_ID:
            	return Constants.FILTER_CONTENT_ITEM_TYPE;
            case MESSAGES:
                return SipMessage.MESSAGE_CONTENT_TYPE;
            case MESSAGES_ID:
                return SipMessage.MESSAGE_CONTENT_ITEM_TYPE;
            case THREADS:
                return SipMessage.MESSAGE_CONTENT_TYPE;
            case THREADS_ID:
                return SipMessage.MESSAGE_CONTENT_ITEM_TYPE;
            case THREADS_ID_ID:
                return SipMessage.MESSAGE_CONTENT_ITEM_TYPE;
            case THREADS_UNREAD_COUNT:
                return SipMessage.MESSAGE_CONTENT_ITEM_TYPE;
            default:
                throw new IllegalArgumentException(UNKNOWN_URI_LOG + uri);
        }
	}
	
	/**
	 * onCreate() method
	 * Inherited from ContentProvider.
	 * 
	 * Called when ContentProvider is created. Initializes DatabaseHelper used for 
	 * provider lifetime.
	 */
	@Override
	public boolean onCreate() {
        Log.df(THIS_FILE, "onCreate;");
        // Has to load SQLCipher libraries at first
        SQLiteDatabase.loadLibs(getContext(), getContext().getFilesDir());

        // Delayer for DB notifications.
        delayer = new DBNotifications(this);

        tryOpenDatabase(getContext());

        // Assumes that any failures will be reported by a thrown exception.
        return true;
	}

    private void tryOpenDatabase(Context context) {
        if (mOpenHelper != null){
            Log.wf(THIS_FILE, "Database already opened");
            return;
        }

        try {
            QuickLoginPersister quickLoginPersister = new QuickLoginPersister(context);
            LoginCredentials credentials = quickLoginPersister.loadCredentials();
            Log.inf(THIS_FILE, "Quick login credentials loaded");
            Log.inf(THIS_FILE, "Storage credentials derived");
            final String dbPassword = DBHelper.DatabaseHelper.formatDbPassword(credentials.getSip(), credentials.password);

            createDbHelperAndSetKey(dbPassword, credentials.getSip());
            final int checkRes = mOpenHelper.checkKey(true);
            Log.inf(THIS_FILE, "Database opened: %s, checkRes: %d", mOpenHelper.getDbFilename(), checkRes);

        } catch (PasswordPersisterException | ServiceUnavailableException e) {
            Log.wf(THIS_FILE, e, "Unable to load quick login creds, continuing");
        }
    }

    private void createDbHelper(String sip) throws NoSuchAlgorithmException {
        mOpenHelper = new DatabaseHelper(getContext(), sip);
        Log.vf(THIS_FILE, "New database holder was created=%s; this=%s", mOpenHelper, this);
    }

    private void createDbHelperAndSetKey(String key, String sip){
        try {
            Log.vf(THIS_FILE, "createDbHelperAndSetKey; key=[%s], sip=[%s]", key, sip);
            createDbHelper(sip);
            mOpenHelper.setEncKeyAndSip(key, sip);
        } catch(Exception e){
            throw new SQLException("Exception in setting encryption key; reason=" + e.getMessage());
        }
    }

	/**
	 * call() method 
	 * Inherited from ContentProvider
	 * 
	 * User may set encryption password using this method and connect the database.
	 * 
	 * Warning! Android documentation says there is no privilege check before this call.
	 * Has to handle it on our own.
	 */
	public Bundle call(String method, String arg, Bundle extras) {
		checkPermissions(METHOD_CALL, null);
		
		if (SET_KEY_METHOD.equals(method) && extras != null) {
			// Set encryption key to the helper + sip (for DB name)
            final String key = extras.getString(SET_KEY_METHOD_EXTRA_KEY);
            final String sip = extras.getString(SET_KEY_METHOD_EXTRA_SIP);
            if (key == null){
                throw new IllegalArgumentException("Exception in setting encryption key; reason=missing encryption key");
            } else if (sip == null){
                throw new IllegalArgumentException("Exception in setting encryption key; reason=missing sip");
            }
            // TODO check that DB is already opened
            createDbHelperAndSetKey(key, sip);
			return null;
		} else if (SET_NEW_KEY_METHOD.equals(method) && arg != null){
			// Sets new encryption key - re-keys database.
			//
			int ret = 0;
			if (extras!=null && extras.containsKey(SET_NEW_KEY_EXTRA_CLOSE_DB)){
				ret = mOpenHelper.changeKey(arg, extras.getBoolean(SET_NEW_KEY_EXTRA_CLOSE_DB));
			} else {
				ret = mOpenHelper.changeKey(arg);
			}
			
			Log.vf(THIS_FILE, "SET_NEW_KEY_METHOD: Result=%s", ret);
			
			Bundle b2return = new Bundle(1);
			b2return.putInt(TEST_KEY_RETURN_KEY, ret);
			return b2return;
			
		} else if (TEST_KEY_METHOD.equals(method) && arg != null){
			// Test whether encryption key is valid.
			//
			int ret = mOpenHelper.checkKey(Boolean.parseBoolean(arg));
			Log.vf(THIS_FILE, "TEST_KEY_METHOD: Result=%s", ret);
			
			Bundle b2return = new Bundle(1);
			b2return.putInt(TEST_KEY_RETURN_KEY, ret);
			return b2return;
			
		} else if (SET_DB_ERR_MODE_METHOD.equals(method) && arg != null){
			// Sets database error mode if database is not connected.
			//
			int c = -1;
			try {
				c = Integer.parseInt(arg);
			} catch(Exception e){
				Log.w(THIS_FILE, "Cannot parse integer");
			}
			
			this.dbNotConnectedMode = c;
		} else if (CLOSE_DB_METHOD.equals(method)){
            if (mOpenHelper != null){
                mOpenHelper.close();
                mOpenHelper = null;
                Log.inf(THIS_FILE, "%s method; mOpenHelper was closed", CLOSE_DB_METHOD);
            } else {
                Log.wf(THIS_FILE, "%s method; mOpenHelper is null, cannot close", CLOSE_DB_METHOD);
            }
        }
		
		return null;
	}
	
    /**
	 * delete() method 
	 * Inherited from ContentProvider
	 */
	@Override
	public int delete(Uri uri, String where, String[] whereArgs) {
		checkPermissions(METHOD_DELETE, uri);
        if (mOpenHelper == null){
            Log.wf(THIS_FILE, "DBHelper null yet");
            return 0;
        }
		
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        if (db==null){
        	Log.d(THIS_FILE, "Database returned in delete() is null");
        	switch(this.dbNotConnectedMode){
        	case DB_CONNECTION_MODE_EXCEPTION:
        		throw new DatabaseNotConnectedException();
        	case DB_CONNECTION_MODE_RETURN_NULL:
        		return 0;
        	default:
        		throw new SQLException("Undefined mode of operation");
        	}
        }

        String finalWhere;
        int count = 0;
        int matched = URI_MATCHER.match(uri);
        Uri regUri = uri;
        
        ArrayList<Long> oldRegistrationsAccounts = null;
        
        switch (matched) {
            case ACCOUNTS:
                count = db.delete(SipProfile.ACCOUNTS_TABLE_NAME, where, whereArgs);
                break;
            case ACCOUNTS_ID:
            	finalWhere = DatabaseUtils.concatenateWhere(SipProfile.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.delete(SipProfile.ACCOUNTS_TABLE_NAME, finalWhere, whereArgs);
                break;
            case CALLLOGS:
                count = db.delete(CallLogScheme.TABLE, where, whereArgs);
            	break;
            case CALLLOGS_ID:
            	finalWhere = DatabaseUtils.concatenateWhere(CallLogScheme.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.delete(CallLogScheme.TABLE, finalWhere, whereArgs);
                break;
            case FILTERS:
            	count = db.delete(CallFirewallScheme.TABLE, where, whereArgs);
            	break;
            case FILTERS_ID:
            	finalWhere = DatabaseUtils.concatenateWhere(CallFirewallScheme.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.delete(CallFirewallScheme.TABLE, finalWhere, whereArgs);
                break;
            case MESSAGES:
                count = db.delete(SipMessage.TABLE_NAME, where, whereArgs);
                break;
            case MESSAGES_ID:
                finalWhere = DatabaseUtils.concatenateWhere(SipMessage.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.delete(SipMessage.TABLE_NAME, finalWhere, whereArgs);
                break;
            case CLIST:
            case CLIST_STATE:
                count = db.delete(SipClist.SIP_CONTACTLIST_TABLE, where, whereArgs);
                break;
            case CLIST_ID:
            case CLIST_STATE_ID:
                finalWhere = DatabaseUtils.concatenateWhere(SipClist.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.delete(SipClist.SIP_CONTACTLIST_TABLE, finalWhere, whereArgs);
                break;
            case CERT:
                count = db.delete(UserCertificate.TABLE, where, whereArgs);
                break;
            case CERT_ID:
                finalWhere = DatabaseUtils.concatenateWhere(UserCertificate.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.delete(UserCertificate.TABLE, finalWhere, whereArgs);
                break;
            case SIGNATURE_WARNING:
                count = db.delete(SipSignatureWarning.TABLE, where, whereArgs);
                break;
            case SIGNATURE_WARNING_ID:
                finalWhere = DatabaseUtils.concatenateWhere(SipSignatureWarning.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.delete(SipSignatureWarning.TABLE, finalWhere, whereArgs);
                break;
            case DH_OFFLINE:
                count = db.delete(DHOffline.TABLE, where, whereArgs);
                break;
            case DH_OFFLINE_ID:
                finalWhere = DatabaseUtils.concatenateWhere(DHOffline.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.delete(DHOffline.TABLE, finalWhere, whereArgs);
                break;
            case RECEIVED_FILES:
                count = db.delete(ReceivedFile.TABLE_NAME, where, whereArgs);
                break;
            case RECEIVED_FILES_ID:
                finalWhere = DatabaseUtils.concatenateWhere(ReceivedFile.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.delete(ReceivedFile.TABLE_NAME, finalWhere, whereArgs);
                break;
            case FILE_TRANSFER:
                count = db.delete(FileTransfer.TABLE, where, whereArgs);
                break;
            case FILE_TRANSFER_ID:
                finalWhere = DatabaseUtils.concatenateWhere(FileTransfer.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.delete(FileTransfer.TABLE, finalWhere, whereArgs);
                break;
            case QUEUED_MESSAGE:
                count = db.delete(QueuedMessage.TABLE_NAME, where, whereArgs);
                break;
            case QUEUED_MESSAGE_ID:
                finalWhere = DatabaseUtils.concatenateWhere(QueuedMessage.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.delete(QueuedMessage.TABLE_NAME, finalWhere, whereArgs);
                break;
            case FILE_STORAGE:
                count = db.delete(FileStorage.TABLE, where, whereArgs);
                break;
            case FILE_STORAGE_ID:
                finalWhere = DatabaseUtils.concatenateWhere(FileStorage.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.delete(FileStorage.TABLE, finalWhere, whereArgs);
                break;
            case THUMBNAIL:
                count = db.delete(Thumbnail.TABLE, where, whereArgs);
                break;
            case THUMBNAIL_ID:
                finalWhere = DatabaseUtils.concatenateWhere(Thumbnail.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.delete(Thumbnail.TABLE, finalWhere, whereArgs);
                break;
            case THREADS_ID:
                String from = uri.getLastPathSegment();
                if(!TextUtils.isEmpty(from)) {
                    count = db.delete(SipMessage.TABLE_NAME, MESSAGES_THREAD_SELECTION, new String[] {
                            from, from
                    });
                }else {
                    count = 0;
                }
                regUri = SipMessage.MESSAGE_URI;
                break;
            case ACCOUNTS_STATUS:
                oldRegistrationsAccounts = new ArrayList<Long>();
            	synchronized (profilesStatus) {
            	    for(Long accId : profilesStatus.keySet()) {
            	        oldRegistrationsAccounts.add(accId);
            	    }
        			profilesStatus.clear();
        		}
            	break;
            case ACCOUNTS_STATUS_ID:
            	long id = ContentUris.parseId(uri);
            	synchronized (profilesStatus) {
        			profilesStatus.remove(id);
        		}
            	break;
            case TRIAL_EVENT_LOG:
                count = db.delete(TrialEventLog.TABLE, where, whereArgs);
                break;
            case TRIAL_EVENT_LOG_ID:
                finalWhere = DatabaseUtils.concatenateWhere(TrialEventLog.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.delete(TrialEventLog.TABLE, finalWhere, whereArgs);
                break;
            case PAIRING_REQUEST:
                count = db.delete(PairingRequest.TABLE, where, whereArgs);
                break;
            case PAIRING_REQUEST_ID:
                finalWhere = DatabaseUtils.concatenateWhere(PairingRequest.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.delete(PairingRequest.TABLE, finalWhere, whereArgs);
                break;

            case ACCOUNTING_LOG:
                count = db.delete(AccountingLog.TABLE, where, whereArgs);
                break;
            case ACCOUNTING_LOG_ID:
                finalWhere = DatabaseUtils.concatenateWhere(AccountingLog.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.delete(AccountingLog.TABLE, finalWhere, whereArgs);
                break;

            case ACCOUNTING_PERMISSION:
                count = db.delete(AccountingLog.TABLE, where, whereArgs);
                break;
            case ACCOUNTING_PERMISSION_ID:
                finalWhere = DatabaseUtils.concatenateWhere(AccountingPermission.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.delete(AccountingPermission.TABLE, finalWhere, whereArgs);
                break;
            default:
                throw new IllegalArgumentException(UNKNOWN_URI_LOG + uri);
        }

        // Notification on this URI - delete.
        notifyChange(regUri);
        if (matched == CLIST) notifyChange(SipClist.CLIST_STATE_URI);
        if (matched == CLIST_ID) notifyChange(SipClist.CLIST_STATE_ID_URI_BASE);

        // Special notification of message deletion
        if (count > 0 && (matched == MESSAGES_ID || matched  == MESSAGES_ID || matched == THREADS_ID)){
            notifyChange(SipMessage.MESSAGE_URI_DELETED);
        }

        if(matched == ACCOUNTS_ID || matched == ACCOUNTS_STATUS_ID) {
        	long rowId = ContentUris.parseId(uri);
        	if(rowId >= 0) {
        	    if(matched == ACCOUNTS_ID) {
        	        broadcastAccountChange(rowId);
        	    }else if(matched == ACCOUNTS_STATUS_ID) {
        	        broadcastRegistrationChange(rowId);
        	    }
        	}
        }
        if (matched == FILTERS || matched == FILTERS_ID) {
            CallFirewall.resetCache();
        }
        if(matched == ACCOUNTS_STATUS && oldRegistrationsAccounts != null) {
            for(Long accId : oldRegistrationsAccounts) {
                if(accId != null) {
                    broadcastRegistrationChange(accId);
                }
            }
        }
        
		return count;
	}

	/**
	 * insert() method 
	 * Inherited from ContentProvider
	 */
	@Override
	public Uri insert(Uri uri, ContentValues initialValues) {
		InsertReturn r = insertInternal(uri, new ContentValues[] { initialValues });
        if (r == null){
            return null;
        }
		return r.uri;
	}
	
	/**
	 * bulkInsert() method.
	 * Inherited from ContentProvider
	 */
	@Override
	public int bulkInsert(Uri uri, ContentValues[] values) {
		InsertReturn r = insertInternal(uri, values);
        if (r == null){
            return 0;
        }
		return r.retInt;
	}
	
	/**
	 * Return class from insertInternal.
	 * @author ph4r05
	 */
	public static class InsertReturn {
		public InsertReturn(Uri uri, Integer integer){
			this.uri = uri;
			this.retInt = integer;
		}
		
		public Uri uri;
		public Integer retInt;
	}

	/**
	 * Internal implementation for insert. 
	 * Supports inserting of multiple elements.
	 * 
	 * To speed-up entry insert query, insert is performed in
	 * one transaction, if allowed by allowBulkInsertInTransaction.
	 * 
	 * @param uri
	 * @param initialValues
	 * @return
	 */
	public InsertReturn insertInternal(Uri uri, ContentValues[] initialValues) {
        if (mOpenHelper == null){
            Log.wf(THIS_FILE, "DBHelper null yet");
            return null;
        }
		checkPermissions(METHOD_INSERT, uri);
		if (initialValues==null){
			throw new IllegalArgumentException("Empty initial values");
		}
		
		boolean multiple = initialValues != null && initialValues.length > 1;
		int matched = URI_MATCHER.match(uri);
    	String matchedTable = null;
    	Uri baseInsertedUri = null;
    	switch (matched) {
		case ACCOUNTS:
		case ACCOUNTS_ID:
			matchedTable = SipProfile.ACCOUNTS_TABLE_NAME;
			baseInsertedUri = SipProfile.ACCOUNT_ID_URI_BASE;
			break;
		case CALLLOGS:
		case CALLLOGS_ID:
			matchedTable = CallLogScheme.TABLE;
			baseInsertedUri = CallLogScheme.ID_URI_BASE;
			break;
		case FILTERS:
		case FILTERS_ID:
			matchedTable = CallFirewallScheme.TABLE;
			baseInsertedUri = CallFirewallScheme.ID_URI_BASE;
			break;
		case MESSAGES:
		case MESSAGES_ID:
		    matchedTable = SipMessage.TABLE_NAME;
            baseInsertedUri = SipMessage.MESSAGE_ID_URI_BASE;
		    break;
		case CLIST:
		case CLIST_ID:
			matchedTable = SipClist.SIP_CONTACTLIST_TABLE;
            baseInsertedUri = SipClist.CLIST_ID_URI_BASE;
			break;
		case CLIST_STATE:
		case CLIST_STATE_ID:
			matchedTable = SipClist.SIP_CONTACTLIST_TABLE;
            baseInsertedUri = SipClist.CLIST_STATE_ID_URI_BASE;
		    break;
		case CERT:
		case CERT_ID:
			matchedTable = UserCertificate.TABLE;
            baseInsertedUri = UserCertificate.CERTIFICATE_ID_URI_BASE;
		    break;
		case SIGNATURE_WARNING:
		case SIGNATURE_WARNING_ID:
			matchedTable = SipSignatureWarning.TABLE;
            baseInsertedUri = SipSignatureWarning.SIGNATURE_WARNING_ID_URI_BASE;
		    break;
		case DH_OFFLINE:
		case DH_OFFLINE_ID:
			matchedTable = DHOffline.TABLE; 
			baseInsertedUri = DHOffline.DH_OFFLINE_ID_URI_BASE;
		    break;
		case RECEIVED_FILES:
		case RECEIVED_FILES_ID:
			matchedTable = ReceivedFile.TABLE_NAME; 
			baseInsertedUri = ReceivedFile.ID_URI_BASE;
		    break;
        case FILE_TRANSFER:
        case FILE_TRANSFER_ID:
            matchedTable = FileTransfer.TABLE;
            baseInsertedUri = FileTransfer.ID_URI_BASE;
            break;
        case QUEUED_MESSAGE:
        case QUEUED_MESSAGE_ID:
            matchedTable = QueuedMessage.TABLE_NAME;
            baseInsertedUri = QueuedMessage.ID_URI_BASE;
            break;
        case TRIAL_EVENT_LOG:
        case TRIAL_EVENT_LOG_ID:
                matchedTable = TrialEventLog.TABLE;
                baseInsertedUri = TrialEventLog.ID_URI_BASE;
                break;
        case FILE_STORAGE:
        case FILE_STORAGE_ID:
            matchedTable = FileStorage.TABLE;
            baseInsertedUri = FileStorage.ID_URI_BASE;
            break;
        case THUMBNAIL:
        case THUMBNAIL_ID:
            matchedTable = Thumbnail.TABLE;
            baseInsertedUri = Thumbnail.ID_URI_BASE;
            break;
        case PAIRING_REQUEST:
        case PAIRING_REQUEST_ID:
            matchedTable = PairingRequest.TABLE;
            baseInsertedUri = PairingRequest.ID_URI_BASE;
            break;
        case ACCOUNTING_LOG:
        case ACCOUNTING_LOG_ID:
            matchedTable = AccountingLog.TABLE;
            baseInsertedUri = AccountingLog.ID_URI_BASE;
            break;
        case ACCOUNTING_PERMISSION:
        case ACCOUNTING_PERMISSION_ID:
            matchedTable = AccountingPermission.TABLE;
            baseInsertedUri = AccountingPermission.ID_URI_BASE;
            break;
		case ACCOUNTS_STATUS_ID:
			long id = ContentUris.parseId(uri);
			if (multiple){
				throw new SQLException("Failed to insert row into " + uri + "; supports only single insert");
			}
			
			ContentValues icv = initialValues!=null && initialValues.length>=1 ? initialValues[0] : null;
			synchronized (profilesStatus){
				SipProfileState ps = new SipProfileState();
				if(profilesStatus.containsKey(id)) {
					ContentValues currentValues = profilesStatus.get(id);
					ps.createFromContentValue(currentValues);
				}
				ps.createFromContentValue(icv);
				ContentValues cv = ps.getAsContentValue();
				cv.put(SipProfileState.ACCOUNT_ID, id);
				profilesStatus.put(id, cv);
				Log.df(THIS_FILE, "Added %s", cv);
			}
            notifyChange(uri);
			return new InsertReturn(uri, null);
		default:
			break;
		}
    	
        if ( matchedTable == null ) {
            throw new IllegalArgumentException(UNKNOWN_URI_LOG + uri);
        }

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        if (db==null){
        	Log.d(THIS_FILE, "Database returned in insert() is null");
        	switch(this.dbNotConnectedMode){
        	case DB_CONNECTION_MODE_EXCEPTION:
        		throw new DatabaseNotConnectedException();
        	case DB_CONNECTION_MODE_RETURN_NULL:
        		return null;
        	default:
        		throw new SQLException("Undefined mode of operation");
        	}
        }

        // Execute inserts in a loop. 
        // Still better than calling insert() in a row on a ContentProvider.
        long lastRowId = -1;
        int successNum = 0;
        
        // Allow to perform bulk inserts in a single transaction
        if (multiple && allowBulkInsertInTransaction){
        	db.beginTransaction();
        }

        List<Long> rowIds = new ArrayList<Long>(initialValues.length);
        Exception toThrow = null;
        try {
        	// Iterate over content values, insert by-one.
	        for(ContentValues ivs : initialValues){
	        	ContentValues values;
	            if (ivs != null) {
	                values = new ContentValues(ivs);
	            } else {
	                values = new ContentValues();
	            }
	        
	            long rowId = db.insert(matchedTable, null, values);
	            rowIds.add(rowId);
	            
	            lastRowId = rowId;
	            if (rowId >=0 ){
	            	successNum += 1;
	            }
	        }
	        
	        // Finish transaction, if was started.
	        if (multiple && allowBulkInsertInTransaction){
	        	db.setTransactionSuccessful();
	        }
        } catch (Exception e){
        	Log.e(THIS_FILE, "Exception in transaction", e);
        	toThrow = e;
        } finally {
        	// Finish transaction, if was started.
	        if (multiple && allowBulkInsertInTransaction){
	        	db.endTransaction();
	        }
        }
        
        // If there was some exception in the bulk insert, throw it.
        if (toThrow!=null){
        	throw new SQLException(toThrow.toString());
        }

        // If there was only single insert and was invalid, throw SQL exception by default
        if (!multiple && lastRowId < 0){
        	throw new SQLException("Failed to insert row into " + uri);
        }
        
        // If the insert succeeded, the row ID exists.
    	// TODO : for inserted account register it here
        if (matched == CLIST_ID || matched == CLIST){
        	for(long rowId : rowIds){
        		if (rowId >= 0){	// Notify only inserted values. 
                    notifyChange(ContentUris.withAppendedId(SipClist.CLIST_STATE_ID_URI_BASE, rowId), SipClist.CLIST_STATE_ID_URI_BASE);
        		}
        	}
        }
        
        // New message was inserted -> notify.
        if (successNum > 0 && (matched == MESSAGES || matched== MESSAGES_ID)){
        	// TODO notify change only for the specific user who received a message
            notifyChange(SipClist.CLIST_STATE_URI);
            // For message expiration
            notifyChange(SipMessage.MESSAGE_URI_INSERTED);
        }
        
        // Keeps call-log short.
        // TODO: custom impl.
        if(successNum > 0 && (matched == CALLLOGS || matched == CALLLOGS_ID)) {
            CallLog.pruneRecords(db);
        }
        
        // Reset filter cache if there was some successful modification.
        if (successNum > 0 && (matched == FILTERS || matched == FILTERS_ID)) {
            CallFirewall.resetCache();
        }
    	
        // Account notification.
        Uri lastUri = null;
        for(long rowId : rowIds){
        	if (rowId < 0) {
        		continue;
        	}
        	
	        Uri retUri = ContentUris.withAppendedId(baseInsertedUri, rowId);
            notifyChange(retUri, baseInsertedUri);
	        lastUri = retUri;
	        
	        if(matched == ACCOUNTS || matched == ACCOUNTS_ID) {
	        	broadcastAccountChange(rowId);
	        }
	        
	        if(matched == ACCOUNTS_STATUS || matched == ACCOUNTS_STATUS_ID) {
	            broadcastRegistrationChange(rowId);
	        }
        }

        return new InsertReturn(lastUri, successNum);
	}

	/**
	 * query() method 
	 * Inherited from ContentProvider
	 */
	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        // Constructs a new query builder and sets its table name
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        String finalSortOrder = sortOrder;
        String[] finalSelectionArgs = selectionArgs;
        String finalGrouping = null;
        String finalHaving = null;
        int type = URI_MATCHER.match(uri);
        
        Uri regUri = uri;
        
        int remoteUid = Binder.getCallingUid();
        int selfUid = android.os.Process.myUid();
        if(remoteUid != selfUid) {
	        if (type == ACCOUNTS || type == ACCOUNTS_ID) {
				for(String proj : projection) {
	        		if(proj.toLowerCase().contains(SipProfile.FIELD_PASSWORD) || proj.toLowerCase().contains("*")) {
	        			throw new SecurityException("Password not readable from external apps");
	        		}
	        	}
			}

	        checkPermissions(METHOD_QUERY, uri);
        }

        Cursor c;
        long id;

        // Only virtual queries
        switch (type) {
            case ACCOUNTS_STATUS:
                synchronized (profilesStatus) {
                    ContentValues[] cvs = new ContentValues[profilesStatus.size()];
                    int i = 0;
                    for(ContentValues  ps : profilesStatus.values()) {
                        cvs[i] = ps;
                        i++;
                    }
                    c = getCursor(cvs);
                }
                if(c != null) {
                    c.setNotificationUri(getContext().getContentResolver(), uri);
                }
                return c;
            case ACCOUNTS_STATUS_ID:
                id = ContentUris.parseId(uri);
                synchronized (profilesStatus) {
                    ContentValues cv = profilesStatus.get(id);
                    if(cv == null) {
                        return null;
                    }
                    c = getCursor(new ContentValues[] {cv});
                }
                c.setNotificationUri(getContext().getContentResolver(), uri);
                return c;
        }


        // Open Database now
        if (mOpenHelper == null){
            Log.wf(THIS_FILE, "DBHelper null yet");
            return null;
        }
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        if (db==null){
            Log.d(THIS_FILE, "Database returned in query() is null");
            switch(this.dbNotConnectedMode){
                case DB_CONNECTION_MODE_EXCEPTION:
                    throw new DatabaseNotConnectedException();
                case DB_CONNECTION_MODE_RETURN_NULL:
                    return null;
                default:
                    throw new SQLException("Undefined mode of operation");
            }
        }

        // Below are real DB queries
        switch (type) {
            case ACCOUNTS:
                qb.setTables(SipProfile.ACCOUNTS_TABLE_NAME);
                if(sortOrder == null) {
                	finalSortOrder = SipProfile.FIELD_PRIORITY + " ASC";
                }
                break;
            case ACCOUNTS_ID:
                qb.setTables(SipProfile.ACCOUNTS_TABLE_NAME);
                qb.appendWhere(SipProfile.FIELD_ID + "=?");
                finalSelectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs, new String[] { uri.getLastPathSegment() });
                break;
            // TODO: custom impl.
            case CALLLOGS:
                qb.setTables(CallLogScheme.TABLE);
                if(sortOrder == null) {
                	finalSortOrder = CallLogScheme.FIELD_DATE + " DESC";
                }
                break;
            case CALLLOGS_ID:
                qb.setTables(CallLogScheme.TABLE);
                qb.appendWhere(CallLogScheme.FIELD_ID + "=?");
                finalSelectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs, new String[] { uri.getLastPathSegment() });
                break;
            case FILTERS:
                qb.setTables(CallFirewallScheme.TABLE);
                if(sortOrder == null) {
                	finalSortOrder = CallFirewallScheme.DEFAULT_ORDER;
                }
                break;
            case FILTERS_ID:
                qb.setTables(CallFirewallScheme.TABLE);
                qb.appendWhere(CallFirewallScheme.FIELD_ID + "=?");
                finalSelectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs, new String[] { uri.getLastPathSegment() });
                break;
            case MESSAGES:
                qb.setTables(SipMessage.TABLE_NAME);
                if(sortOrder == null) {
                    finalSortOrder = SipMessage.FIELD_DATE + " DESC";
                }
                break;
            case MESSAGES_ID:
                qb.setTables(SipMessage.TABLE_NAME);
                qb.appendWhere(SipMessage.FIELD_ID + "=?");
                finalSelectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs, new String[] { uri.getLastPathSegment() });
                break;
            case CLIST: 
            case CLIST_STATE:
                //qb.setTables(SipClist.SIP_CONTACTLIST_TABLE);
            	
            	// We want to count the number of unread messages
            	qb.setTables(SipClist.SIP_CONTACTLIST_TABLE  + " LEFT JOIN " + SipMessage.TABLE_NAME +  " ON "
            	+ SipClist.FIELD_SIP + " = " + SipMessage.FIELD_FROM
            	+ " AND " + SipMessage.FIELD_READ + " = 0");
                if(sortOrder == null) {
                    finalSortOrder = SipClist.FIELD_SIP + " ASC";
                }                                                
                
                String[] newProj = new String[]{
                		"COUNT(" + SipMessage.FIELD_FROM+ ") AS "+SipClist.FIELD_UNREAD_MESSAGES,                        
                    };
                
                // merge projections
                if (projection!=null){
                	Set<String> tmpProjection = new HashSet<String>(projection.length + newProj.length);
                	for(String s:newProj) tmpProjection.add(s);
                	for(String s:projection) tmpProjection.add(s);
                	projection = tmpProjection.toArray(projection);
                } else {
                	projection = newProj;
                }
                //qb.appendWhere(SipMessage.FIELD_READ + "=0"); // count unread                
                finalGrouping = SipClist.FIELD_SIP;
                
                break;
            case CLIST_ID:
            case CLIST_STATE_ID:
                qb.setTables(SipClist.SIP_CONTACTLIST_TABLE);   
                
                qb.appendWhere(SipClist.FIELD_ID + "=?");
                finalSelectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs, new String[] { uri.getLastPathSegment() });
                break;
            case CERT:
                qb.setTables(UserCertificate.TABLE);
                if(sortOrder == null) {
                    finalSortOrder = UserCertificate.FIELD_OWNER + " ASC, " + UserCertificate.FIELD_DATE_CREATED + " DESC ";
                }
                break;
            case CERT_ID:
                qb.setTables(UserCertificate.TABLE);
                qb.appendWhere(UserCertificate.FIELD_ID + "=?");
                finalSelectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs, new String[] { uri.getLastPathSegment() });
                break;
            case SIGNATURE_WARNING:
                qb.setTables(SipSignatureWarning.TABLE);
                if(sortOrder == null) {
                    finalSortOrder = SipSignatureWarning.FIELD_DATE_LAST + " DESC, " + SipSignatureWarning.FIELD_DATE_CREATED + " DESC ";
                }
                break;
            case SIGNATURE_WARNING_ID:
                qb.setTables(SipSignatureWarning.TABLE);
                qb.appendWhere(SipSignatureWarning.FIELD_ID + "=?");
                finalSelectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs, new String[] { uri.getLastPathSegment() });
                break;
            case DH_OFFLINE:
                qb.setTables(DHOffline.TABLE);
                if(sortOrder == null) {
                    finalSortOrder = DHOffline.FIELD_DATE_CREATED + " DESC ";
                }
                break;
            case DH_OFFLINE_ID:
                qb.setTables(DHOffline.TABLE);
                qb.appendWhere(DHOffline.FIELD_ID + "=?");
                finalSelectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs, new String[] { uri.getLastPathSegment() });
                break;
            case RECEIVED_FILES:
                qb.setTables(ReceivedFile.TABLE_NAME);
                if(sortOrder == null) {
                    finalSortOrder = ReceivedFile.FIELD_DATE_RECEIVED + " DESC ";
                }
                break;
            case RECEIVED_FILES_ID:
                qb.setTables(ReceivedFile.TABLE_NAME);
                qb.appendWhere(ReceivedFile.FIELD_ID + "=?");
                finalSelectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs, new String[] { uri.getLastPathSegment() });
                break;
            case FILE_TRANSFER:
                qb.setTables(FileTransfer.TABLE);
                if(sortOrder == null) {
                    finalSortOrder = FileTransfer.FIELD_DATE_CREATED + " DESC ";
                }
                break;
            case FILE_TRANSFER_ID:
                qb.setTables(FileTransfer.TABLE);
                qb.appendWhere(FileTransfer.FIELD_ID + "=?");
                finalSelectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs, new String[] { uri.getLastPathSegment() });
                break;
            case FILE_STORAGE:
                qb.setTables(FileStorage.TABLE);
                if(sortOrder == null) {
                    finalSortOrder = FileStorage.FIELD_FS_NAME + " ASC ";
                }
                break;
            case FILE_STORAGE_ID:
                qb.setTables(FileStorage.TABLE);
                qb.appendWhere(FileStorage.FIELD_ID + "=?");
                finalSelectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs, new String[] { uri.getLastPathSegment() });
                break;
            case THUMBNAIL:
                qb.setTables(Thumbnail.TABLE);
                if(sortOrder == null) {
                    finalSortOrder = Thumbnail.FIELD_URI + " ASC ";
                }
                break;
            case THUMBNAIL_ID:
                qb.setTables(Thumbnail.TABLE);
                qb.appendWhere(Thumbnail.FIELD_ID + "=?");
                finalSelectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs, new String[] { uri.getLastPathSegment() });
                break;
            case PAIRING_REQUEST:
                qb.setTables(PairingRequest.TABLE);
                if(sortOrder == null) {
                    finalSortOrder = PairingRequest.FIELD_ID + " ASC ";
                }
                break;
            case PAIRING_REQUEST_ID:
                qb.setTables(PairingRequest.TABLE);
                qb.appendWhere(PairingRequest.FIELD_ID + "=?");
                finalSelectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs, new String[] { uri.getLastPathSegment() });
                break;
            case ACCOUNTING_LOG:
                qb.setTables(AccountingLog.TABLE);
                if(sortOrder == null) {
                    finalSortOrder = AccountingLog.FIELD_ID + " ASC ";
                }
                break;
            case ACCOUNTING_LOG_ID:
                qb.setTables(AccountingLog.TABLE);
                qb.appendWhere(AccountingLog.FIELD_ID + "=?");
                finalSelectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs, new String[] { uri.getLastPathSegment() });
                break;
            case ACCOUNTING_PERMISSION:
                qb.setTables(AccountingPermission.TABLE);
                if(sortOrder == null) {
                    finalSortOrder = AccountingPermission.FIELD_ID + " ASC ";
                }
                break;
            case ACCOUNTING_PERMISSION_ID:
                qb.setTables(AccountingPermission.TABLE);
                qb.appendWhere(AccountingPermission.FIELD_ID + "=?");
                finalSelectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs, new String[] { uri.getLastPathSegment() });
                break;
            case QUEUED_MESSAGE:
                qb.setTables(QueuedMessage.TABLE_NAME);
                if(sortOrder == null) {
                    // we want to retrieve the oldest unprocessed messages first
                    finalSortOrder = QueuedMessage.FIELD_TIME + " DESC ";
                }
                break;
            case QUEUED_MESSAGE_ID:
                qb.setTables(QueuedMessage.TABLE_NAME);
                qb.appendWhere(QueuedMessage.FIELD_ID + "=?");
                finalSelectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs, new String[] { uri.getLastPathSegment() });
                break;
            case TRIAL_EVENT_LOG:
                qb.setTables(TrialEventLog.TABLE);
                if(sortOrder == null) {
                    finalSortOrder = TrialEventLog.FIELD_DATE + " DESC ";
                }
                break;
            case TRIAL_EVENT_LOG_ID:
                qb.setTables(TrialEventLog.TABLE);
                qb.appendWhere(TrialEventLog.FIELD_ID + "=?");
                finalSelectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs, new String[] { uri.getLastPathSegment() });
                break;

            case QUEUED_MESSAGE_NEWEST_PER_RECIPIENT:
            {
                // Special uri: Greatest-per-group problem
                // Solution explained: http://stackoverflow.com/questions/979034/mysql-shows-incorrect-rows-when-using-group-by/979079#979079

                // required columns for joining and ordering
                List<String> columns = Arrays.asList(projection);
                if (!columns.contains(QueuedMessage.FIELD_TO))
                    columns.add(QueuedMessage.FIELD_TO);
                if (!columns.contains(QueuedMessage.FIELD_TIME))
                    columns.add(QueuedMessage.FIELD_TIME);

                String groupCol = QueuedMessage.FIELD_TO;
                String orderCol = QueuedMessage.FIELD_TIME;

                String finalProjection = Joiner.on(",").join(columns);
                String innerSelect = "SELECT " + finalProjection + " FROM " + QueuedMessage.TABLE_NAME + " WHERE " + selection;
                String outerQuery = "SELECT t1.* FROM (" + innerSelect + ") AS t1 LEFT OUTER JOIN (" + innerSelect + ") AS t2 ON " +
                        "(t1."+groupCol+"=t2."+groupCol+" AND t1."+orderCol+">t2."+orderCol+") WHERE t2."+groupCol+" IS NULL";

                Log.vf("db raw query [%s]; bindings [%s]", outerQuery, join(selectionArgs));
                c = db.rawQuery(outerQuery, selectionArgs);
                c.setNotificationUri(getContext().getContentResolver(), regUri);
                return c;
            }
            case THREADS:
            	//join with clist to get display name
//                qb.setTables(SipMessage.TABLE_NAME + " LEFT JOIN " + SipClist.SIP_CONTACTLIST_TABLE +  " ON " + SipMessage.FIELD_TO + " = " + SipClist.FIELD_SIP);
                qb.setTables(SipMessage.TABLE_NAME + " LEFT JOIN " + SipClist.SIP_CONTACTLIST_TABLE +  " ON thread_remote_sip = " + SipClist.FIELD_SIP);
                if(sortOrder == null) {
                    finalSortOrder = SipMessage.FIELD_DATE + " DESC";
                }
                projection = new String[]{
                    SipMessage.TABLE_NAME +".ROWID AS _id",
                    SipMessage.FIELD_FROM, 
                    SipMessage.FIELD_FROM_FULL, 
                    SipMessage.FIELD_TO, 
                    "CASE " + 
                          "WHEN " + SipMessage.FIELD_IS_OUTGOING + "=1 THEN "
                              + SipMessage.FIELD_TO + 
                          " WHEN " + SipMessage.FIELD_IS_OUTGOING + "!=1 THEN " 
                              + SipMessage.FIELD_FROM
                   + " END AS thread_remote_sip",                    

                    SipMessage.FIELD_BODY, 
                    SipMessage.FIELD_ERROR_CODE,
                    SipMessage.FIELD_ERROR_TEXT,                    
                    SipMessage.FIELD_DATE,
                    SipMessage.FIELD_READ,
                    "COUNT(" + SipMessage.FIELD_DATE + ") AS counter",
                    SipClist.FIELD_DISPLAY_NAME,
                    SipMessage.FIELD_BODY_DECRYPTED,
                    SipMessage.FIELD_BODY_HASH,
                    SipMessage.FIELD_IS_OUTGOING, 
                    SipMessage.FIELD_MIME_TYPE,
                    SipMessage.FIELD_DECRYPTION_STATUS,
                    SipMessage.FIELD_SIGNATURE_OK,
                    SipMessage.FIELD_TYPE
                };

                finalGrouping = "thread_remote_sip";
                finalHaving="MAX("+SipMessage.FIELD_DATE+")";
                regUri = SipMessage.MESSAGE_URI;
                break;
            case THREADS_ID:
                qb.setTables(SipMessage.TABLE_NAME);
                if(sortOrder == null) {
                    finalSortOrder = SipMessage.FIELD_DATE + " DESC";
                }
                
                String[] newProjection = new String[]{
                        "ROWID AS _id",
                        SipMessage.FIELD_FROM, 
                        SipMessage.FIELD_TO, 
                        SipMessage.FIELD_BODY,
                        SipMessage.FIELD_BODY_DECRYPTED,
                        SipMessage.FIELD_BODY_HASH,
                        SipMessage.FIELD_DATE, 
                        SipMessage.FIELD_IS_OUTGOING,
                        SipMessage.FIELD_MIME_TYPE,
                        SipMessage.FIELD_DECRYPTION_STATUS,
                        SipMessage.FIELD_SIGNATURE_OK,
                        SipMessage.FIELD_TYPE,
                        SipMessage.FIELD_STATUS,
                        SipMessage.FIELD_FROM_FULL,
                        SipMessage.FIELD_ERROR_CODE,
                        SipMessage.FIELD_ERROR_TEXT
                    };
                
                // merge projections
                if (projection!=null){
                	Set<String> tmpProjection = new HashSet<String>(projection.length + newProjection.length);
                	for(String s:newProjection) tmpProjection.add(s);
                	for(String s:projection) tmpProjection.add(s);
                	projection = tmpProjection.toArray(projection);
                } else {
                	projection = newProjection;
                }
                
                qb.appendWhere(MESSAGES_THREAD_SELECTION);
                String from = uri.getLastPathSegment();
                
                // ph4r05: bug fixed here, appendWhere goes first, so should parameters also
                if (selectionArgs == null)
                	finalSelectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs, new String[] { from, from });
                else
                	finalSelectionArgs = DatabaseUtils.appendSelectionArgs(new String[] { from, from }, selectionArgs);
                regUri = SipMessage.MESSAGE_URI;
                break;
                
                
            // For selecting specifing conversation between 2 subjects
            // Warning: left joined with received files, so we know how many messages are bind with particular message to correctly draw row in listview
            // USE: do prefix all table columns with SipMessage.TABLE_NAME
            case THREADS_ID_ID:
//            	Log.inf(THIS_FILE, "THREADS_ID_ID setTables [%s]", SipMessage.TABLE_NAME);
                qb.setTables(SipMessage.TABLE_NAME +
                        " LEFT JOIN " + ReceivedFile.TABLE_NAME +
                        " ON " + SipMessage.FIELD_ID + " = " + ReceivedFile.TABLE_NAME + "." + ReceivedFile.FIELD_MSG_ID);

                finalGrouping = SipMessage.TABLE_NAME + "." + SipMessage.FIELD_ID;

                if(sortOrder == null) {
                    finalSortOrder = SipMessage.FIELD_DATE + " DESC";
                }

                String col = ReceivedFile.TABLE_NAME + "." + ReceivedFile.FIELD_FILENAME;

                String[] proj = new String[]{
                        "COUNT(" + ReceivedFile.TABLE_NAME + "." + ReceivedFile.FIELD_ID  + ") as " + SipMessage.JOIN_FIELD_FILES_COUNT,
                        SipMessage.TABLE_NAME + ".ROWID AS _id",
//                        SipMessage.FIELD_DATE,
                        "SUM(CASE WHEN " + col + " LIKE '%.jpg' COLLATE utf8_general_ci OR "
                                + col + " LIKE '%.bmp' COLLATE utf8_general_ci OR "
                                + col + " LIKE '%.png' COLLATE utf8_general_ci OR "
                                + col + " LIKE '%.gif' COLLATE utf8_general_ci OR "
                                + col + " LIKE '%.jpeg' COLLATE utf8_general_ci "
                                + " THEN 1 ELSE 0 END) as " + SipMessage.JOIN_FIELD_IMAGES_COUNT,
                        };
                
                // merge projections
                if (projection!=null){
                	Set<String> tmpProjection = new HashSet<>(projection.length + proj.length);
                	for(String s:proj) tmpProjection.add(s);
                	for(String s:projection) tmpProjection.add(SipMessage.TABLE_NAME + "." + s + " as " + s);
                	projection = tmpProjection.toArray(projection);
                } else {
                	projection = proj;
                }
                
                qb.appendWhere(MESSAGES_THREAD_SELECTION_SUBJECTS);
                List<String> uriSeg =uri.getPathSegments();
                
                String firstSip =  uriSeg.get(uriSeg.size()-2);
                String secondSip = uriSeg.get(uriSeg.size()-1);
                
                // ph4r05: bug fixed here, appendWhere goes first, so should parameters also
                if (selectionArgs == null)
                	finalSelectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs, new String[] { firstSip, secondSip, firstSip,secondSip });
                else
                	finalSelectionArgs = DatabaseUtils.appendSelectionArgs(new String[] { firstSip, secondSip, firstSip,secondSip }, selectionArgs);
                regUri = SipMessage.MESSAGE_URI;
                break;
            case THREADS_UNREAD_COUNT:
                qb.setTables(SipMessage.TABLE_NAME);
                projection = new String[]{SipMessage.FIELD_FROM, "count (" + SipMessage.FIELD_FROM + ") as count"};
                selection = SipMessage.FIELD_READ + " = 0 AND " + SipMessage.FIELD_IS_OUTGOING + " = 0";
                finalGrouping = SipMessage.FIELD_FROM;
                regUri = null;
                break;
            default:
                throw new IllegalArgumentException(UNKNOWN_URI_LOG + uri);
        }
        
        Log.vf(THIS_FILE, "db.query; tbl [%s] projection [%s], selection [%s], finalSelectionArgs [%s],  finalGrouping [%s], finalHaving [%s], finalSortOrder [%s]",
                qb.getTables(), 
                join(projection), 
                selection, 
                join(finalSelectionArgs), 
                finalGrouping, 
                finalHaving, 
                finalSortOrder);
        
        c = qb.query(db, projection, selection, finalSelectionArgs,
                finalGrouping, finalHaving, finalSortOrder);

        if (regUri !=null){
            c.setNotificationUri(getContext().getContentResolver(), regUri);
        }
        return c;
	}
	
	/**
	 * update() method 
	 * Inherited from ContentProvider
	 */
	@Override
	public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
		checkPermissions(METHOD_UPDATE, uri);
        if (mOpenHelper == null){
            Log.wf(THIS_FILE, "DBHelper null yet");
            return 0;
        }
		
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		if (db==null){
        	Log.d(THIS_FILE, "Database returned in update() is null");
        	switch(this.dbNotConnectedMode){
        	case DB_CONNECTION_MODE_EXCEPTION:
        		throw new DatabaseNotConnectedException();
        	case DB_CONNECTION_MODE_RETURN_NULL:
        		return 0;
        	default:
        		throw new SQLException("Undefined mode of operation");
        	}
        }
		
        int count;
        String finalWhere;
        int matched = URI_MATCHER.match(uri);
        
        switch (matched) {
            case ACCOUNTS:
                count = db.update(SipProfile.ACCOUNTS_TABLE_NAME, values, where, whereArgs);
                break;
            case ACCOUNTS_ID:
                finalWhere = DatabaseUtils.concatenateWhere(SipProfile.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.update(SipProfile.ACCOUNTS_TABLE_NAME, values, finalWhere, whereArgs);
                break;
            case CALLLOGS:
                count = db.update(CallLogScheme.TABLE, values, where, whereArgs);
                break;
            case CALLLOGS_ID:
                finalWhere = DatabaseUtils.concatenateWhere(CallLogScheme.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.update(CallLogScheme.TABLE, values, finalWhere, whereArgs);
                break;
            case FILTERS:
                count = db.update(CallFirewallScheme.TABLE, values, where, whereArgs);
                break;
            case FILTERS_ID:
                finalWhere = DatabaseUtils.concatenateWhere(CallFirewallScheme.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.update(CallFirewallScheme.TABLE, values, finalWhere, whereArgs);
                break;
            case MESSAGES:
                count = db.update(SipMessage.TABLE_NAME, values, where, whereArgs);
                break;
            case MESSAGES_ID:
                finalWhere = DatabaseUtils.concatenateWhere(SipMessage.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.update(SipMessage.TABLE_NAME, values, finalWhere, whereArgs);
                break;
            case CLIST:
            case CLIST_STATE:
                count = db.update(SipClist.SIP_CONTACTLIST_TABLE, values, where, whereArgs);
                break;
            case CLIST_ID:
            case CLIST_STATE_ID:
                finalWhere = DatabaseUtils.concatenateWhere(SipClist.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.update(SipClist.SIP_CONTACTLIST_TABLE, values, finalWhere, whereArgs);
                Log.inf("DBProvider", "Updating Contactlistid; where: %s; values: %s", finalWhere, values.toString());
                break;
            case CERT:
                count = db.update(UserCertificate.TABLE, values, where, whereArgs);
                break;
            case CERT_ID:
                finalWhere = DatabaseUtils.concatenateWhere(UserCertificate.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.update(UserCertificate.TABLE, values, finalWhere, whereArgs);
                break;
            case SIGNATURE_WARNING:
                count = db.update(SipSignatureWarning.TABLE, values, where, whereArgs);
                break;
            case SIGNATURE_WARNING_ID:
                finalWhere = DatabaseUtils.concatenateWhere(SipSignatureWarning.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.update(SipSignatureWarning.TABLE, values, finalWhere, whereArgs);
                break;
            case DH_OFFLINE:
                count = db.update(DHOffline.TABLE, values, where, whereArgs);
                break;
            case DH_OFFLINE_ID:
                finalWhere = DatabaseUtils.concatenateWhere(DHOffline.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.update(DHOffline.TABLE, values, finalWhere, whereArgs);
                break;
            case RECEIVED_FILES:
                count = db.update(ReceivedFile.TABLE_NAME, values, where, whereArgs);
                break;
            case RECEIVED_FILES_ID:
                finalWhere = DatabaseUtils.concatenateWhere(ReceivedFile.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.update(ReceivedFile.TABLE_NAME, values, finalWhere, whereArgs);
                break;
            case FILE_TRANSFER:
                count = db.update(FileTransfer.TABLE, values, where, whereArgs);
                break;
            case FILE_TRANSFER_ID:
                finalWhere = DatabaseUtils.concatenateWhere(FileTransfer.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.update(FileTransfer.TABLE, values, finalWhere, whereArgs);
                break;
            case FILE_STORAGE:
                count = db.update(FileStorage.TABLE, values, where, whereArgs);
                break;
            case FILE_STORAGE_ID:
                finalWhere = DatabaseUtils.concatenateWhere(FileStorage.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.update(FileStorage.TABLE, values, finalWhere, whereArgs);
                break;
            case THUMBNAIL:
                count = db.update(Thumbnail.TABLE, values, where, whereArgs);
                break;
            case THUMBNAIL_ID:
                finalWhere = DatabaseUtils.concatenateWhere(Thumbnail.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.update(Thumbnail.TABLE, values, finalWhere, whereArgs);
                break;
            case PAIRING_REQUEST:
                count = db.update(PairingRequest.TABLE, values, where, whereArgs);
                break;
            case PAIRING_REQUEST_ID:
                finalWhere = DatabaseUtils.concatenateWhere(PairingRequest.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.update(PairingRequest.TABLE, values, finalWhere, whereArgs);
                break;
            case QUEUED_MESSAGE:
                count = db.update(QueuedMessage.TABLE_NAME, values, where, whereArgs);
                break;
            case QUEUED_MESSAGE_ID:
                finalWhere = DatabaseUtils.concatenateWhere(QueuedMessage.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.update(QueuedMessage.TABLE_NAME, values, finalWhere, whereArgs);
                break;
            case TRIAL_EVENT_LOG:
                count = db.update(TrialEventLog.TABLE, values, where, whereArgs);
                break;
            case TRIAL_EVENT_LOG_ID:
                finalWhere = DatabaseUtils.concatenateWhere(TrialEventLog.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.update(TrialEventLog.TABLE, values, finalWhere, whereArgs);
                break;
            case ACCOUNTING_LOG:
                count = db.update(AccountingLog.TABLE, values, where, whereArgs);
                break;
            case ACCOUNTING_LOG_ID:
                finalWhere = DatabaseUtils.concatenateWhere(AccountingLog.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.update(AccountingLog.TABLE, values, finalWhere, whereArgs);
                break;
            case ACCOUNTING_PERMISSION:
                count = db.update(AccountingPermission.TABLE, values, where, whereArgs);
                break;
            case ACCOUNTING_PERMISSION_ID:
                finalWhere = DatabaseUtils.concatenateWhere(AccountingPermission.FIELD_ID + " = " + ContentUris.parseId(uri), where);
                count = db.update(AccountingPermission.TABLE, values, finalWhere, whereArgs);
                break;

            case ACCOUNTS_STATUS_ID:
    			long id = ContentUris.parseId(uri);
    			synchronized (profilesStatus){
    				SipProfileState ps = new SipProfileState();
    				if(profilesStatus.containsKey(id)) {
    					ContentValues currentValues = profilesStatus.get(id);
    					ps.createFromContentValue(currentValues);
    				}
    				ps.createFromContentValue(values);
    				ContentValues cv = ps.getAsContentValue();
    				cv.put(SipProfileState.ACCOUNT_ID, id);
    				profilesStatus.put(id, cv);
    				Log.df(THIS_FILE, "Updated %s", cv);
    			}
    			count = 1;
    			break;
            default:
                throw new IllegalArgumentException(UNKNOWN_URI_LOG + uri);
        }

        notifyChange(uri);
        if (matched == CLIST) notifyChange(SipClist.CLIST_STATE_URI);
        if (matched == CLIST_ID) notifyChange(SipClist.CLIST_STATE_ID_URI_BASE);
        
        // a read status of a message may be updated
        if (matched == MESSAGES || matched== MESSAGES_ID){
        	// TODO notify change only for the specific user who received a message
        	notifyChange(SipClist.CLIST_STATE_URI);
        }
        
        long rowId = -1;
        if (matched == ACCOUNTS_ID || matched == ACCOUNTS_STATUS_ID) {
            rowId = ContentUris.parseId(uri);
        }
        if (rowId >= 0) {
            if (matched == ACCOUNTS_ID) {
                // Don't broadcast if we only changed accountManager or only changed priority
                boolean doBroadcast = true;
                if(values.size() == 1) {
                    if(values.containsKey(SipProfile.FIELD_ACCOUNT_MANAGER)) {
                        doBroadcast = false;
                    }else if(values.containsKey(SipProfile.FIELD_PRIORITY)) {
                        doBroadcast = false;
                    }
                }
                if(doBroadcast) {
                    broadcastAccountChange(rowId);
                }
            } else if (matched == ACCOUNTS_STATUS_ID) {
                broadcastRegistrationChange(rowId);
            }
        }
        if (matched == FILTERS || matched == FILTERS_ID) {
            CallFirewall.resetCache();
        }
	
        return count;
	}

    /**
     * Applying batch operations in one transaction.
     * @param operations
     * @return
     * @throws OperationApplicationException
     */
    @Override
    public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations) throws OperationApplicationException {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        db.beginTransaction();

        applyingBatch.set(true);
        try {
            final int numOperations = operations.size();
            final ContentProviderResult[] results = new ContentProviderResult[numOperations];
            for (int i = 0; i < numOperations; i++) {
                results[i] = operations.get(i).apply(this, results, i);
            }
            db.setTransactionSuccessful();
            return results;
        } finally {
            db.endTransaction();
            applyingBatch.set(false);
            flushNotifications(DBNotifications.NotificationsPruningStrategy.PRUNE_TOTAL_BASEURI);
        }
    }

    /**
     * Broadcasts delayed notifications.
     * @param pruningStrategy
     */
    protected void flushNotifications(DBNotifications.NotificationsPruningStrategy pruningStrategy){
        if (delayer != null){
            delayer.flushNotifications(pruningStrategy);
        }
    }

    /**
     * Internal helper for change notification.
     * Takes batch apply into consideration.
     *
     * @param uri
     */
    protected void notifyChange(Uri uri){
        notifyChange(uri, null);
    }

    /**
     * Internal helper for change notification.
     * Takes batch apply into consideration.
     *
     * @param uri
     * @param baseUri
     */
    protected void notifyChange(Uri uri, Uri baseUri){
        if (applyingBatch.get() && delayer != null){
            delayer.addNotification(uri, baseUri);
            return;
        }

        notifyChangeRaw(uri);
    }

    /**
     * Raw method for posting a DB notification. Used by DBNotifications.
     * @param uri
     */
    protected void notifyChangeRaw(Uri uri){
        getContext().getContentResolver().notifyChange(uri, null);
    }

    /**
	 * Checks if a requested access is permitted, if not SecurityException is thrown.
	 * Current implementation is strict, only the same user ID is allowed.
	 * @param method
	 * @param uri
	 */
	protected void checkPermissions(int method, Uri uri){
		// Obtains running process ID and calling process user ID
		int remoteUid = Binder.getCallingUid();
        int selfUid = android.os.Process.myUid();
        if(remoteUid != selfUid) {
        	final Exception e = new SecurityException("No permissions for this call, can be accessed only by local application.");
        	
        	Log.e(THIS_FILE, "checkPermissions detected breach.", e);
        	
        	// Report to the server
            MiscUtils.reportExceptionToAcra(e);
        }
	}
	
	/**
	 * Helper method, joins array of strings with a constant separator ", ".
	 * Used in building parts of the query.
	 * 
	 * @param arr
	 * @return
	 */
	public static String join(String[] arr){
		if (arr==null) return "";
		StringBuilder builder = new StringBuilder();		
		for(String s : arr){
			builder.append(s+", ");	
		}
		return builder.toString();		
	}

	/**
	 * Build a {@link Cursor} with a single row that contains all values
	 * provided through the given {@link ContentValues}.
	 */
	private Cursor getCursor(ContentValues[] contentValues) {
		if(contentValues.length > 0) {
	        final Set<Entry<String, Object>> valueSet = contentValues[0].valueSet();
	        int colSize = valueSet.size();
	        final String[] keys = new String[colSize];
	
	        int i = 0;
	        for (Entry<String, Object> entry : valueSet) {
	            keys[i] = entry.getKey();
	            i++;
	        }
	
	        final MatrixCursor cursor = new MatrixCursor(keys);
	        for (ContentValues cv : contentValues) {
		        final Object[] values = new Object[colSize];
		        i = 0;
		        for (Entry<String, Object> entry : cv.valueSet()) {
		            values[i] = entry.getValue();
		            i++;
		        }
	            cursor.addRow(values);
	        }
	        return cursor;
		}
		return null;
    }
	
	/**
	 * Broadcast the fact that account config has changed
	 * @param accountId
	 */
	private void broadcastAccountChange(long accountId) {
		Intent publishIntent = new Intent(Intents.ACTION_SIP_ACCOUNT_CHANGED);
		publishIntent.putExtra(SipProfile.FIELD_ID, accountId);
        MiscUtils.sendBroadcast(getContext(), publishIntent);
	}
	
	/**
	 * Broadcast the fact that registration / adding status changed
	 * @param accountId the id of the account
	 */
	private void broadcastRegistrationChange(long accountId) {
        Intent publishIntent = new Intent(Intents.ACTION_SIP_REGISTRATION_CHANGED);
        publishIntent.putExtra(SipProfile.FIELD_ID, accountId);
        MiscUtils.sendBroadcast(getContext(), publishIntent);
	}

	/**
	 * Helper method to set encryption key to the content provider.
     * @param cr
     * @param key
     * @param userSip sip is required to name a database, multiple users can have multiple databases
	 */
	public static void setEncKey(ContentResolver cr, String key, String userSip){
		if (cr == null) throw new IllegalArgumentException("ContentResolver is null");
		
		// Reflection here in order to avoid Dalvik VFY errors.
		try {
			Method call = ContentResolver.class.getMethod("call",  CONTENT_RESOLVER_CALL_PARAM_TYPES);

            Bundle b = new Bundle();
            b.putString(SET_KEY_METHOD_EXTRA_SIP, userSip);
            b.putString(SET_KEY_METHOD_EXTRA_KEY, key);

			call.invoke(cr, SipProfile.ACCOUNT_URI, SET_KEY_METHOD, null, b);
		} catch(Exception e){
			Log.e(THIS_FILE, "Problem with reflective call on call()", e);
		}
	}
	
	/**
	 * Helper method to set DB error mode if DB is not connected.
	 * @param cr
	 * @param dbNotConnectedMode
	 */
	public static void setDbNotConnectedMode(ContentResolver cr, int dbNotConnectedMode) {
		if (cr == null) throw new IllegalArgumentException("ContentResolver is null");
        // Reflection here in order to avoid Dalvik VFY errors.
        try {
            Method call = ContentResolver.class.getMethod("call",  CONTENT_RESOLVER_CALL_PARAM_TYPES);
            call.invoke(cr, SipProfile.ACCOUNT_URI, SET_DB_ERR_MODE_METHOD, String.valueOf(dbNotConnectedMode), null);
        } catch(Exception e){
            Log.e(THIS_FILE, "Problem with reflective call on call()", e);
        }
	}


    /**
     * Tries to close a database. Call this only during the login
     * @param cr
     * @return
     */
    public static void closeDb(ContentResolver cr){
        if (cr == null) throw new IllegalArgumentException("ContentResolver is null");
        // Reflection here in order to avoid Dalvik VFY errors.
        try {
            Method call = ContentResolver.class.getMethod("call",  CONTENT_RESOLVER_CALL_PARAM_TYPES);
            call.invoke(cr, SipProfile.ACCOUNT_URI, CLOSE_DB_METHOD, null, null);
        } catch(Exception e){
            Log.e(THIS_FILE, "Problem with reflective call on call()", e);
        }
    }

	/**
	 * Helper method to test given encryption key works.
	 * @param cr
	 * @param forceReinit
	 */
	public static int testEncKey(ContentResolver cr, boolean forceReinit) {
		if (cr == null) throw new IllegalArgumentException("ContentResolver is null");
        try {
            Method call = ContentResolver.class.getMethod("call",  CONTENT_RESOLVER_CALL_PARAM_TYPES);
            Object obj = call.invoke(cr, SipProfile.ACCOUNT_URI, TEST_KEY_METHOD, Boolean.toString(forceReinit), null);
            if (!(obj instanceof Bundle)){
                throw new IllegalArgumentException("API returned bad object on reflective call()");
            }

            final Bundle b = (Bundle) obj;
            if (b == null || !b.containsKey(TEST_KEY_RETURN_KEY)){
                throw new IllegalArgumentException("Something went wrong, invalid response in TEST_KEY_METHOD");
            }

            return b.getInt(TEST_KEY_RETURN_KEY);

        } catch(IllegalAccessException e) {
            Log.e(THIS_FILE, "Problem with reflective call on call()", e);
        } catch (NoSuchMethodException e){
            Log.e(THIS_FILE, "Problem with reflective call on call()", e);
        } catch (InvocationTargetException e) {
            Log.e(THIS_FILE, "Problem with reflective call on call()", e);
        }

        throw new IllegalArgumentException("Something went wrong, invalid response in TEST_KEY_METHOD");

	}
	
	/**
	 * Helper method to re-encrypt the database using new key (e.g. in case of password change)
	 * @param cr
	 * @param key
     * @param forceClose
	 */
	public static int setNewEncKey(ContentResolver cr, String key, boolean forceClose) {
		if (cr == null) throw new IllegalArgumentException("ContentResolver is null");

        // Reflection here in order to avoid Dalvik VFY errors.
        try {
            Bundle e = new Bundle();
            e.putBoolean(SET_NEW_KEY_EXTRA_CLOSE_DB, forceClose);

            Method call = ContentResolver.class.getMethod("call",  CONTENT_RESOLVER_CALL_PARAM_TYPES);
            Object obj = call.invoke(cr, SipProfile.ACCOUNT_URI, SET_NEW_KEY_METHOD, key, e);
            if (!(obj instanceof Bundle)){
                throw new IllegalArgumentException("API returned bad object on reflective call()");
            }

            final Bundle b = (Bundle) obj;
            if (b==null || !b.containsKey(TEST_KEY_RETURN_KEY)){
                throw new IllegalArgumentException("Something went wrong, invalid response in SET_NEW_KEY_METHOD");
            }

            return b.getInt(TEST_KEY_RETURN_KEY);

        } catch(NoSuchMethodException e){
            Log.e(THIS_FILE, "Problem with reflective call on call()", e);
        } catch (IllegalAccessException e) {
            Log.e(THIS_FILE, "Problem with reflective call on call()", e);
        } catch (InvocationTargetException e) {
            Log.e(THIS_FILE, "Problem with reflective call on call()", e);
        }

        throw new IllegalArgumentException("Something went wrong, invalid response in SET_NEW_KEY_METHOD");
	}

	
	/**
	 * Returns string placeholder for IN() clause for a given number of elements.
	 * 
	 * @param numberOfElements
	 * @return
	 */
	public static String getInPlaceholders(int numberOfElements){
		if (numberOfElements<=0)
			throw new IllegalArgumentException("numbe rof elements cannot be less than or equal to 0");
		if (numberOfElements==1)
			return "?";
		else
			return new String(new char[numberOfElements]).replace("\0", "?,") + "?";
	}
	
	/**
     * Special exception to report SQLCipher database is not connected.
     *  
     * @author ph4r05
     */
    public static class DatabaseNotConnectedException extends SQLException {
    	public static final long serialVersionUID=7L;
    	public static final int CODE_NONE=0;
    	private int code=0;

		public DatabaseNotConnectedException() {
			super();
		}
		
		public DatabaseNotConnectedException(String detailMessage, int code) {
			super(detailMessage);
			this.code = code;
		}

		public DatabaseNotConnectedException(String detailMessage) {
			super(detailMessage);
		}
		
		public int getCode() {
			return code;
		}
    }
}

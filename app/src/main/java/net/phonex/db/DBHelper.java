package net.phonex.db;

import android.content.Context;
import android.database.Cursor;
import android.os.Binder;
import android.text.TextUtils;

import net.phonex.db.entity.AccountingLog;
import net.phonex.db.entity.AccountingPermission;
import net.phonex.db.entity.FileStorage;
import net.phonex.db.entity.FileTransfer;
import net.phonex.db.entity.PairingRequest;
import net.phonex.db.entity.ReceivedFile;
import net.phonex.db.entity.SipClist;
import net.phonex.db.entity.SipProfile;
import net.phonex.db.entity.DHOffline;
import net.phonex.db.entity.QueuedMessage;
import net.phonex.db.entity.SipMessage;
import net.phonex.db.entity.SipSignatureWarning;
import net.phonex.db.entity.Thumbnail;
import net.phonex.db.entity.TrialEventLog;
import net.phonex.db.entity.UserCertificate;
import net.phonex.db.scheme.CallFirewallScheme;
import net.phonex.db.scheme.CallLogScheme;
import net.phonex.pref.PreferencesManager;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.StorageUtils;
import net.phonex.util.crypto.CertificatesAndKeys;
import net.phonex.util.crypto.CryptoHelper;
import net.phonex.util.crypto.MessageDigest;
import net.phonex.util.guava.Files;
import net.phonex.util.guava.Joiner;
import net.sqlcipher.DatabaseUtils;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteException;
import net.sqlcipher.database.SQLiteOpenHelper;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DBHelper {
	private final static String THIS_FILE = "DBHelper";

	/**
	 * A helper class to manage database creation and version management of SQLiteDatabase. {@see https://developer.android.com/reference/android/database/sqlite/SQLiteOpenHelper.html}
	 * 
	 * Instance is created in ContentProvider.onCreate() method.
	 */
	public static class DatabaseHelper extends SQLiteOpenHelper {
		private static final int DATABASE_VERSION_LOLD = 103;
		private static final int DATABASE_VERSION_PHNX = 46; // increment this if database definition has changed
		
		// Another database versioning here. 
		// Lower 18 bits is for official version number.
		// Another upper bits are for my version number.
		private static final int DATABASE_VERSION_PHONEX_SHIFT = 18;
		private static final int DATABASE_VERSION_OFFICIAL_MASK = 0x3FFFF;
		private static final int DATABASE_VERSION_PHONEX_MASK = 0xFFFC0000;
		
		// Here goes database version number.
		private static final int DATABASE_VERSION = (DATABASE_VERSION_PHNX << DATABASE_VERSION_PHONEX_SHIFT) | DATABASE_VERSION_LOLD;
		private              int writableReturn=0;	// counter of getWritableDatabase calls
		private              int readableReturn=0;  // counter of getReadableDatabase calls

		// Database filename (derived from user sip)
		private String dbFilename = null;
		// Encryption key for SQLCipher database.
		private String encKey = null;
		private static final String MAGIC_ENC_KEY = "PhoneX-SQLCipherDB";
		private static final int ENC_KEY_ITERATIONS = 4096;
		private static final int ENC_KEY_SIZE = 512;
		
		// checkKey() method return values
		public static int CHECK_RES_OK_KEY = 1;				// Opened successfully with given key.
	    public static int CHECK_RES_OK_CONVERTED = 2;		// Opened successfully after plaintext->encrypted conversion.
	    public static int CHECK_RES_OK_REINTIT = 3;			// Opened successfully after re-initialization.
	    public static int CHECK_RES_INV_KEY = -1;			// Invalid key (empty).
	    public static int CHECK_RES_OPEN_FAIL = -2;			// Failed to open with given key.
	    public static int CHECK_RES_NO_RW_RIGHTS = -3;		// No write rights to database when needed.
	    public static int CHECK_RES_ENC_DB_FAIL = -4;		// Encrypted database cannot be created.
	    public static int CHECK_RES_ENC_DB_OPEN_FAIL = -5;	// Cannot open created database.
	    public static int CHECK_RES_REINIT_FAIL = -6;		// Re-initialization failed.
	    public static int CHECK_RES_GENERAL_FAIL = -7;		// Re-initialization failed.
		public static int CHECK_RES_RENAME_FAILED = -8;

	    
	    // changeKey() method return values
	    private static final int REKEY_RES_OK = 1;
	    private static final int REKEY_RES_ERR = -1;
	    private static final int REKEY_RES_NO_RIGHTS = -3;
		private static final int REKEY_RES_NO_DB = -4;
		private static final int REKEY_RES_SECURITY_EXCEPTION = -5;
		
		// Opened cached writable database.
		private SQLiteDatabase mDatabase = null;
		private boolean mIsInitializing = false;
		private Context mContext = null;
		
		// Temporarily database opened while checking password.
		private SQLiteDatabase tmpDB = null;
		
		/**
		 * Simple constructor
		 * @param context
		 */
		DatabaseHelper(Context context, String sip) throws NoSuchAlgorithmException {
			super(context, CertificatesAndKeys.deriveDbFilename(sip), null, DATABASE_VERSION);
			this.mContext = context;
		}

		/**
		 * Called when database is created (is empty).
		 */
		@Override
		public void onCreate(SQLiteDatabase db) {
			Log.vf(THIS_FILE, "onCreate");
			db.execSQL(SipProfile.CREATE_TABLE);
			db.execSQL(CallLogScheme.CREATE_TABLE);
			db.execSQL(CallFirewallScheme.CREATE_TABLE);
			db.execSQL(SipMessage.CREATE_TABLE);
			db.execSQL(SipClist.CREATE_TABLE);
			db.execSQL(UserCertificate.CREATE_TABLE);
			db.execSQL(SipSignatureWarning.CREATE_TABLE);
			db.execSQL(DHOffline.CREATE_TABLE);
			db.execSQL(ReceivedFile.CREATE_TABLE);
            db.execSQL(QueuedMessage.CREATE_TABLE);
            db.execSQL(FileTransfer.CREATE_TABLE);
			db.execSQL(TrialEventLog.CREATE_TABLE);
            db.execSQL(FileStorage.CREATE_TABLE);
			db.execSQL(Thumbnail.CREATE_TABLE);
			db.execSQL(PairingRequest.CREATE_TABLE);
			db.execSQL(AccountingLog.CREATE_TABLE);
			db.execSQL(AccountingPermission.CREATE_TABLE);
			Log.v(THIS_FILE, "DB: onCreate() finished");
		}

		/**
		 * Called when opened database is older than expected version (passed in constructor).
		 */
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			Log.vf(THIS_FILE, "onUpgrade");
			int phonexNewVersion = (newVersion & DATABASE_VERSION_PHONEX_MASK) >> DATABASE_VERSION_PHONEX_SHIFT;
			int phonexOldVersion = (oldVersion & DATABASE_VERSION_PHONEX_MASK) >> DATABASE_VERSION_PHONEX_SHIFT;
			oldVersion = oldVersion & DATABASE_VERSION_OFFICIAL_MASK;
			newVersion = newVersion & DATABASE_VERSION_OFFICIAL_MASK;
			
			Log.df(THIS_FILE, "Upgrading database of: %d -> %d; phonex: %d -> %d",
                    oldVersion, newVersion, phonexOldVersion, phonexNewVersion);

            if(phonexOldVersion < 25){
            	try {
                    db.execSQL("DROP TABLE IF EXISTS " + SipProfile.ACCOUNTS_TABLE_NAME);
                    db.execSQL(String.format("DROP TABLE IF EXISTS %s", "calllogs"));
                    db.execSQL(String.format("DROP TABLE IF EXISTS %s", "outgoing_filters"));
                    db.execSQL("DROP TABLE IF EXISTS " + SipMessage.TABLE_NAME);
                    db.execSQL("DROP TABLE IF EXISTS " + SipClist.SIP_CONTACTLIST_TABLE);
                    db.execSQL("DROP TABLE IF EXISTS " + UserCertificate.TABLE);
                    db.execSQL("DROP TABLE IF EXISTS " + SipSignatureWarning.TABLE);
                    db.execSQL("DROP TABLE IF EXISTS " + DHOffline.TABLE);
                    db.execSQL("DROP TABLE IF EXISTS " + ReceivedFile.TABLE_NAME);
            	}catch(SQLiteException e) {
                    Log.e(THIS_FILE, "Upgrade fail... maybe a crappy rom...", e);
                }
            }

            if (phonexOldVersion < 27){
                db.execSQL("DROP TABLE IF EXISTS " + CallLogScheme.TABLE);
                db.execSQL("DROP TABLE IF EXISTS " + CallFirewallScheme.TABLE);
            }
            if (phonexOldVersion < 28){
                db.execSQL("DROP TABLE IF EXISTS " + CallLogScheme.TABLE);
            }
            if (phonexOldVersion < 30){
                db.execSQL("DROP TABLE IF EXISTS " + QueuedMessage.TABLE_NAME);
                // add 'resendTime' and remove 'sendCounter' columns in SipMessage
                try {
                    dropColumn(db, SipMessage.TABLE_NAME, SipMessage.CREATE_TABLE, new String[]{SipMessage.FIELD_SEND_COUNTER});
                } catch (SQLException e) {
                    Log.e(THIS_FILE, "Upgrade of SipMessage failed... maybe a crappy rom...", e);
                }
            }
            if (phonexOldVersion < 31){
                try {
                    addColumn(db, SipMessage.TABLE_NAME, SipMessage.FIELD_READ_DATE, "INTEGER DEFAULT 0");
                } catch (Exception e) {
                    Log.e(THIS_FILE, "Upgrade of SipMessage failed... maybe a crappy rom...", e);
                }
            }
            if (phonexOldVersion < 32){
                try {
                    addColumn(db, ReceivedFile.TABLE_NAME, ReceivedFile.FIELD_THUMBNAIL_NAME, "TEXT");
                    addColumn(db, ReceivedFile.TABLE_NAME, ReceivedFile.FIELD_FILE_HASH, "TEXT");
                    addColumn(db, ReceivedFile.TABLE_NAME, ReceivedFile.FIELD_FILE_ORDER, "INTEGER DEFAULT 0");
                } catch (Exception e) {
                    Log.e(THIS_FILE, "Upgrade of SipMessage failed... maybe a crappy rom...", e);
                }
            }

            if (phonexOldVersion < 33){
                try {
                    addColumn(db, ReceivedFile.TABLE_NAME, ReceivedFile.FIELD_MSG_ID, "INTEGER DEFAULT 0");
                    addColumn(db, ReceivedFile.TABLE_NAME, ReceivedFile.FIELD_TRANSFER_ID, "INTEGER DEFAULT 0");
                    addColumn(db, ReceivedFile.TABLE_NAME, ReceivedFile.FIELD_MIME_TYPE, "TEXT");
                    addColumn(db, ReceivedFile.TABLE_NAME, ReceivedFile.FIELD_TITLE, "TEXT");
                    addColumn(db, ReceivedFile.TABLE_NAME, ReceivedFile.FIELD_DESC, "TEXT");
                    addColumn(db, ReceivedFile.TABLE_NAME, ReceivedFile.FIELD_RECORD_TYPE, "INTEGER DEFAULT 0");
                } catch (Exception e) {
                    Log.e(THIS_FILE, "Upgrade of SipMessage failed... maybe a crappy rom...", e);
                }
            }

			if (phonexOldVersion < 34){
				try {
					addColumn(db, SipProfile.ACCOUNTS_TABLE_NAME, SipProfile.FIELD_LICENSE_TYPE, "TEXT");
					addColumn(db, SipProfile.ACCOUNTS_TABLE_NAME, SipProfile.FIELD_LICENSE_ISSUED_ON, "INTEGER DEFAULT 0");
					addColumn(db, SipProfile.ACCOUNTS_TABLE_NAME, SipProfile.FIELD_LICENSE_EXPIRES_ON, "INTEGER DEFAULT 0");
					addColumn(db, SipProfile.ACCOUNTS_TABLE_NAME, SipProfile.FIELD_LICENSE_EXPIRED, "INTEGER DEFAULT 0");
				} catch (Exception e) {
					Log.e(THIS_FILE, "Upgrade of SipProfile failed... maybe a crappy rom...", e);
				}
			}

			if (phonexOldVersion < 35){
				try {
					db.execSQL(TrialEventLog.CREATE_TABLE);
				} catch (Exception e) {
					Log.ef(THIS_FILE, e, "Migration failed, phonexOldVersion %d", phonexNewVersion);
				}
			}
			
            if (phonexOldVersion < 36){
                try {
					db.execSQL(FileStorage.CREATE_TABLE);
				} catch (Exception e) {
					Log.ef(THIS_FILE, e, "Migration failed, phonexOldVersion %d", phonexNewVersion);
				}
            }

			if (phonexOldVersion < 37){
				try {
					addColumn(db, ReceivedFile.TABLE_NAME, ReceivedFile.FIELD_STORAGE_URI, "TEXT");
				} catch (Exception e) {
					Log.ef(THIS_FILE, e, "Migration failed, phonexOldVersion %d", phonexNewVersion);
				}
			}

			if (phonexOldVersion < 38){
				try {
					db.execSQL(PairingRequest.CREATE_TABLE);
				} catch (Exception e) {
					Log.ef(THIS_FILE, e, "Migration failed, phonexOldVersion %d", phonexNewVersion);
				}
			}

			if (phonexOldVersion < 39){
				try {
					addColumn(db, SipProfile.ACCOUNTS_TABLE_NAME, SipProfile.FIELD_CURRENT_POLICY_TIMESTAMP, "INTEGER DEFAULT 0");
				} catch (Exception e) {
					Log.ef(THIS_FILE, e, "Migration failed, phonexOldVersion %d",  phonexOldVersion);
				}
			}

			if (phonexOldVersion < 40){
				try {
					addColumn(db, CallLogScheme.TABLE, CallLogScheme.FIELD_EVENT_TIMESTAMP, "NUMERIC DEFAULT 0");
					addColumn(db, CallLogScheme.TABLE, CallLogScheme.FIELD_EVENT_NONCE, "INTEGER DEFAULT 0");
					addColumn(db, CallLogScheme.TABLE, CallLogScheme.FIELD_SIP_CALL_ID, "TEXT");
				} catch (Exception e) {
					Log.ef(THIS_FILE, e, "Migration failed, phonexOldVersion %d",  phonexOldVersion);
				}
			}

			if (phonexOldVersion < 41){
				try {
					addColumn(db, QueuedMessage.TABLE_NAME, QueuedMessage.FIELD_MESSAGE_PROTOCOL_SUB_TYPE, "INTEGER DEFAULT 0");
				} catch (Exception e) {
					Log.ef(THIS_FILE, e, "Could not add message subtype fields to message queue, phonexOldVersion %d",  phonexOldVersion);
				}
			}

			if (phonexOldVersion < 42){
				try {
					addColumn(db, QueuedMessage.TABLE_NAME, QueuedMessage.FIELD_IS_OFFLINE, "INTEGER DEFAULT 0");
					addColumn(db, QueuedMessage.TABLE_NAME, QueuedMessage.FIELD_SEND_ATTEMPT_COUNTER, "INTEGER DEFAULT 0");
					addColumn(db, QueuedMessage.TABLE_NAME, QueuedMessage.FIELD_LAST_SEND_CALL, "INTEGER DEFAULT 0");
				} catch (Exception e) {
					Log.ef(THIS_FILE, e, "Could not add message subtype fields to message queue, phonexOldVersion %d",  phonexOldVersion);
				}
			}

			if (phonexOldVersion < 43){
				try {
					addColumn(db, SipProfile.ACCOUNTS_TABLE_NAME, SipProfile.FIELD_RECOVERY_EMAIL, "TEXT");
				} catch (Exception e) {
					Log.ef(THIS_FILE, e, "Could not add column to sipProfile, phonexOldVersion %d",  phonexOldVersion);
				}
			}

			if (phonexOldVersion < 46 || oldVersion < 103){
				tryAddIndex(db, AccountingPermission.TABLE, "idx_name",
						Collections.singletonList(AccountingPermission.FIELD_NAME));

				tryAddIndex(db, AccountingPermission.TABLE, "idx_local",
						Collections.singletonList(AccountingPermission.FIELD_LOCAL_VIEW));

				tryAddIndex(db, AccountingPermission.TABLE, "idx_local_name",
						Arrays.asList(AccountingPermission.FIELD_LOCAL_VIEW, AccountingPermission.FIELD_NAME));

				tryAddIndex(db, AccountingPermission.TABLE, "idx_date_from",
						Collections.singletonList(AccountingPermission.FIELD_VALID_FROM));

				tryAddIndex(db, SipClist.SIP_CONTACTLIST_TABLE, "idx_sip",
						Collections.singletonList(SipClist.FIELD_SIP));

				tryAddIndex(db, SipClist.SIP_CONTACTLIST_TABLE, "idx_acc",
						Collections.singletonList(SipClist.FIELD_ACCOUNT));

				tryAddIndex(db, UserCertificate.TABLE, "idx_sip",
						Collections.singletonList(UserCertificate.FIELD_OWNER));

				tryAddIndex(db, PairingRequest.TABLE, "idx_sip",
						Collections.singletonList(PairingRequest.FIELD_FROM_USER));

				tryAddIndex(db, PairingRequest.TABLE, "idx_res_seen",
						Arrays.asList(PairingRequest.FIELD_RESOLUTION, PairingRequest.FIELD_SEEN));

				Log.inf(THIS_FILE, "Indices added");
			}
            
			onCreate(db);
		}

		/**
		 * Callback called when database is opened.
		 * Currently just logs this event, performing no action. 
		 */
		@Override
		public void onOpen(SQLiteDatabase db) {
			Log.vf(THIS_FILE, "Database onOpen() called; this=%s; database=%s", this, db);
			super.onOpen(db);
		}

		/**
		 * Returns readable SQLiteDatabase.
		 * Used mainly in custom ContentProvider methods before operation. Create and/or open a database. 
		 * 
		 * Namely method in DBProvider:
		 * public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)
		 */
		@Override
		public synchronized SQLiteDatabase getReadableDatabase(String passwd) {
			Log.vf(THIS_FILE, "Returning readable database with pass; this=%s; callCount=%d", this, readableReturn);
			readableReturn += 1;
			return super.getReadableDatabase(passwd);
		}

		/**
		 * Returns writable SQLiteDatabase.
		 * Used mainly in custom ContentProvider methods before operation. Create and/or open a database. 
		 * 
		 * Namely methods in DBProvider:
		 * public int delete(Uri uri, String where, String[] whereArgs)
		 * public Uri insert(Uri uri, ContentValues initialValues)
		 * public int update(Uri uri, ContentValues values, String where, String[] whereArgs)
		 */
		@Override
		public synchronized SQLiteDatabase getWritableDatabase(String passwd) {
			Log.vf(THIS_FILE, "Returning writable database with pass; this=%s; callCount=%d", this, writableReturn);
			writableReturn+=1;
			return super.getWritableDatabase(passwd);
		}
		
		/**
		 * Password-free getWritableDatabase method. 
		 * Used mainly in custom ContentProvider. Correct operation assumes the encryption key 
		 * was set before. Returns locally cached writableDatabase. 
		 * 
		 * Locally cached database may be opened on READ_ONLY mode (getReadableDatabase), 
		 * then call tries to open it in READ_WRITE mode if possible. If not, cached DB stays READ_ONLY
		 * and exception is thrown.
		 * 
		 * @return
		 */
		public synchronized SQLiteDatabase getWritableDatabase() {
			Log.vf(THIS_FILE, "Returning writable database; this=%s; callCount=%d; no-password", this, writableReturn);
			writableReturn+=1;
			if (mDatabase != null && mDatabase.isOpen() && !mDatabase.isReadOnly()) {
	            return mDatabase;  // The database is already open for business
	        }
			
			// Writable database is needed here. Tries to re-open in READ_WRITE mode if possible. 
			// If not mDatabase instance is left in previous state, exception is thrown.
			//
			// CheckKey has to be called at first. 
			if (mDatabase==null || !mDatabase.isOpen()){
				Log.w(THIS_FILE, "getWritableDatabase is called prior checkKey() call. No database is opened currently, returning null.");
				return null;
			}
			
			if (this.encKey==null || this.encKey.length()==0){
				Log.w(THIS_FILE, "getWritableDatabase is called with no key set. Returning null.");
				return null;
			}
			
			boolean success  = false;
			boolean dbExists = false;
			try {
				// Verify if database existed prior first call to getWritableDatabase (will create a new if does not exist).
//				File dbFile = getDatabaseFile(Constants.AUTHORITY);
				File dbFile = getDatabaseFile(dbFilename);
				dbExists    = dbFile.exists();
				// Open database in READ_WRITE and test encryption key correctness.
				success     = this.openDbAndTestKey(this.encKey, false);
				
				return tmpDB;
			} finally {
				if (success){
					Log.vf(THIS_FILE, "Database opened in READ_WRITE mode; this=%s; db=%s", this, tmpDB);
					
					if (mDatabase != null) {
						try { mDatabase.close(); } catch (Exception e) { }
				        // mDatabase.unlock();
				    }
					
				    mDatabase = tmpDB;
				    tmpDB = null;
				} else {
					if (dbExists){
						Log.ef(THIS_FILE, "Database cannot be opened in READ_WRITE mode; bad key; this=%s; db=%s", this, tmpDB);
					} else {
						Log.ef(THIS_FILE, "Database cannot be opened in READ_WRITE mode; no rights; this=%s; db=%s", this, tmpDB);
					}
					closeTmpDB();
				}
			}
		}
		
		/**
		 * Password-free getReadableDatabase method. 
		 * Used mainly in custom ContentProvider. Correct operation assumes the encryption key 
		 * was set before. Returns cached writableDatabase. 
		 * 
		 * @return
		 */
		public synchronized SQLiteDatabase getReadableDatabase() {
			Log.vf(THIS_FILE, "Returning readable database; this=%s; callCount=%d; no-password", this, (readableReturn++));
			if (mDatabase != null && mDatabase.isOpen()) {
	            return mDatabase;  // The database is already open for business
	        }
			
			return null;
		}
		
		/**
		 * Sets encryption key to the encrypted database + user's sip
		 * Content provider calls this method upon receiving encrypted key in ContentProvider.call().
		 * 
		 * This call closes all opened databases.
		 * 
		 * Database key is generated from provided key as: 
		 * dbpass = SHA-512-hexaString(PBKDF2(key + "|" + MAGIC_ENC_KEY, salt, iterations, keysize))
		 * salt   = SHA-256(IMEI+"|"+androidUniqueId)
		 * iterations = 1024
		 * keysize = 512
		 * MAGIC_ENC_KEY = PhoneX-SQLCipherDB
		 * 
		 * key should be of the form: username + "|" + password
		 * 
		 * 
		 * @param key
		 * @throws UnsupportedEncodingException 
		 * @throws InvalidKeySpecException 
		 * @throws NoSuchAlgorithmException 
		 * @throws NoSuchProviderException 
		 */
		public synchronized void setEncKeyAndSip(String key, String sip) throws NoSuchAlgorithmException, InvalidKeySpecException, UnsupportedEncodingException, NoSuchProviderException{
			if (TextUtils.isEmpty(sip)){
				throw new IllegalArgumentException("Sip is null");
			}
			if (key==null) 
				throw new IllegalArgumentException("Encryption key is null");
			if (key.length()==0) 
				throw new IllegalArgumentException("Encryption key cannot be empty"); // Empty key would disable encryption
			setRawDatabaseKey(deriveDatabaseKey(key));
			dbFilename = CertificatesAndKeys.deriveDbFilename(sip);
		}

		/**
		 * Sets raw (derived) database encryption key. Closes locally cached database if opened.
		 * 
		 * @param rawKey
		 * @throws UnsupportedEncodingException 
		 * @throws InvalidKeySpecException 
		 * @throws NoSuchAlgorithmException 
		 */
		private void setRawDatabaseKey(String rawKey) throws NoSuchAlgorithmException, InvalidKeySpecException, UnsupportedEncodingException{
			if (rawKey==null) 
				throw new IllegalArgumentException("Encryption key is null");
			if (rawKey.length()==0) 
				throw new IllegalArgumentException("Encryption key cannot be empty"); // Empty key would disable encryption
			
			// Close opened database
			if (mDatabase!=null && mDatabase.isOpen()){
				try { 
					mDatabase.close();
					Log.d(THIS_FILE, "Closed opened database in setEncKeyAndSip()");
				} catch(Exception e){
					Log.e(THIS_FILE, "Problem with closing opened database in setEncKeyAndSip()", e);
				}
			}
			mDatabase = null;
			
			this.encKey = rawKey;
		}
		
		/**
		 * Derives database key from the provided source key.
		 * 
		 * Database key is generated from provided key as: 
		 * dbpass = SHA-512-hexaString(PBKDF2(key + "|" + MAGIC_ENC_KEY, salt,Db iterations, keysize))
		 * salt   = SHA-256(IMEI+"|"+serial+"|"+androidUniqueId)
		 * iterations = 4096
		 * keysize = 512
		 * MAGIC_ENC_KEY = PhoneX-SQLCipherDB
		 * 
		 * key should be of the form: username + "|" + password
		 * Use static method {@link DatabaseHelper#formatDbPassword} to obtain correct format of the key.
		 * 
		 * @param key
		 * @return
		 * @throws UnsupportedEncodingException 
		 * @throws InvalidKeySpecException 
		 * @throws NoSuchAlgorithmException 
		 * @throws NoSuchProviderException 
		 */
		private String deriveDatabaseKey(String key) throws NoSuchAlgorithmException, InvalidKeySpecException, UnsupportedEncodingException, NoSuchProviderException{
			// Special key derivation method is here:
			// PBKDF2(user_pass + magic_string, salt = deviceID)
			final String preKey = key + "|" + MAGIC_ENC_KEY;
			final String deviceId = CertificatesAndKeys.getDeviceId(mContext);
			
			Log.vf(THIS_FILE, "Generating DB encryption key; device salt=%s", deviceId);
			final String encKey = CryptoHelper.pbkdf2String(preKey, MessageDigest.hashSha256(deviceId), ENC_KEY_ITERATIONS, ENC_KEY_SIZE);
			return encKey;
		}
		
		/**
	     * Close any open database object.
	     */
	    public synchronized void close() {
	        if (mIsInitializing) throw new IllegalStateException("Closed during initialization");
	 
	        if (mDatabase != null && mDatabase.isOpen()) {
	        	mDatabase.close();
	        	mDatabase = null;
	        }
	    }
		
	    /**
	     * Sets new encryption key for the database - leads to re-keying of the whole database.
	     * @param key
	     * @return 
	     */
	    public synchronized int changeKey(String key){
	    	return changeKey(key, false);
	    }
	    
	    /**
	     * Sets new encryption key for the database - leads to re-keying of the whole database.
	     * @param key
         * @param forceClose
	     * @return 
	     */
	    public synchronized int changeKey(String key, boolean forceClose) {
			Log.vf(THIS_FILE, "changeKey");
	    	// Re-key the database
	    	boolean success = false;
	    	final String oldKey = this.encKey;					// Already derived


	    	File authDB = this.getDatabaseFile(dbFilename);
	        File authEncDB = this.getDatabaseFile(dbFilename + ".enc");
	        File authOldDB = this.getDatabaseFile(dbFilename + ".old");
	        
	        // Cleanup temporary files prior operation
	        if (authEncDB.exists())
	        	authEncDB.delete();
	        if (authOldDB.exists())
	        	authOldDB.delete();
	        
	    	try {
		    	final String newKey = this.deriveDatabaseKey(key);	// Already derived
		    	
		    	// Available only for internal applications
		    	int remoteUid = Binder.getCallingUid();
		        int selfUid = android.os.Process.myUid();
		        if(remoteUid != selfUid) {
		        	return REKEY_RES_SECURITY_EXCEPTION; //throw new SecurityException("Database re-keying operation is available only for the main application.");
		        }
		        
	    		// 1. At first check necessary conditions
	    		//   1.1. Database is writable
	    		//   1.2. Database can be opened in read write mode
	    		if (!authDB.exists()){
	    			Log.e(THIS_FILE, "Database does not exist, cannot re-key.");
	    			return REKEY_RES_NO_DB;
	    		} else if (!authDB.canWrite()){
	    			Log.e(THIS_FILE, "Database is not writable, cannot re-key.");
	    			return REKEY_RES_NO_RIGHTS;
	    		}
	    		
	    		// 1.5 Force close if wanted
	    		if (forceClose){
	    			Log.v(THIS_FILE, "re-key: Closing database before rekeying. ");
	    			this.close();
	    		}
	    		
	    		// 2. Copy current database to backup place, the whole process
	    		// will be made on the backup database.
	    		Files.copy(authDB, authOldDB);
	    		Files.copy(authDB, authEncDB);

	    		// 3. Open copied database, re-key, set database version
	    		SQLiteDatabase db = SQLiteDatabase.openDatabase(authEncDB.getAbsolutePath(), oldKey, null, SQLiteDatabase.OPEN_READWRITE);
	    		Log.v(THIS_FILE, "re-key: going to start transaction with re-keying.");
	    		db.beginTransaction();
                try {
    	    		final String sqlQueryRekey = String.format("PRAGMA rekey = %s ;", DatabaseUtils.sqlEscapeString(newKey));
    	    		db.rawExecSQL(sqlQueryRekey);
                    db.setVersion(DATABASE_VERSION);
                    db.setTransactionSuccessful();
                    Log.v(THIS_FILE, "re-key: transaction looks good.");
                } finally {
                    db.endTransaction();
                    db.close();
                }
                
                // If here, database is re-keyed at the moment, can switch to new one.
                // 4. Close existing database. 
                // Warning: if old database was changed between step 2 and 4, the new database will be out of sync...
                // It is up to the user to call close() before calling this action.
                this.close();
                // 5. Replace original database with database encrypted under new key.
                Files.copy(authEncDB, authDB);
                // 6. Check correctness of the operation - check key
                this.setRawDatabaseKey(newKey);
                int checked = checkKey(false);
                if (checked > 0){
                	success = true;
                	return REKEY_RES_OK;
                } else {
                	Log.e(THIS_FILE, "Unable to open re-keyed database. Rollback needed.");
                	return REKEY_RES_ERR;
                }
	    	} catch(Exception e){
	    		Log.e(THIS_FILE, "Exception in re-key procedure", e);
	    	} finally{
	    		if (!success){
	    			// Rollback operations,
	    			//   1. Recover database backup.
	    			//   2. Set old encryption key.
	    			//   3. Re-open previous database.
	    			//
	    			try{
	    				if (authOldDB.exists()){
	    					Files.copy(authOldDB, authDB);
	    				}
	    				
	    				this.setRawDatabaseKey(oldKey);
	    			} catch(Exception e){
	    				Log.e(THIS_FILE, "re-key: cannot recover database backup.");
	    			}
	    			
	    			checkKey(false);
	    		}
	    		
	    		if (authEncDB.exists())
		        	authEncDB.delete();
		        if (authOldDB.exists())
		        	authOldDB.delete();
	    	}
	    	
	    	return REKEY_RES_ERR;
	    }
	    
		/**
		 * Method checks whether given encryption key is valid. It actually opens the database connection.
		 * It could take some time to finish so it can not be called in main application thread.
		 * It can also perform conversions, updates and similar operations on database 
		 * (e.g., plaintext->encrypted DB conversion).
		 * 
		 * @return
		 */
		public synchronized int checkKey(boolean forceReinit){
			Log.vf(THIS_FILE, "checkKey; forceReinit=%s", forceReinit);
			if (this.encKey==null || this.encKey.length()==0){
				Log.w(THIS_FILE, "Trying to checkKey() with empty key.");
				return CHECK_RES_INV_KEY;
			}
			 
			if (mIsInitializing) {
				throw new IllegalStateException("getWritableDatabase called recursively");
		    }

			File authDB = getDatabaseFile(dbFilename);
			// Verify if database existed prior first call that creates database (will create a new if does not exist). 
			boolean dbExists = authDB.exists();
			
			// At first try to open database with provided password.
			// If database does not exist, it will be created in this step with no error (opened in READ_WRITE mode) 
			boolean keyValid = openDbAndTestKey(this.encKey, dbExists);
			if (keyValid && this.tmpDB!=null){
				this.mDatabase = this.tmpDB;
				this.tmpDB = null;
				
				Log.vf(THIS_FILE, "Database successfully opened in checkKey(); this=%s; db=%s", this, this.mDatabase);
				return CHECK_RES_OK_KEY;
			} else {
				// delete old secure storage files for all users
				File secureStorageRoot = PreferencesManager.getRootSecureStorageFolder(mContext);
				StorageUtils.deleteDirectoryContents(secureStorageRoot);
			}
			
			// If database does not exist and test is invalid -> no rights to write a database file.
			if (!dbExists){
				Log.wf(THIS_FILE, "checkKey(): database does not exist and cannot be created (no rights); this=%s; ", this);
				return CHECK_RES_NO_RW_RIGHTS;
			}
			
			// If here -> key is not valid for existing database.
			// It could be the case that database is in plain-text form.
			// Close previously opened database and try to open with empty key.
			Log.vf(THIS_FILE, "Database cannot be opened in checkKey(); exists=%s; this=%s; db=%s", dbExists, this, tmpDB);
			closeTmpDB();
			keyValid = this.openDbAndTestKey("", true);
			if (!keyValid || this.tmpDB==null){
				closeTmpDB();
				Log.vf(THIS_FILE, "Database empty password open fail in checkKey(); DB is encrypted with unknown password; forceReinit=%s; this=%s;", forceReinit, this);
				
				// Here we have some database file that is encrypted and cannot be opened with given key.
				if (forceReinit){
					// Here database will be erased and re-created with given key.
					int tmpRet = 0;
					
					Log.v(THIS_FILE, "Going to re-initialize database with a given key");
					if (authDB.exists() && !authDB.canWrite()){
						Log.e(THIS_FILE, "Database file is not writable, cannot reinitialize.");
						return CHECK_RES_NO_RW_RIGHTS;
					}
					
					try {
						authDB.delete();
						tmpRet = checkKey(false);
						return tmpRet==1 ? CHECK_RES_OK_REINTIT : tmpRet;
					} catch(Exception e){
						Log.e(THIS_FILE, "Exception in opening re-generated file");
					}
				}
				
				return CHECK_RES_OPEN_FAIL;
			}
			
			return CHECK_RES_GENERAL_FAIL;
		}

		public boolean existsDatabase(String dbName){
            return getDatabaseFile(dbName).exists();
		}
		
		public static File getDatabaseFile(Context context, String dbName){
            String path = context.getDatabasePath(dbName).getPath();
            return new File(path);
        }

		private File getDatabaseFile(String dbName){
			return getDatabaseFile(mContext, dbName);
		}
		
		/**0000000000000000000000000000000000000000
		 * Tests by a reading query whether given database can be read (enc key is correct).
		 * @param db
		 * @return
		 */
		public boolean testKeyOnDatabase(SQLiteDatabase db){
			Log.vf(THIS_FILE, "testKeyOnDatabase");
			if (db==null)
				throw new IllegalArgumentException("Given database is null");
			if (!db.isOpen())
				return false;
			
			try {
				Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM sqlite_master ", null);
				return cursor != null;
			} catch(Exception e){
				Log.w(THIS_FILE, "Opening database with given key failed.");
			}
			
			return false;
		}

		public void tryVacuum(SQLiteDatabase db){
			try {
				Log.inf(THIS_FILE, "Going to VACUUM");

				db.execSQL("VACUUM");

				Log.inf(THIS_FILE, "VACUUM finished");
			} catch(Exception e){
				Log.ef(THIS_FILE, e, "VACUUM failed");
			}
		}
		
		/**
		 * Returns formated database password in plain. 
		 * 
		 * @param username
		 * @param userpass
		 * @return username  | userpass
		 */
		public static String formatDbPassword(String username, String userpass){
			return username + "|" + userpass;
		}
		
		/**
		 * Internal method for actual key testing.
		 * Opens database with a given key, stores instance to tmpDB. 
		 * @return
		 */
		private boolean openDbAndTestKey(String key, boolean readOnly){
			Log.vf(THIS_FILE, "openDbAndTestKey, readOnly=%s", readOnly);
			try {
				tmpDB = readOnly ? getReadableDatabase(key) : getWritableDatabase(key);
				return testKeyOnDatabase(tmpDB);
			} catch(Exception e){
				Log.w(THIS_FILE, "Opening database with given key failed.");
			}
			
			return false;
		}
		
		/**
		 * Closes temporary opened database silently.
		 */
		private void closeTmpDB(){
			try {
				if (this.tmpDB!=null && this.tmpDB.isOpen()){
					this.tmpDB.close();
				}
			} catch(Exception e){
				
			} finally {
				this.tmpDB = null;
			}
		}

		public String getDbFilename() {
			return dbFilename;
		}

		private void addColumn(SQLiteDatabase db, String table, String field, String type) {
            db.execSQL("ALTER TABLE " + table + " ADD "+ field + " " + type);
        }

		private void tryAddIndex(SQLiteDatabase db, String table, String idx_name, Collection<String> columns){
			try {
				addIndex(db, table, idx_name, columns);
			} catch(Exception e){
				Log.ef(THIS_FILE, e, "Exception when adding index %s to tbl %s", table, idx_name);
			}
		}

		private void addIndex(SQLiteDatabase db, String table, String idx_name, Collection<String> columns){
			String columnList = null;
			if (columns.size() == 1){
				columnList = columns.iterator().next();
			} else {
				columnList = Joiner.on(",").join(columns);
			}

			db.execSQL(String.format("CREATE INDEX IF NOT EXISTS %s ON %s(%s);", idx_name, table, columnList));
		}

        public List<String> getTableColumns(String tableName, SQLiteDatabase db) {
            ArrayList<String> columns = new ArrayList<String>();
            String cmd = "pragma table_info(?);";
            Cursor cur = db.rawQuery(cmd, new String[]{tableName});

            if (cur == null){
                Log.ef(THIS_FILE, "Retrieving table columns for table [%s] returned null cursor, table probably doesn't exist", tableName);
            }

            while (cur.moveToNext()) {
                columns.add(cur.getString(cur.getColumnIndex("name")));
            }
            MiscUtils.closeCursorSilently(cur);
            return columns;
        }

        /**
         * Drob column table softly, preserving data (by recreating table and copying data)
         * Can be also used for adding columns and preserving data
         * @param db
         * @param createTableCmd
         * @param tableName
         * @param colsToRemove
         * @throws java.sql.SQLException
         */
        private void dropColumn(SQLiteDatabase db,
                                String tableName,
                                String createTableCmd,
                                String[] colsToRemove) throws java.sql.SQLException {

            List<String> updatedTableColumns = getTableColumns(tableName, db);
            // Remove the columns we don't want anymore from the table's list of columns
            updatedTableColumns.removeAll(Arrays.asList(colsToRemove));

            String columnsSeperated = TextUtils.join(",", updatedTableColumns);

            db.execSQL("ALTER TABLE " + tableName + " RENAME TO " + tableName + "_old;");

            // Creating the table on its new format (no redundant columns)
            db.execSQL(createTableCmd);

            // Populating the table with the data
            db.execSQL("INSERT INTO " + tableName + "(" + columnsSeperated + ") SELECT "
                    + columnsSeperated + " FROM " + tableName + "_old;");
            db.execSQL("DROP TABLE " + tableName + "_old;");
        }
	}
}

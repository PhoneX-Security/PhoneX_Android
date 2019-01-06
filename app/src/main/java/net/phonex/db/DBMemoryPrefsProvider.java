package net.phonex.db;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.text.TextUtils;

import com.commonsware.cwac.prefs.CWSharedPreferences;
import com.commonsware.cwac.prefs.CWSharedPreferences.LoadPolicy;
import com.commonsware.cwac.prefs.SQLCipherStrategy;

import net.phonex.core.Intents;
import net.phonex.core.MemoryPrefManager;
import net.phonex.pref.PreferencesManager;
import net.phonex.service.SafeNetService;
import net.phonex.util.Base64;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.pref.PreferencesHelper;
import net.phonex.util.crypto.AESCipher;
import net.phonex.util.crypto.CertificatesAndKeys;
import net.phonex.util.crypto.CryptoHelper;
import net.phonex.util.crypto.MessageDigest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * This provider allow to retrieve preference from different process than the UI
 * process Should be used by service For the future could be useful for third
 * party apps.
 * 
 * This class holds in memory preferences, protected by a random encryption key.
 * 
 * @author ph4r05
 */
public class DBMemoryPrefsProvider extends ContentProvider {
	private static final String THIS_FILE = "DBMemoryPrefsProvider";
	
	private Random rand;
	private SQLCipherStrategy strategy;
	private PreferencesHelper prefs;
    private CWSharedPreferences cwprefs;
	
	private int writableReturn=0;
	private int readableReturn=0;

	private static final int KEY_BYTES = 16;
	
	private static final int PREFS = 1;
	private static final int PREF_ID = 2;
    private static final int CALLSIM = 3;
    private static final int CALLSIM_ID = 4;

    public static final String SNAPSHOT_FILE="meatballs";
    public static final int CALL_SNAPSHOT_SAVE=0;
    public static final int CALL_SNAPSHOT_LOAD=1;

	public static final int COL_INDEX_NAME = 0;
	public static final int COL_INDEX_VALUE = 1;
	
	// Custom permissions check
    public static final int METHOD_CALL = 1;
    public static final int METHOD_QUERY = 2;
    public static final int METHOD_INSERT = 3;
    public static final int METHOD_UPDATE = 4;
    public static final int METHOD_DELETE = 5;

	private final static UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
	static {
		URI_MATCHER.addURI(MemoryPrefManager.AUTHORITY, MemoryPrefManager.PREFS_TABLE_NAME, PREFS);
		URI_MATCHER.addURI(MemoryPrefManager.AUTHORITY, MemoryPrefManager.PREFS_TABLE_NAME + "/*", PREF_ID);
        URI_MATCHER.addURI(MemoryPrefManager.AUTHORITY, MemoryPrefManager.CALLSIM_TABLE_NAME, CALLSIM);
        URI_MATCHER.addURI(MemoryPrefManager.AUTHORITY, MemoryPrefManager.CALLSIM_TABLE_NAME + "/*", CALLSIM_ID);
	}

	@Override
	public boolean onCreate() {
        Log.inf(THIS_FILE, "Starting new memory provider, r=%s, w=%s", readableReturn, writableReturn);
		rand = new SecureRandom();
		
		// Encryption key
		String encKey = "magic-password";
		
		// Generate random database password
		final byte[] randKey = new byte[KEY_BYTES];
		rand.nextBytes(randKey);
		
		try {
			final byte[] key2 = MessageDigest.hashSha512(randKey);
			encKey = Base64.encodeBytes(key2);
		} catch(Exception e){
			Log.e(THIS_FILE, "Cannot compute hash?", e);
			encKey = Base64.encodeBytes(randKey);
		}
		
		// Create a in-memory SQLCipher database with random password.
		strategy = new SQLCipherStrategy(getContext(), MemoryPrefManager.AUTHORITY, null, encKey, LoadPolicy.SYNC);
        cwprefs = CWSharedPreferences.getInstance(strategy);
		prefs = new PreferencesHelper(getContext(), cwprefs);
		
		// Start lightweight SafeNet service running in same process preventing this process being killed.
		SafeNetService.start(getContext(), true);
		
		Log.v(THIS_FILE, "New instance created");
		return true;
	}

	/**
	 * Return the MIME type for an known URI in the provider.
	 */
	@Override
	public String getType(Uri uri) {
		switch (URI_MATCHER.match(uri)) {
		case PREFS:
			return MemoryPrefManager.PREF_CONTENT_TYPE;
		case PREF_ID:
			return MemoryPrefManager.PREF_CONTENT_ITEM_TYPE;
        case CALLSIM:
            return MemoryPrefManager.CALLSIM_CONTENT_TYPE;
        case CALLSIM_ID:
            return MemoryPrefManager.CALLSIM_CONTENT_ITEM_TYPE;

            default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String order) {
		checkPermissions(METHOD_QUERY, uri);
		
		MatrixCursor resCursor = new MatrixCursor(new String[] { MemoryPrefManager.FIELD_NAME, MemoryPrefManager.FIELD_VALUE });
		if (URI_MATCHER.match(uri) == PREF_ID) {
			String name = uri.getLastPathSegment();
			Class<?> aClass = null;
			if (TextUtils.isEmpty(selection)) {
				aClass = PreferencesManager.getPreferenceType(name);
			} else {
				try {
					aClass = Class.forName(selection);
				} catch (ClassNotFoundException e) {
					Log.e(THIS_FILE, "Impossible to retrieve class from selection");
				}
			}
			Object value = null;
			if (aClass == String.class) {
				value = prefs.getString(name);
			} else if(aClass == Integer.class) {
                value = prefs.getInteger(name);
            } else if (aClass == Float.class) {
				value = prefs.getFloat(name);
			} else if (aClass == Boolean.class) {
			    Boolean v = prefs.getBoolean(name);
			    if(v != null) {
			        value = v ? 1 : 0;
			    }else {
			        value = -1;
			    }
			}
			
			if (value != null) {
				resCursor.addRow(new Object[] { name, value });
			} else {
				resCursor = null;
			}
		} else if (URI_MATCHER.match(uri) == CALLSIM_ID) {
            long id = ContentUris.parseId(uri);
            Log.vf(THIS_FILE, "call() simulation invoked, id=%s", id);

            if (id == CALL_SNAPSHOT_SAVE){
                // If there is something to save, do it.
                int res = saveSnapshot(selectionArgs);
                resCursor.addRow(new Object[] { "result", Integer.valueOf(res) });
            } else if (id == CALL_SNAPSHOT_LOAD){
                // If there is something to load, do it.
                int res = loadSnapshot(selectionArgs);
                resCursor.addRow(new Object[] { "result", Integer.valueOf(res) });
            } else {
                Log.wf(THIS_FILE, "Unknown call code [%d]", id);
            }
        }
		
		Log.vf(THIS_FILE, "Query on URI; counter: %d", readableReturn); 
		readableReturn+=1;
		return resCursor;
	}

	@Override
	public int update(Uri uri, ContentValues cv, String selection, String[] selectionArgs) {
		checkPermissions(METHOD_UPDATE, uri);
		
		int count = 0;
		switch (URI_MATCHER.match(uri)) {
		case PREFS:
			// Ignore for now
			break;
		case PREF_ID:
			String name = uri.getLastPathSegment();
			Class<?> aClass = null;
			if (TextUtils.isEmpty(selection)) {
				aClass = PreferencesManager.getPreferenceType(name);
			} else {
				try {
					aClass = Class.forName(selection);
				} catch (ClassNotFoundException e) {
					Log.e(THIS_FILE, "Impossible to retrieve class from selection");
				}
			}
			if (aClass == String.class) {
				prefs.setString(name, cv.getAsString(MemoryPrefManager.FIELD_VALUE));
			} else if (aClass == Float.class) {
				prefs.setFloat(name, cv.getAsFloat(MemoryPrefManager.FIELD_VALUE));
			} else if (aClass == Boolean.class) {
				prefs.setBoolean(name, cv.getAsBoolean(MemoryPrefManager.FIELD_VALUE));
			} else if (aClass == Integer.class) {
				prefs.setInteger(name, cv.getAsInteger(MemoryPrefManager.FIELD_VALUE));
			}
			count++;
			break;
		}
		
		Log.vf(THIS_FILE, "Update URI; counter: %d", writableReturn);
		writableReturn+=1;
		return count;
	}

	@Override
	public int delete(Uri arg0, String arg1, String[] arg2) {
		checkPermissions(METHOD_DELETE, arg0);
		return 0;
	}

	@Override
	public Uri insert(Uri arg0, ContentValues arg1) {
		checkPermissions(METHOD_INSERT, arg0);
		return null;
	}
	
	/**
	 * Checks if a requested access is permitted, if not SecurityException is thrown.
	 * Current implementation is strict, only the same user ID is allowed.
	 * @param method
	 * @param uri//import com.github.nativehandler.NativeCrashHandler;
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
     * Deletes snapshot file if exists.
     */
    protected void deleteSnapshot(){
        try {
            getContext().deleteFile(SNAPSHOT_FILE);
        } catch(Exception ex){

        }
    }

    /**
     * Saves snapshot of the memory database to a file for later recovery.
     * Uses encrypted file.
     *
     * Current version uses dumping configuration to memory and encrypting
     * it to the file. If the prefs database gets bigger, it has to be refactored
     * in a streaming way using PipedInputStream & PipedOutputStream, but that would
     * require using threads and now we need to be fast, since this code is usually
     * called just before application crash/restart...
     *
     * @param args
     */
    protected int saveSnapshot(String[] args){
        if (args==null || args.length<2){
            throw new IllegalArgumentException("Invalid argument");
        }

        // If empty, snapshot is not needed.
        if (cwprefs.getAll().isEmpty()){
            Log.v(THIS_FILE, "Nothing to save, empty prefs");
            deleteSnapshot();
            return 0;
        }

        // Save snapshot
        FileOutputStream fos = null;
        try {
            fos = getContext().openFileOutput(SNAPSHOT_FILE, Context.MODE_PRIVATE);

            // Prepare encryption.
            Cipher cipher = prepareCipher(true, args[0], args[1]);

            // Temporary solution - using local ByteArrayOutputStream
            // Refactored solution would be to use a PipedInputStream & PipedOutputStream
            // in a threaded manner.
            Log.v(THIS_FILE, "Starting configuration dump");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            cwprefs.exportPrefs(bos);
            ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());

            // Encrypt & dump to the snapshot file.
            Log.v(THIS_FILE, "Starting configuration encryption");
            CryptoHelper.streamedEncryption(bis, fos, cipher);
            MiscUtils.closeSilently("bis", bis);
            MiscUtils.closeSilently("bos", bos);

            Log.v(THIS_FILE, "Snapshot dump finished");
            return 1;
        } catch (Exception e) {
            Log.e(THIS_FILE, "Exception: cannot save snapshot", e);
        } finally {
            MiscUtils.closeSilently("snapshot", fos);
        }

        return -1;
    }

    /**
     * Loads snapshot file.
     *
     * Current version uses dumping configuration to memory and encrypting
     * it to the file. If the prefs database gets bigger, it has to be refactored
     * in a streaming way using PipedInputStream & PipedOutputStream, but that would
     * require using threads and now we need to be fast, since this code is usually
     * called just before application crash/restart...
     *
     * @param args
     */
    protected int loadSnapshot(String[] args) {
        if (args==null || args.length<2){
            throw new IllegalArgumentException("Invalid argument");
        }

        FileInputStream fis = null;
        try {
            fis = getContext().openFileInput(SNAPSHOT_FILE);

            // Prepare encryption.
            Cipher cipher = prepareCipher(false, args[0], args[1]);

            // Temporary solution - using local ByteArrayOutputStream
            // Refactored solution would be to use a PipedInputStream & PipedOutputStream
            // in a threaded manner.
            Log.v(THIS_FILE, "Starting decryption of a snapshot");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            CryptoHelper.streamedDecryption(fis, bos, cipher);
            ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());

            Log.v(THIS_FILE, "Starting import of a snapshot");
            int importRes = cwprefs.importPrefs(bis);
            MiscUtils.closeSilently("bis", bis);
            MiscUtils.closeSilently("bos", bos);

            if (importRes==1){
                Log.v(THIS_FILE, "Import successful");

                // Send intent notification about successful resurrection.
                // Receiver may re-read settings and login to the application.
                Intent intent = new Intent(Intents.ACTION_RESURRECTED);
				MiscUtils.sendBroadcast(getContext(), intent);
                return 1;
            }

            Log.v(THIS_FILE, "Import finished");
            return 2;
        } catch(Exception ex){
            Log.e(THIS_FILE, "Exception: cannot load snapshot", ex);
        } finally {
            MiscUtils.closeSilently("snapshot", fis);

            // Cleanup in either way.
            deleteSnapshot();
        }

        return -1;
    }

    /**
     * Prepares cipher for encryption/decryption.
     *
     * @param encrypt
     * @param salt
     * @param iv
     * @return
     */
    protected Cipher prepareCipher(boolean encrypt, String salt, String iv)
            throws IOException, NoSuchAlgorithmException, NoSuchProviderException,
                   NoSuchPaddingException, InvalidAlgorithmParameterException, InvalidKeyException
    {
        // Prepare byte representation of the salt & iv.
        if (TextUtils.isEmpty(salt)
                || TextUtils.isEmpty(iv)
                || salt.length()<8
                || iv.length()<8)
        {
            throw new IllegalArgumentException("Invalid argument, null or too short");
        }

        // Generate Initialization Vector from iv string. Has to has 16 Bytes.
        byte[] ivTmp = MessageDigest.hashSha256(iv);
        byte[] ivB = new byte[16];
        for(int i=0;i<16;i++){
            ivB[i] = ivTmp[i];
        }

        // Generate encryption key. Easy SHA derivation.
        // This scheme can be easily changed later since snapshots are ment to be temporary.
        final String pass = salt + "|MEMDBenc|" + CertificatesAndKeys.getDeviceId(getContext());
        final byte encPass[] = MessageDigest.hashSha256(pass);

        // Convert ci keys to the AES encryption keys.
        // Its length has to correspond to the AES encryption key size.
        SecretKey keys = new SecretKeySpec(encPass, 0, encPass.length, "AES");

        // IV parameter specs
        IvParameterSpec ivspec = new IvParameterSpec(ivB);

        // Encrypt given data by AES-CBC
        Cipher aes = Cipher.getInstance(AESCipher.AES, CryptoHelper.BC);
        aes.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, keys, ivspec);

        return aes;
    }
}


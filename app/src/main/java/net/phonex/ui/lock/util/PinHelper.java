package net.phonex.ui.lock.util;

import android.app.Activity;
import android.content.Context;
import android.util.Base64;

import net.phonex.core.MemoryPrefManager;
import net.phonex.pref.PhonexConfig;
import net.phonex.pref.PreferencesConnector;
import net.phonex.util.Log;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Created by miroc on 10.12.14.
 */
public class PinHelper {
    private static final String TAG = "PinHelper";
    private static final String PIN_LOCK_STATE = "pin_lock_state";

    /**
     * Every activity has a thread generating ticks repeatedly in given time
     */
    public static final long PIN_TICK_TIME = 1980;
    public final static long PIN_TICK_MIN_THRESHOLD = 8000;

    // default pin encryption settings
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ROUNDS = 100;
    private static final int KEY_LEN = 256;
    private static final String KEY_ALGORITHM = "PBKDF2WithHmacSHA1";

    public static synchronized boolean hasPinSaved(Context c) {
        boolean hasPin = PhonexConfig.getStringPref(c, PhonexConfig.PIN_HASH_AND_SALT, null) != null;
        Log.vf(TAG, "hasPinSaved; hasPin [%s]", hasPin);
        return hasPin;
    }

    public static void resetSavedPin(Context c) {
        PhonexConfig.deleteStringPref(c, PhonexConfig.PIN_HASH_AND_SALT);
    }

    public static synchronized boolean doesMatchPin(Context c, String pin) {
        try {
            String hashAndSalt = PhonexConfig.getStringPref(c, PhonexConfig.PIN_HASH_AND_SALT);
            String[] pieces = hashAndSalt.split("\\|", 2);

            Log.vf(TAG, "doesMatchPin; hashAndSalt [%s] pin [%s]", hashAndSalt, pin);
            if (pieces == null || pieces.length != 2){
                throw new RuntimeException("error validating PIN, PIN is stored in non-consistent form");
            }

            byte[] storedPinHash = decode(pieces[0]);
            byte[] storedSalt = decode(pieces[1]);
            return validate(pin.toCharArray(), storedPinHash, storedSalt);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("error validating pin", e);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException("error validating pin", e);
        }
    }

    public static synchronized void savePin(Context context, String pin) {
        Log.vf(TAG, "savePin; pin [%s]", pin);
        try {
            final byte[] salt = generateSalt();
            final byte[] hash = hash(pin.toCharArray(), salt);

            // first hash then salt (separator is |, which is not valid Base64 character)
            String hashWithSalt = encode(hash) + "|" + encode(salt);
            Log.vf(TAG, "savePin; hashAndSalt [%s] pin [%s]", hashWithSalt, pin);
            PhonexConfig.setStringPref(context, PhonexConfig.PIN_HASH_AND_SALT, hashWithSalt);

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("error saving pin: ", e);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException("error saving pin: ", e);
        }
    }

    private static String encode(byte[] src) {
        return Base64.encodeToString(src, Base64.DEFAULT);
    }

    private static byte[] decode(String src) {
        return Base64.decode(src, Base64.DEFAULT);
    }

    public static synchronized boolean isLocked(Context context) {
        Boolean value = MemoryPrefManager.getPreferenceBooleanValue(context, PIN_LOCK_STATE, true);
        Log.vf(TAG, "isLocked; [%s]", value);
        return value;
    }

    public static synchronized void lock(Context context, boolean locked) {
        Log.inf(TAG, "PinLock: setting locked state to [%s]", String.valueOf(locked));
        MemoryPrefManager.setPreferenceBooleanValue(context, PIN_LOCK_STATE, locked);
    }

    public static synchronized boolean isEnabled(Context context){
        Log.v(TAG, "isEnabled");
        PreferencesConnector prefs = new PreferencesConnector(context);
        return prefs.getBoolean(PhonexConfig.PIN_LOCK_ENABLE);
    }

    /**
     * Ticking prevents application from locking.
     * If tick doesn't happen for a given threshold time,
     * application lock itself next time it enters onStart() method.
     *
     * This method should be called periodically from each activity
     *
     * @param
     */
    public static synchronized void tick(Activity activity) {
        long tickTime = System.currentTimeMillis();
//        Log.vf(TAG, "tick [%d], activity [%s]", tickTime, activity.getLocalClassName());
        PreferencesConnector preferencesConnector = new PreferencesConnector(activity);
        preferencesConnector.setLong(PhonexConfig.PIN_LAST_TICK, tickTime);
    }

    /**
     * Lock if difference between last tick and current time is bigger than TICK_THRESHOLD
     * @param
     */
    public static void lockIfNoRecentTick(Context context) {
        PreferencesConnector connector = new PreferencesConnector(context);
        connector.pinLockIfNoRecentTick();
    }

    private static byte[] generateSalt() {
        byte[] salt = new byte[24];
        RANDOM.nextBytes(salt);
        return salt;
    }

    private static byte[] hash(char[] pin, byte[] salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        PBEKeySpec spec = new PBEKeySpec(pin, salt, ROUNDS, KEY_LEN);
        Arrays.fill(pin, Character.MIN_VALUE);
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance(KEY_ALGORITHM);
            return skf.generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static boolean validate(char[] actual, byte[] expected, byte[] salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] pwdHash = hash(actual, salt);
        Arrays.fill(actual, Character.MIN_VALUE);
        if (pwdHash.length != expected.length) return false;
        for (int i = 0; i < pwdHash.length; i++) {
            if (pwdHash[i] != expected[i]) return false;
        }
        return true;
    }
}

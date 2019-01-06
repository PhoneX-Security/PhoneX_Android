package net.phonex.util.crypto;

import android.content.Context;

import net.phonex.pref.PreferencesConnector;
import net.phonex.util.Base64;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Random;


/**
 * Class for generating, saving and loading random salt to/from SharedPreferences.
 * 
 * @author ph4r05
 */
public class SharedPrefsSalt {
	/**
	 * Returns preference key for salt for the given user.
	 * @param user
	 * @return
	 */
	public static String getSaltPrefsKey(String prefsKey, String user){
		if (user==null)
			throw new IllegalArgumentException("Cannot work with null");
		
		final int len = user.length();
		if (len==0)
			return prefsKey + "_DEFAULT";
		
		final String userShort = user.substring(0, len > 20 ? 20 : len);
		final String userClean = userShort.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase().trim();
		if (userClean.length()==0)
			throw new IllegalArgumentException("Invalid username format - empty after cleaning from nonalphanumeric characters."); 
		return prefsKey + "_" + userClean;
	}
	
	/**
	 * Returns true if there is stored a salt value in a shared preferences.
	 * @param ctxt
	 * @return
	 */
	public static boolean saltExists(Context ctxt, String prefsKey, String user){
		PreferencesConnector prefs = new PreferencesConnector(ctxt);
		return prefs.getString(getSaltPrefsKey(prefsKey, user), null) != null;
	}
	
	/**
	 * Generates new salt to the shared preferences.
	 * @param ctxt
	 * @param rand
	 */
	public static void generateNewSalt(Context ctxt, String prefsKey, String user, int saltSize){
		generateNewSalt(ctxt, prefsKey, user, saltSize, new SecureRandom());
	}
	
	/**
	 * Generates new salt to the shared preferences.
	 * @param ctxt
	 * @param rand
	 */
	public static void generateNewSalt(Context ctxt, String prefsKey, String user, int saltSize, Random rand){
		//PreferencesManager prefs = new PreferencesManager(ctxt);
		PreferencesConnector prefs = new PreferencesConnector(ctxt);
		
		byte salt[] = new byte[saltSize];
		rand.nextBytes(salt);
		
		final String saltEncoded = Base64.encodeBytes(salt);
		
		prefs.setString(getSaltPrefsKey(prefsKey, user), saltEncoded);
	}
	
	/**
	 * Loads salt from preferences
	 * @param ctxt
	 * @return
	 * @throws IOException
	 */
	public static byte[] getSalt(Context ctxt, String prefsKey, String user) throws IOException{
		final String prefsKeyX = getSaltPrefsKey(prefsKey, user);
		
//		PreferencesManager prefs = new PreferencesManager(ctxt);
//		if (!prefs.hasPreferenceKey(prefsKeyX)){
//			return null;
//		}
		PreferencesConnector prefs = new PreferencesConnector(ctxt);
		if (prefs.getString(prefsKeyX, null) == null) {
			return null;
		}
		
		final String saltEncoded = prefs.getString(prefsKeyX);
		final byte salt[] = Base64.decode(saltEncoded);
		
		return salt;
	}
}


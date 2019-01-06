package net.phonex.autologinQuick;

import android.content.Context;

import net.phonex.autologin.exceptions.LoginStorageException;
import net.phonex.inapp.Base64;
import net.phonex.inapp.Base64DecoderException;
import net.phonex.pref.PhonexConfig;
import net.phonex.pref.PreferencesConnector;

/**
 * Storage for encrypted login credentials in shared preferences
 * Created by miroc on 20.2.16.
 */
public class PreferencesLoginStorage {
    private final PreferencesConnector prefs;

    public PreferencesLoginStorage(Context context) {
        prefs = new PreferencesConnector(context);
    }

    public boolean isLoginAvailable() {
        return prefs.getString(PhonexConfig.ENCRYPTED_LOGIN_CREDS) != null;
    }

    public void deleteLogin() {
        prefs.deleteString(PhonexConfig.ENCRYPTED_LOGIN_CREDS);
    }

    public static void deleteLogin(Context context){
        new PreferencesLoginStorage(context).deleteLogin();
    }

    public void storeLogin(byte[] encryptedLogin) throws LoginStorageException {
        String encodedLogin = Base64.encode(encryptedLogin);
        prefs.setString(PhonexConfig.ENCRYPTED_LOGIN_CREDS, encodedLogin);
    }

    public byte[] loadLogin() throws LoginStorageException {
        String encodedLogin = prefs.getString(PhonexConfig.ENCRYPTED_LOGIN_CREDS);
        byte[] encryptedLogin;
        try {
            encryptedLogin = Base64.decode(encodedLogin);
        } catch (Base64DecoderException e) {
            throw new LoginStorageException("Unable to decode stored credentials", e);
        }
        return encryptedLogin;
    }
}
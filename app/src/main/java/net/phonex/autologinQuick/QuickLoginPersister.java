package net.phonex.autologinQuick;

import android.content.Context;
import android.support.annotation.NonNull;

import net.phonex.autologin.LoginCredentials;
import net.phonex.autologin.exceptions.LoginStorageException;
import net.phonex.autologin.exceptions.PasswordPersisterException;
import net.phonex.autologin.exceptions.ServiceUnavailableException;
import net.phonex.util.Log;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Save and store LoginCredentials securely - used for quick login
 * @author miroc
 */
public class QuickLoginPersister {
    private static final String TAG = "CredentialsPersister";
    private static final String KEY_PAIR_ALIAS = "QuickLoginKey";

    private Context context;
    private PreferencesLoginStorage loginStorage;

    public QuickLoginPersister(Context context){
        if (context == null){
            throw new IllegalArgumentException("Null context");
        }
        this.context = context;
        loginStorage = new PreferencesLoginStorage(context);
    }

    public void storeCredentials(@NonNull LoginCredentials loginCredentials) throws PasswordPersisterException {
        SecretKeyWrapper secretKeyWrapper = loadKeyWrapper();
        byte[] encryptedLogin;
        try {
            encryptedLogin = CredentialsEncrypter.encryptAndSerialize(loginCredentials, secretKeyWrapper);
            loginStorage.storeLogin(encryptedLogin);
        } catch (GeneralSecurityException | LoginStorageException e) {
            throw new PasswordPersisterException(e);
        }
        Log.inf(TAG, "storeCredentials; saving successful");
    }

    public @NonNull LoginCredentials loadCredentials() throws PasswordPersisterException, ServiceUnavailableException {
        SecretKeyWrapper secretKeyWrapper = loadKeyWrapper();
        if (!loginStorage.isLoginAvailable()){
            throw new ServiceUnavailableException("Login is not available in login storage");
        }

        try {
            byte[] storedLogin = loginStorage.loadLogin();
            return CredentialsEncrypter.deserializeAndDecrypt(storedLogin, secretKeyWrapper);
        } catch (LoginStorageException | GeneralSecurityException e) {
            throw new PasswordPersisterException(e);
        }
    }

    private SecretKeyWrapper loadKeyWrapper() throws PasswordPersisterException {
        try {
            return new SecretKeyWrapper(context, KEY_PAIR_ALIAS);
        } catch (GeneralSecurityException | IOException e) {
            throw new PasswordPersisterException(e);
        }
    }
}
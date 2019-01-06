package net.phonex.autologin.soap;

import android.content.Context;
import android.content.Intent;

import net.phonex.autologin.AndroidKeyStorePasswordPersister;
import net.phonex.autologin.FileSystemLoginStorage;
import net.phonex.autologin.LoginCredentials;
import net.phonex.autologin.LoginStorage;
import net.phonex.autologin.PasswordPersister;
import net.phonex.autologin.RemoteServerSecretKeyStorage;
import net.phonex.autologin.SecretKeyStorage;
import net.phonex.autologin.exceptions.LoginStorageException;
import net.phonex.autologin.exceptions.NotSupportedException;
import net.phonex.autologin.exceptions.PasswordPersisterException;
import net.phonex.autologin.exceptions.SecretKeyStorageException;
import net.phonex.autologin.exceptions.ServiceUnavailableException;
import net.phonex.core.Intents;
import net.phonex.soap.RunnableWithException;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;

/**
 * Created by Matus on 02-Sep-15.
 */
public class AuthStateFetchCall implements RunnableWithException {

    private static final String TAG = "AuthStateFetchCall";

    private Exception thrownException = null;

    private Context context;

    public AuthStateFetchCall(Context context) {
        this.context = context;
    }

    @Override
    public Exception getThrownException() {
        return thrownException;
    }

    @Override
    public void run() {
        PasswordPersister passwordPersister = new AndroidKeyStorePasswordPersister(context);

        if (passwordPersister.isKeyStoreAvailable()) {
            Log.i(TAG, "triggerLoginFromSavedState");

            LoginStorage loginStorage = new FileSystemLoginStorage(context);
            SecretKeyStorage secretKeyStorage = new RemoteServerSecretKeyStorage(context);

            try {
                LoginCredentials credentials = passwordPersister.loadPassword(loginStorage, secretKeyStorage);

                Intent intent = new Intent(Intents.ACTION_TRIGGER_LOGIN);
                intent.putExtra(Intents.EXTRA_LOGIN_SIP, credentials.userName);
                intent.putExtra(Intents.EXTRA_LOGIN_PASSWORD, credentials.password);
                intent.putExtra(Intents.EXTRA_LOGIN_DOMAIN, credentials.domain);
                // lock pin immediately after autologin
                intent.putExtra(Intents.EXTRA_LOCK_PIN_AFTER_STARTUP, true);
                MiscUtils.sendBroadcast(context, intent);

            } catch (PasswordPersisterException | SecretKeyStorageException | LoginStorageException e) {
                Log.e(TAG, "Unrecoverable autologin error", e);
                thrownException = e;
                loginStorage.deleteLogin();
//                try {
//                    passwordPersister.deleteKeyPair();
//                } catch (PasswordPersisterException e1) {
//                    Log.w(TAG, "Failed to delete key pair from key store", e);
//                }
            } catch (ServiceUnavailableException e) {
                Log.w(TAG, "Service is not available now", e);
                thrownException = e;
            }
        } else {
            Log.e(TAG, "Key store is not available");
            thrownException = new NotSupportedException("Key store is not available");
        }
    }
}

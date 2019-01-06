package net.phonex.autologin.soap;

import android.content.Context;

import net.phonex.autologin.AndroidKeyStorePasswordPersister;
import net.phonex.autologin.FileSystemLoginStorage;
import net.phonex.autologin.LoginStorage;
import net.phonex.autologin.PasswordPersister;
import net.phonex.autologin.RemoteServerSecretKeyStorage;
import net.phonex.autologin.SecretKeyStorage;
import net.phonex.autologin.exceptions.LoginStorageException;
import net.phonex.autologin.exceptions.NotSupportedException;
import net.phonex.autologin.exceptions.PasswordPersisterException;
import net.phonex.autologin.exceptions.SecretKeyStorageException;
import net.phonex.autologin.exceptions.ServiceUnavailableException;
import net.phonex.soap.RunnableWithException;
import net.phonex.util.Log;

import java.util.Calendar;
import java.util.Date;

/**
 * Created by Matus on 02-Sep-15.
 */
public class AuthStateSaveCall implements RunnableWithException {

    private static final String TAG = "AuthStateSaveCall";

    private Exception thrownException;

    private Context context;

    private byte[] password;
    private byte[] userName;

    public AuthStateSaveCall(Context context, byte[] password, byte[] userName) {
        this.context = context;
        this.password = password;
        this.userName = userName;
    }

    @Override
    public Exception getThrownException() {
        return thrownException;
    }

    @Override
    public void run() {
        PasswordPersister passwordPersister = new AndroidKeyStorePasswordPersister(context);

        if (passwordPersister.isKeyStoreAvailable()) {
            Log.i(TAG, "triggerAuthStateSave");

            LoginStorage loginStorage = new FileSystemLoginStorage(context);
            SecretKeyStorage secretKeyStorage = new RemoteServerSecretKeyStorage(context);

            // setting how long the key pair will be valid, if it is currently invalid
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.MONTH, 1);
            //calendar.add(Calendar.MINUTE, 15);
            Date end = calendar.getTime();

            try {
                passwordPersister.storePassword(loginStorage, secretKeyStorage, password, userName, end);
            } catch (PasswordPersisterException | SecretKeyStorageException | LoginStorageException e) {
                Log.e(TAG, "Unrecoverable autologin error", e);
                loginStorage.deleteLogin();
//                try {
//                    passwordPersister.deleteKeyPair();
//                } catch (PasswordPersisterException e1) {
//                    Log.w(TAG, "Failed to delete key pair from key store", e);
//                }
                thrownException = e;
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

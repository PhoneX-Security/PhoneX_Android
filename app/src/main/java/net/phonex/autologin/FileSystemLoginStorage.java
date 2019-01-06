package net.phonex.autologin;

import android.content.Context;
import net.phonex.util.Log;

import net.phonex.autologin.exceptions.LoginStorageException;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Stores serialized login information in private file storage.
 */
public class FileSystemLoginStorage implements LoginStorage {

    private static final String TAG = "PasswordPersister";

    private static final String FILE_NAME = "QuickRelogin";

    private Context context;

    public FileSystemLoginStorage(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context == null");
        }
        this.context = context;
    }

    @Override
    public boolean isLoginAvailable() {
        FileInputStream inputStream;
        try {
            inputStream = context.openFileInput(FILE_NAME);
            inputStream.close();
        } catch (FileNotFoundException e) {
            return false;
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    @Override
    public void deleteLogin() {
        context.deleteFile(FILE_NAME);
    }

    @Override
    public void storeLogin(byte[] encryptedLogin) throws LoginStorageException {
        FileOutputStream outputStream;
        try {
            outputStream = context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE);
            outputStream.write(encryptedLogin);
            outputStream.close();
        } catch (FileNotFoundException e) {
            Log.d(TAG, "FileNotFoundException", e);
            throw new LoginStorageException("FileNotFoundException, possibly no write rights?", e);
        } catch (IOException e) {
            Log.d(TAG, "IOException", e);
            throw new LoginStorageException("IOException", e);
        }
    }

    @Override
    public byte[] loadLogin(int rsaKeySize) throws LoginStorageException {
        // RSA can only encrypt one block
        final int rsaBlockSize = RSAHelper.getBlockSize(rsaKeySize);

        byte[] encryptedLogin = new byte[rsaBlockSize];

        FileInputStream inputStream;
        try {
            inputStream = context.openFileInput(FILE_NAME);
            int read = inputStream.read(encryptedLogin, 0, rsaBlockSize);
            if (read < rsaBlockSize) {
                Log.d(TAG, "Incorrect size of persisted data: " + read + " expected " + rsaBlockSize);
                throw new LoginStorageException("Incorrect size of persisted data");
            }
            inputStream.close();
            return encryptedLogin;
        } catch (FileNotFoundException e) {
            throw new LoginStorageException("Login file not saved", e);
        } catch (IOException e) {
            throw new LoginStorageException(e);
        }
    }
}

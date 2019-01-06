package net.phonex.autologin;

import net.phonex.autologin.exceptions.LoginStorageException;

/**
 * Stores serialized login information.
 */
public interface LoginStorage {
    /**
     * Verifies if login information is saved.
     *
     * @return true if login is saved.
     */
    boolean isLoginAvailable();

    /**
     * Deletes stored login if available.
     */
    void deleteLogin();

    /**
     * Stores login information.
     * If data leaves device, use e.g. base64.
     *
     * @param encryptedLogin encrypted serialized login to be stored.
     * @throws LoginStorageException if login is not available or error occurs.
     */
    void storeLogin(byte[] encryptedLogin) throws LoginStorageException;

    /**
     * Loads login from storage.
     *
     * @param rsaKeySize size of RSA key modulus used to compute block size
     * @return encrypted serialized login
     * @throws LoginStorageException if login cannot be loaded or was not saved.
     */
    byte[] loadLogin(int rsaKeySize) throws LoginStorageException;
}

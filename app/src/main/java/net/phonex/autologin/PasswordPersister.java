package net.phonex.autologin;

import net.phonex.autologin.exceptions.LoginStorageException;
import net.phonex.autologin.exceptions.PasswordPersisterException;
import net.phonex.autologin.exceptions.SecretKeyStorageException;
import net.phonex.autologin.exceptions.ServiceUnavailableException;

import java.util.Date;

/**
 * Saves password encrypted with random secret key.
 * Key is saved to SecretKey storage (typically secure remote server).
 * Secret key, initialization vector for symmetric encryption and authentication nonce
 * are encrypted using asymmetric crypto.
 */
public interface PasswordPersister {

    /**
     * Verify if AndroidKeyStore provider is available on this device.
     * Should be called before saving or loading login.
     *
     * @return true if AndroidKeyStore provider is available
     */
    boolean isKeyStoreAvailable();

    /**
     * Returns maximal size of data in bytes, that can be saved using this method.
     * This should be called before trying to save long passwords.
     *
     * @return maximal size of data in bytes, that can be saved using this method.
     */
    int getMaxPayloadLength();

    /**
     * Securely stores given password using the AndroidKeyStore and server.
     * Before calling this method, verify that the key store is available by isKeyStoreAvailable().
     *
     * @param loginStorage the storage to which encrypted serialized login information will be written
     * @param password     the password or derived key to be saved
     * @param end          end of validity of keypair that protects this password, or DEFAULT_VALID_HOURS
     * @throws PasswordPersisterException  for errors with crypto and wrong order of operations
     * @throws LoginStorageException       for errors when writing to login storage
     * @throws ServiceUnavailableException server is not reachable, try later
     * @throws SecretKeyStorageException   when unable to store keys
     */
    void storePassword(LoginStorage loginStorage,
                              SecretKeyStorage secretKeyStorage, byte[] password, byte[] userName, Date end)
            throws PasswordPersisterException, ServiceUnavailableException,
            SecretKeyStorageException, LoginStorageException;

    /**
     * Securely loads given password stored using the AndroidKeyStore and server.
     * Before calling this method, verify that the key store is available by isKeyStoreAvailable().
     *
     * @param loginStorage the storage from which encrypted serialized login information will be read
     * @return the recovered password
     * @throws PasswordPersisterException  for errors with crypto and wrong order of operations
     * @throws LoginStorageException       for errors when reading from login storage
     * @throws ServiceUnavailableException server is not reachable, try
     * @throws SecretKeyStorageException   when unable to load keys
     */
    LoginCredentials loadPassword(LoginStorage loginStorage, SecretKeyStorage secretKeyStorage)
            throws PasswordPersisterException, ServiceUnavailableException,
            SecretKeyStorageException, LoginStorageException;

    /**
     * Check if required key pair is stored in AndroidKeyStore
     *
     * @return true if key is available and valid
     */
    boolean isKeyPairAvailable();

//    /**
//     * Deletes stored key pair if present.
//     *
//     * @throws PasswordPersisterException if key store is not available
//     */
//    void deleteKeyPair() throws PasswordPersisterException;

    /**
     * Key pair is not saved, first you have to save login to be able to read login later.
     */
    class MissingKeyPairException extends PasswordPersisterException {
        public MissingKeyPairException() {
        }

        public MissingKeyPairException(String s) {
            super(s);
        }

        public MissingKeyPairException(Exception e) {
            super(e);
        }

        public MissingKeyPairException(String s, Exception e) {
            super(s, e);
        }
    }

    /**
     * Key pair is invalid, you have to save login to be able to read login again.
     */
    class InvalidKeyPairException extends PasswordPersisterException {
        public InvalidKeyPairException() {
        }

        public InvalidKeyPairException(String detailMessage) {
            super(detailMessage);
        }

        public InvalidKeyPairException(String detailMessage, Exception e) {
            super(detailMessage, e);
        }

        public InvalidKeyPairException(Exception e) {
            super(e);
        }
    }
}

package net.phonex.autologin;

import net.phonex.autologin.exceptions.SecretKeyStorageException;
import net.phonex.autologin.exceptions.ServiceUnavailableException;

/**
 * Stores one secret key for a given nonce.
 */
public interface SecretKeyStorage {

    /**
     * Stores secret key for later retrieval. Nonce will be used for authentication.
     *
     * @param key   secret key that will be stored securely for later retrieval
     * @param nonce nonce that must be provided in order to get the secret key later
     * @throws ServiceUnavailableException if the service is temporarily unavailable
     * @throws SecretKeyStorageException   if current state does not allow to upload the key
     */
    void uploadEncodedKeyAndNonce(final byte[] key, final byte[] nonce)
            throws ServiceUnavailableException, SecretKeyStorageException;

    /**
     * @param nonce
     * @param userName
     * @return secret key
     * @throws ServiceUnavailableException if the service is temporarily unavailable
     * @throws SecretKeyStorageException   if current state does not allow to retrieve the key
     *                                     or nonce is incorrect
     */
    byte[] downloadEncodedKey(final byte[] nonce, final byte[] userName)
            throws ServiceUnavailableException, SecretKeyStorageException;
}

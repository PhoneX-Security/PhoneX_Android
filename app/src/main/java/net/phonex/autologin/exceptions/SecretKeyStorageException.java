package net.phonex.autologin.exceptions;

/**
 * Keys are not stored or cannot be stored for unrecoverable reason.
 */
public class SecretKeyStorageException extends Exception {
    public SecretKeyStorageException() {
    }

    public SecretKeyStorageException(Throwable throwable) {
        super(throwable);
    }

    public SecretKeyStorageException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public SecretKeyStorageException(String detailMessage) {
        super(detailMessage);
    }
}

package net.phonex.autologin.exceptions;

/**
 * Errors with crypto, requried values not provided etc. Mostly unrecoverable.
 */
public class PasswordPersisterException extends Exception {
    public PasswordPersisterException() {
    }

    public PasswordPersisterException(String detailMessage) {
        super(detailMessage);
    }

    public PasswordPersisterException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public PasswordPersisterException(Throwable throwable) {
        super(throwable);
    }
}

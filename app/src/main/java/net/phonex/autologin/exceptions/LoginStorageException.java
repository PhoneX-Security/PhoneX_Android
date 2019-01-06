package net.phonex.autologin.exceptions;


public class LoginStorageException extends Exception {
    public LoginStorageException() {
    }

    public LoginStorageException(String detailMessage) {
        super(detailMessage);
    }

    public LoginStorageException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public LoginStorageException(Throwable throwable) {
        super(throwable);
    }
}

package net.phonex.autologin.exceptions;

/**
 * Errors with connection to server. Recoverable, just try later.
 */
public class ServiceUnavailableException extends Exception {
    public ServiceUnavailableException() {
    }

    public ServiceUnavailableException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public ServiceUnavailableException(Throwable throwable) {
        super(throwable);
    }


    public ServiceUnavailableException(String detailMessage) {
        super(detailMessage);
    }
}

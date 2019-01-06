package net.phonex.autologin.exceptions;

/**
 * Created by Matus on 10-Sep-15.
 */
public class NotSupportedException extends Exception {
    public NotSupportedException() {
    }

    public NotSupportedException(String detailMessage) {
        super(detailMessage);
    }

    public NotSupportedException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public NotSupportedException(Throwable throwable) {
        super(throwable);
    }
}

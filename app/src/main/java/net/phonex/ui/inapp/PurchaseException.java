package net.phonex.ui.inapp;

/**
 * Created by miroc on 11.11.15.
 */
public class PurchaseException extends Exception {
    public PurchaseException(String detailMessage) {
        super(detailMessage);
    }

    public PurchaseException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }
}

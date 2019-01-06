package net.phonex.ft.transfer;

/**
 * Custom exceptions to differentiate known error conditions that
 * can happen in the protocol and other.
 * @author ph4r05
 *
 */
public class UploadException extends Exception {
    private static final long serialVersionUID = -316518830383421697L;

    @SuppressWarnings("unused")
    public UploadException() {
        super();
    }

    @SuppressWarnings("unused")
    public UploadException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public UploadException(String detailMessage) {
        super(detailMessage);
    }

    @SuppressWarnings("unused")
    public UploadException(Throwable throwable) {
        super(throwable);
    }
}

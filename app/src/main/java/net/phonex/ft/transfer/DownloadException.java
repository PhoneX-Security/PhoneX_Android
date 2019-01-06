package net.phonex.ft.transfer;

/**
 * Custom exceptions to differentiate known error conditions that
 * can happen in the protocol and other.
 * @author ph4r05
 *
 */
public class DownloadException extends Exception {
    private static final long serialVersionUID = -316518830383421697L;

    @SuppressWarnings("unused")
    public DownloadException() {
        super();
    }

    @SuppressWarnings("unused")
    public DownloadException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public DownloadException(String detailMessage) {
        super(detailMessage);
    }

    @SuppressWarnings("unused")
    public DownloadException(Throwable throwable) {
        super(throwable);
    }
}

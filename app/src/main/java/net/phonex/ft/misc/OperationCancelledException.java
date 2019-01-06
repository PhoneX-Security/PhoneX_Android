package net.phonex.ft.misc;

import java.io.IOException;

/**
 * Exception to inform that current operation was abnormally cancelled on user request.
 *
 * @author ph4r05
 */
public class OperationCancelledException extends IOException {
    private static final long serialVersionUID = 2L;

    public OperationCancelledException() {
        super();
    }

    public OperationCancelledException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public OperationCancelledException(String detailMessage) {
        super(detailMessage);
    }

    public OperationCancelledException(Throwable throwable) {
        super(throwable);
    }
}

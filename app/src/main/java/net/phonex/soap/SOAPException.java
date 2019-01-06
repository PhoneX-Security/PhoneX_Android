package net.phonex.soap;

import java.io.IOException;

public class SOAPException extends IOException {
    public SOAPException() {
    }

    public SOAPException(String detailMessage) {
        super(detailMessage);
    }

    public SOAPException(String message, Throwable cause) {
        super(message, cause);
    }

    public SOAPException(Throwable cause) {
        super(cause);
    }
}

package net.phonex.soap;

import java.io.IOException;

/**
 * Created by dusanklinec on 09.04.15.
 */
public class SOAPParserException extends IOException {
    public SOAPParserException() {
    }

    public SOAPParserException(String detailMessage) {
        super(detailMessage);
    }

    public SOAPParserException(String message, Throwable cause) {
        super(message, cause);
    }

    public SOAPParserException(Throwable cause) {
        super(cause);
    }
}

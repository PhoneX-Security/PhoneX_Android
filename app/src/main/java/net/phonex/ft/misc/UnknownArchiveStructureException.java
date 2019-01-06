package net.phonex.ft.misc;

import java.io.IOException;

/**
 * Thrown is archive file has unknown/forbidden structure.
 * E.g., ZIP file contains folders in protocol version = 1.
 *
 * @author ph4r05
 *
 */
public class UnknownArchiveStructureException extends IOException {
    private static final long serialVersionUID = 1L;

    public UnknownArchiveStructureException() {
        super();
    }

    public UnknownArchiveStructureException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public UnknownArchiveStructureException(String detailMessage) {
        super(detailMessage);
    }

    public UnknownArchiveStructureException(Throwable throwable) {
        super(throwable);
    }
}

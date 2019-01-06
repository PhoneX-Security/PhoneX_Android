package com.commonsware.cwac.prefs;

/**
 * Created by ph4r05 on 7/16/14.
 */
public class ImportException extends Exception {
    public ImportException(String s) {
        super(s);
    }
    public ImportException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }


}
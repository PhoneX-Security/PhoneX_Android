package net.phonex.util.analytics;

/**
 * Created by miroc on 2.9.15.
 */
public enum AppEvents {
    LOGIN,
    LOGOUT,
    CONTACT_ADDED,
    CONTACT_DELETED,
    CONTACT_RENAMED,
    CONTACT_REQUEST_DENIED,
    CALL_ESTABLISHED,
    CALL_ENDED,
    CALL_TAKEN,
    CALL_NOT_TAKEN,
    CALL_INITIATED,
    ZRTP_ESTABLISHED,
    MESSAGE_TEXT_SENT,
    MESSAGE_FILE_SENT,
    PHOTO_TAKEN,
    PHOTO_SAVED,
    STATUS_CHANGED,
    PIN_LOCK_CREATED,
    PIN_LOCK_DISABLED,
    PIN_LOCK_CHANGED,
    ACCOUNT_CREATED,
    PASSWORD_CHANGED,

    GOOGLE_ANALYTICS_DISABLE
    ;


    //    CONTACT_REQUEST_ACCEPTED,
    @Override
    public String toString() {
        return name().toLowerCase();
    }
}

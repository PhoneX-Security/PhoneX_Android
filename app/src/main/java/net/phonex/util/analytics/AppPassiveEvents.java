package net.phonex.util.analytics;

/**
 * Created by miroc on 2.9.15.
 */
public enum AppPassiveEvents {
    CALL_RECEIVED,
    MESSAGE_TEXT_RECEIVED,
    MESSAGE_FILE_RECEIVED,
    CONTACT_REQUEST_RECEIVED;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}

package net.phonex.service.xmpp.customIq;

/**
 * Push message signalizing license associated to this account has changed and we should re-fetch it.
 */
public class LicenseCheckPushMessage extends AbstractPushMessage{
    public static final String ACTION = "licCheck";

    public LicenseCheckPushMessage(long timestamp) {
        super(timestamp);
    }
}

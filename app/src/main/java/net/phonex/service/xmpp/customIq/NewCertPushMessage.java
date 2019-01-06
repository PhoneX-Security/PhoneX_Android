package net.phonex.service.xmpp.customIq;

/**
 * Push message signalizing this account has registered a new valid certificate.
 * In this case we should re-check our session and eventually log out if we are in conflict with
 * another valid license.
 */
public final class NewCertPushMessage extends AbstractPushMessage{
    public static final String ACTION = "newCert";
    public static final String FIELD_NOT_BEFORE = "certNotBefore";
    public static final String FIELD_CERT_HASH_PREFIX = "certHashPref";

    private long certNotBefore;
    private String certHashPrefix;

    public NewCertPushMessage(long timestamp, long certNotBefore, String certHashPrefix) {
        super(timestamp);
        this.certNotBefore = certNotBefore;
        this.certHashPrefix = certHashPrefix;
    }

    public long getCertNotBefore() {
        return certNotBefore;
    }

    public String getCertHashPrefix() {
        return certHashPrefix;
    }
}

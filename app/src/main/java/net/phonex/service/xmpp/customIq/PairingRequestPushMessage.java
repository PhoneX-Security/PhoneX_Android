package net.phonex.service.xmpp.customIq;

/**
 * Pairing request (contact request) push notification
 *
 * Created by Matus on 24-Aug-15.
 */
public class PairingRequestPushMessage extends AbstractPushMessage {
    public static final String ACTION = "pair";

    public PairingRequestPushMessage(long timestamp) {
        super(timestamp);
    }
}

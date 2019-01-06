package net.phonex.service.xmpp.customIq;

/**
 * Push message signalizing some contact is asking us for association (friend request).
 */
public class ContactRequestPushMessage extends AbstractPushMessage{
    public static final String ACTION = "cReq";

    public ContactRequestPushMessage(long timestamp) {
        super(timestamp);
    }
}

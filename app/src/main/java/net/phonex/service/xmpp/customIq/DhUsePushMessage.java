package net.phonex.service.xmpp.customIq;

/**
 * Created by miroc on 11.3.15.
 */
public class DhUsePushMessage extends AbstractPushMessage{
    public static final String ACTION = "dhUse";

    public DhUsePushMessage(long timestamp) {
        super(timestamp);
    }
}

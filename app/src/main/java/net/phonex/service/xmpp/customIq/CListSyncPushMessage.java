package net.phonex.service.xmpp.customIq;

/**
 * Created by miroc on 11.3.15.
 */
public class CListSyncPushMessage extends AbstractPushMessage{
    public static final String ACTION = "clistSync";

    public CListSyncPushMessage(long timestamp) {
        super(timestamp);
    }
}

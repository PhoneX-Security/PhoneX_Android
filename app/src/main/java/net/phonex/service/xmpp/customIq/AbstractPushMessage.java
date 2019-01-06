package net.phonex.service.xmpp.customIq;

/**
 * Created by miroc on 11.3.15.
 */
public abstract class AbstractPushMessage implements PushMessage{
    public AbstractPushMessage(long timestamp) {
        this.timestamp = timestamp;
    }

    protected long timestamp;
    public long getTimestamp() {
        return timestamp;
    }
}

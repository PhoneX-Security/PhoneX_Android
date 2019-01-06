package net.phonex.sip;

import net.phonex.db.entity.SipCallSession;

/**
 * Created by dusanklinec on 21.01.16.
 */
public interface SipCallSessionUpdateTask {
    SipCallSession updateCallSession(SipCallSession session);
}

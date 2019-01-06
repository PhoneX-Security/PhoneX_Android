package net.phonex.util;

import net.phonex.db.entity.SipMessage;

/**
 * Interface for registering and unregistering for event reception at some events.
 *
 * Created by dusanklinec on 10.04.15.
 */
public interface Registerable {
    void register();
    void unregister();
}

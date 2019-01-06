package net.phonex.ui.interfaces;

import net.phonex.ui.intro.SerializableLoginParams;

/**
 * Created by miroc on 20.9.14.
 */
public interface OnAccountCreatedListener {
    void onAccountCreated(SerializableLoginParams parameters);
    void onLoginInitiated(SerializableLoginParams parameters);
}

package net.phonex.login;

import net.phonex.soap.PasswordChangeParams;

/**
 * Created by miroc on 24.2.16.
 */
public interface LoginEventsListener {
    void onLoginCancelled(String localizedErrorMessage);

    void onLoginCancelled(String localizedErrorTitle, String localizedErrorMessage);

    void onLoginFinished();

    void passwordChangeRequired(PasswordChangeParams parameters);
}

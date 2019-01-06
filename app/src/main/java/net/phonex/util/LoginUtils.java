package net.phonex.util;

import android.content.Context;

import net.phonex.pref.PreferencesConnector;
import net.phonex.pref.PreferencesManager;
import net.phonex.ui.lock.util.PinHelper;

/**
 * Created by miroc on 20.9.14.
 */
public class LoginUtils {
    private static final String TAG  = "LoginUtils";

    private static final String SIP_LOGIN_REMEMBER =  "intro_login_remember";
    private static final String SIP_LOGIN_LAST =  "intr_login_last";

    public static void rememberLogin(PreferencesManager manager, boolean remember, String login){
        manager.setBoolean(SIP_LOGIN_REMEMBER, remember);
        if (remember){
            manager.setString(SIP_LOGIN_LAST, login);
        }
    }

    public static boolean isLoginRemembered(PreferencesManager manager){
        Boolean rememberLogin = manager.getBoolean(SIP_LOGIN_REMEMBER);
        if (rememberLogin==null){
            return false;
        }
        return rememberLogin;
    }

    /**
     * returns "" if no login is remembered
     * @param manager
     */
    public static String getLastLogin(PreferencesManager manager){
        String string = manager.getString(SIP_LOGIN_LAST);
        return string==null ? null : string;
    }

    /**
     * Procedures required to be done during first login (e.g. clean up after previous users)
     */
    public static void onFirstLogin(Context context){
        Log.vf(TAG, "onFirstLogin: cleaning old user settings");
        // restart settings
        PreferencesConnector prefs = new PreferencesConnector(context);
        prefs.resetToDefaults();
        PinHelper.resetSavedPin(context);
    }

}

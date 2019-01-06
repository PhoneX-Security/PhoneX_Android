package net.phonex.service;

import android.content.Context;
import android.content.Intent;

import net.phonex.autologin.AutoLoginManager;
import net.phonex.core.Intents;
import net.phonex.db.entity.SipProfile;
import net.phonex.login.LoginEventsListener;
import net.phonex.login.QuickLogin;
import net.phonex.soap.PasswordChangeParams;
import net.phonex.login.FullLogin;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.Registerable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manager for handling login. Created to decouple IntroActivity and LoginCascade.
 *
 * Created by Matus on 04-Sep-15.
 */
public class LoginManager implements Registerable {
    private static final String TAG ="LoginManager";

    private final XService xService;
    private final Context context;

    public LoginManager(XService xService) {
        this.xService = xService;
        this.context = xService;
    }

    public void triggerQuickLogin(String sip, String password, String sipDomain, boolean lockPinAfterLogin) {
        Log.vf(TAG, "triggerQuickLogin");

        final QuickLogin quickLogin = new QuickLogin(xService, new XServiceBinder(xService), new BroadcastingLoginEventsListener());
        final int loginCode = quickLogin.login(sip, password, sipDomain);

        // If current identity is invalid, quick login cannot continue, need to regenerate the certificate.
        // Invoke full login.
        if (loginCode == QuickLogin.CODE_INVALID_IDENTITY){
            Log.wf(TAG, "QuickLogin failed - identity is invalid");

            // Trigger new login without Db detection.
            triggerLoginInternal(sip, password, sipDomain, lockPinAfterLogin);
            return;
        }

        // If quick login is OK but DB is not working - full relogin.
        if (loginCode == QuickLogin.CODE_INVALID_PROFILE){
            Log.wf(TAG, "QuickLogin failed - profile is invalid");
            triggerLoginInternal(sip, password, sipDomain, lockPinAfterLogin);
            return;
        }

        // If quick login is triggered, we do not need legacy login data
        AutoLoginManager.deleteLoginData(xService);
    }

    public void triggerLogin(String userSip, String password, String sipDomain, boolean lockPinAfterLogin) {
        Log.vf(TAG, "triggerLogin");
        if (SipProfile.getCurrentProfile(context) != null) {
            Log.d(TAG, "Cannot login, user already logged in");

            Intent intent = new Intent(Intents.ACTION_LOGIN_FINISHED);
            MiscUtils.sendBroadcast(context, intent);
            return;
        }

        triggerLoginInternal(userSip, password, sipDomain, lockPinAfterLogin);
    }

    protected void triggerLoginInternal(String userSip, String password, String sipDomain, boolean lockPinAfterLogin){
        FullLogin fullLogin = new FullLogin(xService, new XServiceBinder(xService), lockPinAfterLogin);
        fullLogin.setLoginEventsListener(new BroadcastingLoginEventsListener());
        fullLogin.login(userSip, password, sipDomain);
    }

    private class BroadcastingLoginEventsListener implements LoginEventsListener {

        @Override
        public void onLoginCancelled(String localizedErrorMessage) {
            Log.df(TAG, "onLoginCancelled %s", localizedErrorMessage);

//            loginInProgress.set(false);

            Intent intent = new Intent(Intents.ACTION_LOGIN_CANCELLED);
            intent.putExtra(Intents.EXTRA_LOGIN_CANCELLED_ERROR_MESSAGE, localizedErrorMessage);
            MiscUtils.sendBroadcast(context, intent);
        }

        @Override
        public void onLoginCancelled(String localizedErrorTitle, String localizedErrorMessage) {
            Log.df(TAG, "onLoginCancelled %s %s", localizedErrorTitle, localizedErrorMessage);

//            loginInProgress.set(false);

            Intent intent = new Intent(Intents.ACTION_LOGIN_CANCELLED);
            intent.putExtra(Intents.EXTRA_LOGIN_CANCELLED_ERROR_TITLE, localizedErrorTitle);
            intent.putExtra(Intents.EXTRA_LOGIN_CANCELLED_ERROR_MESSAGE, localizedErrorMessage);
            MiscUtils.sendBroadcast(context, intent);
        }

        @Override
        public void onLoginFinished() {
            Log.df(TAG, "onLoginFinished");

//            loginInProgress.set(false);

            Intent intent = new Intent(Intents.ACTION_LOGIN_FINISHED);
            MiscUtils.sendBroadcast(context, intent);

//            Intent phonexIntent;
//            phonexIntent = new Intent(xService, PhonexActivity.class);
//            phonexIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//            phonexIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
//            phonexIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            xService.startActivity(phonexIntent);
        }

        @Override
        public void passwordChangeRequired(PasswordChangeParams parameters) {
            Log.df(TAG, "passwordChangeRequired");

            Intent intent = new Intent(Intents.ACTION_LOGIN_PASSWORD_CHANGE);
            intent.putExtra(Intents.EXTRA_LOGIN_PASSWORD_CHANGE_PARAMS, parameters);
            MiscUtils.sendBroadcast(context, intent);
        }
    }

    @Override
    public void register() {

    }

    @Override
    public void unregister() {

    }
}

package net.phonex.autologin;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.NonNull;

import net.phonex.autologin.exceptions.NotSupportedException;
import net.phonex.autologin.exceptions.PasswordPersisterException;
import net.phonex.autologin.exceptions.ServiceUnavailableException;
import net.phonex.autologin.soap.AuthStateFetchCall;
import net.phonex.autologin.soap.AuthStateSaveCall;
import net.phonex.autologinQuick.QuickLoginPersister;
import net.phonex.core.Intents;
import net.phonex.core.MemoryPrefManager;

import net.phonex.db.entity.SipProfile;
import net.phonex.pub.a.Compatibility;
import net.phonex.pub.parcels.StoredCredentials;
import net.phonex.service.SafeNetService;
import net.phonex.service.XService;
import net.phonex.soap.TaskWithCallback;
import net.phonex.util.DefaultServiceConnector;
import net.phonex.util.Log;
import net.phonex.util.android.StatusbarNotifications;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Schedules saving of authentication state to server for autologin after app update.
 * Created by Matus on 02-Sep-15.
 */
public class AutoLoginManager {

    private static final String TAG = "AutoLoginManager";

    private static final long LOGIN_SAVING_PERIOD = 12*60*60*1000; // save after login and then once every 12 hours

    private XService svc;

    private ExecutorService executor;

    private AtomicBoolean retrySaveLogin;

    private static AtomicBoolean retryLogin = new AtomicBoolean(false);
    private static AtomicBoolean loginInProgress = new AtomicBoolean(false);

    public AutoLoginManager(XService svc) {
        this.svc = svc;
        this.executor = Executors.newSingleThreadExecutor();
        retrySaveLogin = new AtomicBoolean(false);
    }

    public void scheduleAlarm(long timeout) {
        svc.setAlarm(Intents.ALARM_AUTO_LOGIN_SAVE, timeout);
    }

    public void triggerAuthStateSave() {
        StoredCredentials creds = MemoryPrefManager.loadCredentials(svc);
        if (creds == null || creds.getUsrPass() == null) {
            Log.d(TAG, "No password in memory");
            return;
        }
        SipProfile profile = SipProfile.getCurrentProfile(svc);
        if (profile == null || profile.getSip() == null) {
            Log.d(TAG, "No user sip in memory");
            return;
        }

        if (Compatibility.isApiGreaterOrEquals(18)){
            // Quick login (only once, locally only)
            saveState18(creds);

        } else {
            // Old way (periodically, locally with cooperation of the server)
            saveState14(profile, creds);
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void saveState18(@NonNull StoredCredentials creds){
        executor.submit((Runnable) () -> {
            QuickLoginPersister persister = new QuickLoginPersister(svc);
            String sip = creds.getUserSip();
            try {
                persister.storeCredentials(LoginCredentials.from(creds.getUsrPass(), sip));
            } catch (PasswordPersisterException e) {
                Log.ef(TAG, e, "Unable to store login credentials for quick login");
            }

            // when migrating, we want to delete legacy saveState data (as stored in saveState14())
            deleteLoginData(svc);

            // do not plan any alarm here - store quick login only once
        });
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void saveState14(@NonNull SipProfile profile, @NonNull StoredCredentials creds){
        TaskWithCallback task = new TaskWithCallback(new AuthStateSaveCall(svc, creds.getUsrPass().getBytes(), profile.getSip().getBytes()), new TaskWithCallback.Callback() {
            private static final String TAG_TASK = "AutoLoginManager.triggerAuthStateSave()";

            @Override
            public void onCompleted() {
                Log.df(TAG_TASK, "onCompleted");
                scheduleAlarm(LOGIN_SAVING_PERIOD);
            }

            @Override
            public void onFailed(Exception e) {
                Log.df(TAG_TASK, "onFailed");
                if (e == null) {
                    Log.d(TAG, "No exception set for failed AuthStateFetchCall");
                } else if (e instanceof NotSupportedException) {
                    Log.df(TAG, "This device does not support the auto login mechanism, do not plan any more attempts (%s)", e.getMessage());
                } else if (e instanceof ServiceUnavailableException) {
                    Log.df(TAG, e, "Service is temporarily unavailable, may try later");
                    retrySaveLogin.set(true);
                } else {
                    Log.df(TAG, e, "AuthStateFetchCall failed");
                }
            }
        });
        executor.submit(task);
    }

//    public void triggerSaveLoginOnConnectivityChange() {
//        if (retrySaveLogin.compareAndSet(true, false)) {
//            Log.d(TAG, "triggerSaveLoginOnConnectivityChange, save login needed");
//            triggerAuthStateSave();
//        } else {
//            Log.d(TAG, "triggerSaveLoginOnConnectivityChange, save login not needed");
//        }
//    }

    public static void triggerLoginFromSavedState(Context context) {
        Log.df(TAG, "triggerLoginFromSavedState");

        if (!loginInProgress.compareAndSet(false, true)){
            Log.wf(TAG, "Login already in progress");
            return;
        }

        try {
            triggerLogin(context);
        } finally {
            loginInProgress.set(false);
        }
    }

    /**
     * Login internals
     * @param context
     */
    private static void triggerLogin(Context context){
        if (SipProfile.getCurrentProfile(context) != null) {
            Log.df(TAG, "User already logged in");
            return;
        }

        PasswordPersister passwordPersister = new AndroidKeyStorePasswordPersister(context);

        if (!passwordPersister.isKeyStoreAvailable() || !passwordPersister.isKeyPairAvailable()) {
            Log.d(TAG, "KeyStore or KeyPair in KS is not available");
            showUpdateNotification(context);
            return;
        }

        LoginStorage loginStorage = new FileSystemLoginStorage(context);
        if (!loginStorage.isLoginAvailable()) {
            Log.d(TAG, "Login data are not available");
            showUpdateNotification(context);
            return;
        }

        final TaskWithCallback task = new TaskWithCallback(new AuthStateFetchCall(context.getApplicationContext()), new TaskWithCallback.Callback() {
            private static final String TAG_TASK = "AutoLoginManager.triggerLoginFromSavedState()";

            @Override
            public void onCompleted() {
                Log.df(TAG_TASK, "onCompleted - password was recovered and login broadcast sent");
            }

            @Override
            public void onFailed(Exception e) {
                Log.df(TAG_TASK, "onFailed");

                showUpdateNotification(context);

                if (e == null) {
                    Log.d(TAG, "No exception set for failed AuthStateFetchCall");
                } else if (e instanceof NotSupportedException) {
                    Log.df(TAG, "This device does not support the auto login mechanism, do not plan any more attempts (%s)", e.getMessage());
                } else if (e instanceof ServiceUnavailableException) {
                    Log.df(TAG, e, "Service is temporarily unavailable, may try later");
                    retryLogin.set(true);
                } else {
                    Log.df(TAG, e, "AuthStateFetchCall failed");
                }
            }
        });

        SafeNetService.start(context, true);
        XService.start(context, true);

        final DefaultServiceConnector connector = new DefaultServiceConnector();
        connector.setListener(new DefaultServiceConnector.ServiceConnectorListener() {
            @Override
            public void onXServiceConnected(ComponentName arg0, IBinder arg1) {
                Log.d(TAG, "onXServiceConnected");
                connector.disconnectService(context.getApplicationContext());
                Executors.newSingleThreadExecutor().submit(task);
            }

            @Override
            public void onXServiceDisconnected(ComponentName arg0) {
                Log.d(TAG, "onXServiceDisconnected");
            }
        });
        Log.d(TAG, "Connecting to XService");
        connector.connectService(context.getApplicationContext(), false);
    }

    private static void showUpdateNotification(Context context) {
        StatusbarNotifications.buildAndNotifyApplicationUpdate(context);
    }

    public static void deleteLoginData(Context context) {
        Log.d(TAG, "deleteLoginData");
        FileSystemLoginStorage storage = new FileSystemLoginStorage(context);
        storage.deleteLogin();
    }
}

package net.phonex.autologinQuick;

import android.content.ComponentName;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;

import net.phonex.core.IService;
import net.phonex.util.DefaultServiceConnector;
import net.phonex.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

public class AutoQuickLoginBinder {
    private static final String TAG = "AutoQuickLoginBinder";
    private static AtomicBoolean loginInProgress = new AtomicBoolean(false);

    public static void triggerQuickLogin(Context context, String sip, String password, String domain) {
        Log.df(TAG, "triggerQuickLogin");

        if (!loginInProgress.compareAndSet(false, true)){
            Log.wf(TAG, "Login already in progress");
            return;
        }

        try {
            triggerLogin(context, sip, password, domain);
        } finally {
            loginInProgress.set(false);
        }
    }

    /**
     * Login internals
     * @param context
     * @param sip
     * @param password
     * @param domain
     */
    private static void triggerLogin(Context context, final String sip, final String password, final String domain){
        // DO NOT CHECK LOGGED PROFILE HERE - database is already opened, it
//        if (SipProfile.getCurrentProfile(context) != null) {
//            Log.df(TAG, "User already logged in");
//            return;
//        }

//        SafeNetService.start(context, true);
//        XService.start(context, true);

        final DefaultServiceConnector connector = new DefaultServiceConnector();
        connector.setListener(new DefaultServiceConnector.ServiceConnectorListener() {
            @Override
            public void onXServiceConnected(ComponentName arg0, IBinder arg1) {
                Log.d(TAG, "onXServiceConnected");
                IService service = connector.getService();
                try {
                    service.quickLogin(sip, password, domain);
                } catch (RemoteException e) {
                    Log.ef(TAG, e, "Unable to perform quick login.");
                }
                connector.disconnectService(context.getApplicationContext());
            }

            @Override
            public void onXServiceDisconnected(ComponentName arg0) {
                Log.d(TAG, "onXServiceDisconnected");
            }
        });
        Log.d(TAG, "Connecting to XService");
        connector.connectService(context.getApplicationContext(), false);
    }
}

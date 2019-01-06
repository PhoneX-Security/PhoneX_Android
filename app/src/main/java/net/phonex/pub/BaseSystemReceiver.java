package net.phonex.pub;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import net.phonex.autologin.AutoLoginManager;
import net.phonex.core.Intents;
import net.phonex.pref.PhonexConfig;
import net.phonex.pref.PreferencesConnector;
import net.phonex.service.XService;
import net.phonex.util.Log;
import net.phonex.util.android.StatusbarNotifications;

/**
 * Catches system events such as ACTION_BOOT_COMPLETED, CONNECTIVITY_ACTION.
 */
public class BaseSystemReceiver extends BroadcastReceiver {
    private static final String TAG = "BaseSystemReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String intentAction = intent.getAction();

        PreferencesConnector prefWrapper = new PreferencesConnector(context);
        final boolean appWasClosed = prefWrapper.getBoolean(PhonexConfig.APP_CLOSED_BY_USER, false);

        if (Intent.ACTION_BOOT_COMPLETED.equals(intentAction)) {
            Log.d(TAG, "Action boot completed");
            if (prefWrapper.isValidConnectionForIncoming() && !appWasClosed) {
                Log.d(TAG, "Starting service if not started.");
                Intent sip_service_intent = new Intent(context, XService.class);
                sip_service_intent.putExtra(Intents.EXTRA_ON_BOOT, true);
                context.startService(sip_service_intent);
            }

            if (!appWasClosed){
                StatusbarNotifications notifications = new StatusbarNotifications(context);
                notifications.notifyDeviceRebooted();
            }

        } else if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intentAction)){
        	// Connectivity action, do not start XService always, only if
        	// there is active data connection. This code originally called 
        	// startService() no matter what, now start service only 
        	// if there is data channel connected.
        	//
        	ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = cm.getActiveNetworkInfo();
            boolean connected = (info != null && info.isConnected() && !appWasClosed);
            String networkType = connected ? info.getTypeName() : "null";
            
            Log.df(TAG, "Connectivity_action; connected=%s; networkType=[%s] quit=%s; info=%s",
                    connected, 
                    networkType,
                    appWasClosed,
                    info);

            if (connected){
            	Log.d(TAG, "Try to start service if not already started");
                Intent sip_service_intent = new Intent(context, XService.class);
                sip_service_intent.putExtra(Intents.EXTRA_ON_BOOT, false);
                context.startService(sip_service_intent);

                // PHON-678 Do not autologin once it was not able to autologin because of connectivity problem before
//                AutoLoginManager.triggerLoginOnConnectivityChange(context);
            }
        } else if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intentAction)) { // works API 12+, we do not care about the older API
            Log.df(TAG, "intent %s", Intent.ACTION_MY_PACKAGE_REPLACED);

            // only show notification about update if auto login fails
            // if auto login succeeds, there is still the google play notification about update
            //StatusbarNotifications.buildAndNotifyApplicationUpdate(context);

            AutoLoginManager.triggerLoginFromSavedState(context);
        }
    }

}

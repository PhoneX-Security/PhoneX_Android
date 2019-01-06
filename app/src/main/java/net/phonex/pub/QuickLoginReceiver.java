package net.phonex.pub;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import net.phonex.autologinQuick.AutoQuickLoginBinder;
import net.phonex.core.Intents;
import net.phonex.util.Log;

/**
 * Currently receiver for catching login intent and passing it to service via AutoQuickLoginBinder
 * Created by miroc on 9.4.16.
 */
public class QuickLoginReceiver extends BroadcastReceiver{
    private static final String TAG = "QuickLoginReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        switch (action) {
            case Intents.ACTION_TRIGGER_QUICK_LOGIN:
                Log.d(TAG, "ACTION_TRIGGER_QUICK_LOGIN received");

                final String sip = intent.getStringExtra(Intents.EXTRA_LOGIN_SIP);
                final String password = intent.getStringExtra(Intents.EXTRA_LOGIN_PASSWORD);
                final String domain = intent.getStringExtra(Intents.EXTRA_LOGIN_DOMAIN);
                AutoQuickLoginBinder.triggerQuickLogin(context, sip, password, domain);

                break;
            default:
                Log.wf(TAG, "Unregistered action: [%s]", action);
                break;
        }
    }
}

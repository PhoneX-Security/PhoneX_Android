package net.phonex.soap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import net.phonex.core.Intents;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;

/**
 * Created by Matus on 10-Sep-15.
 */
public class LoginCancelledBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "LoginCancelledBroadcastReceiver";

    public interface LoginCancelledListener {
        public void loginCancelled();
    }

    private LoginCancelledListener listener;

    public LoginCancelledBroadcastReceiver(LoginCancelledListener listener) {
        this.listener = listener;
    }

    public void register(Context context) {
        MiscUtils.registerReceiver(context, this, new IntentFilter(Intents.ACTION_CANCEL_LOGIN));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            Log.d(TAG, "onReceive with null intent");
            return;
        }
        if (Intents.ACTION_CANCEL_LOGIN.equals(intent.getAction())) {
            if (listener != null) {
                Log.d(TAG, "onReceive ACTION_CANCEL_LOGIN");
                listener.loginCancelled();
            } else {
                Log.d(TAG, "onReceive with null listener");
            }
        } else {
            Log.wf(TAG, "onReceive with unknown action [%s]", intent.getAction());
        }
    }
}

package net.phonex.events;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import net.phonex.ui.interfaces.OnConnectivityChanged;
import net.phonex.util.Log;

/**
 * Created by miroc on 22.9.14.
 * listens for ConnectivityManager.CONNECTIVITY_ACTION
 */
public class NetworkStateReceiver extends BroadcastReceiver {
    private static final String TAG = "NetworkStateReceiver";

    private OnConnectivityChanged listener;

    public NetworkStateReceiver(){

    }

    public NetworkStateReceiver(OnConnectivityChanged listener) {
        this.listener = listener;
    }

    public void setListener(OnConnectivityChanged listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String intentAction = intent.getAction();

        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intentAction)){

            ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = cm.getActiveNetworkInfo();
            boolean connected = (info != null && info.isConnected());
            String networkType = connected ? info.getTypeName() : "null";

            Log.df(TAG, "Connectivity_action; connected=%s; networkType=[%s]; info=%s",
                    connected,
                    networkType,
                    info);

            if (listener!=null){
                if (connected){
                    listener.onNetworkConnected();
                } else {
                    listener.onNetworkDisconnected();
                }
            }
        }
    }


}

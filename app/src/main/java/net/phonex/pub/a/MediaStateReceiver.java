package net.phonex.pub.a;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;

import net.phonex.util.Log;

import java.lang.ref.WeakReference;

/**
 * Media change listener - used for bluetooth headsets integration.
 * Created by dusanklinec on 17.12.15.
 */
public class MediaStateReceiver extends BroadcastReceiver {
    private static final String TAG = "MediaStateReceiver";
    private final WeakReference<BluetoothManager> bluetoothManager;

    public MediaStateReceiver(BluetoothManager bluetoothManager) {
        this.bluetoothManager = new WeakReference<BluetoothManager>(bluetoothManager);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        Log.df(TAG, "Media SCO updated, action: %s", action);
        if (!AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED.equals(action) &&
            !AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED.equals(action))
        {
            return;
        }

        final BluetoothManager mgr = bluetoothManager.get();
        if (mgr == null){
            Log.w(TAG, "Event received, but manager is null");
            return;
        }

        final boolean useBluetooth = mgr.isUseBluetooth();
        final int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_ERROR);
        Log.df(TAG, "Media SCO state: %s, use bluetooth: %s", state, useBluetooth);

        mgr.onMediaStateChange(state);
    }
}

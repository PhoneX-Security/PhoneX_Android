package net.phonex.util.android;

import android.content.ComponentName;
import android.media.AudioManager;

import net.phonex.pub.HeadsetButtonBroadcastReceiver;
import net.phonex.pub.a.Compatibility;
import net.phonex.service.XService;
import net.phonex.util.Log;


public class AudioFocusHelper {
    private static final String TAG = "AudioFocusHelper";

    private AudioManager audioManager;
    private XService svc;
    private ComponentName headsetButtonRecvName;
    private boolean hasFocus = false;

	public AudioFocusHelper() {

    }

    public void init(XService service, AudioManager manager) {
        this.svc = service;
        audioManager = manager;
        headsetButtonRecvName = new ComponentName(this.svc.getPackageName(), HeadsetButtonBroadcastReceiver.class.getName());
    }

    /**
     * Requests audio focus.
     * @param requestBluetooth
     */
    public void acquireFocus(boolean requestBluetooth) {
        Log.df(TAG, "Audio focus acquired: %s", hasFocus);
        if(!hasFocus) {
            HeadsetButtonBroadcastReceiver.setService(svc.getPjCallback());
            audioManager.registerMediaButtonEventReceiver(headsetButtonRecvName);
            audioManager.requestAudioFocus(onFocusChangeListener, Compatibility.getInCallStream(requestBluetooth), AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            hasFocus = true;
        }
    }

    /**
     * Releases audio focus.
     */
    public void releaseFocus() {
        if(hasFocus) {
            HeadsetButtonBroadcastReceiver.setService(null);
            audioManager.unregisterMediaButtonEventReceiver(headsetButtonRecvName);
            audioManager.abandonAudioFocus(onFocusChangeListener);
            hasFocus = false;
        }
    }

    private AudioManager.OnAudioFocusChangeListener onFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            Log.v(TAG, "Audio focus changed");
        }
    };

}

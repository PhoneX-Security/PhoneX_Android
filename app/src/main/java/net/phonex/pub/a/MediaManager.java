package net.phonex.pub.a;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.NetworkInfo.DetailedState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.widget.Toast;

import net.phonex.R;
import net.phonex.core.Intents;
import net.phonex.core.MediaState;
import net.phonex.pref.PhonexConfig;
import net.phonex.service.SvcRunnable;
import net.phonex.service.XService;
import net.phonex.service.XService.SameThreadException;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.android.AccessibilitySvcManager;
import net.phonex.util.android.AudioFocusHelper;

public class MediaManager implements MediaDeviceChangeListener {
    public static final int TONE_NONE = 0;
    public static final int TONE_CALL_WAITING = 1;
    public static final int TONE_BUSY = 2;
    public static final int TONE_CONGESTION = 3;
    public static final int TONE_BATTERY_LOW = 4;
    public static final int TONE_CALL_ENDED = 5;
    private static final String TAG = "MediaManager";
    private static final String ACTION_AUDIO_VOLUME_UPDATE = "org.openintents.audio.action_volume_update";
    private static final String EXTRA_STREAM_TYPE = "org.openintents.audio.extra_stream_type";
    private static final String EXTRA_VOLUME_INDEX = "org.openintents.audio.extra_volume_index";
    private static final String EXTRA_RINGER_MODE = "org.openintents.audio.extra_ringer_mode";
    private static final int EXTRA_VALUE_UNKNOWN = -9999;

    private int modeSipInCall = AudioManager.MODE_NORMAL;
    private final XService service;
    private final AudioManager audioManager;
    private final AudioBell audioBell;
    private final BluetoothManager bluetoothManager;

    //Locks
    private WifiLock wifiLock;
    private WakeLock screenLock;

    // Media settings to save / restore
    private boolean isSetAudioMode = false;

    //By default we assume user want bluetooth.
    //If bluetooth is not available connection will never be done and then
    //UI will not show bluetooth is activated
    private boolean userWantBluetooth = false;
    private boolean userWantSpeaker = false;
    private boolean userWantMicrophoneMute = false;
    private boolean restartAudioWhenRoutingChange = true;
    private boolean focusAcquired = false;

    private int previousBtState = -1;
    private boolean userClickedBtToTurnOn = false;

    private Intent mediaStateChangedIntent;
    private AudioFocusHelper audioFocusHelper;
    private AccessibilitySvcManager accessibilityManager;
    private SharedPreferences prefs;
    private boolean useWebRTCImpl = false;
    private boolean doFocusAudio = true;
    private boolean startBeforeInit;

    public MediaManager(XService aService) {
        service = aService;
        audioManager = (AudioManager) service.getSystemService(Context.AUDIO_SERVICE);
        prefs = service.getSharedPreferences("audio", Context.MODE_PRIVATE);
        //prefs = PreferenceManager.getDefaultSharedPreferences(service);
        accessibilityManager = new AccessibilitySvcManager();
        accessibilityManager.init(service);

        audioBell = new AudioBell(service);
        mediaStateChangedIntent = new Intent(Intents.ACTION_SIP_MEDIA_CHANGED);
        bluetoothManager = new BluetoothManager(aService);
        //Try to reset if there were a crash in a call could restore previous settings
        restoreAudioState();
    }

    public void startService() {
        if (audioFocusHelper == null) {
            audioFocusHelper = new AudioFocusHelper();
            audioFocusHelper.init(service, audioManager);
        }

        bluetoothManager.setMediaChangesListener(this);
        bluetoothManager.register();

        modeSipInCall = service.getPrefs().getAudioMode();
        useWebRTCImpl = service.getPrefs().getBoolean(PhonexConfig.USE_WEBRTC_HACK);
        doFocusAudio = service.getPrefs().getBoolean(PhonexConfig.ENABLE_FOCUS_AUDIO);
        userWantBluetooth = service.getPrefs().getBoolean(PhonexConfig.BLUETOOTH_DEFAULT_ON);
        userWantSpeaker = service.getPrefs().getBoolean(PhonexConfig.AUTO_CONNECT_SPEAKER);
        restartAudioWhenRoutingChange = service.getPrefs().getBoolean(PhonexConfig.RESTART_AUDIO_ON_ROUTING_CHANGES);
        startBeforeInit = service.getPrefs().getBoolean(PhonexConfig.SETUP_AUDIO_BEFORE_INIT);
    }

    public void stopService() {
        bluetoothManager.setMediaChangesListener(null);
        bluetoothManager.unregister();
        Log.i(TAG, "Remove media manager....");
    }

    private int getAudioTargetMode() {
        int targetMode = modeSipInCall;

        if (service.getPrefs().useModeApi()) {
            Log.df(TAG, "User want speaker now...%s", userWantSpeaker);
            if (!service.getPrefs().generateForSetCall()) {
                return userWantSpeaker ? AudioManager.MODE_NORMAL : AudioManager.MODE_IN_CALL;
            } else {
                return userWantSpeaker ? AudioManager.MODE_IN_CALL : AudioManager.MODE_NORMAL;
            }
        }

        if (shouldRouteBluetooth()) {
            targetMode = AudioManager.MODE_NORMAL;
        }

        Log.df(TAG, "Target mode... : %s", targetMode);
        return targetMode;
    }

    public int validateAudioClockRate(int clockRate) {
        return 0;
    }

    public void setAudioInCall(boolean beforeInit) {
        if (!beforeInit || (beforeInit && startBeforeInit)) {
            actualSetAudioInCall();
        }
    }

    public void unsetAudioInCall() {
        actualUnsetAudioInCall();
    }

    /**
     * Set the audio mode as in call
     */
    @SuppressWarnings("deprecation")
    private synchronized void actualSetAudioInCall() {
        //Ensure not already set
        if (isSetAudioMode) {
            return;
        }
        Log.vf(TAG, "actualSetAudioInCall");
        stopRing();
        saveAudioState();

        //LOCK
        //Wifi management if necessary
        ContentResolver ctntResolver = service.getContentResolver();
        MiscUtils.setWifiSleepPolicy(ctntResolver, MiscUtils.getWifiSleepPolicyNever());

        //Acquire wifi lock
        WifiManager wman = (WifiManager) service.getSystemService(Context.WIFI_SERVICE);
        if (wifiLock == null) {
            wifiLock = wman.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "net.phonex.legacy.InCallLock");
            wifiLock.setReferenceCounted(false);
        }

        WifiInfo winfo = wman.getConnectionInfo();
        if (winfo != null) {
            DetailedState dstate = WifiInfo.getDetailedStateOf(winfo.getSupplicantState());
            //We assume that if obtaining ip addr, we are almost connected so can keep wifi lock
            if (dstate == DetailedState.OBTAINING_IPADDR || dstate == DetailedState.CONNECTED) {
                if (!wifiLock.isHeld()) {
                    wifiLock.acquire();
                }
            }

            //This wake lock purpose is to prevent PSP wifi mode
            if (service.getPrefs().getBoolean(PhonexConfig.KEEP_AWAKE_IN_CALL)) {
                if (screenLock == null) {
                    PowerManager pm = (PowerManager) service.getSystemService(Context.POWER_SERVICE);
                    screenLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "net.phonex.onIncomingCall.SCREEN");
                    screenLock.setReferenceCounted(false);
                }
                //Ensure single lock
                if (!screenLock.isHeld()) {
                    screenLock.acquire();

                }

            }
        }


        if (!useWebRTCImpl) {
            // Audio routing
            int targetMode = getAudioTargetMode();
            Log.df(TAG, "Set mode audio in call to %s", targetMode);

            if (service.getPrefs().generateForSetCall()) {
                boolean needOutOfSilent = (audioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT);
                if (needOutOfSilent) {
                    audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                }
                ToneGenerator toneGenerator = new ToneGenerator(AudioManager.STREAM_VOICE_CALL, 1);
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_CONFIRM);
                toneGenerator.stopTone();
                toneGenerator.release();

                // Restore silent mode
                if (needOutOfSilent) {
                    audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                }
            }

            Log.vf(TAG, "Audio manager set mode: %s", targetMode);
            audioManager.setMode(targetMode);

            // Routing
            if (service.getPrefs().useRoutingApi()) {
                Log.d(TAG, "Routing API is deprecated");
                audioManager.setRouting(targetMode, userWantSpeaker ? AudioManager.ROUTE_SPEAKER : AudioManager.ROUTE_EARPIECE, AudioManager.ROUTE_ALL);

            } else {
                audioManager.setSpeakerphoneOn(userWantSpeaker);
            }

            audioManager.setMicrophoneMute(false);

            final boolean shouldUseBt = shouldRouteBluetooth();
            Log.vf(TAG, "Bluetooth wanted: %s, should route BT: %s", userWantBluetooth, shouldUseBt);
            if(shouldUseBt) {
                Log.d(TAG, "Trying to enable bluetooth");
                bluetoothManager.setBluetoothOn(true);
            }
        } else {
            // WebRTC routing
            audioManager.setSpeakerphoneOn(userWantSpeaker);
        }

        //Set stream solo/volume/focus
        final boolean shouldUseBt = shouldRouteBluetooth();
        final int inCallStream = Compatibility.getInCallStream(shouldUseBt);
        if (doFocusAudio) {
            if (!accessibilityManager.isEnabled()) {
                audioManager.setStreamSolo(inCallStream, true);
            }
            audioFocusHelper.acquireFocus(shouldUseBt);
            focusAcquired = true;
        }

        Log.df(TAG, "Initial volume level : %s", service.getPrefs().getInitialVolumeLevel());
        setStreamVolume(inCallStream,
                (int) (audioManager.getStreamMaxVolume(inCallStream) * service.getPrefs().getInitialVolumeLevel()),
                0);


        isSetAudioMode = true;
        //	System.gc();
    }

    /**
     * Returns true if user wants bluetooth to be enabled and bluetooth is supported.
     * @return
     */
    private boolean shouldRouteBluetooth(){
        return userWantBluetooth && bluetoothManager.isBluetoothSupported();
    }

    /**
     * Changes audio focus settings as routing changes.
     * @param newBluetoothState
     */
    private synchronized void refocus(boolean newBluetoothState){
        if (newBluetoothState == userWantBluetooth || !focusAcquired || !doFocusAudio){
            return;
        }

        final boolean shouldUseBt = shouldRouteBluetooth();
        Log.vf(TAG, "Refocus call, old BT state: %s, new BT state: %s, should use: %s", userWantBluetooth, newBluetoothState, shouldUseBt);

        //Set stream solo/volume/focus
        final int oldStream = Compatibility.getInCallStream(userWantBluetooth);
        final int newStream = Compatibility.getInCallStream(newBluetoothState);

        // Set solo to false on the old stream, release focus on old stream.
        if (!accessibilityManager.isEnabled()) {
            audioManager.setStreamSolo(oldStream, false);
        }
        audioFocusHelper.releaseFocus();

        // Set solo to true on a new stream, acquire focus on a new state.
        if (!accessibilityManager.isEnabled()) {
            audioManager.setStreamSolo(newStream, true);
        }
        audioFocusHelper.acquireFocus(newBluetoothState);
    }

    /**
     * Save current audio mode in order to be able to restore it once done
     */
    @SuppressWarnings("deprecation")
    private synchronized void saveAudioState() {
        if (prefs.getBoolean("isSavedAudioState", false)) {
            //If we have already set, do not set it again !!!
            return;
        }
        ContentResolver ctntResolver = service.getContentResolver();

        Editor ed = prefs.edit();
        ed.putInt("savedWifiPolicy", MiscUtils.getWifiSleepPolicy(ctntResolver));

        int inCallStream = Compatibility.getInCallStream(shouldRouteBluetooth());
        ed.putInt("savedVolume", audioManager.getStreamVolume(inCallStream));

        int targetMode = getAudioTargetMode();
        if (service.getPrefs().useRoutingApi()) {
            ed.putInt("savedRoute", audioManager.getRouting(targetMode));
        } else {
            ed.putBoolean("savedSpeakerPhone", audioManager.isSpeakerphoneOn());
        }
        ed.putInt("savedMode", audioManager.getMode());

        ed.putBoolean("isSavedAudioState", true);
        ed.commit();
    }

    /**
     * Restore the state of the audio
     */
    @SuppressWarnings("deprecation")
    private synchronized void restoreAudioState() {
        if (!prefs.getBoolean("isSavedAudioState", false)) {
            //If we have NEVER set, do not try to reset !
            return;
        }

        ContentResolver ctntResolver = service.getContentResolver();

        MiscUtils.setWifiSleepPolicy(ctntResolver, prefs.getInt("savedWifiPolicy", MiscUtils.getWifiSleepPolicyDefault()));

        int inCallStream = Compatibility.getInCallStream(shouldRouteBluetooth());
        setStreamVolume(inCallStream, prefs.getInt("savedVolume", (int) (audioManager.getStreamMaxVolume(inCallStream) * 0.8)), 0);

        int targetMode = getAudioTargetMode();
        if (service.getPrefs().useRoutingApi()) {
            audioManager.setRouting(targetMode, prefs.getInt("savedRoute", AudioManager.ROUTE_SPEAKER), AudioManager.ROUTE_ALL);
        } else {
            audioManager.setSpeakerphoneOn(prefs.getBoolean("savedSpeakerPhone", false));
        }

        final int audioMode = prefs.getInt("savedMode", AudioManager.MODE_NORMAL);
        Log.vf(TAG, "Setting audio mode: %s", audioMode);
        audioManager.setMode(audioMode);

        Editor ed = prefs.edit();
        ed.putBoolean("isSavedAudioState", false);
        ed.commit();
    }

    /**
     * Reset the audio mode
     */
    private synchronized void actualUnsetAudioInCall() {

        if (!prefs.getBoolean("isSavedAudioState", false) || !isSetAudioMode) {
            return;
        }

        Log.d(TAG, "Unset Audio In call");

        final boolean shouldUseBt = shouldRouteBluetooth();
        final int inCallStream = Compatibility.getInCallStream(shouldUseBt);
        bluetoothManager.setBluetoothOn(false);
        audioManager.setMicrophoneMute(false);
        if (doFocusAudio) {
            audioManager.setStreamSolo(inCallStream, false);
            audioFocusHelper.releaseFocus();
            focusAcquired = false;
        }
        restoreAudioState();

        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
        }
        if (screenLock != null && screenLock.isHeld()) {
            Log.d(TAG, "Release screen lock");
            screenLock.release();
        }


        isSetAudioMode = false;
    }

    /**
     * Start ringing announce for a given contact.
     * It will also focus audio for us.
     *
     * @param remoteContact the contact to ring for. May resolve the contact ringtone if any.
     */
    synchronized public void startRing(String remoteContact) {
        saveAudioState();
        
        // Reset preferences
        userWantBluetooth = service.getPrefs().getBoolean(PhonexConfig.BLUETOOTH_DEFAULT_ON);
        userWantSpeaker = service.getPrefs().getBoolean(PhonexConfig.AUTO_CONNECT_SPEAKER);
        if (!audioBell.isRinging()) {
            Log.vf(TAG, "Start ringing...");
            audioBell.ring(remoteContact, service.getPrefs().getRingtone());
        } else {
            Log.d(TAG, "Already ringing ....");
        }

    }

    /**
     * Stop all ringing. <br/>
     * Warning, this will not unfocus audio.
     */
    synchronized public void stopRing() {
        if (audioBell.isRinging()) {
            Log.vf(TAG, "Stop ringing...");
            audioBell.stopRing();
        }
    }

    /**
     * Stop call announcement.
     */
    public void stopRingAndUnfocus() {
        Log.vf(TAG, "Stop ringing & unfocus...");
        stopRing();
        audioFocusHelper.releaseFocus();
        focusAcquired = false;
    }

    public void resetSettings() {
        userWantBluetooth = service.getPrefs().getBoolean(PhonexConfig.BLUETOOTH_DEFAULT_ON);
        userWantSpeaker = service.getPrefs().getBoolean(PhonexConfig.AUTO_CONNECT_SPEAKER);
        userWantMicrophoneMute = false;

        previousBtState = -1;
        userClickedBtToTurnOn = false;
        Log.vf(TAG, "Settings reset from prefs. AutoSpeaker: %s, AutoBT: %s", userWantSpeaker, userWantBluetooth);
    }

    public void toggleMute() throws SameThreadException {
        setMicrophoneMute(!userWantMicrophoneMute);
    }

    public void setMicrophoneMute(boolean on) {
        //if(on != userWantMicrophoneMute ) {
        userWantMicrophoneMute = on;
        setSoftwareVolume();
        broadcastMediaChanged();
        //}
    }

    /**
     * Reinitializes sound settings.
     * @throws SameThreadException
     */
    public void reinitSound() throws SameThreadException {
        if (service != null) {
            Log.vf(TAG, "Sound restart");
            service.setNoSnd();
            service.setSnd();
            broadcastMediaChanged();
        }
    }

    /**
     * Called by user to enable/disable bluetooth.
     * @param on
     * @throws SameThreadException
     */
    public void setSpeakerphoneOn(boolean on) throws SameThreadException {
        setSpeakerphoneOn(on, true);
    }

    /**
     * Called by user to enable/disable bluetooth.
     * @param on
     * @throws SameThreadException
     */
    private void setSpeakerphoneOn(boolean on, boolean notify) throws SameThreadException {
        // If bluetooth is enabled, it has to be turned off.
        if (on && shouldRouteBluetooth()){
            Log.vf(TAG, "Enabling loudspeaker has to turn off bluetooth mode.");
            setBluetoothOn(false, false);
        }

        if (service != null && restartAudioWhenRoutingChange && !audioBell.isRinging()) {
            Log.vf(TAG, "With sound restart");
            service.setNoSnd();
            userWantSpeaker = on;
            service.setSnd();

        } else {
            userWantSpeaker = on;
            audioManager.setSpeakerphoneOn(on);
        }

        if (notify) {
            broadcastMediaChanged();
        }
    }

    /**
     * Called by user to enable/disable bluetooth.
     * @param on
     * @throws SameThreadException
     */
    public void setBluetoothOn(boolean on) throws SameThreadException {
        if (on){
            userClickedBtToTurnOn = true;
        }
        setBluetoothOn(on, true);
    }

    /**
     * Called by user to enable/disable bluetooth.
     * @param on
     * @throws SameThreadException
     */
    private void setBluetoothOn(boolean on, boolean notify) throws SameThreadException {
        Log.df(TAG, "Enable bluetooth: %s", on);
        // If loud speaker is enabled, it has to be turned off.
        if (on && userWantSpeaker){
            Log.vf(TAG, "Enabling bluetooth has to turn off loudspeaker mode.");
            setSpeakerphoneOn(false, false);
        }

        if (service != null && restartAudioWhenRoutingChange && !audioBell.isRinging()) {
            Log.vf(TAG, "With sound restart");
            service.setNoSnd();
            userWantBluetooth = on;
            service.setSnd();

        } else {
            userWantBluetooth = on;
            bluetoothManager.setBluetoothOn(on);
        }

        if (notify) {
            broadcastMediaChanged();
        }
    }

    public MediaState getMediaState() {
        MediaState mediaState = new MediaState();

        //Bluetooth
        mediaState.isBluetoothScoOn = bluetoothManager.isBluetoothOn();
        mediaState.isBluetoothSupported = bluetoothManager.isBluetoothSupported();

        // Micro
        mediaState.isMicrophoneMute = userWantMicrophoneMute;
        mediaState.canMicrophoneMute = !mediaState.isBluetoothScoOn;

        // Speaker
        mediaState.isSpeakerphoneOn = userWantSpeaker;
        mediaState.canSpeakerphoneOn = !mediaState.isBluetoothScoOn;

        return mediaState;
    }

    /**
     * Change the audio volume amplification according to the fact we are using bluetooth
     */
    public void setSoftwareVolume() {
        if (service == null) {
            return;
        }

        final boolean useBT = bluetoothManager.isBluetoothOn();

        final String speaker_key = useBT ? PhonexConfig.SOUND_BT_SPEAKER_VOLUME : PhonexConfig.SOUND_SPEAKER_VOLUME;
        final String mic_key = useBT ? PhonexConfig.SOUND_BT_MIC_VOLUME : PhonexConfig.SOUND_MIC_VOLUME;

        final float speakVolume = service.getPrefs().getFloat(speaker_key);
        final float micVolume = userWantMicrophoneMute ? 0 : service.getPrefs().getFloat(mic_key);

        Log.vf(TAG, "setSoftwareVolume, useBt: %s, speakerVolume: %s, micVolume: %s", useBT, speakVolume, micVolume);
        service.getHandler().execute(new SvcRunnable("setSoftwareVolume") {

            @Override
            protected void doRun() throws SameThreadException {
                service.confAdjustTxLevel(speakVolume);
                service.confAdjustRxLevel(micVolume);

                // Force the BT mode to normal
                if (useBT) {
                    audioManager.setMode(AudioManager.MODE_NORMAL);
                }
            }
        });
    }

    public void broadcastMediaChanged() {
        MiscUtils.sendBroadcast(service, mediaStateChangedIntent);
    }

    private void broadcastVolumeWillBeUpdated(int streamType, int index) {
        Intent notificationIntent = new Intent(ACTION_AUDIO_VOLUME_UPDATE);
        notificationIntent.putExtra(EXTRA_STREAM_TYPE, streamType);
        notificationIntent.putExtra(EXTRA_VOLUME_INDEX, index);
        notificationIntent.putExtra(EXTRA_RINGER_MODE, EXTRA_VALUE_UNKNOWN);

        service.sendBroadcast(notificationIntent, null);
    }

    public void setStreamVolume(int streamType, int index, int flags) {
        broadcastVolumeWillBeUpdated(streamType, index);
        audioManager.setStreamVolume(streamType, index, flags);
    }

    public void adjustStreamVolume(int streamType, int direction, int flags) {
        broadcastVolumeWillBeUpdated(streamType, EXTRA_VALUE_UNKNOWN);
        audioManager.adjustStreamVolume(streamType, direction, flags);
        if (streamType == AudioManager.STREAM_RING) {
            // Update audioBell
            audioBell.updateRingerMode();
        }

        final int inCallStream = Compatibility.getInCallStream(shouldRouteBluetooth());
        if (streamType == inCallStream) {
            int maxLevel = audioManager.getStreamMaxVolume(inCallStream);
            float modifiedLevel = (audioManager.getStreamVolume(inCallStream) / (float) maxLevel) * 10.0f;
            // Update default stream level
            service.getPrefs().setFloat(PhonexConfig.SOUND_INIT_VOLUME, modifiedLevel);

        }
    }

    public boolean doesUserWantMicrophoneMute() {
        return userWantMicrophoneMute;
    }

    public boolean doesUserWantBluetooth() {
        return userWantBluetooth;
    }

    /**
     * Play a tone in band
     *
     * @param toneId the id of the tone to play.
     */
    public void playInCallTone(int toneId) {
        (new InCallTonePlayer(toneId)).start();
    }

    @Override
    public void onMediaStateChanged(int status) {
        // If bluetooth is disconnected, tear it down.
        if (status == AudioManager.SCO_AUDIO_STATE_DISCONNECTED && !bluetoothManager.isUseBluetooth()){
            Log.vf(TAG, "BT disconnected, refocus, want=false;");
            refocus(false);
            userWantBluetooth = false;

        } else if (status == AudioManager.SCO_AUDIO_STATE_CONNECTED && bluetoothManager.isUseBluetooth()){
            Log.vf(TAG, "BT connected, refocus, want=true;");
            refocus(true);
            userWantBluetooth = true;
        }

        // If user clicks to start bluetooth but it turns on and off again we should say that
        // system was unable to connect to any bluetooth device.
        if (userClickedBtToTurnOn
                && service != null
                && previousBtState == AudioManager.SCO_AUDIO_STATE_CONNECTING
                && status == AudioManager.SCO_AUDIO_STATE_DISCONNECTED)
        {
            Log.vf(TAG, "BT connection failed");
            Toast.makeText(service.getApplicationContext(), R.string.bluetooth_connect_failed, Toast.LENGTH_SHORT).show();
        }

        previousBtState = status;

        setSoftwareVolume();
        broadcastMediaChanged();
    }

    /**
     * Helper class to play tones through the earpiece (or speaker / BT)
     * during a call, using the ToneGenerator.
     * <p/>
     * To use, just instantiate a new InCallTonePlayer
     * (passing in the TONE_* constant for the tone you want)
     * and start() it.
     * <p/>
     * When we're done playing the tone, if the phone is idle at that
     * point, we'll reset the audio routing and speaker state.
     * (That means that for tones that get played *after* a call
     * disconnects, like "busy" or "congestion" or "call ended", you
     * should NOT call resetAudioStateAfterDisconnect() yourself.
     * Instead, just start the InCallTonePlayer, which will automatically
     * defer the resetAudioStateAfterDisconnect() call until the tone
     * finishes playing.)
     */
    private class InCallTonePlayer extends Thread {
        // The tone volume relative to other sounds in the stream
        private static final int TONE_RELATIVE_VOLUME_HIPRI = 80;
        private static final int TONE_RELATIVE_VOLUME_LOPRI = 50;
        private int mToneId;

        InCallTonePlayer(int toneId) {
            super();
            mToneId = toneId;
        }

        @Override
        public void run() {
            Log.df(TAG, "InCallTonePlayer.run(toneId = %s)...", mToneId);

            int toneType; // passed to ToneGenerator.startTone()
            int toneVolume; // passed to the ToneGenerator constructor
            int toneLengthMillis;
            switch (mToneId) {
                case TONE_CALL_WAITING:
                    toneType = ToneGenerator.TONE_SUP_CALL_WAITING;
                    toneVolume = TONE_RELATIVE_VOLUME_HIPRI;
                    toneLengthMillis = 5000;
                    break;
                case TONE_BUSY:
                    toneType = ToneGenerator.TONE_SUP_BUSY;
                    toneVolume = TONE_RELATIVE_VOLUME_HIPRI;
                    toneLengthMillis = 4000;
                    break;
                case TONE_CONGESTION:
                    toneType = ToneGenerator.TONE_SUP_CONGESTION;
                    toneVolume = TONE_RELATIVE_VOLUME_HIPRI;
                    toneLengthMillis = 4000;
                    break;
                case TONE_BATTERY_LOW:
                    // For now, use ToneGenerator.TONE_PROP_ACK (two quick
                    // beeps). TODO: is there some other ToneGenerator
                    // tone that would be more appropriate here? Or
                    // should we consider adding a new custom tone?
                    toneType = ToneGenerator.TONE_PROP_ACK;
                    toneVolume = TONE_RELATIVE_VOLUME_HIPRI;
                    toneLengthMillis = 1000;
                    break;
                case TONE_CALL_ENDED:
                    toneType = ToneGenerator.TONE_PROP_PROMPT;
                    toneVolume = TONE_RELATIVE_VOLUME_LOPRI;
                    toneLengthMillis = 2000;
                    break;
                default:
                    throw new IllegalArgumentException("Bad toneId: " + mToneId);
            }

            // If the mToneGenerator creation fails, just continue without it. It is
            // a local audio signal, and is not as important.
            ToneGenerator toneGenerator;
            try {
                toneGenerator = new ToneGenerator(AudioManager.STREAM_VOICE_CALL, toneVolume);
                // if (DBG) log("- created toneGenerator: " + toneGenerator);
            } catch (RuntimeException e) {
                Log.wf(TAG, "InCallTonePlayer: Exception caught while creating ToneGenerator: %s", e);
                toneGenerator = null;
            }

            // Using the ToneGenerator (with the CALL_WAITING / BUSY /
            // CONGESTION tones at least), the ToneGenerator itself knows
            // the right pattern of tones to play; we do NOT need to
            // manually start/stop each individual tone, or manually
            // insert the correct delay between tones. (We just start it
            // and let it run for however long we want the tone pattern to
            // continue.)
            //
            // TODO: When we stop the ToneGenerator in the middle of a
            // "tone pattern", it sounds bad if we cut if off while the
            // tone is actually playing. Consider adding API to the
            // ToneGenerator to say "stop at the next silent part of the
            // pattern", or simply "play the pattern N times and then
            // stop."

            if (toneGenerator != null) {
                toneGenerator.startTone(toneType);
                SystemClock.sleep(toneLengthMillis);
                toneGenerator.stopTone();

                Log.v(TAG, "- InCallTonePlayer: done playing.");
                toneGenerator.release();
            }
        }
    }

}

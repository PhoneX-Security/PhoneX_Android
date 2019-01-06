package net.phonex.pub.a;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.IntentFilter;
import android.media.AudioManager;

import net.phonex.PhonexSettings;
import net.phonex.service.SvcRunnable;
import net.phonex.service.XService;
import net.phonex.util.Log;
import net.phonex.util.Registerable;

import java.util.Set;

/**
 * Bluetooth manager.
 * Created by dusanklinec on 17.12.15.
 */
public class BluetoothManager implements Registerable {
    private static final String TAG = "BluetoothManager";

    /**
     * XService instance
     */
    private XService service;

    protected BluetoothAdapter bluetoothAdapter;
    private MediaStateReceiver mediaStateReceiver;

    /**
     * Determines whether module is registered for operation.
     */
    private boolean registered = false;

    private AudioManager audioManager;

    /**
     * Indicating state after call to startBluetoothSco()
     * Changes on ACTION_SCO_AUDIO_STATE_UPDATED event,
     * true if SCO_AUDIO_STATE_CONNECTED is received in the sticky intent.
     */
    private boolean bluetoothConnected = false;

    /**
     * True if user wishes to turn bluetooth routing on.
     */
    private boolean useBluetooth = false;

    /**
     * True if SCO was started for the bluetooth.
     * Tracking audio routing settings for bluetooth.
     */
    private boolean bluetoothScoStarted = false;

    /**
     * Audio routing change listener.
     */
    protected MediaDeviceChangeListener mediaChangesListener;

    /**
     * Creates MessageManager.
     * Starts working handlers.
     *
     * @param service
     */
    public BluetoothManager(XService service) {
        this.service = service;
    }

    /**
     * Finalize() - called by GC. Simple check for un-registration.
     * To prevent programming errors to some extent.
     */
    @Override
    protected void finalize() throws Throwable {
        if (registered){
            Log.e(TAG, "Module is still registered");
        }

        super.finalize();
    }

    /**
     * Register for operation (e.g., registers content observers).
     * Calling this effectively enables this module.
     */
    public synchronized void register(){
        if (registered){
            Log.w(TAG, "Already registered");
            return;
        }

        // Get audio manager & bluetooth adapter
        audioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        try {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        } catch (RuntimeException e) {
            Log.e(TAG, "Exception getting bluetooth adapter.", e);
        }

        // Register for state update.
        if (mediaStateReceiver == null) {
            mediaStateReceiver = new MediaStateReceiver(this);
            try {
                getContext().registerReceiver(mediaStateReceiver, new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED));
            } catch (Exception e) {
                Log.e(TAG, "Exception registering audio state change receiver", e);
            }
        }

        PhonexSettings.loadDefaultLanguageNoThrow(getContext());
        Log.v(TAG, "BluetoothManager registered.");
        registered = true;
    }

    /**
     * Unregisters listeners of this module.
     * Has to be called during destruction of the XService.
     */
    public synchronized void unregister(){
        if (!registered){
            Log.w(TAG, "Already unregistered");
            return;
        }

        // Unregister receiver.
        if (mediaStateReceiver != null) {
            try {
                getContext().unregisterReceiver(mediaStateReceiver);
            } catch (Exception e) {
                Log.w(TAG, "Failed to unregister media state receiver", e);
            }

            mediaStateReceiver = null;
        }

        Log.v(TAG, "BluetoothManager unregistered.");
        registered = false;
    }

    public boolean isBluetoothHeadsetConnected() {
        return bluetoothAdapter != null && (bluetoothAdapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothAdapter.STATE_CONNECTED);
    }

    public void setBluetoothOn(boolean on) {
        Log.vf(TAG, "Enable bluetooth: %s, connected: %s, scoStarted: %s, is enabled: %s",
                on, bluetoothConnected, bluetoothScoStarted, audioManager.isBluetoothScoOn());

        useBluetooth = on;
        if(on != bluetoothScoStarted) {
            if(on) {
                Log.v(TAG, "Enabling bluetooth");
                audioManager.startBluetoothSco();
                bluetoothScoStarted = true;
            } else {
                Log.v(TAG, "Disabling bluetooth");
                audioManager.setBluetoothScoOn(false);
                audioManager.stopBluetoothSco();
                bluetoothScoStarted = false;
            }
        } else if(on != audioManager.isBluetoothScoOn()) {
            Log.vf(TAG, "Setting bluetooth: %s", on);
            audioManager.setBluetoothScoOn(on);
        }
    }

    /**
     * Returns true if bluetooth device is connected.
     * @return
     */
    public boolean isBluetoothOn() {
        return bluetoothConnected;
    }

    /**
     * Returns true if bluetooth can be enabled. I.e. there is at least one device paired.
     * @return
     */
    public boolean isBluetoothSupported() {
        if (bluetoothAdapter == null) {
            return false;
        }

        boolean atLeastOneDeviceAvailable = false;
        if (!bluetoothAdapter.isEnabled()){
            return false;
        }

        final Set<BluetoothDevice> availableDevices = bluetoothAdapter.getBondedDevices();
        for(BluetoothDevice device : availableDevices) {
            final BluetoothClass bluetoothClass = device.getBluetoothClass();
            if (bluetoothClass == null) {
                continue;
            }

            final int deviceClass = bluetoothClass.getDeviceClass();
            if(bluetoothClass.hasService(BluetoothClass.Service.RENDER) ||
                    deviceClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET ||
                    deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO ||
                    deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE)
            {
                atLeastOneDeviceAvailable = true;
                Log.vf(TAG, "Bluetooth device found: %s, class %s", device.getName(), deviceClass);
                break;
            }
        }

        final boolean supported = atLeastOneDeviceAvailable && audioManager.isBluetoothScoAvailableOffCall();
        Log.vf(TAG, "Bluetooth supported: %s, at least one device: %s", supported, atLeastOneDeviceAvailable);
        return supported;
    }

    /**
     * Called by event receiver.
     * @param state state
     */
    public void onMediaStateChange(int state){
        if (mediaChangesListener == null) {
            Log.wf(TAG, "Media change listener is null");
            return;
        }

        // Handle particular states.
        if (state == AudioManager.SCO_AUDIO_STATE_CONNECTING) {
            Log.vf(TAG, "Bluetooth: Connecting...");
            audioManager.setBluetoothScoOn(useBluetooth);

        } else if(state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
            Log.vf(TAG, "Bluetooth: Connected");
            setBluetoothConnected(true);

        } else if(state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
            Log.vf(TAG, "Bluetooth: Disconnected");
            setBluetoothConnected(false);

            // On disconnect tear bluetooth down so audio route is back in normal.
            setBluetoothOn(false);
        }

        service.executeJob(new SvcRunnable("MediaStateChange") {
            @Override
            protected void doRun() throws XService.SameThreadException {
                try {
                    mediaChangesListener.onMediaStateChanged(state);
                } catch (Exception e) {
                    Log.ef(TAG, "Exception on listener notification", e);
                }
            }
        });
    }

    /**
     * Returns context from service.
     * @return
     */
    protected Context getContext(){
        if (service==null) return null;
        return service.getApplicationContext();
    }

    public AudioManager getAudioManager() {
        return audioManager;
    }

    public boolean isUseBluetooth() {
        return useBluetooth;
    }

    public boolean isBluetoothConnected() {
        return bluetoothConnected;
    }

    public void setBluetoothConnected(boolean bluetoothConnected) {
        this.bluetoothConnected = bluetoothConnected;
    }

    public MediaDeviceChangeListener getMediaChangesListener() {
        return mediaChangesListener;
    }

    public void setMediaChangesListener(MediaDeviceChangeListener mediaChangesListener) {
        this.mediaChangesListener = mediaChangesListener;
    }
}

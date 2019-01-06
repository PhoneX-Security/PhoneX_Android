/**
 * This file contains relicensed code from Apache copyright of 
 * Copyright (C) 2006 The Android Open Source Project
 */

package net.phonex.pub.a;

import android.content.Context;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Vibrator;

import net.phonex.util.Log;


/**
 * AudioBell manager for the Phone app.
 */
public class AudioBell {
    private static final String TAG = "AudioBell";

    private static final int VIBRATE_LENGTH = 1000; // ms
    private static final int PAUSE_LENGTH = 1000; // ms

    // Uri for the ringtone.
    Uri customRingtoneUri;

    Ringtone ringtone = null;                // [sentinel]
    Vibrator vibrator;
    VibratorThread vibratorThread;
    RingerThread ringerThread;
    Context context;

    public AudioBell(Context aContext) {
        context = aContext;
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    /**
     * Starts the ringtone and/or vibrator.
     */
    public void ring(String remoteContact, String defaultRingtone) {
        Log.d(TAG, "==> ring() called...");

        synchronized (this) {

            AudioManager audioManager =
                    (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

            //Save ringtone at the begining in case we raise vol
            ringtone = getRingtone(remoteContact, defaultRingtone);

            //No ring no vibrate
            int ringerMode = audioManager.getRingerMode();
            if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
                Log.d(TAG, "skipping ring and vibrate because profile is Silent");
                return;
            }

            // Vibrate
            int vibrateSetting = audioManager.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER);
            Log.df(TAG, "v=%s rm=%s", vibrateSetting, ringerMode);
            if (vibratorThread == null &&
                    (vibrateSetting == AudioManager.VIBRATE_SETTING_ON ||
                            ringerMode == AudioManager.RINGER_MODE_VIBRATE)) {
                vibratorThread = new VibratorThread(vibrator);
                Log.d(TAG, "Starting vibrator...");
                vibratorThread.start();
            }

            // Vibrate only
            if (ringerMode == AudioManager.RINGER_MODE_VIBRATE ||
                    audioManager.getStreamVolume(AudioManager.STREAM_RING) == 0) {
                Log.d(TAG, "skipping ring because profile is Vibrate OR because volume is zero");
                return;
            }

            // AudioBell normal, audio set for ring, do it
            if (ringtone == null) {
                Log.d(TAG, "No ringtone available - do not ring");
                return;
            }

            Log.df(TAG, "Starting ring with %s", ringtone.getTitle(context));

            if (ringerThread == null) {
                ringerThread = new RingerThread(ringtone);
                Log.d(TAG, "Starting ringer...");
                audioManager.setMode(AudioManager.MODE_RINGTONE);
                ringerThread.start();
            }
        }
    }

    /**
     * @return true if we're playing a ringtone and/or vibrating
     * to indicate that there's an incoming call.
     * ("Ringing" here is used in the general sense.  If you literally
     * need to know if we're playing a ringtone or vibrating, use
     * isRingtonePlaying() or isVibrating() instead.)
     */
    public boolean isRinging() {
        return (ringerThread != null || vibratorThread != null);
    }

    /**
     * Stops the ringtone and/or vibrator if any of these are actually
     * ringing/vibrating.
     */
    public void stopRing() {
        synchronized (this) {
            Log.d(TAG, "==> stopRing() called...");
            stopVibrator();
            stopRinger();

            try {
                // Try to revert mode back to normal so sound stack works properly on BlackBerry.
                AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                audioManager.setMode(AudioManager.MODE_NORMAL);
                Log.df(TAG, "Audio mode set back to normal");

            } catch(Throwable t){
                Log.ef(TAG, t, "Could not set audio mode back to normal");
            }
        }
    }

    private void stopRinger() {
        if (ringerThread == null) {
            return;
        }

        ringerThread.stopRing();
        try {
            ringerThread.join(1500);
            Log.vf(TAG, "Ringer joined in time.");
        } catch (InterruptedException e) {
        }

        // Try it harder.
        ringerThread.interrupt();
        try {
            ringerThread.join(5500);
            Log.vf(TAG, "Ringer joined in time - interrupted.");
        } catch (InterruptedException e) {
        }
        ringerThread = null;
    }

    private void stopVibrator() {
        if (vibratorThread == null) {
            return;
        }

        vibratorThread.stopVibration();
        vibratorThread.interrupt();
        try {
            vibratorThread.join(500); // Should be plenty long (typ.)
        } catch (InterruptedException e) {
        } // Best efforts (typ.)
        vibratorThread = null;
    }

    public void updateRingerMode() {

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        synchronized (this) {
            int ringerMode = audioManager.getRingerMode();
            // Silent : stop everything
            if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
                stopRing();
                return;
            }

            // Vibrate
            int vibrateSetting = audioManager.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER);
            // If not already started restart it
            if (vibratorThread == null && (vibrateSetting == AudioManager.VIBRATE_SETTING_ON || ringerMode == AudioManager.RINGER_MODE_VIBRATE)) {
                vibratorThread = new VibratorThread(vibrator);
                vibratorThread.start();
            }

            // Vibrate only
            if (ringerMode == AudioManager.RINGER_MODE_VIBRATE || audioManager.getStreamVolume(AudioManager.STREAM_RING) == 0) {
                stopRinger();
                return;
            }

            //AudioBell
            if (ringerThread == null) {
                ringerThread = new RingerThread(ringtone);
                Log.d(TAG, "Starting ringer...");
                audioManager.setMode(AudioManager.MODE_RINGTONE);
                ringerThread.start();
            }

        }
    }

    private Ringtone getRingtone(String remoteContact, String defaultRingtone) {
        Uri ringtoneUri = Uri.parse(defaultRingtone);
        return RingtoneManager.getRingtone(context, ringtoneUri);
    }

    private static class VibratorThread extends Thread {
        private volatile boolean isRunning = true;
        private final Vibrator vibrator;

        private VibratorThread(Vibrator vibrator) {
            this.vibrator = vibrator;
        }

        public void stopVibration(){
            isRunning = false;
        }

        public void run() {
            try {
                while (isRunning) {
                    vibrator.vibrate(VIBRATE_LENGTH);
                    Thread.sleep(VIBRATE_LENGTH + PAUSE_LENGTH);
                }
            } catch (InterruptedException ex) {
                Log.d(TAG, "Vibrator thread interrupt");
            } finally {
                vibrator.cancel();
            }
            Log.d(TAG, "Vibrator thread exiting");
        }
    }

    private static class RingerThread extends Thread {
        private volatile boolean isRunning = true;
        private final Ringtone tone;

        private RingerThread(Ringtone tone) {
            this.tone = tone;
        }

        public void stopRing(){
            isRunning = false;
        }

        public void run() {
            if (tone == null){
                return;
            }

            try {
                while (isRunning) {
                    tone.play();
                    while (tone.isPlaying()) {
                        Thread.sleep(100);
                        if (!isRunning){
                            break;
                        }
                    }
                }
            } catch (InterruptedException ex) {
                Log.d(TAG, "AudioBell thread interrupt");

            } finally {
                try {
                    tone.stop();
                    Log.v(TAG, "Ringing tone stopped");

                } catch(Throwable t){
                    Log.ef(TAG, t, "Exception in stopping ring tone");
                }
            }
            Log.d(TAG, "AudioBell thread exiting");
        }
    }
}

package net.phonex.ui.lock.util;

import android.Manifest.permission;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Vibrator;

import net.phonex.util.Log;

public class VibrationHelper {
    private static final String TAG = "Vibration";

    /**
     * Vibration that checks permissions
     * @param context
     * @param duration
     */
    public static void vibrate(Context context, int duration) {
        if (hasVibrationPermission(context) && shouldVibrate(context)) {
            ((Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE))
                    .vibrate(duration);
        }
    }

    /**
     * Vibration that checks permissions
     * @param context
     * @param pattern
     * @param repeat
     */
    public static void vibrate(Context context, long[] pattern, int repeat) {
        if (hasVibrationPermission(context) && shouldVibrate(context)) {
            ((Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE))
                    .vibrate(pattern, repeat);
        }
    }

    private static boolean shouldVibrate(Context context){
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        //No ring no vibrate
        int ringerMode = audioManager.getRingerMode();
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
            Log.d(TAG, "skipping ring and vibrate because profile is Silent");
            return false;
        }
        return true;
    }

    private static boolean hasVibrationPermission(Context context) {
        int result = context.checkCallingOrSelfPermission(permission.VIBRATE);
        return (result == PackageManager.PERMISSION_GRANTED);
    }

}

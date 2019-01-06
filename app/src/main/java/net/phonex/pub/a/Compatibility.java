package net.phonex.pub.a;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.media.MediaRecorder.AudioSource;
import android.os.Build;
import android.text.TextUtils;

import net.phonex.pref.PhonexConfig;
import net.phonex.pref.PreferencesManager;
import net.phonex.util.Log;

import java.io.File;
import java.io.FileFilter;
import java.lang.reflect.Field;
import java.util.regex.Pattern;

@SuppressWarnings("deprecation")
public class Compatibility {
    private static final String TAG = "Compatibility";

    private Compatibility() {
    }

    public static int getApi() {
        return android.os.Build.VERSION.SDK_INT;
    }

    public static boolean isApiGreaterOrEquals(int apiLevel) {
        return android.os.Build.VERSION.SDK_INT >= apiLevel;
    }

    /**
     * Get the stream id for in call track. Can differ on some devices. Current
     * device for which it's different :
     *
     * @return
     */
    public static int getInCallStream(boolean requestBluetooth) {
        /* Archos 5IT */
        if (android.os.Build.BRAND.equalsIgnoreCase("archos")
                && android.os.Build.DEVICE.equalsIgnoreCase("g7a")) {
            // Since archos has no voice call capabilities, voice call stream is
            // not implemented
            // So we have to choose the good stream tag, which is by default
            // falled back to music
            return AudioManager.STREAM_MUSIC;
        }
        if (requestBluetooth) {
            return 6; /* STREAM_BLUETOOTH_SCO -- Thx @Stefan for the contrib */
        }

        // return AudioManager.STREAM_MUSIC;
        return AudioManager.STREAM_VOICE_CALL;
    }

    public static boolean shouldUseRoutingApi() {
        Log.df(TAG, "Current device %s - %s", android.os.Build.BRAND, android.os.Build.DEVICE);

        // HTC evo 4G
        if (android.os.Build.PRODUCT.equalsIgnoreCase("htc_supersonic")) {
            return true;
        }

        // ZTE joe
        if (android.os.Build.DEVICE.equalsIgnoreCase("joe")) {
            return true;
        }

        // Samsung  GT-S5830
        if (android.os.Build.DEVICE.toUpperCase().startsWith("GT-S")) {
            return true;
        }

        return false;
    }

    public static boolean shouldAllowBusinessCodeTextWatcher(){
        if (Build.MANUFACTURER.toLowerCase().contains("alps")){
            //
            return false;
        }
        return true;
    }

    public static boolean shouldUseModeApi() {
        // ZTE blade et joe
        if (android.os.Build.DEVICE.equalsIgnoreCase("blade")
                || android.os.Build.DEVICE.equalsIgnoreCase("joe")) {
            return true;
        }

        // Samsung GT-S5360 GT-S5830 GT-S6102 ... probably all..
        if (android.os.Build.DEVICE.toUpperCase().startsWith("GT-") ||
                android.os.Build.PRODUCT.toUpperCase().startsWith("GT-")) {
            return true;
        }

        // HTC evo 4G
        if (android.os.Build.PRODUCT.equalsIgnoreCase("htc_supersonic")) {
            return true;
        }

        // LG P500, Optimus V
        if (android.os.Build.DEVICE.toLowerCase().startsWith("thunder")) {
            return true;
        }

        // LG-LS840
        if (android.os.Build.DEVICE.toLowerCase().startsWith("cayman")) {
            return true;
        }

        // Huawei
        if (android.os.Build.DEVICE.equalsIgnoreCase("U8150") ||
                android.os.Build.DEVICE.equalsIgnoreCase("U8110") ||
                android.os.Build.DEVICE.equalsIgnoreCase("U8120") ||
                android.os.Build.DEVICE.equalsIgnoreCase("U8100") ||
                android.os.Build.PRODUCT.equalsIgnoreCase("U8655")) {
            return true;
        }

        // Moto defy mini
        if (android.os.Build.MODEL.equalsIgnoreCase("XT320")) {
            return true;
        }

        // Alcatel
        if (android.os.Build.DEVICE.toUpperCase().startsWith("ONE_TOUCH_993D")) {
            return true;
        }

        // N4
        if (android.os.Build.DEVICE.toUpperCase().startsWith("MAKO")) {
            return true;
        }

        return false;
    }

    public static String guessInCallMode() {
        if (android.os.Build.BRAND.equalsIgnoreCase("sdg") || isApiGreaterOrEquals(10)) {
            // Note that in APIs this is only available from level 11.
            return "3";
        }

        if (android.os.Build.DEVICE.equalsIgnoreCase("blade")) {
            return Integer.toString(AudioManager.MODE_IN_CALL);
        }

        return Integer.toString(AudioManager.MODE_NORMAL);
    }

    public static String getDefaultMicroSource() {
        if (isApiGreaterOrEquals(10)) {
            // Note that in APIs this is only available from level 11.
            // VOICE_COMMUNICATION
            return Integer.toString(0x7);
        }

        return Integer.toString(AudioSource.DEFAULT);
    }

    public static String getDefaultFrequency() {
        if (android.os.Build.DEVICE.equalsIgnoreCase("olympus")) {
            // Atrix bug
            return "32000";
        }
        if (android.os.Build.DEVICE.toUpperCase().equals("GT-P1010")) {
            // Galaxy tab see issue 932
            return "32000";
        }

        return "16000";
    }

    public static String getCpuAbi() {
        Field field;
        try {
            field = android.os.Build.class.getField("CPU_ABI");
            return field.get(null).toString();
        } catch (Exception e) {
            Log.w(TAG, "Announce to be android 1.6 but no CPU ABI field", e);
        }
        return "armeabi";
    }

    public final static int getNumCores() {
        // Private Class to display only CPU devices in the directory listing
        class CpuFilter implements FileFilter {
            @Override
            public boolean accept(File pathname) {
                // Check if filename is "cpu", followed by a single digit number
                if (Pattern.matches("cpu[0-9]", pathname.getName())) {
                    return true;
                }
                return false;
            }
        }
        try {
            // Get directory containing CPU info
            File dir = new File("/sys/devices/system/cpu/");
            // Filter to only list the devices we care about
            File[] files = dir.listFiles(new CpuFilter());
            // Return the number of cores (virtual CPU devices)
            return files.length;
        } catch (Exception e) {
            return Runtime.getRuntime().availableProcessors();
        }
    }

    private static boolean needPspWorkaround() {
        // No workaround needed since Honeycomb, false
        return false;
    }

    private static boolean needToneWorkaround() {
        if (android.os.Build.PRODUCT.toLowerCase().startsWith("gt-i5800") ||
                android.os.Build.PRODUCT.toLowerCase().startsWith("gt-i5801") ||
                android.os.Build.PRODUCT.toLowerCase().startsWith("gt-i9003")) {
            return true;
        }
        return false;
    }

    private static boolean needWebRTCImplementation() {
        if (android.os.Build.DEVICE.toLowerCase().contains("droid2")) {
            return true;
        }
        if (android.os.Build.MODEL.toLowerCase().contains("droid bionic")) {
            return true;
        }
        if (android.os.Build.DEVICE.toLowerCase().contains("sunfire")) {
            return true;
        }
        // Huawei Y300
        if (android.os.Build.DEVICE.equalsIgnoreCase("U8833")) {
            return true;
        }
        return false;
    }

    public static boolean shouldSetupAudioBeforeInit() {
        // Setup for GT / GS samsung devices.
        if (android.os.Build.DEVICE.toLowerCase().startsWith("gt-")
                || android.os.Build.PRODUCT.toLowerCase().startsWith("gt-")) {
            return true;
        }
        return false;
    }

    private static boolean shouldFocusAudio() {
        /* HTC One X */
        if (android.os.Build.DEVICE.toLowerCase().startsWith("endeavoru") ||
                android.os.Build.DEVICE.toLowerCase().startsWith("evita")) {
            return false;
        }

        if (android.os.Build.DEVICE.toUpperCase().startsWith("GT-P7510") && isApiGreaterOrEquals(15)) {
            return false;
        }
        return true;
    }

    private static int getDefaultAudioImplementation() {
        // Acer A510
        if (android.os.Build.DEVICE.toLowerCase().startsWith("picasso")) {
            return PhonexConfig.AUDIO_IMPLEMENTATION_JAVA;
        }
        if (Compatibility.isApiGreaterOrEquals(11)) {
            return PhonexConfig.AUDIO_IMPLEMENTATION_OPENSLES;
        }
        return PhonexConfig.AUDIO_IMPLEMENTATION_JAVA;
    }

    private static void resetCodecsSettings(PreferencesManager preferencesManager) {
        boolean supportFloating = false;

        @SuppressWarnings("unused")
        boolean isHeavyCpu = false;
        String abi = getCpuAbi();
        if (!TextUtils.isEmpty(abi)) {
            if (abi.equalsIgnoreCase("mips") || abi.equalsIgnoreCase("x86")) {
                supportFloating = true;
            }
            if (abi.equalsIgnoreCase("armeabi-v7a") || abi.equalsIgnoreCase("x86")) {
                isHeavyCpu = true;
            }
        }

        // For Narrowband
        preferencesManager.setCodecPriority("PCMU/8000/1", PhonexConfig.CODEC_NB, "60");
        preferencesManager.setCodecPriority("PCMA/8000/1", PhonexConfig.CODEC_NB, "50");
        preferencesManager.setCodecPriority("speex/8000/1", PhonexConfig.CODEC_NB, "240");
        preferencesManager.setCodecPriority("speex/16000/1", PhonexConfig.CODEC_NB, "220");
        preferencesManager.setCodecPriority("speex/32000/1", PhonexConfig.CODEC_NB, "0");
        preferencesManager.setCodecPriority("GSM/8000/1", PhonexConfig.CODEC_NB, "0");  // 230
        preferencesManager.setCodecPriority("G722/16000/1", PhonexConfig.CODEC_NB, "0");
        preferencesManager.setCodecPriority("G729/8000/1", PhonexConfig.CODEC_NB, "0");
        preferencesManager.setCodecPriority("iLBC/8000/1", PhonexConfig.CODEC_NB, "0");
        preferencesManager.setCodecPriority("SILK/8000/1", PhonexConfig.CODEC_NB, "0");  //239
        preferencesManager.setCodecPriority("SILK/12000/1", PhonexConfig.CODEC_NB, "0");
        preferencesManager.setCodecPriority("SILK/16000/1", PhonexConfig.CODEC_NB, "0");
        preferencesManager.setCodecPriority("SILK/24000/1", PhonexConfig.CODEC_NB, "0");
        preferencesManager.setCodecPriority("CODEC2/8000/1", PhonexConfig.CODEC_NB, "0");
        /*preferencesManager.setCodecPriority("G7221/16000/1",  PhonexConfig.CODEC_NB, "0");
        preferencesManager.setCodecPriority("G7221/32000/1",  PhonexConfig.CODEC_NB, "0");
        preferencesManager.setCodecPriority("ISAC/16000/1",   PhonexConfig.CODEC_NB, "0");
        preferencesManager.setCodecPriority("ISAC/32000/1",   PhonexConfig.CODEC_NB, "0");
        preferencesManager.setCodecPriority("AMR/8000/1",     PhonexConfig.CODEC_NB, "0");
        preferencesManager.setCodecPriority("opus/8000/1",   PhonexConfig.CODEC_NB, "0");
        preferencesManager.setCodecPriority("opus/12000/1",   PhonexConfig.CODEC_NB, "0");
        preferencesManager.setCodecPriority("opus/16000/1",   PhonexConfig.CODEC_NB, "0");
        preferencesManager.setCodecPriority("opus/24000/1",   PhonexConfig.CODEC_NB, "0");
        preferencesManager.setCodecPriority("opus/48000/1",   PhonexConfig.CODEC_NB, "0");
        preferencesManager.setCodecPriority("G726-16/8000/1", PhonexConfig.CODEC_NB, "0");
        preferencesManager.setCodecPriority("G726-24/8000/1", PhonexConfig.CODEC_NB, "0");
        preferencesManager.setCodecPriority("G726-32/8000/1", PhonexConfig.CODEC_NB, "0");
        preferencesManager.setCodecPriority("G726-40/8000/1", PhonexConfig.CODEC_NB, "0");*/

        // For Wideband
        preferencesManager.setCodecPriority("PCMU/8000/1", PhonexConfig.CODEC_WB, "60");
        preferencesManager.setCodecPriority("PCMA/8000/1", PhonexConfig.CODEC_WB, "50");
        preferencesManager.setCodecPriority("speex/8000/1", PhonexConfig.CODEC_WB, "240");
        preferencesManager.setCodecPriority("speex/16000/1", PhonexConfig.CODEC_WB, "250");
        preferencesManager.setCodecPriority("speex/32000/1", PhonexConfig.CODEC_WB, "0");
        preferencesManager.setCodecPriority("GSM/8000/1", PhonexConfig.CODEC_WB, "0");
        preferencesManager.setCodecPriority("G722/16000/1", PhonexConfig.CODEC_WB, supportFloating ? "235" : "0");
        preferencesManager.setCodecPriority("G729/8000/1", PhonexConfig.CODEC_WB, "0");
        preferencesManager.setCodecPriority("iLBC/8000/1", PhonexConfig.CODEC_WB, "0");
        preferencesManager.setCodecPriority("SILK/8000/1", PhonexConfig.CODEC_WB, "0");
        preferencesManager.setCodecPriority("SILK/12000/1", PhonexConfig.CODEC_WB, "0");
        preferencesManager.setCodecPriority("SILK/16000/1", PhonexConfig.CODEC_WB, "0");
        preferencesManager.setCodecPriority("SILK/24000/1", PhonexConfig.CODEC_WB, "70");
        preferencesManager.setCodecPriority("CODEC2/8000/1", PhonexConfig.CODEC_WB, "0");
        /*preferencesManager.setCodecPriority("G7221/16000/1",  PhonexConfig.CODEC_WB, "0");
        preferencesManager.setCodecPriority("G7221/32000/1",  PhonexConfig.CODEC_WB, "0");
        preferencesManager.setCodecPriority("ISAC/16000/1",   PhonexConfig.CODEC_WB, "0");
        preferencesManager.setCodecPriority("ISAC/32000/1",   PhonexConfig.CODEC_WB, "0");
        preferencesManager.setCodecPriority("AMR/8000/1",     PhonexConfig.CODEC_WB, "0");
        preferencesManager.setCodecPriority("opus/8000/1",    PhonexConfig.CODEC_WB, "0");
        preferencesManager.setCodecPriority("opus/12000/1",   PhonexConfig.CODEC_WB, "0");
        preferencesManager.setCodecPriority("opus/16000/1",   PhonexConfig.CODEC_WB, "0");
        preferencesManager.setCodecPriority("opus/24000/1",   PhonexConfig.CODEC_WB, "0");
        preferencesManager.setCodecPriority("opus/48000/1",   PhonexConfig.CODEC_WB, "0");
        preferencesManager.setCodecPriority("G726-16/8000/1", PhonexConfig.CODEC_WB, "0");
        preferencesManager.setCodecPriority("G726-24/8000/1", PhonexConfig.CODEC_WB, "0");
        preferencesManager.setCodecPriority("G726-32/8000/1", PhonexConfig.CODEC_WB, "0");
        preferencesManager.setCodecPriority("G726-40/8000/1", PhonexConfig.CODEC_WB, "0");*/

        // Bands repartition
        preferencesManager.setString("band_for_wifi", PhonexConfig.CODEC_WB);
        preferencesManager.setString("band_for_other", PhonexConfig.CODEC_NB);
        preferencesManager.setString("band_for_3g", PhonexConfig.CODEC_NB);
        preferencesManager.setString("band_for_gprs", PhonexConfig.CODEC_NB);
        preferencesManager.setString("band_for_edge", PhonexConfig.CODEC_NB);

    }

    public static void setFirstRunParameters(PreferencesManager preferencesManager) {
        preferencesManager.editStart();
        resetCodecsSettings(preferencesManager);

        preferencesManager.setString(PhonexConfig.MEDIA_QUALITY, getCpuAbi().equalsIgnoreCase("armeabi-v7a") ? "4" : "3");
        preferencesManager.setString(PhonexConfig.SND_CLOCK_RATE, getDefaultFrequency());
        preferencesManager.setBoolean(PhonexConfig.KEEP_AWAKE_IN_CALL, needPspWorkaround());
        preferencesManager.setBoolean(PhonexConfig.USE_ROUTING_API, shouldUseRoutingApi());
        preferencesManager.setBoolean(PhonexConfig.USE_MODE_API, shouldUseModeApi());
        preferencesManager.setBoolean(PhonexConfig.SET_AUDIO_GENERATE_TONE, needToneWorkaround());
        preferencesManager.setString(PhonexConfig.CALL_AUDIO_MODE, guessInCallMode());
        preferencesManager.setString(PhonexConfig.MICRO_SOURCE, getDefaultMicroSource());
        preferencesManager.setBoolean(PhonexConfig.USE_WEBRTC_HACK, needWebRTCImplementation());
        preferencesManager.setBoolean(PhonexConfig.ENABLE_FOCUS_AUDIO, shouldFocusAudio());
        preferencesManager.setString(PhonexConfig.AUDIO_IMPLEMENTATION, Integer.toString(getDefaultAudioImplementation()));
        preferencesManager.setBoolean(PhonexConfig.SETUP_AUDIO_BEFORE_INIT, shouldSetupAudioBeforeInit());

        // Tablet settings
        preferencesManager.setBoolean(PhonexConfig.PREVENT_SCREEN_ROTATION, !Compatibility.isTabletScreen(preferencesManager.getContext()));

        if (android.os.Build.PRODUCT.equalsIgnoreCase("SPH-M900")) {
            preferencesManager.setBoolean(PhonexConfig.INVERT_PROXIMITY_SENSOR, true);
        }

        preferencesManager.editCommit();
    }

    public static void onUpgrade(PreferencesManager prefWrapper, int lastSeenVersion, int runningVersion) {

        prefWrapper.editStart();
        if (lastSeenVersion > 0) {
            prefWrapper.setBoolean(PhonexConfig.HAS_ALREADY_SETUP_SERVICE, true);
        }

        if (lastSeenVersion <= 2240) {
            resetCodecsSettings(prefWrapper);
        }

        if (lastSeenVersion < 2252) {
            // INVITE timeout length, affects bye timeouts.
            prefWrapper.setString(PhonexConfig.TSX_TD_TIMEOUT, "6000");
        }

        if (lastSeenVersion < 2280) {
            prefWrapper.setString(PhonexConfig.KEEP_ALIVE_INTERVAL_WIFI, "90");
            prefWrapper.setString(PhonexConfig.KEEP_ALIVE_INTERVAL_MOBILE, "90");
            prefWrapper.setString(PhonexConfig.TCP_KEEP_ALIVE_INTERVAL_WIFI, "90");
            prefWrapper.setString(PhonexConfig.TCP_KEEP_ALIVE_INTERVAL_MOBILE, "90");
            prefWrapper.setString(PhonexConfig.TLS_KEEP_ALIVE_INTERVAL_WIFI, "90");
            prefWrapper.setString(PhonexConfig.TLS_KEEP_ALIVE_INTERVAL_MOBILE, "90");
            prefWrapper.setString(PhonexConfig.TSX_TD_TIMEOUT, "12000");
        }

        prefWrapper.editCommit();
    }

    public static void onUpgradeApi(PreferencesManager preferencesManager, int lastSeenVersion, int runningVersion) {
        preferencesManager.editStart();
        preferencesManager.setString(PhonexConfig.SND_CLOCK_RATE, getDefaultFrequency());
        preferencesManager.setBoolean(PhonexConfig.KEEP_AWAKE_IN_CALL, needPspWorkaround());
        preferencesManager.setBoolean(PhonexConfig.USE_ROUTING_API, shouldUseRoutingApi());
        preferencesManager.setBoolean(PhonexConfig.USE_MODE_API, shouldUseModeApi());
        preferencesManager.setBoolean(PhonexConfig.SET_AUDIO_GENERATE_TONE, needToneWorkaround());
        preferencesManager.setString(PhonexConfig.CALL_AUDIO_MODE, guessInCallMode());
        preferencesManager.setString(PhonexConfig.MICRO_SOURCE, getDefaultMicroSource());
        preferencesManager.setBoolean(PhonexConfig.USE_WEBRTC_HACK, needWebRTCImplementation());
        preferencesManager.setBoolean(PhonexConfig.ENABLE_FOCUS_AUDIO, shouldFocusAudio());
        preferencesManager.setString(PhonexConfig.AUDIO_IMPLEMENTATION, Integer.toString(getDefaultAudioImplementation()));
        preferencesManager.setBoolean(PhonexConfig.SETUP_AUDIO_BEFORE_INIT, shouldSetupAudioBeforeInit());
        preferencesManager.editCommit();
    }

    public static boolean isTabletScreen(Context ctxt) {
        boolean isTablet = false;
        Configuration cfg = ctxt.getResources().getConfiguration();
        int screenLayoutVal = 0;
        try {
            Field f = Configuration.class.getDeclaredField("screenLayout");
            screenLayoutVal = (Integer) f.get(cfg);
        } catch (Exception e) {
            return false;
        }
        int screenLayout = (screenLayoutVal & 0xF);
        // 0xF = SCREENLAYOUT_SIZE_MASK but avoid 1.5 incompat doing that
        if (screenLayout == 0x3 || screenLayout == 0x4) {
            // 0x3 = SCREENLAYOUT_SIZE_LARGE but avoid 1.5 incompat doing that
            // 0x4 = SCREENLAYOUT_SIZE_XLARGE but avoid 1.5 incompat doing that
            isTablet = true;
        }

        return isTablet;
    }

    public static int getHomeMenuId() {
        return 0x0102002c;
        //return android.R.id.home;
    }

    public static boolean isInstalledOnSdCard(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
            ApplicationInfo ai = pi.applicationInfo;

            // FLAG_EXTERNAL_STORAGE == 0x00040000
            return (ai.flags & 0x00040000) == 0x00040000;
        } catch (NameNotFoundException e) {
            // ignore
        }

        return false;
    }

    /**
     * Returns true if this application is run in blackberry OS.
     * @return
     */
    public static boolean isBlackBerry(){
        try {
            final String osName = System.getProperty("os.name");
            return "qnx".equalsIgnoreCase(osName)
                    || "blackberry".equalsIgnoreCase(osName)
                    || android.os.Build.BRAND.toLowerCase().contains("blackberry");

        } catch(Exception e){
            Log.e(TAG, "Exception, could not determine if running on blackberry", e);
        }

        return false;
    }

    /**
     * Returns true if voice call is supported on this system and in this version.
     * @return
     */
    public static boolean isCallSupported(){
        return true;
    }
}

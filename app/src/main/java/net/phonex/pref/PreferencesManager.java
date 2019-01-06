package net.phonex.pref;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.DateFormat;

import net.phonex.BuildConfig;
import net.phonex.PhonexSettings;
import net.phonex.pub.a.Compatibility;
import net.phonex.ui.lock.util.PinHelper;
import net.phonex.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Main manager for global PhoneX preferences. Use this if you do not require synchronized access, otherwise check out {@PreferencesConnector}
 */
public class PreferencesManager extends PreferencesHelper {

    /* Keys to remember in preferences */
    public static final String LAST_VERSION = "last_version";
    public static final String LAST_ANDROID_VERSION = "last_android_version";
    public static final String CLIST_IN_SYNC = "clist_in_sync";
    public static final String LAST_CLIST_SYNC = "last_clist_sync";
    public static final String LAST_REC_SUBSCRIPTION_CHECK = "last_recurrent_subscription_check";
    public static final String APP_HAS_BEEN_LAUNCHED = "app_has_been_launched";
    public static final String APP_INTRODUCTION_SEEN = "net.phonex.pref.app_introduction_seen";

    public static final String CODECS_SEPARATOR = "|";
    public static final String CODECS_LIST = "codecs_list";
    public final static HashMap<String, String> DEFAULT_PREFS_STRING = new HashMap<String, String>() {
        private static final long serialVersionUID = 194680003244790L;
        {

            put(PhonexConfig.USER_AGENT, PhonexSettings.getUserAgent());
            put(PhonexConfig.LOG_LEVEL, PhonexSettings.debuggingRelease() ? "5" : "1");

            put(PhonexConfig.PJ_UDP_PORT, "0");
            put(PhonexConfig.PJ_TCP_PORT, "0");
            put(PhonexConfig.TLS_TRANSPORT_PORT, "0");
            put(PhonexConfig.KEEP_ALIVE_INTERVAL_WIFI, "90");
            put(PhonexConfig.KEEP_ALIVE_INTERVAL_MOBILE, "90");
            put(PhonexConfig.TCP_KEEP_ALIVE_INTERVAL_WIFI, "90");
            put(PhonexConfig.TCP_KEEP_ALIVE_INTERVAL_MOBILE, "90");
            put(PhonexConfig.TLS_KEEP_ALIVE_INTERVAL_WIFI, "90");
            put(PhonexConfig.TLS_KEEP_ALIVE_INTERVAL_MOBILE, "90");
            put(PhonexConfig.PJ_RTP_PORT, "4000");
            put(PhonexConfig.OVERRIDE_NAMESERVER, "");
            put(PhonexConfig.TIMER_MIN_SE, "90");
            put(PhonexConfig.TIMER_SESS_EXPIRES, "1800");
            put(PhonexConfig.TSX_T1_TIMEOUT, "-1");
            put(PhonexConfig.TSX_T2_TIMEOUT, "-1");
            put(PhonexConfig.TSX_T4_TIMEOUT, "-1");
            put(PhonexConfig.TSX_TD_TIMEOUT, "12000");

            put(PhonexConfig.SND_AUTO_CLOSE_TIME, "1");
            put(PhonexConfig.ECHO_CANCELLATION_TAIL_LEN, "200");
            put(PhonexConfig.ECHO_CANCELLATION_MODE, "3"); /* WEBRTC */
            put(PhonexConfig.MEDIA_QUALITY, "4");
            put(PhonexConfig.SND_CLOCK_RATE, "16000");
            put(PhonexConfig.AUDIO_FRAME_PTIME, "20");
            put(PhonexConfig.CALL_AUDIO_MODE, "0");
            put(PhonexConfig.MICRO_SOURCE, "1");
            put(PhonexConfig.THREAD_COUNT, "0");
            put(PhonexConfig.MEDIA_THREAD_COUNT, Compatibility.getNumCores() > 1 ? "2" : "1");
            put(PhonexConfig.HEADSET_ACTION, "0");
            put(PhonexConfig.AUDIO_IMPLEMENTATION, "0");
            put(PhonexConfig.H264_PROFILE, "66");
            put(PhonexConfig.H264_LEVEL, "0");
            put(PhonexConfig.H264_BITRATE, "0");
            put(PhonexConfig.VIDEO_CAPTURE_SIZE, "");

            put(PhonexConfig.STUN_SERVER, PhonexSettings.getStunServer());
            put(PhonexConfig.TURN_SERVER, PhonexSettings.getTurnServer());
            put(PhonexConfig.TURN_USERNAME, "");
            put(PhonexConfig.TURN_PASSWORD, "");
            put(PhonexConfig.CA_LIST_FILE, "");
            put(PhonexConfig.CERT_FILE, "");
            put(PhonexConfig.PRIVKEY_FILE, "");
            put(PhonexConfig.TLS_METHOD, "0");
            put(PhonexConfig.NETWORK_ROUTES_POLLING, "0");

            put(PhonexConfig.DSCP_VAL, "24");
            put(PhonexConfig.DSCP_RTP_VAL, "46");

            put(PhonexConfig.DEFAULT_CALLER_ID, "");
            put(PhonexConfig.RINGTONE, "");

            put(PhonexConfig.MESSAGES_DELETE_PERIOD, "-1");
            put(PhonexConfig.NETWORK_WATCHDOG, "0");

            put(PhonexConfig.DEFAULT_DHKEYS_PER_CONTACT, "4");
            put(PhonexConfig.DEFAULT_DHKEYS_PERIODIC_REPEAT, Integer.toString(60 * 60 * 5));

            put(PhonexConfig.PIN_LOCK_TIMER, "-1");

            // By default, trial account has 10 outgoing messages allowed per day (50 before PHON-581)
            put(PhonexConfig.TRIAL_MESSAGE_LIMIT_PER_DAY, "10");
        }
    };
    private static final String TAG = "PreferencesManager";
    private static final String CONFIG_FOLDER = "configs";
    private static final String RECORDS_FOLDER = "records";
    private static final String LOGS_FOLDER = "logs";
    private static final String INSTALLATION = "install";
    private static final String INSTALLATION_FILE = "inst";
    private static final String TEMP_FOLDER = "temp";
    public static final String DOWNLOAD_FOLDER = "phonex_storage"; // these are file system names and should be translated dynamically!
    public static final String SECURE_CAMERA_FOLDER = "phonex_camera"; // these are file system names and should be translated dynamically!
    public static final String RECEIVED_FILES_FOLDER = "phonex_received"; // these are file system names and should be translated dynamically!
    public static final String SECURE_TEMP_FOLDER = "phonex_temp";
    private final static HashMap<String, Boolean> DEFAULT_PREFS_BOOLEAN = new HashMap<String, Boolean>() {
        private static final long serialVersionUID = 12378700044L;

        {
            //Network
            put(PhonexConfig.LOCK_WIFI, true);
            put(PhonexConfig.LOCK_WIFI_FULL_HIGH_PERF, false);
            put(PhonexConfig.ENABLE_TCP, true);
            put(PhonexConfig.ENABLE_UDP, true);
            put(PhonexConfig.ENABLE_TLS, false);
            put(PhonexConfig.USE_IPV6, false);
            put(PhonexConfig.ENABLE_DNS_SRV, false);
            put(PhonexConfig.ENABLE_ICE, true);
            put(PhonexConfig.ENABLE_TURN, true);
            put(PhonexConfig.ENABLE_STUN, true);
            put(PhonexConfig.ENABLE_STUN2, false);
            put(PhonexConfig.ENABLE_QOS, false);
            put(PhonexConfig.USE_COMPACT_FORM, false);
            put(PhonexConfig.USE_WIFI_IN, true);
            put(PhonexConfig.USE_WIFI_OUT, true);
            put(PhonexConfig.USE_OTHER_IN, true);
            put(PhonexConfig.USE_OTHER_OUT, true);
            put(PhonexConfig.USE_ANYWAY_IN, true);
            put(PhonexConfig.USE_ANYWAY_OUT, true);
            put(PhonexConfig.FORCE_NO_UPDATE, true);
            put(PhonexConfig.DISABLE_TCP_SWITCH, true);
            put(PhonexConfig.ADD_BANDWIDTH_TIAS_IN_SDP, false);

            //Media
            put(PhonexConfig.ECHO_CANCELLATION, true);
            put(PhonexConfig.DISABLE_VAD, true);
            put(PhonexConfig.ENABLE_NOISE_SUPPRESSION, false);
            put(PhonexConfig.USE_SOFT_VOLUME, false);
            put(PhonexConfig.USE_ROUTING_API, false);
            put(PhonexConfig.USE_MODE_API, false);
            put(PhonexConfig.HAS_IO_QUEUE, true);
            put(PhonexConfig.SET_AUDIO_GENERATE_TONE, false);
            put(PhonexConfig.USE_WEBRTC_HACK, false);
            put(PhonexConfig.ENABLE_FOCUS_AUDIO, true);
            put(PhonexConfig.BLUETOOTH_DEFAULT_ON, false);
            put(PhonexConfig.AUTO_CONNECT_SPEAKER, false);
            put(PhonexConfig.AUTO_DETECT_SPEAKER, false);
            put(PhonexConfig.RESTART_AUDIO_ON_ROUTING_CHANGES, true);
            put(PhonexConfig.SETUP_AUDIO_BEFORE_INIT, true);

            //UI
            put(PhonexConfig.PREVENT_SCREEN_ROTATION, true);
            put(PhonexConfig.KEEP_AWAKE_IN_CALL, false);
            put(PhonexConfig.INVERT_PROXIMITY_SENSOR, false);
            put(PhonexConfig.USE_PARTIAL_WAKE_LOCK, false);
            put(PhonexConfig.APP_CLOSED_BY_USER, false);
            put(PhonexConfig.HAS_ALREADY_SETUP_SERVICE, false);
            put(PhonexConfig.LOG_USE_DIRECT_FILE, false);
            put(PhonexConfig.LOG_TO_FILE, false);
            put(PhonexConfig.USE_DEVEL_DATA_SERVER, false);
            put(PhonexConfig.CONTACT_LIST_SORT_ONLINE, true);
            put(PhonexConfig.CONTACT_LIST_SHOW_USERNAMES, true);
            put(PhonexConfig.ALERT_NEW_MESSAGE_DURING_CHAT, false);

            //Calls
            put(PhonexConfig.SUPPORT_MULTIPLE_CALLS, false);
            put(PhonexConfig.USE_VIDEO, false);

            //Secure
            put(PhonexConfig.TLS_VERIFY_SERVER, false);
            put(PhonexConfig.TLS_VERIFY_CLIENT, false);

            //Security
            put(PhonexConfig.DROP_BAD_INVITE, true);
            put(PhonexConfig.DROP_BAD_BYE, true);
            put(PhonexConfig.SHOW_SIPSIG_WARNINGS, true);
            put(PhonexConfig.PHONEX_UPDATES, false);
            // Blocking screenshots is by default turned off in debug mode
            put(PhonexConfig.PHONEX_BLOCK_SCREENSHOTS, !BuildConfig.DEBUG);
            put(PhonexConfig.PUBLISH_IN_CALL_STATE, true);
            put(PhonexConfig.DEVELOPER_MODE, false);
            put(PhonexConfig.PIN_LOCK_ENABLE, false);
            put(PhonexConfig.P2P_DISABLE, false);
            //Trial
            put(PhonexConfig.TRIAL_EXPIRED_WARNING_SHOWN, false);
            put(PhonexConfig.TRIAL_DAY_TO_EXPIRE_WARNING_SHOWN, false);
            put(PhonexConfig.TRIAL_WEEK_TO_EXPIRE_WARNING_SHOWN, false);
        }
    };
    private final static HashMap<String, Float> DEFAULT_PREFS_FLOAT = new HashMap<String, Float>() {
        private static final long serialVersionUID = 1550045116772L;

        {
            put(PhonexConfig.SOUND_MIC_VOLUME, (float) 1.0);
            put(PhonexConfig.SOUND_SPEAKER_VOLUME, (float) 1.0);
            put(PhonexConfig.SOUND_BT_MIC_VOLUME, (float) 1.0);
            put(PhonexConfig.SOUND_BT_SPEAKER_VOLUME, (float) 1.0);
            put(PhonexConfig.SOUND_INIT_VOLUME, (float) 8.0);
        }
    };
    private final static HashMap<String, Long> DEFAULT_PREFS_LONG = new HashMap<String, Long>() {
        private static final long serialVersionUID = 197248101L;
        {
            put(PhonexConfig.PIN_LAST_TICK, 0l);
        }
    };
    private final static HashMap<String, Integer> DEFAULT_PREFS_INTEGER = new HashMap<String, Integer>() {
        private static final long serialVersionUID = 197248102L;
        {
            // this is not working, historically all integers are stored as strings
        }
    };


    private static String sID = null;
    private static boolean VERSION_UPGRADE_SUCCEEDED = false;

    public PreferencesManager(Context aContext) {
        context = aContext;
        prefs = PreferenceManager.getDefaultSharedPreferences(aContext);
        resolver = aContext.getContentResolver();

        // Check if we need an upgrade here
        // BUNDLE MODE -- upgrade settings
        if (!VERSION_UPGRADE_SUCCEEDED) {
            forceCheckUpgrade();
            checkDevelSettings();
        }
    }

    private static String getString(SharedPreferences aPrefs, String key) {
        if (aPrefs == null) {
            return DEFAULT_PREFS_STRING.get(key);
        }
        if (DEFAULT_PREFS_STRING.containsKey(key)) {
            return aPrefs.getString(key, DEFAULT_PREFS_STRING.get(key));
        }
        return aPrefs.getString(key, null);
    }

    private static Boolean getBoolean(SharedPreferences aPrefs, String key) {
        if (aPrefs == null) {
            return DEFAULT_PREFS_BOOLEAN.get(key);
        }
        if (DEFAULT_PREFS_BOOLEAN.containsKey(key)) {
            return aPrefs.getBoolean(key, DEFAULT_PREFS_BOOLEAN.get(key));
        }
        if (aPrefs.contains(key)) {
            return aPrefs.getBoolean(key, false);
        }
        return null;
    }

    private static Float getFloat(SharedPreferences aPrefs, String key) {
        if (aPrefs == null) {
            return DEFAULT_PREFS_FLOAT.get(key);
        }
        if (DEFAULT_PREFS_FLOAT.containsKey(key)) {
            return aPrefs.getFloat(key, DEFAULT_PREFS_FLOAT.get(key));
        }
        if (aPrefs.contains(key)) {
            return aPrefs.getFloat(key, 0.0f);
        }
        return null;
    }

    private static Long getLong(SharedPreferences aPrefs, String key) {
        if (aPrefs == null) {
            return DEFAULT_PREFS_LONG.get(key);
        }
        if (DEFAULT_PREFS_LONG.containsKey(key)) {
            return aPrefs.getLong(key, DEFAULT_PREFS_LONG.get(key));
        }
        if (aPrefs.contains(key)) {
            return aPrefs.getLong(key, 0l);
        }
        return null;
    }

//    private static Integer getInteger(SharedPreferences aPrefs, String key) {
//        if (aPrefs == null) {
//            return DEFAULT_PREFS_INTEGER.get(key);
//        }
//        if (DEFAULT_PREFS_INTEGER.containsKey(key)) {
//            return aPrefs.getInt(key, DEFAULT_PREFS_INTEGER.get(key));
//        }
//        if (aPrefs.contains(key)) {
//            return aPrefs.getInt(key, 0);
//        }
//        return null;
//    }

    public static Class<?> getPreferenceType(String key) {
        if (DEFAULT_PREFS_STRING.containsKey(key)) {
            return String.class;
        } else if (DEFAULT_PREFS_BOOLEAN.containsKey(key)) {
            return Boolean.class;
        } else if (DEFAULT_PREFS_FLOAT.containsKey(key)) {
            return Float.class;
        } else if(DEFAULT_PREFS_LONG.containsKey(key)){
            return Long.class;
        }
        return null;
    }

    private static File getStorageFolder(Context ctxt, boolean preferCache) {
        File root = Environment.getExternalStorageDirectory();
        if (!root.canWrite() || preferCache) {
            root = ctxt.getCacheDir();
        }

        if (root.canWrite()) {
            File dir = new File(root.getAbsolutePath() + File.separator + PhonexSettings.getSDCardFolder());
            if (!dir.exists()) {
                dir.mkdirs();
                Log.df(TAG, "Create directory %s", dir.getAbsolutePath());
            }
            return dir;
        }
        return null;
    }

    private static File getFolder(Context ctxt, String subFolder, boolean preferCache) {
        File root = getStorageFolder(ctxt, preferCache);
        if (root != null) {
            File dir = new File(root.getAbsoluteFile() + File.separator + subFolder);
            dir.mkdirs();
            return dir;
        }
        return null;
    }

    private static File getFolder(Context ctxt, String subFolder, boolean preferCache, boolean createIfNonExistent) throws IOException {
        File fl = getFolder(ctxt, subFolder, preferCache);
        if (fl == null || fl.exists()){
            return fl;
        }

        boolean success = fl.mkdirs();
        if (!success){
            throw new IOException("Cannot create storage directory: [" + fl.getAbsolutePath() + "]");
        } else {
            Log.vf(TAG, "Storage directory created at: [%s]", fl.getAbsolutePath());
        }

        return fl;
    }

    public static File getConfigFolder(Context ctxt) {
        return getFolder(ctxt, CONFIG_FOLDER, false);
    }

    public static File getRecordsFolder(Context ctxt) {
        return getFolder(ctxt, RECORDS_FOLDER, false);
    }

    public static File getLogsFolder(Context ctxt) {
        return getFolder(ctxt, LOGS_FOLDER, false);
    }

    public static File getTempFolder(Context ctxt) {
        return getFolder(ctxt, TEMP_FOLDER, false);
    }

    public static File getTempFolder(Context ctxt, boolean createIfNonexisting) throws IOException {
        return getFolder(ctxt, TEMP_FOLDER, false, createIfNonexisting);
    }

    public static File getDownloadFolder(Context ctxt) {
        return getFolder(ctxt, DOWNLOAD_FOLDER, false);
    }

    /**
     * Get directory where all secure storage files are saved.
     * It may contain subdirectories for individual users (currently not used).
     * @param ctxt
     * @return the directory or null if cannot create the directory
     */
    public static File getRootSecureStorageFolder(Context ctxt) {
        File root = ctxt.getExternalFilesDir(null);
        if (root == null || !root.canWrite()) {
            root = ctxt.getCacheDir();
        }

        if (root.canWrite()) {
            File dir = new File(root.getAbsolutePath() + File.separator + DOWNLOAD_FOLDER);
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    Log.df(TAG, "Failed to create directory %s", dir.getAbsolutePath());
                    return null;
                }
                Log.df(TAG, "Create directory %s", dir.getAbsolutePath());
            }

            // make directories for received files and for camera also, this way when secure storage
            // is opened from file picker for the first time, it is not empty
            createSubdirectory(dir, SECURE_CAMERA_FOLDER);
            createSubdirectory(dir, RECEIVED_FILES_FOLDER);

            return dir;
        }

        return null;
    }

    private static File createSubdirectory(File parent, String name) {
        if (parent != null && parent.exists() && parent.isDirectory()) {
            File subdir = new File(parent, name);
            if (!subdir.exists()) {
                if (subdir.mkdir()) {
                    return subdir;
                } else {
                    return null;
                }
            } else {
                if (subdir.isDirectory()) {
                    return subdir;
                } else {
                    return null;
                }
            }
        } else {
            return null;
        }
    }

    /**
     * Get directory where received secure storage files are saved.
     *
     * @param ctxt
     * @return the directory or null if cannot create the directory
     */
    public static File getUserSecureStorageFolder(Context ctxt) {
        File rootSecureStorageFolder = getRootSecureStorageFolder(ctxt);
        return createSubdirectory(rootSecureStorageFolder, RECEIVED_FILES_FOLDER);
    }

    /**
     * Get directory where temporary secure storage files are saved.
     *
     * Files are not protected by placing them in this directory, use FileStorage.
     *
     * @param ctxt
     * @return the directory or null if cannot create the directory
     */
    public static File getSecureTempFolder(Context ctxt) {
        // do not put into secure storage folder, so that user does not see temp in secure storage
        //File rootSecureStorageFolder = getRootSecureStorageFolder(ctxt);
        File root = ctxt.getExternalFilesDir(null);
        return createSubdirectory(root, SECURE_TEMP_FOLDER);
    }

    /**
     * Get directory where we store photos taken by secure camera.
     *
     * @param ctxt
     * @return the directory or null if cannot create the directory
     */
    public static File getSecureCameraFolder(Context ctxt) {
        File rootSecureStorageFolder = getRootSecureStorageFolder(ctxt);
        return createSubdirectory(rootSecureStorageFolder, SECURE_CAMERA_FOLDER);
    }

    public static File getInstallationFolder(Context ctxt) {
        return getFolder(ctxt, INSTALLATION, false);
    }

    public synchronized static String getUUID(Context context) {
        if (sID == null) {
            try {
                File installation = new File(getInstallationFolder(context), INSTALLATION_FILE);
                if (!installation.exists())
                    writeInstallationFile(installation);
                sID = readInstallationFile(installation);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return sID;
    }

    private static String readInstallationFile(File installation) throws IOException {
        RandomAccessFile f = new RandomAccessFile(installation, "r");
        byte[] bytes = new byte[(int) f.length()];
        f.readFully(bytes);
        f.close();
        return new String(bytes);
    }

    private static void writeInstallationFile(File installation) throws IOException {
        FileOutputStream out = new FileOutputStream(installation);
        String id = UUID.randomUUID().toString();
        out.write(id.getBytes());
        out.close();
    }

    public static String getDeviceDesc(Context ctxt) {
        String UUID = getUUID(ctxt);

        // get IMEI
        String IMEI = "";
        try {
            TelephonyManager mTelephonyMgr = (TelephonyManager) ctxt.getSystemService(Context.TELEPHONY_SERVICE);
            IMEI = mTelephonyMgr.getDeviceId();
        } catch (Exception ex) {
            Log.e(TAG, "Cannot get IMEI", ex);
        }

        // android ID
        String androidId = "";
        try {
            androidId = Secure.getString(ctxt.getContentResolver(), Secure.ANDROID_ID);
        } catch (Exception ex) {
            Log.e(TAG, "Cannot get android ID", ex);
        }

        return (UUID + IMEI + androidId);
    }

    public static File getLogsFile(Context ctxt, boolean isPjsip) {
        return getLogsFile(ctxt, isPjsip, false);
    }

    public static File getLogsFile(Context ctxt, boolean isPjsip, boolean gzip) {
        File dir = PreferencesManager.getLogsFolder(ctxt);
        File outFile = null;
        if (dir != null) {
            Date d = new Date();
            StringBuffer fileName = new StringBuffer();
            if (isPjsip) {
                fileName.append("pjsip");
            }
            fileName.append("logs_");
            fileName.append(DateFormat.format("yy-MM-dd_kkmmss", d));
            fileName.append(".logcat");
            if (gzip) fileName.append(".gz");
            outFile = new File(dir.getAbsoluteFile() + File.separator + fileName.toString());
        }

        return outFile;
    }

    public static File getZrtpFolder(Context ctxt) {
        File bFiles = ctxt.getFilesDir();
        if (bFiles == null){
            Log.e(TAG, "Cannot obtain internal files directory");
            return null;
        }

        File zrtpFolder = new File(bFiles, "zcache");
        if (!zrtpFolder.exists()){
            zrtpFolder.mkdirs();
        }

        if (!zrtpFolder.exists()){
            Log.ef(TAG, "Cannot create ZRTP directory %s", zrtpFolder.toString());
            return null;
        }

        return zrtpFolder;
    }

    public static void cleanLogsFiles(Context ctxt) {
        File logsFolder = getLogsFolder(ctxt);
        if (logsFolder != null) {
            File[] files = logsFolder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        file.delete();
                    }
                }
            }
        }
    }

    public void forceCheckUpgrade() {
        Integer runningVersion = needUpgrade();
        if (runningVersion != null) {
            Editor editor = prefs.edit();
            editor.putInt(LAST_VERSION, runningVersion);
            editor.commit();
        }
        VERSION_UPGRADE_SUCCEEDED = true;
    }

    /**
     * Check wether an upgrade is needed
     *
     * @return null if not needed, else the new version to upgrade to
     */
    private Integer needUpgrade() {
        Integer runningVersion = null;

        // Application upgrade
        PackageInfo pinfo = PreferencesConnector.getCurrentPackageInfos(context);
        if (pinfo != null) {
            runningVersion = pinfo.versionCode;
            int lastSeenVersion = prefs.getInt(LAST_VERSION, 0);

            Log.df(TAG, "Last known version is %s and currently we are running %s", lastSeenVersion, runningVersion);
            if (lastSeenVersion != runningVersion) {
                Compatibility.onUpgrade(this, lastSeenVersion, runningVersion);
            } else {
                runningVersion = null;
            }
        }

        // Android upgrade
        if (prefs != null) {
            int lastSeenVersion = prefs.getInt(LAST_ANDROID_VERSION, 0);
            Log.df(TAG, "Last known android version %s", lastSeenVersion);
            if (lastSeenVersion != Compatibility.getApi()) {
                Compatibility.onUpgradeApi(this, lastSeenVersion, Compatibility.getApi());
                Editor editor = prefs.edit();
                editor.putInt(LAST_ANDROID_VERSION, Compatibility.getApi());
                editor.commit();
            }
        }
        return runningVersion;
    }

    public String getString(String key) {
        return getString(prefs, key);
    }

    public Boolean getBoolean(String key) {
        return getBoolean(prefs, key);
    }

    public Float getFloat(String key) {
        return getFloat(prefs, key);
    }

    public Long getLong(String key) {
        return getLong(prefs, key);
    }

    public Integer getInteger(String key) {
        try {
            return Integer.parseInt(getString(key));
        } catch (NumberFormatException e) {
            Log.df(TAG, "Invalid format of [%s], int wanted", key);
        }
        String val = DEFAULT_PREFS_STRING.get(key);
        if (val != null) {
            return Integer.parseInt(val);
        }
        return null;
    }

    /**
     * Set all values to default
     */
    public void resetToDefaults() {
        for (String key : DEFAULT_PREFS_STRING.keySet()) {
            setString(key, DEFAULT_PREFS_STRING.get(key));
        }
        for (String key : DEFAULT_PREFS_BOOLEAN.keySet()) {
            setBoolean(key, DEFAULT_PREFS_BOOLEAN.get(key));
        }
        for (String key : DEFAULT_PREFS_FLOAT.keySet()) {
            setFloat(key, DEFAULT_PREFS_FLOAT.get(key));
        }
        Compatibility.setFirstRunParameters(this);
        setBoolean(PhonexConfig.HAS_ALREADY_SETUP_SERVICE, true);
        Log.d(TAG, "Settings were set to defaults");
    }

    private boolean hasStunServer(String string) {
        String[] servers = getString(PhonexConfig.STUN_SERVER).split(",");
        for (String server : servers) {
            if (server.equalsIgnoreCase(string)) {
                return true;
            }
        }

        return false;
    }

    // Codec
    public short getCodecPriority(String codecName, String type, String defaultValue) {
        String key = PhonexConfig.getCodecKey(codecName, type);
        if (key != null) {
            try {
                return (short) Integer.parseInt(prefs.getString(key, defaultValue));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Invalid codec priority", e);
            }
        }
        return (short) Integer.parseInt(defaultValue);
    }

    public void setCodecPriority(String codecName, String type, String newValue) {
        String key = PhonexConfig.getCodecKey(codecName, type);
        if (key != null) {
            setString(key, newValue);
        } else {
            Log.wf(TAG, "Error, key is null for [%s]", codecName);
        }
    }

    public void pinLockIfNecessary(){
        Log.vf(TAG, "pinLockIfNecessary");
        long lastTick = getLong(PhonexConfig.PIN_LAST_TICK);
        long current = System.currentTimeMillis();
        long diff = current - lastTick;

        // threshold is given stored in preferences, but there is some min limit
        long threshold = Math.max(Long.parseLong(getString(PhonexConfig.PIN_LOCK_TIMER)), PinHelper.PIN_TICK_MIN_THRESHOLD);

        //Log.vf(TAG, "PinHelper locking check: lastTick [%d],  timeDiff [%d], threshold[%d] current[%d]", lastTick, diff, threshold, current);
        if (diff > threshold){
            PinHelper.lock(getContext(), true);
//            Log.vf(TAG, "PinHelper locking: lastTick [%d],  timeDiff [%d], threshold[%d]", lastTick/1000, diff, threshold);
        }
    }

    /**
     * Get current mode for the user
     * By default the user is a default user. If becomes an advanced user he will have access to expert mode.
     *
     * @return
     */
    public boolean isDeveloperActive() {
        return prefs.getBoolean(PhonexConfig.DEVELOPER_MODE, false) || PhonexSettings.debuggingRelease();
    }

    /**
     * Toogle the user into an expert user. It will give him access to expert settings if was an expert user
     */
    public void toggleDeveloperMode() {
        setBoolean(PhonexConfig.DEVELOPER_MODE, !isDeveloperActive());
    }

    /**
     * Turn the application as quited by user. It will not register anymore
     *
     * @param quit true if the app should be considered as finished.
     */
    public void setQuit(boolean quit) {
        setBoolean(PhonexConfig.APP_CLOSED_BY_USER, quit);
    }

    /**
     * Get list of audio codecs registered in preference system
     *
     * @return List of possible audio codecs
     * @see PreferencesConnector#setCodecList(java.util.List)
     */
    public String[] getCodecList() {
        return TextUtils.split(prefs.getString(CODECS_LIST, ""), Pattern.quote(CODECS_SEPARATOR));
    }

    /**
     * Resets sensitive settings according to the current policy.
     */
    private void checkDevelSettings(){
        if (!PhonexSettings.debuggingRelease()) {
            setBoolean(PhonexConfig.DEVELOPER_MODE, false);
            setBoolean(PhonexConfig.USE_DEVEL_DATA_SERVER, false);
            setBoolean(PhonexConfig.LOG_TO_FILE, false);
            setString(PhonexConfig.LOG_LEVEL, "0");
        }
    }

}

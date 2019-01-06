package net.phonex.pref;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import net.phonex.PhonexSettings;
import net.phonex.ft.misc.PhotoResizeOptions;
import net.phonex.pub.a.Compatibility;
import net.phonex.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for access to global PhoneX preferences defined in {@PreferencesManager}
 * All access is synchronized across threads and processes, as all requests are serialized as they go via PreferenceProvider
 */
public class PreferencesConnector {
    private static final String TAG = "PreferencesConnector";
    private ContentResolver contentResolver;
    private ConnectivityManager connectivityManager;
    private Context ctxt;

    public PreferencesConnector(Context ctxt) {
        this.ctxt = ctxt;
        contentResolver = ctxt.getContentResolver();
        connectivityManager = (ConnectivityManager) ctxt.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public final static PackageInfo getCurrentPackageInfos(Context ctx) {
        PackageInfo pinfo = null;
        try {
            pinfo = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Impossible to find version of current package !!");
        }
        return pinfo;
    }

    public static File getRecordsFolder(Context ctxt) {
        return PreferencesManager.getRecordsFolder(ctxt);
    }

    /**
     * Set all values to default
     */
    public void resetToDefaults() {
        Uri uri = PhonexConfig.ERASE_URI;
        contentResolver.update(uri, new ContentValues(), null, null);
        Log.d(TAG, "Reset settings to defaults");
    }

    public void pinLockIfNoRecentTick() {
        Uri uri = PhonexConfig.PIN_LOCK_IF_NO_RECENT_TICK_URI;
        contentResolver.update(uri, new ContentValues(), null, null);
        Log.d(TAG, "pinLockIfNoRecentTick");
        //@see PreferenceManager#pinLockIfNecessary for core logic
    }
    // String
    public String getString(String key) {
        return PhonexConfig.getStringPref(ctxt, key);
    }

    public String getString(String key, String defaultVal) {
        return PhonexConfig.getStringPref(ctxt, key, defaultVal);
    }

    public void setString(String key, String newValue) {
        PhonexConfig.setStringPref(ctxt, key, newValue);
    }

    public void deleteString(String key) {
        PhonexConfig.deleteStringPref(ctxt, key);
    }

    // Boolean
    public boolean getBoolean(String string) {
        return PhonexConfig.getBooleanPref(ctxt, string);
    }

    public void setBoolean(String key, boolean newValue) {
        PhonexConfig.setBooleanPref(ctxt, key, newValue);
    }

    public boolean getBoolean(String string, boolean b) {
        return PhonexConfig.getBooleanPref(ctxt, string, b);
    }

    // Integer
    public int getInteger(String key) {
        return PhonexConfig.getIntegerPref(ctxt, key);
    }

    public int getInteger(String key, int i) {
        return PhonexConfig.getIntegerPref(ctxt, key, i);
    }

    public void setInteger(String key, int newValue){
        PhonexConfig.setIntegerPref(ctxt, key, newValue);
    }

    // Float
    public float getFloat(String key) {
        return PhonexConfig.getFloatPref(ctxt, key);
    }

    public float getFloat(String key, float f) {
        return PhonexConfig.getFloatPref(ctxt, key, f);
    }

    public void setFloat(String key, float newValue) {
        PhonexConfig.setFloatPref(ctxt, key, newValue);
    }

    // Long
    public void setLong(String key, long newValue){
        PhonexConfig.setLongPref(ctxt, key, newValue);
    }

    public long getLong(String key) {
        return PhonexConfig.getLongPref(ctxt, key);
    }

    public long getLong(String key, long d) {
        return PhonexConfig.getLongPref(ctxt, key, d);
    }


    public PhotoResizeOptions getPhotoResizeOptions() {
        PhotoResizeOptions options = new PhotoResizeOptions();
        int preferredResolution = getInteger(PhonexConfig.PHOTO_RESIZE_RESOLUTION, 1024);
        options.setDoResize(preferredResolution > 0);
        options.setLongEdgePixels(preferredResolution);
        options.setLowerBoundPercent(getFloat(PhonexConfig.PHOTO_RESIZE_LOWER_BOUND, 0.1f));
        options.setUpperBoundPercent(getFloat(PhonexConfig.PHOTO_RESIZE_UPPER_BOUND, 0.1f));
        options.setJpegQuality(getInteger(PhonexConfig.PHOTO_RESIZE_JPEG_QUALITY, 90));
        return options;
    }

    // Check for wifi
    private boolean isValidWifiConnectionFor(NetworkInfo ni, String suffix) {
        boolean valid_for_wifi = getBoolean("use_wifi_" + suffix, true);
        // We consider ethernet as wifi
        if (valid_for_wifi && ni != null) {
            int type = ni.getType();
            // Wifi connected
            if (ni.isConnected() &&
                    // 9 = ConnectivityManager.TYPE_ETHERNET
                    (type == ConnectivityManager.TYPE_WIFI || type == 9)) {
                return true;
            }
        }
        return false;
    }

    // Check for acceptable mobile data network connection
    private boolean isValidMobileConnectionFor(NetworkInfo ni, String suffix) {
        boolean valid_for_3g = true;//getBoolean("use_3g_" + suffix, true);
        boolean valid_for_edge = true;//getBoolean("use_edge_" + suffix, true);
        boolean valid_for_gprs = true;//getBoolean("use_gprs_" + suffix, true);

        if ((valid_for_3g || valid_for_edge || valid_for_gprs) && ni != null) {
            int type = ni.getType();

            // Any mobile network connected
            if (ni.isConnected() &&
                    // Type 3,4,5 are other mobile data ways
                    (type == ConnectivityManager.TYPE_MOBILE || (type <= 5 && type >= 3))) {
                int subType = ni.getSubtype();

                // 3G (or better)
                if (valid_for_3g &&
                        subType >= TelephonyManager.NETWORK_TYPE_UMTS) {
                    return true;
                }

                // GPRS (or unknown)
                if (valid_for_gprs
                        &&
                        (subType == TelephonyManager.NETWORK_TYPE_GPRS || subType == TelephonyManager.NETWORK_TYPE_UNKNOWN)) {
                    return true;
                }

                // EDGE
                if (valid_for_edge &&
                        subType == TelephonyManager.NETWORK_TYPE_EDGE) {
                    return true;
                }
            }
        }
        return false;
    }

    // Check for other (wimax for example)
    private boolean isValidOtherConnectionFor(NetworkInfo ni, String suffix) {
        boolean valid_for_other = getBoolean("use_other_" + suffix, true);
        // boolean valid_for_other = true;
        if (valid_for_other &&
                ni != null &&
                ni.getType() != ConnectivityManager.TYPE_MOBILE
                && ni.getType() != ConnectivityManager.TYPE_WIFI) {
            return ni.isConnected();
        }
        return false;
    }

    private boolean isValidAnywayConnectionFor(NetworkInfo ni, String suffix) {
        return getBoolean("use_anyway_" + suffix, true);

    }

    // Generic function for both incoming and outgoing
    private boolean isValidConnectionFor(NetworkInfo ni, String suffix) {
        if (isValidWifiConnectionFor(ni, suffix)) {
            return true;
        }
        if (isValidMobileConnectionFor(ni, suffix)) {
            return true;
        }
        if (isValidOtherConnectionFor(ni, suffix)) {
            return true;
        }
        if (isValidAnywayConnectionFor(ni, suffix)) {
            return true;
        }
        return false;
    }

    /**
     * Say whether current connection is valid for outgoing calls
     *
     * @return true if connection is valid
     */
    public boolean isValidConnectionForOutgoing() {
        if (getBoolean(PhonexConfig.APP_CLOSED_BY_USER, false)) {
            // Don't go further, we have been explicitly stopped
            return false;
        }
        NetworkInfo ni = connectivityManager.getActiveNetworkInfo();
        return isValidConnectionFor(ni, "out");
    }

    /**
     * Say whether current connection is valid for outgoing calls, ignoring explicit application quit
     *
     * @return true if connection is valid
     */
    public boolean isValidConnectionForOutgoingRaw() {
        NetworkInfo ni = connectivityManager.getActiveNetworkInfo();
        return isValidConnectionFor(ni, "out");
    }

    /**
     * Returns current active network networkInfo.
     * @return
     */
    public NetworkInfo getCurrentNetworkInfo(){
        return connectivityManager.getActiveNetworkInfo();
    }

    /**
     * Returns true if current connection is connected.
     *
     * @return
     */
    public boolean isValidConnectionRaw() {
        NetworkInfo ni = connectivityManager.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }



    /**
     * Say whether current connection is valid for incoming calls
     *
     * @return true if connection is valid
     */
    public boolean isValidConnectionForIncoming() {
        NetworkInfo ni = connectivityManager.getActiveNetworkInfo();
        return isValidConnectionFor(ni, "in");
    }

    public ArrayList<String> getAllIncomingNetworks() {
        ArrayList<String> incomingNetworks = new ArrayList<String>();
        final String[] availableNetworks = {
                "3g", "edge", "gprs", "wifi", "other"
        };
        for (String network : availableNetworks) {
            incomingNetworks.add(network);
            //if (getBoolean("use_" + network + "_in")) {
            //
            //}
        }

        return incomingNetworks;
    }

    public int getLogLevel() {
        int prefsValue = PhonexConfig.getIntegerPref(ctxt, PhonexConfig.LOG_LEVEL, 1);
        if (prefsValue <= 6 && prefsValue >= 1) {
            return prefsValue;
        }

        return 1;
    }

    /**
     * Get the audio codec quality setting
     *
     * @return the audio quality
     */
    public int getAudioMode() {
        String mode = getString(PhonexConfig.CALL_AUDIO_MODE);
        try {
            return Integer.parseInt(mode);
        } catch (NumberFormatException e) {
            Log.ef(TAG, "In call mode %s not well formated", mode);
        }

        return AudioManager.MODE_NORMAL;
    }

    /**
     * Get current clock rate
     *
     * @return clock rate in Hz
     */
    public long getClockRate() {
        String clockRate = getString(PhonexConfig.SND_CLOCK_RATE);
        try {
            return Integer.parseInt(clockRate);
        } catch (NumberFormatException e) {
            Log.ef(TAG, "Clock rate %s not well formated", clockRate);
        }
        return 16000;
    }

    public boolean useRoutingApi() {
        return getBoolean(PhonexConfig.USE_ROUTING_API);
    }

    public boolean useModeApi() {
        return getBoolean(PhonexConfig.USE_MODE_API);
    }

    public boolean generateForSetCall() {
        return getBoolean(PhonexConfig.SET_AUDIO_GENERATE_TONE);
    }

    // / ---- PURE SIP SETTINGS -----

    public float getInitialVolumeLevel() {
        return (float) (getFloat(PhonexConfig.SOUND_INIT_VOLUME, 8.0f) / 10.0f);
    }

    /**
     * Get sip ringtone
     *
     * @return string uri
     */
    public String getRingtone() {
        String ringtone = getString(PhonexConfig.RINGTONE,
                Settings.System.DEFAULT_RINGTONE_URI.toString());

        if (TextUtils.isEmpty(ringtone)) {
            ringtone = Settings.System.DEFAULT_RINGTONE_URI.toString();
        }
        return ringtone;
    }

    public boolean isTCPEnabled() {
        return getBoolean(PhonexConfig.ENABLE_TCP);
    }

    public boolean isUDPEnabled() {
        return getBoolean(PhonexConfig.ENABLE_UDP);
    }

    public boolean isTLSEnabled() {
        return getBoolean(PhonexConfig.ENABLE_TLS);
    }

    public boolean useIPv6() {
        return getBoolean(PhonexConfig.USE_IPV6);
    }

    private int getPrefPort(String key) {
        int port = getInteger(key);
        if (isValidPort(port)) {
            return port;
        }
        return Integer.parseInt(PreferencesManager.DEFAULT_PREFS_STRING.get(key));
    }

    public int getUDPTransportPort() {
        return getPrefPort(PhonexConfig.PJ_UDP_PORT);
    }

    public int getTCPTransportPort() {
        return getPrefPort(PhonexConfig.PJ_TCP_PORT);
    }

    public int getTLSTransportPort() {
        return getPrefPort(PhonexConfig.TLS_TRANSPORT_PORT);
    }

    private int getKeepAliveInterval(String wifi_key, String mobile_key) {
        NetworkInfo ni = connectivityManager.getActiveNetworkInfo();
        if (ni != null && ni.getType() == ConnectivityManager.TYPE_WIFI) {
            return getInteger(wifi_key);
        }
        return getInteger(mobile_key);
    }

    /**
     * Retrieve UDP keep alive interval for the current connection
     *
     * @return KA Interval in second
     */
    public int getUdpKeepAliveInterval() {
        return getKeepAliveInterval(PhonexConfig.KEEP_ALIVE_INTERVAL_WIFI,
                PhonexConfig.KEEP_ALIVE_INTERVAL_MOBILE);
    }

    /**
     * Retrieve TCP keep alive interval for the current connection
     *
     * @return KA Interval in second
     */
    public int getTcpKeepAliveInterval() {
        return getKeepAliveInterval(PhonexConfig.TCP_KEEP_ALIVE_INTERVAL_WIFI,
                PhonexConfig.TCP_KEEP_ALIVE_INTERVAL_MOBILE);
    }

    /**
     * Retrieve TLS keep alive interval for the current connection
     *
     * @return KA Interval in second
     */
    public int getTlsKeepAliveInterval() {
        return getKeepAliveInterval(PhonexConfig.TLS_KEEP_ALIVE_INTERVAL_WIFI,
                PhonexConfig.TLS_KEEP_ALIVE_INTERVAL_MOBILE);
    }

    public int getRTPPort() {
        return getPrefPort(PhonexConfig.PJ_RTP_PORT);
    }

    public boolean enableDNSSRV() {
        return getBoolean(PhonexConfig.ENABLE_DNS_SRV);
    }

    public int getTLSMethod() {
        return getInteger(PhonexConfig.TLS_METHOD);
    }

    // Utils

    public String getUserAgent(Context ctx) {
        String userAgent = getString(PhonexConfig.USER_AGENT);
        if (userAgent.equalsIgnoreCase(PhonexSettings.getUserAgent())) {
            // If that's the official -not custom- user agent, send the release,
            // the device and the api level
            PackageInfo pinfo = getCurrentPackageInfos(ctx);
            if (pinfo != null) {
                userAgent += "_" + android.os.Build.DEVICE + "-" + Compatibility.getApi()
                        + "/r" + pinfo.versionCode;
            }
        }
        return userAgent;
    }

    /**
     * Check TCP/UDP validity of a network port
     */
    private boolean isValidPort(int port) {
        return (port >= 0 && port < 65535);
    }

    // Media part

    /**
     * Get a property from android property subsystem
     *
     * @param prop property to get
     * @return the value of the property command line or null if failed
     */
    public String getSystemProp(String prop) {
        // String re1 = "^\\d+(\\.\\d+){3}$";
        // String re2 = "^[0-9a-f]+(:[0-9a-f]*)+:[0-9a-f]+$";
        try {
            String line;
            Process p = Runtime.getRuntime().exec("getprop " + prop);
            InputStream in = p.getInputStream();
            InputStreamReader isr = new InputStreamReader(in);
            BufferedReader br = new BufferedReader(isr);
            while ((line = br.readLine()) != null) {
                return line;
            }
        } catch (Exception e) {
            // ignore resolutely
        }
        return null;
    }

    /**
     * Get auto close time after end of the call To avoid crash after hangup --
     * android 1.5 only but even sometimes crash
     */
    public int getAutoCloseTime() {
        return getInteger(PhonexConfig.SND_AUTO_CLOSE_TIME);
    }

    /**
     * Whether echo cancellation is enabled
     *
     * @return true if enabled
     */
    public boolean hasEchoCancellation() {
        return getBoolean(PhonexConfig.ECHO_CANCELLATION);
    }

    public long getEchoCancellationTail() {
        if (!hasEchoCancellation()) {
            return 0;
        }
        return getInteger(PhonexConfig.ECHO_CANCELLATION_TAIL_LEN);
    }

    /**
     * Get the audio codec quality setting
     *
     * @return the audio quality
     */
    public long getMediaQuality() {
        String mediaQuality = getString(PhonexConfig.MEDIA_QUALITY);
        // prefs.getString(MEDIA_QUALITY, String.valueOf(defaultValue));
        try {
            int prefsValue = Integer.parseInt(mediaQuality);
            if (prefsValue <= 10 && prefsValue >= 0) {
                return prefsValue;
            }
        } catch (NumberFormatException e) {
            Log.ef(TAG, "Audio quality %s not well formated", mediaQuality);
        }

        return 4;
    }

    /**
     * Get whether ice is enabled
     *
     * @return 1 if enabled (pjstyle)
     */
    public int getIceEnabled() {
        return getBoolean(PhonexConfig.ENABLE_ICE) ? 1 : 0;
    }

    /**
     * Get whether turn is enabled
     *
     * @return 1 if enabled (pjstyle)
     */
    public int getTurnEnabled() {
        return getBoolean(PhonexConfig.ENABLE_TURN) ? 1 : 0;
    }

    /**
     * Get whether turn is enabled
     *
     * @return 1 if enabled (pjstyle)
     */
    public int getStunEnabled() {
        return getBoolean(PhonexConfig.ENABLE_STUN) ? 1 : 0;
    }

    /**
     * Get turn server
     *
     * @return host:port or blank if not set
     */
    public String getTurnServer() {
        return getString(PhonexConfig.TURN_SERVER);
    }

    /**
     * Setup codecs list Should be only done by the service that get infos from
     * the sip stack(s)
     *
     * @param codecs the list of codecs
     */
    public void setCodecList(List<String> codecs) {
        if (codecs != null) {
            setString(PreferencesManager.CODECS_LIST,
                    TextUtils.join(PreferencesManager.CODECS_SEPARATOR, codecs));
        }
    }

    /**
     * Get the codec priority
     *
     * @param codecName    codec name formated in the pjsip format (the
     *                     corresponding pref is
     *                     codec_{{lower(codecName)}}_{{codecFreq}})
     * @param defaultValue the default value if the pref is not found MUST be
     *                     casteable as Integer/short
     * @return the priority of the codec as defined in preferences
     */
    public short getCodecPriority(String codecName, String type, String defaultValue) {
        String key = PhonexConfig.getCodecKey(codecName, type);
        if (key != null) {
            String val = getString(key, defaultValue);
            if (!TextUtils.isEmpty(val)) {
                try {
                    return (short) Integer.parseInt(val);
                } catch (NumberFormatException e) {
                    Log.ef(TAG, "Impossible to parse %s", val);
                }
            }
        }
        return (short) Integer.parseInt(defaultValue);
    }

    /**
     * Returns true if there is racoon or mtpd VPN daemon running.
     *
     * @return
     */
    public boolean isVPNDaemonRunning() {
        final String[] daemons = new String[]{"mtpd", "racoon"};
        for (String daemon : daemons) {
            String state = getSystemProp("init.svc." + daemon);
            if ("running".equals(state)) {
                return true;
            }
        }

        return false;
    }
}

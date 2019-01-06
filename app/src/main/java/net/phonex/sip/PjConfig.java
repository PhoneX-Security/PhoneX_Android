package net.phonex.sip;

import android.content.Context;
import android.text.TextUtils;

import net.phonex.PhonexSettings;
import net.phonex.pref.PhonexConfig;
import net.phonex.pref.PreferencesConnector;
import net.phonex.pref.PreferencesManager;
import net.phonex.util.Log;
import net.phonex.util.android.JNILibsManager;

import net.phonex.xv.SWIGTYPE_p_pj_ssl_cipher;
import net.phonex.xv.SWIGTYPE_p_pj_stun_auth_cred;
import net.phonex.xv.Xvi;
import net.phonex.xv.dynamic_factory;
import net.phonex.xv.pj_ice_cand_type;
import net.phonex.xv.pj_ssl_cipher;
import net.phonex.xv.pj_str_t;
import net.phonex.xv.pjmedia_srtp_use;
import net.phonex.xv.pjsip_timer_setting;
import net.phonex.xv.pjsip_tls_setting;
import net.phonex.xv.XviConstants;
import net.phonex.xv.pjsua_config;
import net.phonex.xv.pjsua_logging_config;
import net.phonex.xv.pjsua_media_config;
import net.phonex.xv.sipstack_config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ph4r05 on 7/27/14.
 */
public class PjConfig {
    private static final String TAG="PjConfig";
    private final PreferencesConnector prefs;
    private final Context ctxt;

    private pjsua_config ua;
    private pjsua_logging_config log;
    private pjsua_media_config media;
    private sipstack_config pj;

    /**
     * Factory method. Returns initialized configuration.
     * @param prefsWrapper
     * @return
     */
    public static PjConfig initConfiguration(Context ctxt, PreferencesConnector prefsWrapper){
        PjConfig cfg = new PjConfig(ctxt, prefsWrapper);
        cfg.initConfig();
        return cfg;
    }

    public PjConfig(Context ctxt, PreferencesConnector prefs) {
        this.prefs = prefs;
        this.ctxt = ctxt;
    }

    /**
     * Initializes configuration.
     */
    public void initConfig(){
        pj = new sipstack_config();
        ua = new pjsua_config();
        log = new pjsua_logging_config();
        media = new pjsua_media_config();

        initPjConfig();
        initPjsuaConfig();
        initLogConfig();
        initMediaConfig();
    }

    public pjsua_config getUa() {
        return ua;
    }

    public pjsua_logging_config getLog() {
        return log;
    }

    public pjsua_media_config getMedia() {
        return media;
    }

    public sipstack_config getPj() {
        return pj;
    }

    /**
     * Initializes Pj configuration for PjSip.
     */
    private void initPjConfig(){
        // CSS CONFIG
        Xvi.sipstack_config_default(pj);

        // Booleans
        pj.setUse_compact_form_headers(
                PjUtils.toPjBool(prefs.getBoolean(PhonexConfig.USE_COMPACT_FORM)));
        pj.setUse_compact_form_sdp(
                PjUtils.toPjBool(prefs.getBoolean(PhonexConfig.USE_COMPACT_FORM)));
        pj.setUse_no_update(
                PjUtils.toPjBool(prefs.getBoolean(PhonexConfig.FORCE_NO_UPDATE)));
        pj.setUse_noise_suppressor(
                PjUtils.toPjBool(prefs.getBoolean(PhonexConfig.ENABLE_NOISE_SUPPRESSION)));
        pj.setDisable_tcp_switch(
                PjUtils.toPjBool(prefs.getBoolean(PhonexConfig.DISABLE_TCP_SWITCH)));
        pj.setAdd_bandwidth_tias_in_sdp(
                PjUtils.toPjBool(prefs.getBoolean(PhonexConfig.ADD_BANDWIDTH_TIAS_IN_SDP)));
        pj.setDisable_rport(
                PjUtils.toPjBool(false));

        // Intervals
        pj.setTcp_keep_alive_interval(prefs.getTcpKeepAliveInterval());
        pj.setTls_keep_alive_interval(prefs.getTlsKeepAliveInterval());

        // Transaction timeouts
        int tsx_to = prefs.getInteger(PhonexConfig.TSX_T1_TIMEOUT);
        if (tsx_to > 0) {
            pj.setTsx_t1_timeout(tsx_to);
        }
        tsx_to = prefs.getInteger(PhonexConfig.TSX_T2_TIMEOUT);
        if (tsx_to > 0) {
            pj.setTsx_t2_timeout(tsx_to);
        }
        tsx_to = prefs.getInteger(PhonexConfig.TSX_T4_TIMEOUT);
        if (tsx_to > 0) {
            pj.setTsx_t4_timeout(tsx_to);
        }
        tsx_to = prefs.getInteger(PhonexConfig.TSX_TD_TIMEOUT);
        if (tsx_to > 0) {
            pj.setTsx_td_timeout(tsx_to);
        }

        // ZRTP
        File zrtpFolder = PreferencesManager.getZrtpFolder(ctxt);
        if (zrtpFolder != null) {
            pj.setUse_zrtp(PjUtils.toPjBool(PhonexSettings.useZrtp()));
            pj.setStorage_folder(Xvi.pj_str_copy(zrtpFolder.getAbsolutePath()));
        } else {
            throw new SecurityException("ZRTP failed");
        }

        // Audio implementation
        int implementation = prefs.getInteger(PhonexConfig.AUDIO_IMPLEMENTATION); // 0 = java
        if (implementation == PhonexConfig.AUDIO_IMPLEMENTATION_OPENSLES) {
            dynamic_factory audImp = pj.getAudio_implementation();
            audImp.setInit_factory_name(Xvi.pj_str_copy("pjmedia_opensl_factory"));
            File openslLib = JNILibsManager.getJNILibFile(ctxt, JNILibsManager.OPENSL_LIB);
            audImp.setShared_lib_path(Xvi.pj_str_copy(openslLib.getAbsolutePath()));
            pj.setAudio_implementation(audImp);
            Log.d(TAG, "Using OpenSL-ES audio");
        }
    }

    /**
     * Initializes logging configuration for PjSip.
     */
    private void initLogConfig(){
        Xvi.logging_config_default(log);
        log.setConsole_level(PhonexSettings.enableLogs() ? prefs.getLogLevel() : 0);
        log.setLevel(PhonexSettings.enableLogs() ? prefs.getLogLevel() : 0);
        log.setMsg_logging(PjUtils.toPjBool(PhonexSettings.enableLogs()));

        if (prefs.getBoolean(PhonexConfig.LOG_USE_DIRECT_FILE, false) && PhonexSettings.enableLogs()) {
            File outFile = PreferencesManager.getLogsFile(ctxt, true);
            if (outFile != null) {
                log.setLog_filename(Xvi.pj_str_copy(outFile.getAbsolutePath()));
                log.setLog_file_flags(0x1108 /* PJ_O_APPEND */);
            }
        }
    }

    /**
     * Initializes Pjsua configuration.
     */
    private void initPjsuaConfig(){
        Xvi.config_default(ua);
        ua.setCb(XviConstants.WRAPPER_CALLBACK_STRUCT);
        ua.setUser_agent(Xvi.pj_str_copy(prefs.getUserAgent(ctxt)));
        // We need at least one thread
        int threadCount = prefs.getInteger(PhonexConfig.THREAD_COUNT);
        if (threadCount <= 0) {
            threadCount = 1;
        }

        ua.setThread_cnt(threadCount);
        ua.setUse_srtp(getUseSrtp());
        ua.setSrtp_secure_signaling(0);
        ua.setNat_type_in_sdp(0);

        pjsip_timer_setting timerSetting = ua.getTimer_setting();
        int minSe = prefs.getInteger(PhonexConfig.TIMER_MIN_SE);
        int sessExp = prefs.getInteger(PhonexConfig.TIMER_SESS_EXPIRES);
        if (minSe <= sessExp && minSe >= 90) {
            timerSetting.setMin_se(minSe);
            timerSetting.setSess_expires(sessExp);
            ua.setTimer_setting(timerSetting);
        }

        // DNS
        if (prefs.enableDNSSRV() && !prefs.useIPv6()) {
            pj_str_t[] nameservers = getDns();
            if (nameservers != null) {
                ua.setNameserver_count(nameservers.length);
                ua.setNameserver(nameservers);
            } else {
                ua.setNameserver_count(0);
            }
        }

        // STUN
        int isStunEnabled = prefs.getStunEnabled();
        if (isStunEnabled == 1) {
            String[] servers = prefs.getString(PhonexConfig.STUN_SERVER).split(",");
            pj_str_t[] stunServers = null;
            int curCount = 0;

            ua.setStun_srv_cnt(servers.length);
            stunServers = ua.getStun_srv();
            for (String server : servers) {
                Log.df(TAG, "Stun server [%s]", server.trim());
                stunServers[curCount] = Xvi.pj_str_copy(server.trim());
                curCount++;
            }

            ua.setStun_srv(stunServers);
            ua.setStun_map_use_stun2(PjUtils.toPjBool(prefs.getBoolean(PhonexConfig.ENABLE_STUN2)));
        }
    }

    /**
     * Initializes media Pj configuration.
     */
    private void initMediaConfig(){
        int threadCount = prefs.getInteger(PhonexConfig.THREAD_COUNT);
        if (threadCount <= 0) {
            threadCount = 1;
        }

        Xvi.media_config_default(media);

        // For now only this cfg is supported
        media.setChannel_count(1);
        media.setSnd_auto_close_time(prefs.getAutoCloseTime());
        // Echo cancellation
        media.setEc_tail_len(prefs.getEchoCancellationTail());
        int echoMode = prefs.getInteger(PhonexConfig.ECHO_CANCELLATION_MODE);
        long clockRate = prefs.getClockRate();
        if (clockRate > 16000 && echoMode == PhonexConfig.ECHO_MODE_WEBRTC_M) {
            Log.wf(TAG, "Sampling rate is too high for WebRTC [%d]", clockRate);
            echoMode = PhonexConfig.ECHO_MODE_SIMPLE;
        }

        media.setEc_options(echoMode);
        media.setNo_vad(PjUtils.toPjBool(prefs.getBoolean(PhonexConfig.DISABLE_VAD)));
        media.setQuality(prefs.getMediaQuality());
        media.setClock_rate(clockRate);
        media.setAudio_frame_ptime(prefs.getInteger(PhonexConfig.AUDIO_FRAME_PTIME));

        // Disabled ? because only one thread enabled now for battery
        // perfs on normal state
        int mediaThreadCount = prefs.getInteger(PhonexConfig.MEDIA_THREAD_COUNT);
        media.setThread_cnt(mediaThreadCount);
        boolean hasOwnIoQueue = prefs.getBoolean(PhonexConfig.HAS_IO_QUEUE);
        if (threadCount <= 0) {
            // Global thread count is 0, so don't use sip one anyway
            hasOwnIoQueue = false;
        }
        media.setHas_ioqueue(hasOwnIoQueue ? 1 : 0);

        // ICE
        media.setEnable_ice(prefs.getIceEnabled());
        media.setIce_always_update(1);

        // ICE candidates blacklist.
        if (prefs.getBoolean(PhonexConfig.P2P_DISABLE)) {
            long ice_blacklist = getICEBlacklistMap(true);
            media.getIce_opt().setCand_blacklist_map(ice_blacklist);

            Log.vf(TAG, "Disabling P2P, mask: 0x%x", ice_blacklist);
        }

        // TURN
        int isTurnEnabled = prefs.getTurnEnabled();
        if (isTurnEnabled == 1) {
            SWIGTYPE_p_pj_stun_auth_cred creds = media.getTurn_auth_cred();
            String turnUser = prefs.getString(PhonexConfig.TURN_USERNAME);
            String turnPwd = prefs.getString(PhonexConfig.TURN_PASSWORD);

            media.setEnable_turn(isTurnEnabled);
            media.setTurn_server(Xvi.pj_str_copy(prefs.getTurnServer()));
            Xvi.set_turn_credentials(
                    Xvi.pj_str_copy(turnUser),
                    Xvi.pj_str_copy(turnPwd),
                    Xvi.pj_str_copy("*"), creds);

            Log.df(TAG, "TURN cred; user=[%s]; ISNULL(pwd)=%s", turnUser, ((turnPwd) == (null)));
            // Normally this step is useless as manipulating a pointer in C memory at this point, but in case this changes reassign
            media.setTurn_auth_cred(creds);
        } else {
            media.setEnable_turn(Xvi.PJ_FALSE);
        }
    }

    /**
     * Returns name servers.
     * @return
     */
    private pj_str_t[] getDns() {
        pj_str_t[] nameservers = null;

        if (!prefs.enableDNSSRV()) {
            return nameservers;
        }

        String prefsDNS = prefs.getString(PhonexConfig.OVERRIDE_NAMESERVER);
        if (!TextUtils.isEmpty(prefsDNS)) {
            nameservers = new pj_str_t[] { Xvi.pj_str_copy(prefsDNS) };
            return nameservers;
        }

        final String ipv6replace = "[ \\[\\]]";
        final String ipv4regex = "^\\d+(\\.\\d+){3}$";
        final String ipv6regex = "^[0-9a-f]+(:[0-9a-f]*)+:[0-9a-f]+$";
        List<String> dnsServers;
        List<String> dnsServersAll = new ArrayList<String>();
        List<String> dnsServersIpv4 = new ArrayList<String>();
        for (int i = 1; i <= 2; i++) {
            String dnsName = prefs.getSystemProp("net.dns" + i);
            if (TextUtils.isEmpty(dnsName)) {
                continue;
            }

            dnsName = dnsName.replaceAll(ipv6replace, "");
            if (TextUtils.isEmpty(dnsName) || dnsServersAll.contains(dnsName)) {
                continue;
            }

            if (dnsName.matches(ipv4regex) || dnsName.matches(ipv6regex)) {
                dnsServersAll.add(dnsName);
            }

            if (dnsName.matches(ipv4regex)) {
                dnsServersIpv4.add(dnsName);
            }
        }

        if (dnsServersIpv4.size() > 0) {
            dnsServers = dnsServersIpv4;
        } else {
            dnsServers = dnsServersAll;
        }

        if (dnsServers.size() == 0) {
            // This is the ultimate fallback... we should never be here
            nameservers = new pj_str_t[] {
                    Xvi.pj_str_copy("127.0.0.1")
            };
        } else if (dnsServers.size() == 1) {
            nameservers = new pj_str_t[] {
                    Xvi.pj_str_copy(dnsServers.get(0))
            };
        } else {
            nameservers = new pj_str_t[] {
                    Xvi.pj_str_copy(dnsServers.get(0)),
                    Xvi.pj_str_copy(dnsServers.get(1))
            };
        }

        return nameservers;
    }

    /**
     * Determine whether to use SRTP in Pj variables.
     * @return
     */
    private pjmedia_srtp_use getUseSrtp() {
        try {
            int use_srtp = PhonexSettings.useSrtp() ? 1 : 0;
            if (use_srtp >= 0) {
                return pjmedia_srtp_use.swigToEnum(use_srtp);
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Transport port not well formated");
        }

        return pjmedia_srtp_use.PJMEDIA_SRTP_DISABLED;
    }

    /**
     * Sets SSL cipher suite list for PJSIP.
     * Only secure TLS Cipher Suites are allowed.
     */
    public static void setSSlCipherSuites(pjsip_tls_setting tlsSetting){
        pj_ssl_cipher ciphers[] = new pj_ssl_cipher[] {
                pj_ssl_cipher.PJ_TLS_DH_DSS_WITH_AES_256_CBC_SHA256,
                pj_ssl_cipher.PJ_TLS_DH_RSA_WITH_AES_256_CBC_SHA256,
                pj_ssl_cipher.PJ_TLS_DH_DSS_WITH_AES_256_CBC_SHA,
                pj_ssl_cipher.PJ_TLS_DH_RSA_WITH_AES_256_CBC_SHA,
                pj_ssl_cipher.PJ_TLS_DH_DSS_WITH_AES_128_CBC_SHA256,
                pj_ssl_cipher.PJ_TLS_DH_RSA_WITH_AES_128_CBC_SHA256,
                pj_ssl_cipher.PJ_TLS_DH_DSS_WITH_AES_128_CBC_SHA,
                pj_ssl_cipher.PJ_TLS_DH_RSA_WITH_AES_128_CBC_SHA,

                pj_ssl_cipher.PJ_TLS_DHE_DSS_WITH_AES_256_CBC_SHA256,
                pj_ssl_cipher.PJ_TLS_DHE_RSA_WITH_AES_256_CBC_SHA256,
                pj_ssl_cipher.PJ_TLS_DHE_DSS_WITH_AES_256_CBC_SHA,
                pj_ssl_cipher.PJ_TLS_DHE_RSA_WITH_AES_256_CBC_SHA,
                pj_ssl_cipher.PJ_TLS_DHE_DSS_WITH_AES_128_CBC_SHA256,
                pj_ssl_cipher.PJ_TLS_DHE_RSA_WITH_AES_128_CBC_SHA256,
                pj_ssl_cipher.PJ_TLS_DHE_DSS_WITH_AES_128_CBC_SHA,
                pj_ssl_cipher.PJ_TLS_DHE_RSA_WITH_AES_128_CBC_SHA,

                pj_ssl_cipher.PJ_TLS_RSA_WITH_AES_128_CBC_SHA256,
                pj_ssl_cipher.PJ_TLS_RSA_WITH_AES_256_CBC_SHA256,
                pj_ssl_cipher.PJ_TLS_RSA_WITH_AES_128_CBC_SHA,
                pj_ssl_cipher.PJ_TLS_RSA_WITH_AES_256_CBC_SHA,

                pj_ssl_cipher.PJ_TLS_RSA_WITH_IDEA_CBC_SHA,
        };

        SWIGTYPE_p_pj_ssl_cipher cipSuiteArr = Xvi.new_sslCipherArray(ciphers.length);
        for(int i=0, j=ciphers.length; i<j; i++){
            Xvi.sslCipherArray_setitem(cipSuiteArr, i, ciphers[i]);
        }

        tlsSetting.setCiphers(cipSuiteArr);
        tlsSetting.setCiphers_num(ciphers.length);
        Log.vf(TAG, "CipherSuites set for SIP stack. Length=%s", ciphers.length);
    }

    /**
     * Returns blacklist map for ICE.
     * @param blockP2P
     * @return
     */
    public static long getICEBlacklistMap(boolean blockP2P){
        long ice_blacklist = 0;
        if (blockP2P) {
            ice_blacklist |= 1 << pj_ice_cand_type.PJ_ICE_CAND_TYPE_HOST.swigValue();
            ice_blacklist |= 1 << pj_ice_cand_type.PJ_ICE_CAND_TYPE_PRFLX.swigValue();
            ice_blacklist |= 1 << pj_ice_cand_type.PJ_ICE_CAND_TYPE_SRFLX.swigValue();
        }

        return ice_blacklist;
    }

}

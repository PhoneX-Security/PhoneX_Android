package net.phonex.pref;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaRecorder.AudioSource;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings.System;
import android.telephony.TelephonyManager;

/**
 * Manage global configuration of the application.
 * Provides wrapper around preference content provider
 * and define preference keys constants,
 *
 * These preferences are UNENCRYPTED by default.
 */
public class PhonexConfig {

    // Media
    /**
     * Media quality, 0-10.<br/>
     * according to this table: 5-10: resampling use large filter<br/>
     * 3-4: resampling use small filter<br/>
     * 1-2: resampling use linear.<br/>
     * The media quality also sets speex codec quality/complexity to the number.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__media__config.htm#a4cada00a781ce06cd536c4e56a522065"
     * >Pjsip documentation</a>
     * 
     * @see #setIntegerPref(Context, String, Integer)
     */
    public static final String MEDIA_QUALITY = "media_quality";

    /**
     * Echo canceller tail length, in miliseconds.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__media__config.htm#a82c1cf18d42f5ec0a645ed2bdd6ae955"
     * >Pjsip documentation</a>
     * 
     * @see #setIntegerPref(Context, String, Integer)
     */
    public static final String ECHO_CANCELLATION_TAIL_LEN = "ec_tail_len";

    /**
     * Starting RTP port number.
     * 
     * @see #setIntegerPref(Context, String, Integer)
     */
    public static final String PJ_RTP_PORT = "pj_rtp_port";

    /**
     * Port to use for TCP transport.
     * 
     * @see #setIntegerPref(Context, String, Integer)
     */
    public static final String PJ_TCP_PORT = "pj_tcp_port";

    /**
     * Port to use for UDP transport.
     * 
     * @see #setIntegerPref(Context, String, Integer)
     */
    public static final String PJ_UDP_PORT = "pj_udp_port";

    /**
     * Specify idle time of sound device before it is automatically closed, in
     * seconds. <br/>
     * Use value -1 to disable the auto-close feature of sound device<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__media__config.htm#a2c95e5ce554bbee9cc60d0328f508658"
     * >Pjsip documentation</a>
     * 
     * @see #setIntegerPref(Context, String, Integer)
     */
    public static final String SND_AUTO_CLOSE_TIME = "snd_auto_close_time";

    /**
     * Clock rate to be applied to the conference bridge.<br/>
     * If value is zero, default clock rate will be used
     * (PJSUA_DEFAULT_CLOCK_RATE, which by default is 16KHz).<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__media__config.htm#a24792c277d6c6c309eccda9047f641a5"
     * >Pjsip documentation</a>
     * 
     * @see #setIntegerPref(Context, String, Integer)
     */
    public static final String SND_CLOCK_RATE = "snd_clock_rate";

    /**
     * Enable echo cancellation ?
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String ECHO_CANCELLATION = "echo_cancellation";

    /**
     * Enable VAD?<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__media__config.htm#a9f99f0f3d10e14a7a0f75c7f2da8473b"
     * >Pjsip documentation</a>
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String DISABLE_VAD = "disable_vad";

    /**
     * Enable noise suppression ?
     * Only working if echo cancellation activated and webRTC echo canceller backend used.
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String ENABLE_NOISE_SUPPRESSION = "enable_noise_suppression";

    /**
     * Default micro amplification between 0.0 and 10.0.
     * 
     * @see #setFloatPref(Context, String, Float)
     */
    public static final String SOUND_MIC_VOLUME = "sound_mic_volume";

    /**
     * Default speaker amplification between 0.0 and 10.0.
     * 
     * @see #setFloatPref(Context, String, Float)
     */
    public static final String SOUND_SPEAKER_VOLUME = "sound_speaker_volume";

    /**
     * Default Bluethooth micro amplification between 0.0 and 10.0.
     * 
     * @see #setFloatPref(Context, String, Float)
     */
    public static final String SOUND_BT_MIC_VOLUME = "sound_bt_mic_volume";

    /**
     * Default Bluethooth speaker amplification between 0.0 and 10.0.
     * 
     * @see #setFloatPref(Context, String, Float)
     */
    public static final String SOUND_BT_SPEAKER_VOLUME = "sound_bt_speaker_volume";

    /**
     * This option is not used anymore because requires multiple working thread
     * that is not suitable for mobility mode. <br/>
     * Specify whether the media manager should manage its own ioqueue for the
     * RTP/RTCP sockets. <br/>
     * If yes, ioqueue will be created and at least one worker thread will be
     * created too. <br/>
     * If no, the RTP/RTCP sockets will share the same ioqueue as SIP sockets,
     * and no worker thread is needed.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__media__config.htm#ab1ddd57bc94ed7f5a64c819414cb9f96"
     * >Pjsip documentation</a>
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String HAS_IO_QUEUE = "has_io_queue";

    /**
     * Media thread count
     */
    public static final String MEDIA_THREAD_COUNT = "media_thread_count";

    /**
     * Sip stack thread count
     */
    public static final String THREAD_COUNT = "thread_count";

    /**
     * Backend for echo cancellation. <br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__media__config.htm#a734653d7e5d075984b9a51f053ded879"
     * >Pjsip documentation</a>
     * 
     * @see #setIntegerPref(Context, String, Integer)
     * @see #ECHO_MODE_AUTO
     * @see #ECHO_MODE_SIMPLE
     * @see #ECHO_MODE_SPEEX
     * @see #ECHO_MODE_WEBRTC_M
     */
    public static final String ECHO_CANCELLATION_MODE = "echo_cancellation_mode";

    /**
     * Specify audio frame ptime. <br/>
     * The value here will affect the samples per frame of both the sound device
     * and the conference bridge. <br/>
     * Specifying lower ptime will normally reduce the latency.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__media__config.htm#ac6e637f5fdd868c8e77a1d1f5e9d1a51"
     * >Pjsip documentation</a>
     * 
     * @see #setIntegerPref(Context, String, Integer)
     */
    public static final String AUDIO_FRAME_PTIME = "audio_frame_ptime";

    /**
     * Should we generate a silent tone just after the audio is established as a workaround to some devices.<br/>
     * This is useful for some samsung devices. <br/>
     * Leave it managed by app if you want to benefit auto detection
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String SET_AUDIO_GENERATE_TONE = "set_audio_generate_tone";

    /**
     * Should the application use the android legacy route api to route to speaker/earpiece?<br/>
     * Leave it managed by app if you want to benefit auto detection
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String USE_ROUTING_API = "use_routing_api";

    /**
     * Should the application use android {@link AudioManager#MODE_IN_CALL} and
     * {@link AudioManager#MODE_NORMAL} modes to route to speaker/earpiece?<br/>
     * Leave it managed by app if you want to benefit auto detection
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String USE_MODE_API = "use_mode_api";

    /**
     * Which mode to use when in a sip call.
     * 
     * @see #setIntegerPref(Context, String, Integer)
     * @see AudioManager#MODE_IN_CALL
     * @see AudioManager#MODE_IN_COMMUNICATION
     * @see AudioManager#MODE_NORMAL
     * @see AudioManager#MODE_RINGTONE
     */
    public static final String CALL_AUDIO_MODE = "call_audio_mode";

    /**
     * Which audio source to use when in a sip call.
     * 
     * @see #setIntegerPref(Context, String, Integer)
     * @see AudioSource#DEFAULT
     * @see AudioSource#MIC
     * @see AudioSource#VOICE_CALL
     * @see AudioSource#VOICE_COMMUNICATION
     * @see AudioSource#VOICE_DOWNLINK
     * @see AudioSource#VOICE_RECOGNITION
     * @see AudioSource#VOICE_UPLINK
     */
    public static final String MICRO_SOURCE = "micro_source";

    /**
     * Should the application use webRTC library code to setup audio.
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String USE_WEBRTC_HACK = "use_webrtc_hack";

    /**
     * Should we focus audio stream used by the application.<br/>
     * It will for example allows to mute music app while in call
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String ENABLE_FOCUS_AUDIO = "enable_focus_audio";

    /**
     * Level of android audio stream when starting call.<br/>
     * This is the android audio level. <br/>
     * Between 0.0 and 1.0
     * 
     * @see #setFloatPref(Context, String, Float)
     */
    public static final String SOUND_INIT_VOLUME = "snd_init_volume";

    /**
     * Action to perform when headset button is pressed.
     * 
     * @see #setIntegerPref(Context, String, Integer)
     * @see #HEADSET_ACTION_CLEAR_CALL
     * @see #HEADSET_ACTION_HOLD
     * @see #HEADSET_ACTION_MUTE
     */
    public static final String HEADSET_ACTION = "headset_action";

    /**
     * Backend implementation to use for audio calls.<br/>
     * Since android has several ways to plug to audio layer <br/>
     * 
     * @see #setIntegerPref(Context, String, Integer)
     * @see #AUDIO_IMPLEMENTATION_JAVA
     * @see #AUDIO_IMPLEMENTATION_OPENSLES
     */
    public static final String AUDIO_IMPLEMENTATION = "audio_implementation";

    /**
     * If set to true, bluetooth SCO is turned on by default on call.<br/>
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String BLUETOOTH_DEFAULT_ON = "bluetooth_default_on";

    /**
     * Should we automatically connect audio to speaker when call becomes active.
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String AUTO_CONNECT_SPEAKER = "auto_connect_speaker";

    /**
     * Should we activate speaker automatically based on proximity and screen orientation.<br/>
     * The speaker will be automatically turned on when phone is horizontal and off when vertical.
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String AUTO_DETECT_SPEAKER = "auto_detect_speaker";
    
    /**
     * Should the entire audio stream be restarted when audio routing change is asked ?<br/>
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String RESTART_AUDIO_ON_ROUTING_CHANGES = "restart_aud_on_routing_change";
    
    /**
     * Should audio routing be done before media stream start ? <br/>
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String SETUP_AUDIO_BEFORE_INIT = "setup_audio_before_init";

    /**
     * Suffix key for the number of frames per RTP packet for one codec. <br/>
     * To be prefixed with {codec rtp name}_{codec clock rate}_.
     * You can use {@link #getCodecKey(String, String)} if you have codec in form G729/8000 for example.
     */
    public static final String FRAMES_PER_PACKET_SUFFIX = "fpp";
    
    /**
     * H264 Codec profile.<br/>
     * 66 : baseline
     * 77 : mainline 
     */
    public static final String H264_PROFILE = "codec_h264_profile";

    /**
     * H264 Codec level.<br/>
     * 10 for 1.0, 20 for 2.0, 31 for 3.1 etc
     */
    public static final String H264_LEVEL = "codec_h264_level";

    /**
     * H264 Codec bitrate in kbps.<br/>
     * Use 0 for default bitrate for level.
     */
    public static final String H264_BITRATE = "codec_h264_bitrate";

    /**
     * Should we use software volume instead of android audio volume? <br/>
     * Some manufacturers are buggy with android audio volume for the stream
     * used for voice over ip calls <br/>
     * Using software volume force to emulate volume change instide the software
     * instead of using android feature
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String USE_SOFT_VOLUME = "use_soft_volume";

    /**
     * Prevent UI screen rotation?
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String PREVENT_SCREEN_ROTATION = "prevent_screen_rotation";

    /**
     * Set the logging level of the application. <br/>
     * <ul>
     * <li>1 : error</li>
     * <li>2 : warning</li>
     * <li>3 : info</li>
     * <li>4 : debug</li>
     * <li>5 : verbose</li>
     * </ul>
     * 
     * @see #setIntegerPref(Context, String, Integer)
     */
    public static final String LOG_LEVEL = "log_level";
    
    /**
     * Use direct file logging instead of use of logcat. <br/>
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String LOG_USE_DIRECT_FILE = "log_use_direct_file";
    
    /**
     * Copy logcat file. <br/>
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String LOG_TO_FILE = "log_to_file";

    /**
     * Should the application force the screen to remain on when a call is
     * ongoing and calling over wifi.<br/>
     * This is particularly useful for devices affected by the PSP behavior :<br/>
     * These devices turn the wifi card into a slow mode when screen is off.
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String KEEP_AWAKE_IN_CALL = "keep_awake_incall";

    /**
     * Should we assume that proximity sensor values are inverted?<br/>
     * Let app automatically manage this setting if you want auto
     * detection to work
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String INVERT_PROXIMITY_SENSOR = "invert_proximity_sensor";

    /**
     * Should the application take a partial lock when sip is registered?<br/>
     * This particular wake lock will ensures CPU is running which leads to
     * higher battery consumption
     * 
     * @see PowerManager#PARTIAL_WAKE_LOCK
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String USE_PARTIAL_WAKE_LOCK = "use_partial_wake_lock";

    /**
     * The default ringtone uri to setup if no ringtone is found for incoming call.<br/>
     * If empty will get the default ringtone of android.
     * 
     * @see System#RINGTONE
     * @see #setStringPref(Context, String, String)
     */
    public static final String RINGTONE = "ringtone";

    /**
     * Specify TURN domain name or host name, in "DOMAIN:PORT" or "HOST:PORT"
     * format.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__media__config.htm#ac4761935f0f0bd1271dafde91ca8f83d"
     * >Pjsip documentation</a>
     * 
     * @see #setStringPref(Context, String, String)
     */
    public static final String TURN_SERVER = "turn_server";

    /**
     * Enable TURN relay candidate in ICE.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__media__config.htm#ac4761935f0f0bd1271dafde91ca8f83d"
     * >Pjsip documentation</a>
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String ENABLE_TURN = "enable_turn";

    /**
     * Specify username to use wnen authenticating with the TURN server.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__media__config.htm#a4305f174d0f9e3497b3e344236aeea91"
     * >Pjsip documentation</a>
     * 
     * @see #setStringPref(Context, String, String)
     */
    public static final String TURN_USERNAME = "turn_username";

    /**
     * Specify password to use when authenticating with the TURN server.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__media__config.htm#a4305f174d0f9e3497b3e344236aeea91"
     * >Pjsip documentation</a>
     * 
     * @see #setStringPref(Context, String, String)
     */
    public static final String TURN_PASSWORD = "turn_password";

    /**
     * Enable ICE.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__media__config.htm#a8c3030ecc6b84a888f49f6b3e1b204a9"
     * >Pjsip documentation</a>
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String ENABLE_ICE = "enable_ice";

    /**
     * Enable STUN.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__config.htm#abec69c2c899604352f3450368757f39b"
     * >Pjsip documentation</a>
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String ENABLE_STUN = "enable_stun";

    /**
     * Stun server.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__config.htm#abec69c2c899604352f3450368757f39b"
     * >Pjsip documentation</a><br/>
     * If you want to set more than one server, separate it with commas.
     * 
     * @see #setStringPref(Context, String, String)
     */
    public static final String STUN_SERVER = "stun_server";

    /**
     * Enable STUN new format.<br/>
     * This specifies whether STUN requests for resolving socket mapped
     * address should use the new format, i.e: having STUN magic cookie
     * in its transaction ID.
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__config.htm#abec69c2c899604352f3450368757f39b"
     * >Pjsip documentation</a>
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String ENABLE_STUN2 = "enable_stun2";
    
    /**
     * Use IPv6 support.<br/>
     * This has no effect now since the application by default supports IPv6
     * except for DNS resolution<br/>
     * This is a limitation of pjsip which resolution is in pjsip roadmap.
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String USE_IPV6 = "use_ipv6";

    /**
     * Enable UDP transport.
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String ENABLE_UDP = "enable_udp";

    /**
     * Enable TCP transport.
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String ENABLE_TCP = "enable_tcp";

    /**
     * Does the LOCK_WIFI ensures performance of wifi as well?
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String LOCK_WIFI = "lock_wifi";

    /**
     * Does the {@link #LOCK_WIFI} ensures performance of wifi as well.<br/>
     * This should not be required and could lead to higher battery usage
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String LOCK_WIFI_FULL_HIGH_PERF = "lock_wifi_full_high_perf";

    /**
     * Enable DNS SRV feature.<br/>
     * By default disabled, the DNS resolution is made using android system
     * directly <br/>
     * If activated the application will do dns srv requests which is slower. <br/>
     * It also requires to have dns servers. These will be retrieved from
     * android os, or can be set using {@link #OVERRIDE_NAMESERVER}
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String ENABLE_DNS_SRV = "enable_dns_srv";

    /**
     * Enable QoS.
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String ENABLE_QOS = "enable_qos";

    /**
     * DSCP value for SIP packets.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjlib/docs/html/structpj__qos__params.htm#afa7a796d83d188894d207ebba951e425"
     * >Pjsip documentation</a><br/>
     * 
     * @see #setIntegerPref(Context, String, Integer)
     */
    public static final String DSCP_VAL = "dscp_val";

    /**
     * DSCP value for RTP packets.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjlib/docs/html/structpj__qos__params.htm#afa7a796d83d188894d207ebba951e425"
     * >Pjsip documentation</a><br/>
     * 
     * @see #setIntegerPref(Context, String, Integer)
     */
    public static final String DSCP_RTP_VAL = "dscp_rtp_val";

    /**
     * Send UDP socket keep alive when connected using wifi, in seconds.
     * 
     * @see #setIntegerPref(Context, String, Integer)
     */
    public static final String KEEP_ALIVE_INTERVAL_WIFI = "keep_alive_interval_wifi";

    /**
     * Send UDP socket keep alive when connected using mobile, in seconds.
     * 
     * @see #setIntegerPref(Context, String, Integer)
     */
    public static final String KEEP_ALIVE_INTERVAL_MOBILE = "keep_alive_interval_mobile";

    /**
     * Send TCP socket keep alive when connected using wifi, in seconds.
     * 
     * @see #setIntegerPref(Context, String, Integer)
     */
    public static final String TCP_KEEP_ALIVE_INTERVAL_WIFI = "tcp_keep_alive_interval_wifi";

    /**
     * Send TCP socket keep alive when connected using mobile, in seconds.
     * 
     * @see #setIntegerPref(Context, String, Integer)
     */
    public static final String TCP_KEEP_ALIVE_INTERVAL_MOBILE = "tcp_keep_alive_interval_mobile";

    /**
     * Send TLS socket keep alive when connected using wifi, in seconds.
     * 
     * @see #setIntegerPref(Context, String, Integer)
     */
    public static final String TLS_KEEP_ALIVE_INTERVAL_WIFI = "tls_keep_alive_interval_wifi";

    /**
     * Send TLS socket keep alive when connected using mobile, in seconds.
     * 
     * @see #setIntegerPref(Context, String, Integer)
     */
    public static final String TLS_KEEP_ALIVE_INTERVAL_MOBILE = "tls_keep_alive_interval_mobile";

    /**
     * DNS to override instead of using the one configured in android
     * OS.<br/>
     * For now only supports one alternate dns.
     * 
     * @see #setStringPref(Context, String, String)
     */
    public static final String OVERRIDE_NAMESERVER = "override_nameserver";

    /**
     * Use compact form for sip headers and sdp.<br/>
     * This will minimize size of packets sends.<br/>
     * Take care with this option because some sip server does not manage it
     * properly
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String USE_COMPACT_FORM = "use_compact_form";

    /**
     * Change the user agent of the application.<br/>
     * By default if it's the name of the application, it will add extra information
     * about the device
     * 
     * @see #setStringPref(Context, String, String)
     */
    public static final String USER_AGENT = "user_agent";

    /**
     * Avoid the use of UPDATE.<br/>
     * This will ignore what's announced by remote part as feature which is
     * useful for remote part that are buggy with that
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String FORCE_NO_UPDATE = "force_no_update";

    /**
     * Specify minimum session expiration period, in seconds. Must not be lower
     * than 90. Default is 90.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsip__timer__setting.htm#a313ff979b8e59590ec6d50cfa993768b"
     * >Pjsip documentation</a><br/>
     * 
     * @see #setIntegerPref(Context, String, Integer)
     */
    public static final String TIMER_MIN_SE = "timer_min_se";

    /**
     * Specify session expiration period, in seconds. Must not be lower than
     * min_se. Default is 1800.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsip__timer__setting.htm#ae1923dbb2330ce7dbffa37042a50e727"
     * >Pjsip documentation</a><br/>
     * 
     * @see #setIntegerPref(Context, String, Integer)
     */
    public static final String TIMER_SESS_EXPIRES = "timer_sess_expires";

    /**
     * Transaction T1 timeout value.<br/>
     * Timeout of SIP transactions.
     * -1 for default values
     * 
     * @see #setIntegerPref(Context, String, Integer)
     */
    public static final String TSX_T1_TIMEOUT = "tsx_t1_timeout";

    /**
     * Transaction T2 timeout value.<br/>
     * Timeout of SIP transactions.
     * -1 for default values
     * 
     * @see #setIntegerPref(Context, String, Integer)
     */
    public static final String TSX_T2_TIMEOUT = "tsx_t2_timeout";

    /**
     * Transaction T4 timeout value.<br/>
     * Timeout of SIP transactions.
     * -1 for default values
     * 
     * @see #setIntegerPref(Context, String, Integer)
     */
    public static final String TSX_T4_TIMEOUT = "tsx_t4_timeout";

    /**
     *  Transaction TD timeout value.
     *  Transaction completed timer for INVITE.
     *  -1 for default values
     *  
     * @see #setIntegerPref(Context, String, Integer)
     */
    public static final String TSX_TD_TIMEOUT = "tsx_td_timeout";
    
    /**
     * Whether media negotiation should include SDP
     * bandwidth modifier "TIAS" (RFC3890).
     * This option is known to be needed to have video working on
     * some Avaya server. It's also known to break buggy SDP parser
     * of some mainstream SIP providers.
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String ADD_BANDWIDTH_TIAS_IN_SDP = "add_bandwidth_tias_in_sdp";

    /**
     * Enable TLS transport.
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String ENABLE_TLS = "enable_tls";

    /**
     * Local port to bind to for TLS transport.<br/>
     * This is the listen port of the application. 0 means automatic
     * 
     * @see #setIntegerPref(Context, String, Integer)
     */
    public static final String TLS_TRANSPORT_PORT = "network_tls_transport_port";

    /**
     * Certificate of Authority (CA) list file. <br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsip__tls__setting.htm#a96d826c6675c08e465e9dee11f1114d7"
     * >Pjsip documentation</a><br/>
     * 
     * @see #setStringPref(Context, String, String)
     */
    public static final String CA_LIST_FILE = "ca_list_file";

    /**
     * Client certificate file, which will be used for outgoing TLS connections,
     * and server-side certificate for incoming TLS connection.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsip__tls__setting.htm#a03c3308853ef75a0c76d07ddc8227171"
     * >Pjsip documentation</a><br/>
     * 
     * @see #setStringPref(Context, String, String)
     */
    public static final String CERT_FILE = "cert_file";

    /**
     * Optional private key for the endpoint certificate to be used.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsip__tls__setting.htm#a76e62480d01210a7cc21b7bf7c94a89f"
     * >Pjsip documentation</a><br/>
     * 
     * @see #setStringPref(Context, String, String)
     */
    public static final String PRIVKEY_FILE = "privkey_file";

    /**
     * Username associated to the private key.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsip__tls__setting.htm#aa6d4b029668bf017162d4b1d09477fe5"
     * >Pjsip documentation</a><br/>
     * 
     * @see #setStringPref(Context, String, String)
     */
    public static final String TLS_USER = "tls_user";

    /**
     * Default behavior when TLS verification fails on the server side.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsip__tls__setting.htm#aebbfb646cdfc7151edce2b5194cdbddb"
     * >Pjsip documentation</a><br/>
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String TLS_VERIFY_SERVER = "tls_verify_server";

    /**
     * Default behavior when TLS verification fails on the client side.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsip__tls__setting.htm#ade2b579f76aac470c27c6813a1f85b3c"
     * >Pjsip documentation</a><br/>
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String TLS_VERIFY_CLIENT = "tls_verify_client";

    /**
     * TLS protocol method from pjsip_ssl_method.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsip__tls__setting.htm#a3a453c419c092ecc05f0141da36183fa"
     * >Pjsip documentation</a><br/>
     * 
     * @see #setIntegerPref(Context, String, Integer)
     */
    public static final String TLS_METHOD = "tls_method";

    /**
     * Interval for polling network routes. <br/>
     * This is useful to set if using VPN on android 4.0
     * 
     * @see #setIntegerPref(Context, String, Integer)
     */
    public static final String NETWORK_ROUTES_POLLING = "network_route_polling";
    
    /**
     * Interval for polling registration status.
     * 
     * @see #setIntegerPref(Context, String, Integer)
     */
    public static final String NETWORK_WATCHDOG = "network_watchdog";

    /**
     * Enable wifi for incoming calls
     *
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String USE_WIFI_IN = "use_wifi_in";

    /**
     * Enable wifi for outgoing calls
     *
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String USE_WIFI_OUT = "use_wifi_out";

    /**
     * Enable other networks for incoming calls
     *
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String USE_OTHER_IN = "use_other_in";

    /**
     * Enable other networks for outgoing calls
     *
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String USE_OTHER_OUT = "use_other_out";

    /**
     * Enable anyway for incoming calls
     *
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String USE_ANYWAY_IN = "use_anyway_in";

    /**
     * Enable anyway for outgoing calls
     *
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String USE_ANYWAY_OUT = "use_anyway_out";

    /**
     * Default display name to use for sip contact.<br/>
     * This can be overriden per account.
     * 
     * @see #setStringPref(Context, String, String)
     */
    public static final String DEFAULT_CALLER_ID = "default_caller_id";

    /**
     * Should the application allow multiple calls?<br/>
     * Disabling it can help when the app multiply registers
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String SUPPORT_MULTIPLE_CALLS = "support_multiple_calls";

    /**
     * Does the application enable video calls by default?<br/>
     * This setting is not yet stable because video feature is not fully
     * integrated yet.
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String USE_VIDEO = "use_video";

    /**
     * Video capture size in form (width)x(height)@(fps)
     * 
     * @see #setStringPref(Context, String, String)
     */
    public static final String VIDEO_CAPTURE_SIZE = "video_capture_size";
    
    /**
     * Should the stack never switch to TCP when packets are too big?
     * 
     * @see #setBooleanPref(Context, String, boolean)
     */
    public static final String DISABLE_TCP_SWITCH = "disable_tcp_switch";

    /**
     * Are PhoneX updates using PhoneX servers allowed?
     */
    public static final String PHONEX_UPDATES = "phonex_updates";
    
    /**
     * Whether screenshots attempts should be blocked.
     */
    public static final String PHONEX_BLOCK_SCREENSHOTS = "phonex_block_screenshots"; 
    
    /**
     * Allow to publish in-call state. If allowed, new user presence state
     * with INCALL status is published. 
     */
    public static final String PUBLISH_IN_CALL_STATE = "publish_incall_state";

    // Enums
    /**
     * Automatic echo mode.
     * 
     * @see #ECHO_CANCELATION_MODE
     */
    public static final int ECHO_MODE_AUTO = 0;

    /**
     * Simple echo mode. It's a basic implementation
     * 
     * @see #ECHO_CANCELATION_MODE
     */
    public static final int ECHO_MODE_SIMPLE = 1;

    /**
     * Accoustic echo cancellation of Speex
     * 
     * @see #ECHO_CANCELATION_MODE
     */
    public static final int ECHO_MODE_SPEEX = 2;

    /**
     * Accoustic echo cancellation of WebRTC
     * 
     * @see #ECHO_CANCELATION_MODE
     */
    public static final int ECHO_MODE_WEBRTC_M = 3;

    /**
     * Pressing headset button hangup the call.
     * 
     * @see #HEADSET_ACTION
     */
    public static final int HEADSET_ACTION_CLEAR_CALL = 0;

    /**
     * Pressing headset button mute the call
     * 
     * @see #HEADSET_ACTION
     */
    public static final int HEADSET_ACTION_MUTE = 1;

    /**
     * Pressing headset button hold the call
     * 
     * @see #HEADSET_ACTION
     */
    public static final int HEADSET_ACTION_HOLD = 2;

    /**
     * Uses java/jni implementation audio implementation.
     * 
     * @see #AUDIO_IMPLEMENTATION
     */
    public static final int AUDIO_IMPLEMENTATION_JAVA = 0;

    /**
     * Uses opensl-ES implementation audio implementation.
     * 
     * @see #AUDIO_IMPLEMENTATION
     */
    public static final int AUDIO_IMPLEMENTATION_OPENSLES = 1;

    public static final String PREFS_TABLE_NAME = "prefs";
    public static final String RESET_TABLE_NAME = "reset_preferences";
    public static final String PIN_LOCK_IF_NO_RECENT_TICK_TABLE_NAME = "pin_action_lock_if_no_recent_tick";

    /**
     * Authority for preference content provider. <br/>
     * Maybe be changed for forked versions of the app.
     */
    public static final String AUTHORITY = "net.phonex.prefs";

    public static final String APP_CLOSED_BY_USER = "closed_by_user";
    public static final String QUICKLOGIN_USED = "quicklogin_used";
    public static final String DEVELOPER_MODE = "developer_mode";
    public static final String LAST_SIP_STACK_START_TIME = "last_sip_stack_start_time";
    public static final String HAS_ALREADY_SETUP_SERVICE = "has_already_setup_service";
    private static final String BASE_DIR_TYPE = "vnd.android.cursor.dir/vnd.phonex";
    private static final String BASE_ITEM_TYPE = "vnd.android.cursor.item/vnd.phonex";

    /**
     * Content type for preference provider.
     */
    public static final String PREF_CONTENT_TYPE = BASE_DIR_TYPE + ".pref";

    /**
     * FileItemInfo type for preference provider.
     */
    public static final String PREF_CONTENT_ITEM_TYPE = BASE_ITEM_TYPE + ".pref";

    /**
     * Uri for preference content provider.<br/>
     * Deeply advised to not use directly
     * 
     * @see #setBooleanPref(Context, String, boolean)
     * @see #setFloatPref(Context, String, Float)
     * @see #setIntegerPref(Context, String, Integer)
     * @see #setStringPref(Context, String, String)
     */
    public static final Uri PREF_URI = Uri.parse(ContentResolver.SCHEME_CONTENT + "://" + AUTHORITY + "/"
            + PREFS_TABLE_NAME);

    /**
     * Base uri for a specific preference in the content provider.<br/>
     * Deeply advised to not use directly
     * 
     * @see #setBooleanPref(Context, String, boolean)
     * @see #setFloatPref(Context, String, Float)
     * @see #setIntegerPref(Context, String, Integer)
     * @see #setStringPref(Context, String, String)
     */
    private static final Uri PREF_ID_URI_BASE = Uri.parse(ContentResolver.SCHEME_CONTENT + "://" + AUTHORITY + "/"
            + PREFS_TABLE_NAME + "/");


    public static final Uri PIN_LOCK_IF_NO_RECENT_TICK_URI = Uri.parse(ContentResolver.SCHEME_CONTENT + "://" + AUTHORITY + "/"
            + PIN_LOCK_IF_NO_RECENT_TICK_TABLE_NAME);

    // Raz
    /**
     * Reset uri to wipe the entire preference database clean.
     */
    public static final Uri ERASE_URI = Uri
            .parse(ContentResolver.SCHEME_CONTENT + "://" + AUTHORITY + "/" + RESET_TABLE_NAME);

    /**
     * Content value key for preference name.<br/>
     * It is strongly advised that you do NOT use this directly.
     * 
     * @see #setBooleanPref(Context, String, boolean)
     * @see #setFloatPref(Context, String, Float)
     * @see #setIntegerPref(Context, String, Integer)
     * @see #setStringPref(Context, String, String)
     */
    public static final String FIELD_NAME = "name";

    /**
     * Content value key for preference value.<br/>
     * It is strongly advised that you do NOT use this directly.
     * 
     * @see #setBooleanPref(Context, String, boolean)
     * @see #setFloatPref(Context, String, Float)
     * @see #setIntegerPref(Context, String, Integer)
     * @see #setStringPref(Context, String, String)
     */
    public static final String FIELD_VALUE = "value";

    /**
     * Narrow band type codec preference key.<br/>
     * 
     * @see #getCodecKey(String, String)
     */
    public static final String CODEC_NB = "nb";

    /**
     * Wide band type codec preference key.<br/>
     * 
     * @see #getCodecKey(String, String)
     */
    public static final String CODEC_WB = "wb";

    /**
     * Messages resend attempts preference key.<br/>
     * Returns number of attempts to resend failed outgoing message<br/>
     * 
     */
    public static final String MESSAGES_DELETE_PERIOD= "messages_delete_period";
    
    /**
     * Drop INVITE packet if it has invalid digital signature.<br/>
     * Should prevent identity theft from attackers or re-invite zrtp-hash changing. <br/>
     */
	public static final String DROP_BAD_INVITE = "drop_bad_invite";

    /**
     * Drop BYE packet if it has invalid digital signature.<br/>
     * This should prevent from dropping calls by attackers causing DoS. <br/>
     */
	public static final String DROP_BAD_BYE = "drop_bad_bye";
	
	/**
     * Whether to show warnings in UI about received packets with missing/invalid digital signature.<br/>
     */
	public static final String SHOW_SIPSIG_WARNINGS = "show_sipsig_warnings";
	
	/**
     * Explicit application locale.
     */
	public static final String LANGUAGE = "language";
	
	/**
	 * Flag signalizing to use debug data server.
	 */
	public static final String USE_DEVEL_DATA_SERVER="use_devel_data_server";
	
	/**
	 * Default number of DH keys for one contact.
	 */
	public static final String DEFAULT_DHKEYS_PER_CONTACT="default_dhkeys_per_contact";
	
	/**
	 * Default number seconds to call DH keys resync after the last resync.
	 */
	public static final String DEFAULT_DHKEYS_PERIODIC_REPEAT="default_dhkeys_periodic_repeat";

    /**
     * Default number seconds to call DH keys resync after the last resync.
     */
    public static final String CONTACT_LIST_SORT_ONLINE="contactlist_sort_online";

    /**
     * Switch on/off displaying usernames in contact list
     */
    public static final String CONTACT_LIST_SHOW_USERNAMES="contactlist_show_usernames";

    /**
     * Alert for a new message during chatting session.
     */
    public static final String ALERT_NEW_MESSAGE_DURING_CHAT = "alert_new_message_during_chat";

    /**
     * Checkbox if PIN screen locking is enabled
     */
    public static final String PIN_LOCK_ENABLE =  "pinlock.enable";
    public static final String PIN_LOCK_TIMER =  "pinlock.timer";
    public static final String PIN_LOCK_CHANGE_PIN =  "pinlock.change_pin";
    public static final String PIN_LOCK_RESET_PIN =  "pinlock.reset_pin";

    /**
     * Pin tick can be from different processes, therefore do synchronization via Preference Content provider
     */
    public static final String PIN_LAST_TICK = "pin.last_tick";
    /**
     * The actual hashed pin with salt
     */
    public static final String PIN_HASH_AND_SALT = "pin.hash_and_salt";

    /**
     * ICE candidates blacklisting for IP protection, disabling P2P.
     */
    public static final String P2P_DISABLE =  "p2p.disable";

    /**
     * Main support sip account, may be empty
     * */
    public static final String SIP_SUPPORT_ACCOUNT = "support_sip_account";

    public static final String TRIAL_MESSAGE_LIMIT_PER_DAY = "trial.message_limit_per_day";
    public static final String TRIAL_EXPIRED_WARNING_SHOWN = "trial.expired_warning_shown";
    public static final String TRIAL_WEEK_TO_EXPIRE_WARNING_SHOWN = "trial.week_to_expire_warning_shown";
    public static final String TRIAL_DAY_TO_EXPIRE_WARNING_SHOWN = "trial.day_to_expire_warning_shown";
    public static final String RECOVERY_EMAIL_MISSING_WARNING_SHOWN= "recovery_email_missing_warning_shown";

    /**
     * Photo upload resize preferences
     */
    /**
     * Ideal resolution for uploaded photos (in pixels). <0 means original size
     */
    public static final String PHOTO_RESIZE_RESOLUTION = "photo_resize_resolution";
    /**
     * How much smaller result is still tolerable (0-1), 0 - exact size, 0.5 - 50 % shorter etc.
     */
    public static final String PHOTO_RESIZE_LOWER_BOUND = "photo_resize_lower_bound";
    /**
     * How much larger result is still tolerable (0-1), 0 - exact size, 0.5 - 50 % longer etc.
     */
    public static final String PHOTO_RESIZE_UPPER_BOUND = "photo_resize_upper_bound";
    /**
     * Preferred JPEG quality (0-100)
     */
    public static final String PHOTO_RESIZE_JPEG_QUALITY = "photo_resize_jpeg_quality";

    /**
     * GCM related values stored in preferences
     */
    public static final String GCM_TOKEN = "gcm_token";
    public static final String GCM_TOKEN_SENT_TO_SERVER = "gcm_token_sent_to_server";
    public static final String GCM_MESSAGES_JSON = "gcm_messages_json";


    /**
     * Encrypted quick login credentials
     */
    public static final String ENCRYPTED_LOGIN_CREDS = "encrypted_login_creds";


    /**********************************************************************************************/
    /****************************  Helper methods  ************************************************/
    /**********************************************************************************************/

    /**
     * Get the preference key for a codec priority
     * 
     * @param codecName Name of the codec as known by pjsip. Example PCMU/8000/1
     * @param type Type of the codec {@link #CODEC_NB} or {@link #CODEC_WB}
     * @return The key to use to set/get the priority of a codec for a given
     *         bandwidth
     */
    public static String getCodecKey(String codecName, String type) {
        String[] codecParts = codecName.split("/");
        if (codecParts.length >= 2) {
            return "codec_" + codecParts[0].toLowerCase() + "_" + codecParts[1] + "_" + type;
        }
        return null;
    }

    /**
     * Get the preference <b>partial</b> key for a given network kind
     * 
     * @param networkType Type of the network {@link ConnectivityManager}
     * @param subType Subtype of the network {@link TelephonyManager}
     * @return The partial key for the network kind
     */
    private static String keyForNetwork(int networkType, int subType) {
        if (networkType == ConnectivityManager.TYPE_WIFI) {
            return "wifi";
        } else if (networkType == ConnectivityManager.TYPE_MOBILE) {
            // 3G (or better)
            if (subType >= TelephonyManager.NETWORK_TYPE_UMTS) {
                return "3g";
            }

            // GPRS (or unknown)
            if (subType == TelephonyManager.NETWORK_TYPE_GPRS
                    || subType == TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                return "gprs";
            }

            // EDGE
            if (subType == TelephonyManager.NETWORK_TYPE_EDGE) {
                return "edge";
            }
        }

        return "other";
    }

    /**
     * Get preference key for the kind of bandwidth to associate to a network
     * 
     * @param networkType Type of the network {@link ConnectivityManager}
     * @param subType Subtype of the network {@link TelephonyManager}
     * @return the preference key for the network kind passed in argument
     */
    public static String getBandTypeKey(int networkType, int subType) {
        return "band_for_" + keyForNetwork(networkType, subType);
    }

    private static Uri getPrefUriForKey(String key) {
        return Uri.withAppendedPath(PREF_ID_URI_BASE, key);
    }

    public static String getStringPref(Context ctxt, String key) {
        return getStringPref(ctxt, key, null);
    }

    public static String getStringPref(Context ctxt, String key, String defaultValue) {
        String value = defaultValue;
        Uri uri = getPrefUriForKey(key);
        Cursor c = ctxt.getContentResolver().query(uri, null, String.class.getName(), null, null);
        if (c != null) {
        	if (c.moveToFirst()){
	            String strValue = c.getString(1);
	            if (strValue != null) {
	                value = strValue;
	            }
        	}
            c.close();
        }
        return value;
    }

    public static Boolean getBooleanPref(Context ctxt, String key) {
        return getBooleanPref(ctxt, key, null);
    }

    public static Boolean getBooleanPref(Context ctxt, String key, Boolean defaultValue) {
        Boolean value = defaultValue;
        Uri uri = getPrefUriForKey(key);
        Cursor c = ctxt.getContentResolver().query(uri, null, Boolean.class.getName(), null, null);
        if (c != null) {
        	if (c.moveToFirst()){
	            int intValue = c.getInt(1);
	            if (intValue >= 0) {
	                value = (intValue == 1);
	            }
        	}
            c.close();
        }
        return value;
    }

    public static Float getFloatPref(Context ctxt, String key) {
        return getFloatPref(ctxt, key, null);
    }

    public static Float getFloatPref(Context ctxt, String key, Float defaultValue) {
        Float value = defaultValue;
        Uri uri = getPrefUriForKey(key);
        Cursor c = ctxt.getContentResolver().query(uri, null, Float.class.getName(), null, null);
        if (c != null) {
            if (c.moveToFirst()){
	            Float fValue = c.getFloat(1);
	            if (fValue != null) {
	                value = fValue;
	            }
            }
            c.close();
        }
        return value;
    }

    public static Integer getIntegerPref(Context ctxt, String key) {
        return getIntegerPref(ctxt, key, null);
    }

    public static Integer getIntegerPref(Context ctxt, String key, Integer defaultValue) {
        Integer value = defaultValue;
        Uri uri = getPrefUriForKey(key);
        Cursor c = ctxt.getContentResolver().query(uri, null, Integer.class.getName(), null, null);
        if (c != null) {
            if (c.moveToFirst()){
	            Integer iValue = c.getInt(1);
	            if (iValue != null) {
	                value = iValue;
	            }
            }
            c.close();
        }
        return value;
    }

    public static void setStringPref(Context ctxt, String key, String value) {
        Uri uri = getPrefUriForKey(key);
        ContentValues values = new ContentValues();
        values.put(PhonexConfig.FIELD_VALUE, value);
        ctxt.getContentResolver().update(uri, values, String.class.getName(), null);
    }

    public static void deleteStringPref(Context ctxt, String key) {
        Uri uri = getPrefUriForKey(key);
        ctxt.getContentResolver().delete(uri, String.class.getName(), null);
    }

    public static void setBooleanPref(Context ctxt, String key, boolean value) {
        Uri uri = getPrefUriForKey(key);
        ContentValues values = new ContentValues();
        values.put(PhonexConfig.FIELD_VALUE, value);
        ctxt.getContentResolver().update(uri, values, Boolean.class.getName(), null);
    }

    public static void setFloatPref(Context ctxt, String key, Float value) {
        Uri uri = getPrefUriForKey(key);
        ContentValues values = new ContentValues();
        values.put(PhonexConfig.FIELD_VALUE, value);
        ctxt.getContentResolver().update(uri, values, Float.class.getName(), null);
    }

    public static void setIntegerPref(Context ctxt, String key, Integer value) {
        if (value != null) {
            setStringPref(ctxt, key, value.toString());
        }
    }

    public static void setLongPref(Context ctxt, String key, Long newValue) {
        Uri uri = getPrefUriForKey(key);
        ContentValues values = new ContentValues();
        values.put(PhonexConfig.FIELD_VALUE, newValue);
        ctxt.getContentResolver().update(uri, values, Long.class.getName(), null);
    }

    public static Long getLongPref(Context ctxt, String key, Long defaultValue) {
        Long value = defaultValue;
        Uri uri = getPrefUriForKey(key);
        Cursor c = ctxt.getContentResolver().query(uri, null, Long.class.getName(), null, null);
        if (c != null) {
            if (c.moveToFirst()){
                Long iValue = c.getLong(1);
                if (iValue != null) {
                    value = iValue;
                }
            }
            c.close();
        }
        return value;
    }

    public static Long getLongPref(Context ctxt, String key) {
        return getLongPref(ctxt, key, null);
    }
}

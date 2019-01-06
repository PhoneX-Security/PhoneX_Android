package net.phonex.db.entity;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import net.phonex.core.Constants;
import net.phonex.pref.PhonexConfig;
import net.phonex.core.SipUri;
import net.phonex.license.LicenseInformation;
import net.phonex.util.Log;
import net.phonex.util.account.PhonexAccountManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

public class SipProfile implements Parcelable {
    private static final String THIS_FILE = "SipProfile";

    /**
     * Constant for user id - now limited to only one user, TODO: better solution.
     */
    public final static long USER_ID = 1;

    /**
     * Constant for an invalid account id.
     */
    public final static long INVALID_ID = -1;

    public final static int TRANSPORT_AUTO = 0;
    public final static int TRANSPORT_UDP = 1;
    public final static int TRANSPORT_TCP = 2;
    public final static int TRANSPORT_TLS = 3;

    // Stack choices
    /**
     * Use pjsip as backend.<br/>
     * For now it's the only one supported
     */
    public static final int PJSIP_STACK = 0;
    /**
     * @deprecated Use google google android 2.3 backend.<br/>
     *             This is not supported for now.
     */
    public static final int GOOGLE_STACK = 1;

    // Password type choices
    /**
     * Plain password mode.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsip__cred__info.htm#a8b1e563c814bdf8012f0bdf966d0ad9d"
     * >Pjsip documentation</a>
     * 
     * @see #datatype
     */
    public static final int CRED_DATA_PLAIN_PASSWD = 0;
    /**
     * Digest mode.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsip__cred__info.htm#a8b1e563c814bdf8012f0bdf966d0ad9d"
     * >Pjsip documentation</a>
     * 
     * @see #datatype
     */
    public static final int CRED_DATA_DIGEST = 1;
    /**
     * @deprecated Not supported.<br/>
     *             <a target="_blank" href=
     *             "http://www.pjsip.org/pjsip/docs/html/structpjsip__cred__info.htm#a8b1e563c814bdf8012f0bdf966d0ad9d"
     *             >Pjsip documentation</a>
     * @see #datatype
     */
    public static final int CRED_CRED_DATA_EXT_AKA = 2;

    // Scheme credentials choices
    /**
     * Digest scheme for credentials.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsip__cred__info.htm#ae31c9ec1c99fb1ffa20be5954ee995a7"
     * >Pjsip documentation</a>
     * 
     * @see #scheme
     */
    public static final String CRED_SCHEME_DIGEST = "Digest";
    /**
     * PGP scheme for credentials.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsip__cred__info.htm#ae31c9ec1c99fb1ffa20be5954ee995a7"
     * >Pjsip documentation</a>
     * 
     * @see #scheme
     */
    public static final String CRED_SCHEME_PGP = "PGP";

    /**
     * Separator for proxy field once stored in database.<br/>
     * It's the pipe char.
     * 
     * @see #FIELD_PROXY
     */
    public static final String PROXIES_SEPARATOR = "|";

    // Content Provider - account
    /**
     * Table name of content provider for accounts storage
     */
    public final static String ACCOUNTS_TABLE_NAME = "accounts";
    /**
     * Content type for account / sip profile
     */
    public final static String ACCOUNT_CONTENT_TYPE = Constants.BASE_DIR_TYPE + ".account";
    /**
     * FileItemInfo type for account / sip profile
     */
    public final static String ACCOUNT_CONTENT_ITEM_TYPE = Constants.BASE_ITEM_TYPE + ".account";
    /**
     * Uri of accounts / sip profiles
     */
    public final static Uri ACCOUNT_URI = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/" + ACCOUNTS_TABLE_NAME);
    /**
     * Base uri for the account / sip profile. <br/>
     * To append with {@link #FIELD_ID}
     * 
     * @see ContentUris#appendId(android.net.Uri.Builder, long)
     */
    public final static Uri ACCOUNT_ID_URI_BASE = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/" + ACCOUNTS_TABLE_NAME + "/");

    // Content Provider - account status
    /**
     * Virtual table name for sip profile adding/registration table.<br/>
     * An application should use it in read only mode.
     */
    public final static String ACCOUNTS_STATUS_TABLE_NAME = "accounts_status";
    /**
     * Content type for sip profile adding/registration state
     */
    public final static String ACCOUNT_STATUS_CONTENT_TYPE = Constants.BASE_DIR_TYPE
            + ".account_status";
    /**
     * Content type for sip profile adding/registration state item
     */
    public final static String ACCOUNT_STATUS_CONTENT_ITEM_TYPE = Constants.BASE_ITEM_TYPE
            + ".account_status";
    /**
     * Uri for the sip profile adding/registration state.
     */
    public final static Uri ACCOUNT_STATUS_URI = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/" + ACCOUNTS_STATUS_TABLE_NAME);
    /**
     * Base uri for the sip profile adding/registration state. <br/>
     * To append with {@link #FIELD_ID}
     * 
     * @see ContentUris#appendId(android.net.Uri.Builder, long)
     */
    public final static Uri ACCOUNT_STATUS_ID_URI_BASE = Uri.parse(ContentResolver.SCHEME_CONTENT
            + "://"
            + Constants.AUTHORITY + "/" + ACCOUNTS_STATUS_TABLE_NAME + "/");

    public static final String FIELD_ID = "id";
    public static final String FIELD_ACTIVE = "active";
    public static final String FIELD_ACCOUNT_MANAGER = "wizard";
    /**
     * The display name of the account. <br/>
     * This is used in the application interface to show the label representing
     * the account.
     *
     * @see String
     */
    public static final String FIELD_DISPLAY_NAME = "display_name";
    /**
     * The priority of the account.<br/>
     * This is used in the interface when presenting list of accounts.<br/>
     * This can also be used to choose the default account. <br/>
     * Higher means highest priority.
     * 
     * @see Integer
     */
    public static final String FIELD_PRIORITY = "priority";
    /**
     * The full SIP URL for the account. <br/>
     * The value can take name address or URL format, and will look something
     * like "sip:account@serviceprovider".<br/>
     * This field is mandatory.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__acc__config.htm#ab290b04e8150ed9627335a67e6127b7c"
     * >Pjsip documentation</a>
     * 
     * @see String
     */
    public static final String FIELD_ACC_ID = "acc_id";
    
    /**
     * Data useful for the accountManager internal use.
     * The format here is specific to the accountManager and no assumption is made.
     * Could be simplestring, json, base64 encoded stuff etc.
     * 
     * @see String
     */
    public static final String FIELD_ACCOUNT_MANAGER_DATA = "wizard_data";
    
    /**
     * This is the URL to be put in the request URI for the registration, and
     * will look something like "sip:serviceprovider".<br/>
     * This field should be specified if registration is desired. If the value
     * is empty, no account registration will be performed.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__acc__config.htm#a08473de6401e966d23f34d3a9a05bdd0"
     * >Pjsip documentation</a>
     * 
     * @see String
     */
    public static final String FIELD_REG_URI = "reg_uri";
    /**
     * Subscribe to message waiting indication events (RFC 3842).<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__acc__config.htm#a0158ae24d72872a31a0b33c33450a7ab"
     * >Pjsip documentation</a>
     * 
     * @see Boolean
     */
    public static final String FIELD_MWI_ENABLED = "mwi_enabled";
    /**
     * If this flag is set, the presence information of this account will be
     * PUBLISH-ed to the server where the account belongs.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__acc__config.htm#a0d4128f44963deffda4ea9c15183a787"
     * >Pjsip documentation</a>
     * 1 for true, 0 for false
     * 
     * @see Integer
     */
    public static final String FIELD_PUBLISH_ENABLED = "publish_enabled";
    /**
     * Optional interval for registration, in seconds. <br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__acc__config.htm#a2c097b9ae855783bfbb00056055dd96c"
     * >Pjsip documentation</a>
     * 
     * @see Integer
     */
    public static final String FIELD_REG_TIMEOUT = "reg_timeout";
    /**
     * Specify the number of seconds to refresh the client registration before
     * the registration expires.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__acc__config.htm#a52a35fdf8c17263b2a27d2b17111c040"
     * >Pjsip documentation</a>
     * 
     * @see Integer
     */
    public static final String FIELD_REG_DELAY_BEFORE_REFRESH = "reg_dbr";
    /**
     * Set the interval for periodic keep-alive transmission for this account. <br/>
     * If this value is zero, keep-alive will be disabled for this account.<br/>
     * The keep-alive transmission will be sent to the registrar's address,
     * after successful registration.<br/>
     * Note that this value is not applied anymore in flavor to
     * {@link PhonexConfig#KEEP_ALIVE_INTERVAL_MOBILE} and
     * {@link PhonexConfig#KEEP_ALIVE_INTERVAL_WIFI} <br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__acc__config.htm#a98722b6464d16b5a76aec81f2d2a0694"
     * >Pjsip documentation</a>
     * 
     * @see Integer
     */
    public static final String FIELD_KA_INTERVAL = "ka_interval";
    /**
     * Optional PIDF tuple ID for outgoing PUBLISH and NOTIFY. <br/>
     * If this value is not specified, a random string will be used. <br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__acc__config.htm#aa603989566022840b4671f0171b6cba1"
     * >Pjsip documentation</a>
     * 
     * @see String
     */
    public static final String FIELD_PIDF_TUPLE_ID = "pidf_tuple_id";
    /**
     * Optional URI to be put as Contact for this account.<br/>
     * It is recommended that this field is left empty, so that the value will
     * be calculated automatically based on the transport address.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__acc__config.htm#a5dfdfba40038e33af95819fbe2b896f9"
     * >Pjsip documentation</a>
     * 
     * @see String
     */
    public static final String FIELD_FORCE_CONTACT = "force_contact";

    /**
     * This option is used to update the transport address and the Contact
     * header of REGISTER request.<br/>
     * When this option is enabled, the library will keep track of the public IP
     * address from the response of REGISTER request. <br/>
     * Once it detects that the address has changed, it will unregister current
     * Contact, update the Contact with transport address learned from Via
     * header, and register a new Contact to the registrar.<br/>
     * This will also update the public name of UDP transport if STUN is
     * configured.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__acc__config.htm#a22961bb72ea75f7ca7008464f081ca06"
     * >Pjsip documentation</a>
     * 
     * @see Boolean
     */
    public static final String FIELD_ALLOW_CONTACT_REWRITE = "allow_contact_rewrite";
    /**
     * Specify how Contact update will be done with the registration, if
     * allow_contact_rewrite is enabled.<br/>
     * <ul>
     * <li>If set to 1, the Contact update will be done by sending
     * unregistration to the currently registered Contact, while simultaneously
     * sending new registration (with different Call-ID) for the updated
     * Contact.</li>
     * <li>If set to 2, the Contact update will be done in a single, current
     * registration session, by removing the current binding (by setting its
     * Contact's expires parameter to zero) and adding a new Contact binding,
     * all done in a single request.</li>
     * </ul>
     * Value 1 is the legacy behavior.<br/>
     * Value 2 is the default behavior.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__acc__config.htm#a73b69a3a8d225147ce386e310e588285"
     * >Pjsip documentation</a>
     * 
     * @see Integer
     */
    public static final String FIELD_CONTACT_REWRITE_METHOD = "contact_rewrite_method";

    /**
     * Additional parameters that will be appended in the Contact header for
     * this account.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__acc__config.htm#abef88254f9ef2a490503df6d3b297e54"
     * >Pjsip documentation</a>
     * 
     * @see String
     */
    public static final String FIELD_CONTACT_PARAMS = "contact_params";
    /**
     * Additional URI parameters that will be appended in the Contact URI for
     * this account.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__acc__config.htm#aced70341308928ae951525093bf47562"
     * >Pjsip documentation</a>
     * 
     * @see String
     */
    public static final String FIELD_CONTACT_URI_PARAMS = "contact_uri_params";
    /**
     * Transport to use for this account.<br/>
     * 
     * @see #TRANSPORT_AUTO
     * @see #TRANSPORT_UDP
     * @see #TRANSPORT_TCP
     * @see #TRANSPORT_TLS
     */
    public static final String FIELD_TRANSPORT = "transport";
    /**
     * Default scheme to automatically add for this account when calling without uri scheme.<br/>
     * 
     * This is free field but should be one of :
     * sip, sips, tel
     * If invalid (or empty) will automatically fallback to sip
     */
    public static final String FIELD_DEFAULT_URI_SCHEME = "default_uri_scheme";
    /**
     * Way the application should use SRTP. <br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__acc__config.htm#a34b00edb1851924a99efd8fedab917ba"
     * >Pjsip documentation</a>
     * 
     * @see Integer
     */
    public static final String FIELD_USE_SRTP = "use_srtp";
    /**
     * Way the application should use SRTP. <br/>
     * 0 means disabled for this account <br/>
     * 1 means enabled for this account
     *  
     * @see Integer
     */
    public static final String FIELD_USE_ZRTP = "use_zrtp";

    /**
     * Optional URI of the proxies to be visited for all outgoing requests that
     * are using this account (REGISTER, INVITE, etc).<br/>
     * If multiple separate it by {@link #PROXIES_SEPARATOR}. <br/>
     * Warning, for now api doesn't allow multiple credentials so if you have
     * one credential per proxy may not work.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__acc__config.htm#a93ad0699020c17ddad5eb98dea69f699"
     * >Pjsip documentation</a>
     * 
     * @see String
     * @see #PROXIES_SEPARATOR
     */
    public static final String FIELD_PROXY = "proxy";
    /**
     * Specify how the registration uses the outbound and account proxy
     * settings. <br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__acc__config.htm#ad932bbb3c2c256f801c775319e645717"
     * >Pjsip documentation</a>
     * 
     * @see Integer
     */
    public static final String FIELD_REG_USE_PROXY = "reg_use_proxy";

    // For now, assume unique credential
    /**
     * Realm to filter on for credentials.<br/>
     * Put star "*" char if you want it to match all requests.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsip__cred__info.htm#a96eee6bdc2b0e7e3b7eea9b4e1c15674"
     * >Pjsip documentation</a>
     * 
     * @see String
     */
    public static final String FIELD_REALM = "realm";
    /**
     * Scheme (e.g. "digest").<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsip__cred__info.htm#ae31c9ec1c99fb1ffa20be5954ee995a7"
     * >Pjsip documentation</a>
     * 
     * @see String
     * @see #CRED_SCHEME_DIGEST
     * @see #CRED_SCHEME_PGP
     */
    public static final String FIELD_SCHEME = "scheme";
    /**
     * Credential username.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsip__cred__info.htm#a3e1f72a171886985c6dfcd57d4bc4f17"
     * >Pjsip documentation</a>
     * 
     * @see String
     */
    public static final String FIELD_USERNAME = "username";
    /**
     * Type of the data for credentials.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsip__cred__info.htm#a8b1e563c814bdf8012f0bdf966d0ad9d"
     * >Pjsip documentation</a>
     * 
     * @see Integer
     * @see #CRED_DATA_PLAIN_PASSWD
     * @see #CRED_DATA_DIGEST
     * @see #CRED_CRED_DATA_EXT_AKA
     */
    public static final String FIELD_DATATYPE = "datatype";
    /**
     * The data, which can be a plaintext password or a hashed digest.<br/>
     * This is available on in read only for third party application for obvious
     * security reason.<br/>
     * If you update the content provider without passing this parameter it will
     * not override it. <br/>
     * If in a third party app you want to store the password to allow user to
     * see it, you have to manage this by your own. <br/>
     * However, it's highly recommanded to not store it by your own, and keep it
     * stored only in app.<br/>
     * It available for write/overwrite. <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsip__cred__info.htm#ab3947a7800c51d28a1b25f4fdaea78bd"
     * >Pjsip documentation</a>
     * 
     * @see String
     */
    public static final String FIELD_PASSWORD = "data";
    
    /**
     * If this flag is set, the authentication client framework will send an empty Authorization header in each initial request. Default is no.
     *  <a target="_blank" href=
     * "http://www.pjsip.org/docs/latest/pjsip/docs/html/structpjsip__auth__clt__pref.htm#ac3487e53d8d6b3ea392315b08e2aac4a"
     * >Pjsip documentation</a>
     * 
     * @see Integer
     */
    public static final String FIELD_AUTH_INITIAL_AUTH = "initial_auth";
    
    /**
     * If this flag is set, the authentication client framework will send an empty Authorization header in each initial request. Default is no.
     *  <a target="_blank" href=
     * "http://www.pjsip.org/docs/latest/pjsip/docs/html/structpjsip__auth__clt__pref.htm#ac3487e53d8d6b3ea392315b08e2aac4a"
     * >Pjsip documentation</a>
     * 
     * @see Integer
     */
    public static final String FIELD_AUTH_ALGO = "auth_algo";

    // Android stuff
    /**
     * The backend sip stack to use for this account.<br/>
     * For now only pjsip backend is supported.
     * 
     * @see Integer
     * @see #PJSIP_STACK
     * @see #GOOGLE_STACK
     */
    public static final String FIELD_SIP_STACK = "sip_stack";
    /**
     * Sip contact to call if user want to consult his voice mail.<br/>
     * 
     * @see String
     */
    public static final String FIELD_VOICE_MAIL_NBR = "vm_nbr";
    /**
     * Associated contact group for buddy list of this account.<br/>
     * Users of this group will be considered as part of the buddy list of this
     * account and will automatically try to subscribe presence if activated.<br/>
     * Warning : not implemented for now.
     * 
     * @see String
     */
    public static final String FIELD_ANDROID_GROUP = "android_group";

    // Sip outbound
    /**
     * Control the use of SIP outbound feature. <br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__acc__config.htm#a306e4641988606f1ef0993e398ff98e7"
     * >Pjsip documentation</a>
     * 
     * @see Integer
     */
    public static final String FIELD_USE_RFC5626 = "use_rfc5626";
    /**
     * Specify SIP outbound (RFC 5626) instance ID to be used by this
     * application.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__acc__config.htm#ae025bf4538d1f9f9506b45015a46a8f6"
     * >Pjsip documentation</a>
     * 
     * @see String
     */
    public static final String FIELD_RFC5626_INSTANCE_ID = "rfc5626_instance_id";
    /**
     * Specify SIP outbound (RFC 5626) registration ID.<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__acc__config.htm#a71376e1f32e35401fc6c2c3bcb2087d8"
     * >Pjsip documentation</a>
     * 
     * @see String
     */
    public static final String FIELD_RFC5626_REG_ID = "rfc5626_reg_id";

    // Video config
    /**
     * Auto show video of the remote party.<br/>
     * TODO : complete when pjsip-2.x stable documentation out
     */
    public static final String FIELD_VID_IN_AUTO_SHOW = "vid_in_auto_show";
    /**
     * Auto transmit video of our party.<br/>
     * TODO : complete when pjsip-2.x stable documentation out
     */
    public static final String FIELD_VID_OUT_AUTO_TRANSMIT = "vid_out_auto_transmit";

    // RTP config
    /**
     * Begin RTP port for the media of this account.<br/>
     * By default it will use {@link PhonexConfig#PJ_RTP_PORT}
     * 
     * @see Integer
     */
    public static final String FIELD_RTP_PORT = "rtp_port";
    /**
     * Public address to announce in SDP as self media address.<br/>
     * Only use if you have static and known public ip on your device regarding
     * the sip server. <br/>
     * May be helpful in VPN configurations.
     */
    public static final String FIELD_RTP_PUBLIC_ADDR = "rtp_public_addr";
    /**
     * Address to bound from client to enforce on interface to be used. <br/>
     * By default the application bind all addresses. (0.0.0.0).<br/>
     * This is only useful if you want to avoid one interface to be bound, but
     * is useless to get audio path correctly working use
     * {@link #FIELD_RTP_PUBLIC_ADDR}
     */
    public static final String FIELD_RTP_BOUND_ADDR = "rtp_bound_addr";
    /**
     * Should the QoS be enabled on this account.<br/>
     * By default it will use {@link PhonexConfig#ENABLE_QOS}.<br/>
     * Default value is -1 to use global setting. 0 means disabled, 1 means
     * enabled.<br/>
     * 
     * @see Integer
     * @see PhonexConfig#ENABLE_QOS
     */
    public static final String FIELD_RTP_ENABLE_QOS = "rtp_enable_qos";
    /**
     * The value of DSCP.<br/>
     * 
     * @see Integer
     * @see PhonexConfig#DSCP_VAL
     */
    public static final String FIELD_RTP_QOS_DSCP = "rtp_qos_dscp";

    /**
     * Should the application try to clean registration of all sip clients if no
     * registration found.<br/>
     * This is useful if the sip server manage limited serveral concurrent
     * registrations.<br/>
     * Since in this case the registrations may leak in case of failing
     * unregisters, this option will unregister all contacts previously
     * registred.
     * 
     * @see Boolean
     */
    public static final String FIELD_TRY_CLEAN_REGISTERS = "try_clean_reg";
    
    
    /**
     * This option is used to overwrite the "sent-by" field of the Via header
     * for outgoing messages with the same interface address as the one in
     * the REGISTER request, as long as the request uses the same transport
     * instance as the previous REGISTER request. <br/>
     *
     * Default: true <br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/structpjsua__acc__config.htm"
     * >Pjsip documentation</a>
     * 
     * @see Boolean
     */
    public static final String FIELD_ALLOW_VIA_REWRITE = "allow_via_rewrite";

    /**
     * Control the use of STUN for the SIP signaling.
     */
    public static final String FIELD_SIP_STUN_USE = "sip_stun_use";
    
    /**
     * Control the use of STUN for the transports.
     */
    public static final String FIELD_MEDIA_STUN_USE = "media_stun_use";
    
    /**
     * Control the use of ICE in the account. 
     * By default, the settings in the pjsua_media_config will be used. 
     */
    public static final String FIELD_ICE_CFG_USE = "ice_cfg_use";

    /**
     * Enable ICE. 
     */
    public static final String FIELD_ICE_CFG_ENABLE = "ice_cfg_enable";
    
    /**
     * Control the use of TURN in the account. 
     * By default, the settings in the pjsua_media_config will be used. 
     */
    public static final String FIELD_TURN_CFG_USE = "turn_cfg_use";
    
    /**
     *  Enable TURN.
     */
    public static final String FIELD_TURN_CFG_ENABLE = "turn_cfg_enable";
    
    /**
     *  TURN server.
     */
    public static final String FIELD_TURN_CFG_SERVER = "turn_cfg_server";
    
    /**
     *  TURN username.
     */
    public static final String FIELD_TURN_CFG_USER = "turn_cfg_user";
    
    /**
     *  TURN password.
     */
    public static final String FIELD_TURN_CFG_PASSWORD = "turn_cfg_pwd";
    
    /**
     * Should media use ipv6?
     */
    public static final String FIELD_IPV6_MEDIA_USE = "ipv6_media_use";
    
    /**
     * Certificate path in file system.
     * Not used in one user setup.
     * 
     * @author ph4r05
     * @date 02-03-2013
     */
    public static final String FIELD_CERT_PATH = "cert_path";
    
    /**
     * Certificate not before date (when became valid/created).
     * @author ph4r05
     * @date 02-03-2013
     */
    public static final String FIELD_CERT_NOT_BEFORE = "cert_not_before";
    
    /**
     * Certificate hash.
     * @author ph4r05
     * @date 02-03-2013
     */
    public static final String FIELD_CERT_HASH = "cert_hash";
    
    /**
     * XMPP server name to use.
     * @author ph4r05
     * @date 25-05-2014
     */
    public static final String FIELD_XMPP_SERVER = "xmpp_server";
    
    /**
     * XMPP service to connect on the server
     * @author ph4r05
     * @date 25-05-2014
     */
    public static final String FIELD_XMPP_SERVICE = "xmpp_service";
    
    /**
     * XMPP user name for login to use.
     */
    public static final String FIELD_XMPP_USER_NAME = "xmpp_user_name";
    
    /**
     * XMPP password to use for login.
     */
    public static final String FIELD_XMPP_PASSWORD = "xmpp_password";

    public static final String FIELD_LICENSE_TYPE = "license_type";
    public static final String FIELD_LICENSE_ISSUED_ON = "license_issued_on";
    public static final String FIELD_LICENSE_EXPIRES_ON = "license_expires_on";
    public static final String FIELD_LICENSE_EXPIRED = "license_expired";
    public static final String FIELD_CURRENT_POLICY_TIMESTAMP = "current_policy_timestamp";

    public static final String FIELD_RECOVERY_EMAIL = "recovery_email";

    public static final String DATE_FORMAT = "YYYY-MM-DD HH:MM:SS.SSS";
    
    public static final String[] LISTABLE_PROJECTION = new String[] {
            SipProfile.FIELD_ID,
            SipProfile.FIELD_ACC_ID,
            SipProfile.FIELD_ACTIVE,
            SipProfile.FIELD_DISPLAY_NAME,
            SipProfile.FIELD_ACCOUNT_MANAGER,
            SipProfile.FIELD_PRIORITY,
            SipProfile.FIELD_REG_URI,

            SipProfile.FIELD_LICENSE_TYPE,
            SipProfile.FIELD_LICENSE_ISSUED_ON,
            SipProfile.FIELD_LICENSE_EXPIRES_ON,
            SipProfile.FIELD_LICENSE_EXPIRED,
            SipProfile.FIELD_CURRENT_POLICY_TIMESTAMP,
            SipProfile.FIELD_RECOVERY_EMAIL
    };
    

    public static final String[] ACC_PROJECTION = new String[] {
        SipProfile.FIELD_ID,
        SipProfile.FIELD_ACC_ID, // Needed for default domain
        SipProfile.FIELD_REG_URI, // Needed for default domain
        SipProfile.FIELD_PROXY, // Needed for default domain
        SipProfile.FIELD_TRANSPORT, // Needed for default scheme
        SipProfile.FIELD_DISPLAY_NAME,
        SipProfile.FIELD_ACCOUNT_MANAGER,
        SipProfile.FIELD_PUBLISH_ENABLED,
        SipProfile.FIELD_RECOVERY_EMAIL

    };

    // SQL Create command for User account table.
    public static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS "
            + SipProfile.ACCOUNTS_TABLE_NAME
            + " ("
            + SipProfile.FIELD_ID+ 				" INTEGER PRIMARY KEY AUTOINCREMENT,"

            // Application relative fields
            + SipProfile.FIELD_ACTIVE				+ " INTEGER,"
            + SipProfile.FIELD_ACCOUNT_MANAGER + " TEXT,"
            + SipProfile.FIELD_DISPLAY_NAME		+ " TEXT,"

            // Here comes pjsua_acc_config fields
            + SipProfile.FIELD_PRIORITY 			+ " INTEGER,"
            + SipProfile.FIELD_ACC_ID 				+ " TEXT NOT NULL,"
            + SipProfile.FIELD_REG_URI				+ " TEXT,"
            + SipProfile.FIELD_MWI_ENABLED 		+ " BOOLEAN,"
            + SipProfile.FIELD_PUBLISH_ENABLED 	+ " INTEGER,"
            + SipProfile.FIELD_REG_TIMEOUT 		+ " INTEGER,"
            + SipProfile.FIELD_KA_INTERVAL 		+ " INTEGER,"
            + SipProfile.FIELD_PIDF_TUPLE_ID 		+ " TEXT,"
            + SipProfile.FIELD_FORCE_CONTACT 		+ " TEXT,"
            + SipProfile.FIELD_ALLOW_CONTACT_REWRITE + " INTEGER,"
            + SipProfile.FIELD_CONTACT_REWRITE_METHOD + " INTEGER,"
            + SipProfile.FIELD_CONTACT_PARAMS 		+ " TEXT,"
            + SipProfile.FIELD_CONTACT_URI_PARAMS	+ " TEXT,"
            + SipProfile.FIELD_TRANSPORT	 		+ " INTEGER,"
            + SipProfile.FIELD_DEFAULT_URI_SCHEME           + " TEXT,"
            + SipProfile.FIELD_USE_SRTP	 			+ " INTEGER,"
            + SipProfile.FIELD_USE_ZRTP	 			+ " INTEGER,"

            // Proxy infos
            + SipProfile.FIELD_PROXY				+ " TEXT,"
            + SipProfile.FIELD_REG_USE_PROXY		+ " INTEGER,"

            // And now cred_info since for now only one cred info can be managed
            // In future release a credential table should be created
            + SipProfile.FIELD_REALM 				+ " TEXT,"
            + SipProfile.FIELD_SCHEME 				+ " TEXT,"
            + SipProfile.FIELD_USERNAME				+ " TEXT,"
            + SipProfile.FIELD_DATATYPE 			+ " INTEGER,"
            + SipProfile.FIELD_PASSWORD + " TEXT,"
            + SipProfile.FIELD_AUTH_INITIAL_AUTH + " INTEGER,"
            + SipProfile.FIELD_AUTH_ALGO      + " TEXT,"


            + SipProfile.FIELD_SIP_STACK 			+ " INTEGER,"
            + SipProfile.FIELD_VOICE_MAIL_NBR		+ " TEXT,"
            + SipProfile.FIELD_REG_DELAY_BEFORE_REFRESH	+ " INTEGER,"

            + SipProfile.FIELD_TRY_CLEAN_REGISTERS	+ " INTEGER,"

            + SipProfile.FIELD_USE_RFC5626          + " INTEGER DEFAULT 1,"
            + SipProfile.FIELD_RFC5626_INSTANCE_ID  + " TEXT,"
            + SipProfile.FIELD_RFC5626_REG_ID       + " TEXT,"

            + SipProfile.FIELD_VID_IN_AUTO_SHOW          + " INTEGER DEFAULT -1,"
            + SipProfile.FIELD_VID_OUT_AUTO_TRANSMIT     + " INTEGER DEFAULT -1,"

            + SipProfile.FIELD_RTP_PORT                  + " INTEGER DEFAULT -1,"
            + SipProfile.FIELD_RTP_ENABLE_QOS            + " INTEGER DEFAULT -1,"
            + SipProfile.FIELD_RTP_QOS_DSCP              + " INTEGER DEFAULT -1,"
            + SipProfile.FIELD_RTP_BOUND_ADDR            + " TEXT,"
            + SipProfile.FIELD_RTP_PUBLIC_ADDR           + " TEXT,"
            + SipProfile.FIELD_ANDROID_GROUP             + " TEXT,"
            + SipProfile.FIELD_ALLOW_VIA_REWRITE         + " INTEGER DEFAULT 0,"
            + SipProfile.FIELD_SIP_STUN_USE              + " INTEGER DEFAULT -1,"
            + SipProfile.FIELD_MEDIA_STUN_USE            + " INTEGER DEFAULT -1,"
            + SipProfile.FIELD_ICE_CFG_USE               + " INTEGER DEFAULT -1,"
            + SipProfile.FIELD_ICE_CFG_ENABLE            + " INTEGER DEFAULT 0,"
            + SipProfile.FIELD_TURN_CFG_USE              + " INTEGER DEFAULT -1,"
            + SipProfile.FIELD_TURN_CFG_ENABLE           + " INTEGER DEFAULT 0,"
            + SipProfile.FIELD_TURN_CFG_SERVER           + " TEXT,"
            + SipProfile.FIELD_TURN_CFG_USER             + " TEXT,"
            + SipProfile.FIELD_TURN_CFG_PASSWORD         + " TEXT,"
            + SipProfile.FIELD_IPV6_MEDIA_USE            + " INTEGER DEFAULT 0,"
            + SipProfile.FIELD_ACCOUNT_MANAGER_DATA + " TEXT,"
            + SipProfile.FIELD_CERT_PATH                 + " TEXT,"
            + SipProfile.FIELD_CERT_NOT_BEFORE           + " INTEGER DEFAULT 0,"
            + SipProfile.FIELD_CERT_HASH                 + " TEXT,"
            + SipProfile.FIELD_XMPP_SERVER               + " TEXT,"
            + SipProfile.FIELD_XMPP_SERVICE              + " TEXT,"
            + SipProfile.FIELD_XMPP_USER_NAME            + " TEXT,"
            + SipProfile.FIELD_XMPP_PASSWORD             + " TEXT,"

            // license information
            + SipProfile.FIELD_LICENSE_TYPE              + " TEXT,"
            + SipProfile.FIELD_LICENSE_ISSUED_ON         + " INTEGER DEFAULT 0,"
            + SipProfile.FIELD_LICENSE_EXPIRES_ON        + " INTEGER DEFAULT 0,"
            + SipProfile.FIELD_LICENSE_EXPIRED           + " INTEGER DEFAULT 0,"
            + SipProfile.FIELD_CURRENT_POLICY_TIMESTAMP  + " INTEGER DEFAULT 0,"
            + SipProfile.FIELD_RECOVERY_EMAIL            + " TEXT"
            + ");";

    // Full User account table projection (all columns in table).
    public final static String[] FULL_PROJECTION = {
            SipProfile.FIELD_ID,
            // Application relative fields
            SipProfile.FIELD_ACTIVE, SipProfile.FIELD_ACCOUNT_MANAGER, SipProfile.FIELD_DISPLAY_NAME,
            SipProfile.FIELD_ACCOUNT_MANAGER_DATA,

            // pjsua_acc_config fields
            SipProfile.FIELD_PRIORITY, SipProfile.FIELD_ACC_ID, SipProfile.FIELD_REG_URI,
            SipProfile.FIELD_MWI_ENABLED, SipProfile.FIELD_PUBLISH_ENABLED, SipProfile.FIELD_REG_TIMEOUT, SipProfile.FIELD_KA_INTERVAL,
            SipProfile.FIELD_PIDF_TUPLE_ID,
            SipProfile.FIELD_FORCE_CONTACT, SipProfile.FIELD_ALLOW_CONTACT_REWRITE, SipProfile.FIELD_CONTACT_REWRITE_METHOD,
            SipProfile.FIELD_ALLOW_VIA_REWRITE,
            SipProfile.FIELD_CONTACT_PARAMS, SipProfile.FIELD_CONTACT_URI_PARAMS,
            SipProfile.FIELD_TRANSPORT, SipProfile.FIELD_DEFAULT_URI_SCHEME, SipProfile.FIELD_USE_SRTP, SipProfile.FIELD_USE_ZRTP,
            SipProfile.FIELD_REG_DELAY_BEFORE_REFRESH,

            // RTP config
            SipProfile.FIELD_RTP_PORT, SipProfile.FIELD_RTP_PUBLIC_ADDR, SipProfile.FIELD_RTP_BOUND_ADDR,
            SipProfile.FIELD_RTP_ENABLE_QOS, SipProfile.FIELD_RTP_QOS_DSCP,

            // Proxy infos
            SipProfile.FIELD_PROXY, SipProfile.FIELD_REG_USE_PROXY,

            // Credentials.
            SipProfile.FIELD_REALM, SipProfile.FIELD_SCHEME, SipProfile.FIELD_USERNAME, SipProfile.FIELD_DATATYPE,
            SipProfile.FIELD_PASSWORD,

            SipProfile.FIELD_AUTH_INITIAL_AUTH, SipProfile.FIELD_AUTH_ALGO,

            // Stack options.
            SipProfile.FIELD_SIP_STACK, SipProfile.FIELD_VOICE_MAIL_NBR,
            SipProfile.FIELD_TRY_CLEAN_REGISTERS, SipProfile.FIELD_ANDROID_GROUP,

            // RFC 5626
            SipProfile.FIELD_USE_RFC5626, SipProfile.FIELD_RFC5626_INSTANCE_ID, SipProfile.FIELD_RFC5626_REG_ID,

            // Video
            SipProfile.FIELD_VID_IN_AUTO_SHOW, SipProfile.FIELD_VID_OUT_AUTO_TRANSMIT,

            // STUN, ICE, TURN
            SipProfile.FIELD_SIP_STUN_USE, SipProfile.FIELD_MEDIA_STUN_USE,
            SipProfile.FIELD_ICE_CFG_USE, SipProfile.FIELD_ICE_CFG_ENABLE,
            SipProfile.FIELD_TURN_CFG_USE, SipProfile.FIELD_TURN_CFG_ENABLE, SipProfile.FIELD_TURN_CFG_SERVER, SipProfile.FIELD_TURN_CFG_USER, SipProfile.FIELD_TURN_CFG_PASSWORD,

            SipProfile.FIELD_IPV6_MEDIA_USE,

            // Certificate for the user
            SipProfile.FIELD_CERT_PATH, SipProfile.FIELD_CERT_NOT_BEFORE, SipProfile.FIELD_CERT_HASH,

            // XMPP related
            SipProfile.FIELD_XMPP_SERVER, SipProfile.FIELD_XMPP_SERVICE,
            SipProfile.FIELD_XMPP_USER_NAME, SipProfile.FIELD_XMPP_PASSWORD,

            // License
            FIELD_LICENSE_TYPE, FIELD_LICENSE_ISSUED_ON,
            FIELD_LICENSE_EXPIRES_ON, FIELD_LICENSE_EXPIRED,
            FIELD_CURRENT_POLICY_TIMESTAMP,

            FIELD_RECOVERY_EMAIL
    };

    protected Locale locale = Locale.getDefault();

    protected int primaryKey = -1;
    protected long id = INVALID_ID;
    protected String display_name = "";
    protected String accountManager = PhonexAccountManager.getId();
    protected Integer transport = 0;
    protected String default_uri_scheme  = "sip";
    protected boolean active = true;
    protected int priority = 100;
    protected String acc_id = null;
    protected String reg_uri = null;
    protected int publish_enabled = 0;
    protected int reg_timeout = 900;
    protected int ka_interval = 0;
    protected String pidf_tuple_id = null;
    protected String force_contact = null;
    protected boolean allow_contact_rewrite = true;
    protected int contact_rewrite_method = 2;
    protected boolean allow_via_rewrite = false;
    protected String[] proxies = null;
    protected String realm = null;
    protected String username = null;
    protected String scheme = null;
    protected int datatype = 0;
    protected String data = null;
    protected boolean initial_auth = false;
    protected String  auth_algo = "";
    protected int use_srtp = -1;
    protected int use_zrtp = -1;
    protected int reg_use_proxy = 3;
    protected int sip_stack = PJSIP_STACK;
    protected String vm_nbr = null;
    protected int reg_delay_before_refresh = -1;
    protected int try_clean_registers = 1;
    protected Bitmap icon = null;
    protected boolean use_rfc5626 = true;
    protected String rfc5626_instance_id = "";
    protected String rfc5626_reg_id = "";
    protected int vid_in_auto_show = -1;
    protected int vid_out_auto_transmit = -1;

    protected int rtp_port = -1;
    protected String rtp_public_addr = "";
    protected String rtp_bound_addr = "";
    protected int rtp_enable_qos = -1;
    protected int rtp_qos_dscp = -1;

    protected String android_group = "";
    protected boolean mwi_enabled = true;
    protected int sip_stun_use = -1;
    protected int media_stun_use = -1;
    protected int ice_cfg_use = -1;
    protected int ice_cfg_enable = 0;
    protected int turn_cfg_use = -1;
    protected int turn_cfg_enable = 0;
    protected String turn_cfg_server = "";
    protected String turn_cfg_user = "";
    protected String turn_cfg_password = "";
    protected int ipv6_media_use = 0;

    protected String wizard_data = "";
    protected String cert_path = "";
    protected Date cert_not_before = new Date(0l);
    protected String cert_hash = "";

    protected String xmpp_server;
    protected String xmpp_service;
    protected String xmpp_user;
    protected String xmpp_password;

    protected String licenseType;
    protected Date licenseIssuedOn;
    protected Date licenseExpiresOn;
    protected boolean licenseExpired;
    protected Date currentPolicyDate;
    protected String recoveryEmail;

    public SipProfile() {
        setDisplay_name("");
        setAccountManager("EXPERT");
        setActive(true);
    }

    /**
     * Construct a sip profile wrapper from a cursor retrieved with a
     * {@link ContentProvider} query on {@link #ACCOUNTS_TABLE_NAME}.
     * 
     * @param c the cursor to unpack
     */
    public SipProfile(Cursor c) {
        super();
        createFromDb(c);
    }

    /**
     * Construct from parcelable <br/>
     * Only used by {@link #CREATOR}
     * 
     * @param in parcelable to build from
     */
    private SipProfile(Parcel in) {
        primaryKey = in.readInt();
        id = in.readInt();
        display_name = in.readString();
        accountManager = in.readString();
        transport = in.readInt();
        active = (in.readInt() != 0);
        priority = in.readInt();
        acc_id = getReadParcelableString(in.readString());
        reg_uri = getReadParcelableString(in.readString());
        publish_enabled = in.readInt();
        reg_timeout = in.readInt();
        ka_interval = in.readInt();
        pidf_tuple_id = getReadParcelableString(in.readString());
        force_contact = getReadParcelableString(in.readString());
        proxies = TextUtils.split(getReadParcelableString(in.readString()),
                Pattern.quote(PROXIES_SEPARATOR));
        realm = getReadParcelableString(in.readString());
        username = getReadParcelableString(in.readString());
        datatype = in.readInt();
        data = getReadParcelableString(in.readString());
        use_srtp = in.readInt();
        allow_contact_rewrite = (in.readInt() != 0);
        contact_rewrite_method = in.readInt();
        sip_stack = in.readInt();
        reg_use_proxy = in.readInt();
        use_zrtp = in.readInt();
        vm_nbr = getReadParcelableString(in.readString());
        reg_delay_before_refresh = in.readInt();
        icon = (Bitmap) in.readParcelable(Bitmap.class.getClassLoader());
        try_clean_registers = in.readInt();
        use_rfc5626 = (in.readInt() != 0);
        rfc5626_instance_id = getReadParcelableString(in.readString());
        rfc5626_reg_id = getReadParcelableString(in.readString());
        vid_in_auto_show = in.readInt();
        vid_out_auto_transmit = in.readInt();
        rtp_port = in.readInt();
        rtp_public_addr = getReadParcelableString(in.readString());
        rtp_bound_addr = getReadParcelableString(in.readString());
        rtp_enable_qos = in.readInt();
        rtp_qos_dscp = in.readInt();
        android_group = getReadParcelableString(in.readString());
        mwi_enabled = (in.readInt() != 0);
        allow_via_rewrite = (in.readInt() != 0);
        sip_stun_use = in.readInt();
        media_stun_use = in.readInt();
        ice_cfg_use = in.readInt();
        ice_cfg_enable = in.readInt();
        turn_cfg_use = in.readInt();
        turn_cfg_enable = in.readInt();
        turn_cfg_server = getReadParcelableString(in.readString());
        turn_cfg_user = getReadParcelableString(in.readString());
        turn_cfg_password = getReadParcelableString(in.readString());
        ipv6_media_use = in.readInt();
        initial_auth = (in.readInt() != 0);
        auth_algo = getReadParcelableString(in.readString());
        wizard_data = getReadParcelableString(in.readString());
        default_uri_scheme = getReadParcelableString(in.readString());
        cert_path = getReadParcelableString(in.readString());
        cert_not_before = new Date(in.readLong());
        cert_hash = getReadParcelableString(in.readString());
        xmpp_server = getReadParcelableString(in.readString());
        xmpp_service = getReadParcelableString(in.readString());
        xmpp_user = getReadParcelableString(in.readString());
        xmpp_password = getReadParcelableString(in.readString());
        licenseType = getReadParcelableString(in.readString());
        licenseIssuedOn = new Date(in.readLong());
        licenseExpiresOn = new Date(in.readLong());
        licenseExpired = (in.readInt() != 0);
        Long longValue = (Long) in.readValue(Long.class.getClassLoader());
        if (longValue != null){
            currentPolicyDate = new Date(longValue);
        }
        String stringValue = (String) in.readValue(String.class.getClassLoader());
        if (stringValue != null){
            recoveryEmail = stringValue;
        }
    }

    /**
     * Parcelable creator. So that it can be passed as an argument of the aidl
     * interface
     */
    public static final Parcelable.Creator<SipProfile> CREATOR = new Parcelable.Creator<SipProfile>() {
        public SipProfile createFromParcel(Parcel in) {
            return new SipProfile(in);
        }

        public SipProfile[] newArray(int size) {
            return new SipProfile[size];
        }
    };

    /**
     * @see Parcelable#describeContents()
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * @see Parcelable#writeToParcel(Parcel, int)
     */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(primaryKey);
        dest.writeInt((int) id);
        dest.writeString(display_name);
        dest.writeString(accountManager);
        dest.writeInt(transport);
        dest.writeInt(active ? 1 : 0);
        dest.writeInt(priority);
        dest.writeString(getWriteParcelableString(acc_id));
        dest.writeString(getWriteParcelableString(reg_uri));
        dest.writeInt(publish_enabled);
        dest.writeInt(reg_timeout);
        dest.writeInt(ka_interval);
        dest.writeString(getWriteParcelableString(pidf_tuple_id));
        dest.writeString(getWriteParcelableString(force_contact));
        if (proxies != null) {
            dest.writeString(getWriteParcelableString(TextUtils.join(PROXIES_SEPARATOR, proxies)));
        } else {
            dest.writeString("");
        }
        dest.writeString(getWriteParcelableString(realm));
        dest.writeString(getWriteParcelableString(username));
        dest.writeInt(datatype);
        dest.writeString(getWriteParcelableString(data));
        dest.writeInt(use_srtp);
        dest.writeInt(allow_contact_rewrite ? 1 : 0);
        dest.writeInt(contact_rewrite_method);
        dest.writeInt(sip_stack);
        dest.writeInt(reg_use_proxy);
        dest.writeInt(use_zrtp);
        dest.writeString(getWriteParcelableString(vm_nbr));
        dest.writeInt(reg_delay_before_refresh);
        dest.writeParcelable((Parcelable) icon, flags);
        dest.writeInt(try_clean_registers);
        dest.writeInt(use_rfc5626 ? 1 : 0);
        dest.writeString(getWriteParcelableString(rfc5626_instance_id));
        dest.writeString(getWriteParcelableString(rfc5626_reg_id));
        dest.writeInt(vid_in_auto_show);
        dest.writeInt(vid_out_auto_transmit);
        dest.writeInt(rtp_port);
        dest.writeString(getWriteParcelableString(rtp_public_addr));
        dest.writeString(getWriteParcelableString(rtp_bound_addr));
        dest.writeInt(rtp_enable_qos);
        dest.writeInt(rtp_qos_dscp);
        dest.writeString(getWriteParcelableString(android_group));
        dest.writeInt(mwi_enabled ? 1 : 0);
        dest.writeInt(allow_via_rewrite ? 1 : 0);
        dest.writeInt(sip_stun_use);
        dest.writeInt(media_stun_use);
        dest.writeInt(ice_cfg_use);
        dest.writeInt(ice_cfg_enable);
        dest.writeInt(turn_cfg_use);
        dest.writeInt(turn_cfg_enable);
        dest.writeString(getWriteParcelableString(turn_cfg_server));
        dest.writeString(getWriteParcelableString(turn_cfg_user));
        dest.writeString(getWriteParcelableString(turn_cfg_password));
        dest.writeInt(ipv6_media_use);
        dest.writeInt(initial_auth ? 1 : 0);
        dest.writeString(getWriteParcelableString(auth_algo));
        dest.writeString(getWriteParcelableString(wizard_data));
        dest.writeString(getWriteParcelableString(default_uri_scheme));
        dest.writeString(getWriteParcelableString(cert_path));
        dest.writeLong(cert_not_before == null ? 0 : cert_not_before.getTime());
        dest.writeString(getWriteParcelableString(cert_hash));
        dest.writeString(getWriteParcelableString(xmpp_server));
        dest.writeString(getWriteParcelableString(xmpp_service));
        dest.writeString(getWriteParcelableString(xmpp_user));
        dest.writeString(getWriteParcelableString(xmpp_password));

        dest.writeString(getWriteParcelableString(licenseType));
        dest.writeLong(licenseIssuedOn != null ? licenseIssuedOn.getTime() : 0);
        dest.writeLong(licenseExpiresOn != null ? licenseExpiresOn.getTime() : 0);
        dest.writeInt(licenseExpired ? 1 : 0);
        // writeValue is a correct way of serializing attribute with possible null value
        dest.writeValue(currentPolicyDate.getTime());
        dest.writeValue(recoveryEmail);
    }

    // Yes yes that's not clean but well as for now not problem with that.
    // and we send null.
    private String getWriteParcelableString(String str) {
        return (str == null) ? "null" : str;
    }

    private String getReadParcelableString(String str) {
        return str.equalsIgnoreCase("null") ? null : str;
    }

    /**
     * Create account wrapper with cursor datas.
     * 
     * @param c cursor on the database
     */
    private final void createFromDb(Cursor c) {
        ContentValues args = new ContentValues();
        DatabaseUtils.cursorRowToContentValues(c, args);
        createFromContentValue(args);
    }

    /**
     * Create account wrapper with content values pairs.
     * 
     * @param args the content value to unpack.
     */
    private final void createFromContentValue(ContentValues args) {
        Integer tmp_i;
        Long tmp_l;
        String tmp_s;
        
        // Application specific settings
        tmp_i = args.getAsInteger(FIELD_ID);
        if (tmp_i != null) {
            id = tmp_i;
        }
        tmp_s = args.getAsString(FIELD_DISPLAY_NAME);
        if (tmp_s != null) {
            display_name = tmp_s;
        }
        tmp_s = args.getAsString(FIELD_ACCOUNT_MANAGER);
        if (tmp_s != null) {
            accountManager = tmp_s;
        }
        tmp_i = args.getAsInteger(FIELD_TRANSPORT);
        if (tmp_i != null) {
            transport = tmp_i;
        }
        tmp_s = args.getAsString(FIELD_DEFAULT_URI_SCHEME);
        if (tmp_s != null) {
            default_uri_scheme = tmp_s;
        }

        tmp_i = args.getAsInteger(FIELD_ACTIVE);
        if (tmp_i != null) {
            active = (tmp_i != 0);
        } else {
            active = true;
        }
        tmp_s = args.getAsString(FIELD_ANDROID_GROUP);
        if (tmp_s != null) {
            android_group = tmp_s;
        }
        tmp_s = args.getAsString(FIELD_ACCOUNT_MANAGER_DATA);
        if (tmp_s != null) {
            wizard_data = tmp_s;
        }

        // General account settings
        tmp_i = args.getAsInteger(FIELD_PRIORITY);
        if (tmp_i != null) {
            priority = tmp_i;
        }
        tmp_s = args.getAsString(FIELD_ACC_ID);
        if (tmp_s != null) {
            acc_id = tmp_s;
        }
        tmp_s = args.getAsString(FIELD_REG_URI);
        if (tmp_s != null) {
            reg_uri = tmp_s;
        }
        tmp_i = args.getAsInteger(FIELD_PUBLISH_ENABLED);
        if (tmp_i != null) {
            publish_enabled = tmp_i;
        }
        tmp_i = args.getAsInteger(FIELD_REG_TIMEOUT);
        if (tmp_i != null && tmp_i >= 0) {
            reg_timeout = tmp_i;
        }
        tmp_i = args.getAsInteger(FIELD_REG_DELAY_BEFORE_REFRESH);
        if (tmp_i != null && tmp_i >= 0) {
            reg_delay_before_refresh = tmp_i;
        }

        tmp_i = args.getAsInteger(FIELD_KA_INTERVAL);
        if (tmp_i != null && tmp_i >= 0) {
            ka_interval = tmp_i;
        }
        tmp_s = args.getAsString(FIELD_PIDF_TUPLE_ID);
        if (tmp_s != null) {
            pidf_tuple_id = tmp_s;
        }
        tmp_s = args.getAsString(FIELD_FORCE_CONTACT);
        if (tmp_s != null) {
            force_contact = tmp_s;
        }
        tmp_i = args.getAsInteger(FIELD_ALLOW_CONTACT_REWRITE);
        if (tmp_i != null) {
            allow_contact_rewrite = (tmp_i == 1);
        }
        tmp_i = args.getAsInteger(FIELD_CONTACT_REWRITE_METHOD);
        if (tmp_i != null) {
            contact_rewrite_method = tmp_i;
        }
        tmp_i = args.getAsInteger(FIELD_ALLOW_VIA_REWRITE);
        if (tmp_i != null) {
            allow_via_rewrite = (tmp_i == 1);
        }

        tmp_i = args.getAsInteger(FIELD_USE_SRTP);
        if (tmp_i != null && tmp_i >= 0) {
            use_srtp = tmp_i;
        }
        tmp_i = args.getAsInteger(FIELD_USE_ZRTP);
        if (tmp_i != null && tmp_i >= 0) {
            use_zrtp = tmp_i;
        }

        // Proxy
        tmp_s = args.getAsString(FIELD_PROXY);
        if (tmp_s != null) {
            proxies = TextUtils.split(tmp_s, Pattern.quote(PROXIES_SEPARATOR));
        }
        tmp_i = args.getAsInteger(FIELD_REG_USE_PROXY);
        if (tmp_i != null && tmp_i >= 0) {
            reg_use_proxy = tmp_i;
        }

        // Auth
        tmp_s = args.getAsString(FIELD_REALM);
        if (tmp_s != null) {
            realm = tmp_s;
        }
        tmp_s = args.getAsString(FIELD_SCHEME);
        if (tmp_s != null) {
            scheme = tmp_s;
        }
        tmp_s = args.getAsString(FIELD_USERNAME);
        if (tmp_s != null) {
            username = tmp_s;
        }
        tmp_i = args.getAsInteger(FIELD_DATATYPE);
        if (tmp_i != null) {
            datatype = tmp_i;
        }
        tmp_s = args.getAsString(FIELD_PASSWORD);
        if (tmp_s != null) {
            data = tmp_s;
        }
        tmp_i = args.getAsInteger(FIELD_AUTH_INITIAL_AUTH);
        if (tmp_i != null) {
            initial_auth = (tmp_i == 1);
        }
        tmp_s = args.getAsString(FIELD_AUTH_ALGO);
        if (tmp_s != null) {
            auth_algo = tmp_s;
        }
        

        tmp_i = args.getAsInteger(FIELD_SIP_STACK);
        if (tmp_i != null && tmp_i >= 0) {
            sip_stack = tmp_i;
        }
        tmp_i = args.getAsInteger(FIELD_MWI_ENABLED);
        if(tmp_i != null && tmp_i >= 0) {
            mwi_enabled = (tmp_i == 1);
        }
        tmp_s = args.getAsString(FIELD_VOICE_MAIL_NBR);
        if (tmp_s != null) {
            vm_nbr = tmp_s;
        }
        tmp_i = args.getAsInteger(FIELD_TRY_CLEAN_REGISTERS);
        if (tmp_i != null && tmp_i >= 0) {
            try_clean_registers = tmp_i;
        }

        // RFC 5626
        tmp_i = args.getAsInteger(FIELD_USE_RFC5626);
        if (tmp_i != null && tmp_i >= 0) {
            use_rfc5626 = (tmp_i != 0);
        }
        tmp_s = args.getAsString(FIELD_RFC5626_INSTANCE_ID);
        if (tmp_s != null) {
            rfc5626_instance_id = tmp_s;
        }
        tmp_s = args.getAsString(FIELD_RFC5626_REG_ID);
        if (tmp_s != null) {
            rfc5626_reg_id = tmp_s;
        }

        // Video
        tmp_i = args.getAsInteger(FIELD_VID_IN_AUTO_SHOW);
        if (tmp_i != null && tmp_i >= 0) {
            vid_in_auto_show = tmp_i;
        }
        tmp_i = args.getAsInteger(FIELD_VID_OUT_AUTO_TRANSMIT);
        if (tmp_i != null && tmp_i >= 0) {
            vid_out_auto_transmit = tmp_i;
        }

        // RTP cfg
        tmp_i = args.getAsInteger(FIELD_RTP_PORT);
        if (tmp_i != null && tmp_i >= 0) {
            rtp_port = tmp_i;
        }
        tmp_s = args.getAsString(FIELD_RTP_PUBLIC_ADDR);
        if (tmp_s != null) {
            rtp_public_addr = tmp_s;
        }
        tmp_s = args.getAsString(FIELD_RTP_BOUND_ADDR);
        if (tmp_s != null) {
            rtp_bound_addr = tmp_s;
        }
        tmp_i = args.getAsInteger(FIELD_RTP_ENABLE_QOS);
        if (tmp_i != null && tmp_i >= 0) {
            rtp_enable_qos = tmp_i;
        }
        tmp_i = args.getAsInteger(FIELD_RTP_QOS_DSCP);
        if (tmp_i != null && tmp_i >= 0) {
            rtp_qos_dscp = tmp_i;
        }
        

        tmp_i = args.getAsInteger(FIELD_SIP_STUN_USE);
        if (tmp_i != null && tmp_i >= 0) {
            sip_stun_use = tmp_i;
        }
        tmp_i = args.getAsInteger(FIELD_MEDIA_STUN_USE);
        if (tmp_i != null && tmp_i >= 0) {
            media_stun_use = tmp_i;
        }
        tmp_i = args.getAsInteger(FIELD_ICE_CFG_USE);
        if (tmp_i != null && tmp_i >= 0) {
            ice_cfg_use = tmp_i;
        }
        tmp_i = args.getAsInteger(FIELD_ICE_CFG_ENABLE);
        if (tmp_i != null && tmp_i >= 0) {
            ice_cfg_enable = tmp_i;
        }
        tmp_i = args.getAsInteger(FIELD_TURN_CFG_USE);
        if (tmp_i != null && tmp_i >= 0) {
            turn_cfg_use = tmp_i;
        }
        tmp_i = args.getAsInteger(FIELD_TURN_CFG_ENABLE);
        if (tmp_i != null && tmp_i >= 0) {
            turn_cfg_enable = tmp_i;
        }
        tmp_s = args.getAsString(FIELD_TURN_CFG_SERVER);
        if (tmp_s != null) {
            turn_cfg_server = tmp_s;
        }
        tmp_s = args.getAsString(FIELD_TURN_CFG_USER);
        if (tmp_s != null) {
            turn_cfg_user = tmp_s;
        }
        tmp_s = args.getAsString(FIELD_TURN_CFG_PASSWORD);
        if (tmp_s != null) {
            turn_cfg_password = tmp_s;
        }
        tmp_i = args.getAsInteger(FIELD_IPV6_MEDIA_USE);
        if (tmp_i != null && tmp_i >= 0) {
            ipv6_media_use = tmp_i;
        }
        
        tmp_s = args.getAsString(FIELD_CERT_PATH);
        if (tmp_s != null) {
            cert_path = tmp_s;
        }
        tmp_l = args.getAsLong(FIELD_CERT_NOT_BEFORE);
        if (tmp_l != null) {
        	cert_not_before = new Date(tmp_l);
        }
        tmp_s = args.getAsString(FIELD_CERT_HASH);
        if (tmp_s != null) {
            cert_hash = tmp_s;
        }
        tmp_s = args.getAsString(FIELD_XMPP_SERVER);
        if (tmp_s != null) {
            xmpp_server = tmp_s;
        }
        tmp_s = args.getAsString(FIELD_XMPP_SERVICE);
        if (tmp_s != null) {
            xmpp_service = tmp_s;
        }
        tmp_s = args.getAsString(FIELD_XMPP_USER_NAME);
        if (tmp_s != null) {
            xmpp_user = tmp_s;
        }
        tmp_s = args.getAsString(FIELD_XMPP_PASSWORD);
        if (tmp_s != null) {
            xmpp_password = tmp_s;
        }

        // license information
        tmp_s = args.getAsString(FIELD_LICENSE_TYPE);
        if (tmp_s != null) {
            licenseType = tmp_s;
        }

        tmp_s = args.getAsString(FIELD_LICENSE_ISSUED_ON);
        if(tmp_s != null){
            licenseIssuedOn = new Date(Long.valueOf(tmp_s));
        }
//
        tmp_s = args.getAsString(FIELD_LICENSE_EXPIRES_ON);
        if(tmp_s != null){
            licenseExpiresOn = new Date(Long.valueOf(tmp_s));
        }

        tmp_i = args.getAsInteger(FIELD_LICENSE_EXPIRED);
        if (tmp_i != null){
            licenseExpired = tmp_i != 0;
        }

        tmp_s = args.getAsString(FIELD_CURRENT_POLICY_TIMESTAMP);
        if(tmp_s != null){
            currentPolicyDate = new Date(Long.valueOf(tmp_s));
        }

        tmp_s = args.getAsString(FIELD_RECOVERY_EMAIL);
        if (tmp_s != null){
            recoveryEmail = tmp_s;
        }
    }

    /**
     * Transform pjsua_acc_config into ContentValues that can be insert into
     * database. <br/>
     * Take care that if your SipProfile is incomplete this content value may
     * also be uncomplete and lead to override unwanted values of the existing
     * database. <br/>
     * So if updating, take care on what you actually want to update instead of
     * using this utility method.
     * 
     * @return Complete content values from the current wrapper around sip
     *         profile.
     */
    public ContentValues getDbContentValues() {
        ContentValues args = new ContentValues();

        if (id != INVALID_ID) {
            args.put(FIELD_ID, id);
        }
        // TODO : ensure of non nullity of some params

        args.put(FIELD_ACTIVE, active ? 1 : 0);
        args.put(FIELD_ACCOUNT_MANAGER, accountManager);
        args.put(FIELD_DISPLAY_NAME, display_name);
        args.put(FIELD_TRANSPORT, transport);
        args.put(FIELD_DEFAULT_URI_SCHEME, default_uri_scheme);
        args.put(FIELD_ACCOUNT_MANAGER_DATA, wizard_data);

        args.put(FIELD_PRIORITY, priority);
        args.put(FIELD_ACC_ID, acc_id);
        args.put(FIELD_REG_URI, reg_uri);

        args.put(FIELD_PUBLISH_ENABLED, publish_enabled);
        args.put(FIELD_REG_TIMEOUT, reg_timeout);
        args.put(FIELD_KA_INTERVAL, ka_interval);
        args.put(FIELD_PIDF_TUPLE_ID, pidf_tuple_id);
        args.put(FIELD_FORCE_CONTACT, force_contact);
        args.put(FIELD_ALLOW_CONTACT_REWRITE, allow_contact_rewrite ? 1 : 0);
        args.put(FIELD_ALLOW_VIA_REWRITE, allow_via_rewrite ? 1 : 0);
        args.put(FIELD_CONTACT_REWRITE_METHOD, contact_rewrite_method);
        args.put(FIELD_USE_SRTP, use_srtp);
        args.put(FIELD_USE_ZRTP, use_zrtp);

        // CONTACT_PARAM and CONTACT_PARAM_URI not yet in JNI

        if (proxies != null) {
            args.put(FIELD_PROXY, TextUtils.join(PROXIES_SEPARATOR, proxies));
        } else {
            args.put(FIELD_PROXY, "");
        }
        args.put(FIELD_REG_USE_PROXY, reg_use_proxy);

        // Assume we have an unique credential
        args.put(FIELD_REALM, realm);
        args.put(FIELD_SCHEME, scheme);
        args.put(FIELD_USERNAME, username);
        args.put(FIELD_DATATYPE, datatype);
        if (!TextUtils.isEmpty(data)) {
            args.put(FIELD_PASSWORD, data);
        }
        args.put(FIELD_AUTH_INITIAL_AUTH, initial_auth ? 1 : 0);
        if(!TextUtils.isEmpty(auth_algo)) {
            args.put(FIELD_AUTH_ALGO, auth_algo);
        }

        args.put(FIELD_SIP_STACK, sip_stack);
        args.put(FIELD_MWI_ENABLED, mwi_enabled);
        args.put(FIELD_VOICE_MAIL_NBR, vm_nbr);
        args.put(FIELD_REG_DELAY_BEFORE_REFRESH, reg_delay_before_refresh);
        args.put(FIELD_TRY_CLEAN_REGISTERS, try_clean_registers);
        
        
        args.put(FIELD_RTP_BOUND_ADDR, rtp_bound_addr);
        args.put(FIELD_RTP_ENABLE_QOS, rtp_enable_qos);
        args.put(FIELD_RTP_PORT, rtp_port);
        args.put(FIELD_RTP_PUBLIC_ADDR, rtp_public_addr);
        args.put(FIELD_RTP_QOS_DSCP, rtp_qos_dscp);
        
        args.put(FIELD_VID_IN_AUTO_SHOW, vid_in_auto_show);
        args.put(FIELD_VID_OUT_AUTO_TRANSMIT, vid_out_auto_transmit);
        
        args.put(FIELD_RFC5626_INSTANCE_ID, rfc5626_instance_id);
        args.put(FIELD_RFC5626_REG_ID, rfc5626_reg_id);
        args.put(FIELD_USE_RFC5626, use_rfc5626 ? 1 : 0);

        args.put(FIELD_ANDROID_GROUP, android_group);
        
        args.put(FIELD_SIP_STUN_USE, sip_stun_use);
        args.put(FIELD_MEDIA_STUN_USE, media_stun_use);
        args.put(FIELD_ICE_CFG_USE, ice_cfg_use);
        args.put(FIELD_ICE_CFG_ENABLE, ice_cfg_enable);
        args.put(FIELD_TURN_CFG_USE, turn_cfg_use);
        args.put(FIELD_TURN_CFG_ENABLE, turn_cfg_enable);
        args.put(FIELD_TURN_CFG_SERVER, turn_cfg_server);
        args.put(FIELD_TURN_CFG_USER, turn_cfg_user);
        args.put(FIELD_TURN_CFG_PASSWORD, turn_cfg_password);
        
        args.put(FIELD_IPV6_MEDIA_USE, ipv6_media_use);
        
        args.put(FIELD_CERT_PATH, cert_path);
        args.put(FIELD_CERT_NOT_BEFORE, cert_not_before.getTime());
        args.put(FIELD_CERT_HASH, cert_hash);
        
        args.put(FIELD_XMPP_SERVER, xmpp_server);
        args.put(FIELD_XMPP_SERVICE, xmpp_service);
        args.put(FIELD_XMPP_USER_NAME, xmpp_user);
        args.put(FIELD_XMPP_PASSWORD, xmpp_password);

        args.put(FIELD_LICENSE_TYPE, licenseType);
        args.put(FIELD_LICENSE_ISSUED_ON, licenseIssuedOn.getTime());
        args.put(FIELD_LICENSE_EXPIRES_ON, licenseExpiresOn.getTime());
        args.put(FIELD_LICENSE_EXPIRED, licenseExpired ? 1 : 0);

        if (currentPolicyDate != null){
            args.put(FIELD_CURRENT_POLICY_TIMESTAMP, currentPolicyDate.getTime());
        }
        if (recoveryEmail != null){
            args.put(FIELD_RECOVERY_EMAIL, recoveryEmail);
        }

        return args;
    }

    /**
     * Get the default domain for this account
     * 
     * @return the default domain for this account
     */
    public String getDefaultDomain() {
        SipUri.ParsedSipUriInfos parsedInfo = null;
        if (!TextUtils.isEmpty(reg_uri)) {
            parsedInfo = SipUri.parseSipUri(reg_uri);
        } else if (proxies != null && proxies.length > 0) {
            parsedInfo = SipUri.parseSipUri(proxies[0]);
        }

        if (parsedInfo == null) {
            return null;
        }

        if (parsedInfo.domain != null) {
            String dom = parsedInfo.domain;
            if (parsedInfo.port != 5060) {
                dom += ":" + Integer.toString(parsedInfo.port);
            }
            return dom;
        } else {
            Log.d(THIS_FILE, "Domain not found for this account");
        }
        return null;
    }

    // Android API

    /**
     * Gets the flag of 'Auto Registration'
     * 
     * @return true if auto register this account
     */
    public boolean getAutoRegistration() {
        return true;
    }

    /**
     * Gets the display name of the user.
     * 
     * @return the caller id for this account
     */
    public String getDisplayName() {
        if (acc_id != null) {
            SipUri.ParsedSipContactInfos parsed = SipUri.parseSipContact(acc_id);
            if (parsed.displayName != null) {
                return parsed.displayName;
            }
        }
        return "";
    }

    /**
     * Gets the password.
     * 
     * @return the password of the sip profile Using this from an external
     *         application will always be empty
     */
    public String getPassword() {
        return data;
    }

    /**
     * Gets the (user-defined) name of the profile.
     * 
     * @return the display name for this profile
     */
    public String getProfileName() {
        return display_name;
    }

    /**
     * Gets the network address of the server outbound proxy.
     * 
     * @return the first proxy server if any else empty string
     */
    public String getProxyAddress() {
        if (proxies != null && proxies.length > 0) {
            return proxies[0];
        }
        return "";
    }

    /**
     * Gets the SIP domain when acc_id is username@domain.
     * 
     * @return the sip domain for this account
     */
    public String getSipDomain() {
        SipUri.ParsedSipContactInfos parsed = SipUri.parseSipContact(acc_id);
        if (parsed.domain != null) {
            return parsed.domain;
        }
        return "";
    }

    /**
     * Gets the SIP URI string of this profile.
     */
    public String getUriString() {
        return acc_id;
    }

    /**
     * Gets the username when acc_id is username@domain. WARNING : this is
     * different from username of SipProfile which is the authentication name
     * cause of pjsip naming
     * 
     * @return the username of the account sip id. <br/>
     *         Example if acc_id is "Display Name" &lt;sip:user@domain.com&gt;, it
     *         will return user.
     */
    public String getSipUserName() {
        SipUri.ParsedSipContactInfos parsed = SipUri.parseSipContact(acc_id);
        if (parsed.userName != null) {
            return parsed.userName;
        }
        return "";
    }

    public static SipProfile getCurrentProfile(Context ctx){
        return getCurrentProfile(ctx, LISTABLE_PROJECTION); // with license information
    }

    public static SipProfile getCurrentProfile(Context ctx, String[] projection){
        return getProfileFromDbId(ctx, USER_ID, projection);
    }

    // Helpers static factory
    /**
     * Helper method to retrieve a SipProfile object from its account database
     * id.<br/>
     * You have to specify the projection you want to use for to retrieve infos.<br/>
     * As consequence the wrapper SipProfile object you'll get may be
     * incomplete. So take care if you try to reinject it by updating to not
     * override existing values of the database that you don't get here.
     * 
     * @param ctxt Your application context. Mainly useful to get the content provider for the request.
     * @param accountId The sip profile {@link #FIELD_ID} you want to retrieve.
     * @param projection The list of fields you want to retrieve. Must be in FIELD_* of this class.<br/>
     * Reducing your requested fields to minimum will improve speed of the request.
     * @return A wrapper SipProfile object on the request you done. If not found an invalid account with an {@link #id} equals to {@link #INVALID_ID}
     */
    public static SipProfile getProfileFromDbId(Context ctxt, long accountId, String[] projection) {
        SipProfile account = null;
        if (accountId != INVALID_ID) {
            Cursor c = ctxt.getContentResolver().query(
                    ContentUris.withAppendedId(ACCOUNT_ID_URI_BASE, accountId),
                    projection, null, null, null);

            if (c != null) {
                try {
                    if (c.getCount() > 0) {
                        c.moveToFirst();
                        account = new SipProfile(c);
                    }
                } catch (Exception e) {
                    Log.e(THIS_FILE, "Something went wrong while retrieving the account", e);
                } finally {
                    c.close();
                }
            } else {
                Log.wf(THIS_FILE, "Null cursor");
            }
        }
        return account;
    }

    /**
     * Get the list of sip profiles available.
     * @param ctxt Your application context. Mainly useful to get the content provider for the request.
     * @param onlyActive Pass it to true if you are only interested in active accounts.
     * @return The list of SipProfiles containings only fields of {@link #LISTABLE_PROJECTION} filled.
     * @see #LISTABLE_PROJECTION
     */
    public static ArrayList<SipProfile> getAllProfiles(Context ctxt, boolean onlyActive) {
        return getAllProfiles(ctxt, onlyActive, LISTABLE_PROJECTION);
    }
    
    /**
     * Returns profile that matches given sip name from user database.
     * @param ctxt
     * @param sipName
     * @param projection
     * @return
     */
    public static SipProfile getProfileFromDbName(Context ctxt, String sipName, boolean onlyActive, String[] projection) {
    	if (sipName==null || sipName.length()==0)
    		return null;
    	
    	ArrayList<SipProfile> profList = getAllProfiles(ctxt, onlyActive);
    	if (profList==null || profList.isEmpty())
    		return null;
    	
    	final String searchedSip = SipUri.getCanonicalSipContact(sipName, false);
    	for(SipProfile p : profList){
    		final String usrName = p.getSipUserName() + "@" + p.getSipDomain();
    		
    		if (usrName!=null && usrName.equalsIgnoreCase(searchedSip))
    			return p;
    		if (p.username != null && p.username.equalsIgnoreCase(searchedSip))
    			return p;
    		if (p.acc_id != null && p.acc_id.equalsIgnoreCase(searchedSip))
    			return p;
    	}
    	
    	return null;
    	
    }
    
    /**
     * Get the list of sip profiles available.
     * @param ctxt Your application context. Mainly useful to get the content provider for the request.
     * @param onlyActive Pass it to true if you are only interested in active accounts.
     * @param projection The projection to use for cursor
     * @return The list of SipProfiles
     */
    public static ArrayList<SipProfile> getAllProfiles(Context ctxt, boolean onlyActive, String[] projection) {
        ArrayList<SipProfile> result = new ArrayList<SipProfile>();

        String selection = null;
        String[] selectionArgs = null;
        if (onlyActive) {
            selection = SipProfile.FIELD_ACTIVE + "=?";
            selectionArgs = new String[] {
                    "1"
            };
        }
        Cursor c = ctxt.getContentResolver().query(ACCOUNT_URI, projection, selection, selectionArgs, null);

        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    do {
                        result.add(new SipProfile(c));
                    } while (c.moveToNext());
                }
            } catch (Exception e) {
                Log.e(THIS_FILE, "Error on looping over sip profiles", e);
            } finally {
                c.close();
            }
        }

        return result;
    }

    public static int updateProfile(ContentResolver cr, long profileId, ContentValues cv){
        return cr.update(ACCOUNT_URI, cv,
                SipProfile.FIELD_ID + "=?",
                new String[] { String.valueOf(profileId) });
    }

	/**
	 * @return the cert_path
	 */
	public String getCert_path() {
		return cert_path;
	}

	/**
	 * @param cert_path the cert_path to set
	 */
	public void setCert_path(String cert_path) {
		this.cert_path = cert_path;
	}

	/**
	 * @return the cert_not_before
	 */
	public Date getCert_not_before() {
		return cert_not_before;
	}

	/**
	 * @param cert_not_before the cert_not_before to set
	 */
	public void setCert_not_before(Date cert_not_before) {
		this.cert_not_before = cert_not_before;
	}

	/**
	 * @return the cert_hash
	 */
	public String getCert_hash() {
		return cert_hash;
	}

    public String getSip(){
        return SipUri.getCanonicalSipContact(acc_id, false);
    }

    public String getSip(boolean includeScheme){
        return SipUri.getCanonicalSipContact(acc_id, includeScheme);
    }

	/**
	 * @param cert_hash the cert_hash to set
	 */
	public void setCert_hash(String cert_hash) {
		this.cert_hash = cert_hash;
	}

	public String getXmpp_server() {
		return xmpp_server;
	}

	public void setXmpp_server(String xmpp_server) {
		this.xmpp_server = xmpp_server;
	}

	public String getXmpp_service() {
		return xmpp_service;
	}

	public void setXmpp_service(String xmpp_service) {
		this.xmpp_service = xmpp_service;
	}

	public String getXmpp_user() {
		return xmpp_user;
	}

	public void setXmpp_user(String xmpp_user) {
		this.xmpp_user = xmpp_user;
	}

	public String getXmpp_password() {
		return xmpp_password;
	}

	public void setXmpp_password(String xmpp_password) {
		this.xmpp_password = xmpp_password;
	}

	public String getUsername() {
		return username;
	}

    public String getLicenseType() {
        return licenseType;
    }

    public void setLicenseType(String licenseType) {
        this.licenseType = licenseType;
    }

    public Date getLicenseIssuedOn() {
        return licenseIssuedOn;
    }

    public void setLicenseIssuedOn(Date licenseIssuedOn) {
        this.licenseIssuedOn = licenseIssuedOn;
    }

    public Date getLicenseExpiresOn() {
        return licenseExpiresOn;
    }

    public void setLicenseExpiresOn(Date licenseExpiresOn) {
        this.licenseExpiresOn = licenseExpiresOn;
    }

    public Date getCurrentPolicyDate() {
        return currentPolicyDate;
    }

    public void setCurrentPolicyDate(Date currentPolicyDate) {
        this.currentPolicyDate = currentPolicyDate;
    }

    public boolean isLicenseExpired() {
        return licenseExpired;
    }

    public void setLicenseExpired(boolean licenseExpired) {
        this.licenseExpired = licenseExpired;
    }

    public LicenseInformation getLicenseInformation(){
        return new LicenseInformation(this);
    }

    public long getId() {
        return id;
    }

    public int getPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(int primaryKey) {
        this.primaryKey = primaryKey;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getDisplay_name() {
        return display_name;
    }

    public void setDisplay_name(String display_name) {
        this.display_name = display_name;
    }

    public String getAccountManager() {
        return accountManager;
    }

    public void setAccountManager(String accountManager) {
        this.accountManager = accountManager;
    }

    public Integer getTransport() {
        return transport;
    }

    public void setTransport(Integer transport) {
        this.transport = transport;
    }

    public String getDefault_uri_scheme() {
        return default_uri_scheme;
    }

    public void setDefault_uri_scheme(String default_uri_scheme) {
        this.default_uri_scheme = default_uri_scheme;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getAcc_id() {
        return acc_id;
    }

    public void setAcc_id(String acc_id) {
        this.acc_id = acc_id;
    }

    public String getReg_uri() {
        return reg_uri;
    }

    public void setReg_uri(String reg_uri) {
        this.reg_uri = reg_uri;
    }

    public int getPublish_enabled() {
        return publish_enabled;
    }

    public void setPublish_enabled(int publish_enabled) {
        this.publish_enabled = publish_enabled;
    }

    public int getReg_timeout() {
        return reg_timeout;
    }

    public void setReg_timeout(int reg_timeout) {
        this.reg_timeout = reg_timeout;
    }

    public int getKa_interval() {
        return ka_interval;
    }

    public void setKa_interval(int ka_interval) {
        this.ka_interval = ka_interval;
    }

    public String getPidf_tuple_id() {
        return pidf_tuple_id;
    }

    public void setPidf_tuple_id(String pidf_tuple_id) {
        this.pidf_tuple_id = pidf_tuple_id;
    }

    public String getForce_contact() {
        return force_contact;
    }

    public void setForce_contact(String force_contact) {
        this.force_contact = force_contact;
    }

    public boolean isAllow_contact_rewrite() {
        return allow_contact_rewrite;
    }

    public void setAllow_contact_rewrite(boolean allow_contact_rewrite) {
        this.allow_contact_rewrite = allow_contact_rewrite;
    }

    public int getContact_rewrite_method() {
        return contact_rewrite_method;
    }

    public void setContact_rewrite_method(int contact_rewrite_method) {
        this.contact_rewrite_method = contact_rewrite_method;
    }

    public boolean isAllow_via_rewrite() {
        return allow_via_rewrite;
    }

    public void setAllow_via_rewrite(boolean allow_via_rewrite) {
        this.allow_via_rewrite = allow_via_rewrite;
    }

    public String[] getProxies() {
        return proxies;
    }

    public void setProxies(String[] proxies) {
        this.proxies = proxies;
    }

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public int getDatatype() {
        return datatype;
    }

    public void setDatatype(int datatype) {
        this.datatype = datatype;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public boolean isInitial_auth() {
        return initial_auth;
    }

    public void setInitial_auth(boolean initial_auth) {
        this.initial_auth = initial_auth;
    }

    public String getAuth_algo() {
        return auth_algo;
    }

    public void setAuth_algo(String auth_algo) {
        this.auth_algo = auth_algo;
    }

    public int getUse_srtp() {
        return use_srtp;
    }

    public void setUse_srtp(int use_srtp) {
        this.use_srtp = use_srtp;
    }

    public int getUse_zrtp() {
        return use_zrtp;
    }

    public void setUse_zrtp(int use_zrtp) {
        this.use_zrtp = use_zrtp;
    }

    public int getReg_use_proxy() {
        return reg_use_proxy;
    }

    public void setReg_use_proxy(int reg_use_proxy) {
        this.reg_use_proxy = reg_use_proxy;
    }

    public int getSip_stack() {
        return sip_stack;
    }

    public void setSip_stack(int sip_stack) {
        this.sip_stack = sip_stack;
    }

    public String getVm_nbr() {
        return vm_nbr;
    }

    public void setVm_nbr(String vm_nbr) {
        this.vm_nbr = vm_nbr;
    }

    public int getReg_delay_before_refresh() {
        return reg_delay_before_refresh;
    }

    public void setReg_delay_before_refresh(int reg_delay_before_refresh) {
        this.reg_delay_before_refresh = reg_delay_before_refresh;
    }

    public int getTry_clean_registers() {
        return try_clean_registers;
    }

    public void setTry_clean_registers(int try_clean_registers) {
        this.try_clean_registers = try_clean_registers;
    }

    public Bitmap getIcon() {
        return icon;
    }

    public void setIcon(Bitmap icon) {
        this.icon = icon;
    }

    public boolean isUse_rfc5626() {
        return use_rfc5626;
    }

    public void setUse_rfc5626(boolean use_rfc5626) {
        this.use_rfc5626 = use_rfc5626;
    }

    public String getRfc5626_instance_id() {
        return rfc5626_instance_id;
    }

    public void setRfc5626_instance_id(String rfc5626_instance_id) {
        this.rfc5626_instance_id = rfc5626_instance_id;
    }

    public String getRfc5626_reg_id() {
        return rfc5626_reg_id;
    }

    public void setRfc5626_reg_id(String rfc5626_reg_id) {
        this.rfc5626_reg_id = rfc5626_reg_id;
    }

    public int getVid_in_auto_show() {
        return vid_in_auto_show;
    }

    public void setVid_in_auto_show(int vid_in_auto_show) {
        this.vid_in_auto_show = vid_in_auto_show;
    }

    public int getVid_out_auto_transmit() {
        return vid_out_auto_transmit;
    }

    public void setVid_out_auto_transmit(int vid_out_auto_transmit) {
        this.vid_out_auto_transmit = vid_out_auto_transmit;
    }

    public int getRtp_port() {
        return rtp_port;
    }

    public void setRtp_port(int rtp_port) {
        this.rtp_port = rtp_port;
    }

    public String getRtp_public_addr() {
        return rtp_public_addr;
    }

    public void setRtp_public_addr(String rtp_public_addr) {
        this.rtp_public_addr = rtp_public_addr;
    }

    public String getRtp_bound_addr() {
        return rtp_bound_addr;
    }

    public void setRtp_bound_addr(String rtp_bound_addr) {
        this.rtp_bound_addr = rtp_bound_addr;
    }

    public int getRtp_enable_qos() {
        return rtp_enable_qos;
    }

    public void setRtp_enable_qos(int rtp_enable_qos) {
        this.rtp_enable_qos = rtp_enable_qos;
    }

    public int getRtp_qos_dscp() {
        return rtp_qos_dscp;
    }

    public void setRtp_qos_dscp(int rtp_qos_dscp) {
        this.rtp_qos_dscp = rtp_qos_dscp;
    }

    public String getAndroid_group() {
        return android_group;
    }

    public void setAndroid_group(String android_group) {
        this.android_group = android_group;
    }

    public boolean isMwi_enabled() {
        return mwi_enabled;
    }

    public void setMwi_enabled(boolean mwi_enabled) {
        this.mwi_enabled = mwi_enabled;
    }

    public int getSip_stun_use() {
        return sip_stun_use;
    }

    public void setSip_stun_use(int sip_stun_use) {
        this.sip_stun_use = sip_stun_use;
    }

    public int getMedia_stun_use() {
        return media_stun_use;
    }

    public void setMedia_stun_use(int media_stun_use) {
        this.media_stun_use = media_stun_use;
    }

    public int getIce_cfg_use() {
        return ice_cfg_use;
    }

    public void setIce_cfg_use(int ice_cfg_use) {
        this.ice_cfg_use = ice_cfg_use;
    }

    public int getIce_cfg_enable() {
        return ice_cfg_enable;
    }

    public void setIce_cfg_enable(int ice_cfg_enable) {
        this.ice_cfg_enable = ice_cfg_enable;
    }

    public int getTurn_cfg_use() {
        return turn_cfg_use;
    }

    public void setTurn_cfg_use(int turn_cfg_use) {
        this.turn_cfg_use = turn_cfg_use;
    }

    public int getTurn_cfg_enable() {
        return turn_cfg_enable;
    }

    public void setTurn_cfg_enable(int turn_cfg_enable) {
        this.turn_cfg_enable = turn_cfg_enable;
    }

    public String getTurn_cfg_server() {
        return turn_cfg_server;
    }

    public void setTurn_cfg_server(String turn_cfg_server) {
        this.turn_cfg_server = turn_cfg_server;
    }

    public String getTurn_cfg_user() {
        return turn_cfg_user;
    }

    public void setTurn_cfg_user(String turn_cfg_user) {
        this.turn_cfg_user = turn_cfg_user;
    }

    public String getTurn_cfg_password() {
        return turn_cfg_password;
    }

    public void setTurn_cfg_password(String turn_cfg_password) {
        this.turn_cfg_password = turn_cfg_password;
    }

    public int getIpv6_media_use() {
        return ipv6_media_use;
    }

    public void setIpv6_media_use(int ipv6_media_use) {
        this.ipv6_media_use = ipv6_media_use;
    }

    public String getWizard_data() {
        return wizard_data;
    }

    public void setWizard_data(String wizard_data) {
        this.wizard_data = wizard_data;
    }

    public String getRecoveryEmail() {
        return recoveryEmail;
    }

    public void setRecoveryEmail(String recoveryEmail) {
        this.recoveryEmail = recoveryEmail;
    }

    @Override
    public String toString() {
        return "SipProfile{" +
                "locale=" + locale +
                ", primaryKey=" + primaryKey +
                ", id=" + id +
                ", display_name='" + display_name + '\'' +
                ", accountManager='" + accountManager + '\'' +
                ", transport=" + transport +
                ", default_uri_scheme='" + default_uri_scheme + '\'' +
                ", active=" + active +
                ", priority=" + priority +
                ", acc_id='" + acc_id + '\'' +
                ", reg_uri='" + reg_uri + '\'' +
                ", publish_enabled=" + publish_enabled +
                ", reg_timeout=" + reg_timeout +
                ", ka_interval=" + ka_interval +
                ", pidf_tuple_id='" + pidf_tuple_id + '\'' +
                ", force_contact='" + force_contact + '\'' +
                ", allow_contact_rewrite=" + allow_contact_rewrite +
                ", contact_rewrite_method=" + contact_rewrite_method +
                ", allow_via_rewrite=" + allow_via_rewrite +
                ", proxies=" + Arrays.toString(proxies) +
                ", realm='" + realm + '\'' +
                ", username='" + username + '\'' +
                ", scheme='" + scheme + '\'' +
                ", datatype=" + datatype +
                ", data='" + data + '\'' +
                ", initial_auth=" + initial_auth +
                ", auth_algo='" + auth_algo + '\'' +
                ", use_srtp=" + use_srtp +
                ", use_zrtp=" + use_zrtp +
                ", reg_use_proxy=" + reg_use_proxy +
                ", sip_stack=" + sip_stack +
                ", vm_nbr='" + vm_nbr + '\'' +
                ", reg_delay_before_refresh=" + reg_delay_before_refresh +
                ", try_clean_registers=" + try_clean_registers +
                ", icon=" + icon +
                ", use_rfc5626=" + use_rfc5626 +
                ", rfc5626_instance_id='" + rfc5626_instance_id + '\'' +
                ", rfc5626_reg_id='" + rfc5626_reg_id + '\'' +
                ", vid_in_auto_show=" + vid_in_auto_show +
                ", vid_out_auto_transmit=" + vid_out_auto_transmit +
                ", rtp_port=" + rtp_port +
                ", rtp_public_addr='" + rtp_public_addr + '\'' +
                ", rtp_bound_addr='" + rtp_bound_addr + '\'' +
                ", rtp_enable_qos=" + rtp_enable_qos +
                ", rtp_qos_dscp=" + rtp_qos_dscp +
                ", android_group='" + android_group + '\'' +
                ", mwi_enabled=" + mwi_enabled +
                ", sip_stun_use=" + sip_stun_use +
                ", media_stun_use=" + media_stun_use +
                ", ice_cfg_use=" + ice_cfg_use +
                ", ice_cfg_enable=" + ice_cfg_enable +
                ", turn_cfg_use=" + turn_cfg_use +
                ", turn_cfg_enable=" + turn_cfg_enable +
                ", turn_cfg_server='" + turn_cfg_server + '\'' +
                ", turn_cfg_user='" + turn_cfg_user + '\'' +
                ", turn_cfg_password='" + turn_cfg_password + '\'' +
                ", ipv6_media_use=" + ipv6_media_use +
                ", wizard_data='" + wizard_data + '\'' +
                ", cert_path='" + cert_path + '\'' +
                ", cert_not_before=" + cert_not_before +
                ", cert_hash='" + cert_hash + '\'' +
                ", xmpp_server='" + xmpp_server + '\'' +
                ", xmpp_service='" + xmpp_service + '\'' +
                ", xmpp_user='" + xmpp_user + '\'' +
                ", xmpp_password='" + xmpp_password + '\'' +
                ", licenseType='" + licenseType + '\'' +
                ", licenseIssuedOn=" + licenseIssuedOn +
                ", licenseExpiresOn=" + licenseExpiresOn +
                ", licenseExpired=" + licenseExpired +
                ", currentPolicyDate=" + currentPolicyDate +
                ", recoveryEmail='" + recoveryEmail + '\'' +
                '}';
    }
}
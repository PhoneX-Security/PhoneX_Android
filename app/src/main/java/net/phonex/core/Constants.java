package net.phonex.core;

/**
 * Manage SIP application globally <br/>
 * Define intent, action, broadcast, extra constants <br/>
 * It also define authority and uris for some content holds by the internal
 * database
 */
public final class Constants {
    // -------
    // Static constants
    // PERMISSION
    public static final String PERMISSION_PHONEX = "android.permission.PHONEX";

    //    public static final String ACTION_PAIRING_REQUESTS_CHANGED = "net.phonex.PAIRING_REQUESTS_CHANGED";

    // SERVICE intents
    /**
     * Bind Xservice to control calls.<br/>
     * If you start the service using {@link android.content.Context#startService(android.content.Intent intent)}
     * , you may want to pass {@link Intents#EXTRA_OUTGOING_ACTIVITY} to specify you
     * are starting the service in order to make outgoing calls. You are then in
     * charge to unregister for outgoing calls when user finish with your
     * activity or when you are not anymore in calls using
     * {@link Intents#ACTION_OUTGOING_UNREGISTER}<br/>
     * If you actually make a call or ask service to do something but wants to
     * unregister, you must defer unregister of your activity using
     * {@link Intents#ACTION_DEFER_OUTGOING_UNREGISTER}.
     *
     * @see net.phonex.core.IService
     * @see Intents#EXTRA_OUTGOING_ACTIVITY
     */
    public static final String INTENT_XSERVICE = "net.phonex.service.XService";

    /**
     * Bind SafeNet service.<br/>
     * 
     * @see net.phonex.core.IService
     * @see Intents#EXTRA_OUTGOING_ACTIVITY
     */
    public static final String INTENT_SAFENET_SERVICE = "net.phonex.service.SafeNetService";

    /**
     * Scheme for csip uri.
     */
    public static final String PROTOCOL_CSIP = "csip";
    /**
     * Scheme for sip uri.
     */
    public static final String PROTOCOL_SIP = "sip";
    /**
     * Scheme for sips (sip+tls) uri.
     */
    public static final String PROTOCOL_SIPS = "sips";

    /**
     * Bitmask to keep media/call coming from outside
     */
    public final static int BITMASK_IN = 1 << 0;
    /**
     * Bitmask to keep only media/call coming from the app
     */
    public final static int BITMASK_OUT = 1 << 1;
    /**
     * Bitmask to keep all media/call whatever incoming/outgoing
     */
    public final static int BITMASK_ALL = BITMASK_IN | BITMASK_OUT;
    
    /**
     * Plugin action for rewrite numbers. <br/>     
     * You can expect {@link android.content.Intent#EXTRA_PHONE_NUMBER} as argument for the
     * number to rewrite. <br/>
     * Your receiver must
     * {@link android.content.BroadcastReceiver#getResultExtras(boolean)} with parameter true to
     * fill response. <br/>
     * Your response contains :
     * <ul>
     * <li>{@link android.content.Intent#EXTRA_PHONE_NUMBER} with
     * {@link java.lang.String} (optional) : Rewritten phone number.</li>
     * </ul>
     */

    // Content provider
    /**
     * Authority for regular database of the application.
     */
    public static final String AUTHORITY = "net.phonex.db";

    /**
     * Base content type for phonex objects.
     */
    public static final String BASE_DIR_TYPE = "vnd.android.cursor.dir/vnd.phonex";
    /**
     * Base item content type for phonex objects.
     */
    public static final String BASE_ITEM_TYPE = "vnd.android.cursor.item/vnd.phonex";

    // -- Extra fields for call logs
    /**
     * The account used for this call
     */
    // Content Provider - filter
    /**
     * Content type for filter provider.
     */
    public static final String FILTER_CONTENT_TYPE = BASE_DIR_TYPE + ".filter";
    /**
     * FileItemInfo type for filter provider.
     */
    public static final String FILTER_CONTENT_ITEM_TYPE = BASE_ITEM_TYPE + ".filter";

    // EXTRAS

    // Constants
    /**
     * Constant for success return
     */
    public static final int SUCCESS = 0;
    /**
     * Constant for network errors return
     */
    public static final int ERROR_CURRENT_NETWORK = 10;

    // SIP secured message MIME type for text messages
    // when decrypted, saved as text/plain (see SipMessage.MIME_TEXT)
    public static final String SIP_SECURE_MSG_MIME = "application/x-phonex-mime";
    // SIP notification MIME
    // when decrypted, saved as text/file (see SipMessage.MIME_FILE)
    public static final String SIP_SECURE_FILE_NOTIFY_MIME = "application/x-phonex-file-notification-mime";
    
    /**
     * Capabilities.
     */

    // UNUSED CAPABILITIES
//    public static final String CAP_SIP  = "1";
//    public static final String CAP_XMPP = "2";
//    public static final String CAP_XMPP_PRESENCE = "2.1";
//    public static final String CAP_XMPP_MESSAGES = "2.2";
//    public static final String CAP_PROTOCOL = "3";
//    public static final String CAP_PROTOCOL_FILETRANSFER = "3.2";
//    public static final String CAP_PROTOCOL_MESSAGES_2 = "3.1.2";

    // Message protocol v2 - based on protocol buffers
    public static final String CAP_PROTOCOL_MESSAGES_2_1 = "3.1.2.1";
    /**
     * Includes:
     * Upgraded STP_SIMPLE from version 2 to 3
     * Upgraded STP_SIMPLE_AUTH from version 1 to 2
     */
    public static final String CAP_PROTOCOL_MESSAGES_2_2 = "3.1.2.2";


    /**
     * Support for status notifications via GCM/ApplePush
     */
    public static final String CAP_PUSH = "p";

}

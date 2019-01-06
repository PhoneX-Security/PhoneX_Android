package net.phonex.sip;

/**
 * Status code of the sip call dialog Actually just shortcuts to SIP codes<br/>
 * <a target="_blank" href=
 * "http://www.pjsip.org/pjsip/docs/html/group__PJSIP__MSG__LINE.htm#gaf6d60351ee68ca0c87358db2e59b9376"
 * >Pjsip documentation</a>
 */
public class SipStatusCode {
    public static final int TRYING = 100;
    public static final int RINGING = 180;
    public static final int CALL_BEING_FORWARDED = 181;
    public static final int QUEUED = 182;
    public static final int PROGRESS = 183;
    public static final int OK = 200;
    public static final int ACCEPTED = 202;
    public static final int MULTIPLE_CHOICES = 300;
    public static final int MOVED_PERMANENTLY = 301;
    public static final int MOVED_TEMPORARILY = 302;
    public static final int USE_PROXY = 305;
    public static final int ALTERNATIVE_SERVICE = 380;
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int PAYMENT_REQUIRED = 402;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int METHOD_NOT_ALLOWED = 405;
    public static final int NOT_ACCEPTABLE = 406;
    public static final int REQUEST_TIMEOUT = 408;
    public static final int GONE = 410;
    public static final int TEMPORARILY_UNAVAILABLE = 480;
    public static final int PJSIP_SC_CALL_TSX_DOES_NOT_EXIST = 481;
    public static final int BUSY_HERE = 486;
    public static final int REQUEST_TERMINATED = 487;
    public static final int INTERVAL_TOO_BRIEF = 423;
    public static final int INTERNAL_SERVER_ERROR = 500;
    public static final int NOT_IMPLEMENTED = 501;
    public static final int SERVICE_UNAVAILABLE = 503;
    public static final int DECLINE = 603;
    /*
     * PJSIP_SC_PROXY_AUTHENTICATION_REQUIRED = 407,
     * PJSIP_SC_REQUEST_TIMEOUT = 408, PJSIP_SC_GONE = 410,
     * PJSIP_SC_REQUEST_ENTITY_TOO_LARGE = 413,
     * PJSIP_SC_REQUEST_URI_TOO_LONG = 414, PJSIP_SC_UNSUPPORTED_MEDIA_TYPE
     * = 415, PJSIP_SC_UNSUPPORTED_URI_SCHEME = 416, PJSIP_SC_BAD_EXTENSION
     * = 420, PJSIP_SC_EXTENSION_REQUIRED = 421,
     * PJSIP_SC_SESSION_TIMER_TOO_SMALL = 422,
     * PJSIP_SC_TEMPORARILY_UNAVAILABLE = 480,
     * , PJSIP_SC_LOOP_DETECTED = 482,
     * PJSIP_SC_TOO_MANY_HOPS = 483, PJSIP_SC_ADDRESS_INCOMPLETE = 484,
     * PJSIP_AC_AMBIGUOUS = 485, PJSIP_SC_BUSY_HERE = 486,
     * , PJSIP_SC_NOT_ACCEPTABLE_HERE =
     * 488, PJSIP_SC_BAD_EVENT = 489, PJSIP_SC_REQUEST_UPDATED = 490,
     * PJSIP_SC_REQUEST_PENDING = 491, PJSIP_SC_UNDECIPHERABLE = 493,
     * PJSIP_SC_INTERNAL_SERVER_ERROR = 500, PJSIP_SC_NOT_IMPLEMENTED = 501,
     * PJSIP_SC_BAD_GATEWAY = 502, PJSIP_SC_SERVICE_UNAVAILABLE = 503,
     * PJSIP_SC_SERVER_TIMEOUT = 504, PJSIP_SC_VERSION_NOT_SUPPORTED = 505,
     * PJSIP_SC_MESSAGE_TOO_LARGE = 513, PJSIP_SC_PRECONDITION_FAILURE =
     * 580, PJSIP_SC_BUSY_EVERYWHERE = 600, PJSIP_SC_DOES_NOT_EXIST_ANYWHERE
     * = 604, PJSIP_SC_NOT_ACCEPTABLE_ANYWHERE = 606,
     */
}

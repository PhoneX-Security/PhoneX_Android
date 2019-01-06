


// From pjlib/include/pj/types.h:63
typedef int               pj_status_t;
// From pjlib/include/pj/types.h:66
typedef int               pj_bool_t;
// From pjlib/include/pj/types.h:48
typedef unsigned short    pj_uint16_t;
// From pjlib/include/pj/types.h:42
typedef unsigned int      pj_uint32_t;
// From pjlib/include/pj/types.h:54
typedef unsigned char     pj_uint8_t;

// From pjlib/include/pj/types.h:86
/** Status is OK. */
#define PJ_SUCCESS  0
/** True value. */
#define PJ_TRUE     1
/** False value. */
#define PJ_FALSE    0

// From pjlib/include/pj/types.h:57
typedef size_t pj_size_t;

// From pjlib/include/pj/types.h:111
struct pj_str_t
{
    /** Buffer pointer, which is by convention NOT null terminated. */
    char       *ptr;

    /** The length of the string. */
    pj_ssize_t  slen;
};


// From pjmedia/include/pjmedia/port.h
typedef struct pjmedia_port_info
{
    pj_str_t	    name;		/**< Port name.			    */
    pj_uint32_t	    signature;		/**< Port signature.		    */
    pjmedia_dir     dir;                /**< Port direction.                */
    pjmedia_format  fmt;                /**< Format.		            */
} pjmedia_port_info;


typedef struct pjmedia_port
{
    pjmedia_port_info    info;              /**< Port information.  */

    /** Port data can be used by the port creator to attach arbitrary
     *  value to be associated with the port.
     */
    struct port_data {
	void		*pdata;		    /**< Pointer data.	    */
	long		 ldata;		    /**< Long data.	    */
    } port_data;

    /**
     * Sink interface.
     * This should only be called by #pjmedia_port_put_frame().
     */
    pj_status_t (*put_frame)(struct pjmedia_port *this_port,
                             pjmedia_frame *frame);

    /**
     * Source interface.
     * This should only be called by #pjmedia_port_get_frame().
     */
    pj_status_t (*get_frame)(struct pjmedia_port *this_port,
                             pjmedia_frame *frame);

    /**
     * Called to destroy this port.
     */
    pj_status_t (*on_destroy)(struct pjmedia_port *this_port);

} pjmedia_port;




// From pjmedia/include/pjmedia/types.h:97
typedef enum pjmedia_dir
{
    /** None */
            PJMEDIA_DIR_NONE = 0,

    /** Encoding (outgoing to network) stream, also known as capture */
            PJMEDIA_DIR_ENCODING = 1,

    /** Same as encoding direction. */
            PJMEDIA_DIR_CAPTURE = PJMEDIA_DIR_ENCODING,

    /** Decoding (incoming from network) stream, also known as playback. */
            PJMEDIA_DIR_DECODING = 2,

    /** Same as decoding. */
            PJMEDIA_DIR_PLAYBACK = PJMEDIA_DIR_DECODING,

    /** Same as decoding. */
            PJMEDIA_DIR_RENDER = PJMEDIA_DIR_DECODING,

    /** Incoming and outgoing stream, same as PJMEDIA_DIR_CAPTURE_PLAYBACK */
            PJMEDIA_DIR_ENCODING_DECODING = 3,

    /** Same as ENCODING_DECODING */
            PJMEDIA_DIR_CAPTURE_PLAYBACK = PJMEDIA_DIR_ENCODING_DECODING,

    /** Same as ENCODING_DECODING */
            PJMEDIA_DIR_CAPTURE_RENDER = PJMEDIA_DIR_ENCODING_DECODING

} pjmedia_dir;

// From pjsip/include/pjsip/sip_auth.h:108
struct pjsip_cred_info
{
    pj_str_t    realm;          /**< Realm. Use "*" to make a credential that
                                     can be used to authenticate against any
                                     challenges.                            */
    pj_str_t    scheme;         /**< Scheme (e.g. "digest").                */
    pj_str_t    username;       /**< User name.                             */
    int         data_type;      /**< Type of data (0 for plaintext passwd). */
    pj_str_t    data;           /**< The data, which can be a plaintext
                                     password or a hashed digest.           */

    /** Extended data */
    union ext {
        /** Digest AKA credential information. Note that when AKA credential
         *  is being used, the \a data field of this #pjsip_cred_info is
         *  not used, but it still must be initialized to an empty string.
         * Please see \ref PJSIP_AUTH_AKA_API for more information.
         */
        struct aka {
            pj_str_t      k;    /**< Permanent subscriber key.          */
            pj_str_t      op;   /**< Operator variant key.              */
            pj_str_t      amf;  /**< Authentication Management Field    */
            pjsip_cred_cb cb;   /**< Callback to create AKA digest.     */
        } aka;

    } ext;
};

// From pjsip/include/pjsip/sip_auth.h:50
enum pjsip_cred_data_type
{
    PJSIP_CRED_DATA_PLAIN_PASSWD=0, /**< Plain text password.           */
    PJSIP_CRED_DATA_DIGEST      =1, /**< Hashed digest.                 */

    PJSIP_CRED_DATA_EXT_AKA     =16 /**< Extended AKA info is available */

};


// From pjmedia/include/pjmedia/tonegen.h:105
enum
{
    /**
     * Play the tones in loop, restarting playing the first tone after
     * the last tone has been played.
     */
    PJMEDIA_TONEGEN_LOOP    = 1,

    /**
     * Disable mutex protection to the tone generator.
     */
    PJMEDIA_TONEGEN_NO_LOCK = 2
};

// From pjsip/include/pjsip/sip_event.h:79
struct pjsip_event
{
    /** This is necessary so that we can put events as a list. */
    PJ_DECL_LIST_MEMBER(struct pjsip_event);

    /** The event type, can be any value of \b pjsip_event_id_e.
     */
    pjsip_event_id_e type;

    /**
     * The event body as union, which fields depends on the event type.
     * By convention, the first member of each struct in the union must be
     * the pointer which is relevant to the event.
     */
    union body
    {
        /** Timer event. */
        struct
        {
            pj_timer_entry *entry;      /**< The timer entry.           */
        } timer;

        /** Transaction state has changed event. */
        struct
        {
            union
            {
                pjsip_rx_data   *rdata; /**< The incoming message.      */
                pjsip_tx_data   *tdata; /**< The outgoing message.      */
                pj_timer_entry  *timer; /**< The timer.                 */
                pj_status_t      status;/**< Transport error status.    */
                void            *data;  /**< Generic data.              */
            } src;
            pjsip_transaction   *tsx;   /**< The transaction.           */
            int                  prev_state; /**< Previous state.       */
            pjsip_event_id_e     type;  /**< Type of event source:
                                         *      - PJSIP_EVENT_TX_MSG
                                         *      - PJSIP_EVENT_RX_MSG,
                                         *      - PJSIP_EVENT_TRANSPORT_ERROR
                                         *      - PJSIP_EVENT_TIMER
                                         *      - PJSIP_EVENT_USER
                                         */
        } tsx_state;

        /** Message transmission event. */
        struct
        {
            pjsip_tx_data       *tdata; /**< The transmit data buffer.  */

        } tx_msg;

        /** Transmission error event. */
        struct
        {
            pjsip_tx_data       *tdata; /**< The transmit data.         */
            pjsip_transaction   *tsx;   /**< The transaction.           */
        } tx_error;

        /** Message arrival event. */
        struct
        {
            pjsip_rx_data       *rdata; /**< The receive data buffer.   */
        } rx_msg;

        /** User event. */
        struct
        {
            void                *user1; /**< User data 1.               */
            void                *user2; /**< User data 2.               */
            void                *user3; /**< User data 3.               */
            void                *user4; /**< User data 4.               */
        } user;

    } body;
};

// From pjmedia/include/pjmedia/sound.h:69
struct pjmedia_snd_dev_info
{
    char        name[64];               /**< Device name.                   */
    unsigned    input_count;            /**< Max number of input channels.  */
    unsigned    output_count;           /**< Max number of output channels. */
    unsigned    default_samples_per_sec;/**< Default sampling rate.         */
};

// From pjmedia/include/pjmedia/tonegen.h:60
struct pjmedia_tone_desc
{
    short   freq1;          /**< First frequency.                           */
    short   freq2;          /**< Optional second frequency.                 */
    short   on_msec;        /**< Playback ON duration, in miliseconds.      */
    short   off_msec;       /**< Playback OFF duration, ini miliseconds.    */
    short   volume;         /**< Volume (1-16383), or 0 for default.        */
};
/**
 * This structure describes individual MF digits to be played
 * with #pjmedia_tonegen_play_digits().
 */
struct pjmedia_tone_digit
{
    char    digit;	    /**< The ASCI identification for the digit.	    */
    short   on_msec;	    /**< Playback ON duration, in miliseconds.	    */
    short   off_msec;	    /**< Playback OFF duration, ini miliseconds.    */
    short   volume;	    /**< Volume (1-32767), or 0 for default, which
				 PJMEDIA_TONEGEN_VOLUME will be used.	    */
};


// From pjlib/include/pj/pool.h:312
struct pj_pool_t
{
    PJ_DECL_LIST_MEMBER(struct pj_pool_t);  /**< Standard list elements.    */

    /** Pool name */
    char            obj_name[PJ_MAX_OBJ_NAME];

    /** Pool factory. */
    pj_pool_factory *factory;

    /** Data put by factory */
    void            *factory_data;

    /** Current capacity allocated by the pool. */
    pj_size_t       capacity;

    /** Size of memory block to be allocated when the pool runs out of memory */
    pj_size_t       increment_size;

    /** List of memory blocks allcoated by the pool. */
    pj_pool_block   block_list;

    /** The callback to be called when the pool is unable to allocate memory. */
    pj_pool_callback *callback;

};

// From pjsip/include/pjsip/sip_event.h:41
enum pjsip_event_id_e
{
    /** Unidentified event. */
    PJSIP_EVENT_UNKNOWN,

    /** Timer event, normally only used internally in transaction. */
    PJSIP_EVENT_TIMER,

    /** Message transmission event. */
    PJSIP_EVENT_TX_MSG,

    /** Message received event. */
    PJSIP_EVENT_RX_MSG,

    /** Transport error event. */
    PJSIP_EVENT_TRANSPORT_ERROR,

    /** Transaction state changed event. */
    PJSIP_EVENT_TSX_STATE,

    /** Indicates that the event was triggered by user action. */
    PJSIP_EVENT_USER

};
/**
 * Transport types.
 */
enum pjsip_transport_type_e
{
    /** Unspecified. */
    PJSIP_TRANSPORT_UNSPECIFIED,

    /** UDP. */
    PJSIP_TRANSPORT_UDP,

    /** TCP. */
    PJSIP_TRANSPORT_TCP,

    /** TLS. */
    PJSIP_TRANSPORT_TLS,

    /** SCTP. */
    PJSIP_TRANSPORT_SCTP,

    /** Loopback (stream, reliable) */
    PJSIP_TRANSPORT_LOOP,

    /** Loopback (datagram, unreliable) */
    PJSIP_TRANSPORT_LOOP_DGRAM,

    /** Start of user defined transport */
    PJSIP_TRANSPORT_START_OTHER,

    /** Start of IPv6 transports */
    PJSIP_TRANSPORT_IPV6    = 128,

    /** UDP over IPv6 */
    PJSIP_TRANSPORT_UDP6 = PJSIP_TRANSPORT_UDP + PJSIP_TRANSPORT_IPV6,

    /** TCP over IPv6 */
    PJSIP_TRANSPORT_TCP6 = PJSIP_TRANSPORT_TCP + PJSIP_TRANSPORT_IPV6,

    /** TLS over IPv6 */
    PJSIP_TRANSPORT_TLS6 = PJSIP_TRANSPORT_TLS + PJSIP_TRANSPORT_IPV6

};

// From pjsip/include/pjsip-ua/sip_inv.h:87
enum pjsip_inv_state
{
    PJSIP_INV_STATE_NULL,           /**< Before INVITE is sent or received  */
    PJSIP_INV_STATE_CALLING,        /**< After INVITE is sent               */
    PJSIP_INV_STATE_INCOMING,       /**< After INVITE is received.          */
    PJSIP_INV_STATE_EARLY,          /**< After response with To tag.        */
    PJSIP_INV_STATE_CONNECTING,     /**< After 2xx is sent/received.        */
    PJSIP_INV_STATE_CONFIRMED,      /**< After ACK is sent/received.        */
    PJSIP_INV_STATE_DISCONNECTED,   /**< Session is terminated.             */
};

// From pjsip/include/pjsip/sip_msg.h:410
enum pjsip_status_code
{
    PJSIP_SC_TRYING = 100,
    PJSIP_SC_RINGING = 180,
    PJSIP_SC_CALL_BEING_FORWARDED = 181,
    PJSIP_SC_QUEUED = 182,
    PJSIP_SC_PROGRESS = 183,

    PJSIP_SC_OK = 200,
    PJSIP_SC_ACCEPTED = 202,

    PJSIP_SC_MULTIPLE_CHOICES = 300,
    PJSIP_SC_MOVED_PERMANENTLY = 301,
    PJSIP_SC_MOVED_TEMPORARILY = 302,
    PJSIP_SC_USE_PROXY = 305,
    PJSIP_SC_ALTERNATIVE_SERVICE = 380,

    PJSIP_SC_BAD_REQUEST = 400,
    PJSIP_SC_UNAUTHORIZED = 401,
    PJSIP_SC_PAYMENT_REQUIRED = 402,
    PJSIP_SC_FORBIDDEN = 403,
    PJSIP_SC_NOT_FOUND = 404,
    PJSIP_SC_METHOD_NOT_ALLOWED = 405,
    PJSIP_SC_NOT_ACCEPTABLE = 406,
    PJSIP_SC_PROXY_AUTHENTICATION_REQUIRED = 407,
    PJSIP_SC_REQUEST_TIMEOUT = 408,
    PJSIP_SC_GONE = 410,
    PJSIP_SC_REQUEST_ENTITY_TOO_LARGE = 413,
    PJSIP_SC_REQUEST_URI_TOO_LONG = 414,
    PJSIP_SC_UNSUPPORTED_MEDIA_TYPE = 415,
    PJSIP_SC_UNSUPPORTED_URI_SCHEME = 416,
    PJSIP_SC_BAD_EXTENSION = 420,
    PJSIP_SC_EXTENSION_REQUIRED = 421,
    PJSIP_SC_SESSION_TIMER_TOO_SMALL = 422,
    PJSIP_SC_INTERVAL_TOO_BRIEF = 423,
    PJSIP_SC_ERROR_ON_SENDING_TO_NEXT_HOP = 477,
    PJSIP_SC_TEMPORARILY_UNAVAILABLE = 480,
    PJSIP_SC_CALL_TSX_DOES_NOT_EXIST = 481,
    PJSIP_SC_LOOP_DETECTED = 482,
    PJSIP_SC_TOO_MANY_HOPS = 483,
    PJSIP_SC_ADDRESS_INCOMPLETE = 484,
    PJSIP_AC_AMBIGUOUS = 485,
    PJSIP_SC_BUSY_HERE = 486,
    PJSIP_SC_REQUEST_TERMINATED = 487,
    PJSIP_SC_NOT_ACCEPTABLE_HERE = 488,
    PJSIP_SC_BAD_EVENT = 489,
    PJSIP_SC_REQUEST_UPDATED = 490,
    PJSIP_SC_REQUEST_PENDING = 491,
    PJSIP_SC_UNDECIPHERABLE = 493,

    PJSIP_SC_INTERNAL_SERVER_ERROR = 500,
    PJSIP_SC_NOT_IMPLEMENTED = 501,
    PJSIP_SC_BAD_GATEWAY = 502,
    PJSIP_SC_SERVICE_UNAVAILABLE = 503,
    PJSIP_SC_SERVER_TIMEOUT = 504,
    PJSIP_SC_VERSION_NOT_SUPPORTED = 505,
    PJSIP_SC_MESSAGE_TOO_LARGE = 513,
    PJSIP_SC_PRECONDITION_FAILURE = 580,

    PJSIP_SC_BUSY_EVERYWHERE = 600,
    PJSIP_SC_DECLINE = 603,
    PJSIP_SC_DOES_NOT_EXIST_ANYWHERE = 604,
    PJSIP_SC_NOT_ACCEPTABLE_ANYWHERE = 606,

    PJSIP_SC_TSX_TIMEOUT = PJSIP_SC_REQUEST_TIMEOUT,
    /*PJSIP_SC_TSX_RESOLVE_ERROR = 702,*/
    PJSIP_SC_TSX_TRANSPORT_ERROR = PJSIP_SC_SERVICE_UNAVAILABLE

};

/** The following functions are not exposed in pjsua.h, but we need them */
// From pjsip/include/pjsua-lib/pjsua.h:1342
PJ_DECL(pj_pool_t*) pjsua_pool_create(const char *name, pj_size_t init_size, pj_size_t increment);
// From pjlib/include/pj/pool.h:390
PJ_DECL(void) pj_pool_release( pj_pool_t *pool );
// From pjmedia/include/pjmedia/tonegen.h:168
PJ_DECL(pj_status_t) pjmedia_tonegen_create2(pj_pool_t *pool,
	const pj_str_t *name,
	unsigned clock_rate,
	unsigned channel_count,
	unsigned samples_per_frame,
	unsigned bits_per_sample,
	unsigned options,
	pjmedia_port **pp_port);
// From pjmedia/include/pjmedia/tonegen.h:225
PJ_DECL(pj_status_t) pjmedia_tonegen_play(pjmedia_port *tonegen,
	unsigned count,
	const pjmedia_tone_desc tones[],
	unsigned options);
PJ_DECL(pj_status_t) pjmedia_tonegen_play_digits(pjmedia_port *tonegen,
	 unsigned count,
	 const pjmedia_tone_digit digits[],
	 unsigned options);
// From pjmedia/include/pjmedia/tonegen.h:206
PJ_DECL(pj_status_t) pjmedia_tonegen_rewind(pjmedia_port *tonegen);
// From pjmedia/include/pjmedia/tonegen.h
PJ_DECL(pj_status_t) pjmedia_tonegen_stop(pjmedia_port *tonegen);
//From pjmedia/include/pjmedia/port.h
PJ_DECL(pj_status_t) pjmedia_port_destroy( pjmedia_port *port );

// From pjmedia/include/pjmedia/transport_srtp.h:109
enum pjmedia_srtp_use
{
    /**
     * When this flag is specified, SRTP will be disabled, and the transport
     * will reject RTP/SAVP offer.
     */
    PJMEDIA_SRTP_DISABLED,

    /**
     * When this flag is specified, SRTP will be advertised as optional and
     * incoming SRTP offer will be accepted.
     */
    PJMEDIA_SRTP_OPTIONAL,

    /**
     * When this flag is specified, the transport will require that RTP/SAVP
     * media shall be used.
     */
    PJMEDIA_SRTP_MANDATORY

};

// From pjsip/include/pjsip/transport_tls.h

/** SSL protocol method constants. */
typedef enum pjsip_ssl_method
{
    PJSIP_SSL_UNSPECIFIED_METHOD = 0,	/**< Default protocol method.	*/
    PJSIP_SSLV2_METHOD		 = 20,	/**< Use SSLv2 method.		*/
    PJSIP_SSLV3_METHOD		 = 30,	/**< Use SSLv3 method.		*/
    PJSIP_TLSV1_METHOD		 = 31,	/**< Use TLSv1 method.		*/
    PJSIP_TLSV1_1_METHOD	 = 32,	/**< Use TLSv1_1 method.	*/
    PJSIP_TLSV1_2_METHOD	 = 33,	/**< Use TLSv1_2 method.	*/
    PJSIP_SSLV23_METHOD		 = 23,	/**< Use SSLv23 method.		*/
} pjsip_ssl_method;

// From pjsip/sources/include/pj/ssl_sock.h
typedef enum pj_ssl_cipher {

    /* Unsupported cipher */
    PJ_TLS_UNKNOWN_CIPHER                       = -1,

    /* NULL */
    PJ_TLS_NULL_WITH_NULL_NULL               	= 0x00000000,

    /* TLS/SSLv3 */
    PJ_TLS_RSA_WITH_NULL_MD5                 	= 0x00000001,
    PJ_TLS_RSA_WITH_NULL_SHA                 	= 0x00000002,
    PJ_TLS_RSA_WITH_NULL_SHA256              	= 0x0000003B,
    PJ_TLS_RSA_WITH_RC4_128_MD5              	= 0x00000004,
    PJ_TLS_RSA_WITH_RC4_128_SHA              	= 0x00000005,
    PJ_TLS_RSA_WITH_3DES_EDE_CBC_SHA         	= 0x0000000A,
    PJ_TLS_RSA_WITH_AES_128_CBC_SHA          	= 0x0000002F,
    PJ_TLS_RSA_WITH_AES_256_CBC_SHA          	= 0x00000035,
    PJ_TLS_RSA_WITH_AES_128_CBC_SHA256       	= 0x0000003C,
    PJ_TLS_RSA_WITH_AES_256_CBC_SHA256       	= 0x0000003D,
    PJ_TLS_DH_DSS_WITH_3DES_EDE_CBC_SHA      	= 0x0000000D,
    PJ_TLS_DH_RSA_WITH_3DES_EDE_CBC_SHA      	= 0x00000010,
    PJ_TLS_DHE_DSS_WITH_3DES_EDE_CBC_SHA     	= 0x00000013,
    PJ_TLS_DHE_RSA_WITH_3DES_EDE_CBC_SHA     	= 0x00000016,
    PJ_TLS_DH_DSS_WITH_AES_128_CBC_SHA       	= 0x00000030,
    PJ_TLS_DH_RSA_WITH_AES_128_CBC_SHA       	= 0x00000031,
    PJ_TLS_DHE_DSS_WITH_AES_128_CBC_SHA      	= 0x00000032,
    PJ_TLS_DHE_RSA_WITH_AES_128_CBC_SHA      	= 0x00000033,
    PJ_TLS_DH_DSS_WITH_AES_256_CBC_SHA       	= 0x00000036,
    PJ_TLS_DH_RSA_WITH_AES_256_CBC_SHA       	= 0x00000037,
    PJ_TLS_DHE_DSS_WITH_AES_256_CBC_SHA      	= 0x00000038,
    PJ_TLS_DHE_RSA_WITH_AES_256_CBC_SHA      	= 0x00000039,
    PJ_TLS_DH_DSS_WITH_AES_128_CBC_SHA256    	= 0x0000003E,
    PJ_TLS_DH_RSA_WITH_AES_128_CBC_SHA256    	= 0x0000003F,
    PJ_TLS_DHE_DSS_WITH_AES_128_CBC_SHA256   	= 0x00000040,
    PJ_TLS_DHE_RSA_WITH_AES_128_CBC_SHA256   	= 0x00000067,
    PJ_TLS_DH_DSS_WITH_AES_256_CBC_SHA256    	= 0x00000068,
    PJ_TLS_DH_RSA_WITH_AES_256_CBC_SHA256    	= 0x00000069,
    PJ_TLS_DHE_DSS_WITH_AES_256_CBC_SHA256   	= 0x0000006A,
    PJ_TLS_DHE_RSA_WITH_AES_256_CBC_SHA256   	= 0x0000006B,
    PJ_TLS_DH_anon_WITH_RC4_128_MD5          	= 0x00000018,
    PJ_TLS_DH_anon_WITH_3DES_EDE_CBC_SHA     	= 0x0000001B,
    PJ_TLS_DH_anon_WITH_AES_128_CBC_SHA      	= 0x00000034,
    PJ_TLS_DH_anon_WITH_AES_256_CBC_SHA      	= 0x0000003A,
    PJ_TLS_DH_anon_WITH_AES_128_CBC_SHA256   	= 0x0000006C,
    PJ_TLS_DH_anon_WITH_AES_256_CBC_SHA256   	= 0x0000006D,

    /* TLS (deprecated) */
    PJ_TLS_RSA_EXPORT_WITH_RC4_40_MD5        	= 0x00000003,
    PJ_TLS_RSA_EXPORT_WITH_RC2_CBC_40_MD5    	= 0x00000006,
    PJ_TLS_RSA_WITH_IDEA_CBC_SHA             	= 0x00000007,
    PJ_TLS_RSA_EXPORT_WITH_DES40_CBC_SHA     	= 0x00000008,
    PJ_TLS_RSA_WITH_DES_CBC_SHA              	= 0x00000009,
    PJ_TLS_DH_DSS_EXPORT_WITH_DES40_CBC_SHA  	= 0x0000000B,
    PJ_TLS_DH_DSS_WITH_DES_CBC_SHA           	= 0x0000000C,
    PJ_TLS_DH_RSA_EXPORT_WITH_DES40_CBC_SHA  	= 0x0000000E,
    PJ_TLS_DH_RSA_WITH_DES_CBC_SHA           	= 0x0000000F,
    PJ_TLS_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA 	= 0x00000011,
    PJ_TLS_DHE_DSS_WITH_DES_CBC_SHA          	= 0x00000012,
    PJ_TLS_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA 	= 0x00000014,
    PJ_TLS_DHE_RSA_WITH_DES_CBC_SHA          	= 0x00000015,
    PJ_TLS_DH_anon_EXPORT_WITH_RC4_40_MD5    	= 0x00000017,
    PJ_TLS_DH_anon_EXPORT_WITH_DES40_CBC_SHA 	= 0x00000019,
    PJ_TLS_DH_anon_WITH_DES_CBC_SHA          	= 0x0000001A,

    /* SSLv3 */
    PJ_SSL_FORTEZZA_KEA_WITH_NULL_SHA        	= 0x0000001C,
    PJ_SSL_FORTEZZA_KEA_WITH_FORTEZZA_CBC_SHA	= 0x0000001D,
    PJ_SSL_FORTEZZA_KEA_WITH_RC4_128_SHA     	= 0x0000001E,

    /* SSLv2 */
    PJ_SSL_CK_RC4_128_WITH_MD5               	= 0x00010080,
    PJ_SSL_CK_RC4_128_EXPORT40_WITH_MD5      	= 0x00020080,
    PJ_SSL_CK_RC2_128_CBC_WITH_MD5           	= 0x00030080,
    PJ_SSL_CK_RC2_128_CBC_EXPORT40_WITH_MD5  	= 0x00040080,
    PJ_SSL_CK_IDEA_128_CBC_WITH_MD5          	= 0x00050080,
    PJ_SSL_CK_DES_64_CBC_WITH_MD5            	= 0x00060040,
    PJ_SSL_CK_DES_192_EDE3_CBC_WITH_MD5      	= 0x000700C0

} pj_ssl_cipher;

// Array of pj_ssl_ciphers
//%include "arrays_java.i"
//%apply pj_ssl_cipher[] {pj_ssl_cipher*};

%include "carrays.i"
%array_functions(pj_ssl_cipher, sslCipherArray);

/**
 * TLS transport settings.
 */
typedef struct pjsip_tls_setting
{
    /**
     * Certificate of Authority (CA) list file.
     */
    pj_str_t	ca_list_file;

    /**
     * Certificate of Authority (CA) list directory path.
     */
    pj_str_t	ca_list_path;

    /**
     * Public endpoint certificate file, which will be used as client-
     * side  certificate for outgoing TLS connection, and server-side
     * certificate for incoming TLS connection.
     */
    pj_str_t	cert_file;

    /**
     * Optional private key of the endpoint certificate to be used.
     */
    pj_str_t	privkey_file;

    /**
     * Password to open private key.
     */
    pj_str_t	password;

    /**
     * TLS protocol method from #pjsip_ssl_method. In the future, this field
     * might be deprecated in favor of <b>proto</b> field. For now, this field
     * is only applicable only when <b>proto</b> field is set to zero.
     *
     * Default is PJSIP_SSL_UNSPECIFIED_METHOD (0), which in turn will
     * use PJSIP_SSL_DEFAULT_METHOD, which default value is PJSIP_TLSV1_METHOD.
     */
    pjsip_ssl_method	method;

    /**
     * TLS protocol type from #pj_ssl_sock_proto. Use this field to enable
     * specific protocol type. Use bitwise OR operation to combine the protocol
     * type.
     *
     * Default is PJSIP_SSL_DEFAULT_PROTO.
     */
    pj_uint32_t	proto;

    /**
     * Number of ciphers contained in the specified cipher preference.
     * If this is set to zero, then default cipher list of the backend
     * will be used.
     *
     * Default: 0 (zero).
     */
    unsigned ciphers_num;

    /**
     * Ciphers and order preference. The #pj_ssl_cipher_get_availables()
     * can be used to check the available ciphers supported by backend.
     */
    pj_ssl_cipher *ciphers;

    /**
     * Specifies TLS transport behavior on the server TLS certificate
     * verification result:
     * - If \a verify_server is disabled (set to PJ_FALSE), TLS transport
     *   will just notify the application via #pjsip_tp_state_callback with
     *   state PJSIP_TP_STATE_CONNECTED regardless TLS verification result.
     * - If \a verify_server is enabled (set to PJ_TRUE), TLS transport
     *   will be shutdown and application will be notified with state
     *   PJSIP_TP_STATE_DISCONNECTED whenever there is any TLS verification
     *   error, otherwise PJSIP_TP_STATE_CONNECTED will be notified.
     *
     * In any cases, application can inspect #pjsip_tls_state_info in the
     * callback to see the verification detail.
     *
     * Default value is PJ_FALSE.
     */
    pj_bool_t	verify_server;

    /**
     * Specifies TLS transport behavior on the client TLS certificate
     * verification result:
     * - If \a verify_client is disabled (set to PJ_FALSE), TLS transport
     *   will just notify the application via #pjsip_tp_state_callback with
     *   state PJSIP_TP_STATE_CONNECTED regardless TLS verification result.
     * - If \a verify_client is enabled (set to PJ_TRUE), TLS transport
     *   will be shutdown and application will be notified with state
     *   PJSIP_TP_STATE_DISCONNECTED whenever there is any TLS verification
     *   error, otherwise PJSIP_TP_STATE_CONNECTED will be notified.
     *
     * In any cases, application can inspect #pjsip_tls_state_info in the
     * callback to see the verification detail.
     *
     * Default value is PJ_FALSE.
     */
    pj_bool_t	verify_client;

    /**
     * When acting as server (incoming TLS connections), reject inocming
     * connection if client doesn't supply a TLS certificate.
     *
     * This setting corresponds to SSL_VERIFY_FAIL_IF_NO_PEER_CERT flag.
     * Default value is PJ_FALSE.
     */
    pj_bool_t	require_client_cert;

    /**
     * TLS negotiation timeout to be applied for both outgoing and
     * incoming connection. If both sec and msec member is set to zero,
     * the SSL negotiation doesn't have a timeout.
     */
    pj_time_val	timeout;

    /**
     * Should SO_REUSEADDR be used for the listener socket.
     * Default value is PJSIP_TLS_TRANSPORT_REUSEADDR.
     */
    pj_bool_t reuse_addr;

    /**
     * QoS traffic type to be set on this transport. When application wants
     * to apply QoS tagging to the transport, it's preferable to set this
     * field rather than \a qos_param fields since this is more portable.
     *
     * Default value is PJ_QOS_TYPE_BEST_EFFORT.
     */
    pj_qos_type qos_type;

    /**
     * Set the low level QoS parameters to the transport. This is a lower
     * level operation than setting the \a qos_type field and may not be
     * supported on all platforms.
     *
     * By default all settings in this structure are disabled.
     */
    pj_qos_params qos_params;

    /**
     * Specify if the transport should ignore any errors when setting the QoS
     * traffic type/parameters.
     *
     * Default: PJ_TRUE
     */
    pj_bool_t qos_ignore_error;

    /**
     * Specify options to be set on the transport.
     *
     * By default there is no options.
     *
     */
    pj_sockopt_params sockopt_params;

    /**
     * Specify if the transport should ignore any errors when setting the
     * sockopt parameters.
     *
     * Default: PJ_TRUE
     *
     */
    pj_bool_t sockopt_ignore_error;

} pjsip_tls_setting;

//pjlib/include/pj/types.h
typedef long		pj_ssize_t;

/* QOS */
/**
 * High level traffic classification.
 */
enum pj_qos_type
{
    PJ_QOS_TYPE_BEST_EFFORT,	/**< Best effort traffic (default value).
				     Any QoS function calls with specifying
				     this value are effectively no-op	*/
    PJ_QOS_TYPE_BACKGROUND,	/**< Background traffic.		*/
    PJ_QOS_TYPE_VIDEO,		/**< Video traffic.			*/
    PJ_QOS_TYPE_VOICE,		/**< Voice traffic.			*/
    PJ_QOS_TYPE_CONTROL		/**< Control traffic.			*/
};

/**
 * QoS parameters to be set or retrieved to/from the socket.
 */
struct pj_qos_params
{
    pj_uint8_t      flags;    /**< Determines which values to 
				   set, bitmask of pj_qos_flag	    */
    pj_uint8_t      dscp_val; /**< The 6 bits DSCP value to set	    */
    pj_uint8_t      so_prio;  /**< SO_PRIORITY value		    */
    pj_qos_wmm_prio wmm_prio; /**< WMM priority value		    */
};

/**
 * Representation of time value in this library.
 * This type can be used to represent either an interval or a specific time
 * or date. 
 */
struct pj_time_val
{
    /** The seconds part of the time. */
    long    sec;

    /** The miliseconds fraction of the time. */
    long    msec;

};



//pjnat/include/nat_detect.h

/**
 * This enumeration describes the NAT types, as specified by RFC 3489
 * Section 5, NAT Variations.
 */
typedef enum pj_stun_nat_type
{
    /**
     * NAT type is unknown because the detection has not been performed.
     */
    PJ_STUN_NAT_TYPE_UNKNOWN,

    /**
     * NAT type is unknown because there is failure in the detection
     * process, possibly because server does not support RFC 3489.
     */
    PJ_STUN_NAT_TYPE_ERR_UNKNOWN,

    /**
     * This specifies that the client has open access to Internet (or
     * at least, its behind a firewall that behaves like a full-cone NAT,
     * but without the translation)
     */
    PJ_STUN_NAT_TYPE_OPEN,

    /**
     * This specifies that communication with server has failed, probably
     * because UDP packets are blocked.
     */
    PJ_STUN_NAT_TYPE_BLOCKED,

    /**
     * Firewall that allows UDP out, and responses have to come back to
     * the source of the request (like a symmetric NAT, but no
     * translation.
     */
    PJ_STUN_NAT_TYPE_SYMMETRIC_UDP,

    /**
     * A full cone NAT is one where all requests from the same internal 
     * IP address and port are mapped to the same external IP address and
     * port.  Furthermore, any external host can send a packet to the 
     * internal host, by sending a packet to the mapped external address.
     */
    PJ_STUN_NAT_TYPE_FULL_CONE,

    /**
     * A symmetric NAT is one where all requests from the same internal 
     * IP address and port, to a specific destination IP address and port,
     * are mapped to the same external IP address and port.  If the same 
     * host sends a packet with the same source address and port, but to 
     * a different destination, a different mapping is used.  Furthermore,
     * only the external host that receives a packet can send a UDP packet
     * back to the internal host.
     */
    PJ_STUN_NAT_TYPE_SYMMETRIC,

    /**
     * A restricted cone NAT is one where all requests from the same 
     * internal IP address and port are mapped to the same external IP 
     * address and port.  Unlike a full cone NAT, an external host (with 
     * IP address X) can send a packet to the internal host only if the 
     * internal host had previously sent a packet to IP address X.
     */
    PJ_STUN_NAT_TYPE_RESTRICTED,

    /**
     * A port restricted cone NAT is like a restricted cone NAT, but the 
     * restriction includes port numbers. Specifically, an external host 
     * can send a packet, with source IP address X and source port P, 
     * to the internal host only if the internal host had previously sent
     * a packet to IP address X and port P.
     */
    PJ_STUN_NAT_TYPE_PORT_RESTRICTED

} pj_stun_nat_type;
/**
 * This structure contains the result of NAT classification function.
 */
struct pj_stun_nat_detect_result
{
    /**
     * Status of the detection process. If this value is not PJ_SUCCESS,
     * the detection has failed and \a nat_type field will contain
     * PJ_STUN_NAT_TYPE_UNKNOWN.
     */
    pj_status_t		 status;

    /**
     * The text describing the status, if the status is not PJ_SUCCESS.
     */
    /*const*/ char		*status_text;

    /**
     * This contains the NAT type as detected by the detection procedure.
     * This value is only valid when the \a status is PJ_SUCCESS.
     */
    pj_stun_nat_type	 nat_type;

    /**
     * Text describing that NAT type.
     */
    /*const*/ char		*nat_type_name;

} ;


// pjsip/include/pjsip/sip_util.h:80
/**
 * These enumerations specify the action to be performed to a redirect
 * response.
 */
typedef enum pjsip_redirect_op
{
    /**
     * Reject the redirection to the current target. The UAC will
     * select the next target from the target set if exists.
     */
    PJSIP_REDIRECT_REJECT,

    /**
     * Accept the redirection to the current target. The INVITE request
     * will be resent to the current target.
     */
    PJSIP_REDIRECT_ACCEPT,

    /**
     * Defer the redirection decision, for example to request permission
     * from the end user.
     */
    PJSIP_REDIRECT_PENDING,

    /**
     * Stop the whole redirection process altogether. This will cause
     * the invite session to be disconnected.
     */
    PJSIP_REDIRECT_STOP

};

// pjsip/include/psip-ua/sip_timer.h

/**
 * This structure describes Session Timers settings in an invite session.
 */
struct pjsip_timer_setting
{
    /** 
     * Specify minimum session expiration period, in seconds. Must not be
     * lower than 90. Default is 90.
     */
    unsigned			 min_se;

    /**
     * Specify session expiration period, in seconds. Must not be lower than
     * #min_se. Default is 1800.
     */
    unsigned			 sess_expires;	

};

// Silent forward decl of msg_data
// Force decl of msg_data so that the real one is used -- this produce a build error but harmless
struct pjsua_msg_data {
    /**
     * Optional remote target URI (i.e. Target header). If NULL, the target
     * will be set to the remote URI (To header). At the moment this field
     * is only used by #pjsua_call_make_call() and #pjsua_im_send().
     */
    pj_str_t    target_uri;

    /**
     * Additional message headers as linked list. Application can add
     * headers to the list by creating the header, either from the heap/pool
     * or from temporary local variable, and add the header using
     * linked list operation. See pjsua_app.c for some sample codes.
     */
    pjsip_hdr	hdr_list;

    /**
     * MIME type of optional message body.
     */
    pj_str_t	content_type;

    /**
     * Optional message body to be added to the message, only when the
     * message doesn't have a body.
     */
    pj_str_t	msg_body;

    /**
     * Content type of the multipart body. If application wants to send
     * multipart message bodies, it puts the parts in \a parts and set
     * the content type in \a multipart_ctype. If the message already
     * contains a body, the body will be added to the multipart bodies.
     */
    pjsip_media_type  multipart_ctype;

    /**
     * List of multipart parts. If application wants to send multipart
     * message bodies, it puts the parts in \a parts and set the content
     * type in \a multipart_ctype. If the message already contains a body,
     * the body will be added to the multipart bodies.
     */
    pjsip_multipart_part multipart_parts;
};

/**
 * This structure describes person information in RPID document.
 */
struct pjrpid_element
{
    /** Element type. */
    pjrpid_element_type	    type;

    /** Optional id to set on the element. */
    pj_str_t		    id;

    /** Activity type. */
    pjrpid_activity	    activity;

    /** Optional text describing the person/element. */
    pj_str_t		    note;

};


/**
 * This enumeration describes subset of standard activities as 
 * described by RFC 4880, RPID: Rich Presence Extensions to the 
 * Presence Information Data Format (PIDF). 
 */
enum pjrpid_activity
{
    /** Activity is unknown. The activity would then be conceived
     *  in the "note" field.
     */
    PJRPID_ACTIVITY_UNKNOWN,

    /** The person is away */
    PJRPID_ACTIVITY_AWAY,

    /** The person is busy */
    PJRPID_ACTIVITY_BUSY

};

/**
 * From sip_auth.h
 * This structure describes client authentication session preference.
 * The preference can be set by calling #pjsip_auth_clt_set_prefs().
 */
struct pjsip_auth_clt_pref
{
    /**
     * If this flag is set, the authentication client framework will
     * send an empty Authorization header in each initial request.
     * Default is no.
     */
    pj_bool_t   initial_auth;

    /**
     * Specify the algorithm to use when empty Authorization header 
     * is to be sent for each initial request (see above)
     */
    pj_str_t    algorithm;

};

// From pjnath/include/pjnath/ice_session.h
/**
 * This enumeration describes the type of an ICE candidate.
 */
typedef enum pj_ice_cand_type
{
    /**
     * ICE host candidate. A host candidate represents the actual local
     * transport address in the host.
     */
    PJ_ICE_CAND_TYPE_HOST,

    /**
     * ICE server reflexive candidate, which represents the public mapped
     * address of the local address, and is obtained by sending STUN
     * Binding request from the host candidate to a STUN server.
     */
    PJ_ICE_CAND_TYPE_SRFLX,

    /**
     * ICE peer reflexive candidate, which is the address as seen by peer
     * agent during connectivity check.
     */
    PJ_ICE_CAND_TYPE_PRFLX,

    /**
     * ICE relayed candidate, which represents the address allocated in
     * TURN server.
     */
    PJ_ICE_CAND_TYPE_RELAYED,

    /**
     * Number of defined ICE candidate types.
     */
    PJ_ICE_CAND_TYPE_MAX

} pj_ice_cand_type;

// From pjnath/include/pjnath/ice_session.h
/**
 * This structure describes various ICE session options. Application
 * configure the ICE session with these options by calling
 * #pj_ice_sess_set_options().
 */
typedef struct pj_ice_sess_options
{
    /**
     * Specify whether to use aggressive nomination.
     */
    pj_bool_t		aggressive;

    /**
     * For controlling agent if it uses regular nomination, specify the delay
     * to perform nominated check (connectivity check with USE-CANDIDATE
     * attribute) after all components have a valid pair.
     *
     * Default value is PJ_ICE_NOMINATED_CHECK_DELAY.
     */
    unsigned		nominated_check_delay;

    /**
     * For a controlled agent, specify how long it wants to wait (in
     * milliseconds) for the controlling agent to complete sending
     * connectivity check with nominated flag set to true for all components
     * after the controlled agent has found that all connectivity checks in
     * its checklist have been completed and there is at least one successful
     * (but not nominated) check for every component.
     *
     * Default value for this option is
     * ICE_CONTROLLED_AGENT_WAIT_NOMINATION_TIMEOUT. Specify -1 to disable
     * this timer.
     */
    int			controlled_agent_want_nom_timeout;

    /**
     * Candidate blacklist map.
     * If candidate with type from pj_ice_cand_type enum has bit 1 << cand set to 1 in this
     * map it must be excluded from consideration in ICE.
     */
    unsigned    cand_blacklist_map;

} pj_ice_sess_options;

// From: pjsip/include/pjsua-lib/pjsua.h
/**
 * ICE setting. This setting is used in the pjsua_acc_config.
 */
typedef struct pjsua_ice_config
{
    /**
     * Enable ICE.
     */
    pj_bool_t		enable_ice;

    /**
     * Set the maximum number of host candidates.
     *
     * Default: -1 (maximum not set)
     */
    int			ice_max_host_cands;

    /**
     * ICE session options.
     */
    pj_ice_sess_options	ice_opt;

    /**
     * Disable RTCP component.
     *
     * Default: no
     */
    pj_bool_t		ice_no_rtcp;

    /**
     * Send re-INVITE/UPDATE every after ICE connectivity check regardless
     * the default ICE transport address is changed or not. When this is set
     * to PJ_FALSE, re-INVITE/UPDATE will be sent only when the default ICE
     * transport address is changed.
     *
     * Default: yes
     */
    pj_bool_t		ice_always_update;

} pjsua_ice_config;


/** Structure to hold parameters when calling application's callback.
 *  The application's callback is called when the client registration process
 *  has finished.
 */
struct pjsip_regc_cbparam
{
    pjsip_regc		*regc;	    /**< Client registration structure.	    */
    void		*token;	    /**< Arbitrary token set by application */

    /** Error status. If this value is non-PJ_SUCCESS, some error has occured.
     *  Note that even when this contains PJ_SUCCESS the registration might
     *  have failed; in this case the \a code field will contain non
     *  successful (non-2xx status class) code
     */
    pj_status_t		 status;
    int			 code;	    /**< SIP status code received.	    */
    pj_str_t		 reason;    /**< SIP reason phrase received.	    */
    pjsip_rx_data	*rdata;	    /**< The complete received response.    */
    int			 expiration;/**< Next expiration interval.	    */
    int			 contact_cnt;/**<Number of contacts in response.    */
    pjsip_contact_hdr	*contact[PJSIP_REGC_MAX_CONTACT]; /**< Contacts.    */
};

/** Typedef for client registration data. */
typedef struct pjsip_regc pjsip_regc;

typedef struct pj_reg_backoff_struct {
    unsigned attempt_cnt;
    pj_time_val delay;
} pj_reg_backoff_struct;

/**
 * Enumeration of transport state types.
 */
typedef enum pjsip_transport_state
{
    PJSIP_TP_STATE_CONNECTED,	    /**< Transport connected, applicable only
					 to connection-oriented transports
					 such as TCP and TLS.		    */
            PJSIP_TP_STATE_DISCONNECTED,    /**< Transport disconnected, applicable
					 only to connection-oriented
					 transports such as TCP and TLS.    */
            PJSIP_TP_STATE_SHUTDOWN,        /**< Transport shutdown, either
                                         due to TCP/TLS disconnect error
                                         from the network, or when shutdown
                                         is initiated by PJSIP itself.      */
            PJSIP_TP_STATE_DESTROY,         /**< Transport destroy, when transport
                                         is about to be destroyed.          */
} pjsip_transport_state;

/**
 * Forward declaration for SIP transport.
 */
typedef struct pjsip_transport pjsip_transport;

/**
 * Structure of transport state info passed by #pjsip_tp_state_callback.
 */
typedef struct pjsip_transport_state_info {
    /**
     * The last error code related to the transport state.
     */
    pj_status_t		 status;

    /**
     * Optional extended info, the content is specific for each transport type.
     */
    void		*ext_info;

    /**
     * Optional user data. In global transport state notification, this will
     * always be NULL.
     */
    void		*user_data;

} pjsip_transport_state_info;

/**
 * This structure represent the "public" interface of a SIP transport.
 * Applications normally extend this structure to include transport
 * specific members.
 */
struct pjsip_transport
{
    char		    obj_name[PJ_MAX_OBJ_NAME];	/**< Name. */

    pj_pool_t		   *pool;	    /**< Pool used by transport.    */
    pj_atomic_t		   *ref_cnt;	    /**< Reference counter.	    */
    pj_lock_t		   *lock;	    /**< Lock object.		    */
    pj_bool_t		    tracing;	    /**< Tracing enabled?	    */
    pj_bool_t		    is_shutdown;    /**< Being shutdown?	    */
    pj_bool_t		    is_destroying;  /**< Destroy in progress?	    */

    /** Key for indexing this transport in hash table. */
    pjsip_transport_key	    key;

    char		   *type_name;	    /**< Type name.		    */
    unsigned		    flag;	    /**< #pjsip_transport_flags_e   */
    char		   *info;	    /**< Transport info/description.*/

    int			    addr_len;	    /**< Length of addresses.	    */
    pj_sockaddr		    local_addr;	    /**< Bound address.		    */
    pjsip_host_port	    local_name;	    /**< Published name (eg. STUN). */
    pjsip_host_port	    remote_name;    /**< Remote address name.	    */
    pjsip_transport_dir	    dir;	    /**< Connection direction.	    */

    pjsip_endpoint	   *endpt;	    /**< Endpoint instance.	    */
    pjsip_tpmgr		   *tpmgr;	    /**< Transport manager.	    */
    pj_timer_entry	    idle_timer;	    /**< Timer when ref cnt is zero.*/

    pj_timestamp	    last_recv_ts;   /**< Last time receiving data.  */
    pj_size_t		    last_recv_len;  /**< Last received data length. */

    void		   *data;	    /**< Internal transport data.   */
};


// From: pjmedia/include/pjmedia/event.h
/** Additional parameters for underflow event */
typedef struct pjmedia_event_underflow_data
{
    double underflow_ratio;
    int underflow_status;
    int conf_port_idx;
} pjmedia_event_underflow_data;

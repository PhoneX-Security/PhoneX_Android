/*
 * pjsip_mobile_reg_handler.h
 *
 *  Created on: 1 déc. 2012
 *      Author: r3gis3r
 */

#ifndef PJSIP_SIPSIGN_H_
#define PJSIP_SIPSIGN_H_

#include <pj/config_site.h>
#include <pjsua-lib/pjsua.h>

extern "C" {
	#define EESIGN_HDR "EE-Sign"
	#define EESIGN_DESC_HDR "EE-Sign-Desc"
    #define EESIGN_FLAG_DROP_PACKET 1024
	
	typedef struct hashReturn_t_ {
		pj_status_t retStatus;
		int         errCode;
		pj_str_t    hash;
		pj_str_t    desc;
	} hashReturn_t;
	
	// all data required to compute signatures
	typedef struct esignInfo_t_{
		pj_bool_t isRequest;
		
		int cseqInt;
		pj_str_t method;
		int resp_status;
		
	    pj_str_t cseqStr;
	    pj_str_t reqUriStr;
	    pj_str_t fromUriStr;
	    pj_str_t toUriStr;
	    pj_str_t bodyStr;
	    pj_str_t bodySha256Str;
	    pj_str_t accumStr;
	    pj_str_t accumSha256Str;
	    pj_str_t ip;
	} esignInfo_t;

	/**
	 * This enumeration represents packet processing result;
	 */
	typedef enum esign_process_state_e_
	{
	    ESIGN_PROCESS_STATE_NULL=0,	/**< Module didn't process this message.   */
	    ESIGN_PROCESS_STATE_ERROR,	/**< Some critical error during processing.   */
	    ESIGN_PROCESS_STATE_PROCESSED/**< Packet was processed .*/
	} esign_process_state_e;


	enum esign_sign_err_e
	{
		ESIGN_SIGN_ERR_SUCCESS = 0,	             /**< No error.   */
		ESIGN_SIGN_ERR_GENERIC,                  /**< Generic, unspecified error. */
		ESIGN_SIGN_ERR_VERSION_UNKNOWN,          /**< Unknown version in signature descriptor. */
		ESIGN_SIGN_ERR_LOCAL_USER_UNKNOWN,       /**< Local user not known (destination). */
		ESIGN_SIGN_ERR_REMOTE_USER_UNKNOWN,      /**< Remote user not found (source) */
		ESIGN_SIGN_ERR_REMOTE_USER_CERT_MISSING, /**< No certificate for remote user. */
		ESIGN_SIGN_ERR_REMOTE_USER_CERT_ERR,     /**< Certificate error for remote user. */
		ESIGN_SIGN_ERR_SIGNATURE_INVALID,        /**< Signature verification failed. */
		ESIGN_SIGN_ERR_MAX                       /**< Opaque value, maximum value (for int). */
	};

	typedef struct esign_process_info
	{
		/*
		 * State
		 */
		esign_process_state_e process_state;    /**< State of processing of this packet */
		pj_bool_t             signature_present;/**< Is signature present in this packet ? */
		pj_int16_t            callback_return;  /**<Value returned by callback checking signature */
		esign_sign_err_e      verify_err;
		pj_bool_t             signature_valid;
		pj_bool_t             packet_dropped;

		/*
		 * Information for high level application
		 */
		pj_bool_t              is_request;
		pj_str_t               method;
		pj_int32_t             cseq_int;
		pj_str_t               cseq_str;
		pj_str_t               req_uri_str;
		pj_str_t               from_uri_str;
		pj_str_t               to_uri_str;
		pj_str_t               body_sha_256_str;
		pj_str_t               accum_sha_256_str;

		/*
		 * State and status.
		 */
		int				       status_code;    /**< Last status code seen. */
		//pj_str_t			   status_text;	   /**< Last reason phrase.    */

		/*
		 * Signature info
		 */
		pj_str_t               sign;
		pj_str_t               sign_desc;
	} esign_process_info;

	/**
	 * This structure describes SIP signature verification. Inspiration taken from
	 * sip_transaction.h structure
	 */
	typedef struct esign_descriptor
	{
	    /*
	     * Administrivia
	     */
	    pj_pool_t              *pool;           /**< Pool owned by the descriptor. Points to the RX data pool */
	    pjsip_module           *mod;	        /**< Transaction user.	    */
	    pjsip_endpoint         *endpt;          /**< Endpoint instance.     */

	    esign_process_info      sign_info;      /**< Signature verification info */

	    /** Module specific data. */
	    void		       *mod_data[PJSIP_MAX_MODULE];
	} esign_descriptor;
}


class SignCallback {
public:
    virtual ~SignCallback() {}
    virtual pj_status_t sign(const esignInfo_t * sdata, hashReturn_t * hash) {}
    virtual int verifySign(  const esignInfo_t * sdata, const char * sign, const char * desc) {}
};


extern "C" {
	
/** EESign header. */
typedef pjsip_generic_string_hdr pjsip_eesign_hdr;

/** Create EESign header. */
#define pjsip_eesign_hdr_create pjsip_generic_string_hdr_create
	
pj_status_t mod_sign_init();
void mod_sign_set_callback(SignCallback* callback);

/**
 * Get the signature infro in the incoming message. If the message
 * has a corresponding signature data, this function will return non NULL
 * value.
 *
 * @param rdata	    The incoming message buffer.
 *
 * @return	    The signature info associated with this message,
 *		    or NULL if the message doesn't have any signature info
 */
PJ_DECL(int) pjsip_rdata_get_signature( pjsip_rx_data * rdata, esign_process_info * ret );

}


#endif /* PJSIP_SIPSIGN_H_ */

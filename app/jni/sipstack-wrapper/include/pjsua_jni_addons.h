#ifndef __PJSUA_JNI_ADDONS_H__
#define __PJSUA_JNI_ADDONS_H__

#include <pj/config_site.h>
#include <pjsua-lib/pjsua.h>
#include <pjsua-lib/pjsua_internal.h>
#include <jni.h>

#define PJSIP_SC_GSM_BUSY 499
#define PEX_HEADER_BYE_TERMINATION "X-ByeCause"
#define PEX_HEADER_MESSAGE_TYPE "X-MsgType"
PJ_BEGIN_DECL

// css config
typedef struct dynamic_factory {
	/**
	 * Path to the shared library
	 */
	pj_str_t shared_lib_path;

	/**
	 * Name of the factory function to launch to init the codec
	 */
	pj_str_t init_factory_name;
} dynamic_factory;

typedef struct sipstack_config {
    /**
     * Use compact form for sdp
     */
	pj_bool_t use_compact_form_sdp;

	/**
	* Use compact form for header
	*/
	pj_bool_t use_compact_form_headers;

	/**
	 * Disable SDP bandwidth modifier "TIAS"
     * (RFC3890)
	 */
	pj_bool_t add_bandwidth_tias_in_sdp;

	/**
	 * For to send no update and use re-invite instead
	 */
	pj_bool_t use_no_update;

	/**
	 * Use ZRTP
	 */
	pj_bool_t use_zrtp;

	/**
	 * Number of dynamically loaded codecs
	 */
	unsigned extra_aud_codecs_cnt;

	/**
	 * Codecs to be dynamically loaded
	 */
	dynamic_factory extra_aud_codecs[64];

	/**
	 * Number of dynamically loaded codecs
	 */
	unsigned extra_vid_codecs_cnt;

	/**
	 * Codecs to be dynamically loaded
	 */
	dynamic_factory extra_vid_codecs[64];

	dynamic_factory extra_vid_codecs_destroy[64];

	dynamic_factory vid_converter;

	/**
	 * Target folder for content storage
	 */
	pj_str_t storage_folder;

	/**
	 * Audio dev implementation if empty string fallback to default
	 */
	dynamic_factory audio_implementation;

	/**
	 * Video renderer dev implementation if empty no video feature
	 */
	dynamic_factory video_render_implementation;
	/**
	 * Video capture dev implementation if empty no video feature
	 */
	dynamic_factory video_capture_implementation;

	/**
	 * Interval for tcp keep alive
	 */
	int tcp_keep_alive_interval;

	/**
	 * Interval for tls keep alive
	 */
	int tls_keep_alive_interval;

	/**
	 * Transaction T1 Timeout
	 */
	int tsx_t1_timeout;

	/**
	 * Transaction T2 Timeout
	 */
	int tsx_t2_timeout;

	/**
	 * Transaction T4 Timeout
	 */
	int tsx_t4_timeout;
	/**
	 * Transaction TD Timeout
	 */
	int tsx_td_timeout;

	/**
	 * Disable automatic switching from UDP to TCP if outgoing request
	 * is greater than 1300 bytes. See PJSIP_DONT_SWITCH_TO_TCP.
	 */
	pj_bool_t disable_tcp_switch;

	/**
     * Disable rport in request.
     *
     * Default is PJ_FALSE.
     */
    pj_bool_t disable_rport;

	/**
	 * Enable or not noise suppressor.
	 * Only has impact if using webRTC echo canceller as backend.
	 * Disabled by default
	 */
	pj_bool_t use_noise_suppressor;

} sipstack_config;

typedef struct sipstack_acc_config {

	/**
	 * Use ZRTP
	 */
	int use_zrtp;

	/**
	 * P-Preferred-Identity
	 */
	pj_str_t p_preferred_identity;

} sipstack_acc_config;


// methods
PJ_DECL(pj_status_t) send_dtmf_info(int current_call, pj_str_t digits);
PJ_DECL(pj_str_t) call_dump(pjsua_call_id call_id, pj_bool_t with_media, const char *indent);
PJ_DECL(pj_str_t) call_secure_media_info(pjsua_call_id call_id);
PJ_DECL(int)  call_secure_sig_level(pjsua_call_id call_id);
PJ_DECL(pj_str_t) get_error_message(int status);
PJ_DECL(int) get_event_status_code(pjsip_event *e);
PJ_DECL(pj_status_t) pjsua_call_hangup_ex(pjsua_call_id call_id, unsigned code, const pj_str_t *reason);
PJ_DECL(pj_str_t) get_call_hangup_cause(pjsip_event * e);
PJ_DECL(int) get_call_hangup_cause_int(pjsip_event * e);
PJ_DECL(pj_str_t) search_for_header(pj_str_t *hdr, const pjsip_msg * msg);
PJ_DECL(pj_str_t) get_callid_from_msg(const pjsip_msg * msg);
PJ_DECL(pj_str_t) get_callid_from_evt(pjsip_event * e);
PJ_DECL(pjsip_msg *) get_msg_from_evt(pjsip_event *e);

PJ_DECL(void) sipstack_config_default(sipstack_config *css_cfg);
PJ_DECL(void) sipstack_acc_config_default(sipstack_acc_config* css_acc_cfg);

PJ_DECL(pj_status_t) sipstack_init(pjsua_config *ua_cfg,
				pjsua_logging_config *log_cfg,
				pjsua_media_config *media_cfg,
				sipstack_config *css_cfg,
				jobject context);
PJ_DECL(pj_status_t) sipstack_destroy(unsigned flags);
PJ_DECL(pj_status_t) sipstack_set_acc_user_data(pjsua_acc_config* acc_cfg, sipstack_acc_config* css_acc_cfg);
PJ_DECL(pj_status_t) sipstack_init_acc_msg_data(pj_pool_t* pool, pjsua_acc_id acc_id, pjsua_msg_data* msg_data);
PJ_DECL(pj_status_t) sipstack_msg_data_add_string_hdr(pj_pool_t* pool, pjsua_msg_data* msg_data, pj_str_t* hdr_name, pj_str_t* hdr_value);
PJ_DECL(pj_status_t) pj_timer_fire(int entry_id);
PJ_DECL(pj_status_t) update_transport(const pj_str_t *new_ip_addr);
PJ_DECL(pj_status_t) vid_set_android_renderer(pjsua_call_id call_id, jobject window);
PJ_DECL(pj_status_t) vid_set_android_capturer(jobject window);
PJ_DECL(pj_status_t) set_turn_credentials(const pj_str_t username, const pj_str_t password, const pj_str_t realm, pj_stun_auth_cred *turn_auth_cred);
PJ_DECL(pj_str_t)    get_rx_data_header(const pj_str_t name, pjsip_rx_data* data);
PJ_DECL(pjsip_transport *) get_transport(pjsua_reg_info* info);
PJ_DECL(long) get_transport_ptr(pjsip_transport *transport);
PJ_DECL(int) call_get_state(pjsua_call_id call_id);
PJ_DECL(pj_status_t) play_zrtp_ok_sound();
PJ_DECL(int) sipstack_is_underflow_event(pjmedia_event * event);
PJ_DECL(pjmedia_event_underflow_data) sipstack_get_underflow_data(pjmedia_event * event);

// App callback
PJ_DECL(void) css_on_call_state(pjsua_call_id call_id, pjsip_event *e);
PJ_DECL(void) css_on_call_media_state(pjsua_call_id call_id);
PJ_DECL(pj_status_t) pjsua_acc_set_custom_online_status(pjsua_acc_id acc_id, pj_bool_t is_online, const char * statusText);
PJ_DECL(pj_bool_t) pjsua_is_thread_registered();
PJ_DECL(pj_status_t) pjsua_register_thread(int processId, int threadId);
PJ_DECL(pj_status_t) pjsua_register_thread2(const char * thread_name);

PJ_DECL(void) pjsua_kill();
PJ_DECL(void) pjsua_assert();

PJ_DECL(jstring) conv_pj_str_t_to_jstring(const pj_str_t *pj_string);

// Crypto stuff
PJ_DECL(int) openssl_DH_genPrime(unsigned prime_len, char *buffer, unsigned maxlen);
PJ_DECL(int) openssl_DH_check(const char *buffer);

//PJ_DECL(int) crypto_pbkdf2_hmac_sha256(const char *pass, const char salt[], size_t saltLen, int iter, char out[], size_t keyLen);
//PJ_DECL(int) crypto_pbkdf2_hmac_sha1(const char *pass, const char salt[], size_t saltLen, int iter, char out[], size_t keyLen);

PJ_END_DECL

#endif

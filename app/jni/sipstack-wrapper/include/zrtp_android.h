#ifndef __ZRTP_ANDROID_H__
#define __ZRTP_ANDROID_H__


#include <pjsua-lib/pjsua.h>
/**
 * ZRTP stuff
 */
#ifdef __cplusplus
extern "C" {
#endif

PJ_BEGIN_DECL

#define HCODE_MESSAGE 1
#define HCODE_NOT_SUPPORTED 2

pjmedia_transport* on_zrtp_transport_created(pjsua_call_id call_id,
	unsigned media_idx,
	pjmedia_transport *base_tp,
	unsigned flags);

typedef struct zrtp_state_info {
	pjsua_call_id call_id;
	pj_bool_t secure;
	pj_str_t sas;
	pj_str_t cipher;
	pj_bool_t sas_verified;
	int zrtp_hash_match;
} zrtp_state_info;

PJ_DECL(void) jzrtp_SASVerified(pjsua_call_id call_id);
PJ_DECL(void) jzrtp_SASRevoked(pjsua_call_id call_id);
PJ_DECL(zrtp_state_info) jzrtp_getInfoFromCall(pjsua_call_id call_id);
PJ_DECL(void) jzrtp_addEntropy(const char entropyBuffer[], size_t entropyBufferLen);
PJ_DECL(int) jzrtp_zrtp_loadlib(pj_str_t glueLibPath, pj_str_t zrtpLibPath);
PJ_DECL(int) jzrtp_zrtp_unloadlib();

/**
 * Uses singleton static buffer, not pool so memory don't get polluted by
 * log data. Thus this call is not thread safe, has to be called
 * in a serial way for each call separately.
 */
PJ_DECL(pj_str_t) jzrtp_zrtp_call_dump(pjsua_call_id call_id, const char *indent);
PJ_END_DECL

zrtp_state_info jzrtp_getInfoFromTransport(pjmedia_transport* tp);

#ifdef __cplusplus
}
#endif

#endif

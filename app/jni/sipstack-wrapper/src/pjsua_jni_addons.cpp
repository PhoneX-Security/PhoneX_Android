/**
 * Copyright (C) 2010 Regis Montoya (aka r3gis - www.r3gis.fr)
 * This file is part of pjsip_android.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "pjsua_jni_addons.h"
#include "android_logger.h"
#include "sipstack_internal.h"
#include "PjToneRingback.h"
#include "PjToneBusy.h"
#include "PjToneError.h"
#include "PjToneZRTPOK.h"
#pragma GCC diagnostic ignored "-Wwrite-strings"

#if defined(PJMEDIA_HAS_ZRTP) && PJMEDIA_HAS_ZRTP!=0
#include "zrtp_android.h"
#include "transport_zrtp.h"
#endif

#include "android_dev.h"
#if PJMEDIA_HAS_VIDEO
#include "webrtc_android_render_dev.h"
#endif

#include <dlfcn.h>
#include "pj_loader.h"

#include "timer_android.h"
#include <pthread.h>

#include <signal.h>
#include <unistd.h>

#include <openssl/dh.h>

#define THIS_FILE		"pjsua_jni_addons.cpp"

/* CSS application instance. */
struct css_data css_var;
static char errmsg[PJ_ERR_MSG_SIZE];

// External value
#define DEFAULT_TCP_KA 180
#define DEFAULT_TLS_KA 180

int css_tcp_keep_alive_interval = DEFAULT_TCP_KA;
int css_tls_keep_alive_interval = DEFAULT_TLS_KA;

// Linked list of pj_thread_desc
typedef struct list_el {
    pj_thread_desc *thread_desc;
    pj_thread_t *thread_ref;
    struct list_el *next;
} pj_thread_desc_list_el;

/* Linked list for storing pj_thread_desc objects */
typedef struct pj_thread_storage_t {
    pj_thread_desc_list_el *first;   // Pointer to the first element.
    pj_thread_desc_list_el *last;    // Pointer to last list element.
    size_t count;                    // Number of elements in list - for printing.
    pthread_mutex_t mutex;           // Mutex for list manipulation.

} pj_thread_storage_t;
static pj_thread_storage_t pj_thread_storage = {NULL, NULL, 0, PTHREAD_MUTEX_INITIALIZER};

static void pj_thread_storage_reset(){
    pthread_mutex_lock(&(pj_thread_storage.mutex));
    pj_thread_storage.first = NULL;
    pj_thread_storage.last = NULL;
    pj_thread_storage.count = 0;
    pthread_mutex_unlock(&(pj_thread_storage.mutex));
}

PJ_DECL(void) dealloc_pj_thread_desc_list();
PJ_DECL(void) dump_pj_thread_desc_list();

/**
 * Get call infos
 */
 PJ_DECL(pj_str_t) call_dump(pjsua_call_id call_id, pj_bool_t with_media,
		const char *indent) {
	char some_buf[1024 * 3];
	pj_status_t status = pjsua_call_dump(call_id, with_media, some_buf, sizeof(some_buf), indent);
	if(status != PJ_SUCCESS){
		return pj_strerror(status, some_buf, sizeof(some_buf));
	}
	return pj_str(some_buf);
}

/**
 * Send dtmf with info method
 */
 PJ_DECL(pj_status_t) send_dtmf_info(int current_call, pj_str_t digits) {
	/* Send DTMF with INFO */
	if (current_call == -1) {
		PJ_LOG(3, (THIS_FILE, "No current call"));
		return PJ_EINVAL;
	} else {
		const pj_str_t SIP_INFO = pj_str((char *) "INFO");
		int call = current_call;
		int i;
		pj_status_t status = PJ_EINVAL;
		pjsua_msg_data msg_data;
		PJ_LOG(4, (THIS_FILE, "SEND DTMF : %.*s", digits.slen, digits.ptr));

		for (i = 0; i < digits.slen; ++i) {
			char body[80];

			pjsua_msg_data_init(&msg_data);
			msg_data.content_type = pj_str((char *) "application/dtmf-relay");

			pj_ansi_snprintf(body, sizeof(body), "Signal=%c\r\n"
					"Duration=160", digits.ptr[i]);
			msg_data.msg_body = pj_str(body);
			PJ_LOG(
					4,
					(THIS_FILE, "Send %.*s", msg_data.msg_body.slen, msg_data.msg_body.ptr));

			status = pjsua_call_send_request(current_call, &SIP_INFO,
					&msg_data);
			if (status != PJ_SUCCESS) {
				PJ_LOG(2, (THIS_FILE, "Failed %d", status));
				break;
			}
		}
		return status;
	}
}

/**
 * Is call using a secure RTP method (SRTP/ZRTP)
 */
PJ_DECL(pj_str_t) call_secure_media_info(pjsua_call_id call_id) {

	pjsua_call *call;
	pj_status_t status;
	unsigned i;
	pjmedia_transport_info tp_info;

	pj_str_t result = pj_str("");

	PJ_ASSERT_RETURN(call_id>=0 && call_id<(int)pjsua_var.ua_cfg.max_calls,
			result);

	PJSUA_LOCK();

	if (pjsua_call_has_media(call_id)) {
		call = &pjsua_var.calls[call_id];
		for (i = 0; i < call->med_cnt; ++i) {
			pjsua_call_media *call_med = &call->media[i];
			PJ_LOG(4, (THIS_FILE, "Get secure for media type %d", call_med->type));
			if (call_med->tp && call_med->type == PJMEDIA_TYPE_AUDIO) {
				pjmedia_transport_info tp_info;

				pjmedia_transport_info_init(&tp_info);
				pjmedia_transport_get_info(call_med->tp, &tp_info);
				if (tp_info.specific_info_cnt > 0) {
					unsigned j;
					for (j = 0; j < tp_info.specific_info_cnt; ++j) {
						if (tp_info.spc_info[j].type
								== PJMEDIA_TRANSPORT_TYPE_SRTP) {
							pjmedia_srtp_info *srtp_info =
									(pjmedia_srtp_info*) tp_info.spc_info[j].buffer;
							if (srtp_info->active) {
								result = pj_str("SRTP");
								break;
							}
						}

#if defined(PJMEDIA_HAS_ZRTP) && PJMEDIA_HAS_ZRTP!=0
						else if (tp_info.spc_info[j].type
								== PJMEDIA_TRANSPORT_TYPE_ZRTP) {
							zrtp_state_info info = jzrtp_getInfoFromTransport(call_med->tp);
							if(info.secure){
								char msg[512];
								char zrtphash[64];
								PJ_LOG(4, (THIS_FILE, "ZRTP :: V %d", info.sas_verified));
								PJ_LOG(4, (THIS_FILE, "ZRTP :: S L %d", info.sas.slen));
								PJ_LOG(4, (THIS_FILE, "ZRTP :: C L %d", info.cipher.slen));
								PJ_LOG(4, (THIS_FILE, "ZRTP :: hashMatch %d", info.zrtp_hash_match));

								if (info.zrtp_hash_match == 1){
									pj_ansi_snprintf(zrtphash, sizeof(zrtphash), "OK");
								} else {
									pj_ansi_snprintf(zrtphash, sizeof(zrtphash), "Error %d", info.zrtp_hash_match);
								}

								pj_ansi_snprintf(msg, sizeof(msg), "ZRTP - %s\n%.*s\n%.*s\nzrtp-hash: %s",
										info.sas_verified ? "Verified" : "Not verified",
										(int) info.sas.slen, info.sas.ptr,
										(int) info.cipher.slen, info.cipher.ptr,
										zrtphash);

								pj_strdup2_with_null(css_var.pool, &result, msg);
								break;
							}
						}
#endif
					}
				}
			}
		}
	}

	PJSUA_UNLOCK();

	return result;
}

/**
 * Is call using a secure transport method (No (0) / TLS (1) / SIPS (2))
 */
PJ_DECL(int) call_secure_sig_level(pjsua_call_id call_id) {
    int value = 0;
    pjsua_call *call;
    PJ_ASSERT_RETURN(call_id>=0 && call_id<(int)pjsua_var.ua_cfg.max_calls,
            value);

    PJSUA_LOCK();
    call = &pjsua_var.calls[call_id];
    value = call->secure_level;
    PJSUA_UNLOCK();

    return value;
}


// ZRTP and other media dispatcher
pjmedia_transport* on_transport_created_wrapper(pjsua_call_id call_id,
	unsigned media_idx,
	pjmedia_transport *base_tp,
	unsigned flags) {
	pj_status_t status = PJ_SUCCESS;
	pjsua_call_info call_info;
	void* acc_user_data;
	int acc_use_zrtp = -1;

	// By default, use default global def
	pj_bool_t use_zrtp = css_var.default_use_zrtp;
    status = pjsua_call_get_info(call_id, &call_info);
	if(status == PJ_SUCCESS && pjsua_acc_is_valid (call_info.acc_id)){
		acc_user_data = pjsua_acc_get_user_data(call_info.acc_id);
		if(acc_user_data != NULL){
			acc_use_zrtp = ((sipstack_acc_config *) acc_user_data)->use_zrtp;
			if(acc_use_zrtp >= 0){
				use_zrtp = (acc_use_zrtp == 1) ? PJ_TRUE : PJ_FALSE;
			}
		}
	}
#if defined(PJMEDIA_HAS_ZRTP) && PJMEDIA_HAS_ZRTP!=0
	if(use_zrtp){
		PJ_LOG(4, (THIS_FILE, "Dispatch transport creation on ZRTP one"));
		return on_zrtp_transport_created(call_id, media_idx, base_tp, flags);
	}
#endif


	return base_tp;
}


// ---- VIDEO STUFF ---- //
pj_status_t vid_set_stream_window(pjsua_call_media* call_med, pjmedia_dir dir, void* window){
	pj_status_t status = PJ_ENOTFOUND;
	pjsua_vid_win *w = NULL;
	pjsua_vid_win_id wid;
	pjmedia_vid_dev_stream *dev;


	// We are looking for a rendering video dev
	if (call_med->type == PJMEDIA_TYPE_VIDEO
			&& (call_med->dir & dir)) {

		const char* dirName = (dir == PJMEDIA_DIR_RENDER) ? "render" : "capture";
		PJ_LOG(4, (THIS_FILE, "Has video %s media...", dirName));

		wid = (dir == PJMEDIA_DIR_RENDER) ? call_med->strm.v.rdr_win_id : call_med->strm.v.cap_win_id;
		w = &pjsua_var.win[wid];
		// Make sure we have a render dev
		if (w) {
			dev = pjmedia_vid_port_get_stream( (dir == PJMEDIA_DIR_RENDER) ? w->vp_rend : w->vp_cap);
			if (dev) {
				status = pjmedia_vid_dev_stream_set_cap(dev,
						PJMEDIA_VID_DEV_CAP_OUTPUT_WINDOW,
						(void*) window);
				PJ_LOG(4, (THIS_FILE, "Set %s window >> %x - %x", dirName, dev, window));
			}
		}
	}

	return status;
}

PJ_DECL(pj_status_t) vid_set_android_renderer(pjsua_call_id call_id, jobject window) {
	pj_status_t status = PJ_ENOTFOUND;
	pjsua_call *call;
	int i;

	if( !(call_id>=0 && call_id<(int)pjsua_var.ua_cfg.max_calls) ){
			return PJ_ENOTFOUND;
	}

	PJ_LOG(4, (THIS_FILE, "Setup android renderer for call %d", call_id));
	PJSUA_LOCK();
	// Retrieve the stream
	if (pjsua_call_has_media(call_id)) {
		call = &pjsua_var.calls[call_id];
		for (i = 0; i < call->med_cnt; ++i) {
			pjsua_call_media *call_med = &call->media[i];
			vid_set_stream_window(call_med, PJMEDIA_DIR_RENDER, window);
			status = PJ_SUCCESS;
		}
	}

	PJSUA_UNLOCK();
	return status;
}

PJ_DECL(pj_status_t) vid_set_android_capturer(jobject window) {
    unsigned ci, i, count;
    pj_status_t status = PJ_ENOTFOUND;
    pjsua_call *call;
    pjsua_call_id calls_id[PJSUA_MAX_ACC];

    count = PJ_ARRAY_SIZE(calls_id);
    status = pjsua_enum_calls(calls_id, &count);
    if(status != PJ_SUCCESS){
        return status;
    }

    PJ_LOG(4, (THIS_FILE, "Setup android capturer for all calls"));
    PJSUA_LOCK();
    for(ci = 0; ci < count; ++ci){
        pjsua_call_id call_id = calls_id[ci];
        if(pjsua_call_is_active(call_id) && pjsua_call_has_media(call_id)){
            call = &pjsua_var.calls[call_id];
            for (i = 0; i < call->med_cnt; ++i) {
                pjsua_call_media *call_med = &call->media[i];
                vid_set_stream_window(call_med, PJMEDIA_DIR_CAPTURE, window);
                status = PJ_SUCCESS;
            }
        }
    }

    PJSUA_UNLOCK();
    return status;
}

PJ_DECL(pj_status_t) set_turn_credentials(const pj_str_t username, const pj_str_t password, const pj_str_t realm, pj_stun_auth_cred *turn_auth_cred) {

	PJ_ASSERT_RETURN(turn_auth_cred, PJ_EINVAL);

	/* Create memory pool for application. */
	if(css_var.pool == NULL){
		css_var.pool = pjsua_pool_create("css", 1000, 1000);
		PJ_ASSERT_RETURN(css_var.pool, PJ_ENOMEM);
	}

	if (username.slen) {
		turn_auth_cred->type = PJ_STUN_AUTH_CRED_STATIC;
		pj_strdup_with_null(css_var.pool,
					&turn_auth_cred->data.static_cred.username,
					&username);
	} else {
		turn_auth_cred->data.static_cred.username.slen = 0;
	}

	if(password.slen) {
		turn_auth_cred->data.static_cred.data_type = PJ_STUN_PASSWD_PLAIN;
		pj_strdup_with_null(css_var.pool,
				&turn_auth_cred->data.static_cred.data,
				&password);
	}else{
		turn_auth_cred->data.static_cred.data.slen = 0;
	}

	if(realm.slen) {
        pj_strdup_with_null(css_var.pool,
                    &turn_auth_cred->data.static_cred.realm,
                    &realm);
	} else {
		turn_auth_cred->data.static_cred.realm = pj_str("*");
	}

	return PJ_SUCCESS;
}

//Get error message
PJ_DECL(pj_str_t) get_error_message(int status) {
	return pj_strerror(status, errmsg, sizeof(errmsg));
}

PJ_DECL(void) sipstack_config_default(sipstack_config *css_cfg) {
	css_cfg->use_compact_form_sdp = PJ_FALSE;
	css_cfg->use_compact_form_headers = PJ_FALSE;
	css_cfg->add_bandwidth_tias_in_sdp = PJ_FALSE;
	css_cfg->use_no_update = PJ_FALSE;
	css_cfg->use_zrtp = PJ_FALSE;
	css_cfg->extra_aud_codecs_cnt = 0;
	css_cfg->extra_vid_codecs_cnt = 0;
	css_cfg->audio_implementation.init_factory_name = pj_str("");
	css_cfg->audio_implementation.shared_lib_path = pj_str("");
	css_cfg->tcp_keep_alive_interval = DEFAULT_TCP_KA;
	css_cfg->tls_keep_alive_interval = DEFAULT_TLS_KA;
	css_cfg->tsx_t1_timeout = PJSIP_T1_TIMEOUT;
	css_cfg->tsx_t2_timeout = PJSIP_T2_TIMEOUT;
	css_cfg->tsx_t4_timeout = PJSIP_T4_TIMEOUT;
	css_cfg->tsx_td_timeout = PJSIP_TD_TIMEOUT;
	css_cfg->disable_tcp_switch = PJ_TRUE;
    css_cfg->disable_rport = PJ_FALSE;
	css_cfg->use_noise_suppressor = PJ_FALSE;
}

PJ_DECL(void*) get_library_factory(dynamic_factory *impl) {
	char lib_path[512];
	char init_name[512];
	FILE* file;
	pj_ansi_snprintf(lib_path, sizeof(lib_path), "%.*s",
			(int)impl->shared_lib_path.slen, impl->shared_lib_path.ptr);
	pj_ansi_snprintf(init_name, sizeof(init_name), "%.*s",
			(int)impl->init_factory_name.slen, impl->init_factory_name.ptr);

	void* handle = dlopen(lib_path, RTLD_LAZY);
	if (handle != NULL) {
		void* func_ptr = dlsym(handle, init_name);
		if(func_ptr == NULL){
			PJ_LOG(2, (THIS_FILE, "Invalid factory name : %s", init_name));
		}
		return func_ptr;
	} else {
		PJ_LOG(1, (THIS_FILE, "Cannot open : %s %s", lib_path, dlerror()));
	}
	return NULL;
}

static void tone_init(pj_pool_t * pool){
    PjToneRingback * tRing = new PjToneRingback();
    PjToneBusy * tBusy = new PjToneBusy();
    PjToneError * tErr = new PjToneError();
    PjToneZRTPOK * tZrtp = new PjToneZRTPOK();

    tRing->tone_init(css_var.pool);
    tBusy->tone_init(css_var.pool);
    tErr->tone_init(css_var.pool);
    tZrtp->tone_init(css_var.pool);

    css_var.toneRingback = tRing;
    css_var.toneBusy = tBusy;
    css_var.toneError = tErr;
    css_var.toneZRTPOK = tZrtp;
}

static void tone_deinit(){
    if (css_var.toneRingback != NULL){
        PjToneRingback * tRing = static_cast<PjToneRingback *>(css_var.toneRingback);
        tRing->tone_destroy();
        delete tRing;
        css_var.toneRingback = NULL;
    }
    
    if (css_var.toneBusy != NULL){
        PjToneBusy * tBusy = static_cast<PjToneBusy *>(css_var.toneBusy);
        tBusy->tone_destroy();
        delete tBusy;
        css_var.toneBusy = NULL;
    }
    
    if (css_var.toneError != NULL){
        PjToneError * tError = static_cast<PjToneError *>(css_var.toneError);
        tError->tone_destroy();
        delete tError;
        css_var.toneError = NULL;
    }
    
    if (css_var.toneZRTPOK != NULL){
        PjToneZRTPOK * tZrtp = static_cast<PjToneZRTPOK *>(css_var.toneZRTPOK);
        tZrtp->tone_destroy();
        delete tZrtp;
        css_var.toneZRTPOK = NULL;
    }
}

//Wrap start & stop
PJ_DECL(pj_status_t) sipstack_init(pjsua_config *ua_cfg,
		pjsua_logging_config *log_cfg, pjsua_media_config *media_cfg,
		sipstack_config *css_cfg, jobject context) {
	pj_status_t result;
	unsigned i;

    // Prepare thread registration linked list.
    // If it was not deallocated from a previous run we sacrifice memory for stability.
	pj_thread_storage_reset();

	// Setting new thread registrator
	ThreadRegistratorSetter(pjsua_register_thread2);

	/* Create memory pool for application. */
	if(css_var.pool == NULL){
		css_var.pool = pjsua_pool_create("css", 1024, 1024);
		PJ_ASSERT_RETURN(css_var.pool, PJ_ENOMEM);
	}

	// Finalize configuration
	log_cfg->cb = &pj_android_log_msg;

	// Static cfg
	extern pj_bool_t pjsip_use_compact_form;
	extern pj_bool_t pjsip_include_allow_hdr_in_dlg;
	extern pj_bool_t pjmedia_add_rtpmap_for_static_pt;
	extern pj_bool_t pjmedia_add_bandwidth_tias_in_sdp;
	extern pj_bool_t pjsua_no_update;
	extern pj_bool_t pjmedia_webrtc_use_ns;

	pjsua_no_update = css_cfg->use_no_update ? PJ_TRUE : PJ_FALSE;
	pjsip_use_compact_form = css_cfg->use_compact_form_headers ? PJ_TRUE : PJ_FALSE;
	/* do not transmit Allow header */
	pjsip_include_allow_hdr_in_dlg = css_cfg->use_compact_form_headers ? PJ_FALSE : PJ_TRUE;
	/* Do not include rtpmap for static payload types (<96) */
	pjmedia_add_rtpmap_for_static_pt = css_cfg->use_compact_form_sdp ? PJ_FALSE : PJ_TRUE;
	/* Do not enable bandwidth information inclusion in sdp */
	pjmedia_add_bandwidth_tias_in_sdp = css_cfg->add_bandwidth_tias_in_sdp ? PJ_TRUE : PJ_FALSE;
	/* Use noise suppressor ? */
	pjmedia_webrtc_use_ns = css_cfg->use_noise_suppressor ? PJ_TRUE : PJ_FALSE;

	css_tcp_keep_alive_interval = css_cfg->tcp_keep_alive_interval;
	css_tls_keep_alive_interval = css_cfg->tls_keep_alive_interval;

	// Transaction timeouts
	pjsip_sip_cfg_var.tsx.t1 = css_cfg->tsx_t1_timeout;
	pjsip_sip_cfg_var.tsx.t2 = css_cfg->tsx_t2_timeout;
	pjsip_sip_cfg_var.tsx.t4 = css_cfg->tsx_t4_timeout;
	pjsip_sip_cfg_var.tsx.td = css_cfg->tsx_td_timeout;
	pjsip_sip_cfg_var.endpt.disable_tcp_switch = css_cfg->disable_tcp_switch;
	pjsip_sip_cfg_var.endpt.disable_rport = css_cfg->disable_rport;

	// Audio codec cfg
	css_var.extra_aud_codecs_cnt = css_cfg->extra_aud_codecs_cnt;
	for (i = 0; i < css_cfg->extra_aud_codecs_cnt; i++) {
		dynamic_factory *css_codec = &css_var.extra_aud_codecs[i];
		dynamic_factory *cfg_codec = &css_cfg->extra_aud_codecs[i];

		pj_strdup_with_null(css_var.pool, &css_codec->shared_lib_path, &cfg_codec->shared_lib_path);
		pj_strdup_with_null(css_var.pool, &css_codec->init_factory_name, &cfg_codec->init_factory_name);
	}

	// Video codec cfg -- For now only destroy is useful but for future
	// hopefully vid codec mgr will behaves as audio does
	// Also in this case destroy will become obsolete
	css_var.extra_vid_codecs_cnt = css_cfg->extra_vid_codecs_cnt;
	for (i = 0; i < css_cfg->extra_vid_codecs_cnt; i++) {
		dynamic_factory *css_codec = &css_var.extra_vid_codecs[i];
		dynamic_factory *cfg_codec = &css_cfg->extra_vid_codecs[i];

		pj_strdup_with_null(css_var.pool, &css_codec->shared_lib_path, &cfg_codec->shared_lib_path);
		pj_strdup_with_null(css_var.pool, &css_codec->init_factory_name, &cfg_codec->init_factory_name);


		css_codec = &css_var.extra_vid_codecs_destroy[i];
		cfg_codec = &css_cfg->extra_vid_codecs_destroy[i];

		pj_strdup_with_null(css_var.pool, &css_codec->shared_lib_path, &cfg_codec->shared_lib_path);
		pj_strdup_with_null(css_var.pool, &css_codec->init_factory_name, &cfg_codec->init_factory_name);

	}

	// ZRTP cfg
	css_var.default_use_zrtp = css_cfg->use_zrtp;
	ua_cfg->cb.on_create_media_transport = &on_transport_created_wrapper;

#if defined(PJMEDIA_HAS_ZRTP) && PJMEDIA_HAS_ZRTP!=0
	pj_ansi_snprintf(css_var.zid_file, sizeof(css_var.zid_file), "%.*s/simple.zid",
			(int)css_cfg->storage_folder.slen, css_cfg->storage_folder.ptr);
#endif

	JNIEnv *jni_env = 0;
	ATTACH_JVM(jni_env);
	//css_var.context = (*jni_env)->NewGlobalRef(jni_env, context); // C-way
	css_var.context = jni_env->NewGlobalRef(context);
	DETACH_JVM(jni_env);

	result = (pj_status_t) pjsua_init(ua_cfg, log_cfg, media_cfg);
	if (result == PJ_SUCCESS) {
		/* Tones */
                tone_init(css_var.pool);

		/* Init audio device */
		pj_status_t added_audio = PJ_ENOTFOUND;
		if (css_cfg->audio_implementation.init_factory_name.slen > 0) {
			pjmedia_aud_dev_factory* (*init_factory)(pj_pool_factory *pf) = 
                                (pjmedia_aud_dev_factory* (*)(pj_pool_factory *)) 
                                get_library_factory(&css_cfg->audio_implementation);
			if(init_factory != NULL) {
				pjmedia_aud_register_factory(init_factory);
				added_audio = PJ_SUCCESS;
				PJ_LOG(4, (THIS_FILE, "Loaded audio dev"));
			}
		}

		// Fallback to default audio dev if no one found
		if (added_audio != PJ_SUCCESS) {
			pjmedia_aud_register_factory(&pjmedia_android_factory);
		}

		// Init video device
#if PJMEDIA_HAS_VIDEO
		// load renderer
		if (css_cfg->video_render_implementation.init_factory_name.slen > 0) {
			pjmedia_vid_dev_factory* (*init_factory)(pj_pool_factory *pf) = 
                            (pjmedia_vid_dev_factory* (*)(pj_pool_factory *))
                            get_library_factory(&css_cfg->video_render_implementation);
			if(init_factory != NULL) {
				pjmedia_vid_register_factory(init_factory, NULL);
				PJ_LOG(4, (THIS_FILE, "Loaded video render dev"));
			}
		}
		// load capture
		if (css_cfg->video_capture_implementation.init_factory_name.slen > 0) {
			pjmedia_vid_dev_factory* (*init_factory)(pj_pool_factory *pf) = 
                                (pjmedia_vid_dev_factory* (*)(pj_pool_factory *))
                                get_library_factory(&css_cfg->video_capture_implementation);
			if(init_factory != NULL) {
				pjmedia_vid_register_factory(init_factory, NULL);
				PJ_LOG(4, (THIS_FILE, "Loaded video capture dev"));
			}
		}

		// Load ffmpeg converter
		pjmedia_converter_mgr* cvrt_mgr = pjmedia_converter_mgr_instance();
		if(css_cfg->vid_converter.init_factory_name.slen > 0){
			pj_status_t (*init_factory)(pjmedia_converter_mgr* cvrt_mgr) = 
                                (pj_status_t (*)(pjmedia_converter_mgr*)) 
                                get_library_factory(&css_cfg->vid_converter);
			if(init_factory != NULL) {
				init_factory(cvrt_mgr);
				PJ_LOG(4, (THIS_FILE, "Loaded video converter"));
			}
		}


		// Load video codecs
		pjmedia_vid_codec_mgr* vid_mgr = pjmedia_vid_codec_mgr_instance();

		for (i = 0; i < css_var.extra_vid_codecs_cnt; i++) {
			dynamic_factory *codec = &css_var.extra_vid_codecs[i];
			pj_status_t (*init_factory)(pjmedia_vid_codec_mgr *mgr, pj_pool_factory *pf) = 
                                (pj_status_t (*)(pjmedia_vid_codec_mgr *, pj_pool_factory *))
                                get_library_factory(codec);
			if(init_factory != NULL){
				pj_status_t status = init_factory(vid_mgr, &pjsua_var.cp.factory);
				if(status != PJ_SUCCESS) {
					PJ_LOG(2, (THIS_FILE,"Error loading dynamic codec plugin"));
				}
	    	}
		}

#endif
		}

	return result;
}

PJ_DECL(pj_status_t) sipstack_destroy(unsigned flags) {
    tone_deinit();

#if PJMEDIA_HAS_VIDEO
	unsigned i;
	for (i = 0; i < css_var.extra_vid_codecs_cnt; i++) {
		dynamic_factory *codec = &css_var.extra_vid_codecs_destroy[i];
		pj_status_t (*destroy_factory)() = (pj_status_t (*)()) get_library_factory(codec);
		if(destroy_factory != NULL){
			pj_status_t status = destroy_factory();
			if(status != PJ_SUCCESS) {
				PJ_LOG(2, (THIS_FILE,"Error loading dynamic codec plugin"));
			}
    	}
	}
#endif

	if (css_var.pool) {
		pj_pool_release(css_var.pool);
		css_var.pool = NULL;
	}

	// Dump thread reg list.
	dump_pj_thread_desc_list();

	if(css_var.context){
		JNIEnv *jni_env = 0;
		ATTACH_JVM(jni_env);
		//(*jni_env)->DeleteGlobalRef(jni_env, css_var.context); // C-way
		jni_env->DeleteGlobalRef(css_var.context); 
		DETACH_JVM(jni_env);
	}

	pj_status_t pj_status_to_return = (pj_status_t) pjsua_destroy2(flags);
	dealloc_pj_thread_desc_list();
	return pj_status_to_return;
}

PJ_DECL(void) sipstack_acc_config_default(sipstack_acc_config* css_acc_cfg){
	css_acc_cfg->use_zrtp = -1;
	css_acc_cfg->p_preferred_identity.slen = 0;
}

PJ_DECL(pj_status_t) sipstack_set_acc_user_data(pjsua_acc_config* acc_cfg, sipstack_acc_config* css_acc_cfg){

	sipstack_acc_config *additional_acc_cfg = PJ_POOL_ZALLOC_T(css_var.pool, sipstack_acc_config);
	pj_memcpy(additional_acc_cfg, css_acc_cfg, sizeof(sipstack_acc_config));
	pj_strdup(css_var.pool, &additional_acc_cfg->p_preferred_identity, &css_acc_cfg->p_preferred_identity);
	acc_cfg->user_data = additional_acc_cfg;

	return PJ_SUCCESS;
}

PJ_DECL(pj_status_t) sipstack_init_acc_msg_data(pj_pool_t* pool, pjsua_acc_id acc_id, pjsua_msg_data* msg_data){
	sipstack_acc_config *additional_acc_cfg = NULL;
	// P-Asserted-Identity header
	pj_str_t hp_preferred_identity_name = { "P-Preferred-Identity", 20 };

	// Sanity check
	PJ_ASSERT_RETURN(msg_data != NULL, PJ_EINVAL);


	// Get acc infos
	if(pjsua_acc_is_valid(acc_id)){
		additional_acc_cfg = (sipstack_acc_config *) pjsua_acc_get_user_data(acc_id);
	}

	// Process additionnal config for this account
	if(additional_acc_cfg != NULL){
		if(additional_acc_cfg->p_preferred_identity.slen > 0){
			// Create new P-Asserted-Identity hdr if necessary
			pjsip_generic_string_hdr* hdr = pjsip_generic_string_hdr_create(pool,
					&hp_preferred_identity_name, &additional_acc_cfg->p_preferred_identity);
			// Push it to msg data
			pj_list_push_back(&msg_data->hdr_list, hdr);
		}
	}

	return PJ_SUCCESS;
}

PJ_DECL(pj_status_t) sipstack_msg_data_add_string_hdr(pj_pool_t* pool, pjsua_msg_data* msg_data, pj_str_t* hdr_name, pj_str_t* hdr_value){

    // Sanity check
    PJ_ASSERT_RETURN(msg_data != NULL && hdr_name != NULL && hdr_value != NULL, PJ_EINVAL);
    if(hdr_name->slen <= 2 || hdr_value->slen <= 0){
        return PJ_EINVAL;
    }
    // Ensure it's a X- prefixed header. This is to avoid crappy usage/override of specified headers
    // That should be implemented properly elsewhere.
    if(hdr_name->ptr[0] != 'X' || hdr_name->ptr[1] != '-'){
        return PJ_EINVAL;
    }
    pjsip_generic_string_hdr* hdr = pjsip_generic_string_hdr_create(pool,
                        hdr_name, hdr_value);
    // Push it to msg data
    pj_list_push_back(&msg_data->hdr_list, hdr);
}

static void update_active_calls(const pj_str_t *new_ip_addr) {
	pjsip_tpselector tp_sel;
	pjsua_init_tpselector(0, &tp_sel); // << 0 is hard coded here for active transportId.  could be passed in if needed.
	int ndx;
	for (ndx = 0; ndx < pjsua_var.ua_cfg.max_calls; ++ndx) {
		pjsua_call *call = &pjsua_var.calls[ndx];
		if (!call->inv || call->inv->state != PJSIP_INV_STATE_CONFIRMED) {
			continue;
		}

		// -- TODO : we should do something here about transport,
		// but something that actually restart media transport for this call
		// cause copying ip addr somewhere is not valid for stun and nat cases
		//transport_set_sdp_addr_from_string(call->med_orig, new_ip_addr);
		//transport_set_sdp_addr_from_string(call->med_tp,   new_ip_addr);

		if (call->local_hold) {
			pjsua_call_set_hold(ndx, NULL);
		} else {
			pjsua_call_reinvite(ndx, PJ_TRUE, NULL);
		}
	}
}

PJ_DECL(pj_status_t) update_transport(const pj_str_t *new_ip_addr) {
	PJSUA_LOCK();

	PJ_LOG(4, (THIS_FILE,"update_transport to addr = %s", new_ip_addr->ptr));
	// No need ot check thread cause sipstack use handler thread

	/*
	 pjsua_transport_config cfg;
	 pjsua_transport_config_default(&cfg);
	 cfg.port = 0;

	 pjsua_media_transports_create(&cfg);
	 */
	update_active_calls(new_ip_addr);

	PJSUA_UNLOCK();
	return PJ_SUCCESS;
}

PJ_DECL(pj_str_t) get_rx_data_header(const pj_str_t name, pjsip_rx_data* data){
	pjsip_generic_string_hdr *hdr =
			(pjsip_generic_string_hdr*) pjsip_msg_find_hdr_by_name(data->msg_info.msg, &name, NULL);
	if (hdr && hdr->hvalue.ptr) {
		return hdr->hvalue;
	}
	return pj_str("");
}

PJ_DECL(pjsip_transport *) get_transport(pjsua_reg_info * info){
	if (info == NULL
		|| info->cbparam == NULL
		|| info->cbparam->rdata == NULL
		|| info->cbparam->rdata->tp_info.transport == NULL)
	{
		return NULL;
	}

	return info->cbparam->rdata->tp_info.transport;
}

PJ_DECL(long) get_transport_ptr(pjsip_transport *transport){
	return (long)transport;
}

PJ_DECL(pj_status_t) pjsua_call_hangup_ex(pjsua_call_id call_id, unsigned code, const pj_str_t *reason){
	pj_status_t status2 = PJ_EINVAL;
	pjsua_msg_data * p_msg_data = NULL;
	pjsua_msg_data msg_data_aux;
	pjsip_generic_string_hdr bye_cause;
	char hVal[64];

	// If we terminate a call due to GSM unavailability, add a special header.
	if (code != 0) {
		p_msg_data = &msg_data_aux;
		pjsua_msg_data_init(p_msg_data);

		snprintf(hVal, 63, "%d", code);

		pj_str_t hname = pj_str(PEX_HEADER_BYE_TERMINATION);
		pj_str_t hvalue = pj_str(hVal);

		/* Add warning header */
		pjsip_generic_string_hdr_init2(&bye_cause, &hname, &hvalue);
		pj_list_push_back(&(p_msg_data->hdr_list), &bye_cause);
	}

	status2 = pjsua_call_hangup(call_id, code, reason, p_msg_data);
	return status2;
}

PJ_DECL(pj_str_t) get_call_hangup_cause(pjsip_event * e){
	if (e != NULL
			&& e->type == PJSIP_EVENT_TSX_STATE
			&& e->body.tsx_state.type == PJSIP_EVENT_RX_MSG
			&& e->body.tsx_state.src.rdata != NULL
			&& e->body.tsx_state.src.rdata->msg_info.msg != NULL)
	{
		pj_str_t byeHdr = pj_str(PEX_HEADER_BYE_TERMINATION);
		pj_str_t byeCause = search_for_header(&byeHdr, e->body.tsx_state.src.rdata->msg_info.msg);
		return byeCause;
	}

	return pj_str("");
}

PJ_DECL(int) get_call_hangup_cause_int(pjsip_event * e){
#define PEX_INT_BUFF_SIZE 6
	char buff[PEX_INT_BUFF_SIZE+1];
	pj_str_t call_hangup_str = get_call_hangup_cause(e);
	int i = 0, stop_char = call_hangup_str.slen >= PEX_INT_BUFF_SIZE ? PEX_INT_BUFF_SIZE : call_hangup_str.slen;

	if (call_hangup_str.slen == 0 || call_hangup_str.ptr == NULL){
		return -1;
	}

	// Find first non-digit character.
	for(i=0; i < call_hangup_str.slen && i < PEX_INT_BUFF_SIZE; i++){
		const char ch = call_hangup_str.ptr[i];
		if (!isdigit((int)ch)){
			stop_char = i;
			break;
		}
	}

	if (stop_char <= 0){
		return -1;
	}

	memcpy(buff, call_hangup_str.ptr, stop_char);
	buff[stop_char] = 0;
	return atoi(buff);
}

PJ_DECL(int) call_get_state(pjsua_call_id call_id){
	pjsua_call *call = NULL;
	pjsip_dialog *dlg = NULL;
	int state = PJSIP_INV_STATE_NULL;

	PJ_ASSERT_RETURN(call_id>=0 && call_id<(int)pjsua_var.ua_cfg.max_calls, PJSIP_INV_STATE_NULL);

	/* Use PJSUA_LOCK() instead of acquire_call():
	 *  https://trac.pjsip.org/repos/ticket/1371
	 */
	PJSUA_LOCK();

	call = &pjsua_var.calls[call_id];
	dlg = (call->inv ? call->inv->dlg : call->async_call.dlg);
	if (!dlg) {
		PJSUA_UNLOCK();
		return PJSIP_INV_STATE_DISCONNECTED;
	}

	/* state, state_text */
	if (call->inv) {
		state = call->inv->state;
	} else if (call->async_call.dlg && call->last_code==0) {
		state = PJSIP_INV_STATE_NULL;
	} else {
		state = PJSIP_INV_STATE_DISCONNECTED;
	}

	PJSUA_UNLOCK();
	return state;
}

PJ_DEF(int) sipstack_is_underflow_event(pjmedia_event * event){
	return event != NULL && event->type == PJMEDIA_EVENT_UNDERFLOW;
}

PJ_DEF(pjmedia_event_underflow_data) sipstack_get_underflow_data(pjmedia_event * event){
	pjmedia_event_underflow_data to_return;
	to_return.underflow_ratio = 0;
	to_return.underflow_status = -1;
	to_return.conf_port_idx = -1;

	if (event != NULL && event->type == PJMEDIA_EVENT_UNDERFLOW){
		to_return.underflow_ratio = event->data.underflow.underflow_ratio;
		to_return.underflow_status = event->data.underflow.underflow_status;
		to_return.conf_port_idx = event->data.underflow.conf_port_idx;
	}

	return to_return;
}

PJ_DECL(pj_str_t) search_for_header(pj_str_t *hdr, const pjsip_msg * msg){
	if (hdr == NULL || msg == NULL){
		return pj_str("");
	}

	pjsip_generic_string_hdr *s_hdr = NULL;

	/* Save ETag value */
	s_hdr = (pjsip_generic_string_hdr*) pjsip_msg_find_hdr_by_name(msg, hdr, NULL);
	if (s_hdr) {
		return s_hdr->hvalue;
	}

	return pj_str("");
}


PJ_DECL(pjsip_msg *) get_msg_from_evt(pjsip_event *e) {
	if (e == NULL){
		return NULL;
	}

	if (e->type == PJSIP_EVENT_TSX_STATE){
		if (e->body.tsx_state.type == PJSIP_EVENT_RX_MSG
				&& e->body.tsx_state.src.rdata != NULL
				&& e->body.tsx_state.src.rdata->msg_info.msg != NULL)
		{
			return e->body.tsx_state.src.rdata->msg_info.msg;

		}
		else if (e->body.tsx_state.type == PJSIP_EVENT_TX_MSG
				&& e->body.tsx_state.src.tdata != NULL
				&& e->body.tsx_state.src.tdata->msg != NULL)
		{
			return e->body.tsx_state.src.tdata->msg;
		}


	} else if (e->type == PJSIP_EVENT_RX_MSG
			&& e->body.rx_msg.rdata != NULL
			&& e->body.rx_msg.rdata->msg_info.msg != NULL)
	{
		return e->body.rx_msg.rdata->msg_info.msg;

	} else if (e->type == PJSIP_EVENT_TX_MSG
			&& e->body.tx_msg.tdata != NULL
			&& e->body.tx_msg.tdata->msg != NULL)
	{
		return e->body.tx_msg.tdata->msg;

	} else {
		return NULL;
	}

	return NULL;
}

PJ_DECL(pj_str_t) get_callid_from_msg(const pjsip_msg * msg){
	if (msg == NULL){
		return pj_str("");
	}

	pjsip_cid_hdr *hdr = (pjsip_cid_hdr*) pjsip_msg_find_hdr(msg, PJSIP_H_CALL_ID, NULL);
	if (hdr == NULL){
		return pj_str("");
	}

	return hdr->id;
}

PJ_DECL(pj_str_t) get_callid_from_evt(pjsip_event * e){
	pjsip_msg * msg = get_msg_from_evt(e);
	if (msg == NULL){
		return pj_str("");
	}

	return get_callid_from_msg(msg);
}

PJ_DECL(pj_status_t) play_zrtp_ok_sound(){
    if (css_var.toneZRTPOK){
        // Do not play sound in ZRTP (RTP) thread, schedule a timer in order 
        // to avoid concurrency problems.
        return static_cast<PjToneZRTPOK *>(css_var.toneZRTPOK)->tone_schedule_start(250);
    } else {
        return PJ_FALSE;
    }
}

/**
 * On call state used to automatically ringback.
 */
PJ_DECL(void) css_on_call_state(pjsua_call_id call_id, pjsip_event *e) {
	pjsua_call_info call_info;
	pjsua_call_get_info(call_id, &call_info);

        if (css_var.toneRingback != NULL){
            static_cast<PjToneRingback *>(css_var.toneRingback)->on_call_state(call_id, e, &call_info);
        }

        if (css_var.toneBusy != NULL){
            static_cast<PjToneBusy *>(css_var.toneBusy)->on_call_state(call_id, e, &call_info);
        }

        if (css_var.toneError != NULL){
            static_cast<PjToneError *>(css_var.toneError)->on_call_state(call_id, e, &call_info);
        }

        if (css_var.toneZRTPOK != NULL){
            static_cast<PjToneZRTPOK *>(css_var.toneZRTPOK)->on_call_state(call_id, e, &call_info);
        }
}

/**
 * On call media state used to automatically ringback.
 */
PJ_DECL(void) css_on_call_media_state(pjsua_call_id call_id){
        pjsua_call_info call_info;
	pjsua_call_get_info(call_id, &call_info);
        
        if (css_var.toneRingback != NULL){
            static_cast<PjToneRingback *>(css_var.toneRingback)->on_call_media_state(call_id, &call_info);
        }

        if (css_var.toneBusy != NULL){
            static_cast<PjToneBusy *>(css_var.toneBusy)->on_call_media_state(call_id, &call_info);
        }

        if (css_var.toneError != NULL){
            static_cast<PjToneError *>(css_var.toneError)->on_call_media_state(call_id, &call_info);
        }

        if (css_var.toneZRTPOK != NULL){
            static_cast<PjToneZRTPOK *>(css_var.toneZRTPOK)->on_call_media_state(call_id, &call_info);
        }
}

/**
 * Generates DH prime number with generator = 0x02.
 * Prime number is BIGNUM printed in hexadecimal, returned in second parameter.
 */
PJ_DECL(int) openssl_DH_genPrime(unsigned prime_len, char *buffer, unsigned maxlen){
	 int code = 0;
	 int tmplen = 0;
	 char * tmpBuff = NULL;
	 DH* dh = DH_new();

	 DH_generate_parameters_ex(dh, prime_len, DH_GENERATOR_2, NULL);
	 DH_check(dh, &code);

	 tmpBuff = BN_bn2hex(dh->p);
	 DH_free(dh);

	 if (tmpBuff == NULL){
		 return -1;
	 }

	 tmplen = strlen(tmpBuff);
	 strncpy(buffer, tmpBuff, tmplen < maxlen ? tmplen : maxlen);
	 OPENSSL_free(tmpBuff);

	 return code;
}

/**
 * Performs DH_check on a prime given in hexadecimal in first parameter
 * and generator 0x02.
 */
PJ_DECL(int) openssl_DH_check(const char *buffer){
	int code = 0;
	int len  = 0;
	if (buffer==NULL){
		return -1;
	}

	DH* dh = DH_new();

	// Set generator to 0x02.
	dh->g = BN_new();
	BN_set_word(dh->g, DH_GENERATOR_2);

	// Convert to BIGNUM
	len = BN_hex2bn(&(dh->p), buffer);
	if (len == 0 || buffer[len]){
		code = -2;
		goto err;
	}

	// Do the final check.
	if (DH_check(dh, &code) == -1){
		code = -1;
		goto err;
	}

err:
	DH_free(dh);
	return code;
}

/**
 * Sets user presence state + custom status text.
 */
PJ_DECL(pj_status_t) pjsua_acc_set_custom_online_status(pjsua_acc_id acc_id, pj_bool_t is_online, const char * statusText){

	pj_status_t ret;
	pjrpid_element *pr;
	size_t slen = 0;

	if (statusText==NULL){
		return 1;
	}

	slen = strlen(statusText);

	// Debug message - size of the buffer
	PJ_LOG(4, (THIS_FILE, "Presence text buff = %ld", slen + 512));

	// Create a temporary pool
	pj_pool_t * tmp_pool = pjsua_pool_create("tmp-status-pool", slen + 512, 512);
	pjrpid_element * rpid = (pjrpid_element *) pj_pool_zalloc(tmp_pool, sizeof(pjrpid_element));
	rpid->type = PJRPID_ELEMENT_TYPE_PERSON;
	rpid->activity = PJRPID_ACTIVITY_UNKNOWN;

	// Duplicate status string
	pj_strdup2_with_null(tmp_pool, &(rpid->note), statusText);

	// Call original function with prepared rpid
	ret = pjsua_acc_set_online_status2(acc_id, is_online, rpid);

	// Destroy pool after setting. RPID was copied to internal structures.
	pj_pool_release(tmp_pool);

	return ret;
}

PJ_DEF(void) pjsua_kill(){
	kill(getpid(),SIGKILL);
}

PJ_DEF(void) pjsua_assert(){
	assert(0);
}

PJ_DEF(pj_bool_t) pjsua_is_thread_registered(){
    return pj_thread_is_registered();
}

// Helper function for inserting pj_thread_desc into linked list
PJ_DEF(unsigned int) store_pj_thread_desc(pj_thread_desc *thread_desc){
    pj_thread_desc_list_el *item;
    unsigned int curCount = 0;

    item = (pj_thread_desc_list_el *)malloc(sizeof(pj_thread_desc_list_el));
    item->next = NULL;
    item->thread_desc = thread_desc;

    // Perform linked list insertion in a critical section to keep linked list consistent.
    pthread_mutex_lock(&(pj_thread_storage.mutex));
    if (pj_thread_storage.first == NULL){
        pj_thread_storage.first = item;
        pj_thread_storage.last = item;
    } else {
        pj_thread_storage.last->next = item;
        pj_thread_storage.last = item;
    }

    pj_thread_storage.count += 1;
    curCount = pj_thread_storage.count;
    pthread_mutex_unlock(&(pj_thread_storage.mutex));

    return curCount;
}

// Helper function for dumping linked list.
PJ_DEF(void) dump_pj_thread_desc_list(){
    pthread_mutex_lock(&(pj_thread_storage.mutex));
    PJ_LOG(5, (THIS_FILE, "Registered thread dump. Count=%u", pj_thread_storage.count));

    unsigned int idx = 0;
    pj_thread_desc_list_el *current = pj_thread_storage.first;
    while (current != NULL){
        PJ_LOG(5, (THIS_FILE, " - %02u. thread dump cur=%p desc=%p next=%p",
                                idx, current, current->thread_desc, current->next));

        current = current->next;
        idx += 1;
    }

    pthread_mutex_unlock(&(pj_thread_storage.mutex));
}

// Helper function for freeing linked list
PJ_DEF(void) dealloc_pj_thread_desc_list(){
    // Perform linked list deallocation in a critical section to keep linked list consistent.
    pthread_mutex_lock(&(pj_thread_storage.mutex));

    pj_thread_desc_list_el *current = pj_thread_storage.first;
    while (current != NULL){
        // Free descriptor, if non-null.
        if (current->thread_desc != NULL){
            free(current->thread_desc);
            current->thread_desc = NULL;
        }

        pj_thread_desc_list_el *toFree = current;
        current = current->next;

        free(toFree);
    }

    // Put to initial state.
    pj_thread_storage.first = NULL;
    pj_thread_storage.last = NULL;
    pj_thread_storage.count = 0;

    pthread_mutex_unlock(&(pj_thread_storage.mutex));
}

// Registering thread the safe way (putting it into linked list, because thread_desc needs to be persistent, see pj_thread_register() description)
PJ_DEF(pj_status_t) pjsua_register_thread2(const char * thread_name){
    pj_thread_desc *thread_desc;
    const unsigned int thread_desc_size =  sizeof(pj_thread_desc);
    thread_desc = (pj_thread_desc *)malloc(thread_desc_size);

    // Add allocated memory to the linked list register.
    unsigned int registeredThreads = store_pj_thread_desc(thread_desc);

    pj_thread_t *a_thread = NULL;
    pj_status_t status;
    status = pj_thread_register(thread_name, *thread_desc, &a_thread);
    if (status == PJ_SUCCESS){
        PJ_LOG(5, (THIS_FILE, "Registered thread [%s] num=%u, size of allocated memory [%d | %p] B, status [%d].",
                thread_name, registeredThreads, thread_desc_size, *thread_desc, status));
    }

    return status;
}

PJ_DEF(pj_status_t) pjsua_register_thread(int processId, int threadId){
    char thread_name[160];
    int len = pj_ansi_snprintf(thread_name, sizeof(thread_name), "work_thread_%d_%d", processId, threadId);
    thread_name[len] = '\0';

    return pjsua_register_thread2(thread_name);
}

PJ_DEF(jstring) conv_pj_str_t_to_jstring(const pj_str_t *pj_string){
	if (pj_string == NULL || pj_string->ptr == NULL){
		return NULL;
	}

	pj_ssize_t slen = pj_string->slen;
	char* ptr = pj_string->ptr;

	jstring result = NULL;

	JNIEnv *jni_env = 0;
	ATTACH_JVM(jni_env);

    char *dup_str = (char *)malloc(sizeof(char) * (slen + 1));
    if (dup_str == NULL){
        jclass exClass = jni_env->FindClass("java/lang/Exception");
		jni_env->ThrowNew(exClass, "conv_pj_str_t_to_jstring: cannot allocate buffer for string conversion");
    } else {
    	memcpy(dup_str, ptr, slen);
    	dup_str[slen] = 0;
    	result = jni_env->NewStringUTF(dup_str);
    	free(dup_str);
    }
	DETACH_JVM(jni_env);

    return result;
}

///**
// * Returns 1 on success or 0 on error.
// */
//PJ_DEF(int) crypto_pbkdf2_hmac_sha256(const char *pass, const char salt[], size_t saltLen, int iter, char out[], size_t keyLen) {
//    // typecasting explained: https://stackoverflow.com/questions/15078638/can-i-turn-unsigned-char-into-char-and-vice-versa
//    const unsigned char *salt_u = (const unsigned char *) (salt);
//    unsigned char *out_u = (unsigned char *) (out);
//
//    return PKCS5_PBKDF2_HMAC(pass, strlen(pass), salt_u, saltLen, iter, EVP_sha256(), keyLen, out_u);
//}
//
//PJ_DEF(int) crypto_pbkdf2_hmac_sha1(const char *pass, const char salt[], size_t saltLen, int iter, char out[], size_t keyLen){
//    const unsigned char *salt_u = (const unsigned char*) (salt);
//    unsigned char *out_u = (unsigned char *) (out);
//
//    return PKCS5_PBKDF2_HMAC_SHA1(pass, strlen(pass), salt_u, saltLen, iter, keyLen, out_u);
//}


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
#include "zrtp_android.h"
#include "sipstack_internal.h"
#include "pjsua_jni_addons.h"

#include <dlfcn.h>
#include "pj_loader.h"

#include <signal.h>
#include <unistd.h>

/*
 * ZRTP stuff
 */
#define THIS_FILE		"zrtp_android.c"
#if defined(PJMEDIA_HAS_ZRTP) && PJMEDIA_HAS_ZRTP!=0
#include <pjmedia/transport_ice.h>
#include <pjnath/ice_strans.h>
#include <pjnath/errno.h>
#include <inttypes.h>

#include "transport_zrtp.h"
#include "libzrtpcpp/ZrtpCWrapper.h"

/* The ZRTP protocol states */
const char* zrtpStatesName[] = {
    "Initial",            /*!< Initial state after starting the state engine */
    "Detect",             /*!< State sending Hello, try to detect answer message */
    "AckDetected",        /*!< HelloAck received */
    "AckSent",            /*!< HelloAck sent after Hello received */
    "WaitCommit",         /*!< Wait for a Commit message */
    "CommitSent",         /*!< Commit message sent */
    "WaitDHPart2",        /*!< Wait for a DHPart2 message */
    "WaitConfirm1",       /*!< Wait for a Confirm1 message */
    "WaitConfirm2",       /*!< Wait for a confirm2 message */
    "WaitConfAck",        /*!< Wait for Conf2Ack */
    "WaitClearAck",       /*!< Wait for clearAck - not used */
    "SecureState",        /*!< This is the secure state - SRTP active */
    "WaitErrorAck",       /*!< Wait for ErrorAck message */
    "numberOfStates"      /*!< Gives total number of protocol states */
};

const char* InfoCodes[] =
{
    "EMPTY",
    "Hello received, preparing a Commit",
    "Commit: Generated a public DH key",
    "Responder: Commit received, preparing DHPart1",
    "DH1Part: Generated a public DH key",
    "Initiator: DHPart1 received, preparing DHPart2",
    "Responder: DHPart2 received, preparing Confirm1",
    "Initiator: Confirm1 received, preparing Confirm2",
    "Responder: Confirm2 received, preparing Conf2Ack",
    "At least one retained secrets matches - security OK",
    "Entered secure state",
    "No more security for this session"
};

/**
 * Sub-codes for Warning
 */
const char* WarningCodes [] =
{
    "EMPTY",
    "Commit contains an AES256 cipher but does not offer a Diffie-Helman 4096",
    "Received a GoClear message",
    "Hello offers an AES256 cipher but does not offer a Diffie-Helman 4096",
    "No retained shared secrets available - must verify SAS",
    "Internal ZRTP packet checksum mismatch - packet dropped",
    "Dropping packet because SRTP authentication failed!",
    "Dropping packet because SRTP replay check failed!",
    "Valid retained shared secrets available but no matches found - must verify SAS"
};

/**
 * Sub-codes for Severe
 */
const char* SevereCodes[] =
{
    "EMPTY",
    "Hash HMAC check of Hello failed!",
    "Hash HMAC check of Commit failed!",
    "Hash HMAC check of DHPart1 failed!",
    "Hash HMAC check of DHPart2 failed!",
    "Cannot send data - connection or peer down?",
    "Internal protocol error occurred!",
    "Cannot start a timer - internal resources exhausted?",
    "Too much retries during ZRTP negotiation - connection or peer down?"
};

typedef struct zrtp_cb_user_data {
	pjsua_call_id call_id;
	pjmedia_transport *zrtp_tp;
	pj_pool_t * pool;
	pj_str_t sas;
	pj_str_t cipher;
	int sas_verified;
	int zrtp_hash_match;
} zrtp_cb_user_data;

struct jzrtp_allContext {
	ZrtpContext* zrtpContext;
	zrtp_cb_user_data* cbUserData;
};

#define ZRTP_LOG_MSG_BUFF (1024*5)
char zrtp_log_msg_buff[ZRTP_LOG_MSG_BUFF] = {0};

/**
 * Callback code to call functions in zrtpglue->transport.c
 */
typedef struct zrtplib_transport_cb_t {
    pj_status_t (*pjmedia_transport_zrtp_create)(pjmedia_endpt *endpt, const char *name, pjmedia_transport *transport, pjmedia_transport **p_tp, pj_bool_t close_slave);
    pj_status_t (*pjmedia_transport_zrtp_initialize)(pjmedia_transport *tp, const char *zidFilename, pj_bool_t autoEnable);
    void        (*pjmedia_transport_zrtp_setEnableZrtp)(pjmedia_transport *tp, pj_bool_t onOff);
    pj_bool_t   (*pjmedia_transport_zrtp_isEnableZrtp)(pjmedia_transport *tp);
    void        (*pjmedia_transport_zrtp_setUserCallback)(pjmedia_transport *tp, zrtp_UserCallbacks* ucb);
    void*       (*pjmedia_transport_zrtp_getUserData)(pjmedia_transport *tp);
    void        (*pjmedia_transport_zrtp_startZrtp)(pjmedia_transport *tp);
    void        (*pjmedia_transport_zrtp_stopZrtp)(pjmedia_transport *tp);
    void        (*pjmedia_transport_zrtp_setLocalSSRC)(pjmedia_transport *tp, uint32_t ssrc);
    pj_bool_t   (*pjmedia_transport_zrtp_isMitmMode)(pjmedia_transport *tp);
    void        (*pjmedia_transport_zrtp_setMitmMode)(pjmedia_transport *tp, pj_bool_t mitmMode);
    ZrtpContext*(*pjmedia_transport_zrtp_getZrtpContext)(pjmedia_transport *tp);
    pjmedia_transport * (*pjmedia_transport_zrtp_getSlaveTransport)(pjmedia_transport *tp);
} zrtplib_transport_cb_t;

/**
 * Callback code to call functions in zrtpcpp.
 */
typedef struct zrtplib_cb_t {
    int32_t (*zrtp_inState)(ZrtpContext* zrtpContext, int32_t state);
    void    (*zrtp_SASVerified)(ZrtpContext* zrtpContext);
    void    (*zrtp_resetSASVerified)(ZrtpContext* zrtpContext);
    int     (*zrtp_addEntropy)(const uint8_t *buffer, uint32_t length);
} zrtplib_cb_t;


#ifdef ZRTP_DYNAMIC_LINKING
static void* zrtpGlueHandle = NULL;
static void* zrtpCppHandle = NULL;
#endif

static zrtplib_transport_cb_t   zrtplib_transport_cb;
static zrtplib_cb_t             zrtplib_cb;

/**
 * Function code.
 */

// Resolve dynamic symbol to an internal callback structure.
// Solves loading problems, can be used only in jzrtp_zrtp_loadlib().
#ifdef ZRTP_DYNAMIC_LINKING
#define PH4_DLSYM(cb, handle, fction) do {                              \
    dlerror();                                                          \
    cb.fction = dlsym(handle, #fction);                                 \
    const char * dlerr = dlerror();                                     \
    if (dlerr!=NULL){                                                   \
        PJ_LOG(1, (THIS_FILE, "Cannot resolve [%s.%s]. Error: %s", #cb, #fction, dlerr));  \
        goto ON_LIB_LOAD_FAIL;                                          \
    }                                                                   \
} while(0)
#else
#define PH4_DLSYM(cb, handle, fction) do {                              \
    cb.fction = &fction;                                                \
} while(0)
#endif

// Macro for invoking a function in zrtp code.
#ifdef ZRTP_DYNAMIC_LINKING
#define PH4_CAN_INVOKE(cb, fction) (cb.fction!=NULL)
#define PH4_INVOKE(cb, fction, args) cb.fction args

#else
#define PH4_CAN_INVOKE(cb, fction) 1
#define PH4_INVOKE(cb, fction, args) fction args

#endif

PJ_DECL(int) jzrtp_zrtp_loadlib(pj_str_t glueLibPath, pj_str_t zrtpLibPath){
#ifdef ZRTP_DYNAMIC_LINKING
    char glue_lib_path[512];
    char zrtp_lib_path[512];

    // If already non-null, do not reinit.
    if (zrtpCppHandle!=NULL && zrtpGlueHandle!=NULL){
        return 0x10;
    }

	pj_ansi_snprintf(glue_lib_path, sizeof(glue_lib_path), "%.*s", (int)glueLibPath.slen, glueLibPath.ptr);
	pj_ansi_snprintf(zrtp_lib_path, sizeof(zrtp_lib_path), "%.*s", (int)zrtpLibPath.slen, zrtpLibPath.ptr);

	zrtpCppHandle  = dlopen(zrtp_lib_path, RTLD_LAZY);
	if (zrtpCppHandle==NULL){
	    PJ_LOG(1, (THIS_FILE, "Cannot open library [%s]. Error: %s", zrtp_lib_path, dlerror()));
	    return 0x1;
	}

	zrtpGlueHandle = dlopen(glue_lib_path, RTLD_LAZY);
    if (zrtpGlueHandle==NULL){
	    PJ_LOG(1, (THIS_FILE, "Cannot open library [%s]. Error: %s", glue_lib_path, dlerror()));
	    return 0x2;
	}
#endif
    // Resolve function symbols.
    PH4_DLSYM(zrtplib_cb, zrtpCppHandle, zrtp_inState);
    PH4_DLSYM(zrtplib_cb, zrtpCppHandle, zrtp_SASVerified);
    PH4_DLSYM(zrtplib_cb, zrtpCppHandle, zrtp_resetSASVerified);
    PH4_DLSYM(zrtplib_cb, zrtpCppHandle, zrtp_addEntropy);

    // Resolve transport function symbols.
    PH4_DLSYM(zrtplib_transport_cb, zrtpGlueHandle, pjmedia_transport_zrtp_create);
    PH4_DLSYM(zrtplib_transport_cb, zrtpGlueHandle, pjmedia_transport_zrtp_initialize);
    PH4_DLSYM(zrtplib_transport_cb, zrtpGlueHandle, pjmedia_transport_zrtp_setEnableZrtp);
    PH4_DLSYM(zrtplib_transport_cb, zrtpGlueHandle, pjmedia_transport_zrtp_isEnableZrtp);
    PH4_DLSYM(zrtplib_transport_cb, zrtpGlueHandle, pjmedia_transport_zrtp_setUserCallback);
    PH4_DLSYM(zrtplib_transport_cb, zrtpGlueHandle, pjmedia_transport_zrtp_getUserData);
    PH4_DLSYM(zrtplib_transport_cb, zrtpGlueHandle, pjmedia_transport_zrtp_startZrtp);
    PH4_DLSYM(zrtplib_transport_cb, zrtpGlueHandle, pjmedia_transport_zrtp_stopZrtp);
    PH4_DLSYM(zrtplib_transport_cb, zrtpGlueHandle, pjmedia_transport_zrtp_setLocalSSRC);
    PH4_DLSYM(zrtplib_transport_cb, zrtpGlueHandle, pjmedia_transport_zrtp_isMitmMode);
    PH4_DLSYM(zrtplib_transport_cb, zrtpGlueHandle, pjmedia_transport_zrtp_setMitmMode);
    PH4_DLSYM(zrtplib_transport_cb, zrtpGlueHandle, pjmedia_transport_zrtp_getZrtpContext);
    PH4_DLSYM(zrtplib_transport_cb, zrtpGlueHandle, pjmedia_transport_zrtp_getSlaveTransport);

    return 0;
ON_LIB_LOAD_FAIL:
    jzrtp_zrtp_unloadlib();
	return 0x20;
}

PJ_DECL(int) jzrtp_zrtp_unloadlib(){
    // Reset callback memory
    memset(&zrtplib_transport_cb, 0x0, sizeof(zrtplib_transport_cb));
    memset(&zrtplib_cb, 0x0, sizeof(zrtplib_cb));
#ifdef ZRTP_DYNAMIC_LINKING
    // If handle is non-null, unload handles.
    if (zrtpCppHandle!=NULL){
        dlclose(zrtpCppHandle);
        zrtpCppHandle=NULL;
    }

    if (zrtpGlueHandle!=NULL){
        dlclose(zrtpGlueHandle);
        zrtpGlueHandle=NULL;
    }
#endif
    return 0;
}

/**
 * Returns 1 if the ZRTP linking is correctly loaded.
 * If ZRTP is linked statically, always returns 1.
 */
static int isLinkingValid(){
#ifndef ZRTP_DYNAMIC_LINKING
    return 1;
#else
    return zrtpCppHandle!=NULL && zrtpGlueHandle!=NULL;
#endif
}

static void zrtpShowSas(void* data, char* sas, int verified){
	zrtp_cb_user_data* zrtp_data = (zrtp_cb_user_data*) data;
	PJ_LOG(4, (THIS_FILE, "Show sas : %s in ctxt %x", sas, zrtp_data));
	pj_strdup2_with_null(zrtp_data->pool, &zrtp_data->sas, sas);
	zrtp_data->sas_verified = verified;
	on_zrtp_show_sas_wrapper(zrtp_data->call_id, sas, verified);
}

static void zrtpSecureOn(void* data, char* cipher){
	zrtp_cb_user_data* zrtp_data = (zrtp_cb_user_data*) data;
	pj_strdup2_with_null(zrtp_data->pool, &zrtp_data->cipher, cipher);

	on_zrtp_update_transport_wrapper(zrtp_data->call_id);
        
    // Play ZRTP OK sound.
    play_zrtp_ok_sound();

    // Notify upper layer.
    on_zrtp_secure_state_wrapper(zrtp_data->call_id, 1);
}

static void zrtpSecureOff(void* data){
	zrtp_cb_user_data* zrtp_data = (zrtp_cb_user_data*) data;
	on_zrtp_update_transport_wrapper(zrtp_data->call_id);
    on_zrtp_secure_state_wrapper(zrtp_data->call_id, 0);
}

static void confirmGoClear(void* data)
{
    PJ_LOG(3,(THIS_FILE, "GoClear?"));
    zrtp_cb_user_data* zrtp_data = (zrtp_cb_user_data*) data;
    on_zrtp_secure_state_wrapper(zrtp_data->call_id, 0);
}

static void showMessage(void* data, int32_t sev, int32_t subCode)
{
	zrtp_cb_user_data* zrtp_data = (zrtp_cb_user_data*) data;
	
	// propagate to UI
	on_zrtp_update_state_wrapper(zrtp_data->call_id, HCODE_MESSAGE, 0, sev, subCode);
    switch (sev)
    {
    case zrtp_Info:
        PJ_LOG(3,(THIS_FILE, "ZRTP info message: %s", InfoCodes[subCode]));
        if(subCode == zrtp_InfoSecureStateOn
        		|| subCode == zrtp_InfoSecureStateOff){
        	if(zrtp_data != NULL){
        		on_zrtp_update_transport_wrapper(zrtp_data->call_id);
        	}else{
        		PJ_LOG(1, (THIS_FILE, "Got a message without associated call_id"));
        	}
        }
        break;

    case zrtp_Warning:
        PJ_LOG(3,(THIS_FILE, "ZRTP warning message: %s", WarningCodes[subCode]));
        break;

    case zrtp_Severe:
        PJ_LOG(3,(THIS_FILE, "ZRTP severe message: %s", SevereCodes[subCode]));
        break;

    case zrtp_ZrtpError:
        PJ_LOG(1,(THIS_FILE, "ZRTP Error: subcode: %" PRId32, subCode));
        break;
    }
}

static void zrtpNegotiationFailed(void* data, int32_t severity, int32_t subCode)
{
    PJ_LOG(3,(THIS_FILE, "ZRTP negotiation failed: %" PRId32 ", subcode: %" PRId32, severity, subCode));
}

static void zrtpNotSuppOther(void* data)
{
    PJ_LOG(3,(THIS_FILE, "ZRTP not supported by other peer"));

    // TODO: send info to UI
    // propagate to UI
    zrtp_cb_user_data* zrtp_data = (zrtp_cb_user_data*) data;
    on_zrtp_update_state_wrapper(zrtp_data->call_id, HCODE_NOT_SUPPORTED, 0, 0, 0);
    on_zrtp_update_transport_wrapper(zrtp_data->call_id);
}
static void zrtpAskEnrollment(void* data, int32_t info)
{
    PJ_LOG(3,(THIS_FILE, "ZRTP - Ask PBX enrollment"));
}

static void zrtpInformEnrollment(void* data, int32_t info)
{
    PJ_LOG(3,(THIS_FILE, "ZRTP - Inform PBX enrollement"));
}

static void signSAS(void* data, uint8_t* sas)
{
    PJ_LOG(3,(THIS_FILE, "ZRTP - sign SAS"));
}

static int32_t checkSASSignature(void* data, uint8_t* sas)
{
    PJ_LOG(3,(THIS_FILE, "ZRTP - check SAS signature"));
    return 0;
}

static int32_t checkZrtpHashMatch(void* data, int32_t matchResult)
{
	zrtp_cb_user_data* zrtp_data = (zrtp_cb_user_data*) data;
    PJ_LOG(3,(THIS_FILE, "ZRTP - check ZRTP hash match [%d]", matchResult));
    zrtp_data->zrtp_hash_match = matchResult;

    on_zrtp_hash_match_wrapper(zrtp_data->call_id, matchResult);
    return 0;
}

static void transportDestroy(void * data)
{
    zrtp_cb_user_data* zrtp_data = (zrtp_cb_user_data*) data;
    PJ_LOG(3,(THIS_FILE, "ZRTP transport destroyed %p", data));
    if (data == NULL){
        return;
    }

    // Release pool allocated for ZRTP transport.
    if (zrtp_data->pool != NULL) {
        pj_pool_release(zrtp_data->pool);
        zrtp_data->pool = NULL;
    }

    // Unset callback, now is unallocated.
    if (zrtp_data->zrtp_tp != NULL) {
        PH4_INVOKE(zrtplib_transport_cb, pjmedia_transport_zrtp_setUserCallback, (zrtp_data->zrtp_tp, NULL));
    }
}

/* Initialize the ZRTP transport and the user callbacks */
pjmedia_transport* on_zrtp_transport_created(pjsua_call_id call_id,
	unsigned media_idx,
	pjmedia_transport *base_tp,
	unsigned flags) {
        pjsua_call *call;
		pjmedia_transport *zrtp_tp = NULL;
		pj_status_t status;
		pjmedia_endpt* endpt = pjsua_get_pjmedia_endpt();

		// For now, do zrtp only on audio stream
        call = &pjsua_var.calls[call_id];
        if (media_idx < call->med_prov_cnt) {
            pjsua_call_media *call_med = &call->media_prov[media_idx];
            if (call_med->tp && call_med->type != PJMEDIA_TYPE_AUDIO) {
                PJ_LOG(2, (THIS_FILE, "ZRTP transport not yet supported for : %d", call_med->type));
                return base_tp;
            }
        }

        if (!isLinkingValid()){
            PJ_LOG(1, (THIS_FILE, "ZRTP library is not loaded. "));
            return base_tp;
        }

	    // Create zrtp transport adapter
		status = PH4_INVOKE(zrtplib_transport_cb, pjmedia_transport_zrtp_create,
		                    (endpt, NULL, base_tp, &zrtp_tp, (flags & PJSUA_MED_TP_CLOSE_MEMBER)));

        pj_pool_t * pool = NULL;
        if (status == PJ_SUCCESS){
            // Create custom ZRTP memory pool.
            pool = pjsua_pool_create("zrtpPool%p", 512, 512);
        }

        if(status == PJ_SUCCESS && pool != NULL){
			PJ_LOG(4,(THIS_FILE, "ZRTP transport created"));
            zrtp_cb_user_data* zrtp_cb_data = PJ_POOL_ZALLOC_T(pool, zrtp_cb_user_data);
            zrtp_cb_data->pool = pool;
			zrtp_cb_data->zrtp_tp = zrtp_tp;
			zrtp_cb_data->call_id = call_id;
			zrtp_cb_data->cipher = pj_str("");
			zrtp_cb_data->sas = pj_str("");
			zrtp_cb_data->sas_verified = PJ_FALSE;
			zrtp_cb_data->zrtp_hash_match = PJ_FALSE;

			// Build callback struct
			zrtp_UserCallbacks* zrtp_cbs = PJ_POOL_ZALLOC_T(pool, zrtp_UserCallbacks);
			zrtp_cbs->zrtp_secureOn = &zrtpSecureOn;
			zrtp_cbs->zrtp_secureOff = &zrtpSecureOff;
			zrtp_cbs->zrtp_showSAS = &zrtpShowSas;
			zrtp_cbs->zrtp_confirmGoClear = &confirmGoClear;
			zrtp_cbs->zrtp_showMessage = &showMessage;
			zrtp_cbs->zrtp_zrtpNegotiationFailed = &zrtpNegotiationFailed;
			zrtp_cbs->zrtp_zrtpNotSuppOther = &zrtpNotSuppOther;
			zrtp_cbs->zrtp_zrtpAskEnrollment = &zrtpAskEnrollment;
			zrtp_cbs->zrtp_zrtpInformEnrollment = &zrtpInformEnrollment;
			zrtp_cbs->zrtp_signSAS = &signSAS;
			zrtp_cbs->zrtp_checkSASSignature = &checkSASSignature;
			zrtp_cbs->zrtp_checkZrtpHashMatch = &checkZrtpHashMatch;
			zrtp_cbs->zrtp_transportDestroy = &transportDestroy;
			zrtp_cbs->userData = zrtp_cb_data;

			PH4_INVOKE(zrtplib_transport_cb, pjmedia_transport_zrtp_setUserCallback, (zrtp_tp, zrtp_cbs));


			/*
			* Initialize the transport. Just the filename of the ZID file that holds
			* our partners ZID, shared data etc. If the files does not exists it will
			* be created an initialized. The ZRTP configuration is not yet implemented
			* thus the parameter is NULL.
			*/
			PH4_INVOKE(zrtplib_transport_cb, pjmedia_transport_zrtp_initialize, (zrtp_tp, css_var.zid_file, PJ_TRUE));
			return zrtp_tp;
		} else {
			PJ_LOG(1, (THIS_FILE, "ZRTP transport problem : %d", status));
			return base_tp;
		}
}

/*
PJ_DECL(void) jzrtp_SASVerified(pjsua_call_id call_id){ 
	zrtp_cb_user_data* zrtp_data = (zrtp_cb_user_data*) zrtp_data_p;
	ZrtpContext* ctxt = pjmedia_transport_zrtp_getZrtpContext(zrtp_data->zrtp_tp);
	zrtp_SASVerified(ctxt);
}*/

PJ_DECL(int) jzrtp_getCallId(long zrtp_data_p){
	zrtp_cb_user_data* zrtp_data = (zrtp_cb_user_data*) zrtp_data_p;
	return zrtp_data->call_id;

}

pj_str_t jzrtp_getInfo(pjmedia_transport* tp){
	pj_str_t result = {0,0};
	char msg[512] = {0};

	if (!isLinkingValid()){
	    PJ_LOG(1, (THIS_FILE, "ZRTP library is not loaded. "));
	    return result;
	}

	ZrtpContext *ctx = PH4_INVOKE(zrtplib_transport_cb, pjmedia_transport_zrtp_getZrtpContext, (tp));
	int32_t state = PH4_INVOKE(zrtplib_cb, zrtp_inState, (ctx, SecureState));
	zrtp_cb_user_data* zrtp_cb_data = (zrtp_cb_user_data*) PH4_INVOKE(zrtplib_transport_cb, pjmedia_transport_zrtp_getUserData, (tp));

	if (state) {
		pj_ansi_snprintf(msg, sizeof(msg), "ZRTP - %s\n%.*s\n%.*s\nSt: %d", "OK",
				(int)zrtp_cb_data->sas.slen, zrtp_cb_data->sas.ptr,
				(int)zrtp_cb_data->cipher.slen, zrtp_cb_data->cipher.ptr,
				state);
	} else {
	    memset(msg, 0x0, sizeof(msg));
	}

	if (zrtp_cb_data != NULL && zrtp_cb_data->pool != NULL){
	    pj_strdup2_with_null(zrtp_cb_data->pool, &result, msg);
	    PJ_LOG(4, (THIS_FILE, "ZRTP getInfos : [%s], state: %d", msg, state));
	} else {
	    PJ_LOG(1, (THIS_FILE, "ZRTP getInfos Error, empty data or pool"));
	}

	return result;
}

struct jzrtp_allContext jzrtp_getContext(pjsua_call_id call_id) {

	pjsua_call *call;
	pj_status_t status;
	unsigned i;
	pjmedia_transport_info tp_info;

	struct jzrtp_allContext result;
	result.cbUserData = NULL;
	result.zrtpContext = NULL;

	//PJ_ASSERT_RETURN(call_id>=0 && call_id<(int)pjsua_var.ua_cfg.max_calls,
	//		NULL);

	if (!isLinkingValid()){
        PJ_LOG(1, (THIS_FILE, "ZRTP library is not loaded. "));
        return result;
    }

	if (pjsua_call_has_media(call_id)) {
		call = &pjsua_var.calls[call_id];
		for (i = 0; i < call->med_cnt; ++i) {
			pjsua_call_media *call_med = &call->media[i];
			if (call_med->tp && call_med->type == PJMEDIA_TYPE_AUDIO) {
				pjmedia_transport_info tp_info;

				pjmedia_transport_info_init(&tp_info);
				pjmedia_transport_get_info(call_med->tp, &tp_info);
				if (tp_info.specific_info_cnt > 0) {
					unsigned j;
					for (j = 0; j < tp_info.specific_info_cnt; ++j) {
						// just temporary ICE check
						if (tp_info.spc_info[j].type
								== PJMEDIA_TRANSPORT_TYPE_ICE){
							const pjmedia_ice_transport_info *ii;
							unsigned jj;

							ii = (const pjmedia_ice_transport_info*) tp_info.spc_info[j].buffer;
							PJ_LOG(4, (THIS_FILE, "PJMEDIA_ICE transport detected; role:[%s] state_name:[%s] comp_cnt:[%u]",
										pj_ice_sess_role_name(ii->role),
										pj_ice_strans_state_name(ii->sess_state),
										ii->comp_cnt
									));

							// just common transport_ice structure holding everything important
							// see transport_ice.c for more details
							/*struct transport_ice *tp_ice = (struct transport_ice*)tp;
							if (tp_ice==NULL){
								PJ_LOG(3, (THIS_FILE, "PJMEDIA_ICE transport structure is null"));
								continue;
							}

							// obtain defining structure for ICE transport itself
							pj_ice_strans *ice_st = tp_ice->ice_st;
							if (ice_st==NULL){
								PJ_LOG(3, (THIS_FILE, "PJMEDIA_ICE pj_ice_strans defining structure is null"));
								continue;
							}

							// callback structure - we are interested in
							pjmedia_ice_cb *ice_cb = tp_ice->cb;
							if (ice_cb==NULL){
								PJ_LOG(3, (THIS_FILE, "PJMEDIA_ICE callback structure is null"));
							}

							// obtain ICE state here
							pj_ice_strans_state ice_state = pj_ice_strans_get_state(ice_st);
							PJ_LOG(4, (THIS_FILE, "PJMEDIA_ICE state obtained = %d", ice_state));
							*/
						}


						if (tp_info.spc_info[j].type
								== PJMEDIA_TRANSPORT_TYPE_ZRTP) {
							result.zrtpContext = PH4_INVOKE(zrtplib_transport_cb, pjmedia_transport_zrtp_getZrtpContext, (call_med->tp));
							result.cbUserData = (zrtp_cb_user_data*) PH4_INVOKE(zrtplib_transport_cb, pjmedia_transport_zrtp_getUserData, (call_med->tp));
						}
					}
				}
			}
		}
	}
	return result;
}

PJ_DECL(void) jzrtp_SASVerified(pjsua_call_id call_id) {
	struct jzrtp_allContext ac = jzrtp_getContext(call_id);

	if (!isLinkingValid()){
        PJ_LOG(1, (THIS_FILE, "ZRTP library is not loaded. "));
        return;
    }

	if(ac.cbUserData != NULL){
		ac.cbUserData->sas_verified = 1;
	}

	if(ac.zrtpContext != NULL){
		PH4_INVOKE(zrtplib_cb, zrtp_SASVerified, (ac.zrtpContext));
	} else {
		PJ_LOG(1, (THIS_FILE, "jzrtp_SASVerified: No ZRTP context for call %d", call_id));
	}
}

PJ_DECL(void) jzrtp_SASRevoked(pjsua_call_id call_id) {
	struct jzrtp_allContext ac = jzrtp_getContext(call_id);

	if (!isLinkingValid()){
        PJ_LOG(1, (THIS_FILE, "ZRTP library is not loaded. "));
        return;
    }

	if(ac.cbUserData != NULL){
		ac.cbUserData->sas_verified = 0;
	}

	if(ac.zrtpContext != NULL){
		PH4_INVOKE(zrtplib_cb, zrtp_resetSASVerified, (ac.zrtpContext));
	} else {
		PJ_LOG(1, (THIS_FILE, "jzrtp_SASRevoked: No ZRTP context for call %d", call_id));
	}
}

zrtp_state_info jzrtp_getInfoFromContext(struct jzrtp_allContext ac){
	zrtp_state_info info;
	info.sas.slen = 0;
	info.sas.ptr = "";
	info.sas_verified = PJ_FALSE;
	info.cipher.slen = 0;
	info.cipher.ptr = "";
	info.secure = PJ_FALSE;
	info.call_id = PJSUA_INVALID_ID;
	info.zrtp_hash_match = PJ_FALSE;

	PJ_LOG(4, (THIS_FILE, "jzrtp_getInfoFromContext : user data %x", ac.cbUserData));
	if (!isLinkingValid()){
        PJ_LOG(1, (THIS_FILE, "ZRTP library is not loaded. "));
        return info;
    }

	if(ac.zrtpContext != NULL){
		int32_t state = PH4_INVOKE(zrtplib_cb, zrtp_inState, (ac.zrtpContext, SecureState));
		info.secure = state ? PJ_TRUE : PJ_FALSE;
		if(ac.cbUserData){
			info.sas_verified = ac.cbUserData->sas_verified;
			info.call_id = ac.cbUserData->call_id;
			info.zrtp_hash_match = ac.cbUserData->zrtp_hash_match;
			pj_strassign(&info.sas, &ac.cbUserData->sas);
			pj_strassign(&info.cipher, &ac.cbUserData->cipher);
		}

	}
	return info;
}

PJ_DECL(zrtp_state_info) jzrtp_getInfoFromCall(pjsua_call_id call_id){
	PJSUA_LOCK();
	struct jzrtp_allContext ctxt = jzrtp_getContext(call_id);
	zrtp_state_info info = jzrtp_getInfoFromContext(ctxt);
	PJSUA_UNLOCK();
	return info;
}


zrtp_state_info jzrtp_getInfoFromTransport(pjmedia_transport* tp){
	PJSUA_LOCK();
	struct jzrtp_allContext ctxt;

	if (!isLinkingValid()){
        PJ_LOG(1, (THIS_FILE, "ZRTP library is not loaded. "));
        zrtp_state_info info = {0};
        return info;
    }

	ctxt.zrtpContext = PH4_INVOKE(zrtplib_transport_cb, pjmedia_transport_zrtp_getZrtpContext, (tp));
	ctxt.cbUserData = (zrtp_cb_user_data*) PH4_INVOKE(zrtplib_transport_cb, pjmedia_transport_zrtp_getUserData, (tp));
	zrtp_state_info info =  jzrtp_getInfoFromContext(ctxt);
	PJSUA_UNLOCK();
	return info;
}

PJ_DECL(void) jzrtp_addEntropy(const char entropyBuffer[], size_t entropyBufferLen){
    if (!isLinkingValid()){
        PJ_LOG(1, (THIS_FILE, "ZRTP library is not loaded. "));
        return;
    }

	PH4_INVOKE(zrtplib_cb, zrtp_addEntropy, (entropyBuffer, entropyBufferLen));
}

PJ_DECL(pj_status_t) dump_zrtp(pjsua_call_id call_id, char *buffer, unsigned maxlen, const char *indent){
	pjsua_call *call = NULL;
	pj_status_t status = {0};
	pjmedia_transport_info tp_info;

	unsigned i=0, len=0;
	char *p = buffer, *end = buffer+maxlen;

	if (!isLinkingValid()){
        PJ_LOG(1, (THIS_FILE, "ZRTP library is not loaded. "));
        return status;
    }

	// If given call has some media objects
	if (pjsua_call_has_media(call_id)) {
		// get call structure from PJSUA
		call = &pjsua_var.calls[call_id];
		// Iterate over media channels found in call
		for (i = 0; i < call->med_cnt; ++i) {
			// {ZRTP context, ZRTP callback data - SAS} from ZRTP transport
			struct jzrtp_allContext result = {NULL, NULL};
			pjsua_call_media *call_med = &call->media[i];
			// Audio channels are interesting
			if (call_med->tp) {
				// media type string placeholder
				const char *media_type_str;
				switch (call_med->type) {
					case PJMEDIA_TYPE_AUDIO:
					    media_type_str = "audio";
					    break;
					case PJMEDIA_TYPE_VIDEO:
					    media_type_str = "video";
					    break;
					case PJMEDIA_TYPE_APPLICATION:
					    media_type_str = "application";
					    break;
					default:
					    media_type_str = "unknown";
					    break;
				}

				pjmedia_transport_info tp_info;
				pjmedia_transport_info_init(&tp_info);
				pjmedia_transport_get_info(call_med->tp, &tp_info);
				if (tp_info.specific_info_cnt > 0) {
					unsigned j;
					for (j = 0; j < tp_info.specific_info_cnt; ++j) {
						PJ_LOG(4, (THIS_FILE, "Media transport type [%d]; idx=%d ", tp_info.spc_info[j].type, j));

						//
						// ICE basic information - from dump_media
						//
						if (tp_info.spc_info[j].type == PJMEDIA_TRANSPORT_TYPE_ICE){
							unsigned jj;
							const pjmedia_ice_transport_info *ii;

							ii = (const pjmedia_ice_transport_info*) tp_info.spc_info[j].buffer;
							PJ_LOG(4, (THIS_FILE, " ICE media here comp_cnt:[%u] ", ii->comp_cnt));

							/* Print ICE detailed info */
							len = pj_ansi_snprintf(p, end-p,
										   "%s Media[med=%d;tp=%d:name=%s]\n"
										   "%s  ICE: role:[%d:%s]\n"
										   "%s    state_name:[%d:%s]\n"
										   "%s    comp_cnt:[%u] ",
										   indent, i,j,media_type_str,
										   indent, ii->role,       pj_ice_sess_role_name(ii->role),
										   indent, ii->sess_state, pj_ice_strans_state_name(ii->sess_state),
										   indent, ii->comp_cnt);
							if (len > 0 && len < end-p) {
								p += len;
								*p++ = '\n';
								*p = '\0';
							}

							/* Print ICE components */
							for (jj=0; /*ii->sess_state==PJ_ICE_STRANS_STATE_RUNNING &&*/ jj<ii->comp_cnt; ++jj) {
								const char *type1 = pj_ice_get_cand_type_name(ii->comp[jj].lcand_type);
								const char *type2 = pj_ice_get_cand_type_name(ii->comp[jj].rcand_type);
								char addr1[PJ_INET6_ADDRSTRLEN+10];
								char addr2[PJ_INET6_ADDRSTRLEN+10];

								if (pj_sockaddr_has_addr(&ii->comp[jj].lcand_addr))
									pj_sockaddr_print(&ii->comp[jj].lcand_addr, addr1, sizeof(addr1), 3);
								else
									strcpy(addr1, "0.0.0.0:0");
								if (pj_sockaddr_has_addr(&ii->comp[jj].rcand_addr))
									pj_sockaddr_print(&ii->comp[jj].rcand_addr, addr2, sizeof(addr2), 3);
								else
									strcpy(addr2, "0.0.0.0:0");
								len = pj_ansi_snprintf(p, end-p,
													   "   %s     [%d]: L:%s (%c) --> R:%s (%c) \n",
													   indent, jj,
													   addr1, type1[0],
													   addr2, type2[0]);
								if (len > 0 && len < end-p) {
									p += len;
									*p = '\0';
								}
							}
						}

						//
						// ZRTP detected in transport
						//
						if (tp_info.spc_info[j].type == PJMEDIA_TRANSPORT_TYPE_ZRTP) {
							const pjmedia_zrtp_info *ii = (const pjmedia_zrtp_info*) tp_info.spc_info[j].buffer;

							/* Print ZRTP stats */
							len = pj_ansi_snprintf(p, end-p,
										   	"\n%s Media[%s] ZRTP: secureState:[%d] "
										   	"\n%s    engineState:[%d:%s] "
										   	"\n%s    zrtpPackets:[%03" PRIu32 "]"
											"\n%s    starting:[%d]  ICE Busy:[%d]"
											"\n%s    negotiationFailed:[%02d] "
											"\n%s    notSupported:[%02d]"
											"\n%s    zrtpHashCheckPostponed:[%02d]"
											"\n%s    peerSDPZrtpHashesSeen:[%02d]"
											"\n%s    rtpUnsec: [%06" PRIu64 "]   rtpSec: [%06" PRIu64 "]"
											"\n%s    unprotect:[%06" PRIu64 "]   protect:[%06" PRIu64 "] \n",
										   	indent, media_type_str, ii->active,
										   	indent, ii->zrtpState, (ii->zrtpState < numberOfStates) ? zrtpStatesName[ii->zrtpState] : "N/A",
										   	indent, ii->zrtpCn,
										   	indent, ii->starting, ii->iceBusy,
										   	indent, ii->negotFailed,
										   	indent, ii->notSupportedCounter,
										   	indent, ii->zrtpHashCheckPostponed,
										   	indent, ii->peerSDPZrtpHashesSeen,
										   	indent, ii->rtpUnsec, ii->rtpSec,
										   	indent, ii->unprotect, ii->protect
										   );
							if (len > 0 && len < end-p) {
								p += len;
								*p++ = '\n';
								*p = '\0';
							}

							// Get ZRTP context
							if (call_med->type == PJMEDIA_TYPE_AUDIO && result.zrtpContext == NULL){
								result.zrtpContext = PH4_INVOKE(zrtplib_transport_cb, pjmedia_transport_zrtp_getZrtpContext, (call_med->tp));
								result.cbUserData = (zrtp_cb_user_data*) PH4_INVOKE(zrtplib_transport_cb, pjmedia_transport_zrtp_getUserData, (call_med->tp));
							}
						}
					}
				}
			}

			// Get ZRTP context
			if (result.zrtpContext != NULL && result.cbUserData != NULL){
				/* ZRTP cipher dump */
				if (PH4_INVOKE(zrtplib_cb, zrtp_inState, (result.zrtpContext, SecureState))) {
					pj_ansi_snprintf(p, end-p,
							"\n%s ZRTP Audio: ZRTP; \n"
							"%s   SAS=[%.*s]\n"
							"%s   Cipher=[%.*s]\n"
							"%s   hash-match: %d \n",
							indent,
							indent, (int)result.cbUserData->sas.slen, result.cbUserData->sas.ptr,
							indent, (int)result.cbUserData->cipher.slen, result.cbUserData->cipher.ptr,
							indent, result.cbUserData->zrtp_hash_match);
					if (len > 0 && len < end-p) {
						p += len;
						*p++ = '\n';
						*p = '\0';
					}
				}
			}
		}
	}

	return PJ_SUCCESS;
}

PJ_DECL(pj_str_t) jzrtp_zrtp_call_dump(pjsua_call_id call_id, const char *indent){
	pj_str_t ret = {0,0};

	pj_status_t status = dump_zrtp(call_id, zrtp_log_msg_buff, ZRTP_LOG_MSG_BUFF, indent);
	if(status != PJ_SUCCESS){
		return pj_strerror(status, zrtp_log_msg_buff, ZRTP_LOG_MSG_BUFF);
	}

    // Use singleton static buffer, not pool so memory don't get polluted by
    // log data. Thus this call is not thread safe, has to be called
    // in a serial way for each call separately.
    ret = pj_str(zrtp_log_msg_buff);
    return ret;
}

#else
PJ_DECL(void) jzrtp_SASVerified(pjsua_call_id call_id) {
	//TODO : log
	PJ_LOG(3,(THIS_FILE, "ZRTP not active 1"));
}

PJ_DECL(void) jzrtp_SASRevoked(pjsua_call_id call_id) {
	PJ_LOG(3,(THIS_FILE, "ZRTP not active 2"));
}
PJ_DECL(zrtp_state_info) jzrtp_getInfoFromCall(pjsua_call_id call_id){
    zrtp_state_info state;
    state.call_id = call_id;
    state.secure = PJ_FALSE;
    state.sas.slen = 0;
    state.cipher.slen = 0;
    state.sas_verified = PJ_FALSE;
    return state;
}
PJ_DECL(void) jzrtp_addEntropy(char *str, size_t len){
	PJ_LOG(3,(THIS_FILE, "ZRTP not active 3"));
}
PJ_DECL(pj_str_t) jzrtp_zrtp_call_dump(pjsua_call_id call_id, pj_bool_t with_media, const char *indent){
	return pj_str("");
}
#endif

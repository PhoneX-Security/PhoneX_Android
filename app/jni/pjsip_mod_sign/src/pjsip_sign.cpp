/**
 * Copyright (C) 2010 Dusan (ph4r05) Klinec
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
/**
 * This module aims to implements rfc6873
 *
 */
#include <pjsip.h>
#include <pjsip_ua.h>
#include <pjlib-util.h>
#include <pjlib.h>
#include <pjlib.h>
#include <pjsua.h>
#include <pjsua-lib/pjsua_internal.h>
#include <pj/string.h>
#include <pj/pool.h>
#include <pj/assert.h>
#include <pj/os.h>
#include <pj/log.h>
#include <pj/rand.h>
#include <pjsip/sip_parser.h>
#include <pjlib-util/scanner.h>
#include <pjlib-util/string.h>
//#include <pjlib-util/scanner_cis_uint.h>
#include "pjsip_sign.h"
#include <openssl/sha.h>

#define THIS_FILE "pjsip_sign.c"
#define TIMESTAMP_SEC_LEN 10
#define TIMESTAMP_MSEC_LEN 3

#define POOL_INIT_SIZE	2048
#define POOL_INC_SIZE	256

static SignCallback* registeredCallbackObject = NULL;

// disable static string initialization warnings
#pragma GCC diagnostic ignored "-Wwrite-strings"

extern "C" {

// for header searching
typedef enum EE_extra_hdr_t_{
	EE_SIGN_ID = 0,
	EE_SIGN_DESC_ID
} EE_extra_hdr_t;

/* Fowrard declarations */
static pj_status_t mod_sign_load(pjsip_endpoint *endpt);
static pj_status_t mod_sign_unload(void);
static void initParser();
static pjsip_hdr* parse_hdr_EE_SIGN_string(pjsip_parse_ctx *ctx);
static pjsip_hdr* parse_hdr_EE_DESC_string(pjsip_parse_ctx *ctx);
static pj_status_t sign_on_rx_msg(pjsip_rx_data *rdata);
static pj_status_t sign_on_tx_msg(pjsip_tx_data *tdata);
PJ_DECL(esign_descriptor *) pjsip_rdata_get_sigdesc( pjsip_rx_data * rdata );


/* The module instance. */
static pjsip_module pjsua_sipsign_mod =
{
    NULL, NULL,                            /* prev, next.      */
    { "mod-sign", 8 },                     /* Name.            */
    -1,                                    /* Id               */
    PJSIP_MOD_PRIORITY_TRANSPORT_LAYER+1,  /* Priority         */
    &mod_sign_load,          /* load()           */
    NULL,                    /* start()          */
    NULL,                    /* stop()           */
    &mod_sign_unload,		 /* unload()         */
    &sign_on_rx_msg,         /* on_rx_request()  */
    &sign_on_rx_msg,         /* on_rx_response() */
    &sign_on_tx_msg,         /* on_tx_request.   */
    &sign_on_tx_msg,         /* on_tx_response() */
    NULL,                    /* on_tsx_state()   */
};

void mod_sign_set_callback(SignCallback* cb)
{
    registeredCallbackObject = cb;
}

PJ_DEF(pj_status_t) init_sig_desc(struct esign_descriptor * desc){
	if (desc == NULL) return !PJ_SUCCESS;
	memset(desc, 0, sizeof(struct esign_descriptor));
	desc->endpt = NULL;
	desc->mod   = &pjsua_sipsign_mod;
	desc->pool  = NULL;

	return PJ_SUCCESS;
}

PJ_DEF(esign_descriptor *) pjsip_rdata_get_sigdesc( pjsip_rx_data * rdata ){
	if (rdata == NULL) return NULL;
	else               return (esign_descriptor *) rdata->endpt_info.mod_data[pjsua_sipsign_mod.id];
}

PJ_DEF(pj_status_t) pjsip_rdata_set_sigdesc( pjsip_rx_data * rdata, esign_descriptor * desc ){
	if (rdata == NULL) return PJ_EINVAL;
	rdata->endpt_info.mod_data[pjsua_sipsign_mod.id] = desc;
	return PJ_SUCCESS;
}

PJ_DEF(int) pjsip_rdata_get_signature( pjsip_rx_data * rdata, esign_process_info * ret ){
	esign_descriptor * desc = NULL;
	PJ_ASSERT_RETURN(rdata != NULL && ret != NULL, PJ_EINVAL);

	desc = pjsip_rdata_get_sigdesc(rdata);
	pj_bzero(ret, sizeof(*ret));

	pj_memcpy(ret, &(desc->sign_info), sizeof(esign_process_info));

	return PJ_SUCCESS;
}

/* TODO : pass a max len for the line2 buffer */
static pj_status_t sipclf_add_timestamp(pj_str_t* line2, pj_time_val ts)
{
    char* line2ptr = line2->ptr;
    line2ptr += line2->slen;

    // sec since epoch
    pj_utoa_pad(ts.sec, line2ptr, TIMESTAMP_SEC_LEN, '0');
    line2->slen += TIMESTAMP_SEC_LEN;
    line2ptr += TIMESTAMP_SEC_LEN;
    // dot
    pj_strcat2(line2, ".");
    line2ptr++;
    // ms
    pj_utoa_pad(ts.msec, line2ptr, TIMESTAMP_MSEC_LEN, '0');
    line2->slen += TIMESTAMP_MSEC_LEN;
    line2ptr += TIMESTAMP_MSEC_LEN;

    return PJ_SUCCESS;
}

static pj_status_t sipclf_add_index(pj_str_t* line1, int value)
{
    value += 61; /*the headers index length + \n*/
    pj_val_to_hex_digit( (value & 0xFF00) >> 8, line1->ptr + line1->slen);
    pj_val_to_hex_digit( (value & 0x00FF),      line1->ptr + line1->slen + 2);
    line1->slen += 4;

    return PJ_SUCCESS;
}

static pj_status_t sipclf_set_length(char* buf, int len)
{
    pj_val_to_hex_digit( (len & 0xFF0000) >> 16, buf);
    pj_val_to_hex_digit( (len & 0x00FF00) >> 8,  buf + 2);
    pj_val_to_hex_digit( (len & 0x0000FF),       buf + 4);

    return PJ_SUCCESS;
}

static pj_status_t sipclf_add_cseq(pj_str_t* line2, pjsip_msg* msg)
{
    const pjsip_cseq_hdr *cseq;
    char buf[128];
    cseq = (const pjsip_cseq_hdr*) pjsip_msg_find_hdr(msg, PJSIP_H_CSEQ, NULL);
    pj_ansi_snprintf(buf, sizeof(buf), "%d %.*s", cseq->cseq, cseq->method.name.slen, cseq->method.name.ptr);
    pj_strcat2(line2, buf);
    return PJ_SUCCESS;
}

static pj_status_t sipclf_add_status_code(pj_str_t* line2, pjsip_msg* msg)
{
    if(msg->type == PJSIP_RESPONSE_MSG) {
        char buf[128];
        pj_ansi_snprintf(buf, sizeof(buf), "%d", msg->line.status.code);
        pj_strcat2(line2, buf);
    } else {
        pj_strcat2(line2, "-");
    }
    return PJ_SUCCESS;
}

static pj_status_t sipclf_add_request_uri(pj_str_t* line2, pjsip_msg* msg)
{
    int len = 0;
    if(msg->line.req.uri != NULL ){
        len = pjsip_uri_print(PJSIP_URI_IN_REQ_URI,
                                    msg->line.req.uri,
                                    (line2->ptr + line2->slen),
                                   512);
    }
    line2->slen += len;
    if(len == 0){
        pj_strcat2(line2, "-");
    }
    return PJ_SUCCESS;
}

static pj_status_t sipclf_add_tx_destination(pj_str_t* line2, pjsip_tx_data *tdata)
{
    char buf[128];
    pj_ansi_snprintf(buf, sizeof(buf), "%s:%d",
            tdata->tp_info.dst_name,
            tdata->tp_info.dst_port);
    pj_strcat2(line2, buf);
    return PJ_SUCCESS;
}

static pj_status_t sipclf_add_tx_source(pj_str_t* line2, pjsip_tx_data *tdata)
{
    char buf[128];
    pj_sockaddr_print( &tdata->tp_info.transport->local_addr,
                     buf, sizeof(buf),
                     1);
    pj_strcat2(line2, buf);
    return PJ_SUCCESS;
}

static pj_status_t sipclf_add_rx_destination(pj_str_t* line2, pjsip_rx_data *rdata)
{
    char buf[128];
    pj_sockaddr_print( &rdata->tp_info.transport->local_addr,
                     buf, sizeof(buf),
                     1);
    pj_strcat2(line2, buf);
    return PJ_SUCCESS;
}

static pj_status_t sipclf_add_rx_source(pj_str_t* line2, pjsip_rx_data *rdata)
{
    char buf[128];
    pj_sockaddr_print( &rdata->pkt_info.src_addr,
                     buf, sizeof(buf),
                     1);
    pj_strcat2(line2, buf);
    return PJ_SUCCESS;
}

static pj_status_t get_rx_source(pj_pool_t * pool, pj_str_t * dst, pjsip_rx_data *rdata){
	char buf[128];
	int len = 0;

	if (rdata==NULL) return PJ_EINVAL;
	pj_sockaddr_print( &rdata->pkt_info.src_addr,
	                   buf, sizeof(buf),
	                   1);
	pj_strdup2_with_null(pool, dst, buf);
	return PJ_SUCCESS;
}

static pj_status_t sipclf_add_fromto_url(pj_str_t* line2, const pjsip_fromto_hdr* fromto_hdr)
{
    int len = 0;
    if(fromto_hdr != NULL) {
        /*Use req uri context that does not add the display name and brackets stuff */
        len = pjsip_uri_print(PJSIP_URI_IN_REQ_URI,
                                    fromto_hdr->uri,
                                    (line2->ptr + line2->slen),
                                   512);
    }
    line2->slen += len;
    if(len == 0) {
        pj_strcat2(line2, "-");
    }
    return PJ_SUCCESS;
}

static pj_status_t sipclf_add_fromto_tag(pj_str_t* line2, const pjsip_fromto_hdr* fromto_hdr)
{
    if(fromto_hdr != NULL && fromto_hdr->tag.slen > 0) {
        pj_strcat(line2, &fromto_hdr->tag);
    } else {
        pj_strcat2(line2, "-");
    }
    return PJ_SUCCESS;
}

static pj_status_t sipclf_add_to_uri(pj_str_t* line2, pjsip_msg* msg)
{
    const pjsip_fromto_hdr *to = (const pjsip_fromto_hdr*) pjsip_msg_find_hdr(msg, PJSIP_H_TO, NULL);
    return sipclf_add_fromto_url(line2, to);
}

static pj_status_t sipclf_add_to_tag(pj_str_t* line2, pjsip_msg* msg)
{
    const pjsip_to_hdr *to = (const pjsip_to_hdr*) pjsip_msg_find_hdr(msg, PJSIP_H_TO, NULL);
    return sipclf_add_fromto_tag(line2, to);
}

static pj_status_t sipclf_add_from_uri(pj_str_t* line2, pjsip_msg* msg)
{
    const pjsip_to_hdr *from = (const pjsip_to_hdr*) pjsip_msg_find_hdr(msg, PJSIP_H_FROM, NULL);
    return sipclf_add_fromto_url(line2, from);
}

static pj_status_t sipclf_add_from_tag(pj_str_t* line2, pjsip_msg* msg)
{
    const pjsip_from_hdr *from = (const pjsip_from_hdr*) pjsip_msg_find_hdr(msg, PJSIP_H_FROM, NULL);
    return sipclf_add_fromto_tag(line2, from);
}

static pj_status_t sipclf_add_call_id(pj_str_t* line2, pjsip_msg* msg)
{
    const pjsip_call_info_hdr *call_id = (const pjsip_call_info_hdr*) pjsip_msg_find_hdr(msg, PJSIP_H_CALL_ID, NULL);
    if(call_id != NULL && call_id->hvalue.slen > 0) {
        pj_strcat(line2, &call_id->hvalue);
    } else {
        pj_strcat2(line2, "-");
    }
    return PJ_SUCCESS;
}

// =======================================================================================================================
// My methods - obtains wanted information and returns it as pj_str_t
//   NOTE: returned pj_str_t are NULL terminated

static pj_status_t sipclf_get_cseq(pj_pool_t * pool, pj_str_t * dst, pjsip_msg* msg)
{
    const pjsip_cseq_hdr *cseq;
    char buf[128];
    int strLen=0;

    pj_assert(pool != NULL && dst!=NULL && msg!=NULL);
    cseq = (const pjsip_cseq_hdr*) pjsip_msg_find_hdr(msg, PJSIP_H_CSEQ, NULL);
    if (cseq==NULL) return !PJ_SUCCESS;

    strLen = pj_ansi_snprintf(buf, sizeof(buf), "%d|%.*s", cseq->cseq, cseq->method.name.slen, cseq->method.name.ptr);
    pj_strdup2_with_null(pool, dst, buf); // create destination string with exact size allocated from pool
    return PJ_SUCCESS;
}

static pj_status_t sipclf_get_request_uri(pj_pool_t * pool, pj_str_t * dst, pjsip_msg* msg)
{
    int len = 0;
    char buf[256] = {0};

    pj_assert(pool != NULL && dst!=NULL && msg!=NULL);
    if(msg->line.req.uri != NULL && msg->line.req.uri->vptr != NULL && msg->line.req.uri->vptr->p_print != NULL){
        len = pjsip_uri_print(PJSIP_URI_IN_REQ_URI, msg->line.req.uri, buf, 256);
    }

    pj_strdup2_with_null(pool, dst, buf);
    return PJ_SUCCESS;
}

static pj_status_t sipclf_get_fromto_url(pj_pool_t * pool, pj_str_t * dst, const pjsip_fromto_hdr* fromto_hdr)
{
    int len = 0;
    char buf[256] = {0};

    pj_assert(pool != NULL && dst != NULL);
    if(fromto_hdr != NULL && fromto_hdr->uri != NULL && fromto_hdr->uri->vptr != NULL && fromto_hdr->uri->vptr->p_print != NULL) {
        /*Use req uri context that does not add the display name and brackets stuff */
        len = pjsip_uri_print(PJSIP_URI_IN_REQ_URI, fromto_hdr->uri, buf, 256);
    }

    pj_strdup2_with_null(pool, dst, buf);
    return PJ_SUCCESS;
}

static pj_status_t sipclf_get_to_uri(pj_pool_t * pool, pj_str_t * dst, pjsip_msg* msg)
{
    const pjsip_fromto_hdr *to = (const pjsip_fromto_hdr*) pjsip_msg_find_hdr(msg, PJSIP_H_TO, NULL);
    return sipclf_get_fromto_url(pool, dst, to);
}

static pj_status_t sipclf_get_from_uri(pj_pool_t * pool, pj_str_t * dst, pjsip_msg* msg)
{
    const pjsip_fromto_hdr *from = (const pjsip_fromto_hdr*) pjsip_msg_find_hdr(msg, PJSIP_H_FROM, NULL);
    return sipclf_get_fromto_url(pool, dst, from);
}



/* Extracts body from message if any */
static pj_status_t sipclf_get_body(pj_pool_t * pool, pj_str_t * dst, pjsip_msg* msg)
{
    int len = 0;
    char buf[4096] = {0};

    pj_assert(pool != NULL && dst!=NULL && msg!=NULL);
    if (msg->body==NULL || msg->body->print_body == NULL) return PJ_SUCCESS;
    msg->body->print_body(msg->body, buf, 4096);

    pj_strdup2_with_null(pool, dst, buf);
    return PJ_SUCCESS;
}

/* Finds generic string header in message */
static pjsip_generic_string_hdr * sign_get_eehdr(pjsip_msg* msg, EE_extra_hdr_t htype, const void * start){
	pj_assert(msg!=NULL);

	// try to get EESIGN header if present
	pj_str_t hname   = htype==EE_SIGN_ID ? pj_str(EESIGN_HDR) : pj_str(EESIGN_DESC_HDR);
	PJ_LOG(4, (THIS_FILE, "Looking for header [%.*s]", hname.slen, hname.ptr));
	return (pjsip_generic_string_hdr *) pjsip_msg_find_hdr_by_name(msg, &hname, start);
}

/* Sets EE hdr to the message, if does not exist new is added otherwise existing is updated */
static pj_status_t sign_set_eehdr(pjsip_tx_data *tdata, EE_extra_hdr_t htype, const char * hval){
	// at first try to determine whether we saw this message before
	pjsip_generic_string_hdr * eeHdr = sign_get_eehdr(tdata->msg, htype, NULL);
	const char * eeHdrName           = htype == EE_SIGN_ID ? EESIGN_HDR : EESIGN_DESC_HDR;

	// hvalue must be allocated on TX data pool in both cases
	pj_str_t hvalue = {0,0};
	pj_strdup2(tdata->pool, &hvalue, hval);

	if (eeHdr==NULL){
		// not found, add new header
		PJ_LOG(4, (THIS_FILE, "EESIGN_%d was not found; adding new", htype));

		// Create whole message header
		// Warning: allocate from message pool!
		pjsip_generic_string_hdr * my_hdr = PJ_POOL_ZALLOC_T(tdata->pool, pjsip_generic_string_hdr);
		pj_str_t hname  = {0,0};

		pj_strdup2(tdata->pool, &hname, eeHdrName);

		pjsip_generic_string_hdr_init2(my_hdr, &hname, &hvalue);
		pj_list_push_back(&(tdata->msg->hdr), my_hdr);

		pjsip_tx_data_invalidate_msg(tdata);  // invalidate message -> it will be re-printed to buffer from data
	} else {
		// header was found, change its value
		PJ_LOG(4, (THIS_FILE, "EESIGN_%d was found; updating new; previously: [%.*s]", htype, eeHdr->hvalue.slen, eeHdr->hvalue.ptr));
		eeHdr->hvalue.ptr  = hvalue.ptr;
		eeHdr->hvalue.slen = hvalue.slen;

		pjsip_tx_data_invalidate_msg(tdata);  // invalidate message -> it will be re-printed to buffer from data
	}

	return PJ_SUCCESS;
}

/* Notification on incoming messages */
static pj_bool_t log_rx_msg(pjsip_rx_data *rdata)
{
    /* Actually first line should not be bigger than 60 bytes as per RFC def */
    char line1buf[100];
    /* TODO : Create a dedicated pool? */
    char line2buf[1024];
    pj_str_t line1 = {line1buf, 0};
    pj_str_t line2 = {line2buf, 0};

    /* Write in the first line */
    /* Version A */
    pj_strcat2(&line1, "A");
    /* Length of the record will be changed at the end */
    pj_strcat2(&line1, "000000");
    pj_strcat2(&line1, "\x2c");


    /* Start to write in the second line */
    pj_strcat2(&line2, "\x0a");
    sipclf_add_timestamp(&line2, rdata->pkt_info.timestamp);
    pj_strcat2(&line2, "\x09");

    /*  byte 1 -   R = Request /  r = Response */
    pj_strcat2(&line2,
                            (rdata->msg_info.msg->type == PJSIP_REQUEST_MSG) ? "R" : "r");

    /* byte 2 -   Retransmission Flag O = Original transmission /  D = Duplicate transmission / S = Server is stateless [i.e., retransmissions are not detected]*/
    /* TODO : implement that exactly */
    pj_strcat2(&line2, "S");

    /* byte 3 -   Sent/Received Flag S = Sent message / R = Received message */
    pj_strcat2(&line2, "R");

    /* byte 4 -   Transport Flag U = UDP /  T = TCP / S = SCTP */
    pj_strcat2(&line2,
                            (rdata->tp_info.transport->flag & PJSIP_TRANSPORT_RELIABLE) ? "T" : "U");

    /* byte 5 -   Encryption Flag E = Encrypted message (TLS, DTLS, etc.) / U = Unencrypted message */
    pj_strcat2(&line2,
                            (rdata->tp_info.transport->flag & PJSIP_TRANSPORT_SECURE) ? "E" : "U");

    pj_strcat2(&line2, "\x09");

    /* Mandatory fields */
    sipclf_add_index(&line1, line2.slen);
    sipclf_add_cseq(&line2, rdata->msg_info.msg);
    pj_strcat2(&line2, "\x09");

    sipclf_add_index(&line1, line2.slen);
    sipclf_add_status_code(&line2, rdata->msg_info.msg);
    pj_strcat2(&line2, "\x09");

    sipclf_add_index(&line1, line2.slen);
    sipclf_add_request_uri(&line2, rdata->msg_info.msg);
    pj_strcat2(&line2, "\x09");

    sipclf_add_index(&line1, line2.slen);
    sipclf_add_rx_destination(&line2, rdata);
    pj_strcat2(&line2, "\x09");

    sipclf_add_index(&line1, line2.slen);
    sipclf_add_rx_source(&line2, rdata);
    pj_strcat2(&line2, "\x09");

    sipclf_add_index(&line1, line2.slen);
    sipclf_add_to_uri(&line2, rdata->msg_info.msg);
    pj_strcat2(&line2, "\x09");

    sipclf_add_index(&line1, line2.slen);
    sipclf_add_to_tag(&line2, rdata->msg_info.msg);
    pj_strcat2(&line2, "\x09");

    sipclf_add_index(&line1, line2.slen);
    sipclf_add_from_uri(&line2, rdata->msg_info.msg);
    pj_strcat2(&line2, "\x09");

    sipclf_add_index(&line1, line2.slen);
    sipclf_add_from_tag(&line2, rdata->msg_info.msg);
    pj_strcat2(&line2, "\x09");

    sipclf_add_index(&line1, line2.slen);
    sipclf_add_call_id(&line2, rdata->msg_info.msg);
    pj_strcat2(&line2, "\x09");

    /* Server-Txn */
    {
        char buf[64];
        pj_ansi_snprintf(buf, sizeof(buf), "%p", rdata);

        sipclf_add_index(&line1, line2.slen);
        pj_strcat2(&line2, buf);
        pj_strcat2(&line2, "\x09");

        /* Client-Txn */
        sipclf_add_index(&line1, line2.slen);
        pj_strcat2(&line2, buf);
        pj_strcat2(&line2, "\x0a");
    }

    /**
     * Optionnals -- we have nothing here for now
     * TODO : add body etc
     **/
    sipclf_add_index(&line1, line2.slen);


    sipclf_set_length((line1.ptr + 1),  line1.slen + line2.slen);


    PJ_LOG(4,(THIS_FILE, "%.*s%.*s", line1.slen, line1.ptr, line2.slen, line2.ptr));

    /* Always return false, otherwise messages will not get processed! */
    return PJ_FALSE;
}

static pj_status_t log_tx_msg(pjsip_tx_data *tdata){
	if(tdata == NULL || tdata->msg == NULL){
		PJ_LOG(2, (THIS_FILE, "tdata or msg is null!"));
		return PJ_SUCCESS;
	}

	/* Actually first line should not be bigger than 60 bytes as per RFC def */
	char line1buf[100];
	char line2buf[1024];
	pj_str_t line1 = {line1buf, 0};
	pj_str_t line2 = {line2buf, 0};

	/* Write in the first line */
	/* Version A */
	pj_strcat2(&line1, "A");
	/* Length of the record will be changed at the end */
	pj_strcat2(&line1, "000000");
	pj_strcat2(&line1, "\x2c");


	/* Start to write in the second line */
	pj_strcat2(&line2, "\x0a");
	/* TODO : should use some information from tdata? */
	pj_time_val now;
	pj_gettimeofday(&now);
	sipclf_add_timestamp(&line2, now);
	pj_strcat2(&line2, "\x09");

	/*  byte 1 -   R = Request /  r = Response */
	pj_strcat2(&line2, (tdata->msg->type == PJSIP_REQUEST_MSG) ? "R" : "r");

	/* byte 2 -   Retransmission Flag O = Original transmission /  D = Duplicate transmission / S = Server is stateless [i.e., retransmissions are not detected]*/
	/* TODO : implement that exactly */
	pj_strcat2(&line2, "S");

	/* byte 3 -   Sent/Received Flag S = Sent message / R = Received message */
	pj_strcat2(&line2, "S");

	/* byte 4 -   Transport Flag U = UDP /  T = TCP / S = SCTP */
	pj_strcat2(&line2, (tdata->tp_info.transport->flag & PJSIP_TRANSPORT_RELIABLE) ? "T" : "U");

	/* byte 5 -   Encryption Flag E = Encrypted message (TLS, DTLS, etc.) / U = Unencrypted message */
	pj_strcat2(&line2, (tdata->tp_info.transport->flag & PJSIP_TRANSPORT_SECURE) ? "E" : "U");

	pj_strcat2(&line2, "\x09");

	/* Mandatory fields */
	sipclf_add_index(&line1, line2.slen);
	sipclf_add_cseq(&line2, tdata->msg);
	pj_strcat2(&line2, "\x09");

	sipclf_add_index(&line1, line2.slen);
	sipclf_add_status_code(&line2, tdata->msg);
	pj_strcat2(&line2, "\x09");

	sipclf_add_index(&line1, line2.slen);
	sipclf_add_request_uri(&line2, tdata->msg);
	pj_strcat2(&line2, "\x09");

	sipclf_add_index(&line1, line2.slen);
	sipclf_add_tx_destination(&line2, tdata);
	pj_strcat2(&line2, "\x09");

	sipclf_add_index(&line1, line2.slen);
	sipclf_add_tx_source(&line2, tdata);
	pj_strcat2(&line2, "\x09");

	sipclf_add_index(&line1, line2.slen);
	sipclf_add_to_uri(&line2, tdata->msg);
	pj_strcat2(&line2, "\x09");

	sipclf_add_index(&line1, line2.slen);
	sipclf_add_to_tag(&line2, tdata->msg);
	pj_strcat2(&line2, "\x09");

	sipclf_add_index(&line1, line2.slen);
	sipclf_add_from_uri(&line2, tdata->msg);
	pj_strcat2(&line2, "\x09");

	sipclf_add_index(&line1, line2.slen);
	sipclf_add_from_tag(&line2, tdata->msg);
	pj_strcat2(&line2, "\x09");

	sipclf_add_index(&line1, line2.slen);
	sipclf_add_call_id(&line2, tdata->msg);
	pj_strcat2(&line2, "\x09");

	/* Server-Txn */
	sipclf_add_index(&line1, line2.slen);
	pj_strcat2(&line2, tdata->obj_name);
	pj_strcat2(&line2, "\x09");

	/* Client-Txn */
	sipclf_add_index(&line1, line2.slen);
	pj_strcat2(&line2, tdata->obj_name);
	pj_strcat2(&line2, "\x0a");

	/**
	 * Optionals -- we have nothing here for now
	 * TODO : add body etc
	 **/
	sipclf_add_index(&line1, line2.slen);


	sipclf_set_length((line1.ptr + 1),  line1.slen + line2.slen);


	PJ_LOG(4,(THIS_FILE, "%.*s%.*s", line1.slen, line1.ptr, line2.slen, line2.ptr));

	return PJ_SUCCESS;
}

void bin_to_strhex(pj_pool_t * pool, unsigned char *bin, unsigned int binsz, pj_str_t * result){
  char          hex_str[]= "0123456789abcdef";
  unsigned int  i;

  result->slen = 0;
  result->ptr  = (char*) pj_pool_zalloc(pool, sizeof(char) * (binsz * 2 + 1)); // allocate with security margin
  if (result->ptr == NULL){
  		PJ_LOG(2, (THIS_FILE, "Unable to allocate new memory of size=%d from pool: [%s] capacity=%d", binsz * 2 + 1, pool->obj_name, pool->capacity));
  		return;
  }

  result->ptr[binsz * 2] = 0;
  result->slen = binsz * 2 + 1;

  if (!binsz)
    return;

  for (i = 0; i < binsz; i++){
      (result->ptr)[i * 2 + 0] = hex_str[bin[i] >> 4  ];
      (result->ptr)[i * 2 + 1] = hex_str[bin[i] & 0x0F];
  }
}

static pj_status_t get_eesign_data_from_msg(pj_pool_t * pool, pjsip_msg * msg, esignInfo_t * edat){
	// @deprecated
	//const pjsip_call_info_hdr *call_id = (const pjsip_call_info_hdr*) pjsip_msg_find_hdr(msg, PJSIP_H_CALL_ID, NULL);
	int totalSize = 0;

	// CSEQ string repr
	const pjsip_cseq_hdr *cseq = NULL;
	char buf[128] = {0};
	char buf_status[24] = {0};

	// message digest
	unsigned char md[SHA256_DIGEST_LENGTH] = {0};

	pj_assert(pool != NULL);
	pj_assert(msg  != NULL && edat != NULL);

	// set pointers & lengths to zero.
	memset(edat, 0, sizeof(esignInfo_t));

	edat->isRequest = msg->type == PJSIP_REQUEST_MSG;
	cseq = (const pjsip_cseq_hdr*) pjsip_msg_find_hdr(msg, PJSIP_H_CSEQ, NULL);
	if (cseq==NULL) return !PJ_SUCCESS;

	// CSEQ string repr
	pj_ansi_snprintf(buf, sizeof(buf), "%d|%.*s", cseq->cseq, cseq->method.name.slen, cseq->method.name.ptr);
	pj_strdup2_with_null(pool, &(edat->cseqStr), buf); // create destination string with exact size allocated from pool
	edat->cseqInt = cseq->cseq;
	pj_strdup_with_null(pool, &(edat->method), &(cseq->method.name));

	// @deprecated, used direct parsing - we are also interested in cseq number & method name separately
	//sipclf_get_cseq(pool, &(edat->cseqStr), msg);
	totalSize+=edat->cseqStr.slen+1;
	// REQ uri - only in case we have request
	if (edat->isRequest==PJ_TRUE){
		sipclf_get_request_uri(pool, &(edat->reqUriStr), msg);
		totalSize+=edat->reqUriStr.slen+1;

		edat->resp_status = 0;
	} else {
		edat->resp_status = msg->line.status.code;
	}
	// FROM uri
	sipclf_get_from_uri(pool, &(edat->fromUriStr), msg);
	totalSize+=edat->fromUriStr.slen+1;
	// TO uri
	sipclf_get_to_uri(pool, &(edat->toUriStr), msg);
	totalSize+=edat->toUriStr.slen+1;
	// BODY
	sipclf_get_body(pool, &(edat->bodyStr), msg);
	totalSize+=edat->bodyStr.slen+1;
    // Status
	totalSize+=sprintf(buf_status, "%05d", edat->resp_status)+1;

	// now build accumulated string that will be hashed and signed afterwards
	edat->accumStr.ptr = (char*) pj_pool_zalloc(pool, sizeof(char) * (totalSize+64)); // allocate with security margin
	if (edat->accumStr.ptr == NULL){
		PJ_LOG(2, (THIS_FILE, "Unable to allocate new memory of size=%d from pool: [%s] capacity=%d", totalSize+64, pool->obj_name, pool->capacity));
		return !PJ_SUCCESS;
	}

	// Update: ReqUri is probably modified by server, excluded from signature.
	// Caused real problems with INVITE.
	pj_strcat2(&(edat->accumStr), edat->isRequest==PJ_TRUE ? "Q":"S");  pj_strcat2(&(edat->accumStr), "|");
	pj_strcat2(&(edat->accumStr), buf_status);                          pj_strcat2(&(edat->accumStr), "|");
	pj_strcat (&(edat->accumStr), &(edat->cseqStr));                    pj_strcat2(&(edat->accumStr), "|");
	//pj_strcat (&(edat->accumStr), &(edat->reqUriStr));                  pj_strcat2(&(edat->accumStr), "|");
	pj_strcat (&(edat->accumStr), &(edat->fromUriStr));                 pj_strcat2(&(edat->accumStr), "|");
	pj_strcat (&(edat->accumStr), &(edat->toUriStr));                   pj_strcat2(&(edat->accumStr), "|");
	pj_strcat (&(edat->accumStr), &(edat->bodyStr));                    pj_strcat2(&(edat->accumStr), "|\0x00");

	// compute SHA256 digest
	SHA256((unsigned char *) edat->accumStr.ptr, edat->accumStr.slen, (unsigned char *) md);
	bin_to_strhex(pool, md, SHA256_DIGEST_LENGTH, &(edat->accumSha256Str));

	// hash body
	SHA256((unsigned char *) edat->bodyStr.ptr, edat->bodyStr.slen, (unsigned char *) md);
	bin_to_strhex(pool, md, SHA256_DIGEST_LENGTH, &(edat->bodySha256Str));

	// not null terminated string format sequence: %.*s
	PJ_LOG(4, (THIS_FILE, "CSEQ str:    [%s]", edat->cseqStr.ptr));
	PJ_LOG(4, (THIS_FILE, "ReqUri str:  [%s]", edat->reqUriStr.ptr));
	PJ_LOG(4, (THIS_FILE, "FromUri str: [%s]", edat->fromUriStr.ptr));
	PJ_LOG(4, (THIS_FILE, "ToUri str:   [%s]", edat->toUriStr.ptr));
	PJ_LOG(4, (THIS_FILE, "Hash256 str: [%s]", edat->accumSha256Str.ptr));
	PJ_LOG(4, (THIS_FILE, "ToHash str:  [%s]", edat->accumStr.ptr));

	return PJ_SUCCESS;
}

static pj_status_t sign_on_rx_msg(pjsip_rx_data *rdata)
{
	pjsip_method_e mthd;
	pj_status_t toReturn = PJ_SUCCESS;

	PJ_LOG(4, (THIS_FILE, "mod_sign_on_rx_msg"));
	if(rdata == NULL || rdata->msg_info.msg == NULL){
		PJ_LOG(1, (THIS_FILE, "rdata or msg is null!"));
		return PJ_SUCCESS;
	}

	mthd = rdata->msg_info.cseq->method.id;
	if (mthd == PJSIP_INVITE_METHOD || mthd==PJSIP_BYE_METHOD){
		pj_bool_t isRequest =  rdata->msg_info.msg->type == PJSIP_REQUEST_MSG;
		pj_pool_t* pool = NULL;

		PJ_LOG(4, (THIS_FILE, "INVITE or BYE method here, req: %d; objName: [%s]", isRequest, rdata->msg_info.info));
		if (isRequest==PJ_FALSE){
			// We are interested in INVITE 200 OK response, since it contains also ZRTP-HASH
			if (mthd != PJSIP_INVITE_METHOD || rdata->msg_info.msg->line.status.code!=200) return PJ_SUCCESS;
		}

		// allocate new memory pool for subsequent memory allocations
		pool = pjsua_pool_create("signPoolRX", POOL_INIT_SIZE, POOL_INC_SIZE);
		if (pool==NULL){
			PJ_LOG(1, (THIS_FILE, "Cannot create new pool! initSize=%d", POOL_INIT_SIZE));
			return PJ_SUCCESS;
		}

		// generate null descriptor
		esign_descriptor * desc = PJ_POOL_ZALLOC_T(rdata->tp_info.pool, esign_descriptor);
		pjsip_rdata_set_sigdesc(rdata, desc);
		init_sig_desc(desc);
		desc->pool = rdata->tp_info.pool;
		desc->sign_info.process_state     = ESIGN_PROCESS_STATE_PROCESSED;
		desc->sign_info.signature_present = PJ_TRUE;

		//
		// Obtain required parts of the message
		//
		esignInfo_t edat;
		get_eesign_data_from_msg(pool, rdata->msg_info.msg, &edat);

    	// Obtain IP address of message source
    	get_rx_source(pool, &(edat.ip), rdata);

		// try to get EESIGN header if present
		pj_str_t eehash = {0,0};
		pj_str_t eedesc = {0,0};
		pjsip_generic_string_hdr * eesignHdr = sign_get_eehdr(rdata->msg_info.msg, EE_SIGN_ID, NULL);
		pjsip_generic_string_hdr * eedescHdr = sign_get_eehdr(rdata->msg_info.msg, EE_SIGN_DESC_ID, NULL);

		// EE-Sign header
		if (eesignHdr==NULL){
			PJ_LOG(4, (THIS_FILE, "EESIGN was not found"));
			desc->sign_info.signature_present = PJ_FALSE;
		} else {
			pj_strdup_with_null(pool, &eehash, &(eesignHdr->hvalue));
			PJ_LOG(4, (THIS_FILE, "EESIGN was found! [%.*s];", eehash.slen, eehash.ptr));
		}

		// EE-Sign-Desc header
		if (eedescHdr==NULL){
			PJ_LOG(4, (THIS_FILE, "EESIGN-DESC was not found"));
			desc->sign_info.signature_present = PJ_FALSE;
		} else {
			pj_strdup_with_null(pool, &eedesc, &(eedescHdr->hvalue));
			PJ_LOG(4, (THIS_FILE, "EESIGN-DESC was found! [%.*s];", eedesc.slen, eedesc.ptr));
		}

		// DEBUG: dumping headers
		//const pjsip_hdr *hdr=rdata->msg_info.msg->hdr.next, *end=&rdata->msg_info.msg->hdr;
		//for (; hdr!=end; hdr = hdr->next) {
		//	PJ_LOG(4, (THIS_FILE, "hdrDump [%.*s];", hdr->name.slen, hdr->name.ptr));
		//}

		// pass analyzed data to callback object to verify signature
		if (registeredCallbackObject!=NULL){
			int errCode = registeredCallbackObject->verifySign(&edat, eehash.ptr, eedesc.ptr);
			int errCodeClean                  = errCode & (~EESIGN_FLAG_DROP_PACKET);
			desc->sign_info.signature_valid   = errCode == 0 ? PJ_TRUE : PJ_FALSE;
			desc->sign_info.callback_return   = errCode;
			desc->sign_info.cseq_int          = edat.cseqInt;
			desc->sign_info.is_request        = edat.isRequest;
			desc->sign_info.status_code       = edat.resp_status;
			desc->sign_info.packet_dropped    = (errCode & EESIGN_FLAG_DROP_PACKET) > 0;
			desc->sign_info.verify_err        = (errCodeClean >=0 && errCodeClean < ESIGN_SIGN_ERR_MAX) ? (esign_sign_err_e) errCodeClean : ESIGN_SIGN_ERR_GENERIC;

			// Copy interesting parts of the signature & hash
			// to RX data, using long term pool.
			pj_strdup_with_null(desc->pool, &(desc->sign_info.body_sha_256_str),  &(edat.bodySha256Str));
			pj_strdup_with_null(desc->pool, &(desc->sign_info.accum_sha_256_str), &(edat.accumSha256Str));
			pj_strdup_with_null(desc->pool, &(desc->sign_info.sign),              &(eehash));
			pj_strdup_with_null(desc->pool, &(desc->sign_info.sign_desc),         &(eedesc));
			pj_strdup_with_null(desc->pool, &(desc->sign_info.method),            &(edat.method));
			pj_strdup_with_null(desc->pool, &(desc->sign_info.from_uri_str),      &(edat.fromUriStr));
			pj_strdup_with_null(desc->pool, &(desc->sign_info.to_uri_str),        &(edat.toUriStr));
			pj_strdup_with_null(desc->pool, &(desc->sign_info.req_uri_str),       &(edat.reqUriStr));

			// if errcode & EESIGN_FLAG_DROP_PACKET => drop packet
			PJ_LOG(4, (THIS_FILE, "Signature verification result: [%d]", errCode));
			if ((errCode & EESIGN_FLAG_DROP_PACKET) > 0) toReturn = PJ_TRUE;
		} else {
			PJ_LOG(2, (THIS_FILE, "No callback object registered"));
		}

on_error:
		pj_pool_release(pool);
		return toReturn; /* Always return success, otherwise message will not get sent! */
	} else {
		return toReturn ;
	}

	return toReturn;
}

/* Notification on outgoing messages */
static pj_status_t sign_on_tx_msg(pjsip_tx_data *tdata)
{
    pjsip_method_e mthd;
    pj_bool_t isRequest;

    PJ_LOG(4, (THIS_FILE, "mod_sign_on_tx_msg"));
    if(tdata == NULL || tdata->msg == NULL){
    	PJ_LOG(1, (THIS_FILE, "tdata or msg is null!"));
    	return PJ_SUCCESS;
    }

    // real method can be determined from CSEQ, if it is response -> we have status message and line.req is not valid
    isRequest = tdata->msg->type == PJSIP_REQUEST_MSG;
    if (isRequest){
    	mthd = tdata->msg->line.req.method.id;
    } else {
    	if (tdata->msg->line.status.code!=200) return PJ_SUCCESS; // time saver - skip not interesting codes
    	const pjsip_cseq_hdr *cseq = cseq = (const pjsip_cseq_hdr*) pjsip_msg_find_hdr(tdata->msg, PJSIP_H_CSEQ, NULL);
    	if (cseq==NULL) return PJ_SUCCESS;	// CSEQ is missing -> not interesting
    	mthd = cseq->method.id;
    }

    // INVITE and BYE methods are interesting
    if (mthd == PJSIP_INVITE_METHOD || mthd==PJSIP_BYE_METHOD){
    	pj_pool_t* pool = NULL;
    	PJ_LOG(4, (THIS_FILE, "INVITE or BYE method here, req: %d; objName: [%s]", isRequest, tdata->obj_name));
    	if (isRequest==PJ_FALSE && mthd != PJSIP_INVITE_METHOD){
			// We are interested in INVITE 200 OK response, since it contains also ZRTP-HASH
			return PJ_SUCCESS;
		}

    	// allocate new memory pool for subsequent memory allocations
    	pool = pjsua_pool_create("signPoolTX", POOL_INIT_SIZE, POOL_INC_SIZE);
    	if (pool==NULL){
    		PJ_LOG(1, (THIS_FILE, "Cannot create new pool! initSize=%d", POOL_INIT_SIZE));
    		return PJ_SUCCESS;
    	}

    	//
    	// Obtain required parts of the message
    	//
    	esignInfo_t edat;
    	get_eesign_data_from_msg(pool, tdata->msg, &edat);

    	// allocate return string hash
    	pj_str_t hash2append = {0,0};
    	pj_str_t desc2append = {0,0};
    	hash2append.ptr = (char*) pj_pool_zalloc(pool, sizeof(char) * 128);
    	desc2append.ptr = (char*) pj_pool_zalloc(pool, sizeof(char) * 128);

    	if (registeredCallbackObject!=NULL){
    		hashReturn_t hret = {0,0,{hash2append.ptr,0},{desc2append.ptr,0}};

    		pj_status_t hashStatus = registeredCallbackObject->sign(&edat, &hret);

    		// hash should be returned here, null terminated
    		PJ_LOG(4, (THIS_FILE, "Returned hash [%d] to append to message (signature): [%s] len=%d", hashStatus, hret.hash.ptr, hret.hash.slen));
    		if (hashStatus == PJ_SUCCESS){
    			if (hret.hash.ptr != NULL && hret.hash.slen>0){
    				sign_set_eehdr(tdata, EE_SIGN_ID, hret.hash.ptr);
    				PJ_LOG(4, (THIS_FILE, "EESIGN header added to the message [%.*s]", hret.hash.slen, hret.hash.ptr));
    			}

    			if (hret.desc.ptr != NULL && hret.desc.slen>0){
					sign_set_eehdr(tdata, EE_SIGN_DESC_ID, hret.desc.ptr);
					PJ_LOG(4, (THIS_FILE, "EESIGN-DESC header added to the message [%.*s]", hret.desc.slen, hret.desc.ptr));
				}
    		}

    	} else {
    		PJ_LOG(2, (THIS_FILE, "No callback object registered!"));
    	}

on_error:
		pj_pool_release(pool);
		return PJ_SUCCESS; /* Always return success, otherwise message will not get sent! */
    } else {
    	return PJ_SUCCESS;
    }
}

/**
 * Module load()
 */
static pj_status_t mod_sign_load(pjsip_endpoint *endpt)
{
    // Tell to parser that we can handle EESIGN header now
    // pconst is not fully initialized, init NOT_NEWLINE now
    initParser();

    pj_status_t regSign = pjsip_register_hdr_parser(EESIGN_HDR,      NULL, &parse_hdr_EE_SIGN_string);
    pj_status_t regDesc = pjsip_register_hdr_parser(EESIGN_DESC_HDR, NULL, &parse_hdr_EE_DESC_string);
    if (regSign!=PJ_SUCCESS){
    	PJ_LOG(1, (THIS_FILE, "Cannot register parsing function for [%s]", EESIGN_HDR));
    }

    if (regDesc!=PJ_SUCCESS){
    	PJ_LOG(1, (THIS_FILE, "Cannot register parsing function for [%s]", EESIGN_DESC_HDR));
    }

    return PJ_SUCCESS;
}

/**
 * Module unload()
 */
static pj_status_t mod_sign_unload(void)
{
	// unregister EEsign parser
	//pjsip_unregister_hdr_parser(EESIGN_HDR, NULL, &parse_hdr_generic_string);

    return PJ_SUCCESS;
}

/**
 * Module init
 */
PJ_DECL(pj_status_t) mod_sign_init() {
    return pjsip_endpt_register_module(pjsua_get_pjsip_endpt(), &pjsua_sipsign_mod);
}

// =======================================================================================================
// Code below this line was taken from sip_parser.c, for parsing new EESIGN headers for incoming messages
// =======================================================================================================

/* Parser constants - taken from sip_parser.c */
#define IS_NEWLINE(c)	((c)=='\r' || (c)=='\n')
#define IS_SPACE(c)	((c)==' ' || (c)=='\t')
static void parse_hdr_end( pj_scanner *scanner );
static void parse_generic_string_hdr( pjsip_generic_string_hdr *hdr, pjsip_parse_ctx *ctx);
static pjsip_parser_const_t pconst =
{
    { "user", 4},	/* pjsip_USER_STR	*/
    { "method", 6},	/* pjsip_METHOD_STR	*/
    { "transport", 9},	/* pjsip_TRANSPORT_STR	*/
    { "maddr", 5 },	/* pjsip_MADDR_STR	*/
    { "lr", 2 },	/* pjsip_LR_STR		*/
    { "sip", 3 },	/* pjsip_SIP_STR	*/
    { "sips", 4 },	/* pjsip_SIPS_STR	*/
    { "tel", 3 },	/* pjsip_TEL_STR	*/
    { "branch", 6 },	/* pjsip_BRANCH_STR	*/
    { "ttl", 3 },	/* pjsip_TTL_STR	*/
    { "received", 8 },	/* pjsip_RECEIVED_STR	*/
    { "q", 1 },		/* pjsip_Q_STR		*/
    { "expires", 7 },	/* pjsip_EXPIRES_STR	*/
    { "tag", 3 },	/* pjsip_TAG_STR	*/
    { "rport", 5}	/* pjsip_RPORT_STR	*/
};

/* Character Input Specification buffer. */
static pj_cis_buf_t cis_buf;		// do not care about eclipse error, definitions are in scanner.h included header files

static void initParser(){
	pj_status_t status;
	pj_cis_buf_init(&cis_buf);

	status = pj_cis_init(&cis_buf, &pconst.pjsip_NOT_NEWLINE);
	PJ_ASSERT_RETURN(status == PJ_SUCCESS, status);
	pj_cis_add_str(&pconst.pjsip_NOT_NEWLINE, "\r\n");
	pj_cis_invert(&pconst.pjsip_NOT_NEWLINE);

	PJ_LOG(4, (THIS_FILE, "Parser initialized successfully"));
}

static pjsip_hdr* parse_hdr_EE( pjsip_parse_ctx *ctx, EE_extra_hdr_t htype)
{
    pjsip_generic_string_hdr *hdr;
    const char * eeHdrName = htype == EE_SIGN_ID ? EESIGN_HDR : EESIGN_DESC_HDR;
    pj_str_t hname         = {0,0};
    pj_strdup2(ctx->pool, &hname, eeHdrName);

    hdr = pjsip_generic_string_hdr_create(ctx->pool, &hname, NULL);
    parse_generic_string_hdr(hdr, ctx);
    return (pjsip_hdr*)hdr;
}

static pjsip_hdr* parse_hdr_EE_SIGN_string( pjsip_parse_ctx *ctx )
{
    return parse_hdr_EE(ctx, EE_SIGN_ID);
}

static pjsip_hdr* parse_hdr_EE_DESC_string( pjsip_parse_ctx *ctx )
{
    return parse_hdr_EE(ctx, EE_SIGN_DESC_ID);
}

/* Parse generic string header. */
static void parse_generic_string_hdr( pjsip_generic_string_hdr *hdr,
				      pjsip_parse_ctx *ctx)
{
    pj_scanner *scanner = ctx->scanner;
    if (hdr==NULL) PJ_LOG(2, (THIS_FILE, "header is null"));
    hdr->hvalue.slen = 0;

    /* header may be mangled hence the loop */
    while (pj_cis_match(&pconst.pjsip_NOT_NEWLINE, *scanner->curptr)) {
	pj_str_t next, tmp;

	pj_scan_get( scanner, &pconst.pjsip_NOT_NEWLINE, &hdr->hvalue);

	if (pj_scan_is_eof(scanner) || IS_NEWLINE(*scanner->curptr))
	    break;
	/* mangled, get next fraction */
	pj_scan_get( scanner, &pconst.pjsip_NOT_NEWLINE, &next);
	/* concatenate */
	tmp.ptr = (char*)pj_pool_alloc(ctx->pool, hdr->hvalue.slen + next.slen + 2);
	tmp.slen = 0;
	pj_strcpy(&tmp, &hdr->hvalue);
	pj_strcat2(&tmp, " ");
	pj_strcat(&tmp, &next);
	tmp.ptr[tmp.slen] = '\0';

	hdr->hvalue = tmp;
    }

    parse_hdr_end(scanner);
}

/* Parse ending of header. */
static void parse_hdr_end( pj_scanner *scanner )
{
    if (pj_scan_is_eof(scanner)) {
	;   /* Do nothing. */
    } else if (*scanner->curptr == '&') {
	pj_scan_get_char(scanner);
    } else {
	pj_scan_get_newline(scanner);
    }
}

}

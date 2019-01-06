/* 
 * File:   PjToneRingback.cpp
 * Author: dusanklinec
 * 
 * Created on 11. ledna 2015, 14:07
 */

#include "PjToneRingback.h"
#include <cstdlib>
#define THIS_FILE "pjToneRingback"

/* Ringtones		    US	       UK  */
#define RINGBACK_FREQ1	    440	    /* 400 */
#define RINGBACK_FREQ2	    480	    /* 450 */
#define RINGBACK_ON	        2000    /* 400 */
#define RINGBACK_OFF	    4000    /* 200 */
#define RINGBACK_CNT	    1	    /* 2   */
#define RINGBACK_INTERVAL   4000    /* 2000 */

PjToneRingback::PjToneRingback() {
}

PjToneRingback::PjToneRingback(const PjToneRingback& orig) {
}

PjToneRingback::~PjToneRingback() {
}

unsigned int PjToneRingback::tone_cnt() {
    return 1;
}

pj_bool_t PjToneRingback::tone_isLoop() {
    return PJ_TRUE;
}

std::string PjToneRingback::tone_name() {
    return "toneRingback";
}

void PjToneRingback::tone_set(pjmedia_tone_desc* tone) {
    unsigned int i;
    for (i = 0; i < RINGBACK_CNT; ++i) {
        tone[i].freq1 = RINGBACK_FREQ1;
        tone[i].freq2 = RINGBACK_FREQ2;
        tone[i].on_msec = RINGBACK_ON;
        tone[i].off_msec = RINGBACK_OFF;
    }
    tone[RINGBACK_CNT - 1].off_msec = RINGBACK_INTERVAL;    
}

void PjToneRingback::on_call_media_state(pjsua_call_id call_id, pjsua_call_info* pjsua_call_info) {
    this->tone_stop();
}

void PjToneRingback::on_call_state(pjsua_call_id call_id, pjsip_event* e, pjsua_call_info* call_info) {
    pjsua_call_info tmp_call_info;
    if (call_info == NULL){
        pjsua_call_get_info(call_id, &tmp_call_info);
        call_info = &tmp_call_info;
    }

    if (call_info->state == PJSIP_INV_STATE_DISCONNECTED) {
        /* Stop all ringback for this call */
        this->tone_stop();
        PJ_LOG(4, (THIS_FILE, "Call %d is DISCONNECTED [reason=%d (%.*s)]",
                call_id,
                call_info->last_status,
                (int) call_info->last_status_text.slen,
                call_info->last_status_text.ptr));

    } else if (call_info->state == PJSIP_INV_STATE_EARLY && e->type == PJSIP_EVENT_TSX_STATE) {
        int code;
        pj_str_t reason;
        pjsip_msg *msg;

        if (e->body.tsx_state.type == PJSIP_EVENT_RX_MSG) {
            msg = e->body.tsx_state.src.rdata->msg_info.msg;
        } else {
            msg = e->body.tsx_state.src.tdata->msg;
        }

        code = msg->line.status.code;
        reason = msg->line.status.reason;

        /* Start ringback for 180 for UAC unless there's SDP in 180 */
        if (call_info->role == PJSIP_ROLE_UAC && code == 180
                && msg->body == NULL
                && call_info->media_status == PJSUA_CALL_MEDIA_NONE)
        {
            this->tone_start();
        }

        PJ_LOG(4, (THIS_FILE, "Call %d state changed to %.*s (%d %.*s)", call_id,
                (int)call_info->state_text.slen,
                call_info->state_text.ptr,
                code, (int)reason.slen, reason.ptr));
    } else {
        PJ_LOG(4, (THIS_FILE, "Call %d state changed to %.*s", call_id, (int)call_info->state_text.slen, call_info->state_text.ptr));
    }
}



/* 
 * File:   PjToneBusy.cpp
 * Author: dusanklinec
 * 
 * Created on 11. ledna 2015, 14:12
 */

#include "PjToneBusy.h"
#include <cstdlib>
#define THIS_FILE "pjToneBusy"

PjToneBusy::PjToneBusy() {
}

PjToneBusy::PjToneBusy(const PjToneBusy& orig) {
}

PjToneBusy::~PjToneBusy() {
}

unsigned int PjToneBusy::tone_cnt() {
    return 1;
}

pj_bool_t PjToneBusy::tone_isLoop() {
    return PJ_TRUE;
}

std::string PjToneBusy::tone_name() {
    return "toneBusy";
}

void PjToneBusy::tone_set(pjmedia_tone_desc* tone) {
    tone[0].freq1 = 480;
    tone[0].freq2 = 0;
    tone[0].on_msec = 500;
    tone[0].off_msec = 500;
}

void PjToneBusy::on_call_state(pjsua_call_id call_id, pjsip_event* e, pjsua_call_info* call_info) {
    pjsua_call_info tmp_call_info;
    if (call_info == NULL){
        pjsua_call_get_info(call_id, &tmp_call_info);
        call_info = &tmp_call_info;
    }

    const int disconnected_busy = call_info->state == PJSIP_INV_STATE_DISCONNECTED
                                   && e->type == PJSIP_EVENT_TSX_STATE
                                   && call_info->role == PJSIP_ROLE_UAC
                                   && (call_info->last_status == PJSIP_SC_BUSY_HERE
                                       || call_info->last_status == PJSIP_SC_BUSY_EVERYWHERE
                                       || call_info->last_status == PJSIP_SC_DECLINE
                                       || call_info->last_status == PJSIP_SC_GONE);

    int hangup_code = get_call_hangup_cause_int(e);
    const int disconnected_gsm_busy = call_info->state == PJSIP_INV_STATE_DISCONNECTED
                                       && e->type == PJSIP_EVENT_TSX_STATE
                                       && hangup_code == PJSIP_SC_GSM_BUSY;

    if (disconnected_busy || disconnected_gsm_busy)
    {
        PJ_LOG(4, (THIS_FILE, "Busy tone starting, hangup_code: %d, busy: %d, gsm_busy: %d",
                hangup_code, disconnected_busy, disconnected_gsm_busy));
        this->tone_start();

        // Schedule tone shutdown to 3 seconds after.
        this->tone_schedule_stop(3500);
    }
}


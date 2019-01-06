/* 
 * File:   PjToneError.cpp
 * Author: dusanklinec
 * 
 * Created on 11. ledna 2015, 14:15
 */

#include "PjToneError.h"
#include <cstdlib>
#define THIS_FILE "pjToneError"

PjToneError::PjToneError() {
}

PjToneError::PjToneError(const PjToneError& orig) {
}

PjToneError::~PjToneError() {
}

unsigned int PjToneError::tone_cnt() {
    return 1;
}

pj_bool_t PjToneError::tone_isLoop() {
    return PJ_TRUE;
}

std::string PjToneError::tone_name() {
    return "toneError";
}

void PjToneError::tone_set(pjmedia_tone_desc* tone) {
    tone[0].freq1 = 425;
    tone[0].freq2 = 0;
    tone[0].on_msec = 250;
    tone[0].off_msec = 250;   
}

void PjToneError::on_call_state(pjsua_call_id call_id, pjsip_event* e, pjsua_call_info* call_info) {
     pjsua_call_info tmp_call_info;
    if (call_info == NULL){
        pjsua_call_get_info(call_id, &tmp_call_info);
        call_info = &tmp_call_info;
    }

    int call_status_cat = call_info->last_status / 100;
    if (e->type == PJSIP_EVENT_TSX_STATE
            && call_info->last_status != PJSIP_SC_BUSY_HERE
            && call_info->last_status != PJSIP_SC_BUSY_EVERYWHERE
            && call_info->last_status != PJSIP_SC_DECLINE
            && call_info->last_status != PJSIP_SC_GONE
            && call_info->last_status != PJSIP_SC_REQUEST_TERMINATED
            && call_info->last_status != PJSIP_SC_NOT_FOUND
            && call_info->last_status != PJSIP_SC_NOT_IMPLEMENTED
            && call_info->last_status != PJSIP_SC_GSM_BUSY
            && (call_status_cat == 4 || call_status_cat == 5 || call_status_cat == 6))
    {
        this->tone_start();
        
        // Schedule tone shutdown to 3 seconds after.
        this->tone_schedule_stop(3000);
    }
}


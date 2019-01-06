/* 
 * File:   PjTone.cpp
 * Author: dusanklinec
 * 
 * Created on 11. ledna 2015, 12:55
 */

#include "PjTone.h"
#include <cstdlib>
#define THIS_FILE "pjTone"

PjTone::PjTone() {
    _tone = NULL;
    _pool = NULL;
    _tone_cnt = 0;
    _tone_initialized = PJ_FALSE;
    _tone_on = PJ_FALSE;
    _tone_port = NULL;
    _tone_slot = PJSUA_INVALID_ID;
    _tone_duration = 0;
}

PjTone::PjTone(const PjTone& orig) {
    this->tone_destroy();
}

PjTone::~PjTone() {
    
}

pj_status_t PjTone::tone_init(pj_pool_t * pool) {
    _pool = pool;
    _tone = NULL;
    _tone_cnt = 0;
    _tone_initialized = PJ_FALSE;
    _tone_on = PJ_FALSE;
    _tone_port = NULL;
    _tone_slot = PJSUA_INVALID_ID;
    
    // Check tone sanity.
    unsigned int toneCnt = this->tone_cnt();
    if (toneCnt == 0){
        PJ_LOG(1, (THIS_FILE, "Cannot init tone with 0 tones"));
        return PJ_EINVAL;
    }
    
    // Loops
    unsigned options = this->tone_isLoop() ? PJMEDIA_TONEGEN_LOOP : 0u;

    // Copy tone name, it is not copied in tonegen_create2, just assigned.
    pj_str_t toneName = {NULL, 0};
    pj_strdup2(_pool, &toneName, this->tone_name().c_str());
    
    // Create tone port.
    pj_status_t status = pjmedia_tonegen_create2(_pool, &toneName, 16000, 1, 320, 16, options, &_tone_port);
    if (status != PJ_SUCCESS){
        PJ_LOG(1, (THIS_FILE, "Cannot create a new tone"));
        return status;
    }
    
    // Initialize timer.
    _tone_timer_heap = pjsip_endpt_get_timer_heap(pjsua_var.endpt);

    // Create a tone.
    _tone = (pjmedia_tone_desc *) pj_pool_zalloc(_pool, sizeof(pjmedia_tone_desc) * toneCnt);
    pj_bzero(_tone, sizeof(pjmedia_tone_desc) * toneCnt);
    this->tone_set(_tone);

    // Compute tone duration for auto-stop.
    _tone_duration = PjTone::compute_tone_duration(toneCnt, _tone);
    
    // If tone is a loop, this is done only once.
    if (this->tone_isLoop()){
        this->tone_init_tonePlay();
    }

    _tone_initialized = PJ_TRUE;
    PJ_LOG(4, (THIS_FILE, "Tone initialized [%s]", this->tone_name().c_str()));
    return status;

    err_play:
    err_addport:
    return status;
}

pj_status_t PjTone::tone_init_tonePlay() {
    unsigned int toneCnt = this->tone_cnt();
    unsigned options = this->tone_isLoop() ? PJMEDIA_TONEGEN_LOOP : 0u;

    // Stop at first to release resources.
    pj_status_t status = pjmedia_tonegen_stop(_tone_port);
    if (status != PJ_SUCCESS){
        PJ_LOG(1, (THIS_FILE, "Could not stop tone"));
    }

    status = pjmedia_tonegen_play(_tone_port, toneCnt, _tone, options);
    if (status != PJ_SUCCESS){
        PJ_LOG(1, (THIS_FILE, "Cannot set tone data"));
        return status;
    }
    
    if (_tone_slot == PJSUA_INVALID_ID){
        status = pjsua_conf_add_port(_pool, _tone_port, &_tone_slot);
        if (status != PJ_SUCCESS){
            PJ_LOG(1, (THIS_FILE, "Cannot add tone to conf"));
            return status;
        }
    }
    
    PJ_LOG(4, (THIS_FILE, "Tone %s play port, port=%p, slot=%d", 
            this->tone_name().c_str(), _tone_port, _tone_slot));
    
    return status;
}

void PjTone::tone_destroy() {
 if (_tone_initialized && _tone_port && _tone_slot != PJSUA_INVALID_ID) {
        PJ_LOG(4, (THIS_FILE, "Tone de-initialized [%s]", this->tone_name().c_str()));
        
        // Stop the tone of it is still playing.
        this->tone_stop();
        
        // Tone destroy logic.
        pjsua_conf_remove_port(_tone_slot);
        _tone_slot = PJSUA_INVALID_ID;
        pjmedia_port_destroy(_tone_port);
        _tone_port = NULL;
        _tone_initialized = PJ_FALSE;
        _tone_cnt = 0;
        _tone = NULL;
    }
}

pj_bool_t PjTone::tone_start() {
    if (!_tone_initialized) {
        PJ_LOG(1, (THIS_FILE, "Cannot play, tone is not initialized"));
        return PJ_FALSE;
    }

    // For loops.
    if (_tone_on){
        return PJ_FALSE;
    }
    


    _tone_on = PJ_TRUE;
    if (++_tone_cnt == 1) {
        // If tone is not a loop, it needs to be re-initialized.
        if (!this->tone_isLoop()){
            // For no-loop tones this has to be set again.
            if (this->tone_init_tonePlay() != PJ_SUCCESS){
                return PJ_FALSE;
            }
        }

        if (_tone_slot != PJSUA_INVALID_ID) {
            PJ_LOG(4,
                   (THIS_FILE, "Starting tone %s, no_snd=%d, snd_is_on=%d", this->tone_name().c_str(), pjsua_var.no_snd, pjsua_var.snd_is_on));
            pjsua_conf_connect(_tone_slot, 0);

            // Schedule tone stop when tone play is over for non-loop tones.
            if (!this->tone_isLoop() && this->tone_autoStopNoLoop()) {
                PJ_LOG(4,
                       (THIS_FILE, "Going to schedule tone auto-disconnect, time=%lu", _tone_duration));
                this->tone_schedule_stop(_tone_duration + 1000ul);
            }

            return PJ_TRUE;
        }
    }

    return PJ_FALSE;
}

pj_bool_t PjTone::tone_stop() {
    if (!_tone_initialized){
        PJ_LOG(1, (THIS_FILE, "Cannot play, tone is not initialized"));
        return PJ_FALSE;
    }
    
    if (_tone_initialized && _tone_on) {
        _tone_on = PJ_FALSE;
        if (_tone_cnt <= 0){
            PJ_LOG(0, (THIS_FILE, "Tone is in inconsistent state! cnt=%d", _tone_cnt));
        }

        if (--_tone_cnt == 0 && _tone_slot != PJSUA_INVALID_ID) {
            PJ_LOG(4, (THIS_FILE, "Stopping tone %s", this->tone_name().c_str()));
            pjsua_conf_disconnect(_tone_slot, 0);
            pjmedia_tonegen_rewind(_tone_port);
            return PJ_TRUE;
        }
    }

    return PJ_FALSE;
}

pj_status_t PjTone::tone_schedule_stop(int32_t time) {
    pj_time_val timeout;

    timeout.sec = time / 1000;
    timeout.msec = time % 1000;

    pj_timer_entry_init(&_tone_stop_timer, 0, this, &PjTone::timer_callback);
    if(_tone_timer_heap != NULL){
    	PJ_LOG(4, (THIS_FILE, "Going to schedule timer in time: %lu; heap 0x%p heapID", (unsigned long) time, _tone_timer_heap));
    	pj_timer_heap_schedule(_tone_timer_heap, &_tone_stop_timer, &timeout);
    } else {
    	PJ_LOG(2, (THIS_FILE, "Warning! _tone_timer_heap=NULL"));
    }

    return PJ_SUCCESS;
}

pj_status_t PjTone::tone_schedule_start(int32_t time) {
    pj_time_val timeout;

    timeout.sec = time / 1000;
    timeout.msec = time % 1000;

    pj_timer_entry_init(&_tone_start_timer, 0, this, &PjTone::timer_start_callback);
    if(_tone_timer_heap != NULL){
    	PJ_LOG(4, (THIS_FILE, "Going to schedule timer in time: %lu; heap 0x%p heapID", (unsigned long) time, _tone_timer_heap));
    	pj_timer_heap_schedule(_tone_timer_heap, &_tone_start_timer, &timeout);
    } else {
    	PJ_LOG(2, (THIS_FILE, "Warning! _tone_timer_heap=NULL"));
    }

    return PJ_SUCCESS;
}

pj_status_t PjTone::tone_cancel_timer(int32_t time) {
    if(_tone_timer_heap != NULL){
    	pj_timer_heap_cancel_if_active(_tone_timer_heap, &_tone_stop_timer, 0);
    }
    return PJ_SUCCESS;
}

void PjTone::timer_callback_fired(pj_timer_heap_t* ht, pj_timer_entry* e) {
    PJ_LOG(4, (THIS_FILE, "Timer fired"));
    this->tone_stop();
    PJ_UNUSED_ARG(ht);
}

void PjTone::timer_start_callback_fired(pj_timer_heap_t* ht, pj_timer_entry* e) {
    PJ_LOG(4, (THIS_FILE, "Timer fired"));
    this->tone_start();
    PJ_UNUSED_ARG(ht);
}

void PjTone::timer_callback(pj_timer_heap_t* ht, pj_timer_entry* e) {
    if (e->user_data == NULL){
        PJ_LOG(1, (THIS_FILE, "Error: timer has null object"));
        return;
    }
    
    // Static timer now calls tone timer callback instance.
    PjTone * t = static_cast<PjTone *>(e->user_data);
    t->timer_callback_fired(ht, e);
}

void PjTone::timer_start_callback(pj_timer_heap_t* ht, pj_timer_entry* e) {
    if (e->user_data == NULL){
        PJ_LOG(1, (THIS_FILE, "Error: timer has null object"));
        return;
    }
    
    // Static timer now calls tone timer callback instance.
    PjTone * t = static_cast<PjTone *>(e->user_data);
    t->timer_start_callback_fired(ht, e);
}

unsigned long PjTone::compute_tone_duration(int tone_cnt, pjmedia_tone_desc * tone){
    unsigned long duration = 0ul;
    int i;
    for(i = 0; i < tone_cnt; i++){
        duration += (unsigned long) tone[i].on_msec;
        duration += (unsigned long) tone[i].off_msec;
    }

    return duration;
}

std::string PjTone::tone_name() {
    return "test";
}

unsigned int PjTone::tone_cnt() {
    return 0;
}

pj_bool_t PjTone::tone_isLoop() {
    return PJ_TRUE;
}

pj_bool_t PjTone::tone_autoStopNoLoop(){
    return PJ_TRUE;
}

void PjTone::tone_set(pjmedia_tone_desc* tone) {

}

void PjTone::on_call_state(pjsua_call_id call_id, pjsip_event* e, pjsua_call_info* call_info) {

}

void PjTone::on_call_media_state(pjsua_call_id call_id, pjsua_call_info* pjsua_call_info) {

}

pj_status_t PjTone::on_call_media_transport_state(pjsua_call_id call_id, const pjsua_med_tp_state_info* info) {
    return PJ_SUCCESS;
}

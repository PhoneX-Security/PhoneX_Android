/* 
 * File:   PjTone.h
 * Author: dusanklinec
 *
 * Created on 11. ledna 2015, 12:55
 */

#ifndef PJTONE_H
#define	PJTONE_H

#include <pj/config_site.h>
#include <pjsua-lib/pjsua.h>
#include <pjsua-lib/pjsua_internal.h>
#include "pjsua_jni_addons.h"
#include <string>

class PjTone {
public:
    PjTone();
    PjTone(const PjTone& orig);
    virtual ~PjTone();
    
    // Tone create / destroy, start / stop.
    virtual pj_status_t tone_init(pj_pool_t * pool);
    virtual void tone_destroy();
    virtual pj_bool_t tone_start();
    virtual pj_bool_t tone_stop();
    static unsigned long compute_tone_duration(int tone_cnt, pjmedia_tone_desc * tone);
    
    // Loop timer cancellation schedule.
    virtual pj_status_t tone_schedule_stop(int32_t time);
    virtual pj_status_t tone_schedule_start(int32_t time);
    virtual pj_status_t tone_cancel_timer(int32_t time);
    static void timer_callback(pj_timer_heap_t *ht, pj_timer_entry *e);
    static void timer_start_callback(pj_timer_heap_t *ht, pj_timer_entry *e);
    
    // Callback for child. 
    virtual std::string tone_name();
    virtual unsigned int tone_cnt();
    virtual pj_bool_t tone_isLoop();
    virtual pj_bool_t tone_autoStopNoLoop();
    virtual void tone_set(pjmedia_tone_desc * tone);
    
    // Call state handlers.
    virtual void on_call_state(pjsua_call_id call_id, pjsip_event * e, pjsua_call_info * call_info);
    virtual void on_call_media_state(pjsua_call_id call_id, pjsua_call_info * pjsua_call_info);
    virtual pj_status_t on_call_media_transport_state(pjsua_call_id call_id, const pjsua_med_tp_state_info * info); 
    
protected:
    pj_pool_t      *_pool;
    int             _tone_slot;
    int             _tone_cnt;
    pjmedia_port   *_tone_port;
    pjmedia_tone_desc *  _tone;
    pj_bool_t volatile   _tone_on;
    pj_bool_t            _tone_initialized;
    unsigned long        _tone_duration;
    
    pj_pool_t           * _tone_timer_pool;
    pj_timer_heap_t     * _tone_timer_heap;
    pj_timer_entry        _tone_stop_timer; 
    pj_timer_entry        _tone_start_timer;
    
    virtual pj_status_t tone_init_tonePlay();
    
    virtual void timer_callback_fired(pj_timer_heap_t *ht, pj_timer_entry *e);
    virtual void timer_start_callback_fired(pj_timer_heap_t *ht, pj_timer_entry *e);
private:
    
    
};

#endif	/* PJTONE_H */


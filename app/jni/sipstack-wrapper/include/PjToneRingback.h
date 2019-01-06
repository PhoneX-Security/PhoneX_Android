/* 
 * File:   PjToneRingback.h
 * Author: dusanklinec
 *
 * Created on 11. ledna 2015, 14:07
 */

#ifndef PJTONERINGBACK_H
#define	PJTONERINGBACK_H

#include "PjTone.h"
#include <pj/config_site.h>
#include <pjsua-lib/pjsua.h>
#include <pjsua-lib/pjsua_internal.h>
#include <string>

class PjToneRingback : public PjTone{
public:
    PjToneRingback();
    PjToneRingback(const PjToneRingback& orig);
    virtual ~PjToneRingback();
    
    // Callback for child. 
    virtual std::string tone_name();
    virtual unsigned int tone_cnt();
    virtual pj_bool_t tone_isLoop();
    virtual void tone_set(pjmedia_tone_desc * tone);
    
    // Call state handlers.
    virtual void on_call_state(pjsua_call_id call_id, pjsip_event * e, pjsua_call_info * call_info);
    virtual void on_call_media_state(pjsua_call_id call_id, pjsua_call_info * pjsua_call_info);
  
private:

};

#endif	/* PJTONERINGBACK_H */


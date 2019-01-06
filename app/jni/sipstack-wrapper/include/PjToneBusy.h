/* 
 * File:   PjToneBusy.h
 * Author: dusanklinec
 *
 * Created on 11. ledna 2015, 14:12
 */

#ifndef PJTONEBUSY_H
#define	PJTONEBUSY_H

#include "PjTone.h"
#include <pj/config_site.h>
#include <pjsua-lib/pjsua.h>
#include <pjsua-lib/pjsua_internal.h>
#include <string>

class PjToneBusy : public PjTone{
public:
    PjToneBusy();
    PjToneBusy(const PjToneBusy& orig);
    virtual ~PjToneBusy();
    
    // Callback for child. 
    virtual std::string tone_name();
    virtual unsigned int tone_cnt();
    virtual pj_bool_t tone_isLoop();
    virtual void tone_set(pjmedia_tone_desc * tone);
    
    // Call state handlers.
    virtual void on_call_state(pjsua_call_id call_id, pjsip_event * e, pjsua_call_info * call_info);
  
private:

};

#endif	/* PJTONEBUSY_H */


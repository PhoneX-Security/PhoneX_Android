/* 
 * File:   PjToneZRTPOK.h
 * Author: dusanklinec
 *
 * Created on 11. ledna 2015, 14:16
 */

#ifndef PJTONEZRTPOK_H
#define	PJTONEZRTPOK_H

#include "PjTone.h"
#include <pj/config_site.h>
#include <pjsua-lib/pjsua.h>
#include <pjsua-lib/pjsua_internal.h>
#include <string>

class PjToneZRTPOK : public PjTone{
public:
    PjToneZRTPOK();
    PjToneZRTPOK(const PjToneZRTPOK& orig);
    virtual ~PjToneZRTPOK();
    
    // Callback for child. 
    virtual std::string tone_name();
    virtual unsigned int tone_cnt();
    virtual pj_bool_t tone_isLoop();
    virtual void tone_set(pjmedia_tone_desc * tone);
private:

};

#endif	/* PJTONEZRTPOK_H */


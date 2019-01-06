//
//  PEXPjZrtpTimer.h
//  Phonex
//
//  Created by Dusan Klinec on 09.12.14.
//  Copyright (c) 2014 PhoneX. All rights reserved.
//

#ifndef __Phonex__PEXPjZrtpTimer__
#define __Phonex__PEXPjZrtpTimer__

#include <pj/config_site.h>
#include <pjmedia/types.h>
#include <pj/timer.h>

#ifdef DYNAMIC_TIMER
#include <pj/config_site.h>
#include <pjsua-lib/pjsua.h>
#include <pjsua-lib/pjsua_internal.h>
#endif

pj_bool_t zrtp_timer_is_initialized();
pj_status_t zrtp_timer_init(pjmedia_endpt *endpt);
int zrtp_timer_add_entry(pj_timer_entry *entry, pj_time_val *delay);
int zrtp_timer_cancel_entry(pj_timer_entry *entry);
#endif /* defined(__Phonex__PEXPjZrtpTimer__) */

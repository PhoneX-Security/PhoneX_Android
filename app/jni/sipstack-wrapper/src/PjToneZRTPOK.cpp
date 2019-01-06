/* 
 * File:   PjToneZRTPOK.cpp
 * Author: dusanklinec
 * 
 * Created on 11. ledna 2015, 14:16
 */

#include "PjToneZRTPOK.h"
#include <cstdlib>
#define THIS_FILE "pjToneZRTPOK"

PjToneZRTPOK::PjToneZRTPOK() {
}

PjToneZRTPOK::PjToneZRTPOK(const PjToneZRTPOK& orig) {
}

PjToneZRTPOK::~PjToneZRTPOK() {
}

unsigned int PjToneZRTPOK::tone_cnt() {
    return 2;
}

pj_bool_t PjToneZRTPOK::tone_isLoop() {
    return PJ_FALSE;
}

std::string PjToneZRTPOK::tone_name() {
    return "toneZRTPOK";
}

void PjToneZRTPOK::tone_set(pjmedia_tone_desc* tone) {
    tone[0].freq1 = 800;
    tone[0].freq2 = 0;
    tone[0].on_msec = 100;
    tone[0].off_msec = 100;

    tone[1].freq1 = 800;
    tone[1].freq2 = 0;
    tone[1].on_msec = 100;
    tone[1].off_msec = 100;
}

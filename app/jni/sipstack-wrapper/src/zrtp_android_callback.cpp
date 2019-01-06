/**
 * Copyright (C) 2010 Regis Montoya (aka r3gis - www.r3gis.fr)
 * This file is part of pjsip_android.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "zrtp_android_callback.h"

static ZrtpCallback* registeredCallbackObject = NULL;


extern "C" {

void on_zrtp_show_sas_wrapper(pjsua_call_id call_id, char* sas, int verified){
    pj_str_t sas_string = pj_str(sas);
    registeredCallbackObject->on_zrtp_show_sas(call_id, &sas_string, verified);
}

void on_zrtp_update_transport_wrapper(pjsua_call_id call_id){
    registeredCallbackObject->on_zrtp_update_transport(call_id);
}

void on_zrtp_update_state_wrapper(pjsua_call_id call_id, int hcode, int zrtp_state, int sev, int subcode) {
  registeredCallbackObject->on_zrtp_update_state(call_id, hcode, zrtp_state, sev, subcode);
}

void on_zrtp_hash_match_wrapper(pjsua_call_id call_id, int matchResult) {
  registeredCallbackObject->on_zrtp_hash_match(call_id, matchResult);
}

void on_zrtp_secure_state_wrapper(pjsua_call_id call_id, int secure_state_on){
  registeredCallbackObject->on_zrtp_secure_state (call_id, secure_state_on);
}

void setZrtpCallbackObject(ZrtpCallback* callback) {
    registeredCallbackObject = callback;
}

}

/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 2.0.12
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package net.phonex.xv;

public class pjsua_buddy_info {
  private long swigCPtr;
  protected boolean swigCMemOwn;

  protected pjsua_buddy_info(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(pjsua_buddy_info obj) {
    return (obj == null) ? 0 : obj.swigCPtr;
  }

  protected void finalize() {
    delete();
  }

  public synchronized void delete() {
    if (swigCPtr != 0) {
      if (swigCMemOwn) {
        swigCMemOwn = false;
        XviJNI.delete_pjsua_buddy_info(swigCPtr);
      }
      swigCPtr = 0;
    }
  }

  public void setId(int value) {
    XviJNI.pjsua_buddy_info_id_set(swigCPtr, this, value);
  }

  public int getId() {
    return XviJNI.pjsua_buddy_info_id_get(swigCPtr, this);
  }

  public void setUri(pj_str_t value) {
    XviJNI.pjsua_buddy_info_uri_set(swigCPtr, this, pj_str_t.getCPtr(value), value);
  }

  public pj_str_t getUri() {
    long cPtr = XviJNI.pjsua_buddy_info_uri_get(swigCPtr, this);
    return (cPtr == 0) ? null : new pj_str_t(cPtr, false);
  }

  public void setContact(pj_str_t value) {
    XviJNI.pjsua_buddy_info_contact_set(swigCPtr, this, pj_str_t.getCPtr(value), value);
  }

  public pj_str_t getContact() {
    long cPtr = XviJNI.pjsua_buddy_info_contact_get(swigCPtr, this);
    return (cPtr == 0) ? null : new pj_str_t(cPtr, false);
  }

  public void setStatus(pjsua_buddy_status value) {
    XviJNI.pjsua_buddy_info_status_set(swigCPtr, this, value.swigValue());
  }

  public pjsua_buddy_status getStatus() {
    return pjsua_buddy_status.swigToEnum(XviJNI.pjsua_buddy_info_status_get(swigCPtr, this));
  }

  public void setStatus_text(pj_str_t value) {
    XviJNI.pjsua_buddy_info_status_text_set(swigCPtr, this, pj_str_t.getCPtr(value), value);
  }

  public pj_str_t getStatus_text() {
    long cPtr = XviJNI.pjsua_buddy_info_status_text_get(swigCPtr, this);
    return (cPtr == 0) ? null : new pj_str_t(cPtr, false);
  }

  public void setMonitor_pres(int value) {
    XviJNI.pjsua_buddy_info_monitor_pres_set(swigCPtr, this, value);
  }

  public int getMonitor_pres() {
    return XviJNI.pjsua_buddy_info_monitor_pres_get(swigCPtr, this);
  }

  public void setSub_state(SWIGTYPE_p_pjsip_evsub_state value) {
    XviJNI.pjsua_buddy_info_sub_state_set(swigCPtr, this, SWIGTYPE_p_pjsip_evsub_state.getCPtr(value));
  }

  public SWIGTYPE_p_pjsip_evsub_state getSub_state() {
    return new SWIGTYPE_p_pjsip_evsub_state(XviJNI.pjsua_buddy_info_sub_state_get(swigCPtr, this), true);
  }

  public void setSub_state_name(String value) {
    XviJNI.pjsua_buddy_info_sub_state_name_set(swigCPtr, this, value);
  }

  public String getSub_state_name() {
    return XviJNI.pjsua_buddy_info_sub_state_name_get(swigCPtr, this);
  }

  public void setSub_term_code(long value) {
    XviJNI.pjsua_buddy_info_sub_term_code_set(swigCPtr, this, value);
  }

  public long getSub_term_code() {
    return XviJNI.pjsua_buddy_info_sub_term_code_get(swigCPtr, this);
  }

  public void setSub_term_reason(pj_str_t value) {
    XviJNI.pjsua_buddy_info_sub_term_reason_set(swigCPtr, this, pj_str_t.getCPtr(value), value);
  }

  public pj_str_t getSub_term_reason() {
    long cPtr = XviJNI.pjsua_buddy_info_sub_term_reason_get(swigCPtr, this);
    return (cPtr == 0) ? null : new pj_str_t(cPtr, false);
  }

  public void setRpid(pjrpid_element value) {
    XviJNI.pjsua_buddy_info_rpid_set(swigCPtr, this, pjrpid_element.getCPtr(value), value);
  }

  public pjrpid_element getRpid() {
    long cPtr = XviJNI.pjsua_buddy_info_rpid_get(swigCPtr, this);
    return (cPtr == 0) ? null : new pjrpid_element(cPtr, false);
  }

  public void setPres_status(SWIGTYPE_p_pjsip_pres_status value) {
    XviJNI.pjsua_buddy_info_pres_status_set(swigCPtr, this, SWIGTYPE_p_pjsip_pres_status.getCPtr(value));
  }

  public SWIGTYPE_p_pjsip_pres_status getPres_status() {
    return new SWIGTYPE_p_pjsip_pres_status(XviJNI.pjsua_buddy_info_pres_status_get(swigCPtr, this), true);
  }

  public void setBuf_(String value) {
    XviJNI.pjsua_buddy_info_buf__set(swigCPtr, this, value);
  }

  public String getBuf_() {
    return XviJNI.pjsua_buddy_info_buf__get(swigCPtr, this);
  }

  public pjsua_buddy_info() {
    this(XviJNI.new_pjsua_buddy_info(), true);
  }

}

/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 2.0.12
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package net.phonex.xv;

public class pjsua_acc_info {
  private long swigCPtr;
  protected boolean swigCMemOwn;

  protected pjsua_acc_info(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(pjsua_acc_info obj) {
    return (obj == null) ? 0 : obj.swigCPtr;
  }

  protected void finalize() {
    delete();
  }

  public synchronized void delete() {
    if (swigCPtr != 0) {
      if (swigCMemOwn) {
        swigCMemOwn = false;
        XviJNI.delete_pjsua_acc_info(swigCPtr);
      }
      swigCPtr = 0;
    }
  }

  public void setId(int value) {
    XviJNI.pjsua_acc_info_id_set(swigCPtr, this, value);
  }

  public int getId() {
    return XviJNI.pjsua_acc_info_id_get(swigCPtr, this);
  }

  public void setIs_default(int value) {
    XviJNI.pjsua_acc_info_is_default_set(swigCPtr, this, value);
  }

  public int getIs_default() {
    return XviJNI.pjsua_acc_info_is_default_get(swigCPtr, this);
  }

  public void setAcc_uri(pj_str_t value) {
    XviJNI.pjsua_acc_info_acc_uri_set(swigCPtr, this, pj_str_t.getCPtr(value), value);
  }

  public pj_str_t getAcc_uri() {
    long cPtr = XviJNI.pjsua_acc_info_acc_uri_get(swigCPtr, this);
    return (cPtr == 0) ? null : new pj_str_t(cPtr, false);
  }

  public void setHas_registration(int value) {
    XviJNI.pjsua_acc_info_has_registration_set(swigCPtr, this, value);
  }

  public int getHas_registration() {
    return XviJNI.pjsua_acc_info_has_registration_get(swigCPtr, this);
  }

  public void setExpires(int value) {
    XviJNI.pjsua_acc_info_expires_set(swigCPtr, this, value);
  }

  public int getExpires() {
    return XviJNI.pjsua_acc_info_expires_get(swigCPtr, this);
  }

  public void setStatus(pjsip_status_code value) {
    XviJNI.pjsua_acc_info_status_set(swigCPtr, this, value.swigValue());
  }

  public pjsip_status_code getStatus() {
    return pjsip_status_code.swigToEnum(XviJNI.pjsua_acc_info_status_get(swigCPtr, this));
  }

  public void setReg_last_err(int value) {
    XviJNI.pjsua_acc_info_reg_last_err_set(swigCPtr, this, value);
  }

  public int getReg_last_err() {
    return XviJNI.pjsua_acc_info_reg_last_err_get(swigCPtr, this);
  }

  public void setStatus_text(pj_str_t value) {
    XviJNI.pjsua_acc_info_status_text_set(swigCPtr, this, pj_str_t.getCPtr(value), value);
  }

  public pj_str_t getStatus_text() {
    long cPtr = XviJNI.pjsua_acc_info_status_text_get(swigCPtr, this);
    return (cPtr == 0) ? null : new pj_str_t(cPtr, false);
  }

  public void setOnline_status(int value) {
    XviJNI.pjsua_acc_info_online_status_set(swigCPtr, this, value);
  }

  public int getOnline_status() {
    return XviJNI.pjsua_acc_info_online_status_get(swigCPtr, this);
  }

  public void setOnline_status_text(pj_str_t value) {
    XviJNI.pjsua_acc_info_online_status_text_set(swigCPtr, this, pj_str_t.getCPtr(value), value);
  }

  public pj_str_t getOnline_status_text() {
    long cPtr = XviJNI.pjsua_acc_info_online_status_text_get(swigCPtr, this);
    return (cPtr == 0) ? null : new pj_str_t(cPtr, false);
  }

  public void setRpid(pjrpid_element value) {
    XviJNI.pjsua_acc_info_rpid_set(swigCPtr, this, pjrpid_element.getCPtr(value), value);
  }

  public pjrpid_element getRpid() {
    long cPtr = XviJNI.pjsua_acc_info_rpid_get(swigCPtr, this);
    return (cPtr == 0) ? null : new pjrpid_element(cPtr, false);
  }

  public void setBuf_(String value) {
    XviJNI.pjsua_acc_info_buf__set(swigCPtr, this, value);
  }

  public String getBuf_() {
    return XviJNI.pjsua_acc_info_buf__get(swigCPtr, this);
  }

  public pjsua_acc_info() {
    this(XviJNI.new_pjsua_acc_info(), true);
  }

}
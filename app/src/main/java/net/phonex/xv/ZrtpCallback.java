/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 2.0.12
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package net.phonex.xv;

public class ZrtpCallback {
  private long swigCPtr;
  protected boolean swigCMemOwn;

  protected ZrtpCallback(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(ZrtpCallback obj) {
    return (obj == null) ? 0 : obj.swigCPtr;
  }

  protected void finalize() {
    delete();
  }

  public synchronized void delete() {
    if (swigCPtr != 0) {
      if (swigCMemOwn) {
        swigCMemOwn = false;
        XviJNI.delete_ZrtpCallback(swigCPtr);
      }
      swigCPtr = 0;
    }
  }

  protected void swigDirectorDisconnect() {
    swigCMemOwn = false;
    delete();
  }

  public void swigReleaseOwnership() {
    swigCMemOwn = false;
    XviJNI.ZrtpCallback_change_ownership(this, swigCPtr, false);
  }

  public void swigTakeOwnership() {
    swigCMemOwn = true;
    XviJNI.ZrtpCallback_change_ownership(this, swigCPtr, true);
  }

  public void on_zrtp_show_sas(int call_id, pj_str_t sas, int verified) {
    if (getClass() == ZrtpCallback.class) XviJNI.ZrtpCallback_on_zrtp_show_sas(swigCPtr, this, call_id, pj_str_t.getCPtr(sas), sas, verified); else XviJNI.ZrtpCallback_on_zrtp_show_sasSwigExplicitZrtpCallback(swigCPtr, this, call_id, pj_str_t.getCPtr(sas), sas, verified);
  }

  public void on_zrtp_update_transport(int call_id) {
    if (getClass() == ZrtpCallback.class) XviJNI.ZrtpCallback_on_zrtp_update_transport(swigCPtr, this, call_id); else XviJNI.ZrtpCallback_on_zrtp_update_transportSwigExplicitZrtpCallback(swigCPtr, this, call_id);
  }

  public void on_zrtp_update_state(int call_id, int hcode, int zrtp_state, int sev, int subcode) {
    if (getClass() == ZrtpCallback.class) XviJNI.ZrtpCallback_on_zrtp_update_state(swigCPtr, this, call_id, hcode, zrtp_state, sev, subcode); else XviJNI.ZrtpCallback_on_zrtp_update_stateSwigExplicitZrtpCallback(swigCPtr, this, call_id, hcode, zrtp_state, sev, subcode);
  }

  public void on_zrtp_hash_match(int call_id, int matchResult) {
    if (getClass() == ZrtpCallback.class) XviJNI.ZrtpCallback_on_zrtp_hash_match(swigCPtr, this, call_id, matchResult); else XviJNI.ZrtpCallback_on_zrtp_hash_matchSwigExplicitZrtpCallback(swigCPtr, this, call_id, matchResult);
  }

  public void on_zrtp_secure_state(int call_id, int secure_state_on) {
    if (getClass() == ZrtpCallback.class) XviJNI.ZrtpCallback_on_zrtp_secure_state(swigCPtr, this, call_id, secure_state_on); else XviJNI.ZrtpCallback_on_zrtp_secure_stateSwigExplicitZrtpCallback(swigCPtr, this, call_id, secure_state_on);
  }

  public ZrtpCallback() {
    this(XviJNI.new_ZrtpCallback(), true);
    XviJNI.ZrtpCallback_director_connect(this, swigCPtr, swigCMemOwn, false);
  }

}


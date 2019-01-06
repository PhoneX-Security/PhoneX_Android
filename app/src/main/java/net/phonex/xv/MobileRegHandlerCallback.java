/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 2.0.12
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package net.phonex.xv;

public class MobileRegHandlerCallback {
  private long swigCPtr;
  protected boolean swigCMemOwn;

  protected MobileRegHandlerCallback(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(MobileRegHandlerCallback obj) {
    return (obj == null) ? 0 : obj.swigCPtr;
  }

  protected void finalize() {
    delete();
  }

  public synchronized void delete() {
    if (swigCPtr != 0) {
      if (swigCMemOwn) {
        swigCMemOwn = false;
        XviJNI.delete_MobileRegHandlerCallback(swigCPtr);
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
    XviJNI.MobileRegHandlerCallback_change_ownership(this, swigCPtr, false);
  }

  public void swigTakeOwnership() {
    swigCMemOwn = true;
    XviJNI.MobileRegHandlerCallback_change_ownership(this, swigCPtr, true);
  }

  public void on_save_contact(int acc_id, pj_str_t contact, int expires) {
    if (getClass() == MobileRegHandlerCallback.class) XviJNI.MobileRegHandlerCallback_on_save_contact(swigCPtr, this, acc_id, pj_str_t.getCPtr(contact), contact, expires); else XviJNI.MobileRegHandlerCallback_on_save_contactSwigExplicitMobileRegHandlerCallback(swigCPtr, this, acc_id, pj_str_t.getCPtr(contact), contact, expires);
  }

  public pj_str_t on_restore_contact(int acc_id) {
    return new pj_str_t((getClass() == MobileRegHandlerCallback.class) ? XviJNI.MobileRegHandlerCallback_on_restore_contact(swigCPtr, this, acc_id) : XviJNI.MobileRegHandlerCallback_on_restore_contactSwigExplicitMobileRegHandlerCallback(swigCPtr, this, acc_id), true);
  }

  public MobileRegHandlerCallback() {
    this(XviJNI.new_MobileRegHandlerCallback(), true);
    XviJNI.MobileRegHandlerCallback_director_connect(this, swigCPtr, swigCMemOwn, false);
  }

}


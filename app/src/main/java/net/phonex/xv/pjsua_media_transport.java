/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 2.0.12
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package net.phonex.xv;

public class pjsua_media_transport {
  private long swigCPtr;
  protected boolean swigCMemOwn;

  protected pjsua_media_transport(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(pjsua_media_transport obj) {
    return (obj == null) ? 0 : obj.swigCPtr;
  }

  protected void finalize() {
    delete();
  }

  public synchronized void delete() {
    if (swigCPtr != 0) {
      if (swigCMemOwn) {
        swigCMemOwn = false;
        XviJNI.delete_pjsua_media_transport(swigCPtr);
      }
      swigCPtr = 0;
    }
  }

  public void setSkinfo(SWIGTYPE_p_pjmedia_sock_info value) {
    XviJNI.pjsua_media_transport_skinfo_set(swigCPtr, this, SWIGTYPE_p_pjmedia_sock_info.getCPtr(value));
  }

  public SWIGTYPE_p_pjmedia_sock_info getSkinfo() {
    return new SWIGTYPE_p_pjmedia_sock_info(XviJNI.pjsua_media_transport_skinfo_get(swigCPtr, this), true);
  }

  public void setTransport(SWIGTYPE_p_pjmedia_transport value) {
    XviJNI.pjsua_media_transport_transport_set(swigCPtr, this, SWIGTYPE_p_pjmedia_transport.getCPtr(value));
  }

  public SWIGTYPE_p_pjmedia_transport getTransport() {
    long cPtr = XviJNI.pjsua_media_transport_transport_get(swigCPtr, this);
    return (cPtr == 0) ? null : new SWIGTYPE_p_pjmedia_transport(cPtr, false);
  }

  public pjsua_media_transport() {
    this(XviJNI.new_pjsua_media_transport(), true);
  }

}
/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 2.0.12
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package net.phonex.xv;

public class pjmedia_port_info {
  private long swigCPtr;
  protected boolean swigCMemOwn;

  protected pjmedia_port_info(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(pjmedia_port_info obj) {
    return (obj == null) ? 0 : obj.swigCPtr;
  }

  protected void finalize() {
    delete();
  }

  public synchronized void delete() {
    if (swigCPtr != 0) {
      if (swigCMemOwn) {
        swigCMemOwn = false;
        XviJNI.delete_pjmedia_port_info(swigCPtr);
      }
      swigCPtr = 0;
    }
  }

  public void setName(pj_str_t value) {
    XviJNI.pjmedia_port_info_name_set(swigCPtr, this, pj_str_t.getCPtr(value), value);
  }

  public pj_str_t getName() {
    long cPtr = XviJNI.pjmedia_port_info_name_get(swigCPtr, this);
    return (cPtr == 0) ? null : new pj_str_t(cPtr, false);
  }

  public void setSignature(long value) {
    XviJNI.pjmedia_port_info_signature_set(swigCPtr, this, value);
  }

  public long getSignature() {
    return XviJNI.pjmedia_port_info_signature_get(swigCPtr, this);
  }

  public void setDir(pjmedia_dir value) {
    XviJNI.pjmedia_port_info_dir_set(swigCPtr, this, value.swigValue());
  }

  public pjmedia_dir getDir() {
    return pjmedia_dir.swigToEnum(XviJNI.pjmedia_port_info_dir_get(swigCPtr, this));
  }

  public void setFmt(SWIGTYPE_p_pjmedia_format value) {
    XviJNI.pjmedia_port_info_fmt_set(swigCPtr, this, SWIGTYPE_p_pjmedia_format.getCPtr(value));
  }

  public SWIGTYPE_p_pjmedia_format getFmt() {
    return new SWIGTYPE_p_pjmedia_format(XviJNI.pjmedia_port_info_fmt_get(swigCPtr, this), true);
  }

  public pjmedia_port_info() {
    this(XviJNI.new_pjmedia_port_info(), true);
  }

}

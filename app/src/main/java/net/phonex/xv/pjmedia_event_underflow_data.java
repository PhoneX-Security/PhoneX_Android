/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 2.0.12
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package net.phonex.xv;

public class pjmedia_event_underflow_data {
  private long swigCPtr;
  protected boolean swigCMemOwn;

  protected pjmedia_event_underflow_data(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(pjmedia_event_underflow_data obj) {
    return (obj == null) ? 0 : obj.swigCPtr;
  }

  protected void finalize() {
    delete();
  }

  public synchronized void delete() {
    if (swigCPtr != 0) {
      if (swigCMemOwn) {
        swigCMemOwn = false;
        XviJNI.delete_pjmedia_event_underflow_data(swigCPtr);
      }
      swigCPtr = 0;
    }
  }

  public void setUnderflow_ratio(double value) {
    XviJNI.pjmedia_event_underflow_data_underflow_ratio_set(swigCPtr, this, value);
  }

  public double getUnderflow_ratio() {
    return XviJNI.pjmedia_event_underflow_data_underflow_ratio_get(swigCPtr, this);
  }

  public void setUnderflow_status(int value) {
    XviJNI.pjmedia_event_underflow_data_underflow_status_set(swigCPtr, this, value);
  }

  public int getUnderflow_status() {
    return XviJNI.pjmedia_event_underflow_data_underflow_status_get(swigCPtr, this);
  }

  public void setConf_port_idx(int value) {
    XviJNI.pjmedia_event_underflow_data_conf_port_idx_set(swigCPtr, this, value);
  }

  public int getConf_port_idx() {
    return XviJNI.pjmedia_event_underflow_data_conf_port_idx_get(swigCPtr, this);
  }

  public pjmedia_event_underflow_data() {
    this(XviJNI.new_pjmedia_event_underflow_data(), true);
  }

}

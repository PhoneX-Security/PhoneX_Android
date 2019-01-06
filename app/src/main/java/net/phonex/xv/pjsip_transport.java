/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 2.0.12
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package net.phonex.xv;

public class pjsip_transport {
  private long swigCPtr;
  protected boolean swigCMemOwn;

  protected pjsip_transport(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(pjsip_transport obj) {
    return (obj == null) ? 0 : obj.swigCPtr;
  }

  protected void finalize() {
    delete();
  }

  public synchronized void delete() {
    if (swigCPtr != 0) {
      if (swigCMemOwn) {
        swigCMemOwn = false;
        XviJNI.delete_pjsip_transport(swigCPtr);
      }
      swigCPtr = 0;
    }
  }

  public void setObj_name(String value) {
    XviJNI.pjsip_transport_obj_name_set(swigCPtr, value);
  }

  public String getObj_name() {
    return XviJNI.pjsip_transport_obj_name_get(swigCPtr);
  }

  public void setPool(pj_pool_t value) {
    XviJNI.pjsip_transport_pool_set(swigCPtr, pj_pool_t.getCPtr(value), value);
  }

  public pj_pool_t getPool() {
    long cPtr = XviJNI.pjsip_transport_pool_get(swigCPtr);
    return (cPtr == 0) ? null : new pj_pool_t(cPtr, false);
  }

  public void setRef_cnt(SWIGTYPE_p_pj_atomic_t value) {
    XviJNI.pjsip_transport_ref_cnt_set(swigCPtr, SWIGTYPE_p_pj_atomic_t.getCPtr(value));
  }

  public SWIGTYPE_p_pj_atomic_t getRef_cnt() {
    long cPtr = XviJNI.pjsip_transport_ref_cnt_get(swigCPtr);
    return (cPtr == 0) ? null : new SWIGTYPE_p_pj_atomic_t(cPtr, false);
  }

  public void setLock(SWIGTYPE_p_pj_lock_t value) {
    XviJNI.pjsip_transport_lock_set(swigCPtr, SWIGTYPE_p_pj_lock_t.getCPtr(value));
  }

  public SWIGTYPE_p_pj_lock_t getLock() {
    long cPtr = XviJNI.pjsip_transport_lock_get(swigCPtr);
    return (cPtr == 0) ? null : new SWIGTYPE_p_pj_lock_t(cPtr, false);
  }

  public void setTracing(int value) {
    XviJNI.pjsip_transport_tracing_set(swigCPtr, value);
  }

  public int getTracing() {
    return XviJNI.pjsip_transport_tracing_get(swigCPtr);
  }

  public void setIs_shutdown(int value) {
    XviJNI.pjsip_transport_is_shutdown_set(swigCPtr, value);
  }

  public int getIs_shutdown() {
    return XviJNI.pjsip_transport_is_shutdown_get(swigCPtr);
  }

  public void setIs_destroying(int value) {
    XviJNI.pjsip_transport_is_destroying_set(swigCPtr, value);
  }

  public int getIs_destroying() {
    return XviJNI.pjsip_transport_is_destroying_get(swigCPtr);
  }

  public void setKey(SWIGTYPE_p_pjsip_transport_key value) {
    XviJNI.pjsip_transport_key_set(swigCPtr, SWIGTYPE_p_pjsip_transport_key.getCPtr(value));
  }

  public SWIGTYPE_p_pjsip_transport_key getKey() {
    return new SWIGTYPE_p_pjsip_transport_key(XviJNI.pjsip_transport_key_get(swigCPtr), true);
  }

  public void setType_name(String value) {
    XviJNI.pjsip_transport_type_name_set(swigCPtr, value);
  }

  public String getType_name() {
    return XviJNI.pjsip_transport_type_name_get(swigCPtr);
  }

  public void setFlag(long value) {
    XviJNI.pjsip_transport_flag_set(swigCPtr, value);
  }

  public long getFlag() {
    return XviJNI.pjsip_transport_flag_get(swigCPtr);
  }

  public void setInfo(String value) {
    XviJNI.pjsip_transport_info_set(swigCPtr, value);
  }

  public String getInfo() {
    return XviJNI.pjsip_transport_info_get(swigCPtr);
  }

  public void setAddr_len(int value) {
    XviJNI.pjsip_transport_addr_len_set(swigCPtr, value);
  }

  public int getAddr_len() {
    return XviJNI.pjsip_transport_addr_len_get(swigCPtr);
  }

  public void setLocal_addr(SWIGTYPE_p_pj_sockaddr value) {
    XviJNI.pjsip_transport_local_addr_set(swigCPtr, SWIGTYPE_p_pj_sockaddr.getCPtr(value));
  }

  public SWIGTYPE_p_pj_sockaddr getLocal_addr() {
    return new SWIGTYPE_p_pj_sockaddr(XviJNI.pjsip_transport_local_addr_get(swigCPtr), true);
  }

  public void setLocal_name(SWIGTYPE_p_pjsip_host_port value) {
    XviJNI.pjsip_transport_local_name_set(swigCPtr, SWIGTYPE_p_pjsip_host_port.getCPtr(value));
  }

  public SWIGTYPE_p_pjsip_host_port getLocal_name() {
    return new SWIGTYPE_p_pjsip_host_port(XviJNI.pjsip_transport_local_name_get(swigCPtr), true);
  }

  public void setRemote_name(SWIGTYPE_p_pjsip_host_port value) {
    XviJNI.pjsip_transport_remote_name_set(swigCPtr, SWIGTYPE_p_pjsip_host_port.getCPtr(value));
  }

  public SWIGTYPE_p_pjsip_host_port getRemote_name() {
    return new SWIGTYPE_p_pjsip_host_port(XviJNI.pjsip_transport_remote_name_get(swigCPtr), true);
  }

  public void setDir(SWIGTYPE_p_pjsip_transport_dir value) {
    XviJNI.pjsip_transport_dir_set(swigCPtr, SWIGTYPE_p_pjsip_transport_dir.getCPtr(value));
  }

  public SWIGTYPE_p_pjsip_transport_dir getDir() {
    return new SWIGTYPE_p_pjsip_transport_dir(XviJNI.pjsip_transport_dir_get(swigCPtr), true);
  }

  public void setEndpt(SWIGTYPE_p_pjsip_endpoint value) {
    XviJNI.pjsip_transport_endpt_set(swigCPtr, SWIGTYPE_p_pjsip_endpoint.getCPtr(value));
  }

  public SWIGTYPE_p_pjsip_endpoint getEndpt() {
    long cPtr = XviJNI.pjsip_transport_endpt_get(swigCPtr);
    return (cPtr == 0) ? null : new SWIGTYPE_p_pjsip_endpoint(cPtr, false);
  }

  public void setTpmgr(SWIGTYPE_p_pjsip_tpmgr value) {
    XviJNI.pjsip_transport_tpmgr_set(swigCPtr, SWIGTYPE_p_pjsip_tpmgr.getCPtr(value));
  }

  public SWIGTYPE_p_pjsip_tpmgr getTpmgr() {
    long cPtr = XviJNI.pjsip_transport_tpmgr_get(swigCPtr);
    return (cPtr == 0) ? null : new SWIGTYPE_p_pjsip_tpmgr(cPtr, false);
  }

  public void setIdle_timer(SWIGTYPE_p_pj_timer_entry value) {
    XviJNI.pjsip_transport_idle_timer_set(swigCPtr, SWIGTYPE_p_pj_timer_entry.getCPtr(value));
  }

  public SWIGTYPE_p_pj_timer_entry getIdle_timer() {
    return new SWIGTYPE_p_pj_timer_entry(XviJNI.pjsip_transport_idle_timer_get(swigCPtr), true);
  }

  public void setLast_recv_ts(SWIGTYPE_p_pj_timestamp value) {
    XviJNI.pjsip_transport_last_recv_ts_set(swigCPtr, SWIGTYPE_p_pj_timestamp.getCPtr(value));
  }

  public SWIGTYPE_p_pj_timestamp getLast_recv_ts() {
    return new SWIGTYPE_p_pj_timestamp(XviJNI.pjsip_transport_last_recv_ts_get(swigCPtr), true);
  }

  public void setLast_recv_len(long value) {
    XviJNI.pjsip_transport_last_recv_len_set(swigCPtr, value);
  }

  public long getLast_recv_len() {
    return XviJNI.pjsip_transport_last_recv_len_get(swigCPtr);
  }

  public void setData(byte[] value) {
    XviJNI.pjsip_transport_data_set(swigCPtr, value);
  }

  public byte[] getData() {
	return XviJNI.pjsip_transport_data_get(swigCPtr);
}

  public pjsip_transport() {
    this(XviJNI.new_pjsip_transport(), true);
  }

}
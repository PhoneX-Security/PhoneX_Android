/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 2.0.12
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package net.phonex.xv;

public class pjsua_media_config {
  private long swigCPtr;
  protected boolean swigCMemOwn;

  protected pjsua_media_config(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(pjsua_media_config obj) {
    return (obj == null) ? 0 : obj.swigCPtr;
  }

  protected void finalize() {
    delete();
  }

  public synchronized void delete() {
    if (swigCPtr != 0) {
      if (swigCMemOwn) {
        swigCMemOwn = false;
        XviJNI.delete_pjsua_media_config(swigCPtr);
      }
      swigCPtr = 0;
    }
  }

  public void setClock_rate(long value) {
    XviJNI.pjsua_media_config_clock_rate_set(swigCPtr, this, value);
  }

  public long getClock_rate() {
    return XviJNI.pjsua_media_config_clock_rate_get(swigCPtr, this);
  }

  public void setSnd_clock_rate(long value) {
    XviJNI.pjsua_media_config_snd_clock_rate_set(swigCPtr, this, value);
  }

  public long getSnd_clock_rate() {
    return XviJNI.pjsua_media_config_snd_clock_rate_get(swigCPtr, this);
  }

  public void setChannel_count(long value) {
    XviJNI.pjsua_media_config_channel_count_set(swigCPtr, this, value);
  }

  public long getChannel_count() {
    return XviJNI.pjsua_media_config_channel_count_get(swigCPtr, this);
  }

  public void setAudio_frame_ptime(long value) {
    XviJNI.pjsua_media_config_audio_frame_ptime_set(swigCPtr, this, value);
  }

  public long getAudio_frame_ptime() {
    return XviJNI.pjsua_media_config_audio_frame_ptime_get(swigCPtr, this);
  }

  public void setMax_media_ports(long value) {
    XviJNI.pjsua_media_config_max_media_ports_set(swigCPtr, this, value);
  }

  public long getMax_media_ports() {
    return XviJNI.pjsua_media_config_max_media_ports_get(swigCPtr, this);
  }

  public void setHas_ioqueue(int value) {
    XviJNI.pjsua_media_config_has_ioqueue_set(swigCPtr, this, value);
  }

  public int getHas_ioqueue() {
    return XviJNI.pjsua_media_config_has_ioqueue_get(swigCPtr, this);
  }

  public void setThread_cnt(long value) {
    XviJNI.pjsua_media_config_thread_cnt_set(swigCPtr, this, value);
  }

  public long getThread_cnt() {
    return XviJNI.pjsua_media_config_thread_cnt_get(swigCPtr, this);
  }

  public void setQuality(long value) {
    XviJNI.pjsua_media_config_quality_set(swigCPtr, this, value);
  }

  public long getQuality() {
    return XviJNI.pjsua_media_config_quality_get(swigCPtr, this);
  }

  public void setPtime(long value) {
    XviJNI.pjsua_media_config_ptime_set(swigCPtr, this, value);
  }

  public long getPtime() {
    return XviJNI.pjsua_media_config_ptime_get(swigCPtr, this);
  }

  public void setNo_vad(int value) {
    XviJNI.pjsua_media_config_no_vad_set(swigCPtr, this, value);
  }

  public int getNo_vad() {
    return XviJNI.pjsua_media_config_no_vad_get(swigCPtr, this);
  }

  public void setIlbc_mode(long value) {
    XviJNI.pjsua_media_config_ilbc_mode_set(swigCPtr, this, value);
  }

  public long getIlbc_mode() {
    return XviJNI.pjsua_media_config_ilbc_mode_get(swigCPtr, this);
  }

  public void setTx_drop_pct(long value) {
    XviJNI.pjsua_media_config_tx_drop_pct_set(swigCPtr, this, value);
  }

  public long getTx_drop_pct() {
    return XviJNI.pjsua_media_config_tx_drop_pct_get(swigCPtr, this);
  }

  public void setRx_drop_pct(long value) {
    XviJNI.pjsua_media_config_rx_drop_pct_set(swigCPtr, this, value);
  }

  public long getRx_drop_pct() {
    return XviJNI.pjsua_media_config_rx_drop_pct_get(swigCPtr, this);
  }

  public void setEc_options(long value) {
    XviJNI.pjsua_media_config_ec_options_set(swigCPtr, this, value);
  }

  public long getEc_options() {
    return XviJNI.pjsua_media_config_ec_options_get(swigCPtr, this);
  }

  public void setEc_tail_len(long value) {
    XviJNI.pjsua_media_config_ec_tail_len_set(swigCPtr, this, value);
  }

  public long getEc_tail_len() {
    return XviJNI.pjsua_media_config_ec_tail_len_get(swigCPtr, this);
  }

  public void setSnd_rec_latency(long value) {
    XviJNI.pjsua_media_config_snd_rec_latency_set(swigCPtr, this, value);
  }

  public long getSnd_rec_latency() {
    return XviJNI.pjsua_media_config_snd_rec_latency_get(swigCPtr, this);
  }

  public void setSnd_play_latency(long value) {
    XviJNI.pjsua_media_config_snd_play_latency_set(swigCPtr, this, value);
  }

  public long getSnd_play_latency() {
    return XviJNI.pjsua_media_config_snd_play_latency_get(swigCPtr, this);
  }

  public void setJb_init(int value) {
    XviJNI.pjsua_media_config_jb_init_set(swigCPtr, this, value);
  }

  public int getJb_init() {
    return XviJNI.pjsua_media_config_jb_init_get(swigCPtr, this);
  }

  public void setJb_min_pre(int value) {
    XviJNI.pjsua_media_config_jb_min_pre_set(swigCPtr, this, value);
  }

  public int getJb_min_pre() {
    return XviJNI.pjsua_media_config_jb_min_pre_get(swigCPtr, this);
  }

  public void setJb_max_pre(int value) {
    XviJNI.pjsua_media_config_jb_max_pre_set(swigCPtr, this, value);
  }

  public int getJb_max_pre() {
    return XviJNI.pjsua_media_config_jb_max_pre_get(swigCPtr, this);
  }

  public void setJb_max(int value) {
    XviJNI.pjsua_media_config_jb_max_set(swigCPtr, this, value);
  }

  public int getJb_max() {
    return XviJNI.pjsua_media_config_jb_max_get(swigCPtr, this);
  }

  public void setEnable_ice(int value) {
    XviJNI.pjsua_media_config_enable_ice_set(swigCPtr, this, value);
  }

  public int getEnable_ice() {
    return XviJNI.pjsua_media_config_enable_ice_get(swigCPtr, this);
  }

  public void setIce_max_host_cands(int value) {
    XviJNI.pjsua_media_config_ice_max_host_cands_set(swigCPtr, this, value);
  }

  public int getIce_max_host_cands() {
    return XviJNI.pjsua_media_config_ice_max_host_cands_get(swigCPtr, this);
  }

  public void setIce_opt(pj_ice_sess_options value) {
    XviJNI.pjsua_media_config_ice_opt_set(swigCPtr, this, pj_ice_sess_options.getCPtr(value), value);
  }

  public pj_ice_sess_options getIce_opt() {
    long cPtr = XviJNI.pjsua_media_config_ice_opt_get(swigCPtr, this);
    return (cPtr == 0) ? null : new pj_ice_sess_options(cPtr, false);
  }

  public void setIce_no_rtcp(int value) {
    XviJNI.pjsua_media_config_ice_no_rtcp_set(swigCPtr, this, value);
  }

  public int getIce_no_rtcp() {
    return XviJNI.pjsua_media_config_ice_no_rtcp_get(swigCPtr, this);
  }

  public void setIce_always_update(int value) {
    XviJNI.pjsua_media_config_ice_always_update_set(swigCPtr, this, value);
  }

  public int getIce_always_update() {
    return XviJNI.pjsua_media_config_ice_always_update_get(swigCPtr, this);
  }

  public void setEnable_turn(int value) {
    XviJNI.pjsua_media_config_enable_turn_set(swigCPtr, this, value);
  }

  public int getEnable_turn() {
    return XviJNI.pjsua_media_config_enable_turn_get(swigCPtr, this);
  }

  public void setTurn_server(pj_str_t value) {
    XviJNI.pjsua_media_config_turn_server_set(swigCPtr, this, pj_str_t.getCPtr(value), value);
  }

  public pj_str_t getTurn_server() {
    long cPtr = XviJNI.pjsua_media_config_turn_server_get(swigCPtr, this);
    return (cPtr == 0) ? null : new pj_str_t(cPtr, false);
  }

  public void setTurn_conn_type(SWIGTYPE_p_pj_turn_tp_type value) {
    XviJNI.pjsua_media_config_turn_conn_type_set(swigCPtr, this, SWIGTYPE_p_pj_turn_tp_type.getCPtr(value));
  }

  public SWIGTYPE_p_pj_turn_tp_type getTurn_conn_type() {
    return new SWIGTYPE_p_pj_turn_tp_type(XviJNI.pjsua_media_config_turn_conn_type_get(swigCPtr, this), true);
  }

  public void setTurn_auth_cred(SWIGTYPE_p_pj_stun_auth_cred value) {
    XviJNI.pjsua_media_config_turn_auth_cred_set(swigCPtr, this, SWIGTYPE_p_pj_stun_auth_cred.getCPtr(value));
  }

  public SWIGTYPE_p_pj_stun_auth_cred getTurn_auth_cred() {
    return new SWIGTYPE_p_pj_stun_auth_cred(XviJNI.pjsua_media_config_turn_auth_cred_get(swigCPtr, this), true);
  }

  public void setSnd_auto_close_time(int value) {
    XviJNI.pjsua_media_config_snd_auto_close_time_set(swigCPtr, this, value);
  }

  public int getSnd_auto_close_time() {
    return XviJNI.pjsua_media_config_snd_auto_close_time_get(swigCPtr, this);
  }

  public void setVid_preview_enable_native(int value) {
    XviJNI.pjsua_media_config_vid_preview_enable_native_set(swigCPtr, this, value);
  }

  public int getVid_preview_enable_native() {
    return XviJNI.pjsua_media_config_vid_preview_enable_native_get(swigCPtr, this);
  }

  public void setNo_smart_media_update(int value) {
    XviJNI.pjsua_media_config_no_smart_media_update_set(swigCPtr, this, value);
  }

  public int getNo_smart_media_update() {
    return XviJNI.pjsua_media_config_no_smart_media_update_get(swigCPtr, this);
  }

  public void setNo_rtcp_sdes_bye(int value) {
    XviJNI.pjsua_media_config_no_rtcp_sdes_bye_set(swigCPtr, this, value);
  }

  public int getNo_rtcp_sdes_bye() {
    return XviJNI.pjsua_media_config_no_rtcp_sdes_bye_get(swigCPtr, this);
  }

  public void setOn_aud_prev_play_frame(SWIGTYPE_p_f_p_pjmedia_frame__void value) {
    XviJNI.pjsua_media_config_on_aud_prev_play_frame_set(swigCPtr, this, SWIGTYPE_p_f_p_pjmedia_frame__void.getCPtr(value));
  }

  public SWIGTYPE_p_f_p_pjmedia_frame__void getOn_aud_prev_play_frame() {
    long cPtr = XviJNI.pjsua_media_config_on_aud_prev_play_frame_get(swigCPtr, this);
    return (cPtr == 0) ? null : new SWIGTYPE_p_f_p_pjmedia_frame__void(cPtr, false);
  }

  public void setOn_aud_prev_rec_frame(SWIGTYPE_p_f_p_pjmedia_frame__void value) {
    XviJNI.pjsua_media_config_on_aud_prev_rec_frame_set(swigCPtr, this, SWIGTYPE_p_f_p_pjmedia_frame__void.getCPtr(value));
  }

  public SWIGTYPE_p_f_p_pjmedia_frame__void getOn_aud_prev_rec_frame() {
    long cPtr = XviJNI.pjsua_media_config_on_aud_prev_rec_frame_get(swigCPtr, this);
    return (cPtr == 0) ? null : new SWIGTYPE_p_f_p_pjmedia_frame__void(cPtr, false);
  }

  public pjsua_media_config() {
    this(XviJNI.new_pjsua_media_config(), true);
  }

}
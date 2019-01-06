#ifndef __PJMEDIA_TRANSPORT_PROT_ZRTP_H__
#define __PJMEDIA_TRANSPORT_PROT_ZRTP_H__

/* Transport functions prototypes */
static pj_status_t transport_get_info(pjmedia_transport *tp, pjmedia_transport_info *info);
static pj_status_t transport_attach(pjmedia_transport *tp,
                                    void *user_data,
                                    const pj_sockaddr_t *rem_addr,
                                    const pj_sockaddr_t *rem_rtcp,
                                    unsigned addr_len,
                                    void (*rtp_cb)(void*, void*, pj_ssize_t),
                                    void (*rtcp_cb)(void*, void*, pj_ssize_t));
static void    transport_detach(pjmedia_transport *tp, void *strm);
static pj_status_t transport_send_rtp(pjmedia_transport *tp, const void *pkt, pj_size_t size);
static pj_status_t transport_send_rtcp(pjmedia_transport *tp, const void *pkt, pj_size_t size);
static pj_status_t transport_send_rtcp2(pjmedia_transport *tp,
                                        const pj_sockaddr_t *addr,
                                        unsigned addr_len,
                                        const void *pkt,
                                        pj_size_t size);
static pj_status_t transport_media_create(pjmedia_transport *tp,
        pj_pool_t *sdp_pool,
        unsigned options,
        const pjmedia_sdp_session *rem_sdp,
        unsigned media_index);
static pj_status_t transport_encode_sdp(pjmedia_transport *tp,
                                        pj_pool_t *sdp_pool,
                                        pjmedia_sdp_session *local_sdp,
                                        const pjmedia_sdp_session *rem_sdp,
                                        unsigned media_index);
static pj_status_t transport_media_start(pjmedia_transport *tp,
        pj_pool_t *pool,
        const pjmedia_sdp_session *local_sdp,
        const pjmedia_sdp_session *rem_sdp,
        unsigned media_index);
static pj_status_t transport_media_stop(pjmedia_transport *tp);
static pj_status_t transport_simulate_lost(pjmedia_transport *tp, pjmedia_dir dir, unsigned pct_lost);
static pj_status_t transport_destroy(pjmedia_transport *tp);

/* Forward declaration of thethe ZRTP specific callback functions that this
  adapter must implement */
static int32_t zrtp_sendDataZRTP(ZrtpContext* ctx, const uint8_t* data, int32_t length) ;
static int32_t zrtp_activateTimer(ZrtpContext* ctx, int32_t time) ;
static int32_t zrtp_cancelTimer(ZrtpContext* ctx) ;
static void zrtp_sendInfo(ZrtpContext* ctx, int32_t severity, int32_t subCode) ;
static int32_t zrtp_srtpSecretsReady(ZrtpContext* ctx, C_SrtpSecret_t* secrets, int32_t part) ;
static void zrtp_srtpSecretsOff(ZrtpContext* ctx, int32_t part) ;
static void zrtp_srtpSecretsOn(ZrtpContext* ctx, char* c, char* s, int32_t verified) ;
static void zrtp_handleGoClear(ZrtpContext* ctx) ;
static void zrtp_zrtpNegotiationFailed(ZrtpContext* ctx, int32_t severity, int32_t subCode) ;
static void zrtp_zrtpNotSuppOther(ZrtpContext* ctx) ;
static void zrtp_synchEnter(ZrtpContext* ctx) ;
static void zrtp_synchLeave(ZrtpContext* ctx) ;
static void zrtp_zrtpAskEnrollment(ZrtpContext* ctx, int32_t info) ;
static void zrtp_zrtpInformEnrollment(ZrtpContext* ctx, int32_t info) ;
static void zrtp_signSAS(ZrtpContext* ctx, uint8_t* sasHash) ;
static int32_t zrtp_checkSASSignature(ZrtpContext* ctx, uint8_t* sasHash) ;
static int32_t zrtp_checkZrtpHashMatch(ZrtpContext* ctx, int32_t matchResult) ;
static int32_t zrtp_synchTryEnter(ZrtpContext* ctx);
static void zrtp_log(uint8_t severity, const char *obj, const char *fmt, ...);
static void zrtp_vlog(uint8_t severity, const char *obj, const char *fmt, va_list argp);

#endif
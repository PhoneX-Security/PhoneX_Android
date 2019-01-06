package net.phonex.pub.a;

import android.content.Context;
import android.os.SystemClock;
import android.text.TextUtils;

import net.phonex.PhonexSettings;
import net.phonex.db.entity.SipCallSession;
import net.phonex.service.SvcRunnable;
import net.phonex.service.XService;
import net.phonex.service.XService.SameThreadException;
import net.phonex.sip.PjUtils;
import net.phonex.sip.SipStatusCode;
import net.phonex.util.DirtyValue;
import net.phonex.util.Log;

import net.phonex.util.MiscUtils;
import net.phonex.xv.Xvi;
import net.phonex.xv.pj_str_t;
import net.phonex.xv.pj_time_val;
import net.phonex.xv.pjmedia_dir;
import net.phonex.xv.pjsip_event;
import net.phonex.xv.XviConstants;
import net.phonex.xv.pjsua_call_info;
import net.phonex.xv.zrtp_state_info;

import org.apache.commons.lang3.StringUtils;

/**
 * Singleton class to manage pjsip calls. It allows to convert retrieve pjsip
 * calls information and convert that into objects that can be easily managed on
 * Android side
 */
public final class PjCalls {
    private static final String TAG = "PjCalls";

    public static final int CALL_UPDATE_CALL_INCOMING   = 0;
    public static final int CALL_UPDATE_MAKE_CALL       = 1;
    public static final int CALL_UPDATE_CALL_STATE      = 2;
    public static final int CALL_UPDATE_MEDIA_STATE     = 3;
    public static final int CALL_UPDATE_ZRTP_SHOW_SAS   = 4;
    public static final int CALL_UPDATE_ZRTP_SECURE_ON  = 5;
    public static final int CALL_UPDATE_ZRTP_SECURE_OFF = 6;
    public static final int CALL_UPDATE_ZRTP_GO_CLEAR   = 7;
    public static final int CALL_UPDATE_ON_HOLD         = 8;
    public static final int CALL_UPDATE_UN_HOLD         = 9;

    private PjCalls() {
    }

    /**
     * Update the call session infos
     *
     * @param session The session to update (input/output). Must have a correct
     *                call id set. Session to be updated.
     * @param updates Session information extracted by caller from the place of call.
     *                Can contain additional information extracted from e.g., SIP event.
     * @param context Context to be used. Some calls in this function need the particular context.
     * @param service PjManager Sip service to retrieve pjsip accounts info.
     * @param updateCode Caller specifies an update type by the update code.
     *                   Some checks are not performed if not needed by the update cause.
     *
     * @throws SameThreadException
     */
    public static void updateSessionFromPj(SipCallSession session,
                                           SipCallSession updates,
                                           Context context,
                                           PjManager service,
                                           Integer updateCode)
            throws SameThreadException
    {
        Log.df(TAG, "Update call %s", session.getCallId());

        // Update call session by quering call state from PJSIP (PJSUA_LOCK).
        final pjsua_call_info pjInfo = updateCallSessionInfoFromPj(session, context);
        final boolean pjInfoOk = pjInfo != null;

        final int callState = session.getCallState();

        // Update state here because we have pjsip_event here and can get q.850 state
        final Integer eventCode = updates != null ? updates.getLastEventCode() : null;
        if (eventCode != null) {
            int status_code = eventCode;
            if (status_code == 0) {
                try {
                    status_code = pjInfoOk ? pjInfo.getLast_status().swigValue() : session.getLastStatusCode();
                } catch (IllegalArgumentException err) {
                    // The status code does not exist in enum ignore it
                }
            }

            session.setLastStatusCode(status_code);
            Log.df(TAG, "Last status code is %s", status_code);

            // TODO - get comment from q.850 state as well
            final String status_text = pjInfoOk ? PjUtils.pjStrToString(pjInfo.getLast_status_text()) : session.getLastStatusComment();
            session.setLastStatusComment(status_text);
        }

        // And now, about secure information
        session.setSignalisationSecure(Xvi.call_secure_sig_level(session.getCallId()));
        final String secureInfo = PjUtils.pjStrToString(Xvi.call_secure_media_info(session.getCallId()));
        session.setMediaSecureInfo(secureInfo);
        session.setMediaSecure(!TextUtils.isEmpty(secureInfo) && session.isZrtpHashOK());

        final zrtp_state_info zrtpInfo = Xvi.jzrtp_getInfoFromCall(session.getCallId());
        session.setZrtpSASVerified(zrtpInfo.getSas_verified() == XviConstants.PJ_TRUE);
        session.setHasZrtp(zrtpInfo.getSecure() == XviConstants.PJ_TRUE, session.getCallState());

        Log.wf(TAG, "ZRTP Secure: %s, hashOK: %s, callId: %s, secInfo: %s",
                (session.isMediaSecure() ? "Secure: " + secureInfo : "NOT SECURE!"), session.isZrtpHashOK(), session.getCallId(), secureInfo);

        // Reset state information for new calls.
        if (updateMatch(updateCode, CALL_UPDATE_CALL_INCOMING) || updateMatch(updateCode, CALL_UPDATE_MAKE_CALL)){
            session.resetForNewCall();
            session.setRemoteSideAnswered(false);
        }

        // If call is incoming, switch it.
        if (updateMatch(updateCode, CALL_UPDATE_CALL_INCOMING)){
            session.setIncoming(true);
        } else if (updateMatch(updateCode, CALL_UPDATE_MAKE_CALL)) {
            session.setIncoming(false);
        }

        if (session.isIncoming() &&
                (session.getLastStatusCode() == SipStatusCode.BUSY_HERE
                || session.getLastStatusCode() == SipStatusCode.NOT_IMPLEMENTED
                || session.getLastStatusCode() == SipStatusCode.DECLINE))
        {
            Log.df(TAG, "Last status code was not relevant to local user %d", session.getLastStatusCode());
            session.setMediaSecure(true);
        }

        // Reset call start time for new calls as it may interfere with old records.
        if (callState == SipCallSession.InvState.INCOMING || callState == SipCallSession.InvState.EARLY){
            session.setCallStart(0);
        }

        // Call start means call was answered and really started.
        if (callState == SipCallSession.InvState.CONFIRMED && session.getCallStart() == 0){
            session.setCallStart(System.currentTimeMillis());
        }

        if (updateMatch(updateCode, CALL_UPDATE_CALL_STATE)) {
            if (callState == SipCallSession.InvState.EARLY
                    || callState == SipCallSession.InvState.CONFIRMED
                    || callState == SipCallSession.InvState.CONNECTING) {
                session.setRemoteSideAnswered(true);
            }
        }

        // mute microphone - in handler as it requires other components.
        service.getService().getHandler().execute(new SvcRunnable("callSessionMicMute") {
            @Override
            protected void doRun() throws SameThreadException {
                service.setMicrophoneMute(!session.isMediaSecure());
            }
        });

        // on secure clear errors
        if (session.isMediaSecure()) {
            session.setMediaSecureError(false);
            session.setMediaSecureErrorString("");
        }

        // About video info. It causes PJSUAL_LOCK. on_call_media_state also calls call_get_vid_stream_idx.
        final int vidStreamIdx = PhonexSettings.useVideo() ? Xvi.call_get_vid_stream_idx(session.getCallId()) : -1;
        if (vidStreamIdx >= 0) {
            int hasVid = Xvi.call_vid_stream_is_running(session.getCallId(), vidStreamIdx, pjmedia_dir.PJMEDIA_DIR_DECODING);
            session.setMediaHasVideo((hasVid == XviConstants.PJ_TRUE));
        }

        // CallID
        final DirtyValue<String> rawSipCallId = updates == null ? null : updates.getRawSipCallId();
        if (rawSipCallId != null && rawSipCallId.isChanged() && !StringUtils.isEmpty(rawSipCallId.get())){
            session.setSipCallId(rawSipCallId.get());
        }

        // If call ended with BYE, try to determine bye cause.
        final DirtyValue<Integer> rawByeCauseCode = updates == null ? null : updates.getRawByeCauseCode();
        if (rawByeCauseCode != null && rawByeCauseCode.isChanged() && rawByeCauseCode.get() != null){
            session.setByeCauseCode(rawByeCauseCode.get());
        }
    }

    /**
     * Updates just call state from the stack.
     * @param session
     * @return
     */
    public static boolean updateCallStateFromPj(SipCallSession session){
        // Get call state from the stack. Not synchronized on Xvi, uses PJSUA_LOCK internaly.
        final int callState = Xvi.call_get_state(session.getCallId());
        session.setCallState(callState);

        return true;
    }

    /**
     * Updates basic session info from the stack, including call state. Minimalistic call.
     * @param session
     * @param context
     */
    public static pjsua_call_info updateCallSessionInfoFromPj(SipCallSession session, Context context){
        // New call info structure that holds the data.
        final pjsua_call_info pjInfo = new pjsua_call_info();

        // JNI call to the PJSIP to load call info.
        final int status = Xvi.call_get_info(session.getCallId(), pjInfo);
        final boolean pjInfoOk = status == Xvi.PJ_SUCCESS;
        if (!pjInfoOk) {
            Log.wf(TAG, "No call info present in stack. It is disconnected. callId: %s, status: %s, session: %s", session.getCallId(), status, session);
            session.setCallState(SipCallSession.InvState.DISCONNECTED);

        } else {
            // Transform pjInfo into CallSession object
            updateSession(session, pjInfo, context);
        }

        return pjInfoOk ? pjInfo : null;
    }

    /**
     * Reads callId from the given event.
     * @param session
     * @param e
     * @param callState if is non-null, it updates call id only if there is reason to - according to call state.
     */
    public static boolean updateSipCallIdFromEvent(SipCallSession session, pjsip_event e, Integer callState){
        if (e == null){
            return false;
        }

        // If call state is given, check is performed
        if (callState != null
                && callState != SipCallSession.InvState.CONNECTING
                && callState != SipCallSession.InvState.CONFIRMED
                && callState != SipCallSession.InvState.CALLING
                && callState != SipCallSession.InvState.EARLY
                && callState != SipCallSession.InvState.INCOMING)
        {
            return false;
        }

        final pj_str_t pjCallId = Xvi.get_callid_from_evt(e);
        String callId = PjUtils.pjStrToString(pjCallId);
        Log.vf(TAG, "SipCallId: %s, event: %s", callId, e);
        if (!MiscUtils.isEmpty(callId)){
            session.setSipCallId(callId);
            return true;
        }

        return false;
    }

    /**
     * Determines bye cause from the event.
     *
     * @param session
     * @param e
     * @param callState
     * @return
     */
    public static boolean updateByeCauseFromEvent(SipCallSession session, pjsip_event e, Integer callState){
        if (callState != null && callState != SipCallSession.InvState.DISCONNECTED){
            return false;
        }

        final pj_str_t call_hangup_cause = Xvi.get_call_hangup_cause(e);
        final String callHangupCause = PjUtils.pjStrToString(call_hangup_cause);
        Log.vf(TAG, "Bye cause: %s, event: %s", callHangupCause, e);

        if (!MiscUtils.isEmpty(callHangupCause)){
            try {
                // Split on the first whitespace
                session.setByeCauseCode(Integer.parseInt(callHangupCause.split(" ")[0]));
                Log.vf(TAG, "Bye cause is non-empty: %s, code: %s", callHangupCause, session.getByeCauseCode());
                return true;

            } catch(Exception ex){
                Log.ef(TAG, "Could not parse hangup cause: %s", callHangupCause);
            }
        }

        return false;
    }

    /**
     * Updates sip call session with the last event code.
     * @param session
     * @param e
     * @return
     */
    public static boolean updateEventCodeFromEvent(SipCallSession session, pjsip_event e){
        if (e == null){
            return false;
        }

        final Integer status = getStatusCodeFromEvent(e);
        if (status != null){
            session.setLastEventCode(status);
            return true;
        }

        return false;
    }

    /**
     * Reads status code from the event.
     * @param e
     * @return
     */
    public static Integer getStatusCodeFromEvent(pjsip_event e){
        if (e == null){
            return null;
        }

        return Xvi.get_event_status_code(e);
    }

    /**
     * Takes all important information from event to the given session object.
     * Designed to work on the callBack thread with minimum locking.
     *
     * @param session
     * @param fetchCallState if true also call state is fetched from the stack.
     * @param e
     * @param context
     * @throws SameThreadException
     */
    public static void updateSessionFromEvent(SipCallSession session,
                                              boolean fetchCallState,
                                              pjsip_event e,
                                              Integer callState,
                                              Context context)
            throws SameThreadException
    {
        // Fetch call state if desired.
        if (fetchCallState){
            updateCallStateFromPj(session);
            callState = session.getCallState();
        }

        // Update state here because we have pjsip_event here and can get q.850 state
        updateEventCodeFromEvent(session, e);

        // Update call id from the event.
        updateSipCallIdFromEvent(session, e, callState);

        // Update bye cause from the event.
        updateByeCauseFromEvent(session, e, callState);
    }

    /**
     * Copy infos from pjsua call info object to SipCallSession object
     *
     * @param session    the session to copy info to (output)
     * @param pjCallInfo the call info from pjsip
     * @param context    PjManager Sip service to retrieve pjsip accounts infos
     */
    private static void updateSession(SipCallSession session, pjsua_call_info pjCallInfo,
                                      Context context) {
        // Should be unecessary cause we usually copy infos from a valid
        session.setCallId(pjCallInfo.getId());

        // Nothing to think about here cause we have a
        // bijection between int / state
        session.setCallState(pjCallInfo.getState().swigValue());
        session.setMediaStatus(pjCallInfo.getMedia_status().swigValue());
        session.setRemoteContact(PjUtils.pjStrToString(pjCallInfo.getRemote_info()));
        session.setConfPort(pjCallInfo.getConf_slot());

        // Would be needed later in the processing stage.
        // lastSipStatusCode not used due to exceptions on null values and enums.
        session.setLastSipStatusText(PjUtils.pjStrToString(pjCallInfo.getLast_status_text()));

        // Try to retrieve sip account related to this call
        int pjAccId = pjCallInfo.getAcc_id();
        session.setAccId(PjManager.getAccountIdForPjsipId(context, pjAccId));

        pj_time_val duration = pjCallInfo.getConnect_duration();
        session.setConnectStart(SystemClock.elapsedRealtime() - duration.getSec() * 1000 - duration.getMsec());

        Log.df(TAG, "Updated call %s from pjsession, state=%s, session=%s", session.getCallId(), session.getCallState(), session);
    }

    /**
     * Get infos for this pjsip call
     *
     * @param callId pjsip call id
     * @return Serialized information about this call
     * @throws SameThreadException
     */
    public static String dumpCallInfo(int callId) throws SameThreadException {
        return PjUtils.pjStrToString(Xvi.call_dump(callId, Xvi.PJ_TRUE, " "));
    }

    /**
     * Get ZRTP infos for this pjsip call
     *
     * @param callId pjsip call id
     * @return Serialized information about this call
     * @throws SameThreadException
     */
    public static String dumpZRTPCallInfo(int callId) throws SameThreadException {
        return PjUtils.pjStrToString(Xvi.jzrtp_zrtp_call_dump(callId, " "));
    }

    private static boolean updateMatch(Integer updateCode, int code){
        return updateCode != null && updateCode.equals(code);
    }

}

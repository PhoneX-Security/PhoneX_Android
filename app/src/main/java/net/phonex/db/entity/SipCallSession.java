package net.phonex.db.entity;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;

import net.phonex.pub.parcels.ZrtpLogEntry;
import net.phonex.util.DirtyValue;
import net.phonex.util.Log;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Represents state of a call session<br/>
 * This class helps to serialize/deserialize the state of the media layer <br/>
 * <b>Changing these fields has no effect on the sip call session </b>: it's
 * only a structured holder for datas <br/>
 */
public class SipCallSession extends SipCallSessionInfo implements Parcelable, Cloneable {
    private static final String TAG = "SipCallSession";

    /**
     * Describe the control state of a call <br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/group__PJSIP__INV.htm#ga083ffd9c75c406c41f113479cc1ebc1c"
     * >Pjsip documentation</a>
     */
    public static class InvState {

        public static final int PREPARING = -2;
        /**
         * The call is in an invalid state not syncrhonized with sip stack
         */
        public static final int INVALID = -1;
        /**
         * Before INVITE is sent or received
         */
        public static final int NULL = 0;
        /**
         * After INVITE is sent
         */
        public static final int CALLING = 1;
        /**
         * After INVITE is received.
         */
        public static final int INCOMING = 2;
        /**
         * After response with To tag.
         */
        public static final int EARLY = 3;
        /**
         * After 2xx is sent/received.
         */
        public static final int CONNECTING = 4;
        /**
         * After ACK is sent/received.
         */
        public static final int CONFIRMED = 5;
        /**
         * Session is terminated.
         */
        public static final int DISCONNECTED = 6;

        // Should not be constructed, just an older for int values
        // Not an enum because easier to pass to Parcelable
        private InvState() {
        }
    }
    
    /**
     * Option key to flag video use for the call. <br/>
     * The value must be a boolean.
     * 
     * @see Boolean
     */
    public static final String OPT_CALL_VIDEO = "opt_call_video";
    /**
     * Option key to add custom headers (with X- prefix). <br/>
     * The value must be a bundle with key representing header name, and value representing header value.
     * 
     * @see Bundle
     */
    public static final String OPT_CALL_EXTRA_HEADERS = "opt_call_extra_headers";

    /**
     * Describe the media state of the call <br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/group__PJSUA__LIB__CALL.htm#ga0608027241a5462d9f2736e3a6b8e3f4"
     * >Pjsip documentation</a>
     */
    public static class MediaState {
        /**
         * Call currently has no media
         */
        public static final int NONE = 0;
        /**
         * The media is active
         */
        public static final int ACTIVE = 1;
        /**
         * The media is currently put on hold by local endpoint
         */
        public static final int LOCAL_HOLD = 2;
        /**
         * The media is currently put on hold by remote endpoint
         */
        public static final int REMOTE_HOLD = 3;
        /**
         * The media has reported error (e.g. ICE negotiation)
         */
        public static final int ERROR = 4;

        // Should not be constructed, just an older for int values
        // Not an enum because easier to pass to Parcelable
        private MediaState() {
        }
    }

    /**
     * The call signaling is not secure
     */
    public static int TRANSPORT_SECURE_NONE = 0;
    /**
     * The call signaling is secure until it arrives on server. After, nothing ensures how it goes.
     */
    public static int TRANSPORT_SECURE_TO_SERVER = 1;
    /**
     * The call signaling is supposed to be secured end to end.
     */
    public static int TRANSPORT_SECURE_FULL = 2;
    
    /**
     * zrtp-hash match from SDP vs. ZRTP packet
     * Invalid value, no match
     */
    public static int ZRTP_HASH_INVALID = 0;
    
    /**
     * zrtp-hash match from SDP vs. ZRTP packet
     * OK, SDP == ZRTP
     */
    public static int ZRTP_HASH_MATCHES = 1;
    
    /**
     * zrtp-hash match from SDP vs. ZRTP packet
     * Error, SDP session is missing zrtp-hash
     */
    public static int ZRTP_HASH_NO_SDP  = 2;
    
    /**
     * zrtp-hash match from SDP vs. ZRTP packet
     * Error, ZRTP session is missing zrtp-hash, fatal
     */
    public static int ZRTP_HASH_NO_ZRTP = 4;
    
    /**
     * zrtp-hash match from SDP vs. ZRTP packet
     * Error, zrtp-hash present in both, but does not match
     */
    public static int ZRTP_HASH_MISMATCH = 8;
    
    /**
     * Id of an invalid or not existant call
     */
    public static final int INVALID_CALL_ID = -1;

    /**
     * Constructor for a sip call session state object <br/>
     * It will contains default values for all flags This class as no
     * setter/getter for members flags <br/>
     * It's aim is to allow to serialize/deserialize easily the state of a sip
     * call, <n>not to modify it</b>
     */
    public SipCallSession() {
        // Nothing to do in default constructor
    }

    public SipCallSession(int callId) {
        this.callId.set(callId);
    }

    protected SipCallSession(Parcel in) {
        super(in);
        dataChanged();
    }

    /**
     * Parcelable creator. So that it can be passed as an argument of the aidl
     * interface
     */
    public static final Parcelable.Creator<SipCallSession> CREATOR = new Parcelable.Creator<SipCallSession>() {
        public SipCallSession createFromParcel(Parcel in) {
            return new SipCallSession(in);
        }

        public SipCallSession[] newArray(int size) {
            return new SipCallSession[size];
        }
    };

    // Getters / Setters
    /**
     * Sets flag determining whether there is problem with media security
     * 
     * @param mediaSecureError
     */
	public void setMediaSecureError(boolean mediaSecureError) {
		this.mediaSecureError.set(mediaSecureError);
        dataChanged();
	}

	/**
	 * Set media secure error description
	 * 
	 * @param mediaSecureErrorString
	 */
	public void setMediaSecureErrorString(String mediaSecureErrorString) {
		this.mediaSecureErrorString.set(mediaSecureErrorString);
        dataChanged();
	}
	
	public void addZrtpLog(ZrtpLogEntry e){
        final List<ZrtpLogEntry> list = zrtpLog.getMakeDirty();
        if (list == null) {
            throw new IllegalArgumentException("ZRTP log is null");
        }
		list.add(e);
        dataChanged();
	}

    /**
     * Set the call id of this serializable holder
     *
     * @param callId2 the call id to setup
     */
    public void setCallId(int callId2) {
        callId.set(callId2);
        dataChanged();
    }

    /**
     * @param callStart the callStart to set
     */
    public void setCallStart(long callStart) {
        this.callStart.set(callStart);
        dataChanged();
    }

    /**
     * @param callState the new invitation state
     * @see SipCallSession.InvState
     */
    public void setCallState(int callState) {
        this.callState.set(callState);
        dataChanged();
    }

    /**
     * Set the account id for this call of this serializable holder
     *
     * @param accId2 The {@link SipProfile#id} of the account use for this call
     * @see #getAccId()
     */
    public void setAccId(long accId2) {
        accId.set(accId2);
        dataChanged();
    }

    /**
     * Set the signaling secure transport level.
     * Value should be one of {@link SipCallSession#TRANSPORT_SECURE_NONE}, {@link SipCallSession#TRANSPORT_SECURE_TO_SERVER}, {@link SipCallSession#TRANSPORT_SECURE_FULL}
     * @param transportSecure2
     */
    public void setSignalisationSecure(int transportSecure2) {
        transportSecure.set(transportSecure2);
        dataChanged();
    }

    /**
     * Set the media security level for this call of this serializable holder
     *
     * @param mediaSecure2 true if the call has a <b>media</b> encrypted
     * @see #isMediaSecure()
     */
    public void setMediaSecure(boolean mediaSecure2) {
        mediaSecure.set(mediaSecure2);
        dataChanged();
    }

    /**
     * Set the media security info for this call of this serializable holder
     *
     * @param aInfo the information about the <b>media</b> security
     * @see #getMediaSecureInfo()
     */
    public void setMediaSecureInfo(String aInfo) {
        mediaSecureInfo.set(aInfo);
        dataChanged();
    }

    /**
     * Set the latest status code for this call of this serializable holder
     *
     * @param status_code The code of the latest known sip dialog
     * @see #getLastStatusCode()
     * @see net.phonex.sip.SipStatusCode
     */
    public void setLastStatusCode(int status_code) {
        lastStatusCode.set(status_code);
        dataChanged();
    }

    /**
     * Set the last status comment for this call
     *
     * @param lastStatusComment the lastStatusComment to set
     */
    public void setLastStatusComment(String lastStatusComment) {
        this.lastStatusComment.set(lastStatusComment);
        dataChanged();
    }

    /**
     * Set the remote contact of this serializable holder
     *
     * @param remoteContact2 the new remote contact representation string
     * @see #getRemoteContact()
     */
    public void setRemoteContact(String remoteContact2) {
        remoteContact.set(remoteContact2);
        dataChanged();
    }

    /**
     * Set the fact that this call was initiated by the remote party
     *
     * @param isIncoming the isIncoming to set
     * @see #isIncoming()
     */
    public void setIncoming(boolean isIncoming) {
        this.isIncoming.set(isIncoming);
        dataChanged();
    }

    /**
     * Set the time of the beginning of the call as a connected call
     *
     * @param connectStart2 the new connected start time for this call
     * @see #getConnectStart()
     */
    public void setConnectStart(long connectStart2) {
        connectStart.set(connectStart2);
        dataChanged();
    }

    /**
     * Set the conf port of this serializable holder
     *
     * @param confPort2
     * @see #getConfPort()
     */
    public void setConfPort(int confPort2) {
        confPort.set(confPort2);
        dataChanged();
    }

    /**
     * Set the media video stream flag <br/>
     *
     * @param mediaHasVideo pass true if the media of the underlying call has a
     *            video stream
     */
    public void setMediaHasVideo(boolean mediaHasVideo) {
        this.mediaHasVideoStream.set(mediaHasVideo);
        dataChanged();
    }

    /**
     * Set the can record flag <br/>
     *
     * @param canRecord pass true if the audio can be recorded
     */
    public void setCanRecord(boolean canRecord) {
        this.canRecord.set(canRecord);
        dataChanged();
    }

    /**
     * Set the is record flag <br/>
     *
     * @param isRecording pass true if the audio is currently recording
     */
    public void setIsRecording(boolean isRecording) {
        this.isRecording.set(isRecording);
        dataChanged();
    }

    /**
     * @param zrtpSASVerified the zrtpSASVerified to set
     */
    public void setZrtpSASVerified(boolean zrtpSASVerified) {
        this.zrtpSASVerified.set(zrtpSASVerified);
        dataChanged();
    }

    /**
     * @param hasZrtp the hasZrtp to set
     * @param callState
     */
    public void setHasZrtp(boolean hasZrtp, int callState) {
        this.hasZrtp.set(hasZrtp);
        if (hasZrtp && zrtpStart.get() < 0){
            zrtpStart.set(SystemClock.elapsedRealtime());
        } else if (!hasZrtp) {
            // reset zrtp start if this is not call termination (because SipCallSession object may be reused)
            if (callState != SipCallSession.InvState.DISCONNECTED){
                zrtpStart.set(-1L);
            }
        }
        dataChanged();
    }

    /**
     * Set the sip media state of this serializable holder
     *
     * @param mediaStatus2 the new media status
     */
    public void setMediaStatus(int mediaStatus2) {
        mediaStatus.set(mediaStatus2);
        dataChanged();
    }

    public void setRemoteSideAnswered(boolean remoteSideAnswered) {
        this.remoteSideAnswered.set(remoteSideAnswered);
        dataChanged();
    }

    public void setSecondsLimit(int secondsLimit) {
        this.secondsLimit.set(secondsLimit);
        dataChanged();
    }

    public void setByeCauseCode(Integer byeCauseCode) {
        this.byeCauseCode.set(byeCauseCode);
        dataChanged();
    }

    public void setIsIncoming(boolean isIncoming) {
        this.isIncoming.set(isIncoming);
        dataChanged();
    }

    public void setLocalByeCode(Integer localByeCode) {
        this.localByeCode.set(localByeCode);
        dataChanged();
    }

    public void setSipCallId(String sipCallId) {
        this.sipCallId.set(sipCallId);
        dataChanged();
    }

    public void setZrtpHashMatch(int hashMatch){
        this.zrtpHashMatch.set(hashMatch);
        dataChanged();
    }

    public void setLastEventCode(Integer code){
        this.lastEventCode.set(code);
        dataChanged();
    }

    public void setLastSipStatusCode(Integer code){
        this.lastSipStatusCode.set(code);
        dataChanged();
    }

    public void setLastSipStatusText(String text){
        this.lastSipStatusText.set(text);
        dataChanged();
    }

    public void setCntUnderflowMediaRestarts(Integer cntUnderflowMediaRestarts) {
        this.cntUnderflowMediaRestarts.set(cntUnderflowMediaRestarts);
        dataChanged();
    }

    public void setLastUnderflowStatus(Integer lastUnderflowStatus) {
        this.lastUnderflowStatus.set(lastUnderflowStatus);
        dataChanged();
    }

    public void setLastUnderflowRate(Double lastUnderflowRate) {
        this.lastUnderflowRate.set(lastUnderflowRate);
        dataChanged();
    }

    public void setLastUnderflowMediaRestart(Long lastUnderflowMediaRestart) {
        this.lastUnderflowMediaRestart.set(lastUnderflowMediaRestart);
        dataChanged();
    }

    public void resetForNewCall(){
        callStart.set(0L);
        byeCauseCode.set(null);
        localByeCode.set(null);
        sipCallId.set(null);
        lastEventCode.set(null);
        lastSipStatusCode.set(null);
        lastSipStatusText.set(null);

        mediaStatus.set(MediaState.NONE);
        mediaSecure.set(false);
        mediaHasVideoStream.set(false);
        mediaSecureInfo.set("");
        canRecord.set(false);
        isRecording.set(false);
        zrtpSASVerified.set(false);
        hasZrtp.set(false);
        zrtpHashMatch.set(ZRTP_HASH_INVALID);

        lastUnderflowMediaRestart.set(null);
        lastUnderflowRate.set(null);
        lastUnderflowStatus.set(null);
        cntUnderflowMediaRestarts.set(0);
        dataChanged();
    }

    public void applyDisconnect() {
        isIncoming.set(false);
        mediaStatus.set(MediaState.NONE);
        mediaSecure.set(false);
        mediaHasVideoStream.set(false);
        callStart.set(0L);
        mediaSecureInfo.set("");
        canRecord.set(false);
        isRecording.set(false);
        zrtpSASVerified.set(false);
        hasZrtp.set(false);
        zrtpHashMatch.set(ZRTP_HASH_INVALID);
        dataChanged();
    }

    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    /**
     * Copies the object.
     * @return
     */
    public SipCallSession copy() {
        try {
            final SipCallSession clone = (SipCallSession) this.clone();
            clone.versionCounter.set(this.versionCounter.longValue());
            return clone;

        } catch (CloneNotSupportedException e) {
            Log.e(TAG, "Exception in cloning call session", e);
        }

        return null;
    }

    /**
     * Merges changed values from given parameter to this object.
     * @param info source object to merge values from.
     * @return true if info had at least one changed attribute.
     */
    public boolean mergeWithChanges(SipCallSessionInfo info){
        if (info == null){
            Log.e(TAG, "Could not merge with null object");
            return false;
        }

        // For change detection.
        final long curVersion = getVersionCounter();

        if (info.primaryKey.isChanged()) {
            primaryKey.set(info.primaryKey.get());
            dataChanged();
        }
        if (info.callStart.isChanged()) {
            callStart.set(info.callStart.get());
            dataChanged();
        }
        if (info.callId.isChanged()) {
            callId.set(info.callId.get());
            dataChanged();
        }
        if (info.callState.isChanged()) {
            callState.set(info.callState.get());
            dataChanged();
        }
        if (info.remoteContact.isChanged()) {
            remoteContact.set(info.remoteContact.get());
            dataChanged();
        }
        if (info.isIncoming.isChanged()) {
            isIncoming.set(info.isIncoming.get());
            dataChanged();
        }
        if (info.confPort.isChanged()) {
            confPort.set(info.confPort.get());
            dataChanged();
        }
        if (info.accId.isChanged()) {
            accId.set(info.accId.get());
            dataChanged();
        }
        if (info.mediaStatus.isChanged()) {
            mediaStatus.set(info.mediaStatus.get());
            dataChanged();
        }
        if (info.mediaSecure.isChanged()) {
            mediaSecure.set(info.mediaSecure.get());
            dataChanged();
        }
        if (info.transportSecure.isChanged()) {
            transportSecure.set(info.transportSecure.get());
            dataChanged();
        }
        if (info.mediaHasVideoStream.isChanged()) {
            mediaHasVideoStream.set(info.mediaHasVideoStream.get());
            dataChanged();
        }
        if (info.connectStart.isChanged()) {
            connectStart.set(info.connectStart.get());
            dataChanged();
        }
        if (info.zrtpStart.isChanged()) {
            zrtpStart.set(info.zrtpStart.get());
            dataChanged();
        }
        if (info.lastStatusCode.isChanged()) {
            lastStatusCode.set(info.lastStatusCode.get());
            dataChanged();
        }
        if (info.lastStatusComment.isChanged()) {
            lastStatusComment.set(info.lastStatusComment.get());
            dataChanged();
        }
        if (info.mediaSecureInfo.isChanged()) {
            mediaSecureInfo.set(info.mediaSecureInfo.get());
            dataChanged();
        }
        if (info.canRecord.isChanged()) {
            canRecord.set(info.canRecord.get());
            dataChanged();
        }
        if (info.isRecording.isChanged()) {
            isRecording.set(info.isRecording.get());
            dataChanged();
        }
        if (info.zrtpSASVerified.isChanged()) {
            zrtpSASVerified.set(info.zrtpSASVerified.get());
            dataChanged();
        }
        if (info.hasZrtp.isChanged()) {
            hasZrtp.set(info.hasZrtp.get());
            dataChanged();
        }
        if (info.zrtpHashMatch.isChanged()) {
            zrtpHashMatch.set(info.zrtpHashMatch.get());
            dataChanged();
        }
        if (info.mediaSecureError.isChanged()) {
            mediaSecureError.set(info.mediaSecureError.get());
            dataChanged();
        }
        if (info.mediaSecureErrorString.isChanged()) {
            mediaSecureErrorString.set(info.mediaSecureErrorString.get());
            dataChanged();
        }
        if (info.remoteSideAnswered.isChanged()) {
            remoteSideAnswered.set(info.remoteSideAnswered.get());
            dataChanged();
        }
        if (info.zrtpLog.isChanged()) {
            final List<ZrtpLogEntry> log = zrtpLog.getMakeDirty();
            log.clear();
            log.addAll(info.zrtpLog.get());
            dataChanged();
        }
        if (info.secondsLimit.isChanged()) {
            secondsLimit.set(info.secondsLimit.get());
            dataChanged();
        }
        if (info.byeCauseCode.isChanged()) {
            byeCauseCode.set(info.byeCauseCode.get());
            dataChanged();
        }
        if (info.localByeCode.isChanged()) {
            localByeCode.set(info.localByeCode.get());
            dataChanged();
        }
        if (info.sipCallId.isChanged()) {
            sipCallId.set(info.sipCallId.get());
            dataChanged();
        }
        if (info.lastEventCode.isChanged()) {
            lastEventCode.set(info.lastEventCode.get());
            dataChanged();
        }
        if (info.lastUnderflowStatus.isChanged()){
            lastUnderflowStatus.set(info.lastUnderflowStatus.get());
            dataChanged();
        }
        if (info.lastUnderflowRate.isChanged()){
            lastUnderflowRate.set(info.lastUnderflowRate.get());
            dataChanged();
        }
        if (info.lastUnderflowMediaRestart.isChanged()){
            lastUnderflowMediaRestart.set(info.lastUnderflowMediaRestart.get());
            dataChanged();
        }
        if (info.cntUnderflowMediaRestarts.isChanged()){
            cntUnderflowMediaRestarts.set(info.cntUnderflowMediaRestarts.get());
            dataChanged();
        }

        // If changed, version is greater than version before merge.
        return curVersion < getVersionCounter();
    }

    @Override
    public String toString() {
        return "SipCallSession{} " + super.toString();
    }
}

package net.phonex.db.entity;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;

import net.phonex.pub.parcels.ZrtpLogEntry;
import net.phonex.util.DirtyValue;
import net.phonex.util.Log;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by dusanklinec on 12.01.16.
 * Readonly, immutable SipCallSession interface.
 */
public class SipCallSessionInfo implements Parcelable, Cloneable{
    private static final String TAG = "SipCallSessionInfo";

    /**
     * Primary key for the parcelable object
     */
    protected DirtyValue<Integer> primaryKey = new DirtyValue<>(-1, Integer.class);

    /**
     * The starting time of the call
     */
    protected DirtyValue<Long> callStart = new DirtyValue<>(0L, Long.class);

    protected DirtyValue<Integer> callId                  = new DirtyValue<>(SipCallSession.INVALID_CALL_ID, Integer.class);
    protected DirtyValue<Integer> callState               = new DirtyValue<>(SipCallSession.InvState.INVALID, Integer.class);
    protected DirtyValue<String> remoteContact            = new DirtyValue<>(null, String.class);
    protected DirtyValue<Boolean> isIncoming              = new DirtyValue<>(false, Boolean.class);
    protected DirtyValue<Integer> confPort                = new DirtyValue<>(-1, Integer.class);
    protected DirtyValue<Long> accId                      = new DirtyValue<>(SipProfile.INVALID_ID, Long.class);
    protected DirtyValue<Integer> mediaStatus             = new DirtyValue<>(SipCallSession.MediaState.NONE, Integer.class);
    protected DirtyValue<Boolean> mediaSecure             = new DirtyValue<>(false, Boolean.class);
    protected DirtyValue<Integer> transportSecure         = new DirtyValue<>(0, Integer.class);
    protected DirtyValue<Boolean> mediaHasVideoStream     = new DirtyValue<>(false, Boolean.class);
    protected DirtyValue<Long> connectStart               = new DirtyValue<>(0L, Long.class);
    protected DirtyValue<Long> zrtpStart                  = new DirtyValue<>(-1L, Long.class);
    protected DirtyValue<Integer> lastStatusCode          = new DirtyValue<>(0, Integer.class);
    protected DirtyValue<String> lastStatusComment        = new DirtyValue<>("", String.class);
    protected DirtyValue<String> mediaSecureInfo          = new DirtyValue<>("", String.class);
    protected DirtyValue<Boolean> canRecord               = new DirtyValue<>(false, Boolean.class);
    protected DirtyValue<Boolean> isRecording             = new DirtyValue<>(false, Boolean.class);
    protected DirtyValue<Boolean> zrtpSASVerified         = new DirtyValue<>(false, Boolean.class);
    protected DirtyValue<Boolean> hasZrtp                 = new DirtyValue<>(false, Boolean.class);
    protected DirtyValue<Integer> zrtpHashMatch           = new DirtyValue<>(-1, Integer.class);
    protected DirtyValue<Boolean> mediaSecureError        = new DirtyValue<>(false, Boolean.class);
    protected DirtyValue<String> mediaSecureErrorString   = new DirtyValue<>("", String.class);
    protected DirtyValue<Boolean> remoteSideAnswered      = new DirtyValue<>(false, Boolean.class);
    protected DirtyValue<List<ZrtpLogEntry>> zrtpLog      = new DirtyValue<>(new CopyOnWriteArrayList<>());

    // the maximum number of seconds call can take, negative value means unlimited
    protected DirtyValue<Integer> secondsLimit            = new DirtyValue<>(-1, Integer.class);
    protected DirtyValue<Integer> byeCauseCode            = new DirtyValue<>(-1, Integer.class);
    protected DirtyValue<Integer> localByeCode            = new DirtyValue<>(-1, Integer.class);
    protected DirtyValue<String> sipCallId                = new DirtyValue<>(null, String.class);

    /**
     * Status code extracted from the last event.
     */
    protected DirtyValue<Integer> lastEventCode           = new DirtyValue<>(null, Integer.class);
    protected DirtyValue<Integer> lastSipStatusCode       = new DirtyValue<>(null, Integer.class);
    protected DirtyValue<String> lastSipStatusText        = new DirtyValue<>(null, String.class);

    /**
     * Underflow fix / media restart
     */
    protected DirtyValue<Integer> lastUnderflowStatus       = new DirtyValue<>(null, Integer.class);
    protected DirtyValue<Double> lastUnderflowRate          = new DirtyValue<>(null, Double.class);
    protected DirtyValue<Long> lastUnderflowMediaRestart    = new DirtyValue<>(null, Long.class);
    protected DirtyValue<Integer> cntUnderflowMediaRestarts = new DirtyValue<>(0, Integer.class);


    /**
     * If object changes, version is incremented.
     */
    protected final AtomicLong versionCounter = new AtomicLong(0);

    /**
     * Construct from parcelable <br/>
     * Only used by {@link #CREATOR}
     *
     * @param in parcelable to build from
     */
    protected SipCallSessionInfo(Parcel in) {
        primaryKey.readFromParcel(in);
        callId.readFromParcel(in);
        callState.readFromParcel(in);
        mediaStatus.readFromParcel(in);
        remoteContact.readFromParcel(in);
        isIncoming.readFromParcel(in);
        confPort.readFromParcel(in);
        accId.readFromParcel(in);
        lastStatusCode.readFromParcel(in);
        mediaSecureInfo.readFromParcel(in);
        mediaSecureError.readFromParcel(in);
        mediaSecureErrorString.readFromParcel(in);
        connectStart.readFromParcel(in);
        zrtpStart.readFromParcel(in);
        mediaSecure.readFromParcel(in);
        lastStatusComment.readFromParcel(in);
        mediaHasVideoStream.readFromParcel(in);
        canRecord.readFromParcel(in);
        isRecording.readFromParcel(in);
        hasZrtp.readFromParcel(in);
        zrtpSASVerified.readFromParcel(in);
        transportSecure.readFromParcel(in);
        zrtpHashMatch.readFromParcel(in);
        remoteSideAnswered.readFromParcel(in);
        
        in.readTypedList(zrtpLog.get(), ZrtpLogEntry.CREATOR);

        secondsLimit.readFromParcel(in);
        byeCauseCode.readFromParcel(in);
        localByeCode.readFromParcel(in);
        sipCallId.readFromParcel(in);
        lastEventCode.readFromParcel(in);
        lastSipStatusCode.readFromParcel(in);
        lastSipStatusText.readFromParcel(in);

        lastUnderflowStatus.readFromParcel(in);
        lastUnderflowRate.readFromParcel(in);
        lastUnderflowMediaRestart.readFromParcel(in);
        cntUnderflowMediaRestarts.readFromParcel(in);

        versionCounter.set(in.readLong());
    }

    /**
     * Constructor for a sip call session state object <br/>
     * It will contains default values for all flags This class as no
     * setter/getter for members flags <br/>
     * It's aim is to allow to serialize/deserialize easily the state of a sip
     * call, <n>not to modify it</b>
     */
    public SipCallSessionInfo() {
        // Nothing to do in default constructor
    }

    /**
     * @see Parcelable#describeContents()
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * @see Parcelable#writeToParcel(Parcel, int)
     */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        primaryKey.writeToParcel(dest, flags);
        callId.writeToParcel(dest, flags);
        callState.writeToParcel(dest, flags);
        mediaStatus.writeToParcel(dest, flags);
        remoteContact.writeToParcel(dest, flags);
        isIncoming.writeToParcel(dest, flags);
        confPort.writeToParcel(dest, flags);
        accId.writeToParcel(dest, flags);
        lastStatusCode.writeToParcel(dest, flags);
        mediaSecureInfo.writeToParcel(dest, flags);
        mediaSecureError.writeToParcel(dest, flags);
        mediaSecureErrorString.writeToParcel(dest, flags);
        connectStart.writeToParcel(dest, flags);
        zrtpStart.writeToParcel(dest, flags);
        mediaSecure.writeToParcel(dest, flags);
        lastStatusComment.writeToParcel(dest, flags);
        mediaHasVideoStream.writeToParcel(dest, flags);
        canRecord.writeToParcel(dest, flags);
        isRecording.writeToParcel(dest, flags);
        hasZrtp.writeToParcel(dest, flags);
        zrtpSASVerified.writeToParcel(dest, flags);
        transportSecure.writeToParcel(dest, flags);
        zrtpHashMatch.writeToParcel(dest, flags);
        remoteSideAnswered.writeToParcel(dest, flags);

        // todo: write changed flag.
        dest.writeTypedList(zrtpLog.get());

        secondsLimit.writeToParcel(dest, flags);
        byeCauseCode.writeToParcel(dest, flags);
        localByeCode.writeToParcel(dest, flags);
        sipCallId.writeToParcel(dest, flags);
        lastEventCode.writeToParcel(dest, flags);
        lastSipStatusCode.writeToParcel(dest, flags);
        lastSipStatusText.writeToParcel(dest, flags);

        lastUnderflowStatus.writeToParcel(dest, flags);
        lastUnderflowRate.writeToParcel(dest, flags);
        lastUnderflowMediaRestart.writeToParcel(dest, flags);
        cntUnderflowMediaRestarts.writeToParcel(dest, flags);

        dest.writeLong(getVersionCounter());
    }

    /**
     * Parcelable creator. So that it can be passed as an argument of the aidl
     * interface
     */
    public static final Parcelable.Creator<SipCallSessionInfo> CREATOR = new Parcelable.Creator<SipCallSessionInfo>() {
        public SipCallSessionInfo createFromParcel(Parcel in) {
            return new SipCallSessionInfo(in);
        }

        public SipCallSessionInfo[] newArray(int size) {
            return new SipCallSessionInfo[size];
        }
    };

    /**
     * A sip call session is equal to another if both means the same callId
     */
    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof SipCallSession)) {
            return false;
        }
        SipCallSession ci = (SipCallSession) o;
        if (ci.getCallId() == callId.get()) {
            return true;
        }
        return false;
    }

    // Getters / Setters
    /**
     * Get the call id of this call info
     *
     * @return id of this call
     */
    public int getCallId() {
        return callId.get();
    }

    /**
     * Get the call state of this call info
     *
     * @return the invitation state
     * @see SipCallSession.InvState
     */
    public int getCallState() {
        return callState.get();
    }

    public int getMediaStatus() {
        return mediaStatus.get();
    }

    /**
     * Get the remote Contact for this call info
     *
     * @return string representing the remote contact
     */
    public String getRemoteContact() {
        return remoteContact.get();
    }

    /**
     * Get the call way
     *
     * @return true if the remote party was the caller
     */
    public boolean isIncoming() {
        return isIncoming.get();
    }

    /**
     * Get the start time of the connection of the call
     *
     * @return duration in milliseconds
     * @see SystemClock#elapsedRealtime()
     */
    public long getConnectStart() {
        return connectStart.get();
    }

    /**
     * Check if the call state indicates that it is an active call in
     * progress.
     * This is equivalent to state incoming or early or calling or confirmed or connecting
     *
     * @return true if the call can be considered as in progress/active
     */
    public boolean isActive() {
        final int cs = callState.get();
        return (cs == SipCallSession.InvState.INCOMING || cs == SipCallSession.InvState.EARLY ||
                cs == SipCallSession.InvState.CALLING || cs == SipCallSession.InvState.CONFIRMED ||
                cs == SipCallSession.InvState.CONNECTING);
    }

    /**
     * Chef if the call state indicates that it's an ongoing call.
     * This is equivalent to state confirmed.
     * @return true if the call can be considered as ongoing.
     */
    public boolean isOngoing() {
        return callState.get() == SipCallSession.InvState.CONFIRMED;
    }

    /**
     * Get the sounds conference board port <br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/group__PJSUA__LIB__BASE.htm#gaf5d44947e4e62dc31dfde88884534385"
     * >Pjsip documentation</a>
     *
     * @return the conf port of the audio media of this call
     */
    public int getConfPort() {
        return confPort.get();
    }

    /**
     * Get the identifier of the account corresponding to this call <br/>
     * This identifier is the one you have in {@link SipProfile#id} <br/>
     * It may return {@link SipProfile#INVALID_ID} if no account detected for
     * this call. <i>Example, case of peer to peer call</i>
     *
     * @return The {@link SipProfile#id} of the account use for this call
     */
    public long getAccId() {
        return accId.get();
    }

    /**
     * Get the secure level of the signaling of the call.
     */
    public int getTransportSecureLevel() {
        return transportSecure.get();
    }

    /**
     * Get the secure level of the media of the call
     *
     * @return true if the call has a <b>media</b> encrypted
     */
    public boolean isMediaSecure() {
        return mediaSecure.get();
    }

    /**
     * Get the information about the <b>media</b> security of this call
     *
     * @return the information about the <b>media</b> security
     */
    public String getMediaSecureInfo() {
        return mediaSecureInfo.get();
    }

    /**
     * Returns whether there is error with media security
     *
     * @return
     */
    public boolean isMediaSecureError() {
        return mediaSecureError.get();
    }

    /**
     * Returns media secure error description
     *
     * @return
     */
    public String getMediaSecureErrorString() {
        return mediaSecureErrorString.get();
    }

    /**
     * Get the information about local held state of this call
     *
     * @return the information about local held state of media
     */
    public boolean isLocalHeld() {
        return mediaStatus.get() == SipCallSession.MediaState.LOCAL_HOLD;
    }

    /**
     * Get the information about remote held state of this call
     *
     * @return the information about remote held state of media
     */
    public boolean isRemoteHeld() {
        return (mediaStatus.get() == SipCallSession.MediaState.NONE && isActive() && !isBeforeConfirmed());
    }

    /**
     * Check if the specific call info indicates that it is a call that has not yet been confirmed by both ends.<br/>
     * In other worlds if the call is in state, calling, incoming early or connecting.
     *
     * @return true if the call can be considered not yet been confirmed
     */
    public boolean isBeforeConfirmed() {
        final int cs = callState.get();
        return (cs ==SipCallSession. InvState.CALLING  || cs == SipCallSession.InvState.INCOMING
                || cs == SipCallSession.InvState.EARLY || cs == SipCallSession.InvState.CONNECTING);
    }

    public boolean isPreparing(){
        return callState.get() == SipCallSession.InvState.PREPARING;
    }

    public boolean isCalling(){
        return callState.get() == SipCallSession.InvState.CALLING;
    }

    /**
     * Check if the specific call info indicates that it is a call that has been ended<br/>
     * In other worlds if the call is in state, disconnected, invalid or null
     *
     * @return true if the call can be considered as already ended
     */
    public boolean isAfterEnded() {
        return (callState.get() == SipCallSession.InvState.DISCONNECTED
                || callState.get() == SipCallSession.InvState.INVALID
                || callState.get() == SipCallSession.InvState.NULL);
    }

    /**
     * Get the latest status code of the sip dialog corresponding to this call
     * call
     *
     * @return the status code
     * @see net.phonex.sip.SipStatusCode
     */
    public int getLastStatusCode() {
        return lastStatusCode.get();
    }

    /**
     * Get the last status comment of the sip dialog corresponding to this call
     *
     * @return the last status comment string from server
     */
    public String getLastStatusComment() {
        return lastStatusComment.get();
    }

    /**
     * Get whether the call has a video media stream connected
     *
     * @return true if the call has a video media stream
     */
    public boolean mediaHasVideo() {
        return mediaHasVideoStream.get();
    }

    /**
     * Get the current call recording status for this call.
     *
     * @return true if we are currently recording this call to a file
     */
    public boolean isRecording() {
        return isRecording.get();
    }

    /**
     * Get the capability to record the call to a file.
     *
     * @return true if it should be possible to record the call to a file
     */
    public boolean canRecord() {
        return canRecord.get();
    }

    /**
     * @return the zrtpSASVerified
     */
    public boolean isZrtpSASVerified() {
        return zrtpSASVerified.get();
    }

    /**
     * @return whether call has Zrtp encryption active
     */
    public boolean getHasZrtp() {
        return hasZrtp.get();
    }

    /**
     * Get the start time of the call.
     * @return the callStart start time of the call.
     */
    public long getCallStart() {
        return callStart.get();
    }

    public int getZrtpHashMatch() {
        return zrtpHashMatch.get();
    }

    public long getZrtpStart() {
        return zrtpStart.get();
    }

    public boolean isZrtpHashOK(){
        return this.zrtpHashMatch.get() == 1 || !isActive();
    }

    public List<ZrtpLogEntry> getZrtpLog() {
        return zrtpLog.get();
    }

    public boolean isRemoteSideAnswered() {
        return remoteSideAnswered.get();
    }

    public int getSecondsLimit() {
        return secondsLimit.get();
    }

    public Integer getByeCauseCode() {
        return byeCauseCode.get();
    }

    public Integer getLocalByeCode() {
        return localByeCode.get();
    }

    public String getSipCallId() {
        return sipCallId.get();
    }

    public Integer getLastEventCode(){
        return lastEventCode.get();
    }

    public Integer getLastSipStatusCode(){
        return lastSipStatusCode.get();
    }

    public String getLastSipStatusText(){
        return lastSipStatusText.get();
    }

    public Integer getLastUnderflowStatus() {
        return lastUnderflowStatus.get();
    }

    public Double getLastUnderflowRate() {
        return lastUnderflowRate.get();
    }

    public Long getLastUnderflowMediaRestart() {
        return lastUnderflowMediaRestart.get();
    }

    public Integer getCntUnderflowMediaRestarts() {
        return cntUnderflowMediaRestarts.get();
    }

    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    /**
     * Copies the object.
     * @return
     */
    public SipCallSessionInfo copy() {
        try {
            final SipCallSessionInfo clone = (SipCallSessionInfo) this.clone();
            clone.versionCounter.set(this.versionCounter.longValue());
            return clone;

        } catch (CloneNotSupportedException e) {
            Log.e(TAG, "Exception in cloning call session", e);
        }

        return null;
    }

    protected long dataChanged(){
        return versionCounter.incrementAndGet();
    }

    public void resetVersionCounter(){
        versionCounter.set(0);
    }

    public long getVersionCounter(){
        return versionCounter.get();
    }

    @Override
    public String toString() {
        return "SipCallSessionInfo{" +
                "primaryKey=" + primaryKey +
                ", callStart=" + callStart +
                ", callId=" + callId +
                ", callState=" + callState +
                ", remoteContact=" + remoteContact +
                ", isIncoming=" + isIncoming +
                ", confPort=" + confPort +
                ", accId=" + accId +
                ", mediaStatus=" + mediaStatus +
                ", mediaSecure=" + mediaSecure +
                ", transportSecure=" + transportSecure +
                ", mediaHasVideoStream=" + mediaHasVideoStream +
                ", connectStart=" + connectStart +
                ", zrtpStart=" + zrtpStart +
                ", lastStatusCode=" + lastStatusCode +
                ", lastStatusComment=" + lastStatusComment +
                ", mediaSecureInfo=" + mediaSecureInfo +
                ", canRecord=" + canRecord +
                ", isRecording=" + isRecording +
                ", zrtpSASVerified=" + zrtpSASVerified +
                ", hasZrtp=" + hasZrtp +
                ", zrtpHashMatch=" + zrtpHashMatch +
                ", mediaSecureError=" + mediaSecureError +
                ", mediaSecureErrorString=" + mediaSecureErrorString +
                ", remoteSideAnswered=" + remoteSideAnswered +
                ", zrtpLog=" + zrtpLog +
                ", secondsLimit=" + secondsLimit +
                ", byeCauseCode=" + byeCauseCode +
                ", localByeCode=" + localByeCode +
                ", sipCallId=" + sipCallId +
                ", lastEventCode=" + lastEventCode +
                ", lastSipStatusCode=" + lastSipStatusCode +
                ", lastSipStatusText=" + lastSipStatusText +
                ", lastUnderflowStatus=" + lastUnderflowStatus +
                ", lastUnderflowRate=" + lastUnderflowRate +
                ", lastUnderflowMediaRestart=" + lastUnderflowMediaRestart +
                ", cntUnderflowMediaRestarts=" + cntUnderflowMediaRestarts +
                ", versionCounter=" + versionCounter +
                '}';
    }

    public DirtyValue<Integer> getPrimaryKey() {
        return primaryKey;
    }

    public DirtyValue<Boolean> getIsIncoming() {
        return isIncoming;
    }

    public DirtyValue<Integer> getRawTransportSecure() {
        return transportSecure;
    }

    public DirtyValue<Boolean> getRawMediaHasVideoStream() {
        return mediaHasVideoStream;
    }

    public DirtyValue<Integer> getRawCallId() {
        return callId;
    }

    public DirtyValue<Integer> getRawCallState() {
        return callState;
    }

    public DirtyValue<Integer> getRawMediaStatus() {
        return mediaStatus;
    }

    public DirtyValue<String> getRawRemoteContact() {
        return remoteContact;
    }

    public DirtyValue<Boolean> isRawIncoming() {
        return isIncoming;
    }

    public DirtyValue<Long> getRawConnectStart() {
        return connectStart;
    }

    public DirtyValue<Integer> getRawConfPort() {
        return confPort;
    }

    public DirtyValue<Long> getRawAccId() {
        return accId;
    }

    public DirtyValue<Integer> getRawTransportSecureLevel() {
        return transportSecure;
    }

    public DirtyValue<Boolean> getRawMediaSecure() {
        return mediaSecure;
    }

    public DirtyValue<String> getRawMediaSecureInfo() {
        return mediaSecureInfo;
    }

    public DirtyValue<Boolean> getRawMediaSecureError() {
        return mediaSecureError;
    }

    public DirtyValue<String> getRawMediaSecureErrorString() {
        return mediaSecureErrorString;
    }

    public DirtyValue<Integer> getRawLastStatusCode() {
        return lastStatusCode;
    }

    public DirtyValue<String> getRawLastStatusComment() {
        return lastStatusComment;
    }

    public DirtyValue<Boolean> getRawMediaHasVideo() {
        return mediaHasVideoStream;
    }

    public DirtyValue<Boolean> getRawIsRecording() {
        return isRecording;
    }

    public DirtyValue<Boolean> getRawCanRecord() {
        return canRecord;
    }

    public DirtyValue<Boolean> getRawZrtpSASVerified() {
        return zrtpSASVerified;
    }

    public DirtyValue<Boolean> getRawHasZrtp() {
        return hasZrtp;
    }

    public DirtyValue<Long> getRawCallStart() {
        return callStart;
    }

    public DirtyValue<Integer> getRawZrtpHashMatch() {
        return zrtpHashMatch;
    }

    public DirtyValue<Long> getRawZrtpStart() {
        return zrtpStart;
    }

    public DirtyValue<List<ZrtpLogEntry>> getRawZrtpLog() {
        return zrtpLog;
    }

    public DirtyValue<Boolean> getRawRemoteSideAnswered() {
        return remoteSideAnswered;
    }

    public DirtyValue<Integer> getRawSecondsLimit() {
        return secondsLimit;
    }

    public DirtyValue<Integer> getRawByeCauseCode() {
        return byeCauseCode;
    }

    public DirtyValue<Integer> getRawLocalByeCode() {
        return localByeCode;
    }

    public DirtyValue<String> getRawSipCallId() {
        return sipCallId;
    }

    public DirtyValue<Integer> getRawLastEventCode(){
        return lastEventCode;
    }

    public DirtyValue<Integer> getRawLastSipStatusCode(){
        return lastSipStatusCode;
    }

    public DirtyValue<String> getRawLastSipStatusText(){
        return lastSipStatusText;
    }

    public DirtyValue<Integer> getRawLastUnderflowStatus() {
        return lastUnderflowStatus;
    }

    public DirtyValue<Double> getRawLastUnderflowRate() {
        return lastUnderflowRate;
    }

    public DirtyValue<Long> getRawLastUnderflowMediaRestart() {
        return lastUnderflowMediaRestart;
    }

    public DirtyValue<Integer> getRawCntUnderflowMediaRestarts() {
        return cntUnderflowMediaRestarts;
    }
}

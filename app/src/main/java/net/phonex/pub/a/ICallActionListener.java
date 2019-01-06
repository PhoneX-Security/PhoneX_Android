package net.phonex.pub.a;

import net.phonex.db.entity.SipCallSession;
import net.phonex.db.entity.SipCallSessionInfo;

/**
 * Interface definition for a callback to be invoked when a tab is triggered by
 * moving it beyond a target zone.
 */
public interface ICallActionListener {
    /**
     * When user clics on clear call
     */
    static final int TERMINATE_CALL = 1;
    /**
     * When user clics on take call
     */
    static final int TAKE_CALL = TERMINATE_CALL + 1;
    /**
     * When user clics on not taking call
     */
    static final int DONT_TAKE_CALL = TAKE_CALL + 1;
    /**
     * When user clics on reject call
     */
    static final int REJECT_CALL = DONT_TAKE_CALL + 1;
    /**
     * When mute is set on
     */
    static final int MUTE_ON = REJECT_CALL + 1;
    /**
     * When mute is set off
     */
    static final int MUTE_OFF = MUTE_ON + 1;
    /**
     * When bluetooth is set on
     */
    static final int BLUETOOTH_ON = MUTE_OFF + 1;
    /**
     * When bluetooth is set off
     */
    static final int BLUETOOTH_OFF = BLUETOOTH_ON + 1;
    /**
     * When speaker is set on
     */
    static final int SPEAKER_ON = BLUETOOTH_OFF + 1;
    /**
     * When speaker is set off
     */
    static final int SPEAKER_OFF = SPEAKER_ON + 1;
    /**
     * When detailed display is asked
     */
    static final int DETAILED_DISPLAY = SPEAKER_OFF + 1;
    /**
     * When hold / reinvite is asked
     */
    static final int TOGGLE_HOLD = DETAILED_DISPLAY + 1;
    /**
     * When media settings is asked
     */
    static final int MEDIA_SETTINGS = TOGGLE_HOLD + 1;
    /**
     * When add call is asked
     */
    static final int ADD_CALL = MEDIA_SETTINGS + 1;
    /**
     * When xfer to a number is asked
     */
    static final int XFER_CALL = ADD_CALL + 1;
    /**
     * When transfer to a call is asked
     */
    static final int TRANSFER_CALL = XFER_CALL + 1;
    /**
     * When start recording is asked
     */
    static final int START_RECORDING = TRANSFER_CALL + 1;
    /**
     * When stop recording is asked
     */
    static final int STOP_RECORDING = START_RECORDING + 1;
    /**
     * Open the DTMF view
     */
    static final int DTMF_DISPLAY = STOP_RECORDING + 1;
    /**
     * Start the video stream
     */
    static final int START_VIDEO = DTMF_DISPLAY + 1;
    /**
     * Stop the video stream
     */
    static final int STOP_VIDEO = START_VIDEO + 1;
    /**
     * Stop the video stream
     */
    static final int ZRTP_TRUST = STOP_VIDEO + 1;
    /**
     * Stop the video stream
     */
    static final int ZRTP_REVOKE = ZRTP_TRUST + 1;
    /**
     * Terminates call forcefully
     */
    static final int DROP_CALL = ZRTP_REVOKE + 1;
    /**
     * Restarts SIP stack
     */
    static final int RESTART_SERVICE = DROP_CALL + 1;

    /**
     * Called when the user make an action
     *
     * @param actionId what action has been done
     */
    void onCallAction(int actionId, SipCallSessionInfo call);
}

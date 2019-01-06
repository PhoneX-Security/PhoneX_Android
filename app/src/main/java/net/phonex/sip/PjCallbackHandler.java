package net.phonex.sip;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import net.phonex.accounting.PermissionLimits;
import net.phonex.db.entity.SipCallSession;
import net.phonex.db.entity.SipCallSessionInfo;
import net.phonex.db.entity.SipClist;
import net.phonex.core.SipUri;
import net.phonex.db.entity.CallLog;
import net.phonex.pub.a.Compatibility;
import net.phonex.service.MyPresenceManager;
import net.phonex.ui.CallErrorActivity;
import net.phonex.util.Log;

import java.lang.ref.WeakReference;

/**
* Created by ph4r05 on 7/27/14.
* TODO: refactor a bit. call state changes.
*/
public class PjCallbackHandler extends Handler {
    private static final String TAG ="PjCallbackHandler";

    /**
     * Weak reference for Pj Callback.
     * If PjCallback is dead/deallocated, it returns null.
     */
    private WeakReference<PjCallback> sr;

    public PjCallbackHandler(Looper looper, PjCallback callback) {
        super(looper);
        Log.d(TAG, "new WorkerHandler()");
        sr = new WeakReference<>(callback);
    }

    /**
     * Main method for processing input messages sent to this handler.
     * @param msg
     */
    public void handleMessage(Message msg) {
        PjCallback cb = sr.get();
        if (cb == null) {
            return;
        }

        cb.lockCpu("PjHandler: " + msg.what);
        switch (msg.what) {
            case PjCallback.ON_CALL_STATE:
                handleCallState(cb, msg);
                break;

            case PjCallback.ON_MEDIA_STATE:
                handleMediaState(cb, msg);
                break;
        }
        cb.unlockCpu();
    }

    /**
     * Handles messages notifying call state change.
     * @param cb
     * @param msg
     */
    private void handleCallState(PjCallback cb, Message msg){
        SipCallSession callInfo = (SipCallSession) msg.obj;
        final Context ctxt = cb.getPjManager().getService();

        // If contact is not in contact list, do not update call log
        String canonicalSip = SipUri.getCanonicalSipContact(callInfo.getRemoteContact(), false);
        if (SipClist.getProfileFromDbSip(ctxt, canonicalSip) == null){
            Log.wf(TAG, "handleCallState; handling callState from user=%s not present in contact list, callInfo=%s", canonicalSip, callInfo);
            return;
        }


        final int callState = callInfo.getCallState();
        Log.vf(TAG, "Call state changed: %s", callState);

        switch (callState) {
            case SipCallSession.InvState.INCOMING:
            case SipCallSession.InvState.CALLING:
                cb.getNotificationManager().notifyCall(callInfo);
                cb.launchCallHandler(callInfo);
                cb.broadCastAndroidCallState("RINGING", callInfo.getRemoteContact());
                break;

            case SipCallSession.InvState.EARLY:
            case SipCallSession.InvState.CONNECTING:
            case SipCallSession.InvState.CONFIRMED: {
                cb.getNotificationManager().notifyCall(callInfo);
                cb.launchCallHandler(callInfo);
                cb.broadCastAndroidCallState("OFFHOOK", callInfo.getRemoteContact());

                if (cb.getPjManager().getMediaManager() != null) {
                    if (callState == SipCallSession.InvState.CONFIRMED) {
                        // Don't unfocus here
                        cb.getPjManager().getMediaManager().stopRing();
                    }
                }

                // If state is confirmed and not already initialized
                if (callState == SipCallSession.InvState.CONFIRMED && callInfo.getCallStart() == 0) {
                    // write remaining number of seconds to outgoing call (given by license type)
                    PermissionLimits.updateCallLimit(ctxt.getContentResolver(), callInfo);
                }

                // Set presence state to INCALL
                PjCallback ua = sr.get();
                if (callState == SipCallSession.InvState.CONFIRMED && ua != null){
                    MyPresenceManager.changeOnCallState(ctxt, callInfo.getAccId(), true);
                } else {
                    Log.d(TAG, "UA is null, cannot change inCall");
                }
                break;
            }

            case SipCallSession.InvState.DISCONNECTED: {
                if (cb.getPjManager().getMediaManager() != null) {
                    cb.getPjManager().getMediaManager().stopRing();
                }

                Log.d(TAG, "Finish call2");
                cb.broadCastAndroidCallState("IDLE", callInfo.getRemoteContact());

                // If no remaining calls, cancel the notification
                if (cb.getActiveCallInProgress() == null) {
                    cb.getNotificationManager().cancelCalls();
                    // We should now ask parent to stop if needed
                    if (cb.getPjManager() != null && cb.getPjManager().getService() != null) {
                        cb.getPjManager().getService().treatDeferUnregistersForOutgoing();
                    }

                    // Reset presence state INCALL flag.
                    PjCallback ua = sr.get();
                    if (ua != null){
                        MyPresenceManager.changeOnCallState(ctxt, callInfo.getAccId(), false);
                        // non blocking call
                        ua.getPermissionManager().asyncConsumeCall(callInfo);
                    } else {
                        Log.d(TAG, "UA is null, cannot change inCall");
                    }
                }

                // CallLog - store call
                updateCallLog(cb, ctxt, callInfo);
                break;
            }
        }
        cb.onBroadcastCallState(callInfo);
    }

    private void updateCallLog(PjCallback cb, Context ctxt, SipCallSession callInfo) {
        CallLog cli = CallLog.fromSession(ctxt, callInfo);

        boolean isNew = cli.isNew();
        if (isNew && Compatibility.isCallSupported()) {
            cb.getNotificationManager().notifyMissedCall(cli);
        }

        // If the call goes out in error...
        if (Compatibility.isCallSupported() &&
                (callInfo.getLastStatusCode() != 200
                        || (callInfo.getByeCauseCode() != null && callInfo.getByeCauseCode() > 299)
                        || (callInfo.getLocalByeCode() != null && callInfo.getLocalByeCode() > 299))) {
            Log.df(TAG, "Call disconnected with error: %s", callInfo.getLastStatusCode());
            CallErrorActivity.CallErrorMessage callMessage = new CallErrorActivity.CallErrorMessage(callInfo.getLastStatusCode(), callInfo.isIncoming());
            callMessage.isMissedCall = isNew;
            callMessage.remoteSip = cli.getRemoteContactSip();
            callMessage.remoteDisplayName = cli.getRemoteContactName();
            callMessage.remoteId = cli.getRemoteAccountId();
            callMessage.byeCauseCode = callInfo.getByeCauseCode();
            callMessage.localByeCode = callInfo.getLocalByeCode();

            if (callMessage.isRelevantToLocalUser()) {
                cb.getPjManager().getService().notifyUserOfEndedCall(callMessage);
            }
        }

        // Send missed call notification in service.
        cb.getPjManager().getService().onCallEnded(callInfo, cli);

        // Verify this is not already inserted in DB. Avoid duplicate notifications for the same event.
        final CallLog prevCl = CallLog.getLogByEventDescription(ctxt.getContentResolver(),
                cli.getRemoteContactSip(),
                cli.getAccountId(),
                cli.getEventTimestamp(),
                null,
                cli.getSipCallId());

        if (prevCl != null) {
            Log.vf(TAG, "Given callog already inserted in db. From %s, toId %s evtTime %s, evtNonce %s, sipCallId %s",
                    cli.getRemoteContactSip(),
                    cli.getAccountId(),
                    cli.getEventTimestamp(),
                    null,
                    cli.getSipCallId());

        } else {
            // Fill our own database
            cli.addToDatabase(ctxt);
            Log.vf(TAG, "CallLog entry inserted: %s", cli.toString());
        }
    }

    /**
     * Handles message for changing media state.
     * @param cb
     * @param msg
     */
    private void handleMediaState(PjCallback cb, Message msg){
        SipCallSessionInfo mediaCallInfo = (SipCallSessionInfo) msg.obj;
        SipCallSession callInfo = cb.getCallsList().get(mediaCallInfo.getCallId());
        callInfo.setMediaStatus(mediaCallInfo.getMediaStatus());
        cb.getCallsList().put(mediaCallInfo.getCallId(), callInfo);
        cb.onBroadcastCallState(callInfo);
    }
}

package net.phonex.service;

import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;

import net.phonex.PhonexSettings;
import net.phonex.R;
import net.phonex.core.Constants;
import net.phonex.core.IService;
import net.phonex.core.Intents;
import net.phonex.core.MediaState;
import net.phonex.pref.PhonexConfig;
import net.phonex.db.entity.SipCallSession;
import net.phonex.db.entity.SipCallSessionInfo;
import net.phonex.db.entity.SipProfileState;
import net.phonex.pub.a.AudioSettingsActivity;
import net.phonex.pub.a.Compatibility;
import net.phonex.pub.a.PjCalls;
import net.phonex.pub.parcels.CertUpdateParams;
import net.phonex.pub.parcels.CertUpdateProgress;
import net.phonex.pub.parcels.FileTransferProgress;
import net.phonex.pub.parcels.KeyGenProgress;
import net.phonex.pub.parcels.MakeCallResult;
import net.phonex.pub.parcels.SipMsgAux;
import net.phonex.service.messaging.MessageManager;
import net.phonex.util.Log;

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.List;

/**
* Created by ph4r05 on 7/28/14.
*/
class XServiceBinder extends IService.Stub {
    private static final String TAG="XServiceBinder";
    private final WeakReference<XService> sr;
    private XService svc;

    /**
     * Construct the stub at attach it to the interface.
     */
    XServiceBinder(XService sr) {
        super();
        this.sr = new WeakReference<XService>(sr);
    }

    /**
     * Loads service from the weak reference. Returns true if everything is OK.
     * @return
     */
    private boolean loadSvc(){
        this.svc = sr.get();
        if (this.svc==null){
            Log.w(TAG, "Cannot load service, is null");
            return false;
        }

        return true;
    }

    /**
     * Executes job on the service runnable.
     * @param runnable
     */
    private void executeJob(Runnable runnable){
        svc.executeJob(runnable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SipProfileState getSipProfileState(int accountId) throws RemoteException {
        if (!loadSvc()){
            return null;
        }

        svc.enforceCallingOrSelfPermission(Constants.PERMISSION_PHONEX, null);
        return svc.getSipProfileState(accountId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAccountSipValid(final String callee, final int accountId) throws RemoteException {
        if (!loadSvc()){
            return false;
        }

        svc.enforceCallingOrSelfPermission(Constants.PERMISSION_PHONEX, null);
        SvcReturnRunnable action = new SvcReturnRunnable("accValid") {
            @Override
            protected Object runWithReturn() throws XService.SameThreadException {
                return (Boolean) svc.isAccountSipValid(callee, accountId);
            }
        };

        executeJob(action);
        return (Boolean) action.getResult();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cancelAllNotifications() throws RemoteException {
        if (!loadSvc()){
            return;
        }

        svc.enforceCallingOrSelfPermission(Constants.PERMISSION_PHONEX, null);
        executeJob(new SvcRunnable() {
            @Override
            protected void doRun() throws XService.SameThreadException {
            if (svc.getNotificationManager() == null) {
                Log.e(TAG, "Notification manager is null");
                return;
            }

            svc.getNotificationManager().cancelAll();
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MakeCallResult makeCall(final String callee, final int accountId) throws RemoteException {
        return makeCallWithOptions(callee, accountId, null);
    }

    @Override
    public MakeCallResult makeCallWithOptions(final String callee, final int accountId, final Bundle options) throws RemoteException {
        if (!loadSvc()){
            return null;
        }

        svc.enforceCallingOrSelfPermission(Constants.PERMISSION_PHONEX, null);

        //We have to ensure service is properly started and not just binded
        svc.startService(new Intent(svc, XService.class));

        if(svc.getPjManager() == null) {
            Log.e(TAG, "Can't place call if service not started");
            // TODO - we should return a failing status here
            return null;
        }

        if(!svc.isSupportMultipleCalls()) {
            // Check if there is no ongoing calls if so drop this request by alerting user
            SipCallSession activeCall = svc.getPjManager().getActiveCallInProgress();
            if(activeCall != null) {
                if(!PhonexSettings.forceNoMultipleCalls()) {
                    svc.notifyUserOfMessage(R.string.not_configured_multiple_calls);
                }
                return null;
            }
        }

        SvcReturnRunnable action = new SvcReturnRunnable("makeCallWithOptions"){
            @Override
            protected Object runWithReturn() throws XService.SameThreadException {
                return svc.getPjManager().makeCall(callee, accountId, options);
            }
        };
        executeJob(action);
        return (MakeCallResult) action.getResult();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendMessage(final String message, final String callee, final long accountId) throws RemoteException {
        sendMessageMime(message, message, callee, accountId, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendMessageMime(final String message, final String msg2store, final String callee, final long accountId, final String mime) throws RemoteException {
        sendMessageImpl(message, msg2store, callee, accountId, mime, accountId, false, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resendMessageMime(final String message, final String msg2store, final String callee, final long accountId, final String mime, final long messageId) throws RemoteException {
        sendMessageImpl(message, msg2store, callee, accountId, mime, messageId, true, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendNotificationMessage(final String message, final String msg2store, final String callee, final long accountId, final String mime, final long messageId) throws RemoteException {
        if (!loadSvc()){
            return;
        }
        final XService xsvc = svc;
        svc.enforceCallingOrSelfPermission(Constants.PERMISSION_PHONEX, null);

        //We have to ensure service is properly started and not just binded
        svc.startService(new Intent(svc, XService.class));
        executeJob(new SvcRunnable("sendNotificationMessage") {
            @Override
            protected void doRun() throws XService.SameThreadException {
                Log.d(TAG, "will send notification sms to " + callee);

                if (svc.getPjManager() == null) {
                    svc.notifyUserOfMessage(xsvc.getString(R.string.connection_not_valid));
                    return;
                }

                // calls pjManager, which calls underlying c code
                XService.ToCall called = svc.getPjManager().sendMessage(callee, message, accountId, mime, null);

                // update record in DB
                if (called == null) {
                    svc.notifyUserOfMessage(xsvc.getString(R.string.invalid_sip_uri) + " : " + callee);
                }
            }
        });
    }

    /**
     * Implementation of sendMessageMime, resendMessageMime
     */
    public void sendMessageImpl(final String message, final String msg2store, final String callee,
                                final long accountId, final String mime, final long messageId,
                                final boolean isResend, final SipMsgAux msgAux) throws RemoteException {
        if (!loadSvc()){
            return;
        }
        final XService xsvc = svc;

        svc.enforceCallingOrSelfPermission(Constants.PERMISSION_PHONEX, null);
        //We have to ensure service is properly started and not just binded
        svc.startService(new Intent(svc, XService.class));
        executeJob(new SvcRunnable("sendMessageImpl") {
            @Override
            protected void doRun() throws XService.SameThreadException {
                if (svc.getPjManager() == null) {
                    svc.notifyUserOfMessage(xsvc.getString(R.string.connection_not_valid));
                    return;
                }

                // Calls pjService, which calls underlying c code.
                // Performs actual message send.
                XService.ToCall result = svc.getPjManager().sendMessage(callee, message, accountId, mime, msgAux);

                MessageManager.MessageSentDescriptor mDesc =
                        new MessageManager.MessageSentDescriptor(
                                messageId,
                                accountId,
                                message,
                                msg2store,
                                callee,
                                isResend,
                                result);

                try {
                    xsvc.getMsgManager().onMessageSent(mDesc);
                } catch (Throwable t) {
                    Log.e(TAG, "Exception in sendMessage", t);
                }
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int answer(final int callId, final int status) throws RemoteException {
        if (!loadSvc()){
            return -1;
        }

        svc.enforceCallingOrSelfPermission(Constants.PERMISSION_PHONEX, null);
        SvcReturnRunnable action = new SvcReturnRunnable("answer") {
            @Override
            protected Object runWithReturn() throws XService.SameThreadException {
                return (Integer) svc.getPjManager().callAnswer(callId, status);
            }
        };
        executeJob(action);
        //return (Integer) action.getResult();
        return Constants.SUCCESS;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public int hangup(final int callId, final int status) throws RemoteException {
        if (!loadSvc()){
            return -1;
        }

        svc.enforceCallingOrSelfPermission(Constants.PERMISSION_PHONEX, null);
        SvcReturnRunnable action = new SvcReturnRunnable("hangup") {
            @Override
            protected Object runWithReturn() throws XService.SameThreadException {
                Log.d(TAG, "About to hangup call with id: " + callId);
                return (Integer) svc.getPjManager().callHangup(callId, status);
            }
        };
        executeJob(action);

        return Constants.SUCCESS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int terminateCall(final int callId, final int status) throws RemoteException {
        if (!loadSvc()){
            return -1;
        }

        svc.enforceCallingOrSelfPermission(Constants.PERMISSION_PHONEX, null);
        SvcReturnRunnable action = new SvcReturnRunnable("terminateCall") {
            @Override
            protected Object runWithReturn() throws XService.SameThreadException {
                Log.d(TAG, "About to terminate call with id: " + callId);
                return (Integer) svc.getPjManager().callTerminate(callId, status);
            }
        };
        executeJob(action);

        return Constants.SUCCESS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int xfer(final int callId, final String callee) throws RemoteException {
        if (!loadSvc()){
            return -1;
        }

        svc.enforceCallingOrSelfPermission(Constants.PERMISSION_PHONEX, null);
        SvcReturnRunnable action = new SvcReturnRunnable("xfer") {
            @Override
            protected Object runWithReturn() throws XService.SameThreadException {
                return (Integer) svc.getPjManager().callXfer(callId, callee);
            }
        };
        executeJob(action);
        return (Integer) action.getResult();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int xferReplace(final int callId, final int otherCallId, final int options) throws RemoteException {
        if (!loadSvc()){
            return -1;
        }

        svc.enforceCallingOrSelfPermission(Constants.PERMISSION_PHONEX, null);
        Log.d(TAG, "xfer-replace");
        SvcReturnRunnable action = new SvcReturnRunnable() {
            @Override
            protected Object runWithReturn() throws XService.SameThreadException {
                return (Integer) svc.getPjManager().callXferReplace(callId, otherCallId, options);
            }
        };
        executeJob(action);
        return (Integer) action.getResult();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hold(final int callId) throws RemoteException {
        if (!loadSvc()){
            return -1;
        }

        svc.enforceCallingOrSelfPermission(Constants.PERMISSION_PHONEX, null);
        Log.d(TAG, "hold_call");
        SvcReturnRunnable action = new SvcReturnRunnable("hold") {
            @Override
            protected Object runWithReturn() throws XService.SameThreadException {
                return (Integer) svc.getPjManager().callHold(callId);
            }
        };
        executeJob(action);
        return (Integer) action.getResult();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int reinvite(final int callId, final boolean unhold) throws RemoteException {
        if (!loadSvc()){
            return -1;
        }

        svc.enforceCallingOrSelfPermission(Constants.PERMISSION_PHONEX, null);
        Log.d(TAG, "re-invite");
        SvcReturnRunnable action = new SvcReturnRunnable("reinvite") {
            @Override
            protected Object runWithReturn() throws XService.SameThreadException {
                return (Integer) svc.getPjManager().callReinvite(callId, unhold);
            }
        };
        executeJob(action);
        return (Integer) action.getResult();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SipCallSession getCallInfo(final int callId) throws RemoteException {
        if (!loadSvc()){
            return null;
        }

        svc.enforceCallingOrSelfPermission(Constants.PERMISSION_PHONEX, null);
        return svc.getPjManager().getCallInfo(callId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBluetoothOn(final boolean on) throws RemoteException {
        if (!loadSvc()){
            return;
        }

        svc.enforceCallingOrSelfPermission(Constants.PERMISSION_PHONEX, null);
        executeJob(new SvcRunnable("setBluetoothOn") {
            @Override
            protected void doRun() throws XService.SameThreadException {
                svc.getPjManager().setBluetoothOn(on);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMicrophoneMute(final boolean on) throws RemoteException {
        if (!loadSvc()){
            return;
        }

        svc.enforceCallingOrSelfPermission(Constants.PERMISSION_PHONEX, null);
        executeJob(new SvcRunnable("setMicrophoneMute") {
            @Override
            protected void doRun() throws XService.SameThreadException {
                svc.getPjManager().setMicrophoneMute(on);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSpeakerphoneOn(final boolean on) throws RemoteException {
        if (!loadSvc()){
            return;
        }

        svc.enforceCallingOrSelfPermission(Constants.PERMISSION_PHONEX, null);
        executeJob(new SvcRunnable("setSpeakerphoneOn") {
            @Override
            protected void doRun() throws XService.SameThreadException {
                svc.getPjManager().setSpeakerphoneOn(on);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SipCallSession[] getCalls() throws RemoteException {
        if (!loadSvc()){
            return null;
        }

        svc.enforceCallingOrSelfPermission(Constants.PERMISSION_PHONEX, null);
        if(svc.getPjManager() != null) {
            return svc.getPjManager().getCalls();
        }
        return new SipCallSession[0];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void confAdjustTxLevel(final int port, final float value) throws RemoteException {
        if (!loadSvc()){
            return;
        }

        svc.enforceCallingOrSelfPermission(Constants.PERMISSION_PHONEX, null);
        executeJob(new SvcRunnable("confAdjustTxLevel") {
            @Override
            protected void doRun() throws XService.SameThreadException {
                if (svc == null || svc.getPjManager() == null) {
                    return;
                }
                svc.getPjManager().confAdjustTxLevel(port, value);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void confAdjustRxLevel(final int port, final float value) throws RemoteException {
        if (!loadSvc()){
            return;
        }

        svc.enforceCallingOrSelfPermission(Constants.PERMISSION_PHONEX, null);
        executeJob(new SvcRunnable("confAdjustRxLevel") {
            @Override
            protected void doRun() throws XService.SameThreadException {
                if (svc == null || svc.getPjManager() == null) {
                    return;
                }
                svc.getPjManager().confAdjustRxLevel(port, value);
            }
        });

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void adjustVolume(SipCallSessionInfo callInfo, int direction, int flags) throws RemoteException {
        if (!loadSvc()){
            return ;
        }

        svc.enforceCallingOrSelfPermission(Constants.PERMISSION_PHONEX, null);
        if(svc.getPjManager() == null) {
            return;
        }

        boolean ringing = callInfo.isIncoming() && callInfo.isBeforeConfirmed();
        // Mode ringing
        if(ringing) {
            // What is expected here is to silence ringer
            //pjService.adjustStreamVolume(AudioManager.STREAM_RING, direction, AudioManager.FLAG_SHOW_UI);
            svc.getPjManager().silenceRinger();
        }else {
            // Mode in call
            if(svc.getPrefs().getBoolean(PhonexConfig.USE_SOFT_VOLUME)) {
                Intent adjustVolumeIntent = new Intent(svc, AudioSettingsActivity.class);
                adjustVolumeIntent.putExtra(Intent.EXTRA_KEY_EVENT, direction);
                adjustVolumeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                svc.startActivity(adjustVolumeIntent);
            }else {
                svc.getPjManager().adjustStreamVolume(
                        Compatibility.getInCallStream(svc.getPjManager().getMediaManager().doesUserWantBluetooth()),
                        direction,
                        flags);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEchoCancellation(final boolean on) throws RemoteException {
        if (!loadSvc()){
            return;
        }

        svc.enforceCallingOrSelfPermission(Constants.PERMISSION_PHONEX, null);
        if(svc.getPjManager() == null) {
            return;
        }

        executeJob(new SvcRunnable("setEchoCancellation") {
            @Override
            protected void doRun() throws XService.SameThreadException {
                svc.getPjManager().setEchoCancellation(on);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void zrtpSASVerified(final int callId) throws RemoteException {
        if (!loadSvc()){
            return;
        }

        svc.enforceCallingOrSelfPermission(Constants.PERMISSION_PHONEX, null);
        executeJob(new SvcRunnable("zrtpSASVerified") {
            @Override
            protected void doRun() throws XService.SameThreadException {
                svc.getPjManager().zrtpSASVerified(callId);
                svc.getPjManager().getZrtpCallback().updateZrtpInfo(callId);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void zrtpSASRevoke(final int callId) throws RemoteException {
        if (!loadSvc()){
            return;
        }

        svc.enforceCallingOrSelfPermission(Constants.PERMISSION_PHONEX, null);
        executeJob(new SvcRunnable("zrtpSASRevoke") {
            @Override
            protected void doRun() throws XService.SameThreadException {
                svc.getPjManager().zrtpSASRevoke(callId);
                svc.getPjManager().getZrtpCallback().updateZrtpInfo(callId);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLocalNatType() throws RemoteException {
        if (!loadSvc()){
            return "";
        }

        SvcReturnRunnable action = new SvcReturnRunnable("getLocalNatType") {
            @Override
            protected Object runWithReturn() throws XService.SameThreadException {
                return svc.getPjManager().getNatType();
            }
        };

        executeJob(action);
        return (String) action.getResult();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MediaState getCurrentMediaState() throws RemoteException {
        if (!loadSvc()){
            return null;
        }

        svc.enforceCallingOrSelfPermission(Constants.PERMISSION_PHONEX, null);
        MediaState ms = new MediaState();
        if(svc != null && svc.getPjManager() != null && svc.getPjManager().getMediaManager() != null) {
            ms = svc.getPjManager().getMediaManager().getMediaState();
        }
        return ms;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String showCallInfosDialog(final int callId) throws RemoteException {
        if (!loadSvc()){
            return "";
        }

        SvcReturnRunnable action = new SvcReturnRunnable("showCallInfosDialog") {
            @Override
            protected Object runWithReturn() throws XService.SameThreadException {
                StringBuilder sb = new StringBuilder();
                String infos     = PjCalls.dumpCallInfo(callId);
                String zrtpInfo  = PjCalls.dumpZRTPCallInfo(callId);
                Log.d(TAG, "CallInfo: " + infos);
                Log.d(TAG, "ZRTPCallInfo: " + zrtpInfo);

                sb.append(infos);
                sb.append("\n==== <ZRTP> ====\n");
                sb.append(zrtpInfo);
                sb.append("\n==== </ZRTP> ====");
                return sb.toString();
            }
        };

        executeJob(action);
        return (String) action.getResult();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int startLoopbackTest() throws RemoteException {
        if (!loadSvc()){
            return -1;
        }

        if(svc.getPjManager() == null) {
            return Constants.ERROR_CURRENT_NETWORK;
        }

        SvcRunnable action = new SvcRunnable() {
            @Override
            protected void doRun() throws XService.SameThreadException {
                svc.getPjManager().startLoopbackTest();
            }
        };

        executeJob(action);
        return Constants.SUCCESS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int stopLoopbackTest() throws RemoteException {
        if (!loadSvc()){
            return -1;
        }

        if(svc.getPjManager() == null) {
            return Constants.ERROR_CURRENT_NETWORK;
        }

        SvcRunnable action = new SvcRunnable() {
            @Override
            protected void doRun() throws XService.SameThreadException {
                svc.getPjManager().stopLoopbackTest();
            }
        };

        executeJob(action);
        return Constants.SUCCESS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long confGetRxTxLevel(final int port) throws RemoteException {
        if (!loadSvc()){
            return -1;
        }

        SvcReturnRunnable action = new SvcReturnRunnable() {
            @Override
            protected Object runWithReturn() throws XService.SameThreadException {
                return (Long) svc.getPjManager().getRxTxLevel(port);
            }
        };

        executeJob(action);
        return (Long) action.getResult();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateCallOptions(final int callId, final Bundle options) throws RemoteException {
        if (!loadSvc()){
            return;
        }

        executeJob(new SvcRunnable("updateCallOptions") {
            @Override
            protected void doRun() throws XService.SameThreadException {
                svc.getPjManager().updateCallOptions(callId, options);
            }
        });
    }

    @Override
    public void setStoragePassword(String pass) throws RemoteException {
        if (!loadSvc()){
            return;
        }
        svc.setStoragePass(pass);
    }

    @Override
    public String getStoragePassword() throws RemoteException {
        if (!loadSvc()){
            return null;
        }

        return svc.getStoragePass();
    }

    @Override
    public void triggerDHKeySync(long timeout) throws RemoteException {
        if (!loadSvc()) {
            return;
        }

        final long timeout2 = timeout;
        executeJob(new SvcRunnable("triggerDHKeySync") {
            @Override
            protected void doRun() throws XService.SameThreadException {
                svc.triggerDHKeyUpdate(timeout2);
            }
        });
    }

    @Override
    public void triggerCertUpdate(final List<CertUpdateParams> params) {
        if (!loadSvc()){
            return;
        }

        executeJob(new SvcRunnable("triggerCertUpdate") {
            @Override
            protected void doRun() throws XService.SameThreadException {
                svc.triggerCertUpdate(params, false);
            }
        });
    }

    @Override
    public void triggerCertUpdateEx(final List<CertUpdateParams> params, boolean allUsers) {
        if (!loadSvc()){
            return;
        }

        executeJob(new SvcRunnable("triggerCertUpdateEx") {
            @Override
            protected void doRun() throws XService.SameThreadException {
                svc.triggerCertUpdate(params, true);
            }
        });
    }

    @Override
    public List<CertUpdateProgress> getCertUpdateProgress() {
        if (!loadSvc()){
            return null;
        }

        SvcReturnRunnable action = new SvcReturnRunnable("getCertUpdateProgress") {
            @Override
            protected Object runWithReturn() throws XService.SameThreadException {
                if (svc.certUpdateTask == null) return null;
                return svc.certUpdateTask.getCertUpdateProgress();
            }
        };

        executeJob(action);
        Object obj = action.getResult();
        if (obj instanceof List<?>) {
            @SuppressWarnings("unchecked")
            final List<CertUpdateProgress> obj2 = (List<CertUpdateProgress>) obj;
            return obj2;
        } else {
            Log.w(TAG, "Obj is of a different type: " + obj);
            return new LinkedList<CertUpdateProgress>();
        }
    }

    @Override
    public List<KeyGenProgress> getDHKeyProgress() throws RemoteException {
        if (!loadSvc()){
            return null;
        }

        SvcReturnRunnable action = new SvcReturnRunnable("getDHKeyProgress") {
            @Override
            protected Object runWithReturn() throws XService.SameThreadException {
                return svc.getDHKeyProgress();
            }
        };

        executeJob(action);
        Object obj = action.getResult();
        if (obj instanceof List<?>) {
            @SuppressWarnings("unchecked")
            final List<KeyGenProgress> obj2 = (List<KeyGenProgress>) obj;
            return obj2;
        } else {
            Log.w(TAG, "Obj is of a different type: " + obj);
            return new LinkedList<KeyGenProgress>();
        }
    }

//    @Override
//    public void sendFiles(final long sipMessageId, final String destinationSip, final List<String> fileAbsolutePaths) {
//        if (!loadSvc()){
//            return;
//        }
//
//        executeJob(new SvcRunnable("sendFile") {
//            @Override
//            protected void doRun() throws XService.SameThreadException {
//                svc.sendFiles(sipMessageId, destinationSip, fileAbsolutePaths);
//            }
//        });
//    }

    @Override
    public void sendFiles(final String destinationSip, final List<String> fileAbsolutePaths, final String message) {
        if (!loadSvc()){
            Log.e(TAG, "Load service call failed");
            return;
        }

        executeJob(new SvcRunnable("sendFile") {
            @Override
            protected void doRun() throws XService.SameThreadException {
                svc.sendFiles(destinationSip, fileAbsolutePaths, message);
            }
        });
    }

    @Override
    public FileTransferProgress getFileTransferProgress(final long sipMessageId) throws RemoteException {
        if (!loadSvc()){
            return null;
        }

        class SvcReturnProgressRunnable extends SvcReturnRunnable {
            private long messageId;

            public void setMessageId(long id) {
                messageId = id;
            }

            @Override
            protected Object runWithReturn() throws XService.SameThreadException {
                return svc.getFileTransferProgress(messageId);
            }
        }

        SvcReturnProgressRunnable action = new SvcReturnProgressRunnable();
        action.setMessageId(sipMessageId);

        executeJob(action);
        Object obj = action.getResult();
        if (obj instanceof FileTransferProgress) {
            return (FileTransferProgress) obj;
        } else {
            Log.w(TAG, "Obj is of a different type: " + obj);
            // return zero-like progress in case of error
            return new FileTransferProgress(sipMessageId, "", 0);
        }
    }

    @Override
    public void decryptFile(final String uri) throws RemoteException {
        if (!loadSvc()){
            Log.e(TAG, "Load service call failed");
            return;
        }

        executeJob(new SvcRunnable("decryptFile") {
            @Override
            protected void doRun() throws XService.SameThreadException {
                svc.decryptFile(uri);
            }
        });
    }

    @Override
    public void decryptFiles(final List<String> uris) throws RemoteException {
        if (!loadSvc()){
            Log.e(TAG, "Load service call failed");
            return;
        }

        executeJob(new SvcRunnable("decryptFiles") {
            @Override
            protected void doRun() throws XService.SameThreadException {
                svc.decryptFiles(uris);
            }
        });
    }

    @Override
    public boolean isTaskRunningOrPending() {
        if (!loadSvc()){
            Log.e(TAG, "Load service call failed");
            return true;
        }

        class SvcReturnProgressRunnable extends SvcReturnRunnable {

            @Override
            protected Object runWithReturn() throws XService.SameThreadException {
                return svc.isTaskRunningOrPending();
            }
        }

        SvcReturnProgressRunnable action = new SvcReturnProgressRunnable();

        executeJob(action);
        Object obj = action.getResult();
        return (Boolean) obj;
    }

    @Override
    public void cancelDecrypt() throws RemoteException {
        if (!loadSvc()){
            Log.e(TAG, "Load service call failed");
            return;
        }

        executeJob(new SvcRunnable("cancelDecrypt") {
            @Override
            protected void doRun() throws XService.SameThreadException {
                svc.cancelDecrypt();
            }
        });
    }

    @Override
    public void downloadFile(final long sipMessageId, final String destinationDirectory) throws RemoteException {
        if (!loadSvc()){
            return;
        }

        executeJob(new SvcRunnable("downloadFile") {
            @Override
            protected void doRun() throws XService.SameThreadException {
                svc.downloadFile(sipMessageId, destinationDirectory, false);
            }
        });
    }

    @Override
    public void cancelDownload(final long sipMessageId) throws RemoteException {
        if (!loadSvc()){
            return;
        }

        executeJob(new SvcRunnable("cancelDownload") {
            @Override
            protected void doRun() throws XService.SameThreadException {
                svc.ftManager.cancelDownload(sipMessageId);
            }
        });
    }

    @Override
    public void cancelUpload(final long sipMessageId) throws RemoteException {
        if (!loadSvc()){
            return;
        }
        final XService xsvc = svc;

        executeJob(new SvcRunnable("cancelUpload") {
            @Override
            protected void doRun() throws XService.SameThreadException {
                xsvc.ftManager.cancelUpload(sipMessageId);
            }
        });
    }

    @Override
    public void deleteFileFromServer(final long sipMessageId) throws RemoteException {
        if (!loadSvc()){
            return;
        }
        final XService xsvc = svc;

        executeJob(new SvcRunnable("deleteFileFromServer") {
            @Override
            protected void doRun() throws XService.SameThreadException {
                xsvc.downloadFile(sipMessageId, null, true);
            }
        });
    }

    @Override
    public void triggerLoginStateSave() {
        if (!loadSvc()){
            return;
        }
        final XService xsvc = svc;

        executeJob(new SvcRunnable("triggerLoginStateSave") {
            @Override
            protected void doRun() throws XService.SameThreadException {
                xsvc.triggerLoginStateSave();
            }
        });
    }

    @Override
    public void quickLogin(String sip, String password, String domain) throws RemoteException {
        if (!loadSvc()){
            return;
        }
        final XService xsvc = svc;
        executeJob(new SvcRunnable("quickLogin") {
            @Override
            protected void doRun() throws XService.SameThreadException {
                xsvc.getLoginManager().triggerQuickLogin(sip, password, domain, false);
            }
        });
    }
}

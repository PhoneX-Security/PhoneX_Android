package net.phonex.sip;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.SparseArray;

import net.phonex.R;
import net.phonex.accounting.PermissionManager;
import net.phonex.core.Intents;
import net.phonex.pref.PhonexConfig;
import net.phonex.db.entity.SipCallSession;
import net.phonex.db.entity.SipCallSessionInfo;
import net.phonex.db.entity.SipProfile;
import net.phonex.core.SipUri;
import net.phonex.db.entity.QueuedMessage;
import net.phonex.pub.a.Compatibility;
import net.phonex.pub.a.MediaManager;
import net.phonex.pub.a.PjCalls;
import net.phonex.pub.a.PjManager;
import net.phonex.service.SvcRunnable;
import net.phonex.service.XService;
import net.phonex.service.XService.SameThreadException;
import net.phonex.service.messaging.MessageManager;
import net.phonex.service.messaging.MessageProtocolEnvelope;
import net.phonex.service.runEngine.AndroidTimers;
import net.phonex.service.runEngine.MyWakeLock;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.analytics.AnalyticsReporter;
import net.phonex.util.analytics.AppPassiveEvents;
import net.phonex.util.android.StatusbarNotifications;
import net.phonex.xv.Callback;
import net.phonex.xv.SWIGTYPE_p_int;
import net.phonex.xv.SWIGTYPE_p_pj_ice_strans_op;
import net.phonex.xv.SWIGTYPE_p_pjmedia_event;
import net.phonex.xv.SWIGTYPE_p_pjsip_rx_data;
import net.phonex.xv.SWIGTYPE_p_pjsip_transport;
import net.phonex.xv.Xvi;
import net.phonex.xv.esign_process_info;
import net.phonex.xv.esign_process_state_e;
import net.phonex.xv.pj_reg_backoff_struct;
import net.phonex.xv.pj_str_t;
import net.phonex.xv.pj_stun_nat_detect_result;
import net.phonex.xv.pjmedia_event_underflow_data;
import net.phonex.xv.pjsip_event;
import net.phonex.xv.pjsip_redirect_op;
import net.phonex.xv.pjsip_regc_cbparam;
import net.phonex.xv.pjsip_status_code;
import net.phonex.xv.pjsip_transport_state;
import net.phonex.xv.pjsip_transport_state_info;
import net.phonex.xv.pjsua_reg_info;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PjCallback extends Callback {
    private final static String TAG = "PjCallback";
    private final static String ACTION_PHONE_STATE_CHANGED = "android.intent.action.PHONE_STATE";

    private StatusbarNotifications notificationManager;
    private PjManager pjManager;

    // Time in ms during which we should not relaunch call activity again
    private static final long LAUNCH_TRIGGER_DELAY = 2000;
    private long lastLaunchCallHandler = 0;

    private int mPreferedHeadsetAction;
    private int mMicroSource;
    private int regTransport = 0;

    private PjCallbackHandler msgHandler;
    private HandlerThread handlerThread;

    private MyWakeLock ieventLock;
    private MyWakeLock icallLock;

    /**
     * Map callId to known {@link SipCallSession}. This is cache of known
     * session maintained by the UA state receiver. The UA state receiver is in
     * charge to maintain calls list integrity for {@link PjManager}. All
     * information it gets comes from the stack. Except recording status that
     * comes from service.
     */
    private final SparseArray<SipCallSession> callsList = new SparseArray<SipCallSession>();
    private Handler callCancellationHandler = new Handler();

    public static final int ON_CALL_STATE = 2;
    public static final int ON_MEDIA_STATE = 3;
    private PermissionManager permissionManager;

    /**
     * Event triggered from the PJSIP on incoming call request.
     * 
     * private class IncomingCallInfos { public SipCallSession callInfo; public
     * Integer accId; }
     */
    @Override
    public void on_incoming_call(final int accId, final int callId, SWIGTYPE_p_pjsip_rx_data rdata) {
        lockCpu("incoming_call");
        Log.df(TAG, "on_incoming_call: %s", callId);

        try {
            SipCallSessionInfo callInfo = updateCallInfoFromStack(callId, null, PjCalls.CALL_UPDATE_CALL_INCOMING);
            Log.df(TAG, "Incoming call << for account %s", accId);

            // Extra check if set reference counted is false ???
            if (icallLock!=null && !icallLock.isHeld()) {
                icallLock.lock("incoming_call");
            }

            final String remContact = callInfo.getRemoteContact();

            // Check if we have not already an ongoing call
            boolean hasOngoingSipCall = getNumOngoingCallActive(callId) > 0;
            boolean callRejected = false;

            // On BlackBerry there is no support yet.
            if (!Compatibility.isCallSupported()){
                Log.wf(TAG, "Incoming call, calls not supported");
                Xvi.call_hangup(callId, SipStatusCode.NOT_IMPLEMENTED, null, null);
                callRejected = true;
            }

            // Only one active call at time?
            if (!callRejected && hasOngoingSipCall && !pjManager.getService().isSupportMultipleCalls()){
                Log.wf(TAG, "Multiple calls at time are not supported");
                Xvi.call_hangup(callId, SipStatusCode.BUSY_HERE, null, null);
                callRejected = true;
            }
            
            // Signature ?
            if (!callRejected) {
                esign_process_info esign_info = new esign_process_info();
                Xvi.pjsip_rdata_get_signature(rdata, esign_info);
                if (esign_info.getProcess_state() == esign_process_state_e.ESIGN_PROCESS_STATE_NULL) {
                    Log.d(TAG, "on_incoming_call: Signature for INVITE packet not present");
                } else {
                    Log.df(TAG, "on_incoming_call: Signature for INVITE packet is here? %s; signatureValid=%s; dropped=%s",
                            esign_info.getSignature_present(),
                            esign_info.getSignature_valid(),
                            esign_info.getPacket_dropped());
                    if (esign_info.getSignature_present() > 0) {
                        Log.df(TAG, "on_incoming_call: signature[%s]", esign_info.getSign());
                    }
                }
            }

            // GSM call in progress?
            if (pjManager.getService() != null
                    && pjManager.getService().getGSMCallState() != TelephonyManager.CALL_STATE_IDLE)
            {
                Log.df(TAG, "GSM call already in place, have to answer with BUSY tone");
                pjManager.callAnswer(callId, SipStatusCode.BUSY_HERE);
                callRejected = true;
            }

            // Auto answer feature
            if (!callRejected) {
                SipProfile acc = pjManager.getAccountForPjsipId(accId);
                Bundle extraHdr = new Bundle();
                fillRDataHeader("Call-Info", rdata, extraHdr);

                final int shouldAutoReject = pjManager.getService().shouldAutoReject(remContact, acc, extraHdr);
                Log.df(TAG, "Auto reject: %s", shouldAutoReject);
                if (shouldAutoReject >= 200) {
                    pjManager.callHangup(callId, SipStatusCode.NOT_FOUND, callInfo.getConfPort());
                    Log.d(TAG, "Sent 404 to call.");
                    callRejected = true;
                }
            }

            // If not rejected, do start ringing.
            if (!callRejected) {
                // Ring and inform remote about ringing with 180/RINGING.
                notificationManager.notifyCall(callInfo);
                pjManager.callAnswer(callId, SipStatusCode.RINGING);

                if (pjManager.getMediaManager() != null) {
                    if (!hasOngoingSipCall) {
                        pjManager.getMediaManager().startRing(remContact);
                    } else {
                        pjManager.getMediaManager().playInCallTone(MediaManager.TONE_CALL_WAITING);
                    }
                }

                broadCastAndroidCallState("RINGING", remContact);
                launchCallHandler(callInfo);
                Log.d(TAG, "Incoming call >>");
            }

        } catch (SameThreadException e) {
            // That's fine we are in a pjsip thread
        } finally {
            unlockCpu();
        }
    }

    /**
     * Event triggered from the PJSIP on active call state change.
     */
    @SuppressLint("Wakelock")
	@Override
    public void on_call_state(final int callId, pjsip_event e) {
        Xvi.css_on_call_state(callId, e);
        lockCpu("call_state");

        Log.df(TAG, "<on_call_state callId: %s, evt: %s>", callId, e);
        try {
            // Get call id from the event.
            final SipCallSession update = new SipCallSession(callId);
            PjCalls.updateSessionFromEvent(update, true, e, null, pjManager.getService().getApplicationContext());

            // Get current info now on same thread cause fix has been done on pj
            final SipCallSessionInfo callInfo = updateCallInfoFromStack(callId, update, PjCalls.CALL_UPDATE_CALL_STATE);
            final int callState = callInfo.getCallState();

            // Ringing logic on a service handler, move away from callback thread.
            pjManager.getService().getHandler().execute(new SvcRunnable("on_call_state") {
                @Override
                public void doRun() throws SameThreadException {
                    if (callState == SipCallSession.InvState.DISCONNECTED) {
                        if (pjManager.getMediaManager() != null) {
                            pjManager.getMediaManager().stopRingAndUnfocus();
                            pjManager.getMediaManager().resetSettings();
                        }
                    }
                }
            });

            // If disconnected immediate stop required stuffs
            if (callState == SipCallSession.InvState.DISCONNECTED) {
                // if call termination is planned, cancel it
                callCancellationHandler.removeCallbacksAndMessages(null);
                if (icallLock != null) {
                    icallLock.unlock();
                }
            } else {
                if (icallLock != null && !icallLock.isHeld()) {
                    icallLock.lock("call_state");
                }
            }

            msgHandler.sendMessage(msgHandler.obtainMessage(ON_CALL_STATE, callInfo));
            Log.d(TAG, "</on_call_state>");
        } catch (SameThreadException ex) {
            // We don't care about that we are at least in a pjsua thread
        } finally {
            // Unlock CPU anyway
            unlockCpu();
        }
    }

    /**
     * Event triggered from the PJSIP on typing indication.
     */
    @Override
	public void on_typing(int call_id, pj_str_t from, pj_str_t to, pj_str_t contact, int is_typing) {
    	Log.df(TAG, "Typing indication from [%s] to [%s] contact [%s] typing [%s]", from, to, contact, is_typing);
	}

	/**
     * Event triggered from the PJSIP on SIP Message. 
     * Signalizes incoming SIP Message
     */
	@Override
	public void on_pager(int callId, pj_str_t from, pj_str_t to, pj_str_t contact, pj_str_t mime_type, pj_str_t body) {
		lockCpu("message");
		
		final long date = System.currentTimeMillis();
		final String fromStr = PjUtils.pjStrToString(from);
		final String canonicFromStr = SipUri.getCanonicalSipContact(fromStr, false);
		final String contactStr = PjUtils.pjStrToString(contact);
		final String toStr = SipUri.getCanonicalSipContact(PjUtils.pjStrToString(to), false);
		final String bodyStr = PjUtils.pjStrToString(body);
		final String mimeStr = PjUtils.pjStrToString(mime_type);

        Log.df(TAG, "on_pager; fromStr [%s],  toStr [%s], bodyStr [%s], mimeStr [%s]", fromStr, toStr, bodyStr, mimeStr);

//		final int dbType = mimeStr.equals(UserMessageSMIME.SIP_SECURE_FILE_NOTIFY_MIME) ? SipMessage.MESSAGE_TYPE_FILE_READY : SipMessage.MESSAGE_TYPE_INBOX;
		
		// Using async executor for message update. More safe to serialize.
        pjManager.getService().getHandler().execute(new SvcRunnable("msg_received") {
			@Override
			public void doRun() throws SameThreadException {
				// Load user profile for signaled recipient.
				SipProfile acc = null;
				try {
					acc = pjManager.getService().getAccount(toStr, false);
				} catch(Exception e){
					Log.e(TAG, "Cannot obtain SIP profile in on_pager()");
				}
				
				// Verify if the given contact is in my contact list
				// and whether I can accept message from him.
				if (acc!=null){
					Bundle extraHdr = new Bundle();
					final int shouldAutoReject1 = pjManager.getService().shouldAutoReject(canonicFromStr, acc, extraHdr);
					final int shouldAutoReject2 = pjManager.getService().shouldAutoReject(contactStr, acc, extraHdr);
					Log.d(TAG, "Auto-reject for incoming message: autorej1=" + shouldAutoReject1 + "; autorej2=" + shouldAutoReject2);
					
			        if (shouldAutoReject1 >= 200 && shouldAutoReject2 >= 200){
			        	Log.i(TAG, "Message dropped");
			        	
			        	unlockCpu();
			        	return;
			        }
				} else {
					Log.w(TAG, "Recipient with sip ["+toStr+"] was not found in database.");
					unlockCpu();
		        	return;
				}

                // Fix for prepended offline text metadata (possibly (not always) added by SipServer)
                String body = MessageManager.removeOfflineMessageMetadata(bodyStr);

                // Create message
                QueuedMessage messageToQueue = new QueuedMessage(canonicFromStr, toStr, false);
                messageToQueue.setTime(date);
                messageToQueue.setIsProcessed(false);
                messageToQueue.setMimeType(mimeStr);

                try {
                    MessageProtocolEnvelope envelope = MessageProtocolEnvelope.createEnvelope(body);
                    messageToQueue.setTransportProtocolType(envelope.getProtocolType());
                    messageToQueue.setTransportProtocolVersion(envelope.getProtocolVersion());
                    messageToQueue.setEnvelopePayload(envelope.getPayload());

                } catch (Exception e) {
                    Log.w(TAG, "Cannot parse message body, probably old version of sip messages.", e);
                    Log.inf(TAG, "Message dropped");
                    return;
                }

				// Insert message to DB.
                Log.df(TAG, "Inserting QueuedMessage in message queue [%s]", messageToQueue);
				ContentResolver cr = pjManager.getService().getContentResolver();
                cr.insert(QueuedMessage.URI, messageToQueue.getDbContentValues());

                // Broadcast the message
                Intent intent = new Intent(Intents.ACTION_MESSAGE_RECEIVED);
                intent.putExtra(QueuedMessage.FIELD_FROM, messageToQueue.getFrom());
                intent.putExtra(QueuedMessage.FIELD_IS_OUTGOING, false);
                intent.putExtra(QueuedMessage.FIELD_TRANSPORT_PROTOCOL_TYPE, messageToQueue.getTransportProtocolType());
                intent.putExtra(QueuedMessage.FIELD_TRANSPORT_PROTOCOL_VERSION, messageToQueue.getTransportProtocolVersion());
                MiscUtils.sendBroadcast(pjManager.getService(), intent);
			}
		});
		
		unlockCpu();
	}

	/**
     * Event triggered from the PJSIP on SIP Message. 
     * Signalizes state of sent SIP Message
     */
	@Override
	public void on_pager_status(int callId, final pj_str_t to, final pj_str_t body, final pjsip_status_code status, final pj_str_t reason) {
		lockCpu("message_status");
		
		final boolean success = status.equals(pjsip_status_code.PJSIP_SC_OK) || status.equals(pjsip_status_code.PJSIP_SC_ACCEPTED);

		final String toStr = SipUri.getCanonicalSipContact(PjUtils.pjStrToString(to), false);
		final String reasonStr = PjUtils.pjStrToString(reason);
		final String bodyStr = PjUtils.pjStrToString(body);
		
		int statusInt_tmp = 0;
		boolean statusOk_tmp = success;
		try {
			statusInt_tmp = status.swigValue();
			statusOk_tmp = statusInt_tmp == SipStatusCode.OK || statusInt_tmp == SipStatusCode.ACCEPTED;
		} catch(Exception e){
			Log.e(TAG, "Unable to determine status integer counterpart", e);
		}
		
		// Final variables
		final int statusInt = statusInt_tmp;
		final boolean statusOk = statusOk_tmp;

        final MessageManager messageManager = pjManager.getService().getMsgManager();
		
		Log.df(TAG, "SipMessage in on pager status %s / %s", status.toString(), reasonStr);
		pjManager.getService().getHandler().execute(new SvcRunnable("msg_status") {
			@Override
			public void doRun() throws SameThreadException {
                // acknowledgment returns the same message, we check it inside MessageManager
                messageManager.acknowledgmentFromPjSip(toStr, bodyStr, statusOk, reasonStr, statusInt);
			}
		});
		
        unlockCpu();
    }

    @Override
    public void on_reg_state(final int accountId) {
        lockCpu("on_reg_state");
        pjManager.getService().getHandler().execute(new SvcRunnable("on_reg_state") {
            @Override
            public void doRun() throws SameThreadException {
                // Update java infos
                pjManager.updateProfileStateFromService(accountId);
            }
        });
        unlockCpu();
    }

    @Override
    public void on_call_media_state(final int callId) {
        Log.vf(TAG, "on_call_media_state");
        Xvi.css_on_call_media_state(callId);

        lockCpu("call_media_state");
        pjManager.getService().getHandler().execute(new SvcRunnable("on_call_media_state") {
            @Override
            public void doRun() throws SameThreadException {
                if (pjManager.getMediaManager() != null) {
                    // Do not unfocus here since we are probably in call.
                    // Unfocus will be done anyway on call disconnect
                    pjManager.getMediaManager().stopRing();
                }
            }
        });

        try {
            final SipCallSessionInfo callInfo = updateCallInfoFromStack(callId, null, PjCalls.CALL_UPDATE_MEDIA_STATE);

            /*
             * Connect ports appropriately when media status is ACTIVE or REMOTE
             * HOLD, otherwise we should NOT connect the ports.
             */
            if (callInfo.getMediaStatus() == SipCallSession.MediaState.ACTIVE ||
                    callInfo.getMediaStatus() == SipCallSession.MediaState.REMOTE_HOLD) {
                // Old conference connect was done here.
                Log.vf(TAG, "Media active, old conference connect was done here");
            }

            msgHandler.sendMessage(msgHandler.obtainMessage(ON_MEDIA_STATE, callInfo));
        } catch (SameThreadException e) {
            // Nothing to do we are in a pj thread here
        }

        unlockCpu();
    }

    @Override
    public void on_mwi_info(int acc_id, final pj_str_t mime_type, final pj_str_t body) {
        lockCpu("mwi_info");
        // Treat incoming voice mail notification.

        String msg = PjUtils.pjStrToString(body);
        // Log.d(THIS_FILE, "We have a message :: " + acc_id + " | " +
        // mime_type.getPtr() + " | " + body.getPtr());

        boolean hasMessage = false;
        int numberOfMessages = 0;
        // String accountNbr = "";

        String lines[] = msg.split("\\r?\\n");
        // Decapsulate the application/simple-message-summary
        // TODO : should we check mime-type?
        // rfc3842
        Pattern messWaitingPattern = Pattern.compile(".*Messages-Waiting[ \t]?:[ \t]?(yes|no).*", Pattern.CASE_INSENSITIVE);
        // Pattern messAccountPattern =
        // Pattern.compile(".*Message-Account[ \t]?:[ \t]?(.*)",
        // Pattern.CASE_INSENSITIVE);
        Pattern messVoiceNbrPattern = Pattern.compile(".*Voice-Message[ \t]?:[ \t]?([0-9]*)/[0-9]*.*", Pattern.CASE_INSENSITIVE);

        for (String line : lines) {
            Matcher m;
            m = messWaitingPattern.matcher(line);
            if (m.matches()) {
                Log.wf(TAG, "Matches: %s", m.group(1));
                if ("yes".equalsIgnoreCase(m.group(1))) {
                    Log.d(TAG, "Has some voice messages");
                    hasMessage = true;
                }
                continue;
            }

            /*
             * m = messAccountPattern.matcher(line); if(m.matches()) {
             * accountNbr = m.group(1); Log.d(THIS_FILE, "VM acc : " +
             * accountNbr); continue; }
             */
            m = messVoiceNbrPattern.matcher(line);
            if (m.matches()) {
                try {
                    numberOfMessages = Integer.parseInt(m.group(1));
                } catch (NumberFormatException e) {
                    Log.wf(TAG, "Number parsing exception [%s]", m.group(1));
                }
                Log.df(TAG, "Contact: %s", numberOfMessages);
                continue;
            }
        }

        if (hasMessage && numberOfMessages > 0) {
            SipProfile acc = pjManager.getAccountForPjsipId(acc_id);
            //notificationManager.notifyNewVoiceMail(acc, numberOfMessages);
        }

        unlockCpu();
    }
    
    /** (non-Javadoc)
     * @see net.phonex.xv.Callback#on_call_transfer_status(int, int, net.phonex.xv.pj_str_t, int, net.phonex.xv.SWIGTYPE_p_int)
     */
    @Override
    public void on_call_transfer_status(int callId, int st_code, pj_str_t st_text, int final_, SWIGTYPE_p_int p_cont) {
    	lockCpu("call_transfer");
        if((st_code / 100) == 2) {
            Log.inf(TAG, "Transfer done, hangup, callId: %s", callId);
        	Xvi.call_hangup(callId, 0, null, null);
        }
        unlockCpu();
    }

    public int on_validate_audio_clock_rate(int clockRate) {
        if (pjManager != null) {
            return pjManager.validateAudioClockRate(clockRate);
        }
        return -1;
    }

    @Override
    public void on_setup_audio(int beforeInit) {
        if (pjManager != null) {
            pjManager.setAudioInCall(beforeInit);
        }
    }

    @Override
    public void on_teardown_audio() {
        if (pjManager != null) {
            pjManager.unsetAudioInCall();
        }
    }

    @Override
    public pjsip_redirect_op on_call_redirected(int call_id, pj_str_t target) {
        Log.wf(TAG, "call_redirected %s", PjUtils.pjStrToString(target));
        return pjsip_redirect_op.PJSIP_REDIRECT_ACCEPT;
    }

    @Override
    public void on_nat_detect(final pj_stun_nat_detect_result res) {
        Log.df(TAG, "NAT type detected: %s", res.getNat_type_name());
        if (pjManager ==null) {
        	return;
        }
        
        final String natTypeName = res.getNat_type_name();
        final int natStatus = res.getStatus();
        pjManager.getService().getHandler().execute(new SvcRunnable("nat_detected") {
            @Override
            public void doRun() throws SameThreadException {
                if (pjManager != null) {
                    pjManager.setNatType(natTypeName, natStatus);
                }
            }
        });
        
    }

    @Override
    public int on_set_micro_source() {
        return mMicroSource;
    }

    @Override
    public int timer_schedule(int entry, int entryId, int time) {
        return AndroidTimers.schedule(entry, entryId, time);
    }

    @Override
    public int timer_cancel(int entry, int entryId) {
        return AndroidTimers.cancel(entry, entryId);
    }

    @Override
    public void on_reregistration_compute_backoff(int acc_id, pj_reg_backoff_struct backoff_struct) {
        final long attempt_cnt = backoff_struct.getAttempt_cnt();
        Log.df(TAG, "Registration backoff, attempt: %s, delay: %s.%s",
                attempt_cnt,
                backoff_struct.getDelay().getSec(),
                backoff_struct.getDelay().getMsec());

        if (pjManager ==null) {
            return;
        }

        final boolean inBg = false;
        final boolean xmppWorked = true;
        final int numCalls = pjManager.getActiveCallInProgress() == null ? 0 : 1;
        boolean connected = pjManager.isConnected();
        boolean rechecked = false;

        // If not connected, verify by directly checking service connectivity
        if (!connected && pjManager.getService() != null){
            connected = pjManager.getService().isConnectionValid();
            rechecked = true;
        }

        // Delay parameters.
        long delayBase = 60000;
        long delayRandom = 10000;

        // do-while(0) for using break statement.
        do {
            // If internet connection is off, it makes no sense to do re-registration.
            if (!connected){
                delayBase = 1000l*60l*10l;
                delayRandom = 1000;
                break;
            }

            // Foreground mode for now on.
            // When call is active, do it more aggressively.
            if (numCalls > 0) {
                if (attempt_cnt >= 0 && attempt_cnt < 15) {
                    delayBase = 2000;
                    delayRandom = 1000;
                } else if (attempt_cnt < 30) {
                    delayBase = 6000;
                    delayRandom = 3000;
                } else {
                    delayBase = 60000;
                    delayRandom = 10000;
                }

                break;
            }

            // Normal foreground operation. Do it quite quickly.
            if (attempt_cnt >= 0 && attempt_cnt < 10){
                delayBase = 3000;
                delayRandom = 1000;
            } else if (attempt_cnt < 25) {
                delayBase = 6000;
                delayRandom = 3000;
            } else if ((attempt_cnt % 3) == 0){
                // Normal waiting, attempt count too high, not a fact reconnect.
                delayBase = 90000;
                delayRandom = 10000;
            } else {
                // Fast reconnect in the long attempt phases.
                delayBase = 5000;
                delayRandom = 2000;
            }

        } while(false);

        setRegistrationDelayParameters(delayBase, delayRandom, backoff_struct);
        Log.vf(TAG, "Re-registration backoff called for id: %s, attempt: %s, delay: %s s. inBg: %s, connected: %s, " +
                        "rechecked: %s, xmppOn: %s, activeCalls: %s. DelayBase: %s, delayRandom: %s, curDelay: %s.%s",
                acc_id, attempt_cnt, backoff_struct.getDelay().getSec(), inBg, connected, rechecked,
                xmppWorked, numCalls, delayBase, delayRandom,
                backoff_struct.getDelay().getSec(),
                backoff_struct.getDelay().getMsec());
    }

    @Override
    public void on_transport_state(SWIGTYPE_p_pjsip_transport tp, pjsip_transport_state state, pjsip_transport_state_info info) {
        final int transport_ptr = Xvi.get_transport_ptr(tp);
        boolean wasRegTransport = false;

        Log.vf(TAG, "on_transport_state, ptr: 0x%x, state: %s", transport_ptr, state);
        if (state != pjsip_transport_state.PJSIP_TP_STATE_DISCONNECTED){
            return;
        }

        if (transport_ptr == regTransport){
            Log.vf(TAG, "on_transport_state, Registration transport disconnected, ptr: 0x%x", transport_ptr);
        }
    }

    @Override
    public void on_reg_state2(int acc_id, pjsua_reg_info info) {
        final SWIGTYPE_p_pjsip_transport transport = Xvi.get_transport(info);
        final int transport_ptr = Xvi.get_transport_ptr(transport);
        boolean newReg = false;

        final pjsip_regc_cbparam cbparm = info == null ? null : info.getCbparam();
        final int code = cbparm == null ? -1 : cbparm.getCode();
        final int expiration = cbparm == null ? -1 : cbparm.getExpiration();

        if (cbparm != null){
            if (code/100 == 2 && expiration > 0){
                regTransport = transport_ptr;
                newReg = true;

                Log.vf(TAG, "on_reg_state2: New registration, transport ptr: 0x%x, code: %s, expiration: %s",
                        transport_ptr, code, expiration);

            } else if (expiration == 0 && regTransport == transport_ptr){

                regTransport = 0;
            }
        }

        if (!newReg) {
            Log.vf(TAG, "on_reg_state2: transport ptr: 0x%x, regTransport: 0x%x, code: %s, expiration: %s",
                    transport_ptr, regTransport, code, expiration);
        }
    }

    @Override
    public void on_snd_dev_operation(int operation) {
        Log.vf(TAG, "on_snd_dev_operation %s", operation);
    }

    @Override
    public void on_call_media_event(int call_id, long med_idx, SWIGTYPE_p_pjmedia_event event) {
        Log.vf(TAG, "on_call_media_event call_id: %s, med_idx: %s", call_id, med_idx);
        if (Xvi.sipstack_is_underflow_event(event) == 0){
            return;
        }

        lockCpu("callMediaEvent");
        final pjmedia_event_underflow_data underflowData = Xvi.sipstack_get_underflow_data(event);
        final int conf_port_idx = underflowData.getConf_port_idx();
        final double underflow_ratio = underflowData.getUnderflow_ratio();
        final int underflow_status = underflowData.getUnderflow_status();

        try {
            Log.vf(TAG, "Underflow event, port: %s, status: %s, ratio: %s", conf_port_idx, underflow_status, underflow_ratio);
            pjManager.executeOnServiceHandler(new SvcRunnable("Underflow event") {
                @Override
                protected void doRun() throws SameThreadException {
                    pjManager.onUnderflowEvent(call_id, med_idx, conf_port_idx, underflow_status, underflow_ratio);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Exception", e);
        } finally {
            unlockCpu();
        }
    }

    @Override
    public void on_ice_transport_error(int index, SWIGTYPE_p_pj_ice_strans_op op, int status) {
        Log.vf(TAG, "on_ice_transport_error index: %s, status: %s", index, status);
    }

    public void setRegistrationDelayParameters(long delayBase, long delayRandom, pj_reg_backoff_struct backoff_struct) {
        // Time computation.
        long sec = delayBase/1000;
        long msec = delayBase%1000;

        // Randomizing part.
        if (sec >= delayRandom/1000) {
            msec += -delayRandom + (Math.random() * (delayRandom * 2));
        } else {
            msec += (Math.random() * (sec * 1000 + delayRandom));
            sec = 0;
        }

        backoff_struct.getDelay().setSec((int) sec);
        backoff_struct.getDelay().setMsec((int) msec);
    }

    /**
     * Update the call information from pjsip stack by calling pjsip primitives.
     * Warning, contract change. This method returns copy of a SipCallSession to avoid
     * race conditions.
     *
     * @param callId The id to the call to update
     * @param updates Changes to the sip call session made by the caller to me taken into account. i.e. values extracted from the stack in the callback.
     * @return The copy of SipCallSession corresponding to the call state at the moment.
     * @throws SameThreadException if we are calling that from outside the pjsip
     *             thread. It's a virtual exception to make sure not called from
     *             bad place.
     */
    public SipCallSessionInfo updateCallInfoFromStack(Integer callId, SipCallSession updates, Integer updateCode) throws SameThreadException {
        final SipCallSession sipCallSession = updateCallInfoFromStackInternal(callId, updates, updateCode);
        return sipCallSession == null ? null : sipCallSession.copy();
    }

    /**
     * Updates internal call representation from the stack calling native functions to get PJSIP call state.
     * @param callId
     * @param updates Changes to the sip call session made by the caller to me taken into account. i.e. values extracted from the stack in the callback.
     * @param updateCode
     * @return
     * @throws SameThreadException
     */
    public SipCallSession updateCallInfoFromStackInternal(Integer callId, SipCallSession updates, Integer updateCode) throws SameThreadException {
        SipCallSession callInfo;
        Log.df(TAG, "Updating call info, callId: %s", callId);
        synchronized (callsList) {
            callInfo = callsList.get(callId);
            if (callInfo == null) {
                callInfo = new SipCallSession();
                callInfo.setCallId(callId);
            }
        }
        // We update session infos. callInfo is both in/out and will be updated
        PjCalls.updateSessionFromPj(callInfo, updates, pjManager.getService(), pjManager, updateCode);
        synchronized (callsList) {
            // Re-add to list mainly for case newly added session
            callsList.put(callId, callInfo);
        }
        return callInfo.copy();
    }

    /**
     * General SipCallSession update.
     * @param callId
     * @param updateTask task to update call session
     * @return
     * @throws SameThreadException
     */
    public SipCallSession updateCallInfoByTask(Integer callId, SipCallSessionUpdateTask updateTask) throws SameThreadException {
        SipCallSession callInfo;
        Log.df(TAG, "Updating call info, callId: %s", callId);
        synchronized (callsList) {
            callInfo = callsList.get(callId);
            if (callInfo == null) {
                callInfo = new SipCallSession();
                callInfo.setCallId(callId);
            }
        }
        // We update session infos. callInfo is both in/out and will be updated
        callInfo = updateTask.updateCallSession(callInfo);
        synchronized (callsList) {
            // Re-add to list mainly for case newly added session
            callsList.put(callId, callInfo);
        }
        return callInfo.copy();
    }

    /**
     * Get call info for a given call id.
     * 
     * @param callId the id of the call we want infos for
     * @return the call session infos.
     */
    public SipCallSession getCallInfo(Integer callId) {
        SipCallSession callInfo;
        synchronized (callsList) {
            callInfo = callsList.get(callId, null);
        }
        return callInfo;
    }

    /**
     * Get list of calls session available.
     * 
     * @return List of calls.
     */
    public SipCallSession[] getCalls() {
        if (callsList != null) {
            List<SipCallSession> calls = new ArrayList<SipCallSession>();

            for (int i = 0; i < callsList.size(); i++) {
                SipCallSession callInfo = getCallInfo(i);
                if (callInfo != null) {
                    calls.add(callInfo.copy());
                }
            }
            return calls.toArray(new SipCallSession[calls.size()]);
        }
        return new SipCallSession[0];
    }

    public void initService(PjManager srv) {
        pjManager = srv;
        notificationManager = pjManager.getService().getNotificationManager();
        permissionManager = pjManager.getService().getPermissionManager();

        if (handlerThread == null) {
            handlerThread = new HandlerThread("net.phonex.cbhandler");
            handlerThread.start();
        }
        if (msgHandler == null) {
            msgHandler = new PjCallbackHandler(handlerThread.getLooper(), this);
        }

        if (ieventLock==null){
            ieventLock = MyWakeLock.newInstance(pjManager.getService(), "net.phonex.inEventLock", true);
        }

        if (icallLock==null){
            icallLock = MyWakeLock.newInstance(pjManager.getService(), "net.phonex.callLock", false);
        }
    }

    public void stopService() {
    	Log.v(TAG, "stopService()");
    	
        MiscUtils.stopHandlerThread(handlerThread, true);
        handlerThread = null;
        msgHandler = null;

        // Ensure lock is released since this lock is a ref counted one.
        MyWakeLock.deinitIfNotNull(ieventLock);
        MyWakeLock.deinitIfNotNull(icallLock);
    }

    public void reconfigure(Context ctxt) {
        mPreferedHeadsetAction = PhonexConfig.getIntegerPref(ctxt, PhonexConfig.HEADSET_ACTION, PhonexConfig.HEADSET_ACTION_CLEAR_CALL);
        mMicroSource = PhonexConfig.getIntegerPref(ctxt, PhonexConfig.MICRO_SOURCE);
    }

    /**
     * Broadcast intent about the fact we are currently have a sip
     * call state change.<br/>
     * This may be used by third party applications that wants to track
     * pjsip call state
     * 
     * @param callInfo the new call state infos
     */
    public void onBroadcastCallState(final SipCallSessionInfo callInfo) {
        // Internal event
        Intent callStateChangedIntent = new Intent(Intents.ACTION_SIP_CALL_CHANGED);
        callStateChangedIntent.putExtra(Intents.EXTRA_CALL_INFO, callInfo);
        MiscUtils.sendBroadcast(pjManager.getService(), callStateChangedIntent);
    }

    /**
     * Broadcast to android system that we currently have a phone call. This may
     * be managed by other sip apps that want to keep track of incoming calls
     * for example.
     * 
     * @param state The state of the call
     * @param number The corresponding remote number
     */
    public void broadCastAndroidCallState(String state, String number) {
        // Android normalized event
    	if(!Compatibility.isApiGreaterOrEquals(19)) {
			// Not allowed to do that from kitkat    		
			Intent intent = new Intent(ACTION_PHONE_STATE_CHANGED);
			intent.putExtra(TelephonyManager.EXTRA_STATE, state);
		    if (number != null) {
		    	intent.putExtra(TelephonyManager.EXTRA_INCOMING_NUMBER, number);
		    }
		    intent.putExtra(pjManager.getService().getString(R.string.application_name), true);
		    pjManager.getService().sendBroadcast(intent, android.Manifest.permission.READ_PHONE_STATE);
    	}
    }

    /**
     * Start the call activity for a given Sip Call Session. <br/>
     * The call activity should take care to get any ongoing calls when started
     * so the currentCallInfo2 parameter is indication only. <br/>
     * This method ensure that the start of the activity is not fired too much
     * in short delay and may just ignore requests if last time someone ask for
     * a launch is too recent
     * 
     * @param currentCallInfo2 the call info that raise this request to open the
     *            call handler activity
     */
    public synchronized void launchCallHandler(SipCallSessionInfo currentCallInfo2) {
        long currentElapsedTime = SystemClock.elapsedRealtime();
        // Synchronized ensure we do not get this launched several time
        // We also ensure that a minimum delay has been consumed so that we do
        // not fire this too much times
        // Specially for EARLY - CONNECTING states
        if (lastLaunchCallHandler + LAUNCH_TRIGGER_DELAY < currentElapsedTime) {
            Context ctxt = pjManager.getService();

            // Launch activity to choose what to do with this call
            Intent callHandlerIntent = XService.buildCallUiIntent(ctxt, currentCallInfo2);

            AnalyticsReporter.fromApplicationContext(ctxt.getApplicationContext()).passiveEvent(AppPassiveEvents.CALL_RECEIVED);
            Log.d(TAG, "Anounce call activity");
            ctxt.startActivity(callHandlerIntent);

            lastLaunchCallHandler = currentElapsedTime;
        } else {
            Log.d(TAG, "Ignore extra launch handler");
        }
    }

    /**
     * Check if any of call infos indicate there is an active call in progress.
     * 
     * @see SipCallSession#isActive()
     */
    public SipCallSession getActiveCallInProgress() {
        // Go through the whole list of calls and find the first active state.
        for (int i = 0; i < callsList.size(); i++) {
            SipCallSession callInfo = getCallInfo(i);
            if (callInfo != null && callInfo.isActive()) {
                return callInfo;
            }
        }
        return null;
    }

    /**
     * Check if any of call infos indicate there is an active call in progress.
     *
     * @see SipCallSession#isActive()
     */
    public SipCallSession getActiveCallOngoing() {
        // Go through the whole list of calls and find the first active state.
        for (int i = 0; i < callsList.size(); i++) {
            SipCallSession callInfo = getCallInfo(i);
            if (callInfo != null && callInfo.isActive() && callInfo.isOngoing()) {
                return callInfo;
            }
        }
        return null;
    }

    /**
     * Check if any of call infos indicate there is an active call in progress.
     *
     * @see SipCallSession#isActive()
     */
    public List<SipCallSession> getActiveCallsOngoing() {
        // Go through the whole list of calls and find the first active state.
        ArrayList<SipCallSession> arr = new ArrayList<>();

        for (int i = 0; i < callsList.size(); i++) {
            SipCallSession callInfo = getCallInfo(i);
            if (callInfo != null && callInfo.isActive() && callInfo.isOngoing()) {
                arr.add(callInfo);
            }
        }

        return arr;
    }

    /**
     * Broadcast the Headset button press event internally if there is any call
     * in progress. TODO : register and unregister only while in call
     */
    public boolean handleHeadsetButton() {
        final SipCallSession callInfo = getActiveCallInProgress();
        if (callInfo != null) {
            // Headset button has been pressed by user. If there is an
            // incoming call ringing the button will be used to answer the
            // call. If there is an ongoing call in progress the button will
            // be used to hangup the call or mute the microphone.
            int state = callInfo.getCallState();
            if (callInfo.isIncoming() &&
                    (state == SipCallSession.InvState.INCOMING ||
                    state == SipCallSession.InvState.EARLY)) {
                if (pjManager != null && pjManager.getService() != null) {
                    pjManager.getService().getHandler().execute(new SvcRunnable("callAnswer") {
                        @Override
                        protected void doRun() throws SameThreadException {
                            pjManager.callAnswer(callInfo.getCallId(), pjsip_status_code.PJSIP_SC_OK.swigValue());
                        }
                    });
                }
                return true;
            } else if (state == SipCallSession.InvState.INCOMING ||
                    state == SipCallSession.InvState.EARLY ||
                    state == SipCallSession.InvState.CALLING ||
                    state == SipCallSession.InvState.CONFIRMED ||
                    state == SipCallSession.InvState.CONNECTING) {
                //
                // In the Android phone app using the media button during
                // a call mutes the microphone instead of terminating the call.
                // We check here if this should be the behavior here or if
                // the call should be cleared.
                //
                if (pjManager != null && pjManager.getService() != null) {
                    pjManager.getService().getHandler().execute(new SvcRunnable("headsetButton") {

                        @Override
                        protected void doRun() throws SameThreadException {
                            if (mPreferedHeadsetAction == PhonexConfig.HEADSET_ACTION_CLEAR_CALL) {
                                pjManager.callHangup(callInfo.getCallId(), 0, callInfo.getConfPort());
                            } else if (mPreferedHeadsetAction == PhonexConfig.HEADSET_ACTION_HOLD) {
                                pjManager.callHold(callInfo.getCallId());
                            } else if (mPreferedHeadsetAction == PhonexConfig.HEADSET_ACTION_MUTE) {
                                pjManager.getMediaManager().toggleMute();
                            }
                        }
                    });
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Updates call info from the stack and broadcasts ON_MEDIA_STATE message to the handlers.
     * Should be called on Service Executor.
     * @param callId
     * @throws SameThreadException
     */
    public void updateCallMediaState(int callId) throws SameThreadException {
        SipCallSessionInfo callInfo = updateCallInfoFromStack(callId, null, null);
        Log.vf(TAG, "updateCallMediaState, callId: %s, session: %s", callId, callInfo);

        // when ZRTP is established + call is still active, update Intent in StatusbarNotification
        if (!callInfo.isAfterEnded()){
            notificationManager.notifyCall(callInfo);
        }
        msgHandler.sendMessage(msgHandler.obtainMessage(ON_MEDIA_STATE, callInfo));
    }

    /**
     * Wake lock helper.
     * @param holder
     */
    public void lockCpu(String holder) {
        if (ieventLock==null){
            Log.w(TAG, "Lock is null");
            return;
        }

        ieventLock.lock(holder);
    }

    /**
     * Wake lock helper.
     */
    public void unlockCpu() {
        if (ieventLock==null){
            Log.w(TAG, "Lock is null");
            return;
        }

        ieventLock.unlock();
    }

    /**
     * Returns number of active ongoing call on the stack.
     * If besidesThisCallId is non-null, call with this callID is ignored.
     *
     * @param besidesThisCallId
     * @return
     */
    private int getNumOngoingCallActive(Integer besidesThisCallId){
        // Check if we have not already an ongoing call
        int numCalls = 0;
        try {
            if (pjManager != null && pjManager.getService() != null) {
                SipCallSession[] calls = getCalls();
                if (calls != null) {
                    for (SipCallSession existingCall : calls) {
                        if (existingCall.isAfterEnded()) {
                            continue;
                        }

                        if (besidesThisCallId != null && besidesThisCallId.equals(existingCall.getCallId())) {
                            continue;
                        }

                        numCalls += 1;
                    }
                }
            }
        } catch(Exception e){
            Log.e(TAG, "Exception in determining active calls", e);
        }

        return numCalls;
    }

    public PjManager getPjManager() {
        return pjManager;
    }

    public StatusbarNotifications getNotificationManager() {
        return notificationManager;
    }

    public PermissionManager getPermissionManager() {
        return permissionManager;
    }

    public SparseArray<SipCallSession> getCallsList() {
        return callsList;
    }

    /**
     * Helper method for filling Rdata headers into message.
     * @param hdrName
     * @param rdata
     * @param out
     * @throws SameThreadException
     */
    private void fillRDataHeader(String hdrName, SWIGTYPE_p_pjsip_rx_data rdata, Bundle out) throws SameThreadException {
        String valueHdr = PjUtils.pjStrToString(Xvi.get_rx_data_header(Xvi.pj_str_copy(hdrName), rdata));
        if (!TextUtils.isEmpty(valueHdr)) {
            out.putString(hdrName, valueHdr);
        }
    }

    /**
     * When zrtp is established, start call termination handler if user has limited amount of seconds by his license
     * @param callId
     */
    public void onZrtpEstablished(int callId) {
        SipCallSession callInfo = getCallInfo(callId);
        Log.vf(TAG, "onZrtpEstablished; callId=%d, callInfoSecondsLimit=%d", callId, callInfo.getSecondsLimit());
        if (callInfo.getSecondsLimit() > 0){
            callCancellationHandler.postDelayed(() -> {
                try {
                    pjManager.callHangup(callId, 0);
                } catch (SameThreadException e) {
                    Log.ef(TAG, e, "onZrtpEstablished erro");
                }
            }, callInfo.getSecondsLimit() * 1000);
        }
    }
}
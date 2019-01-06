package net.phonex.sip;

import android.content.Intent;

import net.phonex.core.Intents;
import net.phonex.db.entity.SipCallSession;
import net.phonex.db.entity.SipCallSessionInfo;
import net.phonex.pub.a.Compatibility;
import net.phonex.pub.a.PjCalls;
import net.phonex.pub.a.PjManager;
import net.phonex.pub.parcels.ZrtpLogEntry;
import net.phonex.service.SvcRunnable;
import net.phonex.service.XService;
import net.phonex.service.XService.SameThreadException;
import net.phonex.service.runEngine.AndroidTimers;
import net.phonex.util.Log;

import net.phonex.util.MiscUtils;
import net.phonex.util.android.AudioUtils;
import net.phonex.xv.Xvi;
import net.phonex.xv.ZrtpCallback;
import net.phonex.xv.pj_str_t;

import java.util.List;

public class ZrtpCallbackImpl extends ZrtpCallback {
    private static final String TAG = "ZrtpCallbackImpl";
    private PjManager pjManager;

    private static final int HCODE_MESSAGE = 1;
    private static final int HCODE_NOT_SUPPORTED = 2;
    
    public ZrtpCallbackImpl(PjManager service) {
        pjManager = service;
    }

    @Override
    public void on_zrtp_show_sas(int callId, pj_str_t sas, int verified) {
		final String sasString = PjUtils.pjStrToString(sas);
		Log.vf(TAG, "on_zrtp_show_sas; callId: %s, sas: %s", callId, sasString);

		pjManager.executeOnServiceHandler(new SvcRunnable("on_zrtp_show_sas") {
			@Override
			public void doRun() throws SameThreadException {
				onZrtpShowSas(callId, sasString, verified);
			}
		});
    }

    @Override
    public void on_zrtp_update_transport(int callId) {
		Log.vf(TAG, "on_zrtp_update_transport, callId: %s", callId);
		// Service executor(updateCallMediaState -> updateCallSession, send on_media_update message).
		pjManager.refreshCallMediaState(callId);
    }
    
    @Override
    public void on_zrtp_update_state(final int callId, final int hcode, final int zrtp_state, final int sev, final int subcode) {
		Log.df(TAG, "on_zrtp_update_state: code %s, state: %s, sev: %s, subcode: %s", hcode, zrtp_state, sev, subcode);
		pjManager.executeOnServiceHandler(new SvcRunnable("zrtpChange") {
			@Override
			public void doRun() throws SameThreadException {
				onZrtpStateUpdate(callId, hcode, zrtp_state, sev, subcode);
			}
		});
	}

	@Override
	public void on_zrtp_hash_match(final int call_id, final int matchResult) {
		Log.inf(TAG, "MatchResult for call id=%s; result=%s", call_id, matchResult);
		pjManager.executeOnServiceHandler(new SvcRunnable("zrtp_hash_match") {
			@Override
			public void doRun() throws SameThreadException {
				onZrtpHashMatch(call_id, matchResult);
			}
		});
	}

	@Override
	public void on_zrtp_secure_state(final int call_id, final int secure_state_on) {
		Log.df(TAG, "on_zrtp_secure_state: callId=%d, secureState=%d", call_id, secure_state_on);
		pjManager.executeOnServiceHandler(new SvcRunnable("on_zrtp_secure_state") {
			@Override
			public void doRun() throws SameThreadException {
				if (secure_state_on > 0) {
					onZrtpSecureOn(call_id);
				} else {
					onZrtpSecureOff(call_id);
				}
			}
		});
	}

	public void updateZrtpInfo(final int callId) {
		pjManager.refreshCallMediaState(callId);
	}

	/**
	 * Call in service executor.
	 * @param callId
	 * @param hcode
	 * @param zrtp_state
	 * @param sev
	 * @param subcode
	 */
	private void onZrtpStateUpdate(Integer callId, int hcode, int zrtp_state, int sev, int subcode){
		boolean mute=false;
		String errorMessage = "";
		
		// Add ZRTP message to the call info
		SipCallSession callInfo = null;
		try {
			Log.df(TAG, "Updating call infos from the stack, callId: %s, hCode: %s, state: %s, sev: %s", callId, hcode, zrtp_state, sev);
			callInfo = pjManager.getCallInfo(callId);
			if(callInfo != null) {
				// check size of the list, max 500 elements
				List<ZrtpLogEntry> lst = callInfo.getZrtpLog();
				if (lst!=null && lst.size()<500){
					// create ZRTP log entry & add to the log for current file
					ZrtpLogEntry e = new ZrtpLogEntry(System.currentTimeMillis(), zrtp_state, hcode, sev, subcode);
					callInfo.addZrtpLog(e);
				}
			}
		} catch(Exception ex){
			Log.e(TAG, "Problem with error setting to UI", ex);
		}
		
		// Handle error message
		if (hcode==HCODE_MESSAGE && sev==1 && subcode==10){
			// entered secure state - notify user
			return;
		}
		
		if (hcode==HCODE_NOT_SUPPORTED){
			errorMessage = "Remote party does not support ZRTP";
			mute=true;
		}

        // Mute microphone with warning only when ZRTP encryption stops in the middle of the call.
		if (hcode==HCODE_MESSAGE && sev==1 && subcode==11 && (callInfo == null || (
                   callInfo.getCallState() != SipCallSession.InvState.NULL
                && callInfo.getCallState() != SipCallSession.InvState.DISCONNECTED)))
        {
			errorMessage="ZRTP encryption stopped";
			mute=true;
		}
			
		if (hcode==HCODE_MESSAGE && sev==3 && subcode>=5){
			errorMessage="ZRTP fatal error";
			mute=true;
		}
		
		// everything ok 
		if (!mute){
            return;
        }

		Log.ef(TAG, "Muting microphone, %s", errorMessage);
		
		// mute microphone, protect user from attack
		muteMicrophone(callId, mute);
		
		try {
			Log.d(TAG, "Updating call infos from the stack");
			if(callInfo == null) {
				return;
			}
			
			callInfo.setMediaSecureError(true);
			callInfo.setMediaSecureErrorString(errorMessage);
		} catch(Exception ex){
			Log.e(TAG, "Problem with error setting to UI", ex);
		}
	}

	private void onZrtpShowSas(int callId, String sasString, int verified){
		try {
			final SipCallSessionInfo callInfo = pjManager.updateCallInfoFromStack(
					callId, PjCalls.CALL_UPDATE_ZRTP_SHOW_SAS);

			Log.df(TAG, "ZRTP show SAS %s verified : %s", sasString, verified);
			if (verified != 1) {
				Intent zrtpIntent = new Intent(Intents.ACTION_ZRTP_SHOW_SAS);
				zrtpIntent.putExtra(Intent.EXTRA_SUBJECT, sasString);
				zrtpIntent.putExtra(Intents.EXTRA_CALL_INFO, callInfo);
				MiscUtils.sendBroadcast(pjManager.getService(), zrtpIntent);

			} else {
				updateZrtpInfo(callId);
			}

			// when zrtp is established
			pjManager.getPjCallback().onZrtpEstablished(callId);
		} catch(Exception e){
			Log.e(TAG, "Exception in zrtp show sas", e);
		}
	}

	private void onZrtpHashMatch(final int call_id, final int matchResult){
		try {
			Log.d(TAG, "Updating call infos from the stack - on_zrtp_hash_match");
			SipCallSession callInfo = pjManager.getCallInfo(call_id);
			if (callInfo == null) {
				return;
			}

			// set match result to UI
			if (matchResult != 1) {
				Log.e(TAG, "ZRTP has does not match");
				muteMicrophone(call_id, true);
				callInfo.setMediaSecureError(true);

			}

			callInfo.setZrtpHashMatch(matchResult);

			// refresh media state to UI
			pjManager.refreshCallMediaState(call_id);
		} catch (Exception ex) {
			Log.e(TAG, "Problem with error setting to UI", ex);
		}
	}

	/**
	 * Handler for ZRTP secure state = true. Execute on service handler.
	 * @param call_id
	 */
	private void onZrtpSecureOn(final int call_id){
		try {
			final SipCallSessionInfo callInfo = pjManager.updateCallInfoFromStack(
					call_id, PjCalls.CALL_UPDATE_ZRTP_SECURE_ON);

			if(callInfo == null) {
				Log.wf(TAG, "Call info was not found for=%d", call_id);
				return;
			}

			final int callConfSlot = callInfo.getConfPort();
			Log.vf(TAG, "Zrtp secure state, secure=%s, port=%d, callId: %d", true, callConfSlot, call_id);

			if (callConfSlot >= 0){
				final SvcRunnable runnableJob = new SvcRunnable("zrtp_connect_sound") {
					@Override
					protected void doRun() throws SameThreadException {
						connectSound(call_id, callInfo);
					}
				};

				// Because of BlackBerry, it has to execute in timer thread.
				if (Compatibility.isBlackBerry()){
					AndroidTimers.executeInTimerThreadStatic(runnableJob);
				} else {
					pjManager.executeOnServiceHandler(runnableJob);
				}
			}
		} catch(Exception ex){
			Log.e(TAG, "Problem with ZRTP secure state change", ex);
		}
	}

	/**
	 * Handler for ZRTP secure state = false. Execute on service handler.
	 * @param call_id
	 */
	private void onZrtpSecureOff(final int call_id){
		try {
			final SipCallSessionInfo callInfo = pjManager.updateCallInfoFromStack(
					call_id, PjCalls.CALL_UPDATE_ZRTP_SECURE_OFF);

			if(callInfo == null) {
				Log.wf(TAG, "Call info was not found for=%d", call_id);
				return;
			}

			final int callConfSlot = callInfo.getConfPort();
			Log.vf(TAG, "Zrtp secure state, secure=%s, port=%d, callId: %d", false, callConfSlot, call_id);

			final SvcRunnable runnableJob = new SvcRunnable("zrtp_disconnect_sound") {
				@Override
				protected void doRun() throws SameThreadException {
					disconnectSound(call_id, callInfo);
				}
			};

			// Because of BlackBerry, it has to execute in timer thread.
			if (Compatibility.isBlackBerry()){
				AndroidTimers.executeInTimerThreadStatic(runnableJob);
			} else {
				pjManager.executeOnServiceHandler(runnableJob);
			}
		} catch(Exception ex){
			Log.e(TAG, "Problem with ZRTP secure state change", ex);
		}
	}

	/**
	 * Connect sound device to conference bridge.
	 *
	 * Execute on timer thread only (because of BlackBerry).
	 * @param call_id
	 * @param callInfo
	 */
	private void connectSound(final int call_id, final SipCallSessionInfo callInfo){
		final int callConfSlot = callInfo.getConfPort();
		try {
			if (Compatibility.isBlackBerry() && pjManager != null && callInfo.isIncoming()) {
				Log.vf(TAG, "Going to test audio devices. callId: %d", call_id);
				final boolean hasValid = AudioUtils.findAudioRecord();
				if (!hasValid) {
					Log.ef(TAG, "Could not find any usable audio source, aborting call, callId: %d", call_id);
					Thread.sleep(200);

					pjManager.callHangup(call_id, SipStatusCode.TEMPORARILY_UNAVAILABLE);
					return;
				}
			}

			Log.vf(TAG, "Connecting callConfSlot %d to Bbridge", callConfSlot);
			Xvi.conf_connect(callConfSlot, 0);
			Log.vf(TAG, "Connecting Bbridge to callConfSlot %d", callConfSlot);
			Xvi.conf_connect(0, callConfSlot);

			// Adjust software volume
			if (pjManager.getMediaManager() != null) {
				pjManager.getMediaManager().setSoftwareVolume();
			}

			// Refresh media state to UI
			pjManager.refreshCallMediaState(call_id);
		} catch(Exception e){
			Log.e(TAG, "Exception in connecting sound", e);
		}
	}

	/**
	 * Disconnects given call from the conference bridge. Stops audio flow.
	 *
	 * Execute on timer thread only (because of BlackBerry).
	 */
	private void disconnectSound(final int call_id, final SipCallSessionInfo callInfo){
		final int callConfSlot = callInfo.getConfPort();
		final boolean muteSuccess = callConfSlot >= 0 && pjManager.disconnectSound(call_id, callConfSlot);
		if (!muteSuccess) {
			muteMicrophone(call_id, true);
		}

		// Refresh media state to UI
		pjManager.refreshCallMediaState(call_id);
	}

    private void muteMicrophone(final int callId, final boolean mute){
		String errorMessage = "";
		try {
			pjManager.setMicrophoneMute(mute);
		} catch (SameThreadException e) {
			Log.e(TAG, "Cannot mute microphone", e);
			errorMessage = "Cannot mute microphone! " + errorMessage;
			
			// try to hang telephone immediately
			try {
				pjManager.callHangup(callId, 1);
			} catch (SameThreadException e1) {
				Log.e(TAG, "Cannot hangUp call!");
				errorMessage = "Hang call immediatelly!!! " + errorMessage;
			}
		}
	}
}

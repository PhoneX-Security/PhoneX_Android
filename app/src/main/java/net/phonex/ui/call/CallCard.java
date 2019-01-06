package net.phonex.ui.call;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import net.phonex.R;
import net.phonex.db.entity.SipCallSession;
import net.phonex.db.entity.SipCallSessionInfo;
import net.phonex.db.entity.SipClist;
import net.phonex.core.SipUri;
import net.phonex.core.SipUri.ParsedSipContactInfos;
import net.phonex.pub.a.ICallActionListener;
import net.phonex.pref.PreferencesConnector;
import net.phonex.service.XService;
import net.phonex.ui.lock.util.VibrationHelper;
import net.phonex.util.CountUpTimer;
import net.phonex.util.DateUtils;
import net.phonex.util.Log;

import java.util.ArrayList;
import java.util.List;


public class CallCard extends FrameLayout implements OnClickListener {
    private static final String THIS_FILE = "InCallCard";
    
    private SipCallSessionInfo callInfo;
    private String cachedRemoteUri = "";
    private int cachedInvState = SipCallSession.InvState.INVALID;
    private int cachedMediaState = SipCallSession.MediaState.ERROR;
    private boolean cachedCanRecord = false;
    private boolean cachedIsRecording = false;
    private boolean cachedIsHold = false;

    private ImageView callIcon;
    private TextView remoteName, callStatusText, callSecureText, threeDotsText;
    private TextView remainingTime;
    private TextView elapsedTime;
//    private Chronometer elapsedTime;
    private SurfaceView renderView;
    private PreferencesConnector prefs;
    private ViewGroup endCallBar;
    private ImageButton endButton;

    private boolean cachedZrtpVerified;
    private boolean cachedZrtpActive;
    private boolean lastSecure = false;

    private CallChronometerTimer countTimer = new CallChronometerTimer(1000);

    // for three dots animation
    private final Handler handler = new Handler();
    private static final int ANIM_DELAY = 900;

    public CallCard(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CallCard(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.in_call_card, this, true);

        prefs = new PreferencesConnector(context);
        initControllerView();
    }

    private void initControllerView() {
        remoteName = (TextView) findViewById(R.id.contact_name_display_name);
        elapsedTime = (TextView) findViewById(R.id.elapsedTime);
        remainingTime = (TextView) findViewById(R.id.remainingTime);
        callStatusText = (TextView) findViewById(R.id.call_status_text);
        callSecureText = (TextView) findViewById(R.id.call_secure_text);
        threeDotsText = (TextView) findViewById(R.id.threeDots);
        endCallBar = (ViewGroup) findViewById(R.id.end_call_bar);

        callIcon = (ImageView) findViewById(R.id.callIcon);

        endButton = (ImageButton) findViewById(R.id.endButton);
        endButton.setOnClickListener(this);

        startThreeDotsAnim();

        // Long click on the name -> call info.
        remoteName.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                dispatchTriggerEvent(ICallActionListener.DETAILED_DISPLAY);
                return true;
            }
        });
    }

    public synchronized void setCallState(SipCallSessionInfo aCallInfo) {
        callInfo = aCallInfo;
        if (callInfo == null) {
            Log.wf(THIS_FILE, "setCallState; receiving callInfo = null");
            updateElapsedTimer();
            cachedInvState = SipCallSession.InvState.INVALID;
            cachedMediaState = SipCallSession.MediaState.ERROR;
            cachedCanRecord = false;
            cachedIsRecording = false;
            cachedIsHold = false;
            cachedZrtpActive = false;
            cachedZrtpVerified = false;
            return;
        }

        Log.df(THIS_FILE, "Set call state : %s, session: %s", callInfo.getCallState(), callInfo);
        
        updateRemoteName();
        updateCallStateBar();        
        updateElapsedTimer();

        if (callInfo.isAfterEnded()){
            endButton.setVisibility(View.GONE);
        }

        cachedInvState = callInfo.getCallState();
        cachedMediaState = callInfo.getMediaStatus();
        cachedCanRecord = callInfo.canRecord();
        cachedIsRecording = callInfo.isRecording();
        cachedIsHold = callInfo.isLocalHeld();
        cachedZrtpActive = callInfo.getHasZrtp();
        cachedZrtpVerified = callInfo.isZrtpSASVerified();        
    }

    private synchronized void startThreeDotsAnim(){
        handler.postDelayed(threeDotsAnimationRunnable, 100);
    }
    private synchronized void stopThreeDotsAnim(){
        threeDotsText.setVisibility(View.GONE);
        handler.removeCallbacks(threeDotsAnimationRunnable);
    }

    private final Runnable threeDotsAnimationRunnable = new Runnable() {
        private int iteration = 0;
        private String[] dots = {"", ".", "..", "..."};
        @Override
        public void run() {
            iteration = (iteration + 1) % dots.length;
            threeDotsText.setText(dots[iteration]);
            handler.postDelayed(this, ANIM_DELAY);
        }
    };

    /**
     * Bind the main visible view with data from call info
     */
    private void updateCallStateBar() {
        Log.vf(THIS_FILE, "updateCallStateBar; callInfo=[%s]", callInfo);
        // TODO refactor, too complex
        int stateText = -1; 
        int stateIcon = R.drawable.call_phonex_encrypting;
        if (callInfo.isPreparing()){
            stateText = R.string.call_state_preparing;
            stateIcon = R.drawable.svg_logo_large_call;
        } else if (callInfo.isAfterEnded()) {
            stopThreeDotsAnim();
            stateText = R.string.call_state_disconnected;
        } else if (callInfo.isLocalHeld() || callInfo.isRemoteHeld()) {
            stateText = R.string.on_hold;
        } else if (callInfo.isBeforeConfirmed()) {
            if (callInfo.isIncoming()) {
                stateIcon = R.drawable.svg_logo_large_call;
                stateText = R.string.call_state_incoming;
            } else {
            	stateIcon = R.drawable.svg_logo_large_call;
                stateText = R.string.call_state_calling;
            }
        } else if(callInfo.isMediaSecure() || callInfo.isZrtpSASVerified()){
        	stateIcon = R.drawable.call_lock_icon;
            stopThreeDotsAnim();
        } else {
            stateText = R.string.call_state_encrypting;
        }
        
        if( (callInfo.isBeforeConfirmed() && callInfo.isIncoming()) || callInfo.isAfterEnded() ) {
            endCallBar.setVisibility(GONE);
        }else {
            endCallBar.setVisibility(VISIBLE);
        }

        if(stateText != -1) {
            callStatusText.setText(stateText);
            setVisibleWithFade(callStatusText, true);
        } else {
            setVisibleWithFade(callStatusText, false);
        }
        callIcon.setImageResource(stateIcon);
    }

    private void updateRemoteName() {
        final String aRemoteUri = callInfo.getRemoteContact();

        // If not already set with the same value, just ignore it
        if (aRemoteUri != null && !aRemoteUri.equalsIgnoreCase(cachedRemoteUri)) {
            cachedRemoteUri = aRemoteUri;
            ParsedSipContactInfos uriInfos = SipUri.parseSipContact(cachedRemoteUri);
            String text = SipUri.getDisplayedSimpleContact(aRemoteUri);
            SipClist sipClist = SipClist.getProfileFromDbSip(getContext(), uriInfos.getContactAddress());

            if (sipClist!=null && sipClist.getId()!=null && sipClist.getId()!=SipClist.INVALID_ID){
            	remoteName.setText(sipClist.getDisplayName());            	
            } else{
            	remoteName.setText(text);
            }
        }
        
        // Useless to process that
        if (cachedInvState == callInfo.getCallState() &&
                cachedMediaState == callInfo.getMediaStatus()) {
            return;
        }
    }

    private void updateElapsedTimer() {

        if (callInfo == null) {
            countTimer.stop();
//            elapsedTime.stop();
            remainingTime.setVisibility(GONE);
            elapsedTime.setVisibility(VISIBLE);
            return;
        }


//        elapsedTime.setBase(callInfo.getConnectStart());

        try {
            int sigSecureLevel = callInfo.getTransportSecureLevel();
            boolean isSecure = (callInfo.isMediaSecure() || sigSecureLevel > 0);
//	        String secureInfo = callInfo.getMediaSecureInfo();
//	        String secureMsg = "";
            if (isSecure) {
                List<String> secureTxtList = new ArrayList<String>();
                if (sigSecureLevel == SipCallSession.TRANSPORT_SECURE_TO_SERVER) {
                    secureTxtList.add(getContext().getString(R.string.transport_secure_to_server));
                } else if (sigSecureLevel == SipCallSession.TRANSPORT_SECURE_FULL) {
                    secureTxtList.add(getContext().getString(R.string.transport_secure_full));
                }
                if (callInfo.isMediaSecure()) {
                    secureTxtList.add(callInfo.getMediaSecureInfo());
                }
//	            secureMsg = TextUtils.join("\r\n", secureTxtList);
            }

            callSecureText.setText(callInfo.getMediaSecureInfo());

            // error handling
            if (callInfo.isMediaSecure() == false || callInfo.isMediaSecureError()) {

                if (callInfo.isMediaSecureError()) {
                    String mediaError = callInfo.getMediaSecureErrorString();
                    mediaError = mediaError == null ? "" : mediaError.trim();

                    // zrtp-hash error
                    int zrtpHashMatch = callInfo.getZrtpHashMatch();
                    if (zrtpHashMatch != 1 && zrtpHashMatch != -1 && callInfo.isActive()) {
                        Log.df(THIS_FILE, "zrtp-hash does not match! code: %s", zrtpHashMatch);
                        mediaError += (TextUtils.isEmpty(mediaError) ? "" : "\r\n")
                                + getContext().getString(R.string.incall_zrtphash_nomatch_error)
                                + zrtpHashMatch;
                    }

                    callSecureText.setText(mediaError);
                }

                if (this.lastSecure == true) {
                    this.lastSecure = false;
                    this.vibrateNotif(2);
                }
            } else {
                // notify user about encryption OK - vibration (+ sound, directly in pjsip-wrapper)
                if (this.lastSecure == false) {
                    this.lastSecure = true;
                    vibrateNotif(1);
                }
            }
        } catch (Exception ex) {
            Log.e(THIS_FILE, "Exception occurred in secure box", ex);
        }

        if (!callInfo.isIncoming() && callInfo.getHasZrtp() && callInfo.getSecondsLimit() >= 0) {
            remainingTime.setVisibility(VISIBLE);
            countTimer.setZrtpStart(callInfo.getZrtpStart());
            countTimer.setSecondsLimit(callInfo.getSecondsLimit());
        }

        int state = callInfo.getCallState();
        switch (state) {
            case SipCallSession.InvState.PREPARING:
            case SipCallSession.InvState.INCOMING:
            case SipCallSession.InvState.CALLING:
            case SipCallSession.InvState.EARLY:
            case SipCallSession.InvState.CONNECTING:
                elapsedTime.setVisibility(GONE);
                break;
            case SipCallSession.InvState.CONFIRMED:
                Log.v(THIS_FILE, "we start the timer now ");
                if (callInfo.isLocalHeld()) {
                    countTimer.stop();
//                    elapsedTime.stop();
                    elapsedTime.setVisibility(View.GONE);
                } else {
//                    elapsedTime.start();
                    countTimer.start(callInfo.getConnectStart());
                    elapsedTime.setVisibility(View.VISIBLE);
                }
                break;
            case SipCallSession.InvState.NULL:
            case SipCallSession.InvState.DISCONNECTED:
                countTimer.stop();
//                elapsedTime.stop();
                elapsedTime.setVisibility(VISIBLE);
                break;
            default:
                break;
        }
    }

    public void vibrateNotif(int patternId){
        long[] pattern = { 0, 1000 };
        switch(patternId){
            case 1:
                long[] pattern1 = {100, 200, 100, 200};
                pattern = pattern1;
                break;
            case 2:
                long[] pattern2 = {0, 1000};
                pattern = pattern2;
                break;
        }

        VibrationHelper.vibrate(getContext(), pattern, -1);
    }

    boolean isForcedToTerminate = false;
    boolean isForcedToRestart = false;
    private int terminateCallCounter = 0;
    private ICallActionListener onTriggerListener;

    /*
     * Registers a callback to be invoked when the user triggers an event.
     * @param listener the OnTriggerListener to attach to this view
     */
    public void setOnTriggerListener(ICallActionListener listener) {
        onTriggerListener = listener;
    }

    private void dispatchTriggerEvent(int whichHandle) {
        Log.df(THIS_FILE, "dispatch %s", onTriggerListener);
        if (onTriggerListener != null) {
            onTriggerListener.onCallAction(whichHandle, callInfo);
        }
    }

    public void terminate() {
        if(callInfo != null && renderView != null) {
            XService.setVideoWindow(callInfo.getCallId(), null, false);
        }
    }
    
    private void setVisibleWithFade(View v, boolean in) {
        if(v.getVisibility() == View.VISIBLE && in) {
            // Already visible and ask to show, ignore
            return;
        }
        if(v.getVisibility() == View.GONE && !in) {
            // Already gone and ask to hide, ignore
            return;
        }
        
        Animation anim = AnimationUtils.loadAnimation(getContext(), in ? android.R.anim.fade_in : android.R.anim.fade_out);
        anim.setDuration(1000);
        v.startAnimation(anim);
        v.setVisibility(in ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onClick(View v) {
        if (callInfo == null){
            return;
        }

        int id = v.getId();
        if(id == R.id.endButton) {
            if (callInfo.getCallState() == SipCallSession.InvState.PREPARING){
                dispatchTriggerEvent(ICallActionListener.DROP_CALL);
            } else if (callInfo.isBeforeConfirmed() && callInfo.isIncoming()) {
                dispatchTriggerEvent(ICallActionListener.REJECT_CALL);
            } else if (!callInfo.isAfterEnded()) {
                terminateCallCounter++;
                
                // two ways of calling forced to terminate: 5x press call_end 
                if (!isForcedToTerminate && terminateCallCounter == 5){
                	Log.v(THIS_FILE, "DROP_CALL 1");
                	
                	dispatchTriggerEvent(ICallActionListener.DROP_CALL);
                	isForcedToTerminate=true;
                	return;
                }

                // terminate tried -> restart sip stack
                // 10x means we try more hardcore way -- experimental
                if (isForcedToTerminate && !isForcedToRestart && terminateCallCounter == 10){
                	Log.v(THIS_FILE, "RESTART_SERVICE");

                	dispatchTriggerEvent(ICallActionListener.RESTART_SERVICE);
                	isForcedToRestart = true;
                	
                	// hide button
                	v.setVisibility(View.GONE);
                	return;
                }
                
                Log.v(THIS_FILE, "TERMINATE_CALL");
                dispatchTriggerEvent(ICallActionListener.TERMINATE_CALL);
            }
        }
    }

    private class CallChronometerTimer extends CountUpTimer {
        private long zrtpStart = -1;
        private int secondsLimit = -1;

        public CallChronometerTimer(long countDownInterval) {
            super(countDownInterval);
        }

        @Override
        public void onTick(long elapsedTimeMillis) {
            Log.vf(THIS_FILE, "onTick; elapsedTimeMillis=%d", elapsedTimeMillis);
            String elapsedTimeText = DateUtils.formatTime(elapsedTimeMillis);
            Long remainingMillis = getRemainingMillis();
            if (remainingMillis != null){
                if (remainingMillis < 0){
                    remainingMillis = 0l;
                }
                remainingTime.setText(String.format(getContext().getString(R.string.remaining_time), DateUtils.formatTime(remainingMillis)));
//                remainingTime.setText(String.valueOf(remainingMillis / 1000));
                elapsedTime.setText(elapsedTimeText);
            } else {
                elapsedTime.setText(elapsedTimeText);
            }
        }

        @Override
        public void onStop(long elapsedTimeMillis) {
            Long remainingMillis = getRemainingMillis();
            if (remainingMillis!= null && remainingMillis < 1000){
                remainingTime.setText(R.string.call_out_of_minutes);
            }
        }

        private Long getRemainingMillis(){
            if (secondsLimit > 0 && secondsLimit > 0){
                long callDuration = (SystemClock.elapsedRealtime() - zrtpStart);
                long millisLimit = (secondsLimit * 1000);
                long remaining = millisLimit - callDuration;
                Log.vf(THIS_FILE, "getRemainingMillis; remaining=%d, callDuration=%d, millisLimit=%d, zrtpStart=%d", remaining, callDuration, millisLimit, zrtpStart);
                return remaining;
            } else {
                return null;
            }
        }

        public void setZrtpStart(long zrtpStart) {
            this.zrtpStart = zrtpStart;
        }

        public void setSecondsLimit(int secondsLimit) {
            this.secondsLimit = secondsLimit;
        }
    }
}

package net.phonex.ui.call;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.TextAppearanceSpan;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

import net.phonex.R;
import net.phonex.accounting.PermissionLimits;
import net.phonex.annotations.PinUnprotected;
import net.phonex.core.IService;
import net.phonex.core.Intents;
import net.phonex.core.MediaState;
import net.phonex.pref.PhonexConfig;
import net.phonex.db.entity.SipCallSession;
import net.phonex.db.entity.SipCallSessionInfo;
import net.phonex.pub.a.AudioSettingsActivity;
import net.phonex.pub.a.CallAnswerView;
import net.phonex.pub.a.ICallActionListener;
import net.phonex.pref.PreferencesConnector;
import net.phonex.pub.a.ProximityManager;
import net.phonex.pub.parcels.MakeCallResult;
import net.phonex.pub.parcels.ZrtpLogEntry;
import net.phonex.service.XService;
import net.phonex.sip.SipStatusCode;
import net.phonex.sip.ZrtpConstants;
import net.phonex.ui.inapp.ExpiredLicenseDialogs;
import net.phonex.ui.lock.activity.LockActionBarActivity;
import net.phonex.util.DefaultServiceConnector;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.analytics.AnalyticsReporter;
import net.phonex.util.analytics.AppButtons;
import net.phonex.util.analytics.AppEvents;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@PinUnprotected
public class CallActivity extends LockActionBarActivity implements DefaultServiceConnector.ServiceConnectorListener, ICallActionListener, ProximityManager.ProximityDirector {
    private final static String TAG = "CallActivity";
    private static final int QUIT_DELAY = 3000;

    private final Object callMutex = new Object();

    // remember only session that invoked this activity
    private volatile SipCallSessionInfo call0 = null;
    private volatile int activeCallId = SipCallSession.INVALID_CALL_ID;

    private MediaState lastMediaState;

    private ProximityManager proximityManager;

    private CallMenu callMenu;
    private CallCard callCard;
    private CallAnswerView callAnswerView;

    private boolean useAutoDetectSpeaker = false;

    private DefaultServiceConnector connector;
    private PreferencesConnector prefsWrapper;
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;

    private Timer quitTimer;

    private TelephonyManager telephonyManager;
    private MyPhoneStateListener phoneStateListener;

    private AlertDialog infoDialog;

    // single working thread executor for executing call tasks synchronously
    ExecutorService callActionsExecutor;

    // Deferred calls if service was not yet connected, so important call events are not missed.
    private final AtomicBoolean deferredCallStateChange = new AtomicBoolean(false);
    private final AtomicBoolean deferredMediaStateChange = new AtomicBoolean(false);

    public static synchronized void invokeActivityForCall(Context ctxt, String callDestination, Number accountId){
        int callLimit = PermissionLimits.getCallLimit(ctxt.getContentResolver());
        Log.vf(TAG, "invokeActivityForCall; callLimit=%d", callLimit);
        if (callLimit == 0){
//            -1=infinite, 0=no more minutes
            ExpiredLicenseDialogs.showCalLimitPrompt(ctxt);
            return;
        }

        // construct initial fake call session - just to execute makeCall later
        final SipCallSession session = new SipCallSession();
        session.resetForNewCall();
        session.setIncoming(false);
        if (accountId != null){
            session.setAccId(accountId.intValue());
        }
        session.setRemoteContact(callDestination);
        session.setCallState(SipCallSession.InvState.PREPARING);
        session.resetVersionCounter();
        Intent callHandlerIntent = XService.buildCallUiIntent(ctxt, session);

        Log.d(TAG, "Starting Intent to call activity");
        ctxt.startActivity(callHandlerIntent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "Create in call");
        setContentView(R.layout.in_call_main);

        final SipCallSessionInfo initialSession = processIntent(getIntent());
        if (initialSession == null){
            Log.e(TAG, "Session cannot be null");
            finish();
            return;
        }

        prefsWrapper = new PreferencesConnector(this);
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                        | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE,
                "net.phonex.onIncomingCall");
        wakeLock.setReferenceCounted(false);

        // Create default service connector
        connector = new DefaultServiceConnector();
        connector.setDefCtxt(this);
        connector.setListener(this);

        initViews();
        updateUI(initialSession);

        useAutoDetectSpeaker = prefsWrapper.getBoolean(PhonexConfig.AUTO_DETECT_SPEAKER);

        // Listen to media & sip events to update the UI
        MiscUtils.registerReceiver(this, callStateReceiver, new IntentFilter(Intents.ACTION_SIP_CALL_CHANGED));
        MiscUtils.registerReceiver(this, callStateReceiver, new IntentFilter(Intents.ACTION_SIP_MEDIA_CHANGED));
        MiscUtils.registerReceiver(this, callStateReceiver, new IntentFilter(Intents.ACTION_ZRTP_SHOW_SAS));

        proximityManager = new ProximityManager(this, this);
        proximityManager.startTracking();

        callActionsExecutor = Executors.newSingleThreadExecutor();

        if (quitTimer == null) {
            quitTimer = new Timer("QuitTimer");
        }

        connector.connectService(this);
    }

    private void initViews(){
        // Cache views
        callMenu = (CallMenu) findViewById(R.id.inCallMenu);
        callAnswerView = (CallAnswerView) findViewById(R.id.inCallAnswerControls);
        callCard = (CallCard) findViewById(R.id.callCard);
        callMenu.setOnTriggerListener(this);
        callAnswerView.setOnTriggerListener(this);
        callCard.setOnTriggerListener(this);

        // Enable to show call screen when screen is locked
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        if (prefsWrapper.getBoolean(PhonexConfig.PREVENT_SCREEN_ROTATION)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        telephonyManager = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
        phoneStateListener = new MyPhoneStateListener(telephonyManager, this);
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        runOnUiThread(new UpdateUIFromCallRunnable(call0 == null ? null : call0.copy()));
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (telephonyManager!=null && phoneStateListener!=null){
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
            Log.i(TAG, "Unregistering phoneStateListener - GSM calls allowed from now on");
            phoneStateListener = null;
        }
    }

    @Override
    protected void onDestroy() {
        if (connector!=null && connector.isConnected()){
            connector.disconnectService(this);
        }

        if (quitTimer != null) {
            quitTimer.cancel();
            quitTimer.purge();
            quitTimer = null;
        }

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        try {
            unregisterReceiver(callStateReceiver);
        } catch (IllegalArgumentException e) {
            Log.wf(TAG, e, "onDestroy;");
        }

        super.onDestroy();

    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.df(TAG, "New intent is launched; [%s]", intent);
        setIntent(intent);

        // Processing intent content, updating internal values.
        // UI will be refreshed on subsequent onResume() call
        processIntent(intent);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "Configuration changed");
        runOnUiThread(new UpdateUIFromCallRunnable(call0 == null ? null : call0.copy()));
    }

    @Override
    public void onXServiceConnected(ComponentName arg0, IBinder arg1) {
        // there should be only one session
        Log.vf(TAG, "SvcConnected, call0: %s", call0);
        synchronized (callMutex){
            final SipCallSessionInfo session = call0;
            if (session.getCallState() == SipCallSession.InvState.PREPARING){
                initCall(session.getRemoteContact(), session);
            }
        }

        // Check if there are deferred events waiting for processing.
        callActionsExecutor.submit(() -> {
            if (deferredCallStateChange.get()) {
                Log.v(TAG, "Deferred execution of onCallStateChange");
                onCallStateChangedAsync();
            }

            if (deferredMediaStateChange.get()) {
                Log.v(TAG, "Deferred execution of onMediaStateChange");
                onMediaStateChangedAsync();
            }
        });
    }

    @Override
    public void onXServiceDisconnected(ComponentName arg0) {
    }

    private SipCallSessionInfo processIntent(Intent intent){
        final SipCallSessionInfo initialSession = intent.getParcelableExtra(Intents.EXTRA_CALL_INFO);
        if (initialSession == null) {
            Log.e(TAG, "Session cannot be null");
            return null;
        }

        Log.vf(TAG, "processIntent; received SipCallSession [%s], callId: %s, this: %s, call0: %s",
                initialSession, initialSession.getCallId(), this, call0);

        synchronized (callMutex) {
            call0 = initialSession;
            if (call0.getCallId() != SipCallSession.INVALID_CALL_ID){
                activeCallId = call0.getCallId();
            }
        }

        return initialSession;
    }

    private void updateUI(SipCallSessionInfo callSession){
        Log.vf(TAG, "updateUI; SipCallSession [%s]", callSession);
        callMenu.setCallState(callSession);
        callAnswerView.setCallState(callSession);
        callCard.setCallState(callSession);
    }

    private void initCall(final String callee, SipCallSessionInfo sipCallSession) {
        final int accountId = (int) sipCallSession.getAccId();
        callActionsExecutor.submit(() -> {
            Log.vf(TAG, "initCall; call to remoteContact [%s]; accountId [%d]", callee, accountId);
            try {
                MakeCallResult result = connector.getService().makeCall(callee, accountId);


                Log.inf(TAG, "invokeActivityForCall; MakeCallResult [%s]", result);

                AnalyticsReporter.from(CallActivity.this).event(AppEvents.CALL_INITIATED);

                // TODO fix that returned CallId is always 0
                synchronized (callMutex) {
                    if (result != null && result.getStatus() >= 0) {
                        activeCallId = result.getCallId();
                        Log.vf(TAG, "invokeActivityForCall; callId set to  [%d]", result.getCallId());
                    }
                }

            } catch (RemoteException e) {
                Log.ef(TAG, e, "Can't initiate call");
            }
        });
    }

    private boolean answerCall(SipCallSessionInfo call) throws RemoteException {
        final IService service = connector.getService();
        if (service == null){
            Log.ef(TAG, "Could not answer the call, service is null. Call: %s", call);
            return false;
        }

        Log.df(TAG, "Answer call %s", call.getCallId());
        boolean shouldHoldOthers = false;

        // Well actually we should be always before confirmed
        if (call.isBeforeConfirmed()) {
            shouldHoldOthers = true;
        }

        service.answer(call.getCallId(), SipStatusCode.OK);
        AnalyticsReporter.from(this).event(AppEvents.CALL_TAKEN);

        // if it's a ringing call, we assume that user wants to
        // hold other calls
        if (shouldHoldOthers && callAnswerView != null) {
            if (SipCallSession.InvState.CONFIRMED == call0.getCallState()
                    && !call0.isLocalHeld()
                    && call0.getCallId() != call.getCallId()) {

                Log.df(TAG, "Hold call %s", call0.getCallId());
                service.hold(call0.getCallId());
            }
        }

        return true;
    }

    private void hangup(final int statusCode){
        callActionsExecutor.submit(() -> {
            IService service = connector.getService();
            int hangupCallId = activeCallId;
            if (hangupCallId == SipCallSession.INVALID_CALL_ID){
                Log.wf(TAG, "Cannot hangup call with invalid callId");
                return;
            }

            Log.vf(TAG, "hangup; terminating call with id [%d], requested status code [%d]", hangupCallId, statusCode);
            if (service != null) {
                try {
                    service.hangup(hangupCallId, statusCode);
                } catch (RemoteException e) {
                    Log.ef(TAG, e, "Can't hangup call");
                }

            } else {
                Log.e(TAG, "Tried to hangup call but service is null");
            }
        });
    }

    private void terminate(final int statusCode){
        callActionsExecutor.submit(new Runnable() {
            @Override
            public void run() {
                IService service = connector.getService();
                int hangupCallId = activeCallId;
                // check validity of callId
                if (hangupCallId == SipCallSession.INVALID_CALL_ID) {
                    Log.wf(TAG, "Cannot terminate call with invalid callId");
                    return;
                }
                Log.vf(TAG, "terminate; terminating call with id [%d], requested status code [%d]", hangupCallId, statusCode);


                if (service != null) {
                    try {
                        service.terminateCall(hangupCallId, statusCode);//481);
                    } catch (RemoteException e) {
                        Log.ef(TAG, e, "Can't hangup call");
                    }
                } else {
                    Log.e(TAG, "Tried to hangup call but service is null");
                    MiscUtils.sendBroadcast(CallActivity.this, new Intent(Intents.ACTION_SIP_REQUEST_BRUTAL_RESTART));
                }
            }
        });
    }

    private void onCallStateChangedAsync(){
        callActionsExecutor.submit(this::onCallStateChanged);
    }

    private void onCallStateChanged(){
        final IService service = connector.getService();
        if (service == null) {
            deferredCallStateChange.set(true);
            Log.wf(TAG, "Call state changed, service not yet connected");
            return;
        }

        deferredCallStateChange.set(false);
        try {
            synchronized (callMutex) {
                boolean callUpdated = false;

                SipCallSession mostPrioritizedCall = null; // TMP fix for PHON-255
                for(SipCallSession sipCallSession : service.getCalls()){
                    Log.df(TAG, "callStateReceiver; received session from pjsip; callId [%d], callState [%d]",
                            sipCallSession.getCallId(),
                            sipCallSession.getCallState()
                    );

                    if (sipCallSession.getCallId() == activeCallId){
                        call0 = sipCallSession;
                        Log.df(TAG, "callStateReceiver; Updating call session; [%s]", sipCallSession);
                        callUpdated = true;
                        break;
                    }
                    mostPrioritizedCall = getPrioritaryCall(sipCallSession, mostPrioritizedCall);
                }

                if (!callUpdated){
                    // In case call was not updated for any reason, pick up the most prioritized call and
                    // display info about it (fix some non-deterministic behaviour)
                    Log.wf(TAG, "callStateReceiver; no retrieved call session from pjsip has callId same as the one stored in activity, [%d]", activeCallId);
                    if (mostPrioritizedCall != null){
                        activeCallId = mostPrioritizedCall.getCallId();
                    }
                    call0 = mostPrioritizedCall;
                }


                runOnUiThread(new UpdateUIFromCallRunnable(call0 == null ? null : call0.copy()));
            }
        } catch (RemoteException e) {
            Log.ef(TAG, e, "Not able to retrieve calls");
        }
    }

    private void onMediaStateChangedAsync(){
        callActionsExecutor.submit(this::onMediaStateChanged);
    }

    private void onMediaStateChanged(){
        final IService service = connector.getService();
        if (service == null) {
            deferredMediaStateChange.set(true);
            Log.wf(TAG, "Media state changed, service not yet connected");
            return;
        }

        deferredMediaStateChange.set(false);
        try {
            final MediaState mediaState = service.getCurrentMediaState();
            Log.d(TAG, "Media update: " + mediaState);
            synchronized (callMutex) {
                // Before it was equals check here but race conditions applied when switching was done too fast
                // And buttons were automatically de-activated e.g., on bluetooth connection fail.
                lastMediaState = mediaState;
                runOnUiThread(new UpdateUIFromMediaRunnable());
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Can't get the media state ", e);
        }
    }

    public void microphoneMuteRequest(boolean mute){
        final IService service = connector.getService();

//        //Ask the user if they want to quit
        new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle("Unmute?")
                .setMessage("Do you really want to unmute even if channel is NOT SECURE?")
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            if (service!=null)
                                service.setMicrophoneMute(false);
                        }catch(Exception e){
                            Log.e(TAG, "Exception in confirm dialog", e);
                        }
                    }
                })
                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            callMenu.muteMicButtonState(true);
                        }catch(Exception e){
                            Log.e(TAG, "Exception in confirm dialog", e);
                        }
                    }
                })
                .show();
    }

    @Override
    public void onCallAction(int actionId, SipCallSessionInfo call) {
        // Sanity checks
        if (!(actionId == RESTART_SERVICE
                || actionId == DONT_TAKE_CALL
                || actionId == REJECT_CALL
                || actionId == TERMINATE_CALL)){
            if (call == null) {
                Log.ef(TAG, "Try to do an action [%d] on a null call.", actionId);
                return;
            }
            if (call.getCallId() == SipCallSession.INVALID_CALL_ID) {
                    Log.ef(TAG, "Try to do an action [%d] on an invalid call id", actionId);
                    return;
            }
        }

        // Reset proximity sensor timer
        proximityManager.restartTimer();

        IService service = connector.getService();

        try {
            switch (actionId) {
                case TAKE_CALL: {
                    analyticsReporter.buttonClick(AppButtons.CALL_TAKE);
                    answerCall(call);
                    break;
                }
                case DONT_TAKE_CALL: {
                    analyticsReporter.buttonClick(AppButtons.CALL_NOT_TAKE_CALL);
                    hangup(603);
                    AnalyticsReporter.from(this).event(AppEvents.CALL_NOT_TAKEN);
                    break;
                }
                case REJECT_CALL:
                case TERMINATE_CALL: {
                    analyticsReporter.buttonClick(AppButtons.CALL_TERMINATE);
                    hangup(0);
                    break;
                }
                case DROP_CALL: {
                    analyticsReporter.buttonClick(AppButtons.CALL_DROP);
                    // TODO: this should be allowed only after user tried to terminate call by normal way
                    // allow to call only once on one call!
                    terminate(0);
                    break;
                }
                case RESTART_SERVICE: {
                    Log.i(TAG, "Going to restart SIP service on request - sending intent");
                    MiscUtils.sendBroadcast(this, new Intent(Intents.ACTION_SIP_REQUEST_BRUTAL_RESTART));
                    break;
                }

                case MUTE_ON:
                case MUTE_OFF: {
                    if (service != null) {
                        // security things
                        boolean fin=false;
                        try {
                            SipCallSessionInfo tmpSessInfo = getActiveCallSession();
                            if (tmpSessInfo!=null){
                                if (actionId ==MUTE_OFF && (!tmpSessInfo.isMediaSecure() || tmpSessInfo.isMediaSecureError())){
                                    this.microphoneMuteRequest(true);
                                    fin=true;
                                }
                            }
                        } catch(Exception e){
                            Log.e(TAG, "Exception in MicMute button handling", e);
                        }

                        if(!fin)
                            service.setMicrophoneMute((actionId == MUTE_ON));
                    }
                    break;
                }
                case SPEAKER_ON:
                case SPEAKER_OFF: {
                    if (service != null) {
                        Log.d(TAG, "Manually switch to speaker");
                        useAutoDetectSpeaker = false;
                        service.setSpeakerphoneOn((actionId == SPEAKER_ON));
                    }
                    break;
                }
                case BLUETOOTH_ON:
                case BLUETOOTH_OFF: {
                    if (service != null) {
                        Log.df(TAG, "Enabling bluetooth: %s", actionId == BLUETOOTH_ON);
                        service.setBluetoothOn((actionId == BLUETOOTH_ON));
                    }
                    break;
                }

                case MEDIA_SETTINGS: {
                    startActivity(new Intent(this, AudioSettingsActivity.class));
                    break;
                }

                case ZRTP_TRUST : {
                    getAnalyticsReporter().buttonClick(AppButtons.CALL_ZRTP_TRUST);
                    if(service != null) {
                        service.zrtpSASVerified(call.getCallId());
                    }
                    break;
                }
                case ZRTP_REVOKE : {
                    getAnalyticsReporter().buttonClick(AppButtons.CALL_ZRTP_REVOKE);
                    if(service != null) {
                        service.zrtpSASRevoke(call.getCallId());
                    }
                    break;
                }
                case DETAILED_DISPLAY: {
                    if (service != null) {
                        if(infoDialog != null) {
                            infoDialog.dismiss();
                        }
                        String infos = service.showCallInfosDialog(call.getCallId());
                        String natType = service.getLocalNatType();
                        SpannableStringBuilder buf = new SpannableStringBuilder();
                        AlertDialog.Builder builder = new AlertDialog.Builder(this);

                        buf.append(infos);
                        if(!TextUtils.isEmpty(natType)) {
                            buf.append("\r\nLocal NAT type detected : ");
                            buf.append(natType);
                            buf.append("\r\n=========\r\n");
                        }

                        // ZRTP log
                        List<ZrtpLogEntry> zrtpLog = call.getZrtpLog();
                        if (zrtpLog!=null && !zrtpLog.isEmpty()){
                            buf.append("\r\nZRTP log : \r\n");

                            DateFormat formatter = new SimpleDateFormat("hh:mm:ss.SSS");
                            Calendar calendar   = Calendar.getInstance();

                            Iterator<ZrtpLogEntry> it = zrtpLog.iterator();
                            while(it.hasNext()){
                                ZrtpLogEntry e = it.next();
                                calendar.setTimeInMillis(e.getTime());

                                int hcode = e.getHcode();
                                buf.append("# ")
                                        .append(formatter.format(calendar.getTime()))
                                        .append(": ");
                                if(hcode==1){
                                    buf.append("ZrtpState=[")
                                            .append(ZrtpConstants.getStateName(e.getZrtpState()))
                                            .append(" (")
                                            .append(String.valueOf(e.getZrtpState()))
                                            .append(")]; [")
                                            .append(ZrtpConstants.getSeverity(e.getSeverity()))
                                            .append("]: ")
                                            .append(ZrtpConstants.getReportCode(e.getSeverity(), e.getSubcode()))
                                            .append(" (").append(String.valueOf(e.getSeverity()))
                                            .append(";").append(String.valueOf(e.getSubcode()))
                                            .append(");");
                                } else {
                                    buf.append(e.toString());
                                }

                                buf.append("\r\n\r\n");
                            }
                        }

                        TextAppearanceSpan textSmallSpan = new TextAppearanceSpan(this,
                                android.R.style.TextAppearance_Small);
                        buf.setSpan(textSmallSpan, 0, buf.length(),
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                        infoDialog = builder.setIcon(android.R.drawable.ic_dialog_info)
                                .setMessage(buf)
                                .setNeutralButton(R.string.ok, null)
                                .create();
                        infoDialog.show();
                    }
                    break;
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Was not able to call service method", e);
        }

    }

    private BroadcastReceiver callStateReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(Intents.ACTION_SIP_CALL_CHANGED)) {
                onCallStateChangedAsync();

            } else if (action.equals(Intents.ACTION_SIP_MEDIA_CHANGED)) {
                onMediaStateChangedAsync();

            } else if (action.equals(Intents.ACTION_ZRTP_SHOW_SAS)) {
                AnalyticsReporter.from(CallActivity.this).event(AppEvents.ZRTP_ESTABLISHED);
                SipCallSession callSession = intent.getParcelableExtra(Intents.EXTRA_CALL_INFO);
                String sas = intent.getStringExtra(Intent.EXTRA_SUBJECT);
                runOnUiThread(new ShowZRTPDialogTask(callSession, sas));
            }
        }
    };

    private synchronized void delayedQuit() {
        AnalyticsReporter.from(this).event(AppEvents.CALL_ENDED);

        if (wakeLock != null && wakeLock.isHeld()) {
            Log.d(TAG, "Releasing wake up lock");
            wakeLock.release();
        }

        Log.i(TAG, "CallActivity DelayQuit");
        proximityManager.release(0);
        callMenu.setVisibility(View.GONE);

        Log.d(TAG, "Start quit timer");
        if (quitTimer != null) {
            quitTimer.schedule(new QuitTimerTask(), QUIT_DELAY);
        } else {
            finish();
        }
    }

    private SipCallSessionInfo getActiveCallSession(){
        return call0;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.df(TAG, "Key down : %s", keyCode);
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:
                //
                // Volume has been adjusted by the user.
                //
                Log.d(TAG, "onKeyDown: Volume button pressed");
                int action = AudioManager.ADJUST_RAISE;
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    action = AudioManager.ADJUST_LOWER;
                }

                // Detect if ringing
                SipCallSessionInfo currentCallInfo = getActiveCallSession();
                // If not any active call active
                if (currentCallInfo == null) {
                    break;
                }

                IService service = connector.getService();
                if (service != null) {
                    try {
                        service.adjustVolume(currentCallInfo, action, AudioManager.FLAG_SHOW_UI);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Can't adjust volume", e);
                    }
                }

                return true;
            case KeyEvent.KEYCODE_CALL:
            case KeyEvent.KEYCODE_ENDCALL:
                return callAnswerView.onKeyDown(keyCode, event);
            case KeyEvent.KEYCODE_SEARCH:
                // Prevent search
                return true;
            default:
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.df(TAG, "Key up : %s", keyCode);
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_CALL:
            case KeyEvent.KEYCODE_SEARCH:
                return true;
            case KeyEvent.KEYCODE_ENDCALL:
                return callAnswerView.onKeyDown(keyCode, event);

        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean shouldActivateProximity() {
        if(lastMediaState != null) {
            if(lastMediaState.isBluetoothScoOn) {
                return false;
            }
            if(lastMediaState.isSpeakerphoneOn && ! useAutoDetectSpeaker) {
                return false;
            }
        }

        if (call0 == null){
            return false;
        }


        boolean isValidCallState = true;

        if (!call0.isAfterEnded()) {
            int state = call0.getCallState();

            isValidCallState &= (
                    (state == SipCallSession.InvState.CONFIRMED) ||
                            (state == SipCallSession.InvState.CONNECTING) ||
                            (state == SipCallSession.InvState.CALLING) ||
                            (state == SipCallSession.InvState.EARLY && !call0.isIncoming())
            );
        } else {
            return false;
        }

        return isValidCallState;
    }

    @Override
    public void onProximityTrackingChanged(boolean acquired) {
        IService service = connector.getService();
        if (service == null){
            return;
        }

        if(useAutoDetectSpeaker) {
            if(acquired) {
                if(lastMediaState == null || lastMediaState.isSpeakerphoneOn) {
                    try {
                        service.setSpeakerphoneOn(false);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Can't run speaker change");
                    }
                }
            }else {
                if(lastMediaState == null || !lastMediaState.isSpeakerphoneOn) {
                    try {
                        service.setSpeakerphoneOn(true);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Can't run speaker change");
                    }
                }
            }
        }
    }

    /* Runnables below */
    private class UpdateUIFromCallRunnable implements Runnable {
        private final SipCallSessionInfo mainCallSession;

        private UpdateUIFromCallRunnable(SipCallSessionInfo mainCallSession) {
            this.mainCallSession = mainCallSession;
        }

        @Override
        public void run() {
            Log.inf(TAG, "UpdateUIFromCallRunnable; mainCallSession [%s]", mainCallSession);
            updateUI(mainCallSession);

            if (mainCallSession != null) {
                switch (mainCallSession.getCallState()) {
                    case SipCallSession.InvState.INCOMING:
                    case SipCallSession.InvState.EARLY:
                    case SipCallSession.InvState.CALLING:
                    case SipCallSession.InvState.CONNECTING:
                        Log.d(TAG, "Acquire wake up lock");
                        if (wakeLock != null && !wakeLock.isHeld()) {
                            wakeLock.acquire();
                        }
                        break;
                    case SipCallSession.InvState.CONFIRMED:
                        AnalyticsReporter.from(CallActivity.this).event(AppEvents.CALL_ESTABLISHED);
                        break;
                    case SipCallSession.InvState.NULL:
                    case SipCallSession.InvState.DISCONNECTED:
                        Log.df(TAG, "Active call session is disconnected or null wait for quit... state=%s, session: %s", mainCallSession.getCallState(), mainCallSession);
                        delayedQuit();
                        break;
                }
            }
        }
    }

    private class UpdateUIFromMediaRunnable implements Runnable {
        @Override
        public void run() {
            callMenu.setMediaState(lastMediaState);
            proximityManager.updateProximitySensorMode();
        }
    }

    private class ShowZRTPDialogTask implements Runnable {
        private String sasString;
        private SipCallSession callSession;

        public ShowZRTPDialogTask(SipCallSession call, String sas) {
            callSession = call;
            sasString = sas;
        }

        @Override
        public void run() {
            final IService service = connector.getService();
            try {
                SasDialogFragment fragment = new SasDialogFragment();
                fragment.setSas(sasString);
                fragment.setSayFirst(!callSession.isIncoming());

                // Confirm ZRTP SAS.
                fragment.setOnConfirm(new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Log.d(TAG, "ZRTP confirmed");
                        try {
                            if (service != null) {
                                try {
                                    service.zrtpSASVerified(callSession.getCallId());
                                } catch (RemoteException e) {
                                    Log.e(TAG, "Error while calling service", e);
                                }
                                dialog.dismiss();
                            }
                        } catch(Exception ex){
                            Log.e(TAG, "Exception: cannot confirm ZRTP", ex);
                        }
                    }
                });

                // Reject ZRTP SAS.
                fragment.setOnReject(new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Log.d(TAG, "ZRTP rejected");
                        try {
                            if (service != null) {
                                try {
                                    service.zrtpSASRevoke(callSession.getCallId());
                                    service.hangup(callSession.getCallId(), 0);
                                } catch (RemoteException e) {
                                    Log.e(TAG, "Error while calling service", e);
                                }
                                dialog.dismiss();
                            }
                        } catch(Exception ex){
                            Log.e(TAG, "Exception: cannot reject ZRTP", ex);
                        }
                    }
                });

                fragment.show(getFragmentManager(), "ZRTPDialog");
            } catch(Exception ex){
                Log.e(TAG, "Exception: cannot show SAS dialog", ex);
            }
        }
    }

    private class QuitTimerTask extends TimerTask {
        @Override
        public void run() {
            Log.d(TAG, "Run quit timer");
            finish();
        }
    };

    private static final int INFO_MENU = 1;
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Call info
        menu.add(Menu.NONE, INFO_MENU, Menu.NONE, R.string.info)
                .setIcon(R.drawable.ic_menu_more)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case INFO_MENU:
                Log.v(TAG, "Going to display call info");

                SipCallSessionInfo inf = getActiveCallSession();
                if (inf == null){
                    Log.w(TAG, "Null call info, cannot continue");
                    return true;
                }

                onCallAction(ICallActionListener.DETAILED_DISPLAY, inf);
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * !legacy last resort option!
     * Get the call with the higher priority comparing two calls
     * @param call1 First call object to compare
     * @param call2 Second call object to compare
     * @return The call object with highest priority
     */
    private SipCallSession getPrioritaryCall(SipCallSession call1, SipCallSession call2) {
        // We prefer the not null
        if (call1 == null) {
            return call2;
        } else if (call2 == null) {
            return call1;
        }
        // We prefer the one not terminated
        if (call1.isAfterEnded()) {
            return call2;
        } else if (call2.isAfterEnded()) {
            return call1;
        }
        // We prefer the one not held
        if (call1.isLocalHeld()) {
            return call2;
        } else if (call2.isLocalHeld()) {
            return call1;
        }
        // We prefer the older call
        // to keep consistancy on what will be replied if new call arrives
        return (call1.getCallStart() > call2.getCallStart()) ? call2 : call1;
    }

    @Override
    protected String activityAnalyticsName() {
        return this.getClass().getSimpleName();
    }
}

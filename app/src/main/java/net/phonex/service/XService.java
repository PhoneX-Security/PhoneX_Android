package net.phonex.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.SurfaceView;

import net.phonex.PhonexSettings;
import net.phonex.R;
import net.phonex.accounting.PermissionManager;
import net.phonex.autologin.AutoLoginManager;
import net.phonex.core.Constants;
import net.phonex.core.IService;
import net.phonex.core.Intents;
import net.phonex.core.MemoryPrefManager;
import net.phonex.db.DBHelper.DatabaseHelper;
import net.phonex.db.DBProvider;
import net.phonex.db.entity.CallFirewall;
import net.phonex.db.entity.CallLog;
import net.phonex.db.entity.SipCallSession;
import net.phonex.db.entity.SipCallSessionInfo;
import net.phonex.db.entity.SipClist;
import net.phonex.db.entity.SipProfile;
import net.phonex.db.entity.SipProfileState;
import net.phonex.events.EventListener;
import net.phonex.ft.storage.FileDecryptManager;
import net.phonex.ft.transfer.FileTransferManager;
import net.phonex.gcm.GcmManager;
import net.phonex.license.LicenseExpirationCheckManager;
import net.phonex.pref.PhonexConfig;
import net.phonex.pref.PreferencesConnector;
import net.phonex.pref.PreferencesManager;
import net.phonex.pub.a.PjManager;
import net.phonex.pub.parcels.CertUpdateParams;
import net.phonex.pub.parcels.CertUpdateProgress;
import net.phonex.pub.parcels.KeyGenProgress;
import net.phonex.service.messaging.AmpDispatcher;
import net.phonex.service.messaging.MessageManager;
import net.phonex.service.runEngine.AppWakeLock;
import net.phonex.service.xmpp.XmppManager;
import net.phonex.sip.PjCallback;
import net.phonex.sip.SipStatusCode;
import net.phonex.soap.CertificateSelfCheckTask;
import net.phonex.soap.CertificateUpdateTask;
import net.phonex.soap.ClistFetchCall;
import net.phonex.soap.ClistFetchParams;
import net.phonex.soap.LicenseCheckCall;
import net.phonex.soap.ServiceConstants;
import net.phonex.ui.CallErrorActivity;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.ProfileUtils;
import net.phonex.util.android.StatusbarNotifications;
import net.phonex.util.system.ProcKiller;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XService extends Service {
	private static final String TAG = "XService";

    public static final int TOAST_MESSAGE = 0;
    public static final int TOAST_MESSAGE_CALL_ENDED = 1;
    public static final int TOAST_MESSAGE_CUSTOM_ERROR = 2;
	
	// helper reference number of bound clients
	private static int sNumBoundClients = 0;
	private static boolean sStopped=false;
    private boolean holdResources = false;
	
	//storage password is saved in memory
	private String storagePass;	

	private AppWakeLock appWakeLock;
	private boolean autoHangupCurrent = true;
	private boolean supportMultipleCalls = false;

	private static XService singleton = null;
	
	// Timer for watch-dog restart
    protected Timer restartTimer;
	
	// onStart mutex
    protected final Object onStartMutex = new Object();
    protected boolean onStartCalled=false;
    
    //
    // <ALARM_CODE>
    //
    // Alarm manager for long running background tasks
    protected AlarmManager alarmManager;
    // Thread for handler taking care of alarm onReceive()
    protected static HandlerThread alarmExecutorThread;
    // Handler for alarm stuff.
    protected AlarmExecutor alarmExecutor;
    // Broadcast receiver for listening alarm broadcasts.
    protected InternalAlarmReceiver alarmReceiver;
    // Constants for alarm
	private static final String ALARM_ACTION = "net.phonex.service.alarm";
	private static final String EXTRA_ALARM_SCHEME = "phonexalarm";
	private static final String EXTRA_ALARM_ACTION = "alarmAction";
	private static final String EXTRA_ALARM_ACTION_DHKEY = "dhkey";
	//
	// </ALARM_CODE>
	//

	//
	// <Certificate updater code>
	private static final int  TEXECUTOR_NTHREADS = 2;
    protected ScheduledThreadPoolExecutor texecutor;
    Queue<Runnable> rescheduledRunnables = new LinkedList<>();

    // cert update
    protected ScheduledFuture<?> futureCertUpdate;
    protected CertificateUpdateTask certUpdateTask;

    // clist fetch
    protected ScheduledFuture<?> futureContactListFetch;
    protected ClistFetchCall contactListFetchCall;

    // Manager
    protected FileTransferManager ftManager;
    protected MessageManager msgManager;
    protected XmppManager xmppManager;
    protected MyPresenceManager myPresenceManager;
    protected MessageSecurityManager messageSecurityManager;
    protected ContactsManager contactsManager;
    protected PermissionManager permissionManager;

    protected FileDecryptManager fileDecryptManager;
    protected PairingRequestManager pairingRequestManager;
    protected LogSendingManager logSendingManager;
    protected AutoLoginManager autoLoginManager;
    protected LoginManager loginManager;
    protected GcmManager gcmManager;

    protected LicenseExpirationCheckManager licenseExpirationCheckManager;

    protected Handler errorMessageHandler = new ErrorMessageSvcHandler(this);
    protected boolean hasSomeActiveAccount = false;
    protected int pollingInterval=0;

    protected WakeLock wakeLock;
    protected WifiLock wifiLock;
    protected EventListener deviceStateReceiver;
    protected PreferencesConnector prefsWrapper;
    protected ServicePhoneStateReceiver phoneConnectivityReceiver;
    protected TelephonyManager telephonyManager;

    protected StatusbarNotifications notificationManager;
    protected XServiceHandler mExecutor;
    protected PjManager pjManager;
    protected static HandlerThread handlerThread;

    protected AccountStatusContentObserver statusObserver = null;
    protected ServiceBroadcastReceiver serviceReceiver;

    protected List<ComponentName> activitiesForOutgoing = new ArrayList<>();
    protected List<ComponentName> deferedUnregisterForOutgoing = new ArrayList<>();
    protected static String UI_CALL_PACKAGE = null;

    protected LoginReceiver loginReceiver;

    /**
     * Whether there are any clients bound to this service.
     */
    private AtomicBoolean clientsBound;

    /**
     * Holds last connectivity change status.
     */
    private final AtomicBoolean connected = new AtomicBoolean(true);


    /**
     * Whether task should be cleaned up.
     *
     * Set to true, when task is killed by user, but some clients are bound.
     * If all clients unbind, clean the service.
     * If some clients bind in the meantime, cancel the cleanup.
     */
//    private AtomicBoolean cleanupService;
//    private AtomicBoolean cleanupServiceInProgress;

    protected class LoginReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.df(TAG, "LoginReceiver; Internal receive %s", action);

            switch (action) {
                case Intents.ACTION_TRIGGER_LOGIN:
                    Log.d(TAG, "ACTION_TRIGGER_LOGIN received");
                    getLoginManager().triggerLogin(
                            intent.getStringExtra(Intents.EXTRA_LOGIN_SIP),
                            intent.getStringExtra(Intents.EXTRA_LOGIN_PASSWORD),
                            intent.getStringExtra(Intents.EXTRA_LOGIN_DOMAIN),
                            intent.getBooleanExtra(Intents.EXTRA_LOCK_PIN_AFTER_STARTUP, false)
                            );
                    break;
                default:
                    Log.wf(TAG, "Unregistered action: [%s]", action);
                    break;
            }
        }
    }

	//
	// </Certificate updater code>
	// 
	
	// Implement public interface for the service
	private final IService.Stub binder = new XServiceBinder(this);

    /**
     * Returns main handler that processes queries in a serial manner.
     * @return
     */
    public XServiceHandler getHandler() {
        // create mExecutor lazily
        if (mExecutor == null) {
        	mExecutor = new XServiceHandler(this);
        }
        return mExecutor;
    }

    /**
     * Executes job on the internal handler.
     * @param runnable
     */
    public void executeJob(Runnable runnable){
        final XServiceHandler handler = getHandler();
        if (handler==null){
            Log.e(TAG, "Handler is null");
            return;
        }

        handler.execute(runnable);
    }

	protected Object getFileTransferProgress(long sipMessageId) {
        return ftManager.getFileTransferProgress(sipMessageId);
	}

    public MessageSecurityManager getMessageSecurityManager() {
        return messageSecurityManager;
    }

    @Override
	public void onCreate() {
		super.onCreate();
        Log.i(TAG, "onCreate");
		singleton = this;
		sStopped  = false;

        registerLoginReceiver();

		prefsWrapper = new PreferencesConnector(this);
		Log.setLogLevel(PhonexSettings.debuggingRelease() ? prefsWrapper.getLogLevel() : 0);

		telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		notificationManager = new StatusbarNotifications(this);
		notificationManager.onServiceCreate();
		appWakeLock = new AppWakeLock((PowerManager) getSystemService(Context.POWER_SERVICE));
		
		boolean hasSetup = prefsWrapper.getBoolean(PhonexConfig.HAS_ALREADY_SETUP_SERVICE, false);
        Log.df(TAG, "Service was configured: %s", hasSetup);
		
		// XMPP manager, all that has something to do with XMPP.
		xmppManager = new XmppManager(this);
		xmppManager.register();
		
		// General presence manager
		myPresenceManager = new MyPresenceManager(this);
		myPresenceManager.register();

        // AlarmManager (for conflict with Android class called PhonexAlarmManager)
        messageSecurityManager = new MessageSecurityManager(this);
        messageSecurityManager.register();

        licenseExpirationCheckManager = new LicenseExpirationCheckManager(this);
        licenseExpirationCheckManager.register();

        registerServiceBroadcasts();
        registerAlarmBroadcasts();
        registerDeviceStateReceiver();

        texecutor = new ScheduledThreadPoolExecutor(TEXECUTOR_NTHREADS);
        ftManager = new FileTransferManager(this);
        ftManager.register();

        certUpdateTask = new CertificateUpdateTask(this);
        contactListFetchCall = new ClistFetchCall(this);

        msgManager = new MessageManager(this);
        msgManager.register();
        msgManager.setFtMgr(ftManager);

        contactsManager = new ContactsManager(this);
        contactsManager.register();

        gcmManager = new GcmManager(this);

        permissionManager = new PermissionManager(this);

        fileDecryptManager = new FileDecryptManager(this);

        pairingRequestManager = new PairingRequestManager(this);

        logSendingManager = new LogSendingManager(this);
        autoLoginManager = new AutoLoginManager(this);
        loginManager = new LoginManager(this);

		if(!hasSetup) {
			Log.i(TAG, "Settings reset");
			prefsWrapper.resetToDefaults();
		}

        clientsBound = new AtomicBoolean(false);

		// try to recover storage password if needed
        fixStoragePassMissing();
	}

    private void registerLoginReceiver(){
        Log.d(TAG, "Registering login receiver");
        loginReceiver = new LoginReceiver();
        IntentFilter loginIntentFilter = new IntentFilter(Intents.ACTION_TRIGGER_LOGIN);
        MiscUtils.registerReceiver(this, loginReceiver, loginIntentFilter);
    }

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.inf(TAG, "Destroying XService; hasSomeActiveAccount=%s", hasSomeActiveAccount);
		
		// Shut down task executor.;
		texecutor.shutdown();
		ftManager.unregister();
        messageSecurityManager.unregister();
        msgManager.unregister();
        licenseExpirationCheckManager.unregister();
        contactsManager.unregister();

        unregisterDeviceStateReceiver();
		unregisterBroadcasts();
		unregisterServiceBroadcasts();
		unregisterAlarmBroadcasts();

        unregisterLoginReceiver();

		notificationManager.onServiceDestroy();

		getHandler().execute(new FinalizeDestroyRunnable());
		onStartCalled = false;
		
		Log.v(TAG, "OnDestroy() call ended");
	}

	public void cleanStop () {
        Log.df(TAG, "cleanStop; setting app_closing to true");
        MemoryPrefManager.setPreferenceBooleanValue(this, MemoryPrefManager.APP_CLOSING, true);
		// Destroy XMPP sessions.
		getHandler().execute(new SvcRunnable("XMPPUnregister") {
            @Override
            protected void doRun() throws SameThreadException {
                if (xmppManager != null) {
                    xmppManager.unregister();
                }
            }
        });
		
		getHandler().execute(new DestroyRunnable());
		Log.d(TAG, "CleanStop() called - 1");
		
		try {
			long stime = System.currentTimeMillis();
			while(!sStopped){
				long ctime = System.currentTimeMillis();
				if ((ctime-stime) > 7500) break;
				
				Thread.sleep(100);
			}
			Log.df(TAG, "CleanStop() called - 2; delta=%s; sStopped=%s", ((System.currentTimeMillis()) - (stime)), sStopped);
			
		} catch(InterruptedException e){
			Log.e(TAG, "Interrupted waiting", e);
		}

		this.stopSelf();
	}

    /**
     * Service's internal broadcast receiver.
     */
    private class ServiceBroadcastReceiver extends BroadcastReceiver {
        private ServiceBroadcastReceiver() {
            super();
        }

        @Override
        public void onReceive(Context context, final Intent intent) {
            final String action = intent.getAction();
            if (Intents.ACTION_OUTGOING_UNREGISTER.equals(action)) {
                unregisterForOutgoing(intent.getParcelableExtra(Intents.EXTRA_OUTGOING_ACTIVITY));
            } else if (Intents.ACTION_DEFER_OUTGOING_UNREGISTER.equals(action)) {
                deferUnregisterForOutgoing(intent.getParcelableExtra(Intents.EXTRA_OUTGOING_ACTIVITY));
            } else if (Intents.ACTION_LOGIN_SUCCESSFUL.equals(action)) {
                getHandler().execute(new SvcRunnable("loginSuccessfulTask") {
                    @Override
                    protected void doRun() throws SameThreadException {
                        onLoginSuccessful(false);
                    }
                });
            } else if (Intents.ACTION_RESURRECTED.equalsIgnoreCase(action)) {
                // Action called after killer did its job.
                // Time to recover from stored credentials.
                Log.v(TAG, "Resurrection event received");

                getHandler().execute(new SvcRunnable("ResurrectTask") {
                    @Override
                    protected void doRun() throws SameThreadException {
                        fixStoragePassMissing();
                    }
                });
            } else if (Intents.ACTION_CALL_CUSTOM.equalsIgnoreCase(action)) {
                // Catch these intents if call should be made directly from the event listener and no
                // via CallStarterActivity. Check if we are connected. If not, show error.
                if (!isConnectionValid()
                        || !isConnectivityValid()
                        || pjManager ==null
                        || !pjManager.isCreated())
                {
                    Log.w(TAG, "Cannot call, not connected");
                    notifyUserOfCustomMessage(getString(R.string.call_error_not_valid));
                    return;
                }

                Log.v(TAG, "Sending call intent.");
                Intent nIntent = new Intent(intent);
                nIntent.setAction(Intent.ACTION_CALL);
                nIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(nIntent);

                // Following code is used to invoke call directly, no UI.
                // Connectivity is on, launch event handler.
                /*getHandler().execute(new SvcRunnable("CallTask") {
                    @Override
                    protected void doRun() throws SameThreadException {
                        onCallIntentReceived(intent);
                    }
                });*/
            } else if (Intents.ACTION_HIDE_CALLLOG_NOTIF.equals(action)){
                if (notificationManager!=null) {
                    notificationManager.cancelMissedCalls();
                }
            } else if (Intents.ACTION_SET_CHAT_INFO.equals(action)){
                if (notificationManager!=null) {
                    notificationManager.setChatInfo(intent);
                }
            } else if (Intents.ACTION_MISSED_CALL_RECEIVED.equals(action)){
                if (notificationManager!=null) {
                    notificationManager.notifyMissedCall(intent);
                }
            } else if (Intents.ACTION_SETTINGS_MODIFIED.equals(action)){
                onSettingsModified();
            }
        }
    }

	private void registerServiceBroadcasts() {
        Log.vf(TAG, "registerServiceBroadcasts");
	    if(serviceReceiver == null) {
            serviceReceiver = new ServiceBroadcastReceiver();

	        IntentFilter intentfilter = new IntentFilter();
            intentfilter.addAction(Intents.ACTION_DEFER_OUTGOING_UNREGISTER);
            intentfilter.addAction(Intents.ACTION_OUTGOING_UNREGISTER);
            intentfilter.addAction(Intents.ACTION_LOGIN_SUCCESSFUL);
            intentfilter.addAction(Intents.ACTION_CALL_CUSTOM);
            intentfilter.addAction(Intents.ACTION_RESURRECTED);
            intentfilter.addAction(Intents.ACTION_HIDE_CALLLOG_NOTIF);
            intentfilter.addAction(Intents.ACTION_SET_CHAT_INFO);
            intentfilter.addAction(Intents.ACTION_MISSED_CALL_RECEIVED);
            intentfilter.addAction(Intents.ACTION_SETTINGS_MODIFIED);
            MiscUtils.registerReceiver(this, serviceReceiver, intentfilter);

            // Register call intents.
            intentfilter = new IntentFilter();
            intentfilter.addAction(Intents.ACTION_CALL_CUSTOM);
            intentfilter.addDataScheme(Constants.PROTOCOL_SIPS);
            intentfilter.addDataScheme(Constants.PROTOCOL_SIP);
            intentfilter.addDataScheme(Constants.PROTOCOL_CSIP);
            MiscUtils.registerReceiver(this, serviceReceiver, intentfilter);
	   }
	}

	private void unregisterServiceBroadcasts() {
        Log.vf(TAG, "unregisterServiceBroadcasts");
	    if(serviceReceiver != null) {
	        unregisterReceiver(serviceReceiver);
	        serviceReceiver = null;
	    }
	}
	
	/**
	 * Starts service by call startService()
	 * @param ctxt
	 * @param async
	 */
	public static void start(final Context ctxt, boolean async){
        final Intent serviceIntent = getStartServiceIntent(ctxt.getApplicationContext());
		if (!async){
	        Log.v(TAG, "Going to start XService");
	        ctxt.getApplicationContext().startService(serviceIntent);
	        return;
		}
		
		Thread t = new Thread("StartXService") {
            public void run() {
    	        Log.v(TAG, "Going to start XService async");
    	        ctxt.getApplicationContext().startService(serviceIntent);
            };
        };
        t.start();
	}

    /**
     * Register these receivers early in the lifecycle
     */
    private void registerDeviceStateReceiver(){
        Log.inf(TAG, "registerDeviceStateReceiver");
        // Register own broadcast receiver
        if (deviceStateReceiver == null) {
            deviceStateReceiver = new EventListener(this);
            deviceStateReceiver.registerReceiver();
            deviceStateReceiver.startMonitoring();
        }
    }

    private void unregisterDeviceStateReceiver() {
        Log.inf(TAG, "unregisterDeviceStateReceiver");
        if(deviceStateReceiver != null) {
            try {
                Log.d(TAG, "Stop and unregister device receiver");
                deviceStateReceiver.stopMonitoring();
                deviceStateReceiver.unregisterReceiver();
                deviceStateReceiver = null;
            } catch (IllegalArgumentException e) {
                // This is the case if already unregistered itself.
                Log.d(TAG, "Device receiver already unregistered.");
            }
        }
    }

        /**
         * Register broadcast receivers.
         */
	private void registerBroadcasts() {
        Log.vf(TAG, "registerBroadcasts");
		
		// Telephony
		if (phoneConnectivityReceiver == null) {
			Log.d(TAG, "Listen for phone state");
			phoneConnectivityReceiver = new ServicePhoneStateReceiver(this);
			
			telephonyManager.listen(phoneConnectivityReceiver, /*PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
					| */PhoneStateListener.LISTEN_CALL_STATE);
		}
		// Content observer
		if(statusObserver == null) {
        	statusObserver = new AccountStatusContentObserver(errorMessageHandler);
    		getContentResolver().registerContentObserver(SipProfile.ACCOUNT_STATUS_URI, true, statusObserver);
		}
	}

	/**
	 * Remove registration of broadcasts receiver.
	 */
	private void unregisterBroadcasts() {
        Log.vf(TAG, "unregisterBroadcasts");

		if (phoneConnectivityReceiver != null) {
			Log.d(TAG, "Unregister phone receiver");
			telephonyManager.listen(phoneConnectivityReceiver, PhoneStateListener.LISTEN_NONE);
			phoneConnectivityReceiver = null;
		}

		if(statusObserver != null) {
    		getContentResolver().unregisterContentObserver(statusObserver);
    		statusObserver = null;
    	}

	}

    public void unregisterLoginReceiver() {
        Log.d(TAG, "unregisterLoginReceiver");
        if (loginReceiver != null) {
            unregisterReceiver(loginReceiver);
            Log.d(TAG, "Unregistered login receiver");
            loginReceiver = null;
        } else {
            Log.d(TAG, "Login receiver was not registered, cannot unregister");
        }
    }
	
	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("deprecation")
    @Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		Log.vf(TAG, "onStart() XService this=%s; pid=%s", this, android.os.Process.myPid());
		onStartRoutine(intent);
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.vf(TAG, "onStartCommand() intent=%s, XService this=%s; pid=%s", intent, this, android.os.Process.myPid());
		onStartRoutine(intent);
		
	    // We want this service to continue running until it is explicitly
	    // stopped, so return sticky.
	    return START_STICKY;
	}
	
	public void onStartRoutine(Intent intent){
		Log.vf(TAG, "onStartRoutine() XService: %s, CL=0x%x; TID=%s;",
                this, this.getClassLoader().hashCode(), android.os.Process.myTid());
		
		synchronized(onStartMutex){
			onStartCalled = true;
			
			if(intent != null) {
	    		Parcelable p = intent.getParcelableExtra(Intents.EXTRA_OUTGOING_ACTIVITY);
	    		if(p != null) {
	    		    ComponentName outActivity = (ComponentName) p;
	    		    registerForOutgoing(outActivity);
	    		}
	    		
//	    		Boolean onBoot = intent.getBooleanExtra(Intents.EXTRA_ON_BOOT, false);
//	    		if (onBoot){
//	    			Log.v(TAG, "Service was started by on_boot_completed event.");
	    			
//	    			// Ensure that no password is left in database
//	    	    	try {
//	    	    		ContentValues args = new ContentValues();
//	    	    		args.put(SipProfile.FIELD_PASSWORD, "");
//	    	    		args.put(SipProfile.FIELD_ACTIVE, "0");
//	    	    		getContentResolver().update(SipProfile.ACCOUNT_URI, args, null, null);
//	    	    		getContentResolver().delete(SipClist.CLIST_URI, null, null);
//	    	    		Log.d(TAG, "Old data was wiped out of database");
//	    	    	}catch(Exception e){
//	    	    		Log.e(TAG, "Cannot delete password on user exit", e);
//	    	    	}
//	    		}
			}
			
	        // Check connectivity, else just finish itself.
			// This also checks if service has been quit. If yes, no start.
			// Ph4r05: disabled. Service holds auth data, after restart
			// it will get lost and further service start would be 
			// useless.
            connected.set(isConnectionValid());
	        if (!isConnectivityValid()) {
	        	// User message notification only in case app has not been quit.
	        	if(!prefsWrapper.getBoolean(PhonexConfig.APP_CLOSED_BY_USER, false)) {
	        		notifyUserOfMessage(R.string.connection_not_valid);
	    	    }

	            return;
	        }
	        
	        // try to recover storage password if needed
	        fixStoragePassMissing();
			
			// Autostart the stack - make sure started and that receivers are there
			// NOTE : the stack may also be autostarted cause of phoneConnectivityReceiver
			if (!loadStack()) {
				Log.e(TAG, "Loading stack was not successful");
				return;
			}
		}
	}

	public void registerForOutgoing(ComponentName activityKey) {
	    if(!activitiesForOutgoing.contains(activityKey)) {
	        activitiesForOutgoing.add(activityKey);
	    }
	}

	public void unregisterForOutgoing(ComponentName activityKey) {
	    activitiesForOutgoing.remove(activityKey);
	    
	    if(!isConnectivityValid()) {
	        cleanStop();
	    }
	}

	public void deferUnregisterForOutgoing(ComponentName activityKey) {
	    if(!deferedUnregisterForOutgoing.contains(activityKey)) {
	        deferedUnregisterForOutgoing.add(activityKey);
	    }
	}

	public void treatDeferUnregistersForOutgoing() {
	    for(ComponentName cmp : deferedUnregisterForOutgoing) {
	        activitiesForOutgoing.remove(cmp);
	    }
	    deferedUnregisterForOutgoing.clear();
        if(!isConnectivityValid()) {
            cleanStop();
        }
	}

    /**
     * Executed when settings are modified.
     */
    private void onSettingsModified(){
        final Locale lang = PhonexSettings.loadDefaultLanguageNoThrow(this);
        Log.vf(TAG, "onSettingsChanged, current locale=%s", lang);
    }
	
	/**
	 * Determines whether current connectivity is valid for use.
	 * Takes HAS_BEEN_QUIT into account. If was quit, return false.
	 * @return
	 */
    public boolean isConnectivityValid() {
	    if(prefsWrapper.getBoolean(PhonexConfig.APP_CLOSED_BY_USER, false)) {
	        return false;
	    }

	    boolean valid = prefsWrapper.isValidConnectionForIncoming();
	    if(activitiesForOutgoing.size() > 0) {
	        valid |= prefsWrapper.isValidConnectionForOutgoing();
	    }
	    return valid;
	}

    /**
     * Returns true if current connection is connected.
     * @return
     */
    public boolean isConnectionValid(){
        if(prefsWrapper.getBoolean(PhonexConfig.APP_CLOSED_BY_USER, false)) {
            return false;
        }

        return prefsWrapper.isValidConnectionRaw();
    }

    /**
     * Returns current active network networkInfo.
     * @return
     */
    public NetworkInfo getCurrentNetworkInfo(){
        return prefsWrapper.getCurrentNetworkInfo();
    }

    /**
     * Event signalized by event listeners when connectivity is changed.
     *
     * @param on    if true, connectivity has been recovered, otherwise connectivity was lost.
     */
    public void onConnectivityChanged(boolean on) throws SameThreadException {
        try {
            Log.vf(TAG, "onConnChanged, recovered=%s", on);
            connected.set(on);

            // old method (restart stack if last restart happened more than day ago)
            long restartThreshold = 24*60*60*1000;
            long currTime = System.currentTimeMillis();
            long lastSipStackStartTime = currTime;
            String time = getPrefs().getString(PhonexConfig.LAST_SIP_STACK_START_TIME);
            if (time != null){
                lastSipStackStartTime = Long.valueOf(time);
            }
            boolean restartStack = false;

            Log.df(TAG, "onConnChanged: restartThreshold [%d], currTime [%d], lastRestartTime [%d]", restartThreshold, currTime, lastSipStackStartTime);

            if (!on){
                if (currTime - lastSipStackStartTime > restartThreshold) {
                    restartStack = true;
                    stopSipStack();
                    Log.df(TAG, "onConnChanged: stopping sip stack; the last restart happened [%d] secs ago.", (currTime - lastSipStackStartTime) / 1000);
                }

            } else {
                if (!isSipStackCreated()){
                    restartStack = true;
                    restartSipStack();
                    Log.df(TAG, "onConnChanged: restarting sip stack");
                }
            }

            // new method
            if (!restartStack){
                getPjManager().onConnectivityChange(getAccount(SipProfile.USER_ID), on);
            }

            // TODO: refactor to loose binding - intent.
            getMsgManager().onConnectivityChange(on);
            getXmppManager().onConnectivityChanged(on);

            //
            //if (on) getAutoLoginManager().triggerSaveLoginOnConnectivityChange();

            // Broadcast intent about connectivity change so modules can react.
            final Intent connChangeIntent = new Intent(Intents.ACTION_CONNECTIVITY_CHANGE);
            connChangeIntent.putExtra(Intents.EXTRA_CONNECTIVITY_CHANGE, on);
            MiscUtils.sendBroadcast(getApplicationContext(), connChangeIntent);

            // re-execute runnables
            rescheduleRunnables();

        } catch(Exception e){
            Log.ef(TAG, e, "Exception in event handling, connectivity=%s", on);
        }
    }
	
	/**
	 * Tries to load PJSIP stack
	 * @return
	 */
	private boolean loadStack() {
		//Ensure pjService exists
		if(pjManager == null) {
			pjManager = new PjManager();
		}
		pjManager.setService(this);
		
		if (pjManager.loadStack()) {
			return true;
		}
		return false;
	}

	@Override
	public IBinder onBind(Intent intent) {
		String serviceName = intent.getAction();

        clientsBound.set(true);
//        if (cleanupServiceInProgress.get()) {
//            Log.w(TAG, "Asked to bind but service cleanup in progress");
//            return null;
//        }
//        //if (cleanupService.compareAndSet(true, false)) {
//        if (cleanupService.compareAndSet(true, true)) {
//            Log.d(TAG, "Pending service cleanup was cancelled");
//            return null;
//        }
		sNumBoundClients+=1;
		Log.df(TAG, "onBind(); Action is %s; numClients=%s", serviceName, sNumBoundClients);
		
		// Ph4r05: originally fixed problem with not starting PjStack
		// but it was fixed by intent rename, now is not needed, only if intent demands it
		Boolean autostart = intent.getBooleanExtra(Intents.EXTRA_AUTO_START_STACK, false);
		if (autostart != null && autostart && !onStartCalled && pjManager ==null){
			Log.v(TAG, "Starting stack in onBind() method");
			this.onStartRoutine(intent);
		}
		
		if (serviceName == null || serviceName.equalsIgnoreCase(Constants.INTENT_XSERVICE)) {
			Log.d(TAG, "Service returned");
			return binder;
		}
		
		Log.d(TAG, "Default service (XService) returned");
		return binder;
	}

	@Override
	public void onRebind(Intent intent) {
        clientsBound.set(true);
		sNumBoundClients += 1;
        Log.df(TAG, "onRebind(); clients=%s", sNumBoundClients);
		super.onRebind(intent);
	}

	@Override
	public boolean onUnbind(Intent intent) {
        clientsBound.set(false);
//        if (cleanupService.get()) {
//            Log.d(TAG, "All clients unbound, can proceed with cleanup");
//            taskRemovedCleanup();
//        }
		sNumBoundClients = 0;//-=1;
		Log.df(TAG, "onUnbind(); clients=%s", sNumBoundClients);
		return super.onUnbind(intent);
	}

	// This is always done in SipExecutor thread
	private void startSipStack() throws SameThreadException {
        Log.vf(TAG, "startSipStack");
		supportMultipleCalls = prefsWrapper.getBoolean(PhonexConfig.SUPPORT_MULTIPLE_CALLS);
		
		if(!isConnectivityValid()) {
			Log.e(TAG, "No need to start sip");
			return;
        }

		Log.d(TAG, "Start was asked and we should actually start now");
		if(pjManager == null) {
			Log.d(TAG, "Start was asked and pjService in not there");
			if(!loadStack()) {
				Log.e(TAG, "Unable to load SIP stack !! ");
				return;
			}
		}
		Log.d(TAG, "Ask pjservice to start itself");

		if (pjManager.startStack()) {
            onSipStackStarted();
		}
	}

    private void onSipStackStarted()  throws SameThreadException{
        Log.d(TAG, "onSipStackStarted");
        getPrefs().setString(PhonexConfig.LAST_SIP_STACK_START_TIME, String.valueOf(System.currentTimeMillis()));
        registerBroadcasts();
        addAllAccounts();
        // Signalize connectivity recovered to the XMPP
        // manager since this is usually executed
        // on service start & connectivity is valid.
        xmppManager.onConnectivityOn();
    }
	
	/**
	 * Safe stop the sip stack
	 * @return true if can be stopped, false if there is a pending call and the sip service should not be stopped
	 */
	public boolean stopSipStack() throws SameThreadException {
		Log.d(TAG, "Stop sip stack");
		boolean canStop = true;
		if(pjManager != null) {
			canStop = pjManager.stopStack();
		} else {
			Log.w(TAG, "SipStack is already null");
		}
		
		if(canStop) {
//            unregisterDeviceStateReceiver();
            unregisterBroadcasts();
			releaseResources();
			stopWatchdog();
		} else {
			Log.w(TAG, "Cannot stop sip stack");
		}

		return canStop;
	}
	
    public void restartSipStack() throws SameThreadException {
        if(stopSipStack()) {
            startSipStack();
        }else {
            Log.e(TAG, "Can't stop ... so do not restart ! ");
        }
    }

    public boolean isSipStackCreated(){
        if(pjManager != null) {
            return pjManager.isCreated();
        }
        return false;
    }
	
    /**
     * Forces SipStack process to crash.
     * 
     * @param byAssert if true assert() call is used, otherwise kill() call is used.
     * @throws SameThreadException
     */
//    public void restartBrutalSipStack(boolean byAssert) throws SameThreadException {
//    	if(pjManager != null) {
//    		if (byAssert){
//    			pjManager.pjsuaAssert();
//    		} else {
//    			pjManager.pjsuaKill();
//    		}
//    	} else {
//    		Log.w(TAG, "Asked to brutally restart SIP stack, but service is null");
//    	}
//    }

    /**
     * Notify user from a message the sip stack wants to transmit - call has ended
     * filter also those which are relevant
     * @param callErrorMessage
     */
    public void notifyUserOfEndedCall(CallErrorActivity.CallErrorMessage callErrorMessage) {
        Log.df(TAG, "User message notification of ended call: status_code=%d, is_incoming=%s", callErrorMessage.statusCode, callErrorMessage.isIncoming);
        errorMessageHandler.sendMessage(errorMessageHandler.obtainMessage(TOAST_MESSAGE_CALL_ENDED, callErrorMessage));
    }

    /**
     * Called on call end, broadcast missed call message if applicable.
     * @param callInfo
     * @param callLog
     */
    public void onCallEnded(SipCallSession callInfo, CallLog callLog){
        final int status = callInfo.getLastStatusCode();
        if ((status / 100) == 2){
            // Success termination, no missed call here.
            Log.vf(TAG, "Status family 200, no missedCall notification");
            return;
        }

        if (callInfo.isRemoteSideAnswered() || callInfo.isIncoming()){
            // Remote side will already have a call log.
            Log.vf(TAG, "No missedCall notification, incomming: %s, remoteSideAnswered: %s", callInfo.isIncoming(), callInfo.isRemoteSideAnswered());
            return;
        }

        if (status == SipStatusCode.FORBIDDEN
                || status == SipStatusCode.BUSY_HERE
                || status == SipStatusCode.DECLINE
                || status == SipStatusCode.NOT_IMPLEMENTED)
        {
            // No right to notify or user already knows the missed call.
            Log.vf(TAG, "No missedCall notification, status=%s", status);
            return;
        }

        final long accId = callInfo.getAccId();
        final SipProfile profile = SipProfile.getProfileFromDbId(this, accId, SipProfile.ACC_PROJECTION);
        AmpDispatcher.dispatchMissedCallNotification(this, profile.getSip(false), callLog.getRemoteContactSip(), callInfo.getSipCallId());
    }

    /**
     * Notify user from a message the sip stack wants to transmit.
     * For now it shows a toaster
     * @param msg String message to display
     */
	public void notifyUserOfMessage(String msg) {
		Log.df(TAG, "User message notification: str=%s", msg);
		errorMessageHandler.sendMessage(errorMessageHandler.obtainMessage(TOAST_MESSAGE, msg));
	}

    /**
     * Notify user from a message the sip stack wants to transmit.
     * For now it shows a toaster
     * @param msg String message to display
     */
    public void notifyUserOfCustomMessage(String msg) {
        Log.df(TAG, "User message notification: realstr=%s", msg);
        errorMessageHandler.sendMessage(errorMessageHandler.obtainMessage(TOAST_MESSAGE_CUSTOM_ERROR, msg));
    }

	/**
	 * Notify user from a message the sip stack wants to transmit.
     * For now it shows a toaster
	 * @param resStringId The id of the string resource to display
	 */
	public void notifyUserOfMessage(int resStringId) {
		Log.df(TAG, "User message notification: id=0x%s string=[%s]", Integer.toHexString(resStringId), this.getString(resStringId));
	    errorMessageHandler.sendMessage(errorMessageHandler.obtainMessage(TOAST_MESSAGE, resStringId, 0));
	}

	/**
	 * Add accounts from database to the stack.
	 */
	private void addAllAccounts() throws SameThreadException {
		Log.d(TAG, "We are adding all accounts right now....");

		boolean hasSomeSuccess = false;
		Cursor c = getContentResolver().query(SipProfile.ACCOUNT_URI, SipProfile.FULL_PROJECTION,
				SipProfile.FIELD_ACTIVE + "=?", new String[] {"1"}, null);
		
		if (c != null) {
			try {
				for(int index = 0; c.moveToNext() && index < 10; index++){
					SipProfile account = new SipProfile(c);
					Log.df(TAG, "Adding account [%s] id=[%s]", account.getDisplay_name(), account.getId());
					if (pjManager != null && pjManager.addAccount(account) ) {
						Log.df(TAG, "Account [%s] id=[%s] successfully added", account.getDisplay_name(), account.getId());
						hasSomeSuccess = true;
					}
					
					// Add to XMPP manager as well.
					xmppManager.accountChanged(account);
				}
			} catch (Exception e) {
				Log.e(TAG, "Error on looping over sip profiles", e);
			} finally {
				c.close();
			}
		}
		
		hasSomeActiveAccount = hasSomeSuccess;

		if (hasSomeSuccess) {
			acquireResources();
			
		} else {
			releaseResources();
			if (notificationManager != null) {
				Log.d(TAG, "Cancel registration in addAllAccounts()");
				notificationManager.cancelRegisters();
			}
		}
	}
	
	/**
	 * Publish presence state for all active users.
	 */
	private void publishPresenceForAllAccounts() throws SameThreadException {
		Log.d(TAG, "We are publishing presence for all accounts right now....");
		Cursor c = getContentResolver().query(SipProfile.ACCOUNT_URI, SipProfile.FULL_PROJECTION,
				SipProfile.FIELD_ACTIVE + "=?", new String[] {"1"}, null);
		
		if (c != null) {
			try {
				for(int index = 0; c.moveToNext() && index < 10; index++){
					SipProfile account = new SipProfile(c);
					Log.df(TAG, "Publishing account [%s] id=[%s]", account.getDisplay_name(), account.getId());

					publishPresence(account);
				}
			} catch (Exception e) {
				Log.e(TAG, "Exception on iterating account profiles", e);
			} finally {
				c.close();
			}
		}
	}
	
	/**
	 * Call from dynamic receiver. 
	 * 
	 * @param account
	 * @throws SameThreadException 
	 */
	public void accountChanged(SipProfile account) throws SameThreadException{
		if (account!=null){
			Log.vf(TAG, "Account changed, acc=%s", account.getUsername());
			
			// PJsip call to add account to the stack.
			setAccountRegistration(account, account.isActive() ? 1 : 0, true);
			
			// Add user to the XMPP stack.
			xmppManager.accountChanged(account);
		}
	}
	
	/**
	 * Account registration, call to pjService.
	 * 
	 * @param account
	 * @param renew
	 * @param forceReAdd
	 * @return
	 * @throws SameThreadException
	 */
	public boolean setAccountRegistration(SipProfile account, int renew, boolean forceReAdd) throws SameThreadException {
		boolean status = false;
		if(pjManager != null) {
			status = pjManager.setAccountRegistration(account, renew, forceReAdd);
		}		
		
		return status;
	}

	/**
	 * Remove accounts from database
	 */
	private void unregisterAllAccounts(boolean cancelNotification) throws SameThreadException {
		releaseResources();
		Log.d(TAG, "Remove all accounts");
		
		Cursor c = getContentResolver().query(SipProfile.ACCOUNT_URI, SipProfile.FULL_PROJECTION, null, null, null);
		if (c != null) {
			try {
				for(; c.moveToNext(); ){
					SipProfile account = new SipProfile(c);
					setAccountRegistration(account, 0, false);
				}
			} catch (Exception e) {
				Log.e(TAG, "Error on looping over sip profiles", e);
			} finally {
				c.close();
			}
		}

		if (notificationManager != null && cancelNotification) {
			Log.d(TAG, "Cancel registration in unregisterAllAccounts()");
			notificationManager.cancelRegisters();
		}
	}

	public SipProfileState getSipProfileState(int accountDbId) {
		final SipProfile acc = getAccount(accountDbId);
		if(pjManager != null && acc != null) {
			return pjManager.getProfileState(acc);
		}
		return null;
	}

	public void updateRegistrationsState() {
		Log.d(TAG, "Update registration state");
		
		ArrayList<SipProfileState> activeProfilesState = new ArrayList<SipProfileState>();		
		Cursor c = getContentResolver().query(SipProfile.ACCOUNT_STATUS_URI, null, null, null, null);
		if (c != null) {
			try {
                while(c.moveToNext()){
                    SipProfileState ps = new SipProfileState(c);

                    // If is valid for call -> add to active profiles (reachable by call).
                    if(ps.isValidForCall()) {
                        activeProfilesState.add(ps);
                    }
				}
			} catch (Exception e) {
				Log.e(TAG, "Exception on iterating over accounts", e);
			} finally {
				c.close();
			}
		}
		
		Collections.sort(activeProfilesState, SipProfileState.getComparator());
		Log.vf(TAG, "updateRegistrationsState(), activeProfiles: %s", activeProfilesState.size());

		// Send registration intents to UI
		if (activeProfilesState.size() > 0) {
            for(SipProfileState s : activeProfilesState) {
                Intent publishIntent = new Intent(Intents.ACTION_SIP_REGISTRATION_CHANGED_REGISTERED);
                publishIntent.putExtra(SipProfile.FIELD_ID, s == null ? SipProfile.INVALID_ID : s.getAccountId());
                MiscUtils.sendBroadcast(this, publishIntent);
            }
		} else {
			Intent publishIntent = new Intent(Intents.ACTION_SIP_REGISTRATION_CHANGED_UNREGISTERED);
            MiscUtils.sendBroadcast(this, publishIntent);
		}
		
		// Status bar registration
		if (activeProfilesState.size() > 0) {
			notificationManager.notifyRegisteredAccounts(activeProfilesState, false);
		} else {
			Log.d(TAG, "Canceling registration in updateRegistration()");
			notificationManager.cancelRegisters();
		}

        // Message manager notification
        if (activeProfilesState.size() > 0){
            msgManager.onAccountLoggedIn();
        }
		
		if(hasSomeActiveAccount) {
			acquireResources();
			
			// Watchdog monitoring.
			if (activeProfilesState.size() > 0){
				stopWatchdog();
			} else {
				startWatchdog();
			}
		} else {
			Log.d(TAG, "UpdateRegistrations() - no active accounts listed, release resources");
			releaseResources();
			stopWatchdog();
		}
	}

    /**
     * Called when swiping away the app (closing) after long-press of the main button
     * @param rootIntent
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.df(TAG, "onTaskRemoved");
        taskRemovedCleanup();
    }

    private void taskRemovedCleanup() {
        Log.d(TAG, "Cleanup started; ");
        ProfileUtils.secureQuit(this, false);

//        // TODO turning temporarily off, check out consequences
////        prefsWrapper.setBoolean(PhonexConfig.APP_CLOSED_BY_USER, true);
//
//        try {
//            // wait a while until APP_CLOSED_BY_USER settings is stored
//            Thread.sleep(100);
//        } catch (InterruptedException e) {
//            Log.wf(TAG,e, "onTaskRemoved;");
//        }
        cleanStop();
    }

    /**
     * check if at least some account is registered
     * 
     * @return
     */
    private boolean checkRegistrationState() {
		ArrayList<SipProfileState> activeProfilesState = new ArrayList<SipProfileState>();
		Cursor c = getContentResolver().query(SipProfile.ACCOUNT_STATUS_URI, null, null, null, null);
		if (c != null) {
			try {
                while(c.moveToNext()){
                    SipProfileState ps = new SipProfileState(c);
                    if(ps.isValidForCall()) {
                        // check if account has setup password
                        SipProfile account = SipProfile.getProfileFromDbId(this.getApplicationContext(), ps.getAccountId(), SipProfile.FULL_PROJECTION);
                        if (account == null) continue;

                        String pwd = account.getPassword();
                        if (pwd != null && !TextUtils.isEmpty(pwd)){
                            activeProfilesState.add(ps);
                        }
                    }
                }
			} catch (Exception e) {
				Log.e(TAG, "Error on looping over sip profiles", e);
			} finally {
				c.close();				
			}
		}

		// is profile registered?
		if (activeProfilesState.size() > 0) {
            return true;
        } else {
            return false;
        }
	}

	public void startWatchdog() {
		try {
			pollingInterval = getPrefs().getInteger(PhonexConfig.NETWORK_WATCHDOG);
		} catch(Exception e){
			Log.i(TAG, "Cannot determine polling interval");
			pollingInterval = 0;
		}

        // aren't we running some timer already?
    	if (restartTimer!=null){
    		return;
    	}
    		
        Log.df(TAG, "Start watchdog, interval %s s", pollingInterval);
        if(pollingInterval > 0) {
            restartTimer = new Timer("WatchdogMonitor", true);
            restartTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                        Log.d(TAG, "Watchdog run() started");
                        // Test if accounts are still not registered
                        boolean registered = checkRegistrationState();
                        if (!hasSomeActiveAccount || registered){
                        	return;
                        }
                        
                        int ln = 0;
                        if (storagePass!=null) ln = storagePass.length(); 
                        Log.i(TAG, "Watchdog activated, accont not registered for some amount of time...; len(pass) = " + ln);
                        
                        // Run the handler in XServiceHandler to be protected by wake lock
                        getHandler().execute(new SvcRunnable("restartSipStack") {
                            public void doRun() throws SameThreadException {
                                Log.d(TAG, "Going to restart SIP stack by watchdog timer");
                                restartSipStack();
                            }
                        });
                }
            }, (pollingInterval + 240) * 1000, (pollingInterval + 240) * 1000);
        }
    }
    
    public void stopWatchdog() {
    	Log.d(TAG, "Stop watchdog");
        if(restartTimer != null) {
        	try {
        		restartTimer.cancel();
        		restartTimer.purge();
        		restartTimer = null;
        	} catch(Exception ex){
        		Log.e(TAG, "Cannot stop restart timer", ex);
        	}
        }
    }
	
	/**
	 * Get the currently instanciated prefsWrapper (to be used by
	 * PjCallback)
	 * 
	 * @return the preferenceWrapper instanciated
	 */
	public PreferencesConnector getPrefs() {
		// Is never null when call so ok, just not check...
		return prefsWrapper;
	}
	
	//Binders for media manager to sip stack
	/**
	 * Adjust tx software sound level
	 * @param speakVolume volume 0.0 - 1.0
	 */
	public void confAdjustTxLevel(float speakVolume) throws SameThreadException {
		if(pjManager != null) {
			pjManager.confAdjustTxLevel(0, speakVolume);
		}
	}
	/**
	 * Adjust rx software sound level
	 * @param speakVolume volume 0.0 - 1.0
	 */
	public void confAdjustRxLevel(float speakVolume) throws SameThreadException {
		if(pjManager != null) {
			pjManager.confAdjustRxLevel(0, speakVolume);
		}
	}

	/**
	 * Ask to take the control of the wifi and the partial wake lock if
	 * configured
	 */
    private synchronized void acquireResources() {
        if(holdResources) {
            return;
        }

        // Add a wake lock for CPU if necessary
        Log.v(TAG, "acquireResources()");
        if (prefsWrapper.getBoolean(PhonexConfig.USE_PARTIAL_WAKE_LOCK)) {
            PowerManager pman = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (wakeLock == null) {
                wakeLock = pman.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "net.phonex.XService");
                wakeLock.setReferenceCounted(false);
            }
            // Extra check if set reference counted is false ???
            if (!wakeLock.isHeld()) {
                Log.d(TAG, "Acquiring wake lock");
                wakeLock.acquire();
            }
        }

        // Add a lock for WIFI if necessary
        WifiManager wman = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (wifiLock == null) {
            int mode = WifiManager.WIFI_MODE_FULL;
            if(prefsWrapper.getBoolean(PhonexConfig.LOCK_WIFI_FULL_HIGH_PERF)) {
                mode = 0x3; // WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wman.createWifiLock(mode, "net.phonex.XService.wifi");
            wifiLock.setReferenceCounted(false);
            Log.df(TAG, "WiFi lock already held: %s", wifiLock.isHeld());
        }

        // Lock wifi if configured.
        if (prefsWrapper.getBoolean(PhonexConfig.LOCK_WIFI) && !wifiLock.isHeld()) {
            WifiInfo winfo = wman.getConnectionInfo();
            if (winfo != null) {
                DetailedState dstate = WifiInfo.getDetailedStateOf(winfo.getSupplicantState());
                if (dstate == DetailedState.OBTAINING_IPADDR || dstate == DetailedState.CONNECTED) {
                    if (!wifiLock.isHeld()) {
                        Log.d(TAG, "Acquiring WiFi lock");
                        wifiLock.acquire();
                    }
                }
            }
        }

        holdResources = true;
    }

	private synchronized void releaseResources() {
		if (wakeLock != null && wakeLock.isHeld()) {
			Log.d(TAG, "Releasing WakeLock");
			wakeLock.release();
		}
		if (wifiLock != null && wifiLock.isHeld()) {
			Log.d(TAG, "Releasing WiFiLock");
			wifiLock.release();
		}
		holdResources = false;
	}

	public PjCallback getPjCallback() {
		return pjManager.getPjCallback();
	}

	public int getGSMCallState() {
		return telephonyManager == null ? TelephonyManager.CALL_STATE_IDLE : telephonyManager.getCallState();
	}

    public void onGSMStateChanged(int state, String incomingNumber) {
        // Update on-call presence for all active accounts.
        try {
            publishPresenceForAllAccounts();
        } catch(Exception e){
            Log.e(TAG, "Exception: XMPPmanager error", e);
        }
    }

	public static final class ToCall {
		private Integer pjsipAccountId;
		private String callee;
		
		public ToCall(Integer acc, String uri) {
			pjsipAccountId = acc;
			callee = uri;
		}

		/**
		 * @return the pjsipAccountId
		 */
		public Integer getPjsipAccountId() {
			return pjsipAccountId;
		}
		/**
		 * @return the callee
		 */
		public String getCallee() {
			return callee;
		}
	};
	
	public SipProfile getAccount(long accountId) {
		// TODO : create cache at this point to not requery each time as far as it's a service query
		return SipProfile.getProfileFromDbId(this, accountId, SipProfile.FULL_PROJECTION);
	}
	
	public SipProfile getAccount(final String sip, boolean onlyActive) {
		// TODO : create cache at this point to not requery each time as far as it's a service query
		return SipProfile.getProfileFromDbName(this, sip, onlyActive, SipProfile.FULL_PROJECTION);
	}

	/**
	 * Should a current incoming call be automatically rejected?
	 * Decision is based on parsing the contact and matching the filter corresponding to the given account.
	 * 
	 * @param remContact The remote contact to test
	 * @param acc The incoming guessed account
	 * @return the sip code to auto-answer with. If > 0 it means that an auto hangup must be fired
	 */
	public int shouldAutoReject(String remContact, SipProfile acc, Bundle extraHdr) {

		Log.df(TAG, "Search if I should auto-reject for [%s]", remContact);
		int shouldAutoHangup = 200;

        if (remContact == null){
            return 0;
        }

		if(!autoHangupCurrent) {
			Log.d(TAG, "Auto hangup this");
			autoHangupCurrent = true;
			return 0;
		}
		
		if(acc != null) {
			Pattern p = Pattern.compile("^(?:\")?([^<\"]*)(?:\")?[ ]*(?:<)?sip(?:s)?:([^@]*@[^>]*)(?:>)?", Pattern.CASE_INSENSITIVE);
			Matcher m = p.matcher(remContact);
			String number = remContact;
			if (m.matches()) {
				number = m.group(2);
			}
			final int isAnswerable = CallFirewall.isAnswerable(this, acc.getId(), number, extraHdr);
			shouldAutoHangup = isAnswerable >= 200 ? 0 : 200;
		}else {
			Log.e(TAG, "Account is null");
			// If some user need to auto hangup if comes from unknown account, just needed to add local account and filter on it.
		}
		return shouldAutoHangup;
	}

	public void setNoSnd() throws SameThreadException {
		if (pjManager != null) {
			pjManager.setNoSnd();
		}
	}
	
	public void setSnd() throws SameThreadException {
		if (pjManager != null) {
			pjManager.setSnd();
		}
	}
	
    public static Looper createLooper() {
        if(handlerThread == null) {
            Log.d(TAG, "New handler thread");
            // ADT gives a fake warning due to bad parse rule.
            handlerThread = new HandlerThread("Svc.Handler");
            handlerThread.start();
        }
        return handlerThread.getLooper();
    }

    public AppWakeLock getWakeLock(){
        return appWakeLock;
    }

    static boolean detectDebuggerTime(){
    	long start = Debug.threadCpuTimeNanos();
    	if (start == -1) return false;
    	
    	for(int i=0; i<1000000l; ++i) continue;
    	long stop = Debug.threadCpuTimeNanos();
    	
    	return !((stop-start) < 10000000l);
    }
	
    class StartRunnable extends SvcRunnable {
        StartRunnable() {
            super("StackStart");
        }

        @Override
		protected void doRun() throws SameThreadException {
    		startSipStack();
    	}
    }
	
	class DestroyRunnable extends SvcRunnable {
        DestroyRunnable() {
            super("StackDestroy");
        }

		@Override
		protected void doRun() throws SameThreadException {
			if(stopSipStack()) {
				Log.d(TAG, "Going to stop this service; calling stopSelf()");
				XService.this.stopSelf();
			} else {
				Log.i(TAG, "Stopping SIP stack was not successfull");
			}
		}
	}
	
	class FinalizeDestroyRunnable extends SvcRunnable {
        FinalizeDestroyRunnable() {
            super("StackDestroy2");
        }

		@Override
		protected void doRun() throws SameThreadException {
			
			mExecutor = null;
			
			Log.d(TAG, "Destroy sip stack");
			
			appWakeLock.reset();
			
			if(stopSipStack()) {
				notificationManager.cancelAll();
				notificationManager.cancelCalls();
			}else {
				Log.e(TAG, "Somebody has stopped the service while there is an ongoing call !!!");
			}
			
			// Stops alarm handler.
            if (alarmExecutorThread != null){
                synchronized(alarmExecutorThread){
                    HandlerThread cht = alarmExecutorThread;
                    alarmExecutorThread = null;
                    MiscUtils.stopHandlerThread(cht, false);
                }
            }

            // Intent to stop SafeNet
            Intent intent = new Intent(Intents.ACTION_QUIT_SAFENET);
            MiscUtils.sendBroadcast(XService.this, intent);

            // App should be almost killed at this moment, set app closing to false (if it fails to be reset by deleting memory preferences)
            MemoryPrefManager.setPreferenceBooleanValue(getApplication(), MemoryPrefManager.APP_CLOSING, false);

			// We will not go longer
			Log.i(TAG, "--- SIP SERVICE DESTROYED ---");
			sStopped = true;
			onStartCalled = false;
			
			XService.this.stopSelf();
			
			//
			// Try to force kill of the app - in case of troubles with library.
			//
            ProcKiller.killCurrentProcess();
			Log.v(TAG, "Probably dead right now?");
		}
	}
	
	// Enforce same thread contract to ensure we do not call from somewhere else
	public class SameThreadException extends Exception {
		private static final long serialVersionUID = -905639124232613768L;

		public SameThreadException() {
			super("Should be launched from a single worker thread");
		}
	}

    /**
     * Sends SIGKILL to this service.
     * Has to be executed from the service process.
     */
    public void sigkillService(){
        ProcKiller.killCurrentProcess();
    }
    
    /**
     * Tries to send SIGKILL to the running service from the outside - using 
     * ps to get process ID and then use kill.
     */
    public static boolean sigKillServiceExt(){
		ProcKiller.killProcess("net.phonex:service");
		return false;
    }

    /**
     * Returns explicit intent for the Service
     * @param ctxt
     * @return
     */
    public static Intent getStartServiceIntent(Context ctxt){
        return buildIntent(ctxt, Constants.INTENT_XSERVICE, XService.class);
    }

    /**
     * Builds intent for a given action.
     * Specifiec package name, restricted to the application package.
     * @param ctxt
     * @param action
     * @return
     */
    public static Intent buildIntent(Context ctxt, String action){
        final Intent intent = new Intent(action);
        intent.setPackage(ctxt.getPackageName());
        return intent;
    }

    /**
     * Builds intent for a given action.
     * Specifiec package name, restricted to the application package.
     * Builds explicit intent for the particular class.
     *
     * @param ctxt
     * @param action
     * @param destination
     * @return
     */
    public static Intent buildIntent(Context ctxt, String action, Class<?> destination){
        final Intent intent = new Intent(action);
        intent.setPackage(ctxt.getPackageName());
        intent.setClass(ctxt, destination);
        return intent;
    }

    /**
     * Builds intent for starting a call from given call session information.
     * @param ctxt
     * @param callInfo
     * @return
     */
    public static Intent buildCallUiIntent(Context ctxt, SipCallSessionInfo callInfo) {
        // Resolve the package to handle call.
        if(UI_CALL_PACKAGE == null) {
            UI_CALL_PACKAGE = ctxt.getPackageName();
        }
        
        Intent intent = new Intent(Intents.ACTION_SIP_CALL_UI);
        intent.putExtra(Intents.EXTRA_CALL_INFO, callInfo);
        intent.setPackage(UI_CALL_PACKAGE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    public static void setVideoWindow(int callId, SurfaceView window, boolean local) {
        if(singleton != null) {
            if(local) {
                singleton.setCaptureVideoWindow(window);
            }else {
                singleton.setRenderVideoWindow(callId, window);
            }
        }
    }

    /**
     * Returns true if the callee can be reached by the accountId. 
     * @param callee
     * @param accountId
     * @return
     * @throws SameThreadException
     */
    protected boolean isAccountSipValid(final String callee, final int accountId) throws SameThreadException {
        return (pjManager.isCreated() && pjManager.sanitizeSipUri(callee, accountId) != null);
    }

    /**
     * Event on receiving call intent.
     * Starts call on desired directly. Checks request validity, i.g., non-empty destination, ...
//     * @param intent
     */
//    private boolean onCallIntentReceived(Intent intent){
//        final String action = intent.getAction();
//        boolean valid=false;
//
//        Log.vf(TAG, "Call intent received, action=%s", action);
//        try {
//            String callDestination = UriUtils.extractContactFromIntent(intent, this);
//            if (TextUtils.isEmpty(callDestination) && intent.hasExtra(Intents.EXTRA_CALL_DESTINATION)) {
//                callDestination = intent.getStringExtra(Intents.EXTRA_CALL_DESTINATION);
//            }
//
//            // If call destination is empty, call cannot be performed.
//            if (TextUtils.isEmpty(callDestination)) {
//                Log.w(TAG, "Call destination is empty.");
//                notifyUserOfCustomMessage(getString(R.string.call_error_generic));
//                return false;
//            }
//
//            final long callAccount = intent.getLongExtra(SipProfile.FIELD_ACC_ID, SipProfile.INVALID_ID);
//
//            // is valid for call?
//            if (!isConnectionValid()
//                    || !isConnectivityValid()
//                    || pjManager ==null
//                    || !pjManager.isCreated()
//                    || !isAccountSipValid(callDestination, (int) callAccount))
//            {
//                Log.wf(TAG, "Cannot call [%s] from account [%s], not connected", callDestination, callAccount);
//                notifyUserOfCustomMessage(getString(R.string.call_error_not_valid));
//                return false;
//            }
//
//            Log.vf(TAG, "ToCall [%s] accountId: [%s]", callDestination, callAccount);
//            try {
//                getBinder().makeCall(callDestination, (int) callAccount);
//                Log.v(TAG, "Call started");
//            } catch(Exception e){
//                Log.e(TAG, "Cannot make a call", e);
//            }
//
//            valid=true;
//        } catch(Exception e){
//            Log.e(TAG, "Exception in making a call", e);
//        }
//
//        if (!valid){
//            Log.e(TAG, "Generic error in making a call");
//            notifyUserOfCustomMessage(getString(R.string.call_error_generic));
//        }
//
//        return valid;
//    }

    private void setRenderVideoWindow(final int callId, final SurfaceView window) {
        getHandler().execute(new SvcRunnable("setVideoRender") {
            @Override
            protected void doRun() throws SameThreadException {
                pjManager.setVideoAndroidRenderer(callId, window);
            }
        });
    }
   
    private void setCaptureVideoWindow(final SurfaceView window) {
        getHandler().execute(new SvcRunnable("setVideoWindow") {
            @Override
            protected void doRun() throws SameThreadException {
                pjManager.setVideoAndroidCapturer(window);
            }
        });
    }

	public String getStoragePass() {
        Log.df(TAG, "Retrieving Storage password [%s]", storagePass);
		return storagePass;
	}

	public void setStoragePass(String storagePass) {
		Log.df(TAG, "Storage password was set right now [%s]", storagePass);
		this.storagePass = storagePass;
	}
	
	/**
	 * If service crashed, there is no StoragePassword.
	 * This method tries to load StoragePassword from user database.
	 * 
	 * TODO: also very poor design, REFACTOR THIS!
	 */
	public void fixStoragePassMissing(){
        Log.vf(TAG, "fixStoragePassMissing");
		// if service has been quit -> nothing to do here
		if(prefsWrapper.getBoolean(PhonexConfig.APP_CLOSED_BY_USER, false)) {
	        return;
	    }
		
		// if storage password is here, nothing to do also
		String stPass = getStoragePass(); 
		if (stPass != null && !TextUtils.isEmpty(stPass)){
			return;
		}
		
		// Service was running && storagePass = empty -> load from SafeNet.
		final String snStoragePass = MemoryPrefManager.getPreferenceStringValue(this, MemoryPrefManager.CRED_STORAGE_PASS, null);
		if (snStoragePass != null && snStoragePass.length()>0){
			this.setStoragePass(snStoragePass);
			Log.i(TAG, "Storage password recovered from SafeNet.");
			
			// Database password was probably lost as well... Database needs to be reloaded
			try {
				final String usrSip = MemoryPrefManager.getPreferenceStringValue(this, MemoryPrefManager.CRED_USR_NAME, null);
				final String usrPass = MemoryPrefManager.getPreferenceStringValue(this, MemoryPrefManager.CRED_USR_PASS, null);
				final String dbKey = DatabaseHelper.formatDbPassword(usrSip, usrPass);
				
				// Set encryption password at first.
                DBProvider.closeDb(getContentResolver());
		    	DBProvider.setEncKey(getContentResolver(), dbKey, usrSip);
		    	// Check if it is correct.
		    	int checkResult = DBProvider.testEncKey(getContentResolver(), false);
				Log.vf(TAG, "Check DB key result: %s", checkResult);
				
				// Simulate login event.
				onLoginSuccessful(true);
			} catch(Exception e){
				Log.e(TAG, "Exception in recovering DB from SafeNet.", e);
			}
		} else {
            Log.inf(TAG, "No storage password recovered from SafeNet");
        }
	}
	
	/**
	 * Called after login to application was successful.
	 */

	public void onLoginSuccessful(boolean sendIntent){
        Log.vf(TAG, "Login successful event received, sendIntent=%s", sendIntent);
        // Part 1 - on login, SIP stack is restarted (or started if not started yet).
        try {
            startSipStack();
        } catch(Exception e){
            Log.e(TAG, "Problem during SIP stack restart", e);
        }
		
		// Signalize login finished event to the XMPPManager.
		// It can switch its internal state and for example start
		// publishing presence updates to the database (this was
		// not possible before, because contact list was not
		// fetched).
		xmppManager.onLoginFinished();
        notificationManager.onLoginFinished();
        messageSecurityManager.onLoginFinished();
        msgManager.onLoginFinished();

        licenseExpirationCheckManager.makeExpirationCheck(true);

        // Part 1 - on login, SIP stack is restarted (or started if not started yet).
        try {
            restartSipStack();
        } catch(Exception e){
            Log.e(TAG, "Problem during SIP stack restart", e);
        }
		
		// Presence stuff.
		try {
			// Publish presence update for all active accounts
			publishPresenceForAllAccounts();
		} catch(Exception e){
			Log.e(TAG, "Exception: XMPPmanager error", e);
		}

        if (sendIntent){
            Intent intent = new Intent(Intents.ACTION_CHECK_LOGIN);
            MiscUtils.sendBroadcast(this, intent);
        }

	}
	
	/**
	 * Called on registration changed event from dynamic receiver.
	 * 
	 * @param account
	 * @throws SameThreadException
	 */
	public void registrationChanged(SipProfile account) throws SameThreadException {
		if (account==null){
			return;
		}
		
		Log.d(TAG, "Enqueue account status change publishing.");
        publishPresence(account);
	}
	
    /**
     * Publishes current XMPP presence information (stored in SipProfileState)
     * if such publishing is enabled.
     * 
     * @throws SameThreadException 
     */
    public boolean publishPresence(SipProfile account) throws SameThreadException{
        SipProfileState profileState = getSipProfileState((int) account.getId());

        try {
            // GSM in-call hack.
            if (profileState != null) {
                profileState.setInGsmCall(getGSMCallState() != TelephonyManager.CALL_STATE_IDLE);
            } else {
                Log.w(TAG, "Profile state is null");
            }

            String protobufStatus = myPresenceManager.generatePresenceText(account, profileState);

            // Use XMPP available presence status in all cases - true
        	xmppManager.setPresence(account.getXmpp_user(), true, protobufStatus, true);
        } catch(Exception ex){
        	Log.e(TAG, "Cannot publish presence, not connected", ex);
        }
        
        // Has to be added into stack
        if (profileState==null || !profileState.isAddedToStack()){
        	Log.vf(TAG, "User is not added to stack: %s", account.getDisplay_name());
        	return false;
        }
        
        Log.v(TAG, String.format("New presence information published for user: %s; engineId=%d; dbId=%d;", account.getDisplay_name(), profileState.getPjsuaId(), account.getId()));
        return true;
    }

    public class AccountStatusContentObserver extends ContentObserver {
        public AccountStatusContentObserver(Handler h) {
            super(h);
        }

        public void onChange(boolean selfChange) {
            Log.df(TAG, "AccountStatusContentObserver.onChange(selfChange=%s)", selfChange);
            updateRegistrationsState();
        }
    }

    /**
	 * Stores all scheduled alarm entries.
	 */
	private Map<String, Object> alarmScheduleEntries = new ConcurrentHashMap<>();
	
	/**
	 * Current DH key task runnable, keeps original task.
	 * Used for cancellation.
	 */
	protected volatile DhKeyTaskRunnable dhKeyTaskRunnable = null;
	
	/**
	 * Interface for updating progress of DH key generation.
	 * @author ph4r05
	 *
	 */
	public interface KeyGenProgressUpdatable{
		void updateDHProgress(KeyGenProgress progress);
		void updateDHProgress(List<KeyGenProgress> progress);
	}
	
	public Map<String, KeyGenProgress> progressMap = new ConcurrentHashMap<String, KeyGenProgress>();
	
	/**
     * Returns recent progress for certificate update.
     * @return
     */
    public List<KeyGenProgress> getDHKeyProgress(){
    	List<KeyGenProgress> lst = new LinkedList<>();
    	for(Iterator<Map.Entry<String, KeyGenProgress>> it = progressMap.entrySet().iterator(); it.hasNext(); ){
    		Map.Entry<String, KeyGenProgress> e = it.next();
    		
    		final KeyGenProgress le = e.getValue();
    		
    		lst.add(le);
    	}
    	
    	return lst;
    }

    /**
     * Broadcasts intent to trigger DH keys update.
     * @param ctxt
     * @param now - if true, resync will start immediately, otherwise there is 3 second pause
     *            in order to optimize consecutive call of this method and group invocations.
     */
    public static void triggerDHKeyUpdate(Context ctxt, boolean now){
        Intent i = new Intent(now ? Intents.ACTION_TRIGGER_DHKEY_SYNC_NOW : Intents.ACTION_TRIGGER_DHKEY_SYNC);
        MiscUtils.sendBroadcast(ctxt, i);
    }

    /**
     * Broadcasts intent to trigger DH keys update.
     * @param ctxt
     */
    public static void triggerDHKeyUpdate(Context ctxt){
        triggerDHKeyUpdate(ctxt, false);
    }
	
	/**
	 * Triggers DH key update task in a given timeout.
	 * Uses alarm, cancels currently running alarm.
	 * 
	 * If given timeout is negative, default 5000 millisecond timeout is chosen.
	 * 
	 * @param timeout
	 * @throws SameThreadException
	 */
	public void triggerDHKeyUpdate(long timeout) throws SameThreadException {
		final long intentTimeout = timeout < 0 ? 5000 : timeout;
		setAlarm(EXTRA_ALARM_ACTION_DHKEY, intentTimeout);
        Log.df(TAG, "DHKey action planned for timeout: %s", intentTimeout);

    }

    public void triggerLoginStateSave() {
        autoLoginManager.scheduleAlarm(10000);
    }

	/**
	 * Returns whether there is DHKeyUpdate scheduled.
	 * @return
	 */
	public boolean isDhKeyUpdateScheduled(){
		return alarmScheduleEntries.containsKey(EXTRA_ALARM_ACTION_DHKEY);
	}
	
	/**
	 * Sets alarm for a given action in a given timeout.
	 * 
	 * @param action
	 * @param timeout
	 */
    public void setAlarm(String action, long timeout){
        setAlarm(action, timeout, null);
    }
	public void setAlarm(String action, long timeout, Map<String, String> extras){
		final PendingIntent intent = getPendingIntentForAlarm(action, extras);
		
		alarmScheduleEntries.put(action, new Object());
		alarmManager.set(
				AlarmManager.ELAPSED_REALTIME_WAKEUP,
		        SystemClock.elapsedRealtime() + timeout, 
		        intent);
	}
		
	/**
	 * Creates pendingIntents for alarm with given action.
	 * 
	 * @param action
	 * @return
	 */
	private PendingIntent getPendingIntentForAlarm(String action, Map<String, String> extras) {
		Intent intent = new Intent(ALARM_ACTION);
		String toSend = EXTRA_ALARM_SCHEME + "://" + action;
		intent.setData(Uri.parse(toSend));
		intent.putExtra(EXTRA_ALARM_ACTION, action);
        if (extras!=null){
            for (String key : extras.keySet()){
                intent.putExtra(key, extras.get(key));
            }
        }
		
		return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
	}
	
	/**
	 * Registers broadcasts for alarm
	 */
	private void registerAlarmBroadcasts() {
        Log.vf(TAG, "registerAlarmBroadcasts");
	    if(alarmReceiver == null) {
	    	alarmReceiver = new InternalAlarmReceiver(this);
	    	
	    	// Intent filter for alarm actions.
	    	IntentFilter filter = new IntentFilter(ALARM_ACTION);
    		filter.addDataScheme(EXTRA_ALARM_SCHEME);
    		
            registerReceiver(alarmReceiver, filter);
	   }
	}
	
	/**
	 * Unregisters broadcast for alarm.
	 */
	private void unregisterAlarmBroadcasts() {
        Log.vf(TAG, "unregisterAlarmBroadcasts");
	    if(alarmReceiver != null) {
	        unregisterReceiver(alarmReceiver);
	        alarmReceiver = null;
	    }
	}
	
	/**
	 * Broadcast receiver registered for alarm actions.
	 * Handles fired alarm and takes an action w.r.t. alarm action.
	 * 
	 * @author ph4r05
	 */
	private class InternalAlarmReceiver extends BroadcastReceiver {
        private final WeakReference<XService> sr;

        private InternalAlarmReceiver(XService sr) {
            this.sr = new WeakReference<XService>(sr);
        }

        @Override
        public void onReceive(Context ctxt, Intent intent) {
            final XService svc = sr.get();
            if (svc == null) {
                Log.w(TAG, "AlarmReceiver: Service is null");
                return;
            }

            final String action = intent.getAction();
            if (!ALARM_ACTION.equals(action)) {
                Log.wf(TAG, "Unrecognized alarm intent action: %s", action);
                return;
            }

            // If-else statements based on extra alarm action.
            final String alarmAction = intent.getStringExtra(EXTRA_ALARM_ACTION);
            alarmScheduleEntries.remove(alarmAction);

            Log.inf(TAG, "InternalAlarmReceiver, receiving alarmAction=[%s]", alarmAction);

            if (EXTRA_ALARM_ACTION_DHKEY.equals(alarmAction)) {
                // Trigger DH key task.
                // Alarm executor executes in a different thread that SIP.
                if (dhKeyTaskRunnable != null) {
                    Log.w(TAG, "DHkeyTaskRunnable is not null...");
                }

                dhKeyTaskRunnable = new DhKeyTaskRunnable(svc);
                getAlarmHandler().execute(dhKeyTaskRunnable);
                Log.d(TAG, "DHKey action posted for execution");
            } else if (Intents.ALARM_MESSAGE_RESEND.equals(alarmAction)) {
                MessageManager mm = getMsgManager();
                if (mm == null) {
                    Log.e(TAG, "Message manager is null, cannot execute message backoff");
                    return;
                }

                String toSip = intent.getStringExtra(MessageManager.EXTRA_TO);
                mm.triggerAlarmCheck(toSip);

            } else if (Intents.ALARM_MESSAGE_EXPIRATION_CHECK.equals(alarmAction)) {
                MessageSecurityManager msm = getMessageSecurityManager();
                if (msm == null){
                    Log.e(TAG, "MessageSecurityManager is null, cannot check message expiration state");
                    return;
                }
                msm.onExpirationCheckAlarm();
            } else if (Intents.ALARM_LICENSE_EXPIRATION_CHECK.equals(alarmAction)) {
                if (licenseExpirationCheckManager != null){
                    licenseExpirationCheckManager.makeExpirationCheck(false);
                } else {
                    Log.e(TAG, "LicenseExpirationCheckManager is null, cannot check expiration state");
                }
            } else if (Intents.ALARM_AUTO_LOGIN_SAVE.equals(alarmAction)) {
                if (autoLoginManager != null) {
                    autoLoginManager.triggerAuthStateSave();
                } else {
                    Log.e(TAG, "AutoLoginManager is null, cannot save authentication state");
                }
            } else {
                Log.wf(TAG, "Unrecognized alarm action: %s", alarmAction);
            }
        }
	}
	
	/**
	 * Returns alarmExecutor (handler), creates a new one if does not exist.
	 * 
	 * Possible extension: If alarm tasks are very long running, 
	 * they may block execution of another tasks triggered by alarm.
	 * In this situation we may want to have more different threads 
	 * working in parallel. If needed, change Handler pattern to
	 * ScheduledThreadPoolExecutor. It enables to have multiple 
	 * working threads, canceling of the tasks and etc...
	 * 
	 * @return
	 */
	public AlarmExecutor getAlarmHandler() {
        // create mExecutor lazily
        if (alarmExecutor == null) {
        	alarmExecutor = new AlarmExecutor(this);
        }
        return alarmExecutor;
	}
	
	/**
	 * Creates thread for an Alarm handler if does not exist.
	 * @return
	 */
    protected static Looper createAlarmLooper() {
    	if(alarmExecutorThread == null) {
    		Log.d(TAG, "Creating new alarm handler thread");
    		alarmExecutorThread = new HandlerThread("Svc.AlarmHandler");
    		alarmExecutorThread.start();
    	}
    	
        return alarmExecutorThread.getLooper();
    }
	
	/**
	 * Executes immediate tasks in a single alarmExecutorThread.
	 * Hold/release wake lock for running tasks
	 * 
	 * @author ph4r05
	 */
    private static class AlarmExecutor extends Handler {
        WeakReference<XService> handlerService;
        
        public AlarmExecutor(XService s) {
            super(createAlarmLooper());
            handlerService = new WeakReference<>(s);
        }

        public void execute(Runnable task) {
            XService s = handlerService.get();
            if(s != null) {
                s.appWakeLock.lock(task);
            }
            Message.obtain(this, 0/* don't care */, task).sendToTarget();
        }

        @Override
        public void handleMessage(Message msg) {
	        if (msg.obj instanceof Runnable) {
                executeInternal((Runnable) msg.obj);
            } else {
                Log.wf(TAG, "can't handle msg: %s", msg);
            }
        }

        private void executeInternal(Runnable task) {
            try {
                task.run();                
            } catch (Throwable t) {
                Log.ef(TAG, t, "run task: %s", task);
            } finally {
                XService s = handlerService.get();
                if(s != null) {
                    s.appWakeLock.unlock(task);
                }
            }
        }
    }

    /**
     * send file to to, update progress of SipMessage (having sipMessageId) in MessageFragment
     * Assuming all files exists (this should have been checked before)
     * @param remoteContact
     * @param fileUris formatted according to FileStorageUri
     */
    protected void sendFiles(String remoteContact, final List<String> fileUris, String message) {
        msgManager.sendFiles(remoteContact, fileUris, message);
	}
	
	/**
	 * Add file to download.
	 * 
	 * @param sipMessageId
	 * @param destinationDirectory If is null, Android download directory is used.
	 */
	protected void downloadFile(long sipMessageId, String destinationDirectory, boolean deleteOnly){
		msgManager.downloadFile(sipMessageId, destinationDirectory, deleteOnly);
	}
    
    /**
     * Broadcasts intent to trigger account update.
     * @param ctxt
     * @param params
     */
    public static void triggerCertUpdate(Context ctxt, CertUpdateParams params){
    	final ArrayList<CertUpdateParams> lst = new ArrayList<CertUpdateParams>();
    	lst.add(params);
    	
    	triggerCertUpdate(ctxt, lst, false);
    }
    
    /**
     * Broadcasts intent to trigger account update.
     * @param ctxt
     * @param params
     */
    public static void triggerCertUpdate(Context ctxt, ArrayList<CertUpdateParams> params, boolean allUsers){
        Log.vf(TAG, "triggering certificate update");
    	Intent certIntent = new Intent(Intents.ACTION_CERT_UPDATE);
		certIntent.putExtra(Intents.CERT_INTENT_PARAMS, params);
		certIntent.putExtra(Intents.CERT_INTENT_ALLUSERS, allUsers);
        MiscUtils.sendBroadcast(ctxt, certIntent);
    }   
    
    /**
     * Function for certificate update for a particular user.
     * Function decides whether to update certificate for user implementing
     * DoS avoidance policy.
     *  
     * @param paramsList
     */
    public void triggerCertUpdate(final List<CertUpdateParams> paramsList){
    	triggerCertUpdate(paramsList, false);
    }
    
    /**
     * Generates certificate check list for users in contactlist of a given sip profile.
     * If argument is null, all contacts are loaded. 
     * 
     * @param sipProfile
     * @param forceCheck
     * @return
     */
    private List<CertUpdateParams> certCheckAllAccounts(String sipProfile, boolean forceCheck) {
    	List<CertUpdateParams> ret = new ArrayList<CertUpdateParams>();
    	
		String selection=null;
		String selectionArgs[]=null;
		if (sipProfile!=null){
			selection = SipClist.FIELD_ACCOUNT + "=?";
			selectionArgs = new String[] {sipProfile};
		}
		
		Cursor c = getContentResolver().query(
				SipClist.CLIST_URI, 
				SipClist.LIGHT_PROJECTION,
				selection, 
				selectionArgs,null);
		
		if (c==null) return ret;
		try {
			while(c.moveToNext()){
				SipClist cl = new SipClist(c);
				ret.add(new CertUpdateParams(cl.getSip(), forceCheck));
			}
		} catch (Exception e) {
			Log.e(TAG, "Error on looping over sip profiles", e);
		} finally {
			c.close();
		}
		
		return ret;
	}

    public void triggerContactListFetch(){
        Log.vf(TAG, "triggerContactListFetch");

        // Start task if it is not running.
        // Schedules new task only if there is none scheduled or previous has finished.
        if (futureContactListFetch == null || futureContactListFetch.isDone()){

            SipProfile account = getAccount(SipProfile.USER_ID);
            ClistFetchParams clistFetchParameters = new ClistFetchParams();
            clistFetchParameters.setDbId(1);
            clistFetchParameters.setStoragePass(getStoragePass());
            clistFetchParameters.setSip(account.getSip());
            clistFetchParameters.setServiceURL(ServiceConstants.getServiceURL(account.getSipDomain(), true));
            clistFetchParameters.setClistTableUpdateStrategy(ClistFetchParams.UpdateStrategy.UPDATE);

            contactListFetchCall.setParameters(clistFetchParameters);
            contactListFetchCall.setContext(this.getApplicationContext());

            // Schedule a new task in a near future.
            futureContactListFetch = texecutor.schedule(contactListFetchCall, 500, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Trigger self certificate check to verify we have the newest one
     * If not, it means someone else logged using same credentials on different device, so this device should log off
     * @param certHashPrefix
     * @param pushedCertNotBefore
     */
    public void triggerCertificateSelfCheck(String certHashPrefix, long pushedCertNotBefore) {
        SipProfile account = getAccount(SipProfile.USER_ID);

        String certHash = account.getCert_hash();
        long certNotBefore = account.getCert_not_before().getTime();

        if (pushedCertNotBefore < certNotBefore){
            Log.inf(TAG, "certSelfCheck; Not checking. Pushed not_before is older than current cert, pushedCertNotBefore [%d], certNotBefore [%d]", pushedCertNotBefore,certNotBefore);
            return;
        } else if (pushedCertNotBefore == certNotBefore && certHash.startsWith(certHashPrefix)){
            Log.inf(TAG, "certSelfCheck; Not checking. Pushed not_before is the same as current cert not_before, hashPrefix is also OK.");
            return;
        }
        Log.inf(TAG, "certSelfCheck; not_before [%d] is newer than current cert not_before [%d]. Doing self-check", pushedCertNotBefore, certNotBefore);

        CertificateSelfCheckTask task = new CertificateSelfCheckTask(this, certHash, account.getSip());
        texecutor.schedule(task, 100, TimeUnit.MILLISECONDS);
    }

    /**
     * Trigger license check to retrieve new license information
     */
    public void triggerLicenseCheck() {
        Log.inf(TAG, "triggerLicenseCheck");
        LicenseCheckCall task = new LicenseCheckCall(this);
        texecutor.schedule(task, 100, TimeUnit.MILLISECONDS);
    }

    /**
     * For runnables that has to be re-executed
     * @param runnable
     */
    public void scheduleRunnableAfterConnectivityIsOn(Runnable runnable){
        rescheduledRunnables.add(runnable);
    }

    private void rescheduleRunnables(){
        Runnable r;
        while ((r = rescheduledRunnables.poll()) != null){
            texecutor.schedule(r, 100, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Function for certificate update for a particular user.
     * Function decides whether to update certificate for user implementing
     * DoS avoidance policy.
     *  
     * @param paramsList
     * @param allUsers
     */
    public void triggerCertUpdate(List<CertUpdateParams> paramsList, boolean allUsers){
    	if (paramsList==null){
    		throw new IllegalArgumentException("Params cannot be null.");
    	}
    	
    	if (paramsList.isEmpty()){
    		if(allUsers){
    			paramsList = certCheckAllAccounts(null, false); 
    		} else return;
    	}
    	
    	if (paramsList.isEmpty()){
    		return;
    	}
    	
    	// Add all params to the check list.
    	certUpdateTask.addToCheckList(paramsList);
    	
    	// Start task if it is not running.
    	// Wait some amount of time in order to group multiple users in one check (optimization).
    	// Schedules new task only if there is none scheduled or previous has finished.
    	if (futureCertUpdate == null || futureCertUpdate.isDone()){
    		// Set current context to the task.
    		certUpdateTask.setCtxt(this);
    		// Schedule a new task in a near future.
    		futureCertUpdate = texecutor.schedule(certUpdateTask, 3000, TimeUnit.MILLISECONDS);    		
    	}
    }

    /**
     * Returns progress of the certificate update.
     * @return
     */
    public List<CertUpdateProgress> getCertUpdateProgress(){
		if (certUpdateTask==null) return null;
		return XService.this.certUpdateTask.getCertUpdateProgress();
	}
		
	/**
	 * @return the notificationManager
	 */
	public StatusbarNotifications getNotificationManager() {
		return notificationManager;
	}



    public PermissionManager getPermissionManager() {
        return permissionManager;
    }

    public IService.Stub getBinder() {
		return binder;
	}

	public XmppManager getXmppManager() {
		return xmppManager;
	}

    public PairingRequestManager getPairingRequestManager() {
        return pairingRequestManager;
    }

    public LoginManager getLoginManager() {
        return loginManager;
    }

    public PjManager getPjManager() {
        return pjManager;
    }

    public MessageManager getMsgManager() {
        return msgManager;
    }

    public GcmManager getGcmManager() {
        return gcmManager;
    }

    public LicenseExpirationCheckManager getLicenseExpirationCheckManager() {
        return licenseExpirationCheckManager;
    }

    public LogSendingManager getLogSendingManager() {
        return logSendingManager;
    }

    public boolean isSupportMultipleCalls() {
        return supportMultipleCalls;
    }

    public FileTransferManager getFtManager() {
        return ftManager;
    }

    public ContactsManager getContactsManager() {
        return contactsManager;
	}

    public void decryptFile(String uri) {
        Log.d(TAG, "decryptFile(String uri) " + uri);
        fileDecryptManager.decryptFile(uri);
    }

    public void decryptFiles(List<String> uris) {
        Log.d(TAG, "decryptFiles(List<String> uris) " + uris);
        fileDecryptManager.decryptFiles(uris);
    }

    public void cancelDecrypt() {
        Log.d(TAG, "cancelDecrypt()");
        fileDecryptManager.cancelDecrypt();
    }

    public Boolean isTaskRunningOrPending() {
        return fileDecryptManager.taskRunningOrPending();
    }

    public AtomicBoolean getConnected() {
        return connected;
    }
}
package net.phonex.service;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.os.PowerManager;

import net.phonex.PhonexSettings;
import net.phonex.core.Constants;
import net.phonex.core.Intents;
import net.phonex.pref.PhonexConfig;
import net.phonex.pref.PreferencesConnector;
import net.phonex.service.runEngine.AppWakeLock;
import net.phonex.util.Log;
import net.phonex.util.LogMonitor;
import net.phonex.util.MiscUtils;
import net.phonex.util.system.ProcKiller;

import java.lang.ref.WeakReference;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Safe net service. Runs in a separate process.
 * Main purpose: monitor the main service and process. Watchdog.
 * In this process also runs in-memory encrypted preferences database.
 */
public class SafeNetService extends Service {
	private static final String TAG = "SafeNetService";
    private static int startCtr=0;

    /**
     * Log monitorint thread.
     */
    protected LogMonitor logMonitor = null;
    private PreferencesConnector prefs;
    private BcastListener bcastListener;
    private AppWakeLock wlock;
    private JobHandler handler;
    private static HandlerThread handlerThread;

    @Override
    public void onCreate() {
    	Log.vf(TAG, "Service started, CL=0x%x", this.getClassLoader().hashCode());
        prefs = new PreferencesConnector(this);
        wlock = new AppWakeLock((PowerManager) getSystemService(Context.POWER_SERVICE));
        bcastListener = new BcastListener();
        bcastListener.register(this);
        logMonitor = new LogMonitor();
        logMonitor.setContext(getApplicationContext());

        // If logging is selected to be enabled, do it so.
        final boolean logging = prefs.getBoolean(PhonexConfig.LOG_TO_FILE, false) && PhonexSettings.debuggingRelease();
        if (logging){
            Log.d(TAG, "Going to start a logcat monitor");
            getHandler().execute(new SvcRunnable() {
                @Override
                protected void doRun() throws XService.SameThreadException {
                    setLoggingEx(true);
                }
            });
        }

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.vf(TAG, "Received start id %s: %s, CL=0x%x; ctr=%s", startId, intent, this.getClassLoader().hashCode(), startCtr);
        startCtr+=1;
        
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
    	Log.v(TAG, "Service shutting down");
        super.onDestroy();

        if (bcastListener !=null){
            bcastListener.unregister(this);
        }

        if (logMonitor!=null) {
            logMonitor.stopLogging();
        }
    }

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}
	
	/**
	 * Starts service by call startService()
	 * @param ctxt
	 * @param async
	 */
	public static void start(final Context ctxt, boolean async){
        final Intent serviceIntent = XService.buildIntent(
                ctxt.getApplicationContext(),
                Constants.INTENT_SAFENET_SERVICE,
                SafeNetService.class);

		if (!async){
	        Log.v(TAG, "Going to start SafeNet service");
	        ctxt.getApplicationContext().startService(serviceIntent);
	        return;
		}
		
		Thread t = new Thread("StartSafeNet") {
            public void run() {
    	        Log.v(TAG, "Going to start SafeNet service");
    	        ctxt.getApplicationContext().startService(serviceIntent);
            };
        };
        t.start();
	}

    private void cleanStop(){
        stopSelf();
        //
        // Try to force kill of the app - in case of troubles with library.
        //
        ProcKiller.killCurrentProcess();
    }

    /**
     * Enables/disables logging.
     * Waits until logging / stopping loging finishes.
     * Thus should be executed in the handler thread.
     *
     * @param logging
     */
    private void setLoggingEx(boolean logging){
        if (logMonitor.isRunning() == logging || logMonitor.isAlive()==logging){
            Log.wf(TAG, "Logging state already set: %s", logging);
            return;
        }

        if (logging){
            Log.i(TAG, "Going to start logging thread.");
            logMonitor = new LogMonitor();
            logMonitor.setContext(getApplicationContext());
            logMonitor.setDaemon(true);
            logMonitor.setName("LogThread");
            logMonitor.start();

            // Wait 3 seconds...
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Waiting interrupted", e);
            }
        } else {
            Semaphore s = logMonitor.getShutdownSemaphore();
            logMonitor.stopLogging();

            // Wait for shutdownSemaphore.
            try {
                s.tryAcquire(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Log.e(TAG, "Waiting interrupted", e);
            }

            // Join logMonitor thread.
            try {
                logMonitor.join(2000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Waiting interrupted", e);
            }

            // Wait 3 seconds...
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Waiting interrupted", e);
            }
        }

        // Set on boot property
        prefs.setBoolean(PhonexConfig.LOG_TO_FILE, logging);
    }

    /**
     * Creates lazily a new handler thread.
     * @return
     */
    private static Looper createLooper() {
        if(handlerThread == null) {
            Log.d(TAG, "Creating new handler thread");
            // ADT gives a fake warning due to bad parse rule.
            handlerThread = new HandlerThread("SafeNet.handler");
            handlerThread.start();
        }
        return handlerThread.getLooper();
    }

    /**
     * Creates lazily a new job handler.
     * @return
     */
    public JobHandler getHandler() {
        // create handler lazily
        if (handler == null) {
            handler = new JobHandler(this);
        }
        return handler;
    }



    /**
     * Executes immediate tasks in a single handlerThread.
     * Hold/release wake lock for running tasks
     */
    public static class JobHandler extends Handler {
        WeakReference<SafeNetService> handlerService;

        JobHandler(SafeNetService s) {
            super(createLooper());
            handlerService = new WeakReference<SafeNetService>(s);
        }

        public void execute(Runnable task) {
            SafeNetService s = handlerService.get();
            if(s != null) {
                s.wlock.lock(task);
            }
            Log.v(TAG, "Schedule @ executor: " + task);
            Message.obtain(this, 0, task).sendToTarget();
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.obj instanceof Runnable) {
                Log.v(TAG, "<lge: " + msg.obj + ">");
                executeInternal((Runnable) msg.obj);
                Log.v(TAG, "</lge: " + msg.obj + ">");
            } else {
                Log.w(TAG, "can't handle msg: " + msg);
            }
        }

        private void executeInternal(Runnable task) {
            try {
                task.run();
            } catch (Throwable t) {
                Log.e(TAG, "run task: " + task, t);
            } finally {

                SafeNetService s = handlerService.get();
                if(s != null) {
                    s.wlock.unlock(task);
                } else {
                    Log.w(TAG, "No wakelock release, task=" + task);
                }
            }
        }
    }
    
    /**
     * Broadcast listener for logging events.
     */
    private class BcastListener extends BroadcastReceiver {
        private static final String TAG = "BcastListener";

        public void register(Context ctxt){
            try {
                IntentFilter intentfilter = new IntentFilter();
                intentfilter.addAction(Intents.ACTION_LOGGER_CHANGE);
                intentfilter.addAction(Intents.ACTION_FOREGROUND);
                intentfilter.addAction(Intents.ACTION_QUIT_SAFENET);
                MiscUtils.registerReceiver(ctxt, this, intentfilter);
                Log.v(TAG, "LogChangeRecv registered");
            } catch(Exception ex){
                Log.e(TAG, "Exception: Unable to register", ex);
            }
        }

        public void unregister(Context ctxt){
            try {
                ctxt.unregisterReceiver(this);
                Log.v(TAG, "LogChangeRecv unregistered");
            } catch(Exception ex){
                Log.e(TAG, "Exception: Unable to unregister", ex);
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String iaction = intent.getAction();
            if (Intents.ACTION_LOGGER_CHANGE.equals(iaction)){
                loggerAction(intent);
            } else if (Intents.ACTION_FOREGROUND.equals(iaction)){
                foregroundAction(intent);
            } else if (Intents.ACTION_QUIT_SAFENET.equals(iaction)){
                Log.vf(TAG, "Received ACTION_QUIT_SAFENET intent, going to stop.");
                cleanStop();
                Log.v(TAG, "Probably dead right now?");
            } else {
                Log.wf(TAG, "Unknown intent action %s", iaction);
            }
        }

        /**
         * Intent for logging action processing.
         * @param intent
         */
        private void loggerAction(Intent intent){
            if (intent.hasExtra(Intents.EXTRA_LOGGER_ENABLE)==false){
                Log.w(TAG, "Intent received, but invalid");
                return;
            }

            final boolean enable = intent.getBooleanExtra(Intents.EXTRA_LOGGER_ENABLE, false);
            Log.vf(TAG, "Intent received, logging=%s", enable);

            getHandler().execute(new SvcRunnable() {
                @Override
                protected void doRun() throws XService.SameThreadException {
                    setLoggingEx(enable);
                }
            });
        }

        /**
         * Intent for foreground setting.
         * @param intent
         */
        private void foregroundAction(Intent intent){
            if (intent.hasExtra(Intents.EXTRA_FOREGROUND_ENABLE)==false){
                Log.w(TAG, "Intent received, but invalid");
                return;
            }

            final boolean enable = intent.getBooleanExtra(Intents.EXTRA_FOREGROUND_ENABLE, false);
            if (!enable){
                Log.v(TAG, "Foreground stop");
                stopForeground(false);
                return;
            }

            if (intent.hasExtra(Intents.EXTRA_FOREGROUND_ID)==false || intent.hasExtra(Intents.EXTRA_FOREGROUND_NOTIF)==false){
                Log.w(TAG, "Intent received, but invalid");
                return;
            }

            try {
                final int id = intent.getIntExtra(Intents.EXTRA_FOREGROUND_ID, -1);
                final Parcelable parcelNotif = intent.getParcelableExtra(Intents.EXTRA_FOREGROUND_NOTIF);
                final Notification notif = (Notification) parcelNotif;

                Log.v(TAG, "Foreground start");
                startForeground(id, notif);
            } catch(Exception ex){
                Log.e(TAG, "Exception: cannot start foreground", ex);
            }
        }
    }

}

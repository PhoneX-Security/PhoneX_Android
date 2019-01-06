/**
 * Copyright (C) 2010-2012 Ph4r05
 * This file is part of PhoneX
 *
 * @author Ph4r05
 */

package net.phonex.service.runEngine;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;

import net.phonex.service.SvcRunnable;
import net.phonex.service.XService;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.MultiSet;

import net.phonex.xv.Xvi;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Important class providing timer capability to the SIP stack.
 */
public class AndroidTimers  {
	private final static String TAG = "AndroidTimers";
	protected final static String TIMER_ACTION = "net.phonex.engine_timer";
    protected final static String EXTRA_TIMER_SCHEME = "timer";
    protected final static String EXTRA_TIMER_ENTRY_ID = "entry";
    protected final static String EXTRA_TIMER_ENTRY_ADDR = "entry_addr";
    protected final static String EXTRA_TIMER_ENTRY_FIRSTIME = "entry_firstime";
    protected final static String EXTRA_TIMER_ENTRY_SCHEDULED = "entry_scheduled";

    protected final static String EXTRA_TIMER_PARCELABLE = "entry_parcelable";
	private final static int INTENT_ID = 12345;
	// fast alarm is an experimental feature (see PHON-520), do not use at the moment
	private static final boolean USE_FAST_ALARM = false;

	private XService service;
	private AlarmManager alarmManager;
	private AppWakeLock wakeLock;

	private FastAlarm fastAlarm;


	private static AndroidTimers singleton;
    private static HandlerThread executorThread;
	private boolean serviceRegistered = false;
    private AndroidTimerJobHandler mExecutor;
    private final static Object singletonLock = new Object();

    // Broadcast listener for the Android timers.
    private AlarmTimerReceiver alarmReceiver;

    /**
     * If true then no fire should be fired anymore.
     * Serves as a alarm blocking switch in case a SIP stack is
     * not correctly initialized.
     */
    private volatile boolean shuttingDown=false;

	//
	// Set of scheduled timer entries.
	// Timer is registered to scheduleEntries upon receiving schedule request.
	// When Android Alarm fires (timeout for timer expired), timer has to have
	// scheduleEntries entry in order to fire PJSIP timer (to be posted on
	// executor thread in treat()). Otherwise is considered as canceled in waiting
	// for Android alarm phase.
	//
	// Basic protection not to add canceled timers to executors.
	//
	// Note: Was list before - not optimal... Use SET instead
	// Note on timer indexing:
	//   Formerly it was indexed only by entryId, but it was ambiguous.
	// Some scenarios caused to cancel foreign timers (on different addresses)
	// New indexing also includes timer address to prevent canceling foreign timers.
	private final Set<TimerIdx> scheduleEntries = Collections.newSetFromMap(new ConcurrentHashMap<TimerIdx,Boolean>());

	//
	// MultiSet of all timers for which was Android Alarm fired (treat call) and
	// posted to executor (we lost control of it).
	//
	// Basic tool for canceling already posted jobs to executor. Executor
	// checks for this. If is present: 1. doFire=true; 2. remove entry.
	//
	// Reminds semaphore. Gets incremented only in treat() call, decremented only
	// in executor.
	private final MultiSet<TimerIdx> treatedEntries = new MultiSet<TimerIdx>(256);

	//
	// Store timers that were already treated (alarm timer fired -> posted to executor)
	// and canceled later, in phase waiting-for-executor.
	//
	// Stores time of cancel() call for given timer. Executor ignores all timers with
	// treat time lower/equal than cancel() time.
	//
	private final Map<TimerIdx, Long> treatedCancelled = new ConcurrentHashMap<TimerIdx, Long>(256);

	/**
	 * Basic class to represent one timer, created by doSchedule() call.
	 * This object is posted for execution to executor on Android Alarm fire (treat).
	 *
	 * @author ph4r05
	 */
	private class TimerID {
		public int entry;				// timer address (pj_timer_entry)
		public int entryId;				// timer slot in heaps
		public long timeFiredAlarmEl;		// Timestamp of creation of an alarm job (android alarm was fired), in SystemClock.elapsedRealtime().
		public long timeFiredAlarmUTC;		// Timestamp of creation of an alarm job (android alarm was fired), in System.currentTimeMillis().

		public TimerID(int entry, int entryId) {
			this.entry = entry;
			this.entryId = entryId;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + entry;
			result = prime * result + entryId;
			result = prime * result + (int) (timeFiredAlarmEl ^ (timeFiredAlarmEl >>> 32));
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			TimerID other = (TimerID) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (entry != other.entry)
				return false;
			if (entryId != other.entryId)
				return false;
			if (timeFiredAlarmEl != other.timeFiredAlarmEl)
				return false;
			return true;
		}

		public String tid(){
			return String.format("%d [%d | 0x%x]", entryId, entry, entry);
		}

		public TimerIdx idx(){
			return new TimerIdx(entry, entryId);
		}

		private AndroidTimers getOuterType() {
			return AndroidTimers.this;
		}
	}

	/**
	 * Class for indexing timers in sets and hash maps (entryId && entry address)
	 * @author ph4r05
	 */
	private class TimerIdx{
		public int entry;
		public int entryId;

		public TimerIdx(int entry, int entryId) {
			this.entry = entry;
			this.entryId = entryId;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + entry;
			result = prime * result + entryId;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			TimerIdx other = (TimerIdx) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (entry != other.entry)
				return false;
			if (entryId != other.entryId)
				return false;
			return true;
		}
		private AndroidTimers getOuterType() {
			return AndroidTimers.this;
		}
	}

	private AndroidTimers(XService ctxt) {
		super();
		setContext(ctxt);

		fastAlarm = new FastAlarm();
	}


	private synchronized void setContext(XService ctxt) {
	    // If we have a new context, restart bindings
		Context appContext = ctxt.getApplicationContext();
		if(service != ctxt) {
    	    // Reset
    		quit();
    		// Set new service
    		service = ctxt;
    		alarmManager = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
            wakeLock = new AppWakeLock((PowerManager) appContext.getSystemService(Context.POWER_SERVICE));
            Log.d(TAG, "New context was set");
		}

		if(!serviceRegistered) {
            alarmReceiver = new AlarmTimerReceiver(this);
    		IntentFilter filter = new IntentFilter(TIMER_ACTION);
    		filter.addDataScheme(EXTRA_TIMER_SCHEME);
    		appContext.registerReceiver(alarmReceiver, filter);
    		serviceRegistered = true;
    		Log.df(TAG, "Registered alarm listener, this=%s", this);
		}

        shuttingDown=false;
	}

	private synchronized void quit() {
		Log.v(TAG, "Quit this wrapper");
		if(serviceRegistered) {
            serviceRegistered = false;
			try {
                if (alarmReceiver != null) {
                    service.getApplicationContext().unregisterReceiver(alarmReceiver);
                    alarmReceiver = null;
                }
			} catch (IllegalArgumentException e) {
				Log.e(TAG, "Impossible to destroy timer wrapper", e);
			}
		}

//		if (fastAlarm != null){
//			fastAlarm.deinit();
//		}

		if(wakeLock != null) {
			wakeLock.reset();
		}

		if(alarmManager != null) {
			for(TimerIdx entryId : scheduleEntries) {
				alarmManager.cancel(getPendingIntentForTimer(entryId));
			}
		}

		scheduleEntries.clear();
		treatedEntries.clear();
		treatedCancelled.clear();
	}

	/**
	 * Creates PendingIntent for Android Alarm timers.
	 * When new alarm is created / cancelled, this intent uniquely
	 * defines the alarm. Equals used: Intent.filterEquals(Intent other)
	 *
	 * Definition of filterEquals:
	 * Determine if two intents are the same for the purposes of intent resolution (filtering).
	 * That is, if their action, data, type, class, and categories are the same.
	 * This does not compare any extra data included in the intents.
	 *
	 * @param entry
     * @param entryId
     * @param scheduled
     * @param firstime
     * @return
	 */
	private PendingIntent getPendingIntentForTimer(int entry, int entryId, long scheduled, long firstime) {
		Intent intent = new Intent(TIMER_ACTION);
		String toSend = EXTRA_TIMER_SCHEME + "://" + Integer.toString(entryId) + "/" + Integer.toString(entry);
		intent.setData(Uri.parse(toSend));

		// Extras are not involved in intent comparison.
		intent.putExtra(EXTRA_TIMER_ENTRY_ID, entryId);
		intent.putExtra(EXTRA_TIMER_ENTRY_ADDR, entry);
		intent.putExtra(EXTRA_TIMER_ENTRY_SCHEDULED, scheduled);
		intent.putExtra(EXTRA_TIMER_ENTRY_FIRSTIME, firstime);
		return PendingIntent.getBroadcast(service.getApplicationContext(), INTENT_ID, intent, PendingIntent.FLAG_CANCEL_CURRENT);
	}

	private PendingIntent getPendingIntentForTimer(TimerIdx idx) {
		return getPendingIntentForTimer(idx.entry, idx.entryId, 0, 0);
	}

	private PendingIntent getPendingIntentForTimer(TimerID t) {
		return getPendingIntentForTimer(t.entry, t.entryId, 0, 0);
	}

	private PendingIntent getPendingIntentForTimer(TimerID t, long scheduled, long firstime) {
		return getPendingIntentForTimer(t.entry, t.entryId, scheduled, firstime);
	}

	private synchronized int doScheduleTimer(final int entry, final int entryId, int intervalMs) {
		final TimerID tid  = new TimerID(entry, entryId);
		final TimerIdx idx = tid.idx();

		Log.df(TAG, "SCHED timer %s in [%s] ms; ", tid.tid(), intervalMs);
		PendingIntent pendingIntent = getPendingIntentForTimer(tid);

		// If less than 1 sec, do not wake up -- that's probably stun check so useless to wake up about that
		//int alarmType = (intervalMs < 1000) ? AlarmManager.ELAPSED_REALTIME : AlarmManager.ELAPSED_REALTIME_WAKEUP;

		final long curTime = SystemClock.elapsedRealtime();
		long firstTime = curTime;

		// Cancel previous reg anyway
		alarmManager.cancel(pendingIntent);
		scheduleEntries.remove(idx);

		// Timer was fired, posted on executor (treated), new schedule ->
		// this schedule should cancel previous one.
		// TreatedEntries is decremented only in executor.
		if (treatedEntries.contains(idx)){
			Log.inf(TAG, "Timer %s androidFired, waiting for executor, yet and again scheduled", tid.tid());

			// Check for very rare event:
			// SCHED | {all next in same millisecond} TREAT, CANCEL[set cancel time], SCHED, TREAT |
			// The second scheduling of timer is legitimate, but precision of timer is
			// too small so also second timer is canceled, since treat occurred in same millisecond
			// period as first cancel.
			// Solution: if this rare event is detected, second schedule with 0ms will be changed to 1ms,
			// thus escaping from the same window of cancel and saving timer from the first cancel.
			Long cancelTime = treatedCancelled.get(idx);
			if (intervalMs == 0 && cancelTime!=null && cancelTime.longValue() == firstTime){
				Log.w(TAG, "Rare event occurred; TREAT, CANCEL, SCHED[0ms], TREAT in one millisecond window. Changing to 1ms");
				intervalMs = 1;
			}

			treatedCancelled.put(idx, firstTime);
		}

		firstTime += (long)intervalMs;

		// Special treating for 0ms timers - treat immediately
		// and do not bother with alarm.
		if (intervalMs == 0){
			scheduleEntries.add(idx);
			alarmFired(entry, entryId, curTime, firstTime);
			return 1;
		}

        scheduleEntries.add(idx);
		planAlarmInternally(tid, curTime, firstTime, intervalMs);
		return 1;
	}

	private void planAlarmInternally(TimerID timerID, long scheduled, long firstTime, long intervalMs){
		Log.vf(TAG, "planAlarmInternally - alarm for timer %s in ms=%s ms; firstTime=%s ms; (scheduled-first)=%s", timerID.tid(),
				intervalMs, firstTime,
				((scheduled) - (firstTime)));

		if (USE_FAST_ALARM && intervalMs <= 500){
			// Fast alarm - just use HandlerThread
			fastAlarm.setAlarm(timerID.entry, timerID.entryId, scheduled, firstTime, intervalMs);
		} else {
			// Slow alarm - use AlarmManager system service
			PendingIntent pendingIntent = getPendingIntentForTimer(timerID, scheduled, firstTime);
			MiscUtils.setExactAlarm(alarmManager, AlarmManager.ELAPSED_REALTIME_WAKEUP, firstTime, pendingIntent);
		}
	}

	private synchronized int doCanceltimer(int entry, int entryId) {
		TimerID tid  = new TimerID(entry, entryId);
		TimerIdx idx = tid.idx();
		long curTime = SystemClock.elapsedRealtime();

        Log.vf(TAG, "doCancel timer %s; time=%s", tid.tid(), curTime);
		alarmManager.cancel(getPendingIntentForTimer(tid));

		final boolean scheduleEntriesModified = scheduleEntries.remove(idx);
		boolean treatedEntriesModified = false;

		// important add on - remove also from treatedEntries, like it
		// would never be fired. Otherwise if timer was treated and not yet
		// executed by executor, and cancelled, it is not wanted to execute
		// it anymore.
		if (treatedEntries.contains(idx)){
			// Was treated -> it will be launched soon by executor.
			Log.vf(TAG, "Timer %s was already posted for execution -> disable it; time=%s", tid.tid(), curTime);
			// this disables old timers from running
			treatedCancelled.put(idx, curTime); // this will be removed by treated
			treatedEntriesModified = true;
		}

		return (scheduleEntriesModified || treatedEntriesModified) ? 1 : 0;
	}

	/**
	 * Android timer fired -> fire corresponding PJSIP timer from executor
	 * treat() call.
	 *
	 * This call adds TimerID job to executor, if it was not canceled meanwhile.
	 *
	 * @param
	 */
	public synchronized void alarmFired(int entry, int entryId, long scheduled, long firstime){
		TimerID tid  = new TimerID(entry, entryId);
		TimerIdx idx = tid.idx();

		long curTimeUTC = System.currentTimeMillis();
		long curTimeEl  = SystemClock.elapsedRealtime();
		if(singleton == null) {
			Log.e(TAG, "Not found singleton");
			return;
		}

		Log.vf(TAG, "Treat alarm for timer %s; curTimeEl=%d; curTimeUTC=%d; scheduled=%d, firsttime=%d",
				tid.tid(), curTimeEl, curTimeUTC, scheduled, firstime);

		// Base logic assertion
		if (scheduled > firstime){
			Log.ef(TAG, "Invalid timer %s, scheduled > firstime", tid.tid());
		}

		// If timer is not in schedule list, then it
		// was canceled during waiting on Android Alarm -> no treat for this timer.
        if (scheduleEntries.contains(idx)==false){
        	Log.vf(TAG, " timer %s was canceled meanwhile, not executing...", tid.tid());
        	return;
        }

        // Determine if alarm was not triggered too early.
     	if (firstime > curTimeEl){
     		Log.ef(TAG, "Timer %s was triggered too early. Firstime > curTimeEl", tid.tid());

     		// Do not allow this behavior. Re-schedule timer.
			planAlarmInternally(tid, scheduled, firstime, (firstime - curTimeEl));
			Log.vf(TAG, "Timer %s was re-scheduled.", tid.tid());
            return;
     	}

        // Treat finally - post job to executor
    	tid.entryId = entryId;
    	tid.timeFiredAlarmEl  = curTimeEl;  // this is time of creation a job, not timer (alarm already fired)
    	tid.timeFiredAlarmUTC = curTimeUTC;

    	// Increase treated counter in multi-set.
    	treatedEntries.inc(idx);
    	singleton.treatAlarm(tid);

    	Log.vf(TAG, "Scheduled timer %s onExecutor; wrapperTID: %s", tid.tid(), android.os.Process.myTid());
	}

	public void executeInTimerThread(String desc, Runnable job){
		getHandler().execute(new JobWrapper(job, desc));
	}

	public void treatAlarm(TimerID tID) {
		getHandler().execute(new TimerJob(tID));
	}

	// Public API
	public static void create(XService ctxt) {
	    synchronized (singletonLock) {
	        if(singleton == null) {
	            singleton = new AndroidTimers(ctxt);
	        }else {
	            singleton.setContext(ctxt);
	        }
        }
	}

	public static void destroy() {
	    synchronized (singletonLock) {
    		if(singleton != null) {
    			singleton.quit();
    		}
	    }
	}

	public static int executeInTimerThreadStatic(SvcRunnable job){
		return executeInTimerThreadStatic(job.getName(), job);
	}

	public static int executeInTimerThreadStatic(String desc, Runnable job){
		if(singleton == null) {
			Log.e(TAG, "Timer NOT initialized");
			return -1;
		}

		singleton.executeInTimerThread(desc, job);
		return 1;
	}

	public static int schedule(final int entry, final int entryId, final int time) {
		if(singleton == null) {
			Log.e(TAG, "Timer NOT initialized");
			return -1;
		}

		return singleton.doScheduleTimer(entry, entryId, time);
	}

	public static int cancel(int entry, int entryId) {
		return singleton.doCanceltimer(entry, entryId);
	}

    private static Looper createLooper() {
        if (executorThread == null) {
            Log.d(TAG, "NewHandlerThread");
            executorThread = new HandlerThread("AndroidTimer.Handler");
            executorThread.start();
        }
        return executorThread.getLooper();
    }

    private AndroidTimerJobHandler getHandler() {
        // create mExecutor lazily
        if (mExecutor == null) {
            mExecutor = new AndroidTimerJobHandler(createLooper());
            Log.i(TAG, "Executor was null, creating new AndroidTimerJobHandler");
        }
        return mExecutor;
    }

    /**
     * Return date in specified format.
     * @param milliSeconds Date in milliseconds
     * @param dateFormat Date format
     * @return String representing date in specified format
     */
    public static String getDate(long milliSeconds, String dateFormat){
        // Create a DateFormatter object for displaying date in specified format.
        DateFormat formatter = new SimpleDateFormat(dateFormat, Locale.getDefault());

        // Create a calendar object that will convert the date and time value in milliseconds to date.
         Calendar calendar = Calendar.getInstance();
         calendar.setTimeInMillis(milliSeconds);
         return formatter.format(calendar.getTime());
    }

    public static String getDate(long milliSeconds){
    	return getDate(milliSeconds, "hh:mm:ss.SSS");
    }

    public static boolean isShuttingDown() {
        if(singleton == null) {
            Log.e(TAG, "Timer NOT initialized");
            return false;
        }

        return singleton.shuttingDown;
    }

    /**
     * Sets emergency shutting down switch. No timer will be fired from the point after setting this
     * to true value.
     * @param shuttingDown
     */
    public static void setShuttingDown(boolean shuttingDown) {
        if(singleton == null) {
            Log.e(TAG, "Timer NOT initialized");
            return;
        }

        singleton.shuttingDown = shuttingDown;
    }


	/**
     * Basic timer task.
     * @author ph4r05
     */
	private class TimerJob implements Runnable {

		private final TimerID t;
		private final TimerIdx idx;
		public TimerJob(TimerID tID){
			this.t = tID;
			this.idx = t.idx();
			wakeLock.lock(this);
		}

		public String tid(){
			return t.tid();
		}

		@Override
		public void run() {
			long curTime = System.currentTimeMillis();
			long delta = curTime - t.timeFiredAlarmUTC;

			try {
			    boolean doFire = false;
			    synchronized (AndroidTimers.this) {

			    	Log.log(delta > 20 ? Log.LOG_W : Log.LOG_V, TAG,
							String.format("FIRE START timer %s; firedAt=%d; treatedCn=%d; deltaExecutor=%d; TID=%s",
									tid(), t.timeFiredAlarmEl, treatedEntries.count(idx), delta, android.os.Process.myTid()));

			    	// TreatedEntries in a multiset, if we are here
			    	// and there is no element in multiset, it is a serious
			    	// problem with logic of timers.
			    	if (treatedEntries.contains(idx)==false){
			    		Log.e(TAG, "Fatal error in AndroidTimers impl. Executor-fired timer was not treated...");
			    	} else {
			    		doFire = true;

		                // Really do fire? Check if it was not cancelled
		                // Fixes scenario treat&cancel,treat&cancel,treat&cancel,treat&cancel,treat
		                // where we have 4 posted task, all of them cancelled, but it will execute 5 times.
			    		// In another words, allow only the latest timer to fire.
		                if (treatedCancelled.containsKey(idx) && t.timeFiredAlarmEl!=-1){
		                	final Long expiredLimit = treatedCancelled.get(idx);
		                	Log.df(TAG, "doFire && cancel-record exists for timer %s; AFired=%s; Climit=%s; doFire=%s",
		                	        tid(),
		                	        t.timeFiredAlarmEl,
		                	        expiredLimit,
		                	        doFire);

		                	// All timers posted to execute before this limit are ignored automatically.
		                	if (t.timeFiredAlarmEl <= expiredLimit){
		                		doFire=false;
		                	} else {
		                		// This thread is OK, so we can remove limit, every earlier timer
		                		// was already seen.
		                		treatedCancelled.remove(idx);
		                	}
		                }

		                // TreatedEntries multiset counter is decremented only on this place.
		                // Everything is OK, so it gets decremented right here.
		                // No matter if it will or wont fire. The point is it was handled.
		                //
		                // In this place there is no chance to affect cancel of this timer.
		                treatedEntries.dec(idx);
			    	}
                }

			    if(doFire) {
			    	Log.vf(TAG, "FIRE START - before JNIcall timer %s; firedAt=%s; (now-firedAt)=%s",
			    	        tid(),
			    	        t.timeFiredAlarmEl,
			    	        ((SystemClock.elapsedRealtime()) - (t.timeFiredAlarmEl)));

                    // If shutting down, timer is not triggered. This shutting down switch is emergency switch
                    // in case of memory corruption and in order to avoid segfault.
                    if (!shuttingDown) {
                        Xvi.pj_timer_fire(t.entryId);
                    } else {
                        Log.wf(TAG, "Shutting down, timer ignored %s", tid());
                    }
			    }else {
			        Log.wf(TAG, "Fire from old run timer %s", tid());
			    }
			}catch(Exception e) {
				Log.e(TAG, "Native error ", e);
			}finally {
				wakeLock.unlock(this);
			}

			Log.vf(TAG, "FIRE DONE timer %s; firedAt=%s; (now-firedAt)=%s",
			        tid(),
			        t.timeFiredAlarmEl,
			        ((SystemClock.elapsedRealtime()) - (t.timeFiredAlarmEl)));
		}
	}

	/**
	 * Runnable wrapper for a job planned for execution in a timer thread.
	 */
	private class JobWrapper implements Runnable {
		private final Runnable job;
		private final String desc;
		private final long submitTime;
		private final long submitTimeEl;

		public JobWrapper(Runnable job, String desc) {
			this.job = job;
			this.desc = desc;
			this.submitTime = System.currentTimeMillis();
			this.submitTimeEl = SystemClock.elapsedRealtime();
			wakeLock.lock(this);
		}

		@Override
		public void run() {
			final long curTime = System.currentTimeMillis();
			final long curTimeEl = SystemClock.elapsedRealtime();
			final long delta = curTime - submitTime;

			try {
				synchronized (AndroidTimers.this) {
					Log.log(delta > 20 ? Log.LOG_W : Log.LOG_V, TAG,
							String.format("FIRE START job %s; firedAt=%d; deltaExecutor=%d; TID=%s",
									desc, submitTimeEl, delta, android.os.Process.myTid()));

					// If shutting down, timer is not triggered. This shutting down switch is emergency switch
					// in case of memory corruption and in order to avoid segfault.
					if (!shuttingDown) {
						job.run();
					} else {
						Log.wf(TAG, "Shutting down, timer ignored %s", desc);
					}
				}
			} catch (Exception e) {
				Log.e(TAG, "Native error ", e);
			} finally {
				wakeLock.unlock(this);
			}

			Log.vf(TAG, "FIRE DONE timer %s; firedAt=%s; (now-firedAt)=%s",
					desc,
					submitTimeEl,
					((SystemClock.elapsedRealtime()) - (submitTimeEl)));
		}
	}

	private class FastAlarm{
		private HandlerThread fastHandlerThread;
		private FastAlarmHandler fastHandler;

		public FastAlarm() {
			fastHandlerThread = new HandlerThread("fast_handler_thread");
			fastHandlerThread.start();
			fastHandler = new FastAlarmHandler(AndroidTimers.this, fastHandlerThread.getLooper());
		}

		public void setAlarm(int entry, int entryId, long scheduled, long firstime, long intervalMs){
			Log.vf(TAG, "Schedule fast alarm, ms = %d", intervalMs);
			Message m = new Message();
			m.what = FastAlarmHandler.TYPE_FAST_ALARM ;
			Bundle bundle = new Bundle();
			bundle.putInt(EXTRA_TIMER_ENTRY_ID, entryId);
			bundle.putInt(EXTRA_TIMER_ENTRY_ADDR, entry);
			bundle.putLong(EXTRA_TIMER_ENTRY_SCHEDULED, scheduled);
			bundle.putLong(EXTRA_TIMER_ENTRY_FIRSTIME, firstime);
			m.setData(bundle);
			fastAlarm.getHandler().sendMessageDelayed(m, intervalMs);
		}

		public void deinit(){
			fastHandler = null;
			MiscUtils.stopHandlerThread(fastHandlerThread, false);
		}

		public Handler getHandler() {
			return fastHandler;
		}
	}
}

package net.phonex.service.runEngine;


import android.content.Context;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import net.phonex.util.Log;


/**
 * Partial wake lock for CPU locking.
 * 
 * @author ph4r05
 */
public class MyWakeLock {
	private static final String THIS_FILE = "MyWakeLock";
	
	/**
	 * Wakelock itself.
	 */
	private WakeLock lock;
	
	/**
	 * Wake lock reference counter.
	 */
	private int lockCount = 0;
	
	/**
	 * Name of the wakelock - registration into system.
	 */
	private final String wakeLockName;
	
	/**
	 * Reference counted?
	 */
	private final boolean referenceCounted;

    /**
     * Factory method.
     * @param ctxt
     * @param wakeLockName
     * @return
     */
    public static MyWakeLock newInstance(Context ctxt, String wakeLockName){
        return new MyWakeLock(ctxt, wakeLockName);
    }

    /**
     * Factory method.
     * @param ctxt
     * @param wakeLockName
     * @param referenceCounted
     * @return
     */
    public static MyWakeLock newInstance(Context ctxt, String wakeLockName, boolean referenceCounted){
        return new MyWakeLock(ctxt, wakeLockName, referenceCounted);
    }

	/**
	 * Creates a wakelock.
	 * ReferenceCounted = true.
	 * Calls initWakeLock() if ctxt != null.
	 */
	public MyWakeLock(Context ctxt, String wakeLockName) {
		this.wakeLockName = wakeLockName;
		this.referenceCounted = true;
		
		if (ctxt!=null){
			initWakeLock(ctxt);
		}
	}
	
	/**
	 * Creates a wakelock.
	 * Calls initWakeLock() if ctxt != null.
	 * 
	 * @param wakeLockName
	 * @param referenceCounted
	 */
	public MyWakeLock(Context ctxt, String wakeLockName, boolean referenceCounted) {
		this.wakeLockName = wakeLockName;
		this.referenceCounted = referenceCounted;
		
		if (ctxt!=null){
			initWakeLock(ctxt);
		}
	}
	
	/**
	 * Initializes wake lock.
	 */
	public synchronized final void initWakeLock(Context ctxt){
		if (lock == null) {
		    PowerManager pman = (PowerManager) ctxt.getSystemService(Context.POWER_SERVICE);
		    lock = pman.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, wakeLockName);
		    lock.setReferenceCounted(referenceCounted);
		}
	}
	
	/**
	 * Frees wake lock.
	 */
	public synchronized void deinit(){
		// Ensure lock is released since this lock is a ref counted one.
        if (lock != null) {
            while (lock.isHeld()) {
            	lock.release();
            }
        }
	}

	public synchronized void lock() {
		lock(null);
	}
	
	/**
	 * Locks wakelock.
	 */
	public synchronized void lock(String holder) {
        if (lock != null) {
            Log.df(THIS_FILE, "%s:<- WL locked: %s; holder=%s", wakeLockName, lockCount, holder);
            lock.acquire();
            lockCount++;
        } else {
        	Log.w(THIS_FILE, "Lock is null");
        }
    }

	/**
	 * Unlocks wakelock.
	 */
    public synchronized void unlock() {
        if (lock != null){
        	if (lock.isHeld()) {
        		lock.release();
        		lockCount--;
        		Log.df(THIS_FILE, "%s:-> WL unlocked %s", wakeLockName, lockCount);
        	} else {
        		Log.d(THIS_FILE, "Lock not held");
        	}
        } else {
        	Log.w(THIS_FILE, "Lock is null");
        }
    }

    /**
     * Returns current lock state.
     * @return
     */
    public boolean isHeld(){
        if (lock==null){
            Log.w(THIS_FILE, "Lock is null");
            return false;
        }

        return lock.isHeld();
    }

	public WakeLock getLock() {
		return lock;
	}

	public int getLockCount() {
		return lockCount;
	}

    /**
     * Static helper. Deinit if not null.
     * @param wl
     */
    public static void deinitIfNotNull(MyWakeLock wl){
        if (wl!=null){
            wl.deinit();
        }
    }
}

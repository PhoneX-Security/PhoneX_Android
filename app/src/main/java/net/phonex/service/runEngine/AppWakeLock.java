package net.phonex.service.runEngine;

import android.os.PowerManager;

import net.phonex.util.Log;

import java.util.HashSet;


public class AppWakeLock {
    private static final String THIS_FILE = "AppWakeLock";
    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;
    private PowerManager.WakeLock mTimerWakeLock;
    private HashSet<Object> mHolders = new HashSet<Object>();
    
    /**
     * Name of the wake lock.
     */
    private final String wakeLockName;
    
    /**
     * Name of the timed wake lock.
     */
    private final String timerWakeLockName;

    /**
     * Creates a new wake lock with fixed names.
     * 
     * @param powerManager
     * @param namePrefix
     */
    public AppWakeLock(PowerManager powerManager, String namePrefix) {
    	mPowerManager = powerManager;
        wakeLockName = namePrefix + ".lock";
        timerWakeLockName = namePrefix + ".lock.timer";
    }
    
    public AppWakeLock(PowerManager powerManager) {
        mPowerManager = powerManager;
        wakeLockName = "PhoneXWakeLock";
        timerWakeLockName = "PhoneXWakeLock.timer";
    }

    /**
     * Release this lock and reset all holders
     */
    public synchronized void reset() {
        mHolders.clear();
        unlock(null);
        if(mWakeLock != null) {
	        while(mWakeLock.isHeld()) {
	        	mWakeLock.release();
	        }
	        
	        Log.vf(THIS_FILE, "reset wakelock[%s]; held=%s", wakeLockName, mWakeLock.isHeld());
        }
    }

    public synchronized void lock(long timeout) {
        if (mTimerWakeLock == null) {
            mTimerWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, timerWakeLockName);
            mTimerWakeLock.setReferenceCounted(true);
        }
        mTimerWakeLock.acquire(timeout);
    }

    public synchronized void lock(Object holder) {
        mHolders.add(holder);
        if (mWakeLock == null) {
            mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, wakeLockName);
        }
        
        if (!mWakeLock.isHeld()) {
        	mWakeLock.acquire();
        }
        
        Log.vf(THIS_FILE, "WL locked [%s]: holder count=%s; holder=%s", wakeLockName, mHolders.size(), holder);
    }

    public synchronized void unlock(Object holder) {
        mHolders.remove(holder);
        if ((mWakeLock != null) && mHolders.isEmpty() && mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        
        Log.vf(THIS_FILE, "WL unlocked [%s]: holder count=%s; holder=%s", wakeLockName, mHolders.size(), holder);
    }
}

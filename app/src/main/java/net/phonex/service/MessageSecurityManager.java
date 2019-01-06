package net.phonex.service;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;

import net.phonex.core.Intents;
import net.phonex.pref.PhonexConfig;
import net.phonex.db.entity.QueuedMessage;
import net.phonex.db.entity.SipMessage;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.Registerable;

/**
 * Periodically deletes "expired" messages from SipMessage  and MessageQueue tables
 * Created by miroc on 3.12.14.
 */
public class MessageSecurityManager implements Registerable{
    private static final String TAG = "MessageSecurityManager";
    public static final int MESSAGE_QUEUE_LEFTOVERS_EXPIRATION_HOURS = 24;
    private static final Long MIN_ALARM_DISTANCE = 5*60*1000l; // 5 min

    /**
     * Determines whether module is registered for operation.
     */
    private boolean registered = false;
    private Handler basicHandler = new Handler();
    private SipMessageChangeObserver msgObserver;

    private XService svc;
    private Long msgExpAlarmTime = null;
    private Long msgExpLastAlarmTime = null;

    public MessageSecurityManager(XService svc) {
        this.svc = svc;
    }

    public synchronized void register() {
        if (registered){
            Log.w(TAG, "Already registered");
            return;
        }

        if(msgObserver == null && svc != null) {
            msgObserver = new SipMessageChangeObserver(basicHandler);
            svc.getContentResolver().registerContentObserver(SipMessage.MESSAGE_URI_DELETED, true, msgObserver);
            svc.getContentResolver().registerContentObserver(SipMessage.MESSAGE_URI_INSERTED, true, msgObserver);
        }

        Log.v(TAG, "MessageSecurityManager registered.");
        registered = true;
    }

    public synchronized void unregister(){
        if (!registered){
            Log.w(TAG, "Already unregistered");
            return;
        }

        // Unregister content observer
        if(msgObserver != null && svc != null) {
            try {
                svc.getContentResolver().unregisterContentObserver(msgObserver);
            } catch(Exception e){
                Log.e(TAG, "Exception during unregisterContentObserver(msgObserver)", e);
            }
        }

        Log.v(TAG, "MessageSecurityManager unregistered.");
        registered = false;
    }

    public static void deleteExpiredMessages(Context ctx){
        try {
            long deletePeriod = getDeletePeriod(ctx);
            long deleteThreshold = System.currentTimeMillis() - deletePeriod;

            ContentResolver contentResolver = ctx.getContentResolver();
            if (deletePeriod<0){ }//none
            else {
                int deletedCount = contentResolver.delete(SipMessage.MESSAGE_URI, " date < " + deleteThreshold + " ", null);
                Log.vf(TAG, "deleteExpiredMessages(): number of deleted messages [%d]", deletedCount);
            }
        } catch (Exception e){
            Log.ef(TAG, e, "Unable to delete expired messages from SipMessage table");
        }
    }

    public static void deleteLeftoversFromMessageQueue(Context ctx){
        try {
            ContentResolver contentResolver = ctx.getContentResolver();
            int deletePeriodInHours  = MESSAGE_QUEUE_LEFTOVERS_EXPIRATION_HOURS;

            //older than 'deletePeriod' seconds
            long millis = System.currentTimeMillis() - deletePeriodInHours*60*60*1000l;
            int deletedCount = contentResolver.delete(QueuedMessage.URI, QueuedMessage.FIELD_TIME + " < ?", new String[]{String.valueOf(millis)});
            Log.vf(TAG, "Number of deleted QueuedMessage leftovers is [%d]", deletedCount);
        } catch (Exception e){
            Log.ef(TAG, e, "Unable to delete expired messages from SipMessage table");
        }
    }

    /**
     * Alarm invoked method
     */
    public synchronized void onExpirationCheckAlarm() {
        Log.v(TAG, "Alarm fired for message expiration check");
        msgExpLastAlarmTime = System.currentTimeMillis();
        // remove flag about ongoing alarm
        msgExpAlarmTime = null;

        deleteExpiredMessages(svc.getApplicationContext());

        // plan future alarm
        setupExpirationCheckAlarm(false);
    }

    public synchronized void setupExpirationCheckAlarm(boolean force){
        Log.v(TAG, "Trying to setup expiration check alarm.");
        try {
            if (!force && msgExpAlarmTime != null) {
                return;
            }
            long oldestMessageTime = getOldestMessageTime();
            if (oldestMessageTime == 0){
                return; // no messages
            }

            // absolute time in the future
            long timeToFire = oldestMessageTime + getDeletePeriod(svc.getApplicationContext());

            // check if we do not fire alarm too soon
            if (msgExpLastAlarmTime != null) {
                timeToFire = Math.max(timeToFire, msgExpLastAlarmTime + MIN_ALARM_DISTANCE);
            }

            // time delay
            long delay = timeToFire - System.currentTimeMillis();

            // if there are some leftovers, the time can be negative, set it to 0
            delay = Math.max(0, delay);

            Log.inf(TAG, "Setting Alarm [%s] in [%d] ms", Intents.ALARM_MESSAGE_EXPIRATION_CHECK, delay);
            msgExpAlarmTime = timeToFire;
            svc.setAlarm(Intents.ALARM_MESSAGE_EXPIRATION_CHECK, delay);
        } catch (Exception e){
            Log.ef(TAG, e, "Cannot setup expirationCheck alarm");
        }
    }

    /**
     * Callback from XService
     */
    public void onLoginFinished() {
        deleteLeftoversFromMessageQueue(svc.getApplicationContext());
        // initially run the alarm manually
        onExpirationCheckAlarm();
    }

    /**
     * Get delete period in milliseconds
     */
    public static long getDeletePeriod(Context ctx){
        int deletePeriod = Integer.parseInt(PhonexConfig.getStringPref(ctx, PhonexConfig.MESSAGES_DELETE_PERIOD, "0"));
        return deletePeriod*60*60*1000l;
    }

    private long getOldestMessageTime(){
        ContentResolver contentResolver = svc.getApplicationContext().getContentResolver();

        String[] projection = new String[]{" MIN(" + SipMessage.FIELD_DATE + ") as min_time"};
        Cursor c = null;
        try {
            c = contentResolver.query(SipMessage.MESSAGE_URI, projection, null, null, null);
            if (c == null) {
                return 0;
            } else if (c.moveToFirst()) {
                long minTime = c.getLong(0);
                return minTime;
            } else {
                return 0;
            }
        } finally {
            MiscUtils.closeCursorSilently(c);
        }
    }

    /**
     * Observer waiting for insertion/deleting of messages
     */
    private class SipMessageChangeObserver extends ContentObserver {
        public SipMessageChangeObserver(Handler h) {
            super(h);
        }

        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            setupExpirationCheckAlarm(false);
        }
    }
}

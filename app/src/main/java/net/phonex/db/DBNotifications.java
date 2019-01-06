package net.phonex.db;

import android.net.Uri;

import net.phonex.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Management object for delayed notifications.
 *
 * Created by dusanklinec on 11.06.15.
 */
public class DBNotifications {
    private static final String TAG = "DBNotifications";
    private final DBProvider provider;
    private final Queue<DelayedNotification> delayedNotifications = new ConcurrentLinkedQueue<>();

    public DBNotifications(DBProvider provider) {
        this.provider = provider;
    }

    /**
     * Post a new notification to the delayer.
     */
    public void addNotification(Uri uri, Uri baseUri){
        delayedNotifications.add(new DelayedNotification(uri, baseUri));
    }

    /**
     * Broadcasts delayed notifications.
     * @param pruningStrategy Strategy to remove duplicates from notification list.
     */
    public void flushNotifications(NotificationsPruningStrategy pruningStrategy){
        if (delayedNotifications.isEmpty()){
            return;
        }

        try {
            // Remove all elements from queue to the array list, cleaning queue + easier manipulation.
            ArrayList<DelayedNotification> notifs = new ArrayList<>(delayedNotifications.size());
            while (true) {
                DelayedNotification notif = delayedNotifications.poll();
                if (notif == null) {
                    break;
                }

                notifs.add(notif);
            }

            // Cancellation of consecutive elements.
            notifs = pruneNotifications(notifs, pruningStrategy);

            // Broadcasting of notifications left.
            Log.vf(TAG, "Broadcasting delayed notifications, size=%s", notifs.size());
            for(DBNotifications.DelayedNotification notif : notifs){
                provider.notifyChangeRaw(notif.uri);
            }

        } catch(Exception e){
            Log.ef(TAG, e, "Exception when broadcasting delayed notifications");
        }
    }

    /**
     * Prune notification list so we do not trigger sae updates in a row after applyBatch().
     * @param notifs
     * @param strategy
     * @return
     */
    private ArrayList<DelayedNotification> pruneNotifications(ArrayList<DelayedNotification> notifs, NotificationsPruningStrategy strategy){
        if (notifs.size() <= 1 || strategy == NotificationsPruningStrategy.PRUNE_NONE) {
            return notifs;
        }

        ArrayList<DBNotifications.DelayedNotification> notifsNew = new ArrayList<>(delayedNotifications.size());

        // Consecutive pruning method.
        if (strategy == NotificationsPruningStrategy.PRUNE_CONSECUTIVE_URI || strategy == NotificationsPruningStrategy.PRUNE_CONSECUTIVE_BASEURI) {
            pruneConsecutive(notifs, notifsNew, strategy);
        } else if (strategy == NotificationsPruningStrategy.PRUNE_TOTAL_URI || strategy ==  NotificationsPruningStrategy.PRUNE_TOTAL_BASEURI) {
            pruneTotal(notifs, notifsNew, strategy);
        }

        Log.vf(TAG, "Notifications pruning, original %s vs. new %s", notifs.size(), notifsNew.size());
        return notifsNew;
    }

    /**
     * Separate logic for total pruning strategy.
     * @param notifs
     * @param notifsNew
     * @param strategy
     */
    private void pruneTotal(ArrayList<DelayedNotification> notifs, ArrayList<DelayedNotification> notifsNew, NotificationsPruningStrategy strategy){
        Set<Uri> tmpSet = new HashSet<>();
        for(DelayedNotification notif : notifs){
            final Uri uriToCheck = strategy == NotificationsPruningStrategy.PRUNE_TOTAL_BASEURI ? notif.baseUri : notif.uri;
            if (tmpSet.add(uriToCheck)){
                notifsNew.add(notif);
            }
        }
    }

    /**
     * Separate logic for sequential pruning.
     * @param notifs
     * @param notifsNew
     * @param strategy
     */
    private void pruneConsecutive(ArrayList<DelayedNotification> notifs, ArrayList<DelayedNotification> notifsNew, NotificationsPruningStrategy strategy){
        final int len = notifs.size();

        DBNotifications.DelayedNotification cmpNotif = notifs.get(0);
        notifsNew.add(cmpNotif);

        // Iterate over old notifications and add only unique in the row.
        for (int i = 1; i < len; i++) {
            DBNotifications.DelayedNotification curNotif = notifs.get(i);

            // Same uri as previous compare item -> do not add to new notifications, move forward.
            if (curNotif.uri.equals(cmpNotif.uri)) {
                continue;
            }

            // If we should check also match for base uri, do it, if both are non-null.
            if (strategy ==  DBNotifications.NotificationsPruningStrategy.PRUNE_CONSECUTIVE_BASEURI
                    && curNotif.baseUri != null
                    && curNotif.baseUri.equals(cmpNotif.baseUri))
            {
                continue;
            }

            // This is a new notification in a row, add to the list, move cmpNotif.
            notifsNew.add(curNotif);
            cmpNotif = curNotif;
        }
    }

    /**
     * Notification list prune strategy.
     */
    public enum NotificationsPruningStrategy {
        PRUNE_NONE,
        PRUNE_CONSECUTIVE_URI,
        PRUNE_CONSECUTIVE_BASEURI,
        PRUNE_TOTAL_URI,
        PRUNE_TOTAL_BASEURI
    }

    /**
     * Delayed notification storage, with baseUri for inserts.
     */
    public static class DelayedNotification {
        public Uri uri;
        public Uri baseUri;

        public DelayedNotification(Uri uri) {
            this.uri = uri;
            this.baseUri = uri;
        }

        public DelayedNotification(Uri uri, Uri baseUri) {
            this.uri = uri;
            this.baseUri = baseUri;
            if (this.baseUri == null){
                this.baseUri = this.uri;
            }
        }

        @Override
        public String toString() {
            return "DelayedNotification{" +
                    "uri=" + uri +
                    ", baseUri=" + baseUri +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            DelayedNotification that = (DelayedNotification) o;

            if (uri != null ? !uri.equals(that.uri) : that.uri != null) return false;
            return !(baseUri != null ? !baseUri.equals(that.baseUri) : that.baseUri != null);

        }

        @Override
        public int hashCode() {
            int result = uri != null ? uri.hashCode() : 0;
            result = 31 * result + (baseUri != null ? baseUri.hashCode() : 0);
            return result;
        }
    }
}

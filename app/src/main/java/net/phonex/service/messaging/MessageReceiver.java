package net.phonex.service.messaging;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import net.phonex.core.Intents;
import net.phonex.util.Log;

import java.lang.ref.WeakReference;

/**
 * Broadcast receiver for sent messages.
 */
public class MessageReceiver extends BroadcastReceiver {
    private static final String TAG = "MessageReceiver";
    private final WeakReference<MessageManager> msgMgrWr;
    private MessageManager mgr;

    public MessageReceiver(MessageManager msgMgr) {
        this.msgMgrWr = new WeakReference<MessageManager>(msgMgr);
    }

    @Override
    public void onReceive(Context arg0, Intent arg1) {
        mgr = msgMgrWr.get();
        if (mgr == null) {
            Log.w(TAG, "Intent received, manager is null.");
            return;
        }

        Log.inf(TAG, "received intent in MessageReceiver, action:%s", arg1.getAction());
        mgr.wakeLock.lock("onReceive");

        final String action = arg1.getAction();
        if (Intents.ACTION_CHECK_MESSAGE_DB.equals(action)) {
            onManualRecheck();
        } else if (Intents.ACTION_REJECT_FILE_CONFIRMED.equals(action)){
            onRejectConfirmed(arg1);
        } else if (Intents.ACTION_ACCEPT_FILE_CONFIRMED.equals(action)) {
            onAcceptConfirmed(arg1);
        } else if (Intents.ACTION_SETTINGS_MODIFIED.equals(action)){
            onSettingsChanged(arg1);
        } else {
            Log.ef(TAG, "Unknown action %s", action);
        }

        mgr.wakeLock.unlock();
    }

    /**
     * Settings changed by user.
     * @param intent
     */
    private void onSettingsChanged(Intent intent){
        mgr.onSettingsChanged();
    }

    /**
     * Triggers manual database re-check.
     */
    private void onManualRecheck() {
        Log.v(TAG, "DB check triggered via intent");
        mgr.dbCheckTask(false);
        mgr.triggerCheck();
    }

    /**
     * Reacts on file reject request.
     * @param intent
     */
    private void onRejectConfirmed(Intent intent){
        if (!intent.hasExtra(Intents.EXTRA_REJECT_FILE_CONFIRMED_MSGID)){
            Log.e(TAG, "Intent does not have desired extra value");
            return;
        }

        long msgId = intent.getLongExtra(Intents.EXTRA_REJECT_FILE_CONFIRMED_MSGID, -1);
        mgr.onRejectConfirmed(msgId);
    }

    /**
     * Reacts on file accept request
     * @param intent
     */
    private void onAcceptConfirmed(Intent intent){
        if (!intent.hasExtra(Intents.EXTRA_ACCEPT_FILE_CONFIRMED_MSGID)){
            Log.e(TAG, "Intent does not have desired extra value");
            return;
        }

        long msgId = intent.getLongExtra(Intents.EXTRA_ACCEPT_FILE_CONFIRMED_MSGID, -1);
        mgr.onAcceptConfirmed(msgId);
    }
}

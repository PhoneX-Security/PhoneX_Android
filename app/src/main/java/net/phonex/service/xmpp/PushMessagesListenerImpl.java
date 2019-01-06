package net.phonex.service.xmpp;

import net.phonex.core.MemoryPrefManager;
import net.phonex.pref.PreferencesManager;
import net.phonex.service.SvcRunnable;
import net.phonex.service.XService;
import net.phonex.service.xmpp.customIq.CListSyncPushMessage;
import net.phonex.service.xmpp.customIq.DhUsePushMessage;
import net.phonex.service.xmpp.customIq.LicenseCheckPushMessage;
import net.phonex.service.xmpp.customIq.NewCertPushMessage;
import net.phonex.service.xmpp.customIq.PairingRequestPushMessage;
import net.phonex.service.xmpp.customIq.PushMessage;
import net.phonex.util.Log;

/**
 * Listener for received XMPP messages
 * Created by miroc on 31.8.15.
 */
public class PushMessagesListenerImpl implements IQPacketListener.PushMessagesListener{
    public static final String TAG = "PushMessagesListenerImpl";
    private XService xService;

    public PushMessagesListenerImpl(XService xService) {
        this.xService = xService;
    }

    @Override
    public void onCListSyncPush(final CListSyncPushMessage message) {
        Log.df(TAG, "onCListSyncPush; message [%s]", message);
        executeOnServiceHandler(() -> {
            if (isPushDuplicate(message, MemoryPrefManager.XMPP_PUSH_LAST_CLIST_FETCH_REQUEST)) {
                return;
            }

            final Long lastSync = MemoryPrefManager.getPreferenceLongValue(xService, PreferencesManager.LAST_CLIST_SYNC, 0l);

            // fetch contact list
            Log.vf(TAG, "onCListSyncPush, starting new clist sync, lastSync=%s, msgStamp=%s", lastSync, message.getTimestamp());
            xService.triggerContactListFetch();

            // run certificate update for all users
//                    ArrayList<CertUpdateParams> emptyList = new ArrayList<>();
//                    svc.triggerCertUpdate(emptyList, true);
        });
    }

    @Override
    public void onDhUse(final DhUsePushMessage message) {
        Log.df(TAG, "onDhUse; message [%s]", message);
        executeOnServiceHandler(() -> {
            if (isPushDuplicate(message, MemoryPrefManager.XMPP_PUSH_LAST_DH_USE)) {
                return;
            }
            XService.triggerDHKeyUpdate(xService.getApplicationContext());

        });
    }

    @Override
    public void onNewCertificatePush(final NewCertPushMessage pushMessage) {
        Log.df(TAG, "onNewCertificatePush; message [%s]", pushMessage);
        executeOnServiceHandler(() -> {
            if (isPushDuplicate(pushMessage, MemoryPrefManager.XMPP_PUSH_LAST_NEW_CERTIFICATE)) {
                return;
            }
            xService.triggerCertificateSelfCheck(pushMessage.getCertHashPrefix(), pushMessage.getCertNotBefore());
        });
    }

    @Override
    public void onLicenseCheckPush(final LicenseCheckPushMessage message) {
        Log.df(TAG, "onLicenseCheckPush; message [%s]", message);
        executeOnServiceHandler(() -> {
            if (isPushDuplicate(message, MemoryPrefManager.XMPP_PUSH_LAST_LICENSE_CHECK)) {
                return;
            }
            xService.triggerLicenseCheck();
        });
    }

    @Override
    public void onPairingRequestPush(final PairingRequestPushMessage message) {
        Log.df(TAG, "onPairingRequestPush; message [%s]", message);
        executeOnServiceHandler(() -> {
            if (isPushDuplicate(message, MemoryPrefManager.XMPP_PUSH_LAST_PAIRING_REQUEST)) {
                return;
            }
            xService.getPairingRequestManager().triggerPairingRequestFetch();
        });
    }

    private boolean isPushDuplicate(PushMessage message, String preferenceStringName){
        // first store push message time (when ack is lost, server resends push request, so we want to avoid doing the same action twice)
        long lastFetchRequestTime = Long.parseLong(MemoryPrefManager.getPreferenceStringValue(xService, preferenceStringName, "0"));
        if (message.getTimestamp() == lastFetchRequestTime){
            Log.inf(TAG, "isPushDuplicate; duplicate detected [%s]", preferenceStringName);
            // the same request, probably resend, drop
            return true;
        } else {
            Log.vf(TAG, "new pushMessage, ref=%s, time=%s", preferenceStringName, message.getTimestamp());
            MemoryPrefManager.setPreferenceStringValue(xService, preferenceStringName, String.valueOf(message.getTimestamp()));
        }
        return false;
    }

    private void executeOnServiceHandler(Runnable runnable){
        if (xService == null){
            Log.wf(TAG, "executeOnServiceHandler; xService is null");
            return ;
        }

        xService.getHandler().execute(new SvcRunnable("pushExec") {
            @Override
            protected void doRun() throws XService.SameThreadException {
                try {
                    runnable.run();
                } catch (Exception e) {
                    Log.ef(TAG, e, "Exception.");
                }
            }
        });

    }
}
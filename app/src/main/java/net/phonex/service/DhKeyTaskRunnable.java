package net.phonex.service;

import android.content.Context;
import android.content.Intent;

import net.phonex.core.Intents;
import net.phonex.core.MemoryPrefManager;
import net.phonex.pref.PhonexConfig;
import net.phonex.pref.PreferencesConnector;
import net.phonex.pub.parcels.KeyGenProgress;
import net.phonex.pub.parcels.StoredCredentials;
import net.phonex.soap.DHKeysSustainCall;
import net.phonex.soap.DHKeysSustainParams;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Task executes DH key task re-sync.
 * Should be executed in Alarm Handler Thread so only a single
 * running instance should exist at time.
 * <p/>
 * Automatically re-schedules this task on finish
 * after DEFAULT_DHKEYS_PERIODIC_REPEAT seconds
 * (If 0 automatic periodic repeat is disabled).
 *
 * @author ph4r05
 */
class DhKeyTaskRunnable extends SvcRunnable implements XService.KeyGenProgressUpdatable {
    private static final String TAG = "DHKeyTask";
    private final WeakReference<XService> sr;

    private DHKeysSustainCall task = null;
    private Context ctxt;
    private XService svc;

    DhKeyTaskRunnable(String name, XService sr) {
        super(name);
        this.sr = new WeakReference<XService>(sr);
    }

    DhKeyTaskRunnable(XService sr) {
        this.sr = new WeakReference<XService>(sr);
    }

    /**
     * Loads service from the weak reference. Returns true if everything is OK.
     *
     * @return
     */
    private boolean loadSvc() {
        this.svc = sr.get();
        if (this.svc == null) {
            Log.w(TAG, "Cannot load service, is null");
            return false;
        }

        return true;
    }

    @Override
    protected void doRun() throws XService.SameThreadException {
        if (!loadSvc()) {
            return;
        }

        try {
            entryPoint();
        } catch (Throwable t) {
            Log.e(TAG, "Exception ocurred during DHKeySync", t);
        }

        // Cancel notification.
        svc.getNotificationManager().cancelKeygen();
        svc.dhKeyTaskRunnable = null;
    }

    /**
     * Main entry point for DHKeySync.
     *
     * @throws net.phonex.service.XService.SameThreadException
     */
    protected void entryPoint() throws XService.SameThreadException {
        Log.d(TAG, "DHKey action started");
        ctxt = svc;

        // Load shared info from in-memory database
        StoredCredentials creds = MemoryPrefManager.loadCredentials(svc);
        if (creds.getUserSip() == null) {
            Log.w(TAG, "Cannot execute DH key task, empty credentials.");
            return;
        }

        // Clear progress map before task starts.
        svc.progressMap.clear();

        // Prepare task
        task = new DHKeysSustainCall(svc);
        task.setProgress(this);

        PreferencesConnector cprefsWrapper = new PreferencesConnector(svc);

        // Prepare parameters
        DHKeysSustainParams params = new DHKeysSustainParams();
        params.setDhkeys(cprefsWrapper.getInteger(PhonexConfig.DEFAULT_DHKEYS_PER_CONTACT));
        params.setCreds(creds);
        params.setExpireKeys(true);        // Expire old DH keys.

        // Execute task, may take a very long time.
        Log.i(TAG, "<DHKeyTask>");
        try {
            svc.getNotificationManager().notifyKeyGen();
            task.doTask(params);
        } catch (Throwable t) {
            Log.e(TAG, "Execution of DHKeySustain task went wrong", t);
        }

        // Mark all user progress meters as done, whatever is performed.
        for (Map.Entry<String, KeyGenProgress> e : svc.progressMap.entrySet()) {
            e.getValue().setState(KeyGenProgress.KeyGenStateEnum.DONE);
        }
        Log.i(TAG, "</DHKeyTask>");

        // Re-schedule the new run if is not already scheduled (may be scheduled sooner...)
        final int rescheduleSeconds = cprefsWrapper.getInteger(PhonexConfig.DEFAULT_DHKEYS_PERIODIC_REPEAT);
        if (rescheduleSeconds > 0 && svc.isDhKeyUpdateScheduled() == false) {
            Log.inf(TAG, "Triggering DHKey update in %s seconds.", rescheduleSeconds);
            svc.triggerDHKeyUpdate(rescheduleSeconds * 1000L);
        }
    }

    @SuppressWarnings("unused")
    public void cancel() {
        if (task == null) {
            Log.w(TAG, "Want to cancel task, but it is null");
            return;
        }

        task.manualCancel();
    }

    @Override
    public void updateDHProgress(KeyGenProgress progress) {
        svc.progressMap.put(progress.getUser(), progress);
        bcastState();
    }

    @Override
    public void updateDHProgress(List<KeyGenProgress> progress) {
        for (KeyGenProgress p : progress) {
            svc.progressMap.put(p.getUser(), p);
        }

        bcastState();
    }

    /**
     * Broadcast certificate update state.
     */
    public void bcastState() {
        final ArrayList<KeyGenProgress> cup = new ArrayList<KeyGenProgress>(svc.getDHKeyProgress());

        final Intent i = new Intent(Intents.ACTION_KEYGEN_UPDATE_PROGRESS);
        i.putParcelableArrayListExtra(Intents.KEYGEN_INTENT_PROGRESS, cup);
        MiscUtils.sendBroadcast(ctxt, i);
    }
}

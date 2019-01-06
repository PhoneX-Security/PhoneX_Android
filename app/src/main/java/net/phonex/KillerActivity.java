package net.phonex;

import android.content.ContentProviderClient;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;

import net.phonex.core.Intents;
import net.phonex.core.MemoryPrefManager;
import net.phonex.pref.PreferencesManager;
import net.phonex.pub.parcels.StoredCredentials;
import net.phonex.ui.intro.IntroActivity;
import net.phonex.ui.lock.activity.LockActivity;
import net.phonex.util.Base64;
import net.phonex.util.Log;
import net.phonex.util.crypto.CertificatesAndKeys;
import net.phonex.util.crypto.MessageDigest;
import net.phonex.util.system.ProcKiller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by ph4r05 on 7/13/14.
 */
public class KillerActivity extends LockActivity {
    private static final String THIS_FILE="KillerActivity";
    public static final String EXTRA_NONCE = "kill_potatoes";
    public static final String EXTRA_KILL_SVC = "kill_svc";
    public static final String EXTRA_KILL_UI = "kill_ui";
    public static final String EXTRA_KILL_SAFENET = "kill_safe";
    public static final String EXTRA_KILL_ERROR_REPORTER = "kill_error_reporter";
    public static final String EXTRA_START_UI = "start_ui";
    public static final String EXTRA_SALT = "kill_salat";
    public static final String EXTRA_IV = "kill_olives";

    private static final int STATE_INIT=0;
    private static final int STATE_AFTER_SNAPSHOT=1;
    private static final int STATE_PROCS_KILLED=80;
    private static final int STATE_FINAL=100;

    /**
     * Current step of the killing activity that was initiated.
     */
    public static final String CURRENT_STEP = "kill_step";

    private PreferencesManager prefs;

    private String decryptSalt="";
    private String decryptIv="";
    private boolean startUi = false;
    private int curStep = 0;
    private String curNonce = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PhonexSettings.loadDefaultLanguageNoThrow(this);

        Intent intent = getIntent();
        if (intent==null
                || Intents.ACTION_KILL.equals(intent.getAction())==false
                || intent.hasExtra(EXTRA_NONCE)==false
                || intent.hasExtra(EXTRA_SALT)==false
                || intent.hasExtra(EXTRA_IV)==false){
            Log.e(THIS_FILE, "Invalid call");
            finish();
            return;
        }

        prefs = new PreferencesManager(this.getApplicationContext());
        curNonce = intent.getStringExtra(EXTRA_NONCE);
        if (TextUtils.isEmpty(curNonce)){
            Log.e(THIS_FILE, "Invalid call 2");
            finish();
            return;
        }

        if (intent.hasExtra(EXTRA_START_UI)){
            startUi = intent.getBooleanExtra(EXTRA_START_UI, false);
        }

        // Current step of the killing process.
        curStep = STATE_INIT;
        setContentView(R.layout.apprestart);

        // Start kill task in the background.
        KillTask task = new KillTask();
        task.execute(intent);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        Log.v(THIS_FILE, "onRestore()");
    }

    @Override
    protected void onPause() {
        Log.v(THIS_FILE, "onPause()");
        moveToFinish();
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.v(THIS_FILE, "onStop()");
        moveToFinish();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.v(THIS_FILE, "onDestroy()");
        moveToFinish();
        super.onDestroy();

        // Kill current process anyway .
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    /**
     * On task finishes its killing spree.
     */
    private void onTaskFinished(){
        Log.v(THIS_FILE, "onTaskfinished");
        finish();
    }

    /**
     * Worker killer task, so as ANR won't kill this activity prematurely.
     */
    private class KillTask extends AsyncTask<Intent, Void, Void>{
        @Override
        protected Void doInBackground(Intent... params) {
            if (params==null || params.length==0){
                throw new IllegalArgumentException();
            }

            final Intent intent = params[0];
            Log.v(THIS_FILE, "Task started");

            // If the killing process got interrupted due to Android bug or something
            // do not cycle in the loop.
            String lastNonce = prefs.getString(EXTRA_NONCE);
            Integer lastStep = null;

            // Obtain the last step in integer format.
            if (curNonce.equals(lastNonce)){
                try {
                    lastStep = prefs.getInteger(CURRENT_STEP);
                    if (lastStep!=null){
                        Log.vf(THIS_FILE, "Recovering from old kill session, nonce [%s], lastStep [%s]", lastNonce, lastStep);
                        curStep = lastStep;
                    }
                } catch(Exception e){
                    Log.wf(THIS_FILE, "Exception in prefs.");
                }
            }

            // If the nonce is different, this is new killing session, reset step and start from
            // the beginning. Otherwise start where we stopped.
            if (curStep==STATE_INIT){
                Log.vf(THIS_FILE, "Starting new kill session, nonce [%s]", curNonce);
                prefs.setString(EXTRA_NONCE, curNonce);
                moveStep(STATE_INIT);
            }

            // Instruct SafeNet to store data to encrypted backup file, pass encryption
            // salt to the intro activity in intent. If decryption fails, remove file,
            // start as a clean service.
            // Safenet sends intent on successfull recovering from the saved state.
            deriveMaterial(intent);
            if (curStep==STATE_INIT){
                if (startUi){
                    memorySnapshot();
                }

                moveStep(STATE_AFTER_SNAPSHOT);
            }

            // Build list of processes to kill.
            List<String> procs = initProcList(intent);
            Log.vf(THIS_FILE, "Going to kill processes, size=%d, intent=0x%x, step=%d", procs.size(), intent.hashCode(), curStep);

            // Try to release content provider somehow.
            try {
                final ContentProviderClient contentProviderClient = getContentResolver().acquireContentProviderClient(MemoryPrefManager.AUTHORITY);
                if (contentProviderClient != null) {
                    Log.v(THIS_FILE, "Releasing content provider client.");
                    contentProviderClient.release();
                }
            } catch(Exception ex){
                Log.e(THIS_FILE, "Exception in releasing cp");
            }

            int killedProcesses = 0;
            final Map<String, Integer> pidMap = ProcKiller.getPids(procs);
            for(int procCounter = curStep-1; procCounter < procs.size(); procCounter++){
                // Move step here so sometimes this activity gets killed after killing some
                // process from the list.
                moveStep(STATE_AFTER_SNAPSHOT + 1 + procCounter);
                final String proc = procs.get(procCounter);

                if (pidMap.containsKey(proc)==false){
                    Log.vf(THIS_FILE, "Cannot kill process [%s], no corresponfind PID", proc);
                    continue;
                }

                try {
                    Integer pid = (Integer) pidMap.get(proc);
                    Log.vf(THIS_FILE, "Going to kill process [%s] pid: [%s]", proc, pid);
                    //Runtime.getRuntime().exec("kill -9 " + pid).waitFor();
                    android.os.Process.killProcess(pid);
                    //android.os.Process.sendSignal(pid, android.os.Process.SIGNAL_KILL);
                    Log.vf(THIS_FILE, "Process [%s] pid: [%s] killed", proc, pid);
                    killedProcesses+=1;

                } catch(Exception e){
                    Log.e(THIS_FILE, "Kill was not successful", e);
                }
            }

            Log.vf(THIS_FILE, "Processes killed [%d]", killedProcesses);
            moveStep(STATE_PROCS_KILLED);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            onTaskFinished();
        }

        @Override
        protected void onCancelled() {
            Log.v(THIS_FILE, "Cancelled?");
            onTaskFinished();
        }
    }

    /**
     * Moves state to the final state & starts PhoneX if desired.
     */
    private void moveToFinish(){
        Log.vf(THIS_FILE, "Moving to final state, step: %d", curStep);

        // Start IntroActivity for user to login.
        // Active only if
        if (curStep < STATE_FINAL && curStep >= STATE_PROCS_KILLED) {
            if (startUi) {
                startPhonex();
            }

            // Move to final state
            moveStep(STATE_FINAL);

            // Kill current process.
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    /**
     * Derives cryptographic material for snapshot.
     */
    private void deriveMaterial(Intent intent) {
        if (intent.hasExtra(EXTRA_SALT)==false || intent.hasExtra(EXTRA_IV)==false){
            throw new SecurityException("Invalid intent");
        }

        String tmpString;
        byte tmpByte[];

        String intentSalt = intent.getStringExtra(EXTRA_SALT);
        String intentIV = intent.getStringExtra(EXTRA_IV);
        String deviceDesc = CertificatesAndKeys.getDeviceId(this.getApplicationContext());
        if (TextUtils.isEmpty(intentSalt)
            || TextUtils.isEmpty(intentIV)
            || intentSalt.length() < 12
            || intentIV.length() < 12){
            throw new SecurityException("Invalid intent 2");
        }

        // Derive salt.
        try {
            decryptSalt = Base64.encodeBytes(MessageDigest.hashSha256(curNonce + "|" + intentSalt + "|PhoneXSnapshot_cherry|" + deviceDesc + "|"));
            decryptIv = Base64.encodeBytes(MessageDigest.hashSha256(curNonce + "|" + intentIV + "|PhoneXSnapshot_mango|" + deviceDesc + "|"));
        } catch(Exception ex){
            Log.e(THIS_FILE, "Hashing error", ex);
            throw new RuntimeException("Cannot continue");
        }
    }

    /**
     * Moves kill step forward.
     * @param step
     */
    private void moveStep(int step){
        Log.vf(THIS_FILE, "Moving to step [%d]", step);
        this.curStep = step;
        this.prefs.setString(CURRENT_STEP, String.valueOf(curStep));
    }

    /**
     * Starts PhoneX, signalize killer activity took place.
     */
    private void startPhonex(){
        Intent phonexIntent = new Intent(this, IntroActivity.class);
        phonexIntent.setAction("android.intent.category.LAUNCHER");
        phonexIntent.putExtra(IntroActivity.EXTRA_CANCEL_NOTIFS, true);
        phonexIntent.putExtra(IntroActivity.EXTRA_JUST_KILLED, true);
        phonexIntent.putExtra(IntroActivity.EXTRA_DECRYPT_SALT, decryptSalt);
        phonexIntent.putExtra(IntroActivity.EXTRA_DECRYPT_IV, decryptIv);
        phonexIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        phonexIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        phonexIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        Log.v(THIS_FILE, "Starting PhoneX activity");
        startActivity(phonexIntent);
    }

    /**
     * Initializes list of processes to kill based on the intent provided.
     * @param intent
     * @return
     */
    private List<String> initProcList(Intent intent){
        List<String> procs = new ArrayList<String>(4);

        if (intent.hasExtra(EXTRA_KILL_SVC) && intent.getBooleanExtra(EXTRA_KILL_SVC, false)){
            procs.add("net.phonex:service");
        }

        if (intent.hasExtra(EXTRA_KILL_UI) && intent.getBooleanExtra(EXTRA_KILL_UI, false)){
            procs.add("net.phonex");
        }

        if (intent.hasExtra(EXTRA_KILL_ERROR_REPORTER) && intent.getBooleanExtra(EXTRA_KILL_ERROR_REPORTER, false)){
            procs.add("net.phonex:CrashHandler");
        }

        if (intent.hasExtra(EXTRA_KILL_SAFENET) && intent.getBooleanExtra(EXTRA_KILL_SAFENET, false)){
            procs.add("net.phonex:safeNet");
        }

        return procs;
    }

    /**
     * Performs snapshoting of the in-memory preferences to a temporary encrypted
     * snapshot file.
     */
    private void memorySnapshot(){
        if (TextUtils.isEmpty(decryptSalt) || TextUtils.isEmpty(decryptIv)){
            throw new RuntimeException("Cannot continue");
        }

        final StoredCredentials creds = MemoryPrefManager.loadCredentials(this);
        if (creds!=null && TextUtils.isEmpty(creds.getUsrPass())==false){
            Log.v(THIS_FILE, "Going to store memory snapshot");

            MemoryPrefManager.saveSnapshot(this, decryptSalt, decryptIv);
            Log.v(THIS_FILE, "Memory snapshot finished");
        } else {
            Log.v(THIS_FILE, "Nothing to snapshot, empty memory storage");
        }
    }

    @Override
    protected String activityAnalyticsName() {
        return this.getClass().getSimpleName();
    }
}

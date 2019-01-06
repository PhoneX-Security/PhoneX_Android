package net.phonex.util.system;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import net.phonex.KillerActivity;
import net.phonex.core.Intents;
import net.phonex.util.Base64;
import net.phonex.util.Log;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by ph4r05 on 7/13/14.
 */
public class ProcKiller {
    private static final String THIS_FILE="ProcKiller";

    /**
     * Kill calling process by SIGKILL.
     */
    public static void killCurrentProcess(){
        final int pid = android.os.Process.myPid();
        final int tid = android.os.Process.myTid();
        Log.vf(THIS_FILE, "Going to sigkill process by sending signal to PID=%s; TID=%s", pid, tid);

        try {
            //Runtime.getRuntime().exec("kill -9 " + pid).waitFor();
            android.os.Process.killProcess(pid);
            Log.ef(THIS_FILE, "Should not reach this code! PID: %s", pid);

        } catch(Exception e){
            Log.e(THIS_FILE, "Kill was not successful", e);
        }
    }

    /**
     * Helper method to kill process for particular package.
     * @param packageName
     */
    public static void killPackageProcesses(Context ctxt, String packageName) {
        try {
            ActivityManager am = (ActivityManager) ctxt.getSystemService(Context.ACTIVITY_SERVICE);
            am.killBackgroundProcesses(packageName);
        } catch (Exception e){
            Log.e(THIS_FILE, "Exception in killing routine", e);
        }
    }

    /**
     * Returns PIDs of selected processes.
     *
     * @param processes
     * @return
     */
    public static Map<String, Integer> getPids(List<String> processes){
        Map<String, Integer> pidMap = new HashMap<String, Integer>();
        if (processes==null || processes.isEmpty()){
            return pidMap;
        }

        try {
            CmdExecutor.CmdExecutionResult res = CmdExecutor.execute("ps", CmdExecutor.OutputOpt.EXECUTE_STDOUT_ONLY);
            if (res.exitValue!=0 || TextUtils.isEmpty(res.stdOut)){
                Log.ef(THIS_FILE, "Cannot use ps command to obtain service pid, exitVal=%s", res.exitValue);
                return pidMap;
            }

            // Read ps output line by line and find line corresponding to phonex service.
            String psLines[] = res.stdOut.split("\\r?\\n");
            for(String line : psLines){
                line = line.trim();
                if (TextUtils.isEmpty(line)){
                    continue;
                }

                String lineElems[] = line.split("\\s+\\t*");
                if (lineElems.length<=1){
                    Log.ef(THIS_FILE, "Invalid PS line: %s", line);
                    continue;
                }

                // Last element is a process name
                final String procName = lineElems[lineElems.length-1].trim();
                if (processes.contains(procName)==false){
                    continue;
                }

                // 2nd element is PID.
                Integer pid = null;
                try {
                    pid = Integer.parseInt(lineElems[1]);
                    pidMap.put(procName, pid);
                } catch(Exception e){
                    Log.e(THIS_FILE, "Problem with integer parsing", e);
                }
            }
        } catch (Exception e){
            Log.e(THIS_FILE, "Exception: Cannot run ps, exception: ", e);
        }

        return pidMap;
    }

    /**
     * Kill specified process.
     * @param process
     */
    public static int killProcess(String process){
        List<String> procs = new ArrayList<String>(1);
        procs.add(process);
        return killProcesses(procs);
    }

    /**
     * Kill processes in he given order.
     * @param procs
     */
    public static int killProcesses(List<String> procs){
        int killedProcesses = 0;
        final Map<String, Integer> pidMap = getPids(procs);
        for(String proc : procs){
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

        return killedProcesses;
    }

    /**
     * Kills phonex services by starting a Killer activity.
     * @param ctxt
     * @param killSafeNet
     * @param killSvc
     * @param killUi
     * @param killReport
     * @param startUi
     */
    public static void killPhoneX(Context ctxt, boolean killSafeNet, boolean killSvc, boolean killUi, boolean killReport, boolean startUi){
        // Generate random nonce to identify killing session.
        byte tmpB[] = new byte[32];
        SecureRandom rnd = new SecureRandom();
        rnd.nextBytes(tmpB);
        final String tmpNonce = Base64.encodeBytes(tmpB);

        // Generate salt & iv randomly.
        byte[] tmpByte = new byte[32];
        SecureRandom rand = new SecureRandom();

        rand.nextBytes(tmpByte);
        String decryptSalt = Base64.encodeBytes(tmpByte);

        rand.nextBytes(tmpByte);
        String decryptIv = Base64.encodeBytes(tmpByte);

        // Build intent and exit.
        final Intent intent = new Intent(Intents.ACTION_KILL);
        intent.putExtra(KillerActivity.EXTRA_KILL_SAFENET, killSafeNet);
        intent.putExtra(KillerActivity.EXTRA_KILL_SVC, killSvc);
        intent.putExtra(KillerActivity.EXTRA_KILL_UI, killUi);
        intent.putExtra(KillerActivity.EXTRA_KILL_ERROR_REPORTER, killReport);
        intent.putExtra(KillerActivity.EXTRA_START_UI, startUi);

        intent.putExtra(KillerActivity.EXTRA_NONCE, tmpNonce);
        intent.putExtra(KillerActivity.EXTRA_SALT, decryptSalt);
        intent.putExtra(KillerActivity.EXTRA_IV, decryptIv);

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setPackage(ctxt.getPackageName());
        ctxt.startActivity(intent);
    }

}

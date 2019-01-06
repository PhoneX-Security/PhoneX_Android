package net.phonex.ui.intro;

import android.content.Intent;

import net.phonex.R;
import net.phonex.core.Intents;
import net.phonex.db.DBHelper;
import net.phonex.db.DBProvider;
import net.phonex.pub.parcels.GenericError;
import net.phonex.pub.parcels.GenericTaskProgress;
import net.phonex.soap.BaseLoginRunnable;
import net.phonex.soap.LoginCancelledBroadcastReceiver;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;

/**
* Task that checks encrypted database key - required for encrypting the database.
* @author ph4r05
*/
public class DbCheckKeyTask extends BaseLoginRunnable implements LoginCancelledBroadcastReceiver.LoginCancelledListener {
    public final static String TAG = "DbCheckKeyTask";
    private OnDbKeyCheckCompleted callback = null;
    private int checkResult = 0;

    private final String sip;
    private final String password;

    public interface OnDbKeyCheckCompleted{
        void onDbKeyCheckCompleted(int checkResult);
    }

    @Override
    public void loginCancelled() {
        cancel();
    }

    public DbCheckKeyTask(OnDbKeyCheckCompleted c, String sip, String password){
        this.callback = c;
        this.sip = sip;
        this.password = password;
    }

    @Override
    public void run() {
        LoginCancelledBroadcastReceiver receiver = new LoginCancelledBroadcastReceiver(this);
        receiver.register(context);

        Exception result = runInternal();

        context.unregisterReceiver(receiver);

        if (!isCancelled()) {
            onPostExecute(result);
        }
    }

    protected Exception runInternal() {

        Log.v(TAG, "Going to test DB encryption key");
        try {
            publishProgress(getContext().getString(R.string.p_testing_db));
            // first close database if not closed before
            DBProvider.closeDb(getContext().getContentResolver());

            // Set encryption password at first (this may also create a new encrypted db if not created before)
            final String dbPassword = DBHelper.DatabaseHelper.formatDbPassword(sip, password);
            DBProvider.setEncKey(getContext().getContentResolver(), dbPassword, sip);
            // Check if it is correct.
            checkResult = DBProvider.testEncKey(getContext().getContentResolver(), true);
            Log.vf(TAG, "DB check completed; result=%s", checkResult);
        } catch(Exception e){
            Log.e(TAG, "Exception in DB enc key testing task", e);
            return e;
        }
        return null;
    }

    public void publishProgress(String message) {
        Intent intent = new Intent(Intents.ACTION_LOGIN_PROGRESS);
        intent.putExtra(Intents.EXTRA_GENERIC_PROGRESS, GenericTaskProgress.progressInstance(message));
        MiscUtils.sendBroadcast(context, intent);
    }

    protected void onPostExecute(Exception result) {
        if (result == null){
            Log.vf(TAG, "Database password checked, result=%s", checkResult);

            int errResId = R.string.p_problem_db_general;

            if (checkResult <= 0) {
                publishError(GenericError.GENERIC_ERROR, getContext().getString(errResId));
            }

            if (callback != null){
                callback.onDbKeyCheckCompleted(checkResult);
            }
        } else {
            int errResId = R.string.p_problem_nonspecific;
            Log.w(TAG, "onPostExecute(null)");
            publishError(GenericError.GENERIC_ERROR, getContext().getString(errResId));
        }
    }

    private void publishError(GenericError error, String message) {
        Intent intent = new Intent(Intents.ACTION_LOGIN_PROGRESS);
        intent.putExtra(Intents.EXTRA_GENERIC_PROGRESS, GenericTaskProgress.errorInstance(error, message));
        MiscUtils.sendBroadcast(context, intent);
    }
}

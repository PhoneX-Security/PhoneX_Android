package net.phonex.soap;

import android.content.Intent;

import net.phonex.R;
import net.phonex.core.Intents;
import net.phonex.pub.parcels.GenericError;
import net.phonex.pub.parcels.GenericTaskProgress;
import net.phonex.ui.dialogs.AlertDialogFragment;
import net.phonex.util.MiscUtils;

/**
 * Async task for fetching contact list and storing to content manager
 */
public class ClistFetchTask extends BaseLoginRunnable implements LoginCancelledBroadcastReceiver.LoginCancelledListener, DefaultSOAPCall.ProgressEventListener {
	public final static String TAG = "ClistFetchTask";

    public ClistFetchTask(OnClistFetchTaskCompleted listener, ClistFetchParams params){
		onCompletedListener = listener;
		this.params = params;
	}

	@Override
	public void run() {
		init();
		Exception result = runInternal(params);
		if (!isCancelled()) {
			onPostExecute(result);
		}
	}

	public interface OnClistFetchTaskCompleted{
		void onClistFetchTaskCompleted(boolean success, Object result, Object error);
	}
	private OnClistFetchTaskCompleted onCompletedListener;

	private LoginCancelledBroadcastReceiver receiver;

	private ClistFetchParams params;

	@Override
	public void loginCancelled() {
		// do not allow to cancel this task, because it may lead to inconsistent state
		//cancel(true);
	}

	protected Exception runInternal(ClistFetchParams... arg0) {
		if (arg0.length==0){
			throw new IllegalArgumentException("Empty configuration");
		}
		ClistFetchParams par = arg0[0];

        ClistFetchCall call = new ClistFetchCall(getContext(), par);
        call.setProgressEventListener(this);
        call.run();

        return call.getThrownException();
    }

	@Override
	public void publishProgress(String... values) {
		Intent intent = new Intent(Intents.ACTION_LOGIN_PROGRESS);
		intent.putExtra(Intents.EXTRA_GENERIC_PROGRESS, GenericTaskProgress.progressInstance(values != null && values.length > 0 ? values[0] : null));
		MiscUtils.sendBroadcast(context, intent);
	}

	protected void init() {
		receiver = new LoginCancelledBroadcastReceiver(this);
		receiver.register(context);
	}

	protected void onPostExecute(Exception result) {

		context.unregisterReceiver(receiver);
    	//if (mFragment==null) return;
    	
		if (result == null){
			if (onCompletedListener!=null) {
				onCompletedListener.onClistFetchTaskCompleted(true, null, null);
			}
		
		} else {
			publishError(GenericError.GENERIC_ERROR, getContext().getString(R.string.p_problem_nonspecific));

			// Trigger error to the server, this is bug
            MiscUtils.reportExceptionToAcra(result);

			if (onCompletedListener!=null) {
				onCompletedListener.onClistFetchTaskCompleted(false, null, result);
			}
		}
	}

	private void publishError(GenericError error, String message) {
		Intent intent = new Intent(Intents.ACTION_LOGIN_PROGRESS);
		intent.putExtra(Intents.EXTRA_GENERIC_PROGRESS, GenericTaskProgress.errorInstance(error, message));
		MiscUtils.sendBroadcast(context, intent);
	}
}


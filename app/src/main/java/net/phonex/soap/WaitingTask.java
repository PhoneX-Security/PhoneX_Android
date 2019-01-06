package net.phonex.soap;

import android.content.Intent;

import net.phonex.core.Intents;
import net.phonex.pub.parcels.GenericTaskProgress;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;


public class WaitingTask extends BaseLoginRunnable implements LoginCancelledBroadcastReceiver.LoginCancelledListener {
	public final static String TAG = "WaitingTask";
	public final static int CHECKPOINTS_NUMBER = 1; //how many progress checkpoints it needs

	@Override
	public void run() {
		init();
		Exception result = runInternal(params);
		if (!isCancelled()) {
			onPostExecute(result);
		}
	}

	// Callback interface.
	public interface OnWaitTaskCompleted{
		void onWaitTaskCompleted(Object userData, boolean cancelled);
	}
	private OnWaitTaskCompleted listener;
	private Object userData;

	private WaitingTaskParams params;

	public WaitingTask(WaitingTaskParams params){
		this.params = params;
	}

	private LoginCancelledBroadcastReceiver receiver;

	@Override
	public void loginCancelled() {
		cancel();
	}

	protected String getWaitingText(WaitingTaskParams p, long diff){
		if (p.reasonAddRemainingSeconds){
			return String.format(p.reasonText, (int) Math.ceil(diff/1000.0));
		} else {
			return p.reasonText;
		}
	}

	protected Exception runInternal(WaitingTaskParams... arg0) {
		if (arg0==null || arg0.length==0){
			return null;
		}
		
		final WaitingTaskParams p = arg0[0];
		
		// Main thing this task does - waits...
		try {
			final long timeStart = System.currentTimeMillis();
			final long timeStop  = timeStart + p.milli;
			long timeLastNotif = 0;
			
			// If there is some label, set it.
			if (p.reasonText!=null){
				publishProgress(/*new BaseAsyncProgress(*/getWaitingText(p, p.milli)/*, 0)*/);
			}
			
			// Loop - user may cancel waiting
			while(true){
				final long timeCurr = System.currentTimeMillis();
				if (timeCurr >= timeStop){
					break;
				}
				
				// User may cancelled waiting.
				if (isCancelled()){
					return null;
				}
				
				// Notification for user
				if (p.reasonText!=null && p.reasonAddRemainingSeconds && (timeCurr - timeLastNotif) >= 950){
					timeLastNotif = timeCurr;
					publishProgress(/*new BaseAsyncProgress(*/getWaitingText(p, timeStop - timeCurr)/*, 0)*/);
				}
						
				Thread.sleep(100);
			}
			
		} catch(Exception ex){
			Log.e(TAG, "Sleep interrupted", ex);
		}
		
		return null;
	}

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

    	if (listener!=null){
    		listener.onWaitTaskCompleted(userData, false);
    	}
	}

	/**
	 * @return the listener
	 */
	public OnWaitTaskCompleted getListener() {
		return listener;
	}

	/**
	 * @param listener the listener to set
	 */
	public void setListener(OnWaitTaskCompleted listener) {
		this.listener = listener;
	}

	/**
	 * @return the userData
	 */
	public Object getUserData() {
		return userData;
	}

	/**
	 * @param userData the userData to set
	 */
	public void setUserData(Object userData) {
		this.userData = userData;
	}
}

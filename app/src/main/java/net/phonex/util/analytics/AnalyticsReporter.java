package net.phonex.util.analytics;

import android.app.Activity;
import android.app.Fragment;
import android.app.Service;
import android.content.Context;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import net.phonex.PhoneX;
import net.phonex.util.Log;

/**
 * Google analytics helper object
 * 
 * @author miroc
 *
 */
public class AnalyticsReporter {
	private static final String TAG = "AnalyticsReporter";

	public static Tracker getTracker(Activity activity){
		return ((PhoneX ) activity.getApplication()).getDefaultTracker();
	}

	private final Tracker tracker;

	private AnalyticsReporter(Tracker tracker){
		this.tracker = tracker;
	}

	/**
	 * This may fail easily and may be slow!
	 * @return
	 */
	public static AnalyticsReporter fromApplicationContext(Context context){
		if (context != null && context instanceof PhoneX){
			PhoneX app = (PhoneX) context;
			return new AnalyticsReporter(app.getDefaultTracker());
		} else {
			if (context==null){
				Log.ef(TAG, "fromApplicationContext; Cannot properly initialize analytics utils, context is null");
			}else {
				Log.ef(TAG, "fromApplicationContext; Cannot properly initialize analytics utils, context is not Phonex object instance, rather %s instance", context.getClass().getName());
			}
		}
		return new AnalyticsReporter(null);
	}

	public static AnalyticsReporter from(Activity activity){
		if (activity == null){
			Log.ef(TAG, "Cannot properly initialize analytics utils, activity is null");
			return new AnalyticsReporter(null);
		}

		return new AnalyticsReporter(((PhoneX) activity.getApplication()).getDefaultTracker());
	}

	public static AnalyticsReporter from(Fragment fragment){
		Activity activity = fragment.getActivity();
		if (activity == null){
			Log.ef(TAG, "Cannot properly initialize analytics utils, activity is null");
			return new AnalyticsReporter(null);
		}

		return new AnalyticsReporter(((PhoneX) activity.getApplication()).getDefaultTracker());
	}

	public static AnalyticsReporter from(Service service){
		if (service == null){
			Log.ef(TAG, "Cannot properly initialize analytics utils, activity is null");
			return new AnalyticsReporter(null);
		}

		return new AnalyticsReporter(((PhoneX) service.getApplication()).getDefaultTracker());
	}

	public void passiveEvent(AppPassiveEvents event){
		if (tracker!=null){
			AnalyticsReporter.passiveEvent(tracker, event);
		}
	}

	public void event(AppEvents event){
		if (tracker!=null){
			AnalyticsReporter.event(tracker, event);
		}
	}

	public void buttonClick(AppButtons button){
		if (tracker!=null){
			AnalyticsReporter.buttonClick(tracker, button);
		}
	}


	/**
	 * Send passive event
	 * @param tracker
	 * @param event
	 */
	public static void passiveEvent(Tracker tracker, AppPassiveEvents event){
		tracker.send(new HitBuilders.EventBuilder()
				.setCategory(AnalyticsCategories.PASSIVE_EVENT.toString())
				.setAction(event.toString())
				.build()
		);
	}

	/**
	 * Send active event (aka action)
	 * @param tracker
	 * @param event
	 */
	public static void event(Tracker tracker, AppEvents event){
		tracker.send(new HitBuilders.EventBuilder()
						.setCategory(AnalyticsCategories.EVENT.toString())
						.setAction(event.toString())
						.build()
		);
	}

	/**
	 * Send button click action
	 * @param tracker
	 * @param button
	 */
	public static void buttonClick(Tracker tracker, AppButtons button){
		tracker.send(new HitBuilders.EventBuilder()
						.setCategory(AnalyticsCategories.BUTTON_CLICK.toString())
						.setAction(button.toString())
						.build()
		);
	}

	// TODO view pager switch

}

package net.phonex.service;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.text.TextUtils;

import net.phonex.PhonexSettings;
import net.phonex.R;
import net.phonex.pref.PhonexConfig;
import net.phonex.db.entity.SipProfile;
import net.phonex.db.entity.SipProfileState;
import net.phonex.pref.PreferencesManager;
import net.phonex.pub.proto.PushNotifications.PresencePush;
import net.phonex.pub.proto.PushNotifications.PresencePush.Status;
import net.phonex.ft.ProtoBuffHelper;
import net.phonex.util.Base64;
import net.phonex.util.Log;
import net.phonex.util.Registerable;
import net.phonex.util.analytics.AnalyticsReporter;
import net.phonex.util.analytics.AppEvents;
import net.phonex.util.guava.MapUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * Class managing current user presence. Presence for contacts in contact list is managed in {@link ContactsManager}
 *
 * @author ph4r05
 */
public class MyPresenceManager implements Registerable{
	private static final String TAG = "PresenceManager";

    public final static List<Integer> ACTIVABLE_PRESENCE_STATUSES = new ArrayList<Integer>(){
        {
            add(Status.ONLINE_VALUE);
            add(Status.AWAY_VALUE);
            add(Status.OFFLINE_VALUE);
        }
    };

    private static final PresenceData ONLINE_PRESENCE_DATA =  new PresenceData(R.string.presence_online, android.R.drawable.presence_online);
    private static final PresenceData AWAY_PRESENCE_DATA =  new PresenceData(R.string.presence_away, android.R.drawable.presence_away);
    private static final PresenceData OFFLINE_PRESENCE_DATA =  new PresenceData(R.string.presence_invisible, android.R.drawable.presence_offline);
    private static final PresenceData ONCALL_PRESENCE_DATA =  new PresenceData(R.string.presence_invisible, android.R.drawable.presence_busy);

    private static final Map<Integer, PresenceData> PRESENCE_MAP = MapUtils.mapOf(
            Status.ONLINE_VALUE, ONLINE_PRESENCE_DATA,
            Status.AWAY_VALUE, AWAY_PRESENCE_DATA,
            Status.DEVSLEEP_VALUE, AWAY_PRESENCE_DATA,
            Status.OFFLINE_VALUE, OFFLINE_PRESENCE_DATA,
            Status.ONCALL_VALUE, ONCALL_PRESENCE_DATA
    );

	/**
	 * XService handle.
	 * Used to obtain context and call services.
	 */
	private XService svc;

	public MyPresenceManager(XService svc) {
		this.svc = svc;
	}

    /**
     * update current presence state
     * @param context
     * @param accountId SipProfileState id
     * @return
     */
    public static synchronized int updatePresenceState(final Context context, long accountId, int presenceValue){
        AnalyticsReporter.fromApplicationContext(context.getApplicationContext()).event(AppEvents.STATUS_CHANGED);

        ContentValues cv = new ContentValues();
        cv.put(SipProfileState.STATUS_TYPE, presenceValue);
        int res = context.getContentResolver().update(ContentUris.withAppendedId(SipProfile.ACCOUNT_STATUS_ID_URI_BASE, accountId), cv, null, null);
        Log.vf(TAG, "Updated sipProfileState presence, accountId: %d, statusType: %d", accountId, presenceValue);
        return res;
    }

    /**
     * retrieve current state stored in SipProfileState
     * @param context
     * @param accountId SipProfileState id
     * @return
     */
    public static Status getPresenceState(final Context context, long accountId){
        final SipProfileState profileState = SipProfileState.getById(context, null, accountId);
        if (profileState == null){
            Log.ef(TAG, "SipProfileState is null for accountId=%d, cannot retrieve presence state - returning offline presence instead.", accountId);
            return Status.OFFLINE;
        } else {
            Log.vf(TAG, "checking presence state, profileState=[%s]", profileState.toString());
            return Status.valueOf(profileState.getStatusType());
        }
    }

    /**
     * check if account is registered in the network or not
     *
     * @return
     */
    public static boolean checkRegistrationState(final Context context, long accountId) {
        final SipProfileState profileState = SipProfileState.getById(context, null, accountId);
        if (profileState == null){
            return false;
        }
        return profileState.isValidForCall();
    }

    /**
     * Specific method for setting in call state for the particular user.
     * Global preferences are checked - setting in call state has to be allowed.
     *
     * @param ctxt
     * @param accountId
     * @param onCall
     */
    public static int changeOnCallState(final Context ctxt, long accountId, boolean onCall){
        PreferencesManager prefs = new PreferencesManager(ctxt);

        // Has to be here because in case of a disabled pub. option (privacy),
        // it would lead to publishing new state information -> information leak
        // about call in progress.
        if (prefs.getBoolean(PhonexConfig.PUBLISH_IN_CALL_STATE)){

            ContentValues cv = new ContentValues();
            cv.put(SipProfileState.IN_CALL, onCall);

            int res = SipProfileState.update(ctxt, accountId, cv);
            Log.vf(TAG, "Updated inCall state; %s; onCall: %s", res, onCall);

            return res;
        } else {
            Log.v(TAG, "InCall state is private.");
            return -1;
        }
    }

    /**
     * Retrieve icon for particular status value, takes ProfileState into account (if not valid for call, we retrieve contact icon as offline)
     * @param ctx
     * @param statusValue
     * @return
     */
    public static int getStatusIcon(Context ctx, int statusValue){
        if (ctx == null){
            return OFFLINE_PRESENCE_DATA.icon;
        }

        SipProfileState state = SipProfileState.getProfileState(ctx, SipProfile.USER_ID);
        // in case of state not being valid for call or not initialized yet, display all contacts as offline
        if (state == null || !state.isValidForCall()){
            return OFFLINE_PRESENCE_DATA.icon;
        } else {
            return getStatusIcon(statusValue);
        }
    }

    public static int getStatusIcon(int statusValue){
        PresenceData presenceItem = PRESENCE_MAP.get(statusValue);
        // in case no such status is yet mapped to icon, return ONLINE icon
        return presenceItem!=null ? presenceItem.icon : ONLINE_PRESENCE_DATA.icon;
    }

    public static int getOfflineStatusIcon(){
        return OFFLINE_PRESENCE_DATA.icon;
    }

    public static int getStatusText(int statusValue){
        PresenceData presenceItem = PRESENCE_MAP.get(statusValue);
        // in case no such status is yet mapped to text, return ONLINE text
        return presenceItem!=null ? presenceItem.text : ONLINE_PRESENCE_DATA.text;
    }

    /**
	 * Initialization of the module.
	 */
	public void register(){
	}

	/**
	 * Deinitialization  of the module.
	 */
	public void unregister(){
    }

    /**
	 * Generates status text using special format for push notifications (protocol buffers structure).
	 *
	 * @param account
	 * @param profileState
	 * @return
	 */
	public String generatePresenceText(SipProfile account, SipProfileState profileState){
		try {
			PresencePush.Builder b = PresencePush.newBuilder();

			// Setting things to tell to the world.
			b.setVersion(1);
            // by default or if not available for call, propagate it as offline
            b.setStatusText("");
            b.setStatus(Status.OFFLINE);
            b.setSipRegistered(false);

			// Certificate related data from stored profile.
			if (account!=null){
				// Certificate freshness, cert hash add only few characters - prefix.
				final String cHash = account.getCert_hash();
				if (!TextUtils.isEmpty(cHash)){
					final int cHashLen = cHash.length();

					b.setCertHashShort(cHash.substring(0, cHashLen < 10 ? cHashLen : 10));
				}

				// Certificate created date (validity starts).
				if (account.getCert_not_before()!=null){
					b.setCertNotBefore(account.getCert_not_before().getTime());
				}
			}

			// Status text and type from in-memory state.
			if (account != null && profileState!=null){
                // SIP registered state (if available for call).
                if (profileState.isValidForCall()) {
                    final String statusText = profileState.getStatusText();
                    if (!TextUtils.isEmpty(statusText)){
                        b.setStatusText(statusText);
                    }
                    // Extended status
                    try {
                        b.setStatus(Status.valueOf(profileState.getStatusType()));
                        b.setSipRegistered(true);

                        // In-call status - only if allowed in prefs.
                        PreferencesManager prefs = new PreferencesManager(this.svc);
                        if (prefs.getBoolean(PhonexConfig.PUBLISH_IN_CALL_STATE)
                                && (profileState.isInCall() || profileState.isInGsmCall()))
                        {
                            b.setStatus(Status.ONCALL);
                        }

                    } catch(Exception e){
                        Log.df(TAG, "Unknown state value: %s", profileState.getStatusType());
                    }
                }
			}

			// Capabilities.
            b.setCapabilitiesSkip(false);
			b.addAllCapabilities(PhonexSettings.getCapabilities());

			// Serialization and text conversion.
			PresencePush push = b.build();
			final byte[] bpush = ProtoBuffHelper.writeTo(push);
			final String spush = Base64.encodeBytes(bpush);

			Log.df(TAG, "PUSH notification string: [%s] and encoded [%s]", push.toString(), spush);

			// Protocol buffers serialized status text.
			return spush;

		} catch (IOException e) {
			Log.e(TAG, "Exception during generating presence text", e);
		}

		return null;
	}


	public XService getSvc() {
		return svc;
	}

    // Stores icon + text to given status
    private static class PresenceData {
        public PresenceData(int text, int icon) {
            this.text = text;
            this.icon = icon;
        }
        public int text;
        public int icon;
    }

}

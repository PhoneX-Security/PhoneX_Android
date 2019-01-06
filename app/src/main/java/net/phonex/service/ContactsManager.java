package net.phonex.service;

import android.content.BroadcastReceiver;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.LruCache;

import net.phonex.PhonexSettings;
import net.phonex.R;
import net.phonex.core.Constants;
import net.phonex.core.Intents;
import net.phonex.db.entity.SipClist;
import net.phonex.core.SipUri;
import net.phonex.db.DBBulkQuery;
import net.phonex.pub.parcels.CertUpdateParams;
import net.phonex.pub.parcels.GenericError;
import net.phonex.pub.parcels.GenericTaskProgress;
import net.phonex.pub.proto.PushNotifications;
import net.phonex.soap.ClistAddTask;
import net.phonex.soap.ClistAddTaskParams;
import net.phonex.util.Base64;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.Registerable;
import net.phonex.util.analytics.AnalyticsReporter;
import net.phonex.util.analytics.AppEvents;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ContactsManager implements Registerable{
    private static final String TAG ="ContactsManager";
    public static final String NOTIFIER_SUFFIX = "*i_notify";
    private static final String DISPLAY_NAME_ORDER = ContactsContract.Contacts.DISPLAY_NAME + " COLLATE LOCALIZED";
    private static final String SORT_ORDER = ContactsContract.Contacts.TIMES_CONTACTED + " DESC,"
            +  DISPLAY_NAME_ORDER + "," + ContactsContract.CommonDataKinds.Phone.TYPE;

    public static int URI_NBR = 1 << 0;
    public static int URI_IM = 1 << 1;
    public static int URI_SIP = 1 << 2;
    public static int URI_ALLS = URI_IM | URI_NBR | URI_SIP;

    private volatile boolean registered = false;
    private final XService xService;

    /**
     * Logic for listening to presence updates
     */
    private ManagerReceiver receiver;

    /**
     * Cache for delayed presence updates for non-existing contacts.
     * User id -> Presence update.
     */
    private final LruCache<String, PresenceUpdate> delayedPresenceUpdate = new LruCache<>(128);

    public ContactsManager(XService xService) {
        this.xService = xService;
    }

    public synchronized void register(){
        if (registered) {
            Log.w(TAG, "Already registered");
            return;
        }

        receiver = new ManagerReceiver(this);
        IntentFilter intentfilter = new IntentFilter();
        intentfilter.addAction(Intents.ACTION_USER_ADDED);
        MiscUtils.registerReceiver(xService, receiver, intentfilter);

        PhonexSettings.loadDefaultLanguageNoThrow(xService);
        Log.v(TAG, "Contacts Manager registered");
        registered = true;
    }

    public synchronized void unregister() {
        if (!registered) {
            Log.w(TAG, "Already unregistered");
            return;
        }

        xService.unregisterReceiver(receiver);
        receiver = null;

        Log.v(TAG, "Contacts manager unregistered");
        registered = false;
    }

    /**
     * Push back the presence status to the contact database
     * @param ctxt
     * @param presences the new presence status
     */
    public void updatePresence(Context ctxt, final Collection<PresenceUpdate> presences){
        if (presences == null){
            Log.wf(TAG, "updatePresence; null presence, not updating");
            return;
        }

        Log.vf(TAG, "updatePresence; going to update presence in a bulk, size=%d", presences.size());

        // Prepare bulk query loader.
        DBBulkQuery bulkQuery = new DBBulkQuery(ctxt.getContentResolver(),
                SipClist.CLIST_URI,
                SipClist.LIGHT_PROJECTION,
                SipClist.FIELD_ACCOUNT + "=1",
                SipClist.FIELD_SIP,
                new String[] { });

        // Separate server accounts from user accounts.
        Map<String, PresenceUpdate> usrAccounts = new HashMap<>(presences.size());
        for (PresenceUpdate p : presences){
            final String buddyUri = p.getBuddyUri();

            // Determine if it is special server notifier account.
            if (handleServerIfApplicable(ctxt, buddyUri, p.isAvailable(), p.getProtobufStatusText())){
                continue;
            }

            usrAccounts.put(buddyUri, p);
            bulkQuery.add(buddyUri);
        }

        ArrayList<ContentProviderOperation> operations = new ArrayList<>(usrAccounts.size());
        ArrayList<CertUpdateParams> certUpdParamList = new ArrayList<>(usrAccounts.size());

        // Bulk query on the accounts, process in a row.
        for(; bulkQuery.moveToNext(); ){
            Cursor c = bulkQuery.getCurrentCursor();
            try {
                SipClist contact = new SipClist(c);
                final String buddyUri = contact.getSip();
                final PresenceUpdate p = usrAccounts.get(buddyUri);
                if (p == null){
                    Log.wf(TAG, "URI not found in register %s", buddyUri);
                    continue;
                }

                // Process presence information
                final PresenceProcessingResult res = processPresence(new SipClist(c), buddyUri, p.isAvailable(), p.getProtobufStatusText());

                // Broadcast certificate check event.
                // Should be handled by background service to check it. It handles DoS policy & stuff...
                if (res.sendEvent) {
                    Log.vf(TAG, "Cert update (adding to update list) by notif for user, : %s", buddyUri);
                    certUpdParamList.add(new CertUpdateParams(buddyUri, false, true, res.notBefore, res.certHash));

                } else {
                    // If event for a certificate check was not broadcasted
                    // (presence info does not convey certificate freshness information),
                    if (!res.contact.isPresenceOnline() && p.isAvailable()) {
                        Log.vf(TAG, "Cert update by becoming online: %s; presStatus=%s", buddyUri, p.isAvailable());
                        certUpdParamList.add(new CertUpdateParams(buddyUri, false));
                    }
                }

                Log.df(TAG, "Going to update presence, pk=%s; buddy=%s; contentValues=[%s];",
                                res.contact.getId(), buddyUri, res.values.toString());

                // Build batch operation here...
                ContentProviderOperation op = ContentProviderOperation.newUpdate(SipClist.CLIST_URI)
                        .withValues(res.values)
                        .withSelection(SipClist.FIELD_ID + "=?", new String[]{res.contact.getId().toString()})
                        .withYieldAllowed(true)
                        .build();
                operations.add(op);

            } catch(Exception e){
                Log.ef(TAG, e, "Exception in updating presence");
            }
        }

        // Apply batch operation.
        try {
            final ContentProviderResult[] res = ctxt.getContentResolver().applyBatch(Constants.AUTHORITY, operations);
            if (res != null){
                for(ContentProviderResult cres : res){
                    if (cres == null){
                        Log.wf(TAG, "Update res is null");
                    } else if (cres.count == null || cres.count == 0){
                        Log.wf(TAG, "Update had no effect: %s", cres);
                    }
                }
            }
        } catch (Exception e) {
            Log.ef(TAG, e, "Failed to apply batch operation to database.");
        }

        // Apply certificate update requests.
        try {
            XService.triggerCertUpdate(ctxt, certUpdParamList, false);
        } catch (Exception e) {
            Log.ef(TAG, e, "Failed to trigger batch certificate update.");
        }
    }

    public void triggerAddContact(String sip, String alias) {
        // TODO run in one of the executors
        ClistAddTaskParams params = new ClistAddTaskParams();
        params.setUserName(sip);
        params.setDiplayName(alias);
        params.setLoadIdentity(true);
        ClistAddTask task = new ClistAddTask(xService, params);
        task.setListener(new ClistAddTask.OnClistAddCallback() {
            @Override
            public void onCompleted() {
                Log.df(TAG, "triggerAddContact - onCompleted");
                Intent intent = new Intent(Intents.ACTION_ADD_CONTACT_PROGRESS);
                intent.putExtra(Intents.EXTRA_GENERIC_PROGRESS, GenericTaskProgress.doneInstance());
                MiscUtils.sendBroadcast(xService, intent);

                AnalyticsReporter.from(xService).event(AppEvents.CONTACT_ADDED);
            }

            @Override
            public void onError(Exception e) {
                Log.df(TAG, "triggerAddContact - onError");
                Intent intent = new Intent(Intents.ACTION_PAIRING_REQUEST_UPDATE_PROGRESS);
                // error enum is not specified, only message
                String errorMsg;

                if (e instanceof ClistAddTask.ClistAddException){
                    ClistAddTask.ClistAddException ex = (ClistAddTask.ClistAddException) e;
                    errorMsg = ex.getErrorCodeMessage(xService);
                } else {
                    errorMsg = xService.getString(R.string.p_problem_nonspecific);
                }

                intent.putExtra(Intents.EXTRA_GENERIC_PROGRESS, GenericTaskProgress.errorInstance(GenericError.GENERIC_ERROR, errorMsg));
                MiscUtils.sendBroadcast(xService, intent);
            }
        });

        Handler h = new Handler();
        h.post(task);
    }

    private static class PresenceProcessingResult {
        public SipClist contact;
        public Uri userUri;
        public boolean sendEvent = false;
        public Long notBefore = null;
        public String certHash = null;
        public ContentValues values;
    }

    private boolean handleServerIfApplicable(Context ctxt, String buddyUri, boolean isAvailable, String protobufStatusText){
        // Determine if it is special server notifier account.
        SipUri.ParsedSipContactInfos uinfo = SipUri.parseSipContact(SipUri.getCanonicalSipContact(buddyUri, true));
        if (uinfo.userName.endsWith(NOTIFIER_SUFFIX)){
            Log.df(TAG, "Special notifier account: [%s]", buddyUri);
            handleServerPush(ctxt, buddyUri, isAvailable, protobufStatusText);
            return true;
        }

        return false;
    }

    private PresenceProcessingResult processPresence(SipClist contact, final String buddyUri, final boolean isAvailable, final String protobufStatusText){
        PresenceProcessingResult res = new PresenceProcessingResult();

        // Presence push notification for this contact.
        // Using Google Protocol Buffers to serialize complex structures
        // into presence status text information.
        PushNotifications.PresencePush presencePush = null;

        // Attempt to parse presence text into PushNotification.
        try {
            if (!TextUtils.isEmpty(protobufStatusText)
                    && !"?".equals(protobufStatusText)
                    && !"online".equalsIgnoreCase(protobufStatusText)
                    && !"offline".equalsIgnoreCase(protobufStatusText)
                    && !"unknown".equalsIgnoreCase(protobufStatusText)){

                final byte[] bpush = Base64.decode(protobufStatusText);
                presencePush = PushNotifications.PresencePush.parseFrom(bpush);
            }
        } catch(com.google.protobuf.InvalidProtocolBufferException pbex){
            Log.d(TAG, "ProtocolBuffers cannot read binary data.");
        } catch(java.lang.IllegalArgumentException iaex){
            Log.d(TAG, "IllegalArgumentException reading presence data (maybe not in BASE64)");
        } catch(java.io.IOException ioex){
            Log.d(TAG, "IOException reading presence data (maybe not in BASE64)");
        }  catch(Exception ex){
            Log.e(TAG, "Cannot parse presence information", ex);
        }

        // call update on given user
        res.contact = contact;
        res.userUri = ContentUris.withAppendedId(SipClist.CLIST_STATE_ID_URI_BASE, res.contact.getId());

        SimpleDateFormat iso8601Format = new SimpleDateFormat(SipClist.DATE_FORMAT, Locale.getDefault());
        res.values = new ContentValues();
        res.values.put(SipClist.FIELD_PRESENCE_ONLINE, isAvailable ? "1":"0");
        res.values.put(SipClist.FIELD_PRESENCE_LAST_UPDATE, iso8601Format.format(new Date()));

        Integer presenceType;
        if (presencePush != null){
            Log.vf(TAG, "Extended presence notification for user '%s' [%s]", res.userUri.toString(), presencePush.toString());

            if (presencePush.hasStatus()){
                presenceType = presencePush.getStatus().getNumber();
                res.values.put(SipClist.FIELD_PRESENCE_STATUS_TYPE, presenceType);
                // in case user is XMPP.Available but has presence status Offline, mark him in DB as offline - to have Contact list sorted properly
                res.values.put(SipClist.FIELD_PRESENCE_ONLINE, presenceType == PushNotifications.PresencePush.Status.OFFLINE_VALUE ? "0":"1");
            } else {
                // If not present, set default state in order to reset previous one.
                res.values.put(SipClist.FIELD_PRESENCE_STATUS_TYPE, !isAvailable ? PushNotifications.PresencePush.Status.OFFLINE_VALUE : PushNotifications.PresencePush.Status.ONLINE_VALUE);
            }

            if (presencePush.hasStatusText()){
                res.values.put(SipClist.FIELD_PRESENCE_STATUS_TEXT, MiscUtils.getStringMaxLen(presencePush.getStatusText(), 512));
            } else {
                // If not present set empty state to reset previous one.
                res.values.put(SipClist.FIELD_PRESENCE_STATUS_TEXT, "");
            }

            if (presencePush.hasCertHashShort()){
                res.values.put(SipClist.FIELD_PRESENCE_CERT_HASH_PREFIX, MiscUtils.getStringMaxLen(presencePush.getCertHashShort(), 256));

                res.certHash = presencePush.getCertHashShort();
                res.sendEvent = true;
            }

            if (presencePush.hasCertNotBefore()){
                res.values.put(SipClist.FIELD_PRESENCE_CERT_NOT_BEFORE, presencePush.getCertNotBefore());

                res.notBefore = presencePush.getCertNotBefore();
                res.sendEvent=true;
            }

            // Update capabilities
            if (presencePush.hasCapabilitiesSkip()){
                final boolean capSkip = presencePush.getCapabilitiesSkip();

                if (!capSkip){
                    List<String> capabilities = presencePush.getCapabilitiesList();
                    if (capabilities!=null && !capabilities.isEmpty()){
                        final Set<String> capsSet = new HashSet<>(capabilities);
                        final String capAcc = SipClist.assembleCapabilities(capsSet);

                        Log.vf(TAG, "Updating caps for user %s; caps=%s", buddyUri, capAcc);
                        res.values.put(SipClist.FIELD_CAPABILITIES, capAcc);
                    } else {
                        res.values.put(SipClist.FIELD_CAPABILITIES, "");
                    }
                }
            }

        } else if (!isAvailable){ // when presencePush is null but contact is not available
            Log.vf(TAG, "No extended presence notification for user '%s', setting as offline", res.userUri.toString());
            res.values.put(SipClist.FIELD_PRESENCE_STATUS_TYPE, PushNotifications.PresencePush.Status.OFFLINE_VALUE);
        }

        return res;
    }

    /**
     * Our own presence handler. Since contacts are loaded from database, we update presence status in database.
     * @param ctxt
     * @param buddyUri
     * @param isAvailable true means that user shares his status
     * @param protobufStatusText ProtocolBuffers encoded message, see {@link MyPresenceManager#generatePresenceText}
     */
    public void updatePresence(Context ctxt, String buddyUri, boolean isAvailable, String protobufStatusText) {
        Log.df(TAG, "Updating presence information for buddy [%s] isAvailable='%s'; protobufStatusText='%s'",
                buddyUri,
                isAvailable,
                protobufStatusText);

        // Determine if it is special server notifier account.
        if (handleServerIfApplicable(ctxt, buddyUri, isAvailable, protobufStatusText)){
            return;
        }

        Cursor c = null;
        try {
            // at first fetch contact with given SIP
            // is contact already in database?
            String selection = SipClist.FIELD_SIP + "=?" + " AND " + SipClist.FIELD_ACCOUNT + "=?";
            String[] selectionArgs = new String[] { buddyUri, "1" };
            c = ctxt.getContentResolver().query(SipClist.CLIST_URI, SipClist.LIGHT_PROJECTION, selection, selectionArgs, null);
            if (c==null || c.getCount()<=0 || !c.moveToNext()){
                Log.wf(TAG, "User with sip[%s] was not found in contacts database.", buddyUri);
                addDelayedPresence(new PresenceUpdate(buddyUri, isAvailable, protobufStatusText, System.currentTimeMillis()));
                return;
            }

            final PresenceProcessingResult res = processPresence(new SipClist(c), buddyUri, isAvailable, protobufStatusText);

            // Broadcast certificate check event.
            // Should be handled by background service to check it. It handles DoS policy & stuff...
            if (res.sendEvent){
                Log.vf(TAG, "Cert update by push notif for user: %s", buddyUri);
                XService.triggerCertUpdate(ctxt, new CertUpdateParams(buddyUri, false, true, res.notBefore, res.certHash));
            } else {
                // If event for a certificate check was not broadcasted
                // (presence info does not convey certificate freshness information),
                if (!res.contact.isPresenceOnline() && isAvailable){
                    Log.vf(TAG, "Cert update by becoming online: %s; presStatus=%s", buddyUri, isAvailable);
                    XService.triggerCertUpdate(ctxt, new CertUpdateParams(buddyUri, false));
                }
            }

            Log.d(TAG,
                    String.format("Going to update presence, pk=%s; buddy=%s; contentValues=[%s];",
                            res.contact.getId(),
                            buddyUri,
                            res.values.toString()
                    ));

            ctxt.getContentResolver().update(res.userUri, res.values, null, null);

        } catch (Exception e){
            Log.e(TAG, "Can't update status", e);
        } finally {
            MiscUtils.closeCursorSilently(c);
        }
    }

    /**
     * Handle presence update
     * @param ctxt
     * @param buddyUri
     * @param presStatus
     * @param protobufStatusText
     */
    public void handleServerPush(Context ctxt, String buddyUri, boolean presStatus, String protobufStatusText){
        // Presence push notification from the server.
        // Using Google Protocol Buffers to serialize complex structures
        // into presence status text information.
        PushNotifications.ServerNotificationPush push = null;

        // Attempt to parse presence text into PushNotification.
        try {
            if (TextUtils.isEmpty(protobufStatusText)==false
                    && "?".equals(protobufStatusText)==false
                    && "online".equalsIgnoreCase(protobufStatusText)==false
                    && "offline".equalsIgnoreCase(protobufStatusText)==false
                    && "unknown".equalsIgnoreCase(protobufStatusText)==false){

                final byte[] bpush = Base64.decode(protobufStatusText);
                push = PushNotifications.ServerNotificationPush.parseFrom(bpush);
                Log.vf(TAG, "Server push notification: %s", push.toString());
            }
        } catch(com.google.protobuf.InvalidProtocolBufferException pbex){
            Log.d(TAG, "ProtocolBuffers cannot read binary data.");
        } catch(java.lang.IllegalArgumentException iaex){
            Log.d(TAG, "IllegalArgumentException reading presence data (maybe not in BASE64)");
        } catch(java.io.IOException ioex){
            Log.d(TAG, "IOException reading presence data (maybe not in BASE64)");
        }  catch(Exception ex){
            Log.e(TAG, "Cannot parse presence information", ex);
        }

        // If invalid structure, nothing to do...
        // TODO: handle new files in mailbox notification.
    }

    /**
     * Sets all contacts for the given user to the offline presence.
     */
    public void setOfflinePresence(Context ctxt, String contactOwner) {
        // For now (single user mode) turn all contacts to offline.
        SimpleDateFormat iso8601Format = new SimpleDateFormat(SipClist.DATE_FORMAT, Locale.getDefault());
        ContentValues values = new ContentValues();
        values.put(SipClist.FIELD_PRESENCE_STATUS_TYPE, PushNotifications.PresencePush.Status.OFFLINE_VALUE);
        values.put(SipClist.FIELD_PRESENCE_ONLINE, "0");

        values.put(SipClist.FIELD_PRESENCE_LAST_UPDATE, iso8601Format.format(new Date()));

        try {
            ctxt.getContentResolver().update(SipClist.CLIST_URI, values, "1", null);
        } catch(Exception e){
            Log.e(TAG, "Exception, cannot update presence to offline", e);
        }
    }

    /**
     * Triggers action for scanning delayed presence updates.
     * @param ctxt
     */
    public static void broadcastUserAddedChange(Context ctxt){
        final Intent intent = new Intent(Intents.ACTION_USER_ADDED);
        MiscUtils.sendBroadcast(ctxt, intent);
    }

    /**
     * Adds presence update to the delayed presence state.
     * Stores for later, when user got inserted to the contact list, this update may be handy that time.
     */
    protected void addDelayedPresence(PresenceUpdate update){
        synchronized (delayedPresenceUpdate){
            // Add only new one, due to rebroadcasting, it may happen older update is added.
            final PresenceUpdate prevUpd = delayedPresenceUpdate.get(update.buddyUri);
            if (prevUpd == null || prevUpd.getTimestamp() < update.getTimestamp()) {
                delayedPresenceUpdate.put(update.buddyUri, update);
            }
        }
    }

    /**
     * Rebroadcasts delayed presence stored in the manager state.
     * Useful if presence update came earlier that user got inserted to the contact list.
     */
    protected void rebroadcastDelayedPresence(){
        Log.vf(TAG, "Rebroadcasting delayed presence, size: %s", delayedPresenceUpdate.size());

        final Map<String, PresenceUpdate> snapshot;
        synchronized (delayedPresenceUpdate){
            snapshot = delayedPresenceUpdate.snapshot();
            delayedPresenceUpdate.evictAll();
        }

        List<PresenceUpdate> pushBack = new ArrayList<>(snapshot.size());

        // Process each presence update, allow to pass only if isAvailable = true and not older than 5 minutes.
        final long curTime = System.currentTimeMillis();
        for(PresenceUpdate upd : snapshot.values()){
            // Freshness check. If it is too old, this presence update is discarded permanently.
            if ((curTime - upd.getTimestamp()) > 1000 * 60 * 5){
                continue;
            }

            pushBack.add(upd);
        }

        // Rebroadcasting happens here.
        Log.vf(TAG, "Final rebroadcasting, #of elements: %s", pushBack.size());
        updatePresence(xService, pushBack);
    }

    /**
     * Class represents presence update.
     * Holds information about presence update for given user
     * that should be reflected to the UI.
     *
     * the actual holder of status type & text is protobufStatusText, it needs to be encoded first
     * 
     * @author ph4r05
     *
     */
    public static class PresenceUpdate {
    	private final String buddyUri;
    	private final boolean isAvailable;
    	private final String protobufStatusText;
        private long timestamp;
    	
		public PresenceUpdate(String buddyUri, boolean isAvailable, String protobufStatus) {
			this.buddyUri = buddyUri;
			this.isAvailable = isAvailable;
			this.protobufStatusText = protobufStatus;
		}

        public PresenceUpdate(String buddyUri, boolean isAvailable, String protobufStatusText, long timestamp) {
            this.buddyUri = buddyUri;
            this.isAvailable = isAvailable;
            this.protobufStatusText = protobufStatusText;
            this.timestamp = timestamp;
        }

        /**
		 * @return the buddyUri
		 */
		public String getBuddyUri() {
			return buddyUri;
		}

		/**
		 * @return the protobufStatusText
		 */
		public String getProtobufStatusText() {
			return protobufStatusText;
		}

        public boolean isAvailable() {
            return isAvailable;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
    }

    /**
     * Broadcast receiver for intents.
     */
    public static class ManagerReceiver extends BroadcastReceiver {
        final private WeakReference<ContactsManager> wMgr;

        public ManagerReceiver(ContactsManager wMgr) {
            this.wMgr = new WeakReference<ContactsManager>(wMgr);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (MiscUtils.isEmpty(action)){
                return;
            }

            ContactsManager mgr = wMgr.get();
            if (mgr == null){
                Log.d(TAG, "Manager is null");
                return;
            }

            switch(intent.getAction()){
                case Intents.ACTION_USER_ADDED:
                    mgr.rebroadcastDelayedPresence();
                    break;
                default:
                    break;
            }
        }
    }
}

package net.phonex.util.android;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;

import net.phonex.PhonexSettings;
import net.phonex.R;
import net.phonex.core.Constants;
import net.phonex.core.Intents;
import net.phonex.core.MemoryPrefManager;
import net.phonex.pref.PhonexConfig;
import net.phonex.db.entity.SipCallSessionInfo;
import net.phonex.db.entity.SipClist;
import net.phonex.db.entity.SipProfile;
import net.phonex.db.entity.SipProfileState;
import net.phonex.core.SipUri;
import net.phonex.db.entity.CallLog;
import net.phonex.db.entity.PairingRequest;
import net.phonex.db.entity.SipMessage;
import net.phonex.db.entity.SipSignatureWarning;
import net.phonex.license.WarningType;
import net.phonex.pub.a.Compatibility;
import net.phonex.pref.PreferencesConnector;
import net.phonex.pub.parcels.StoredCredentials;
import net.phonex.service.XService;
import net.phonex.ui.PhonexActivity;
import net.phonex.ui.intro.IntroActivity;
import net.phonex.license.LicenseInformation;
import net.phonex.ui.pairingRequest.PairingRequestsActivity;
import net.phonex.ui.inapp.ManageLicenseActivity;
import net.phonex.ui.preferences.ChangeRecoveryEmailActivity;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.account.PhonexAccountManager;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class StatusbarNotifications {
    private static final String TAG = "Notifications";

    private final NotificationManagerCompat notificationManager;
    private final Context ctxt;

    private NotificationCompat.Builder appRegNotification;
    private NotificationCompat.Builder inCallNotification;
    private NotificationCompat.Builder missedCallNotification;
    private NotificationCompat.Builder messageNotification;
    private NotificationCompat.Builder warningNotification;
    private NotificationCompat.Builder keygenNotification;
    private NotificationCompat.Builder certUpdNotification;
    private NotificationCompat.Builder licExpNotification;
    private NotificationCompat.Builder licUpdNotification;
    private NotificationCompat.Builder pairingRequestNotification;
    private static boolean isInit = false;
    private boolean isServiceWrapper = false;

    /**
     * State of notification so they can be reconstructed if needed.
     */
    private int strRegContentId;
    private int strRegTickerId;
    private WarningType licExpWarningType;
    private LicenseInformation licUpdInformation;

    /**
     * Notification Id -> visible flag.
     */
    private final Map<Integer, Boolean> notificationVisibility = new ConcurrentHashMap<>();

    /**
     * Current contact address user is chatting with.
     * Not to display notifications for new messages from user in active chat window.
     * If null, there is no chat window active.
     */
    private static String currentConversationContact = null;

    /**
     * Contact address the last message notification was shown for.
     * If a new currentConversationContact is set and matches this value
     * the message notification gets cancelled since user switched to
     * chat window with the contact.
     */
    private static String lastMessageNotificationContact = null;

    public static final int REGISTER_NOTIF_ID = 1;
    public static final int CALL_NOTIF_ID = REGISTER_NOTIF_ID + 1;
    public static final int CALLLOG_NOTIF_ID = REGISTER_NOTIF_ID + 2;
    public static final int MESSAGE_NOTIF_ID = REGISTER_NOTIF_ID + 3;
    public static final int WARNING_NOTIF_ID = REGISTER_NOTIF_ID + 5;
    public static final int KEYGEN_NOTIF_ID = REGISTER_NOTIF_ID + 6;
    public static final int CERTUPD_NOTIF_ID = REGISTER_NOTIF_ID + 7;
    public static final int APPUDP_NOTIF_ID = REGISTER_NOTIF_ID + 8;
    public static final int DEVICEREBOOT_NOTIF_ID = REGISTER_NOTIF_ID + 9;
    public static final int LICENSE_NOTIF_ID = REGISTER_NOTIF_ID + 10;
    public static final int LICENSE_EXPIRATION_NOTIF_ID = REGISTER_NOTIF_ID + 11;
    public static final int PAIRING_REQUEST_NOTIF_ID = REGISTER_NOTIF_ID + 12;
    public static final int MISSING_REC_EMAIL_NOTIF_ID = REGISTER_NOTIF_ID + 13;

    private static final String EXTRA_CHAT_INFO_NAME = "remoteContact";

    final private ScheduledThreadPoolExecutor texecutor = new ScheduledThreadPoolExecutor(1);
    final private UnregisteredPhonexTask regTask = new UnregisteredPhonexTask();
    private ScheduledFuture<?> futureRegTask;
    private Boolean regStatePrevRegistered = null;
    private PreferencesConnector prefs = null;
    private BcastReceiver receiver;

    /**
     * Constructor.
     * @param ctxt
     */
    public StatusbarNotifications(Context ctxt) {
        this.ctxt = ctxt;
        notificationManager = NotificationManagerCompat.from(ctxt);

        if (!isInit) {
            cancelAll();
            cancelCalls();
            isInit = true;
        }

        prefs = new PreferencesConnector(ctxt);
    }

    /**
     * This is a wrapper around the new startForeground method, using the older
     * APIs if it is not available.
     */
    private void startForeground(int id, Notification notification, NotificationCompat.Builder builder) {
        notify(id, notification, builder);

        if (!(ctxt instanceof Service)){
            Log.wf(TAG, "Cannot call startForeground(), not a service");
            return;
        }

        final Service svc = (Service) ctxt;

        // Broadcast start foreground intent for SafeNet.
        Intent intent = new Intent(Intents.ACTION_FOREGROUND);
        intent.putExtra(Intents.EXTRA_FOREGROUND_ENABLE, true);
        intent.putExtra(Intents.EXTRA_FOREGROUND_ID, id);
        intent.putExtra(Intents.EXTRA_FOREGROUND_NOTIF, notification);
        svc.startForeground(id, notification);
        MiscUtils.sendBroadcast(svc, intent);
    }

    /**
     * This is a wrapper around the new stopForeground method, using the older
     * APIs if it is not available.
     */
    private void stopForeground(int id) {
        cancel(id);

        if (!(ctxt instanceof Service)){
            Log.wf(TAG, "Cannot call stopForeground(), not a service");
            return;
        }

        final Service svc = (Service) ctxt;
        svc.stopForeground(true);

        // Broadcast start foreground intent for SafeNet.
        Intent intent = new Intent(Intents.ACTION_FOREGROUND);
        intent.putExtra(Intents.EXTRA_FOREGROUND_ENABLE, false);
        MiscUtils.sendBroadcast(svc, intent);
    }

    public void onServiceCreate() {
        isServiceWrapper = true;

        receiver = new BcastReceiver();
        IntentFilter intentfilter = new IntentFilter();
        intentfilter.addAction(Intents.ACTION_SETTINGS_MODIFIED);
        MiscUtils.registerReceiver(ctxt, receiver, intentfilter);
    }

    public void onServiceDestroy() {
        cancelAll();
        cancelCalls();

        try {
            ctxt.unregisterReceiver(receiver);
        } catch(Exception ex){
            Log.e(TAG, "Exception when unregistering listener", ex);
        }

        receiver = null;
    }

    /**
     * SIP registration events.
     *  @param activeAccountsInfos
     *
     */
    public synchronized void notifyRegisteredAccounts(ArrayList<SipProfileState> activeAccountsInfos, boolean internal) {
        if (!isServiceWrapper) {
            Log.e(TAG, "Trying to create a service notification from outside the service");
            return;
        }

        // Load correct language.
        PhonexSettings.loadDefaultLanguageNoThrow(ctxt);

        // Cancel unregistered notification if scheduled.
        if (!internal && futureRegTask!=null && !futureRegTask.isDone()){
            futureRegTask.cancel(false);
        }

//        int icon = R.drawable.status_icon;
        int icon = R.drawable.svg_logo_square_mini;
        strRegTickerId = R.string.service_notification_registered;

        // Check if there is at least one registered account.
        boolean atLeastOneRegistered = false;
        for(SipProfileState s : activeAccountsInfos){
            if (s.isValidForCall()) {
                atLeastOneRegistered = true;
                break;
            }
        }

        // If there is no active account - show gray icon and unregistered ticker.
        if (!atLeastOneRegistered){
            icon = R.drawable.svg_logo_square_mini_bw;
            strRegTickerId = R.string.service_notification_unregistered;

            // If grey notification is disabled?
            if(!allowGreyAccountNotification()) {
                Log.v(TAG, "App has been quit/logged out, no status notification");
                cancelRegistersReal();
                return;
            }
        }

        // If is same as previous one do nothing
        if (regStatePrevRegistered != null && regStatePrevRegistered == atLeastOneRegistered){
            Log.vf(TAG, "Reg notification same as before, not showing, %s", atLeastOneRegistered);
            return;
        }

        final long when = System.currentTimeMillis();
        appRegNotification = new NotificationCompat.Builder(ctxt);
        appRegNotification.setSmallIcon(icon);
        appRegNotification.setTicker(ctxt.getString(strRegTickerId));
        appRegNotification.setWhen(when);
        Intent notificationIntent = XService.buildIntent(ctxt, Intents.ACTION_CONTACT_LIST);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(ctxt, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);


        // Currently do not show your name in notifications (for paranoid users)
//        AccountNotification contentView = new AccountNotification(ctxt.getPackageName());
//        contentView.hideRegistration();
//        contentView.AddRegistration(ctxt, activeAccountsInfos);

        appRegNotification.setOngoing(true);
        appRegNotification.setOnlyAlertOnce(true);
        appRegNotification.setContentIntent(contentIntent);
        appRegNotification.setContentTitle("PhoneX");

        SipProfileState accountInfo = activeAccountsInfos.get(0);
        // Online vs. offline
        if (accountInfo.isValidForCall()){
            appRegNotification.setSmallIcon(R.drawable.svg_logo_square_mini);
            strRegContentId = R.string.registered;

        } else {
            appRegNotification.setSmallIcon(R.drawable.svg_logo_square_mini_bw);
            strRegContentId = R.string.unregistered;

        }

        appRegNotification.setContentText(ctxt.getString(strRegContentId));
        Notification notification = appRegNotification.build();
        regNotifPostProcess(notification);

        // Start new one
        startForeground(REGISTER_NOTIF_ID, notification, appRegNotification);
        regStatePrevRegistered = atLeastOneRegistered;
    }

    private void regNotifPostProcess(Notification notification){
        notification.flags |= Notification.FLAG_NO_CLEAR;
    }

    /**
     * Shows notification for current pending call.
     * @param currentCallInfo2
     */
    public void notifyCall(SipCallSessionInfo currentCallInfo2) {
        final long when = System.currentTimeMillis();

        @SuppressWarnings("deprecation")
        int icon = android.R.drawable.stat_sys_phone_call;
        CharSequence tickerText = ctxt.getText(R.string.ongoing_call);

        if(inCallNotification == null) {
            inCallNotification = new NotificationCompat.Builder(ctxt);
            inCallNotification.setSmallIcon(icon);
            inCallNotification.setOngoing(true);
        }

        // Load correct language.
        PhonexSettings.loadDefaultLanguageNoThrow(ctxt);
        inCallNotification.setContentTitle(ctxt.getText(R.string.ongoing_call));

        Intent notificationIntent = XService.buildCallUiIntent(ctxt, currentCallInfo2);
        PendingIntent contentIntent = PendingIntent.getActivity(ctxt, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        inCallNotification.setWhen(when);
        inCallNotification.setTicker(tickerText);
        inCallNotification.setContentText(SipUri.stripSipScheme(currentCallInfo2.getRemoteContact()));
        inCallNotification.setContentIntent(contentIntent);

        Notification notification = inCallNotification.build();
        notification.flags |= Notification.FLAG_NO_CLEAR;
        notify(CALL_NOTIF_ID, notification, inCallNotification);
    }

    /**
     * Missed call notification with intent - broadcast.
     * @param intent
     */
    public void notifyMissedCall(Intent intent){
        if (intent == null || !intent.hasExtra(Intents.EXTRA_MISSED_CALL_RECEIVED)){
            Log.ef(TAG, "onMissedCall(): invalid intent data");
            return;
        }

        if (!Compatibility.isCallSupported()){
            return;
        }

        final CallLog cl = intent.getParcelableExtra(Intents.EXTRA_MISSED_CALL_RECEIVED);
        notifyMissedCall(cl);
    }

    /**
     * Shows notification for missed call.
     * @param callLog
     */
    public void notifyMissedCall(CallLog callLog) {
        final long when = System.currentTimeMillis();
        PhonexSettings.loadDefaultLanguageNoThrow(ctxt);
        CharSequence tickerText = ctxt.getText(R.string.missed_call);

        if (missedCallNotification == null) {
            missedCallNotification = new NotificationCompat.Builder(ctxt);
//            missedCallNotification.setSmallIcon(android.R.drawable.stat_notify_missed_call);
            missedCallNotification.setSmallIcon(R.drawable.ic_call_missed_white_24px);
            missedCallNotification.setOnlyAlertOnce(true);
            missedCallNotification.setAutoCancel(true);
            missedCallNotification.setDefaults(Notification.DEFAULT_ALL);
        }

        // Set text, in case of language change.
        missedCallNotification.setContentTitle(ctxt.getText(R.string.missed_call));

        Intent notificationIntent = XService.buildIntent(ctxt, Intents.ACTION_NOTIFICATIONS);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        notificationIntent.putExtra(SipClist.FIELD_SIP, callLog.getRemoteContact());
        PendingIntent contentIntent = PendingIntent.getActivity(ctxt, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        String displayName = callLog.getRemoteContactName();
        String sip = callLog.getRemoteContactSip();
        if (!TextUtils.isEmpty(sip)){
            displayName = displayName + " <" + sip + ">";
        }

        missedCallNotification.setTicker(tickerText);
        missedCallNotification.setWhen(when);
        missedCallNotification.setContentText(displayName);
        missedCallNotification.setContentIntent(contentIntent);
        notify(CALLLOG_NOTIF_ID, missedCallNotification.build(), missedCallNotification);
    }

    /**
     * Show notification for warning messages.
     * @param warn Signature warning descriptor.
     */
    public void notifyWarning(SipSignatureWarning warn) {
        final long when = System.currentTimeMillis();
        int icon = android.R.drawable.stat_notify_error;

        PhonexSettings.loadDefaultLanguageNoThrow(ctxt);
        CharSequence tickerText = ctxt.getText(R.string.warning_signature);

        if (warningNotification == null) {
            warningNotification = new NotificationCompat.Builder(ctxt);
            warningNotification.setSmallIcon(icon);
            warningNotification.setOnlyAlertOnce(true);
            warningNotification.setAutoCancel(true);
            warningNotification.setDefaults(Notification.DEFAULT_ALL);
        }

        warningNotification.setContentTitle(ctxt.getText(R.string.warning_signature));

        Intent notificationIntent = XService.buildIntent(ctxt, Intents.ACTION_SIP_SIGNATURE_WARNING);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(ctxt, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        warningNotification.setTicker(tickerText);
        warningNotification.setWhen(when);
        warningNotification.setContentText(warn.getRemoteURI());
        warningNotification.setContentIntent(contentIntent);
        notify(WARNING_NOTIF_ID, warningNotification.build(), warningNotification);
    }

    /**
     * Show notification for new unread message.
     * @param msg New unread message.
     */
    public void notifyUnreadMessage(SipMessage msg) {
        final long when = System.currentTimeMillis();
        if (!PhonexSettings.supportMessaging()) {
            return;
        }

        PhonexSettings.loadDefaultLanguageNoThrow(ctxt);
        final String canonFrom = SipUri.getCanonicalSipContact(msg.getFrom(), false);
        final boolean doAlert = prefs.getBoolean(PhonexConfig.ALERT_NEW_MESSAGE_DURING_CHAT);

        // CharSequence tickerText = ctxt.getText(R.string.instance_message);
        if (canonFrom.equalsIgnoreCase(currentConversationContact) && !doAlert) {
            Log.v(TAG, String.format("MsgNotification missed from=[%s] viewingFrom=[%s]", canonFrom, currentConversationContact));
            return;
        }

        String messageBody = msg.getBody();
        if (Constants.SIP_SECURE_MSG_MIME.equalsIgnoreCase(msg.getMimeType())){
            messageBody = "[" + ctxt.getText(R.string.msg_enc_tit_encrypted) +"]";
        } else if (Constants.SIP_SECURE_FILE_NOTIFY_MIME.equalsIgnoreCase(msg.getMimeType())){
            messageBody = "[" + ctxt.getText(R.string.msg_enc_tit_encrypted_file) +"]";
        }

        String from = msg.getDisplayName();

        //get display name, format: test610@phone-x.net
        Log.vf(TAG, "notification for message from: %s, currentConversation: %s, lastNotif: %s, doAlert: %s", canonFrom, currentConversationContact, lastMessageNotificationContact, doAlert);

        Cursor c = ctxt.getContentResolver().query(SipClist.CLIST_URI,SipClist.LIGHT_PROJECTION,SipClist.FIELD_SIP +  "=?",new String[] {canonFrom},null);
        String dn = null;
        if (c != null) {
            try {
                if (c.moveToFirst()){
                    dn = c.getString(c.getColumnIndex(SipClist.FIELD_DISPLAY_NAME));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error while getting accountManager", e);
            } finally {
                MiscUtils.closeCursorSilently(c);
            }
        }

        Log.v(TAG, String.format("MsgNotif from=[%s] dn=[%s]", from, dn));
        if (dn!=null){
            from = dn;
        }

        CharSequence tickerText = buildTickerLabel(ctxt, from, messageBody);
        if (messageNotification == null) {
            messageNotification = new NotificationCompat.Builder(ctxt);
            messageNotification.setSmallIcon(SipUri.isPhoneNumber(from) ? R.drawable.stat_notify_sms : R.drawable.stat_notify_chat);
            messageNotification.setDefaults(Notification.DEFAULT_ALL);
            messageNotification.setAutoCancel(true);
            messageNotification.setOnlyAlertOnce(true);
        }

        Intent notificationIntent = XService.buildIntent(ctxt, Intents.ACTION_SIP_MESSAGES);
        notificationIntent.putExtra(SipMessage.FIELD_FROM, msg.getFrom());
        notificationIntent.putExtra(SipMessage.FIELD_FROM_FULL, from);
        notificationIntent.putExtra(SipMessage.FIELD_BODY, messageBody);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(ctxt, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT); // TODO: FLAG_UPDATE_CURRENT ?

        messageNotification.setWhen(when);
        messageNotification.setTicker(tickerText);
        messageNotification.setContentTitle(from);
        messageNotification.setContentText(messageBody);
        messageNotification.setContentIntent(contentIntent);

        // Cancel previous notification.
        this.cancelMessages();

        // Show new notification.
        notify(MESSAGE_NOTIF_ID, messageNotification.build(), messageNotification);
        lastMessageNotificationContact = canonFrom;
    }

    /**
     * Show notification for generating file transfer keys.
     */
    public void notifyKeyGen() {
        final long when = System.currentTimeMillis();
        PhonexSettings.loadDefaultLanguageNoThrow(ctxt);

        if (keygenNotification == null) {
            keygenNotification = new NotificationCompat.Builder(ctxt);
//            keygenNotification.setSmallIcon(R.drawable.key_on1);
            keygenNotification.setSmallIcon(R.drawable.ic_vpn_key_white_24px);
            keygenNotification.setOngoing(true);
            keygenNotification.setOnlyAlertOnce(false);
        }

        Intent notificationIntent = XService.buildIntent(ctxt, Intents.ACTION_KEY_GEN_NOTIFICATION);
        //notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(ctxt, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        keygenNotification.setTicker(ctxt.getText(R.string.notification_keygen));
        keygenNotification.setWhen(when);
        keygenNotification.setContentTitle(ctxt.getText(R.string.application_name));
        keygenNotification.setContentText(ctxt.getText(R.string.notification_keygen));
        keygenNotification.setContentIntent(contentIntent);

        Notification notification = keygenNotification.build();
        notification.flags |= Notification.FLAG_NO_CLEAR;

        notify(KEYGEN_NOTIF_ID, notification, keygenNotification);
    }

    /**
     * Show notification for new incoming pairing requests.
     */
    public void notifyPairingRequest(PairingRequest lastRequest, int totalCount) {
        Log.df(TAG, "notifyPairingRequest; lastRequest=, totalCount=", lastRequest, totalCount);
        final long when = System.currentTimeMillis();
        PhonexSettings.loadDefaultLanguageNoThrow(ctxt);

        if (pairingRequestNotification == null) {
            pairingRequestNotification = new NotificationCompat.Builder(ctxt);
            pairingRequestNotification.setSmallIcon(R.drawable.ic_person_add_black_24px);
            pairingRequestNotification.setOngoing(false);
            pairingRequestNotification.setAutoCancel(true);
            pairingRequestNotification.setOnlyAlertOnce(true);
        }

        Intent notificationIntent = new Intent(ctxt, PairingRequestsActivity.class);
        notificationIntent.putExtra(PairingRequest.FIELD_FROM_USER, lastRequest.getFromUser());
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(ctxt, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        String usernameOnly = SipUri.parseSipContact(lastRequest.getFromUser()).userName;

        pairingRequestNotification.setWhen(when);
        pairingRequestNotification.setTicker(ctxt.getString(R.string.pairing_request_notif_title));
        pairingRequestNotification.setContentTitle(ctxt.getString(R.string.pairing_request_notif_title));
        pairingRequestNotification.setContentText(String.format(ctxt.getString(R.string.pairing_request_notif_text), usernameOnly));
        pairingRequestNotification.setContentIntent(contentIntent);

        // Show new notification.
        notify(PAIRING_REQUEST_NOTIF_ID, pairingRequestNotification.build(), pairingRequestNotification);
    }

    public void notifyMissingRecoveryEmail() {
        Intent notificationIntent = new Intent(ctxt, ChangeRecoveryEmailActivity.class);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(ctxt, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        NotificationCompat.Builder missingRecEmailNotification = new NotificationCompat.Builder(ctxt);
        missingRecEmailNotification.setSmallIcon(R.drawable.ic_report_problem_white_24px)
                .setOngoing(false)
                .setDefaults(Notification.DEFAULT_ALL)
                .setOnlyAlertOnce(false)
                .setWhen(System.currentTimeMillis())
                .setTicker(ctxt.getString(R.string.missing_recovery_email))
                .setContentTitle(ctxt.getString(R.string.application_name))
                .setContentText(ctxt.getString(R.string.missing_recovery_email_desc))
                .setContentIntent(contentIntent);

        Notification notification = missingRecEmailNotification.build();
        notification.flags |= Notification.FLAG_NO_CLEAR;

        notify(MISSING_REC_EMAIL_NOTIF_ID, notification, missingRecEmailNotification);
    }

    /**
     * Show notification for certificate update.
     */
    public void notifyCertificateUpdate() {
        long when = System.currentTimeMillis();
        PhonexSettings.loadDefaultLanguageNoThrow(ctxt);

        if (certUpdNotification == null) {
            certUpdNotification = new NotificationCompat.Builder(ctxt);
//            certUpdNotification.setSmallIcon(R.drawable.stat_sys_gen_key);
            certUpdNotification.setSmallIcon(R.drawable.ic_lock_white_24px);
            certUpdNotification.setOngoing(true);
            certUpdNotification.setOnlyAlertOnce(false);
        }

        Intent notificationIntent = XService.buildIntent(ctxt, Intents.ACTION_CERT_UPD_NOTIFICATION);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(ctxt, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        certUpdNotification.setTicker(ctxt.getText(R.string.notification_certupd));
        certUpdNotification.setWhen(when);
        certUpdNotification.setContentTitle(ctxt.getText(R.string.application_name));
        certUpdNotification.setContentText(ctxt.getText(R.string.notification_certupd));
        certUpdNotification.setContentIntent(contentIntent);

        Notification notification = certUpdNotification.build();
        notification.flags |= Notification.FLAG_NO_CLEAR;

        notify(CERTUPD_NOTIF_ID, notification, certUpdNotification);
    }

    public static void buildAndNotifyApplicationUpdate(Context context) {
        PackageManager pm = context.getPackageManager();
        PackageInfo info = null;
        try {
            info = pm.getPackageInfo(context.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.ef(TAG, "Cannot retrieve package info for package name [%s]", context.getPackageName());
            return;
        }

        String versionName = info.versionName;
        int versionCode = info.versionCode;
        Log.i(TAG, "Package has been replaced: " + context.getPackageName() + "");

        StatusbarNotifications notifications = new StatusbarNotifications(context);
        notifications.notifyApplicationUpdate(versionName, versionCode);
    }

    /**
     * Show notification when application package is updated.
     */
    public void notifyApplicationUpdate(String versionName, int versionCode) {
        PhonexSettings.loadDefaultLanguageNoThrow(ctxt);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctxt);
        builder.setSmallIcon(R.drawable.ic_sync);
        builder.setOngoing(true);
        builder.setDefaults(Notification.DEFAULT_ALL);
        builder.setOnlyAlertOnce(false);

        Intent notificationIntent = new Intent(ctxt, IntroActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(ctxt, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        builder.setTicker(ctxt.getText(R.string.notification_appupd));
        builder.setWhen(System.currentTimeMillis());
        builder.setContentTitle(ctxt.getText(R.string.notification_appupd));
        builder.setContentText(String.format(ctxt.getString(R.string.notification_appupd_text), String.valueOf(versionCode)));
        builder.setContentIntent(contentIntent);

        Notification notification = builder.build();
        notification.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_AUTO_CANCEL ;

        notify(APPUDP_NOTIF_ID, notification, builder);
    }

    /**
     * Show notification when application package is updated.
     */
    public void notifyDeviceRebooted() {
        PhonexSettings.loadDefaultLanguageNoThrow(ctxt);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctxt);
        builder.setSmallIcon(R.drawable.svg_logo_square_small_bw);
        builder.setOngoing(true);
        builder.setDefaults(Notification.DEFAULT_ALL);
        builder.setOnlyAlertOnce(false);

        Intent notificationIntent = new Intent(ctxt, IntroActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(ctxt, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        builder.setTicker(ctxt.getText(R.string.notification_device_reboot));
        builder.setWhen(System.currentTimeMillis());
        builder.setContentTitle(ctxt.getText(R.string.notification_device_reboot));
        builder.setContentText(ctxt.getText(R.string.notification_device_reboot_text));
        builder.setContentIntent(contentIntent);

        Notification notification = builder.build();
        notification.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_AUTO_CANCEL ;

        notify(APPUDP_NOTIF_ID, notification, builder);
    }

    public void notifyLicenseExpiration(WarningType warningType) {
        PhonexSettings.loadDefaultLanguageNoThrow(ctxt);
        licExpWarningType = warningType;
        notifyLicenseExpiration();
    }

    private void notifyLicenseExpiration(){
        if (licExpWarningType == null){
            Log.e(TAG, "Empty warning type");
            return;
        }

        CharSequence title = null;
        String text = null;
        Intent notificationIntent = null;
        int strLicExpTitle;
        int strLicExpText;

        switch (licExpWarningType){
            case WEEK_TO_EXPIRE:{
                strLicExpTitle = R.string.notification_license_expires_title;
                strLicExpText = R.string.notification_license_expires_week_text;
                notificationIntent = new Intent(ctxt, ManageLicenseActivity.class);
                break;
            }
            case DAY_TO_EXPIRE: {
                strLicExpTitle = R.string.notification_license_expires_title;
                strLicExpText = R.string.notification_license_expires_day_text;
                notificationIntent = new Intent(ctxt, ManageLicenseActivity.class);
                break;
            }
            case EXPIRED:
                // license has already expired
                notificationIntent = new Intent(ctxt, ManageLicenseActivity.class);
                strLicExpTitle = R.string.notification_license_expired;
                strLicExpText = R.string.notification_license_expired_text;
                break;
            default:
                return;
        }

        licExpNotification = new NotificationCompat.Builder(ctxt);
        licExpNotification.setSmallIcon(R.drawable.ic_trial);
        licExpNotification.setOngoing(false);
        licExpNotification.setDefaults(Notification.DEFAULT_ALL);
        licExpNotification.setOnlyAlertOnce(false);

        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent contentIntent = PendingIntent.getActivity(ctxt, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        title = ctxt.getText(strLicExpTitle);
        text = ctxt.getString(strLicExpText);

        licExpNotification.setTicker(title);
        licExpNotification.setWhen(System.currentTimeMillis());
        licExpNotification.setContentTitle(title);
        licExpNotification.setContentText(text);
        licExpNotification.setContentIntent(contentIntent);

        Notification notification = licExpNotification.build();
        notification.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_AUTO_CANCEL;
        notify(LICENSE_EXPIRATION_NOTIF_ID, notification, licExpNotification);
    }

    public void notifyLicenseUpdated(SipProfile profile) {
        PhonexSettings.loadDefaultLanguageNoThrow(ctxt);
        if (profile == null) {
            return;
        }

        licUpdInformation = profile.getLicenseInformation();
        notifyLicenseUpdated();
    }

    private void notifyLicenseUpdated(){
        if (licUpdInformation == null){
            Log.e(TAG, "Empty license information");
            return;
        }

        licUpdNotification = new NotificationCompat.Builder(ctxt);
        licUpdNotification.setSmallIcon(R.drawable.ic_trial);
        licUpdNotification.setOngoing(false);
        licUpdNotification.setDefaults(Notification.DEFAULT_ALL);
        licUpdNotification.setOnlyAlertOnce(false);

        Intent notificationIntent = new Intent(ctxt, PhonexActivity.class);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent contentIntent = PendingIntent.getActivity(ctxt, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        licUpdNotification.setTicker(ctxt.getText(R.string.notification_license_updated));
        licUpdNotification.setWhen(System.currentTimeMillis());
        licUpdNotification.setContentTitle(ctxt.getText(R.string.notification_license_updated));

        String contentText = String.format(ctxt.getString(R.string.notification_license_updated_text),
                licUpdInformation.getFormattedLicenseType(ctxt),
                licUpdInformation.getFormattedExpiration(PhonexSettings.loadDefaultLanguage(ctxt)));

        licUpdNotification.setContentText(contentText);
        licUpdNotification.setContentIntent(contentIntent);

        Notification notification = licUpdNotification.build();
//        notification.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_AUTO_CANCEL;
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        notify(LICENSE_EXPIRATION_NOTIF_ID, notification, licUpdNotification);
    }

    /**
     * Sets the current conversation contact so the new messages are not displayed in the Android
     * notifications. We can see it directly.
     *
     * @param contact SIP address of the remote contact.
     */
    public void setCurrentConversationContact(String contact) {
        Log.vf(TAG, "Currently conversating with: %s, prev=%s, last=%s",
                contact,
                currentConversationContact,
                lastMessageNotificationContact);

        // If conversation fragment is dismissing, null is set here.
        // If it is so, and we have displayed notification from the
        // user we were chatting with, close it since message was already read.
        // Only purpose of the notification was to alert user on new incoming message.
        if (contact==null
                && currentConversationContact!=null
                && currentConversationContact.equalsIgnoreCase(lastMessageNotificationContact)){
            cancelMessages();
        }

        currentConversationContact = contact;
        if (currentConversationContact==null){
            return;
        }

        // Normalize so comparisson has exact semantics.
        currentConversationContact = SipUri.getCanonicalSipContact(contact, false);

        // If current conversation contact is the one we have last message notification, cancel it.
        if (currentConversationContact.equalsIgnoreCase(lastMessageNotificationContact)){
            cancelMessages();
        }
    }

    /**
     * Handles incomming intent notification about current chat.
     * @param intent
     */
    public void setChatInfo(Intent intent){
        if (Intents.ACTION_SET_CHAT_INFO.equals(intent.getAction())==false
                || intent.hasExtra(EXTRA_CHAT_INFO_NAME)==false){
            return;
        }

        final String contact = intent.getStringExtra(EXTRA_CHAT_INFO_NAME);
        setCurrentConversationContact(contact);
    }

    /**
     * Generates new intent setting current chat contact to the service handling notifications.
     * @param ctxt
     * @param contact
     */
    public static void setCurrentConversationInfo(Context ctxt, String contact){
        final Intent intent = XService.buildIntent(ctxt, Intents.ACTION_SET_CHAT_INFO);
        intent.putExtra(EXTRA_CHAT_INFO_NAME, contact);
        MiscUtils.sendBroadcast(ctxt, intent);
    }

    /**
     * Ticker label builder.
     * @param context
     * @param address
     * @param body
     * @return
     */
    private static CharSequence buildTickerLabel(Context context, String address, String body) {
        String displayAddress = address;

        StringBuilder buf = new StringBuilder(displayAddress == null ? "" : displayAddress.replace('\n', ' ').replace('\r', ' '));
        buf.append(':').append(' ');

        int offset = buf.length();

        if (!TextUtils.isEmpty(body)) {
            body = body.replace('\n', ' ').replace('\r', ' ');
            buf.append(body);
        }

        SpannableString spanText = new SpannableString(buf.toString());
        spanText.setSpan(new StyleSpan(Typeface.BOLD), 0, offset, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        return spanText;
    }

    /**
     * Cancellation of SIP registered notification.
     * Preserves android notification, to keep SIP service foreground and running.
     * Adds grayed-out account dummy placeholder.
     */
    public final void cancelRegisters() {
        if (!isServiceWrapper) {
            Log.e(TAG, "Trying to cancel a service notification from outside the service");
            return;
        }

        //stopForeground(REGISTER_NOTIF_ID);

        // Start timer for 5 seconds, show Phonex not registered then.
        // TODO: add this behavior to preferences.
        if (futureRegTask==null || futureRegTask.isDone()){
            Log.v(TAG, "Going to schedule unregistered notification");
            futureRegTask = texecutor.schedule(regTask, 5, TimeUnit.SECONDS);
        }
    }

    /**
     * Whether to display unregistered notification.
     * If application has been quit - do not display this icon.
     * User could be suspicious about background running service while there should be none.
     * User message notification only in case app has not been quit.
     * @return
     */
    protected boolean allowGreyAccountNotification(){
        if(prefs.getBoolean(PhonexConfig.APP_CLOSED_BY_USER, false)) {
            Log.v(TAG, "App has been quit, no status notification");
            return false;
        }

        // 1. load shared info from in-memory database
        StoredCredentials creds = MemoryPrefManager.loadCredentials(ctxt);
        if (creds==null || TextUtils.isEmpty(creds.getUserSip())){
            return false;
        }

        return true;
    }

    /**
     * Task to display empty notifications.
     * @author ph4r05
     */
    private class UnregisteredPhonexTask implements Runnable {

        @Override
        public void run() {
            Log.v(TAG, "Unregistered notification task");

            // If application has been quit - do not display this icon.
            // User could be suspicious about background running service while there should be none.
            // User message notification only in case app has not been quit.
            if(!allowGreyAccountNotification()) {
                Log.v(TAG, "App has been quit, no status notification");
                return;
            }

            SipProfileState s = new SipProfileState();
            s.setActive(false);
            s.setDisplayName(PhonexAccountManager.getLabel());
            s.setAccountManager(PhonexAccountManager.getId());

            ArrayList<SipProfileState> activeAccountsInfos = new ArrayList<SipProfileState>();
            activeAccountsInfos.add(s);

            notifyRegisteredAccounts(activeAccountsInfos, true);
        }
    }

    /**
     * Really cancels android SIP-registered notification.
     */
    public final void cancelRegistersReal() {
        if (!isServiceWrapper) {
            Log.e(TAG, "Trying to cancel a service notification from outside the service");
            return;
        }

        regStatePrevRegistered=false;
        stopForeground(REGISTER_NOTIF_ID);

        if (futureRegTask!=null && !futureRegTask.isDone()){
            futureRegTask.cancel(false);
        }
    }

    public final void cancelCalls() {
        cancel(CALL_NOTIF_ID);
    }

    public final void cancelMissedCalls() {
        cancel(CALLLOG_NOTIF_ID);
    }

    public final void cancelMessages() {
        Log.v(TAG, "Cancel messages notification");
        cancel(MESSAGE_NOTIF_ID);
        lastMessageNotificationContact = null;
    }

    public final void cancelWarnings(){
        cancel(WARNING_NOTIF_ID);
    }

    public final void cancelKeygen(){
        cancel(KEYGEN_NOTIF_ID);
    }

    public final void cancelAppUpdateNotif(){
        cancel(APPUDP_NOTIF_ID);
    }

    public final void cancelDeviceRebootedNotif(){
        cancel(DEVICEREBOOT_NOTIF_ID);
    }

    public final void canceLicenseUpdatedNotif(){
        cancel(LICENSE_NOTIF_ID);
    }

    public final void cancelPairingRequestNotif(){
        cancel(PAIRING_REQUEST_NOTIF_ID);
    }

    public synchronized final void cancelCertUpd(){
        cancel(CERTUPD_NOTIF_ID);
    }

    public final void cancel(int notificationId){
        notificationManager.cancel(notificationId);
        notificationVisibility.put(notificationId, false);
    }

    private void notify(int notificationId, Notification notification, NotificationCompat.Builder builder){
        notificationManager.notify(notificationId, notification);
        notificationVisibility.put(notificationId, true);
    }

    /**
     * Place here all notifications that should be cancelled after successful login
     */
    public void onLoginFinished() {
        cancelAppUpdateNotif();
        cancelDeviceRebootedNotif();
    }

    public final void cancelAll() {
        // Do not cancel calls notification since it's possible that there is
        // still an ongoing call.
        Log.v(TAG, "Cancel all notifs");
        if (isServiceWrapper) {
            cancelRegistersReal();
        }
        cancelMessages();
        cancelMissedCalls();
        cancelWarnings();
        cancelKeygen();
        cancelCertUpd();
        cancelAppUpdateNotif();
        cancelDeviceRebootedNotif();
        canceLicenseUpdatedNotif();

        cancel(LICENSE_EXPIRATION_NOTIF_ID);
        cancelPairingRequestNotif();
        cancelMissingRecoveryEmailNotif();
    }

    public void cancelMissingRecoveryEmailNotif() {
        cancel(MISSING_REC_EMAIL_NOTIF_ID);
    }

    /**
     * Returns true if notification is visible on the screen.
     * Warning! If notification was not canceled programmatically but user clicked on it / deleted it
     * it won't be reflected in this result. In order to make it work also in these scenarios, this
     * manager would have to set another content and deleted intents and monitor them.
     * @param notifId
     * @return
     */
    public boolean isNotificationVisible(int notifId){
        final Boolean bVis = notificationVisibility.get(notifId);
        return bVis != null && bVis;
    }

    private void regenerateRegNotif(){
        if (!isNotificationVisible(REGISTER_NOTIF_ID) || appRegNotification == null){
            return;
        }

        appRegNotification.setTicker(ctxt.getString(strRegTickerId));
        appRegNotification.setContentText(ctxt.getString(strRegContentId));
        Notification notif = appRegNotification.build();
        regNotifPostProcess(notif);
        startForeground(REGISTER_NOTIF_ID, notif, appRegNotification);
    }

    /**
     * Called when settings are changed.
     */
    private synchronized void onSettingsChanged(){
        PhonexSettings.loadDefaultLanguageNoThrow(ctxt);

        // Regenerate long-term notifications after possible language change.
        regenerateRegNotif();
    }

    /**
     * Custom intent receiver.
     */
    private class BcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intents.ACTION_SETTINGS_MODIFIED.equals(action)){
                onSettingsChanged();
            }
        }
    }
}

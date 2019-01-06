package net.phonex.core;

import net.phonex.db.entity.SipCallSession;
import net.phonex.db.entity.SipProfile;

/**
 * Created by miroc on 29.8.15.
 */
public class Intents {
    /**
     * Action launched when a sip call is ongoing.
     * <p>
     * Provided extras :
     * <ul>
     * <li>{@link #EXTRA_CALL_INFO} a {@link SipCallSession} containing infos of
     * the call</li>
     * </ul>
     * </p>
     */
    public static final String ACTION_SIP_CALL_UI = "net.phonex.phone.action.INCALL";
    /**
     * Action launched when the contact list icon clicked.<br/>
     * Should raise the contact list.
     */
    public static final String ACTION_CONTACT_LIST = "net.phonex.phone.action.CONTACT_LIST";
    /**
     * Action launched when a missed call notification entry is clicked.<br/>
     * Should raise call logs list.
     */
    public static final String ACTION_NOTIFICATIONS = "net.phonex.phone.action.NOTIFICATIONS";
    /**
     * Action launched when a sip message notification entry is clicked.<br/>
     * Should raise the sip message list.
     */
    public static final String ACTION_SIP_MESSAGES = "net.phonex.phone.action.MESSAGES";
    /**
     * Action launched to enter global phonex settings.<br/>
     */
    public static final String ACTION_UI_PREFS_GLOBAL = "net.phonex.ui.action.PREFS_GLOBAL";
    /**
     * Action launched when a sip signature warning notification entry is clicked.<br/>
     * Should raise warning tab
     */
    public static final String ACTION_SIP_SIGNATURE_WARNING = "net.phonex.ui.action.SIPWARNING";
    /**
     * Action launched when a key generation notification entry is clicked.<br/>
     * TODO: on click display new pop-up (separate activity from main screen, bound to service).
     * Progress could be indicated in this pop-up, user will be able to cancel the task
     * or reschedule it or disable it.
     */
    public static final String ACTION_KEY_GEN_NOTIFICATION = "net.phonex.ui.action.KEYGEN";
    /**
     * Action launched when a certificate update notification entry is clicked.<br/>
     * TODO: on click display new pop-up (separate activity from main screen, bound to service).
     * Progress could be indicated in this pop-up, user will be able to cancel the task
     * or reschedule it or disable it.
     */
    public static final String ACTION_CERT_UPD_NOTIFICATION = "net.phonex.ui.action.CERTUPD";
    public static final String ACTION_LOGOUT = "net.phonex.phone.action.LOGOUT";
    public static final String EXTRA_LOGOUT_SECOND_DEVICE_DETECTED = "extra_logout_second_device_detected";
    /**
     * Broadcast sent when call state has changed.
     * <p>
     * Provided extras :
     * <ul>
     * <li>{@link #EXTRA_CALL_INFO} a {@link SipCallSession} containing infos of
     * the call</li>
     * </ul>
     * </p>
     */
    public static final String ACTION_SIP_CALL_CHANGED = "net.phonex.service.CALL_CHANGED";
    /**
     * Broadcast sent when sip account has been changed.
     * <p>
     * Provided extras :
     * <ul>
     * <li>{@link SipProfile#FIELD_ID} the long id of the account</li>
     * </ul>
     * </p>
     */
    public static final String ACTION_SIP_ACCOUNT_CHANGED = "net.phonex.service.ACCOUNT_CHANGED";
    /**
     * Broadcast sent when sip account registration has changed.
     * <p>
     * Provided extras :
     * <ul>
     * <li>{@link SipProfile#FIELD_ID} the long id of the account</li>
     * </ul>
     * </p>
     */
    public static final String ACTION_SIP_REGISTRATION_CHANGED = "net.phonex.service.REGISTRATION_CHANGED";
    /**
     *  Broadcast sent when sip account registration has changed - account is registered
     */
    public static final String ACTION_SIP_REGISTRATION_CHANGED_REGISTERED = "net.phonex.service.REGISTRATION_CHANGED_REGISTERED";
    /**
     * Broadcast sent when sip account registration has changed - registration is lost
     */
    public static final String ACTION_SIP_REGISTRATION_CHANGED_UNREGISTERED = "net.phonex.service.REGISTRATION_CHANGED_UNREGISTERED";
    /**
     * Broadcast sent when the state of device media has been changed.
     */
    public static final String ACTION_SIP_MEDIA_CHANGED = "net.phonex.service.MEDIA_CHANGED";
    /**
     * Broadcast sent when a ZRTP SAS
     */
    public static final String ACTION_ZRTP_SHOW_SAS = "net.phonex.service.SHOW_SAS";
    /**
     * Broadcast sent when a general message has ben received
     * This means that message was inserted in MESSAGE QUEUE
     */
    public static final String ACTION_MESSAGE_RECEIVED = "net.phonex.service.MESSAGE_RECEIVED";
    /**
     * Broadcast sent when a message has been received.<br/>
     * By message here, we mean a SIP SIMPLE message of the sip simple protocol. Understand a chat / im message.
     */
    public static final String ACTION_SIP_MESSAGE_RECEIVED = "net.phonex.service.SIP_MESSAGE_RECEIVED";
    /**
     * Broadcast sent when user enters fragment with messages or with conversation list.
     */
    public static final String ACTION_CHECK_MESSAGE_DB = "net.phonex.service.CHECK_MESSAGE_DB";
    public static final String ALARM_MESSAGE_RESEND = "net.phonex.service.ALARM_MESSAGE_RESEND";
    /**
     * Alarm for removing expired messages (when message archivation is not set for unlimited time)
     */
    public static final String ALARM_MESSAGE_EXPIRATION_CHECK = "net.phonex.service.ALARM_MESSAGE_EXPIRATION_CHECK";
    public static final String ALARM_LICENSE_EXPIRATION_CHECK = "net.phonex.service.ALARM_LICENSE_EXPIRATION_CHECK";
    /**
     * Alarm for saving authentication state for auto login feature.
     */
    public static final String ALARM_AUTO_LOGIN_SAVE= "net.phonex.service.ALARM_AUTO_LOGIN_SAVE";

    public static final String ACTION_SETTINGS_MODIFIED = "net.phonex.service.ACTION_SETTINGS_MODIFIED";
    /**
     * Broadcast to send when the sip service can be stopped.
     */
    public static final String ACTION_SIP_CAN_BE_STOPPED = "net.phonex.service.ACTION_SIP_CAN_BE_STOPPED";
    /**
     * Broadcast to send when the sip service should be restarted.
     */
    public static final String ACTION_SIP_REQUEST_RESTART = "net.phonex.service.ACTION_SIP_REQUEST_RESTART";
    /**
     * Broadcast to send when the sip service should be restarted by a brute force.
     */
    public static final String ACTION_SIP_REQUEST_BRUTAL_RESTART = "net.phonex.service.ACTION_SIP_REQUEST_BRUTAL_RESTART";
    /**
     * Broadcast to send when your activity doesn't allow anymore user to make outgoing calls.<br/>
     * You have to pass registered {@link #EXTRA_OUTGOING_ACTIVITY}
     *
     * @see #EXTRA_OUTGOING_ACTIVITY
     */
    public static final String ACTION_OUTGOING_UNREGISTER = "net.phonex.service.ACTION_OUTGOING_UNREGISTER";
    /**
     * Broadcast to send when you have launched a sip action (such as make call), but that your app will not anymore allow user to make outgoing calls actions.<br/>
     * You have to pass registered {@link #EXTRA_OUTGOING_ACTIVITY}
     *
     * @see #EXTRA_OUTGOING_ACTIVITY
     */
    public static final String ACTION_DEFER_OUTGOING_UNREGISTER = "net.phonex.service.ACTION_DEFER_OUTGOING_UNREGISTER";
    /**
     * Broadcast to send when there is a reason to trigger DH key sync task.
     */
    public static final String ACTION_TRIGGER_DHKEY_SYNC = "net.phonex.service.ACTION_TRIGGER_DHKEY_SYNC";
    public static final String ACTION_TRIGGER_DHKEY_SYNC_NOW = "net.phonex.service.ACTION_TRIGGER_DHKEY_SYNC_NOW";

    /**
     * Pairing request Intents + extras
     */
    public static final String ACTION_TRIGGER_PAIRING_REQUEST_UPDATE = "net.phonex.service.ACTION_TRIGGER_PAIRING_REQUEST_UPDATE";
    public static final String EXTRA_PAIRING_REQUEST_RESOLUTION = "pairingRequestResolution";
    public static final String EXTRA_PAIRING_REQUEST_ID = "pairingRequestId";
    public static final String ACTION_PAIRING_REQUEST_UPDATE_PROGRESS = "net.phonex.service.ACTION_PAIRING_REQUEST_UPDATE_PROGRESS";


    /**
     * Add contact Intents + extras
     */
    public static final String ACTION_TRIGGER_ADD_CONTACT = "net.phonex.service.ACTION_TRIGGER_ADD_CONTACT";
    public static final String EXTRA_ADD_CONTACT_SIP = "addContactSip";
    public static final String EXTRA_ADD_CONTACT_ALIAS = "addContactAlias";
    public static final String ACTION_ADD_CONTACT_PROGRESS = "net.phonex.service.ACTION_ADD_CONTACT_PROGRESS";

    /**
     * Log upload + extras
     */
    public static final String ACTION_TRIGGER_LOG_UPLOAD = "net.phonex.service.ACTION_TRIGGER_LOG_UPLOAD";
    public static final String EXTRA_LOG_UPLOAD_USER_MESSAGE = "userMessage";

    /*
     * Login intent + extras
     */
    public static final String ACTION_TRIGGER_LOGIN = "net.phonex.service.ACTION_TRIGGER_LOGIN";
    public static final String ACTION_TRIGGER_QUICK_LOGIN = "net.phonex.service.ACTION_TRIGGER_QUICK_LOGIN";
    public static final String EXTRA_LOGIN_SIP = "loginSip";
    public static final String EXTRA_LOGIN_PASSWORD = "loginPassword";
    public static final String EXTRA_LOGIN_DOMAIN = "loginDomain";

    public static final String ACTION_CANCEL_LOGIN = "net.phonex.service.ACTION_CANCEL_LOGIN";

    /**
     * Gcm messages passing
     */
    public static final String ACTION_GCM_MESSAGES_RECEIVED = "net.phonex.service.ACTION_GCM_MESSAGES_RECEIVED";
    public static final String EXTRA_GCM_MESSAGES = "gcmMessages";

    /**
     * Save purchase progress
     */
    public static final String ACTION_SAVE_PURCHASE_PROGRESS = "net.phonex.service.ACTION_SAVE_PURCHASE_PROGRESS";
    public static final String ACTION_RESTORE_PURCHASE_PROGRESS = "net.phonex.service.ACTION_RESTORE_PURCHASE_PROGRESS";

    /**
     * Login progress intents
     */
    public static final String ACTION_LOGIN_PROGRESS = "net.phonex.service.ACTION_LOGIN_PROGRESS";
    public static final String ACTION_LOGIN_CANCELLED = "net.phonex.service.ACTION_LOGIN_CANCELLED";
    public static final String EXTRA_LOGIN_CANCELLED_ERROR_TITLE = "loginErrorTitle";
    public static final String EXTRA_LOGIN_CANCELLED_ERROR_MESSAGE = "loginErrorMessage";
    public static final String ACTION_LOGIN_FINISHED = "net.phonex.service.ACTION_LOGIN_FINISHED";
    public static final String ACTION_LOGIN_PASSWORD_CHANGE = "net.phonex.service.ACTION_LOGIN_PASSWORD_CHANGE";
    public static final String EXTRA_LOGIN_PASSWORD_CHANGE_PARAMS = "loginPasswordChangeParams";

    /**
     * Generic progress extra
     */
    public static final String EXTRA_GENERIC_PROGRESS = "genericProgress";

    /**
     * Broadcast to send when there is a certificate push update notification.
     */
    public static final String ACTION_CERT_UPDATE = "net.phonex.service.ACTION_CERT_UPDATE";
    public static final String CERT_INTENT_PARAMS = "params";
    public static final String CERT_INTENT_ALLUSERS = "allUsers";
    /**
     * Broadcast to send when there is an update in certificate refresh.
     */
    public static final String ACTION_CERT_UPDATE_PROGRESS = "net.phonex.service.ACTION_CERT_UPDATE_PROGRESS";
    public static final String CERT_INTENT_PROGRESS = "progress";
    /**
     * Broadcast to send when there is an update in DH key generation.
     */
    public static final String ACTION_KEYGEN_UPDATE_PROGRESS = "net.phonex.service.ACTION_KEYGEN_UPDATE_PROGRESS";
    public static final String KEYGEN_INTENT_PROGRESS = "progress";
    /**
     * Broadcast to send when there is update in file upload progress.
     */
    public static final String ACTION_FILEUPLOAD_PROGRESS = "net.phonex.service.ACTION_FILEUPLOAD_PROGRESS";
    public static final String FILEUPLOAD_INTENT_PROGRESS = "progress";
    public static final String ACTION_FILEDOWNLOAD_PROGRESS = "net.phonex.service.ACTION_FILEDOWNLOAD_PROGRESS";
    public static final String FILEDOWNLOAD_INTENT_PROGRESS = "progress";
    public static final String ACTION_UPDATE_FT_PROGRESS = "net.phonex.service.ACTION_UPDATE_FT_PROGRESS";
    public static final String EXTRA_UPDATE_FT_PROGRESS = "progress";
    /**
     * Broadcast to send when there is update in file decryption on user request progress.
     */
    public static final String ACTION_SECURE_STORAGE_DECRYPT_PROGRESS = "net.phonex.service.ACTION_SECURE_STORAGE_DECRYPT_PROGRESS";
    public static final String SECURE_STORAGE_DECRYPT_INTENT_PROGRESS = "progress";
    /**
     * Intent broadcast when a new missed call was detected.
     */
    public static final String ACTION_MISSED_CALL_RECEIVED = "net.phonex.service.ACTION_MISSED_CALL_RECEIVED";
    public static final String EXTRA_MISSED_CALL_RECEIVED = "missedCall";
    /**
     * When user requests file transfer progress (pull) for some transfer, broadcasts this intent.
     */
    public static final String ACTION_REQUEST_TRANSFER_PROGRESS = "net.phonex.service.ACTION_REQUEST_TRANSFER_PROGRESS";
    public static final String EXTRA_REQUEST_TRANSFER_PROGRESS = "net.phonex.service.EXTRA_REQUEST_TRANSFER_PROGRESS";
    /**
     * When user clicks download button, reflect it to the UI asap.
     */
    public static final String ACTION_INIT_DOWNLOAD_PROCESS = "net.phonex.service.ACTION_INIT_DOWNLOAD_PROCESS";
    public static final String EXTRA_INIT_DOWNLOAD_PROCESS = "net.phonex.service.EXTRA_INIT_DOWNLOAD_PROCESS";
    public static final String EXTRA_INIT_DOWNLOAD_PROCESS_ACCEPT = "net.phonex.service.EXTRA_INIT_DOWNLOAD_PROCESS_ACCEPT";
    /**
     * After user confirms file rejection this intent is broadcasted from confirm dialog.
     */
    public static final String ACTION_REJECT_FILE_CONFIRMED = "net.phonex.service.ACTION_REJECT_FILE_CONFIRMED";
    public static final String EXTRA_REJECT_FILE_CONFIRMED_MSGID = "net.phonex.service.EXTRA_REJECT_FILE_CONFIRMED_MSGID";
    public static final String ACTION_ACCEPT_FILE_CONFIRMED = "net.phonex.service.ACTION_ACCEPT_FILE_CONFIRMED";
    public static final String EXTRA_ACCEPT_FILE_CONFIRMED_MSGID = "net.phonex.service.EXTRA_ACCEPT_FILE_CONFIRMED_MSGID";
    /**
     * Extra package for resulting data from file picker fragment.
     */
    public static final String EXTRA_FILES_PICKED_PATHS = "net.phonex.service.EXTRA_FILES_PICKED_PATHS";
    /**
     * Extra package for resulting action from file picker fragment.
     */
    public static final String EXTRA_FILES_PICKED_ACTION = "net.phonex.service.EXTRA_FILES_PICKED_ACTION";
    /**
     * Broadcast sent after successful login into the application (after contactlist is loaded).
     */
    public static final String ACTION_LOGIN_SUCCESSFUL = "net.phonex.service.ACTION_LOGIN_SUCCESSFUL";
    /**
     * Broadcast sent after a new user has been added successfully (e.g., for delayed presence updates).
     */
    public static final String ACTION_USER_ADDED = "net.phonex.service.ACTION_USER_ADDED";
    /**
     * Extra key to contains infos about a sip call.<br/>
     * @see SipCallSession
     */
    public static final String EXTRA_CALL_INFO = "call_info";
    public static final String EXTRA_CALL_DESTINATION = "call_dest";
    /**
     * Tell sip service that it's an user interface requesting for outgoing call.<br/>
     * It's an extra to add to sip service start as string representing unique key for your activity.<br/>
     * We advise to use your own component name {@link android.content.ComponentName} to avoid collisions.<br/>
     * Each activity is in charge unregistering broadcasting
     * {@link Intents#ACTION_OUTGOING_UNREGISTER} or
     * {@link Intents#ACTION_DEFER_OUTGOING_UNREGISTER}<br/>
     *
     * @see android.content.ComponentName
     */
    public static final String EXTRA_OUTGOING_ACTIVITY = "outgoing_activity";
    /**
     * Extra key to tell if intent is sent from onBoot() event.<br/>
     */
    public static final String EXTRA_ON_BOOT = "on_boot";
    /**
     * Extra key for onBind() call intents, tells to XService that it should try
     * to auto-start PjStack if it is not started.
     */
    public static final String EXTRA_AUTO_START_STACK = "auto_start_stack";
    public static final String ACTION_HIDE_CALLLOG_NOTIF = "net.phonex.HIDE_CALLLOG_NOTIF";
    public static final String ACTION_SET_CHAT_INFO = "net.phonex.SET_CHAT_INFO";
    public static final String ACTION_RESURRECTED = "net.phonex.resurrected";
    public static final String ACTION_CHECK_LOGIN = "net.phonex.checklogin";
    public static final String ACTION_KILL = "net.phonex.KILL";
    public static final String ACTION_CALL_CUSTOM = "net.phonex.CALL";
    public static final String ACTION_LOGGER_CHANGE = "net.phonex.logger.CHANGED";
    public static final String EXTRA_LOGGER_ENABLE = "net.phonex.logger.enable";
    public static final String ACTION_FOREGROUND = "net.phonex.FOREGROUND";
    public static final String EXTRA_FOREGROUND_ENABLE = "net.phonex.foreground.enable";
    public static final String EXTRA_FOREGROUND_ID = "net.phonex.foreground.id";
    public static final String EXTRA_FOREGROUND_NOTIF = "net.phonex.foreground.notif";
    public static final String ACTION_CONNECTIVITY_CHANGE = "net.phonex.connectivity.action.CHANGED";
    public static final String EXTRA_CONNECTIVITY_CHANGE = "net.phonex.connectivity.extra.CHANGED";
    public static final String ACTION_QUIT_SAFENET = "net.phonex.QUIT_SAFENET";
    public static final String EXTRA_LOCK_PIN_AFTER_STARTUP = "lock_pin_after_startup";
}

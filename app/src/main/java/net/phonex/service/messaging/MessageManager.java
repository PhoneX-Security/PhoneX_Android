package net.phonex.service.messaging;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.MergeCursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.LruCache;

import net.phonex.PhonexSettings;
import net.phonex.core.Intents;
import net.phonex.core.MemoryPrefManager;
import net.phonex.db.DBProvider;
import net.phonex.db.entity.SipProfile;
import net.phonex.core.SipUri;
import net.phonex.core.SipUri.ParsedSipContactInfos;
import net.phonex.db.entity.FileTransfer;
import net.phonex.db.entity.QueuedMessage;
import net.phonex.db.entity.SipMessage;
import net.phonex.db.entity.UserCertificate;
import net.phonex.ft.FTHolder;
import net.phonex.ft.transfer.DownloadFileParams;
import net.phonex.ft.transfer.FileTransferManager;
import net.phonex.ft.transfer.UploadFileParams;
import net.phonex.pub.parcels.CertUpdateProgress;
import net.phonex.pub.parcels.CertUpdateProgress.CertUpdateStateEnum;
import net.phonex.pub.parcels.StoredCredentials;
import net.phonex.service.XService;
import net.phonex.service.runEngine.AppWakeLock;
import net.phonex.service.runEngine.MyWakeLock;
import net.phonex.soap.CertificateRefreshCall;
import net.phonex.ft.misc.Canceller;
import net.phonex.ft.misc.OperationCancelledException;
import net.phonex.util.FileTransferUtils;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.Registerable;
import net.phonex.util.crypto.CertificatesAndKeys;
import net.phonex.util.crypto.CertificatesAndKeys.UserIdentity;
import net.phonex.util.crypto.MessageDigest;
import net.phonex.util.guava.MapUtils;
import net.phonex.util.system.FilenameUtils;

import java.io.File;
import java.lang.ref.WeakReference;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


/**
 * This service now processes message_queue DB table containing QueuedMessage items
 *
 * @author ph4r05
 * @author miroc
 */
public class MessageManager implements MessageQueueActions, Registerable {
	private static final String TAG = "MessageManager";

	/**
	 * XService instance
	 */
	private XService service;

	/**
	 * Identity of the sender.
	 */
	final private UserIdentity identity = new UserIdentity();

	/**
	 * SecureRandom instance.
	 */
	private SecureRandom rand;

	/**
	 * Determines whether module is registered for operation.
	 */
	private boolean registered = false;

	/**
	 * Local certificate cache.
	 */
	final private LruCache<String, UserCertificate> certCache = new LruCache<String, UserCertificate>(3);

	/**
	 * Certificate observer - clears certificate cache if certificate is updated.
	 */
	private CertificateContentObserver certObserver = null;

	/**
	 * Message database observer - if message database is changed, it is checked for messages to process.
	 */
	private MessageContentObserver msgObserver = null;

	/**
	 * Receiver for message sent event.
	 */
	private MessageReceiver msgReceiver = null;

	/**
	 * File transfer manager instance.
	 */
	private FileTransferManager ftManager = null;

	/**
	 * If to try to fetch user certificate if it is missing, before sending message.
	 */
	private boolean fetchCertIfMissing = true;

	/**
	 * Operation cancellation.
	 */
	private Canceller canceller = null;

	/**
	 * CPU wakelock.
	 */
	protected final MyWakeLock wakeLock = new MyWakeLock(null, "net.phonex.MessageServiceLock", true);

	/**
	 * Handler wakelock.
	 */
	private AppWakeLock swakelock;

    private static final String EXECUTOR_NAME = "MessageManager.Handler";
    private static WorkerHandler msgHandler;
    private static HandlerThread handlerThread;
    private final Handler certHandler = new Handler();
    private WaitForConnectivityTask connTask = null;

    // Backoff resending variables
    public static final String EXTRA_TO = "EXTRA_TO";
    private final static int RESEND_BACKOFF_THRESHOLD = 3;
    private final static int RESEND_STACK_BACKOFF_THRESHOLD = 3;
    private Map<String, Long> futureResendAlarms = new ConcurrentHashMap<String, Long>();

    /**
     * Creates MessageManager.
     * Starts working handlers.
     *
     * @param service
     */
	public MessageManager(XService service) {
		this.service = service;
		this.rand = new SecureRandom();

		// Create worker thread for handler.
		if (handlerThread == null) {
            handlerThread = new HandlerThread(EXECUTOR_NAME);
            handlerThread.start();
        }

		// Create message handler.
        if (msgHandler == null) {
            msgHandler = new WorkerHandler(handlerThread.getLooper(), this);
        }

        // Creates sip wake lock - used in handler.
        swakelock = new AppWakeLock((PowerManager) service.getSystemService(Context.POWER_SERVICE), "net.phonex.MessageManager");
	}

    /**
	 * Finalize() - called by GC. Simple check for un-registration.
	 * To prevent programming errors to some extent.
	 */
	@Override
	protected void finalize() throws Throwable {
		if (registered){
			Log.e(TAG, "Module is still registered");
		}

		super.finalize();
	}

	/**
	 * Register for operation (e.g., registers content observers).
	 * Calling this effectively enables this module.
	 */
	public synchronized void register(){
		if (registered){
			Log.w(TAG, "Already registered");
			return;
		}

		// Register content observer for certificates -> update CertCache on change.
        if(msgObserver == null && service != null) {
        	msgObserver = new MessageContentObserver(getHandler());
        	service.getContentResolver().registerContentObserver(QueuedMessage.URI, true, msgObserver);
        }

        // Register certificate observer to clean certificate cache.
        if (certObserver == null && service != null) {
        	certObserver = new CertificateContentObserver(certHandler);
        	service.getContentResolver().registerContentObserver(UserCertificate.CERTIFICATE_URI, true, certObserver);
        }

        // Register receiver for message sent result.
        if (msgReceiver == null && service != null){
        	msgReceiver = new MessageReceiver(this);
        	IntentFilter filter = new IntentFilter();
        	filter.addAction(Intents.ACTION_MESSAGE_RECEIVED);
        	filter.addAction(Intents.ACTION_CHECK_MESSAGE_DB);
			filter.addAction(Intents.ACTION_REJECT_FILE_CONFIRMED);
			filter.addAction(Intents.ACTION_ACCEPT_FILE_CONFIRMED);
			filter.addAction(Intents.ACTION_SETTINGS_MODIFIED);
			MiscUtils.registerReceiver(service, msgReceiver, filter);
        }

        // Instantiate wake lock.
        if (wakeLock!=null){
        	wakeLock.initWakeLock(getContext());
        }

		PhonexSettings.loadDefaultLanguageNoThrow(getContext());
        Log.v(TAG, "MessageManager registered.");
		registered = true;
	}

	/**
	 * Unregisters listeners of this module.
	 * Has to be called during destruction of the XService.
	 */
	public synchronized void unregister(){
		if (!registered){
			Log.w(TAG, "Already unregistered");
			return;
		}

		// Unregister content observer
		if(msgObserver != null && service != null) {
			try {
				service.getContentResolver().unregisterContentObserver(msgObserver);
			} catch(Exception e){
				Log.e(TAG, "Exception during unregisterContentObserver(msgObserver)", e);
			}
        }

		// Unregister content observer
		if(certObserver != null && service != null) {
			try {
				service.getContentResolver().unregisterContentObserver(certObserver);
			} catch(Exception e){
				Log.e(TAG, "Exception during unregisterContentObserver(certObserver)", e);
			}
        }

		// Unregister receiver for message sent result.
        if (msgReceiver != null && service != null){
        	try {
        		service.unregisterReceiver(msgReceiver);
			} catch(Exception e){
				Log.e(TAG, "Exception during unregisterReceiver(msgReceiver)", e);
			}
        }

        // Free wake lock.
        if (wakeLock!=null){
        	try {
        		wakeLock.deinit();
        	} catch(Exception e){
				Log.e(TAG, "Exception during wakeLock.deinit()", e);
			}
        }

        // Free sip wake lock
        try {
        	swakelock.reset();
        } catch(Exception e){
        	Log.e(TAG, "Exception during swakeLock.reset()", e);
        }

        Log.v(TAG, "MessageManager unregistered.");
		registered = false;
	}

    /**
     * Event triggered on connectivity change.
     * @param recovered    If true, connectivity was recovered, otherwise lost.
     */
    public void onConnectivityChange(boolean recovered){
        // If connectivity was recovered, schedule task to refresh the database.
        // There may be messages in the queue qaiting to be sent.
        if (!recovered){
            return;
        }

        // If already running, give it up.
        if (connTask!=null){
            if (connTask.isAlive()) {
                return;
            } else {
                connTask = null; // cleanup.
            }
        }

        connTask = new WaitForConnectivityTask();
        connTask.start();
        Log.v(TAG, "Connectivity thread started");
    }

    /**
     * Event triggered as some account get logged in and may be used to send waiting messages.
     */
    public void onAccountLoggedIn(){
        // If connection task is running, do nothing. It will be triggered later.
        if (connTask!=null && connTask.isAlive()){
            return;
        }

		// Cleaning database, sanitizing back to consistent state.
		dbCheckTask(true);

        // Trigger database check.
        Log.v(TAG, "Triggering database check by account change.");
        triggerCheck();
    }

    /**
     * Waiting thread finishes waiting for connectivity.
     */
    private void onWaitingThreadFinish(){
        connTask = null; // cleanup.
        if (isConnected()){
            Log.v(TAG, "Check triggered from connectivity thread.");
			dbCheckTask(false);
            triggerCheck();
        }
    }

	/**
	 * Settings has changed - language for example.
	 */
	protected void onSettingsChanged(){
		final Locale lang = PhonexSettings.loadDefaultLanguageNoThrow(getContext());
		Log.vf(TAG, "onSettingsChanged, current locale=%s", lang);
	}

	/**
	 * Tries to quit handlers.
	 */
	public void quit(){
		try {
			msgHandler = null;
			MiscUtils.stopHandlerThread(handlerThread, false);
		} catch(Exception e){
			Log.e(TAG, "Cannot stop handler thread.");
		}
	}

	/**
	 * Loads credentials.
	 * Should be called when credentials are available.
	 */
	public int reloadIdentity(){
		int res = this.loadIdentity(rand, getContext());
		return res;
	}

	/**
	 * Manually triggers the database check.
	 * Check is performed on own executor.
	 */
	public void triggerCheck(){
		getHandler().execute(new Runnable() {
			@Override
			public void run() {
				Log.v(TAG, "Manual trigger of the DB check.");
				databaseChanged(false, null);
			}
		});
	}

	public void dbCheckTask(final boolean justStarted) {
		getHandler().execute(new Runnable() {
			@Override
			public void run() {
				Log.v(TAG, "Msg queue recheck.");
				dbCheckInt(justStarted);
			}
		});
	}

	/**
	 * Check database consistency, lost messages.
	 * @param justStarted if yes all the messages in the queue are set as unprocessed.
	 */
	protected void dbCheckInt(boolean justStarted) {
		// Check all DBMessages that have state PENDING of BACKOFF and do not have corresponding message queue entries.
		// For those re-enqueue a new queue message record, update message time.
		dbCheckWithoutQueuedMsg();

		// If just started -> set all to not processed to start over again.
		if (justStarted){
			// Reset all messages waiting for feedback form PJSIP. They are never going to have it. System was restarted.
			//
			// Get all messages from the queue - for debugging purposes, generating a log report.
			messageQueueLogReport();

			// Set all messages in the queue to not processed.
			ContentValues cv = new ContentValues();
			cv.put(QueuedMessage.FIELD_IS_PROCESSED, 0);
			final int affected = service.getContentResolver().update(QueuedMessage.URI, cv, "", null);
			Log.df(TAG, "Number of rows affected by switch to unprocessed: %s", affected);
			return;
		}

		// If check called during normal run it is hard to say whether the feedback for a message is lost or not.
		// Check messages intended for backoff that are quite a long time after backoff trigger and still stucked in that state.
		dbCheckOldProcessed();
	}

	/**
	 * Confirms acceptance or rejection of the transfer.
	 *
	 * @param msgId
	 * @param accept
	 */
	public static void confirmTransfer(final Context ctxt, final long msgId, final boolean accept){
		try {
			final Intent intent = new Intent(accept ? Intents.ACTION_ACCEPT_FILE_CONFIRMED : Intents.ACTION_REJECT_FILE_CONFIRMED);
			intent.putExtra(accept ? Intents.EXTRA_ACCEPT_FILE_CONFIRMED_MSGID : Intents.EXTRA_REJECT_FILE_CONFIRMED_MSGID, msgId);
			MiscUtils.sendBroadcast(ctxt, intent);

		} catch (Throwable t) {
			Log.ef(TAG, t, "Unable to confirm file transfer for msg: %s", msgId);
		}
	}

	/**
	 * Called when user confirms file rejection.
	 * Uses internal executor to finish a call since this is invoked from message receiver.
	 *
	 * @param msgId
	 */
	public void onRejectConfirmed(final long msgId){
		onTransferConfirmation(msgId, false);
	}

	/**
	 * Called when user confirms file acceptance / download.
	 * Uses internal executor to finish a call since this is invoked from message receiver.
	 *
	 * @param msgId
	 */
	public void onAcceptConfirmed(final long msgId){
		onTransferConfirmation(msgId, true);
	}

	public void onTransferConfirmation(final long msgId, final boolean accept){
		Log.vf(TAG, "Transfer confirmation file with msgid: %s, accept: %s", msgId, accept);
		if (msgId < 0){
			return;
		}

		getHandler().execute(new Runnable() {
			@Override
			public void run() {
				onFileAcceptReject(getContext(), msgId, accept);
			}
		});
	}

	/**
	 * Helper for accepting / rejecting files.
	 *
	 * @param ctxt
	 * @param messageId
	 * @param isAccepted
	 */
	public static void onFileAcceptReject(final Context ctxt, final long messageId, final boolean isAccepted){
		if (!isAccepted) {
			FileTransferUtils.setMessageToRejected(messageId, ctxt);
		}

		FileTransferManager.dispatchDownloadTransfer(ctxt, messageId, isAccepted);
	}

    /**
     * DB check initialized from AlarmManager for specific user
     * @param to SIP of receiver who initialized backoff alarm
     */
    public void triggerAlarmCheck(String to) {
        futureResendAlarms.remove(to);
        triggerCheck();
    }

	/**
	 * Broadcasts intent to re-check database.
	 * @param ctxt
	 */
	public static void triggerCheck(Context ctxt){
		final Intent i = new Intent(Intents.ACTION_CHECK_MESSAGE_DB);
		MiscUtils.sendBroadcast(ctxt, i);
	}

	/**
	 * After transfer finished, mark corresponding queued message appropriatelly. Handes failures & re-transfer.
	 *
	 * @param msgId
	 * @param queueMsgId
	 * @param statusOk
	 * @param recoverable
	 */
	public void onTransferFinished(final long msgId, final long queueMsgId, final boolean statusOk, final boolean recoverable) {
		if (queueMsgId < 0){
			Log.e(TAG, "Cannot accept transfer finish event, negative queue id.");
			return;
		}

		getHandler().execute(new Runnable() {
			@Override
			public void run() {
				final ContentResolver cr = getContext().getContentResolver();
				Log.df(TAG, "Receiving FT ack for message id=%s qMsgId=%s, ok=%s, recoverable=%s", msgId, queueMsgId, statusOk, recoverable);

				QueuedMessage queuedMessage = QueuedMessage.getById(cr, queueMsgId);
				// If there is non-recoverable error, remove and do not process message again.
				if ((!statusOk && !recoverable) || queuedMessage == null) {
					Log.vf(TAG, "Non-recoverable error detected, setting to failed state, msg=%s", queuedMessage);
					deleteAndReportToAppLayer(queuedMessage, SendingState.FAILED_GENERIC);
					// Delete temporary files associated with given message id.
					FileTransfer.deleteTempFileByDbMessageId(msgId, getContext());

				} else if (statusOk) {
					// error code may represent something like "Offline message"
					SendingState state = new SendingState(SendingState.Type.ACK_POSITIVE, 200, null);
					// after positive ack, we can delete the message
					deleteAndReportToAppLayer(queuedMessage, state);

				} else {
					// report negative ack back to app layer
					SendingState state = new SendingState(SendingState.Type.ACK_NEGATIVE, -1, null);

					// statusInt - this has unknown function, do not report back
					final AmpDispatcher ampDispatcher = new AmpDispatcher(service);
					ampDispatcher.reportState(queuedMessage, state);

					// Plan resend (if max hasnt been reached yet)
					// Mark as unprocessed (counter was already increased after message was sent).
					final Integer sendCtr = queuedMessage.getSendCounter();
					if (sendCtr != null && sendCtr >= getMaxResendAttempts()) {
						Log.inf(TAG, "Maximum number of resents has been reached [%s], marking as failed", sendCtr);
						deleteAndReportToAppLayer(queuedMessage, SendingState.FAILED_REACHED_MAX_NUM_OF_RESENDS);

					} else if (sendCtr != null && sendCtr < RESEND_BACKOFF_THRESHOLD) {
						Log.df(TAG, "Planning immediate resend of message with id [%s]", queuedMessage.getId());
						increaseSendCtr(queueMsgId, sendCtr, queuedMessage);
						setMessageProcessed(queueMsgId, false);

					} else {
						increaseSendCtr(queueMsgId, sendCtr, queuedMessage);
						setupBackoffResend(queuedMessage, false);
					}
				}
			}
		});
	}

	/**
	 * Increases send counter for given message by one.
	 * @param queueMsgId
	 * @param sendCtr
	 * @param qMessage if not null, new counter value is stored to this object also.
	 * @return
	 */
	private int increaseSendCtr(final long queueMsgId, final Integer sendCtr, final QueuedMessage qMessage) {
		final ContentValues args = new ContentValues();
		final int newSendCtr = sendCtr == null ? 1 : sendCtr + 1;
		args.put(QueuedMessage.FIELD_SEND_COUNTER, newSendCtr);

		if (qMessage != null) {
			qMessage.setSendCounter(newSendCtr);
		}

		return QueuedMessage.updateMessage(getContext().getContentResolver(), queueMsgId, args);
	}

	/**
	 * Increases send attempt counter for given message by one.
	 * @param queueMsgId
	 * @param sendCtr
	 * @param qMessage if not null, new counter value is stored to this object also.
	 * @return
	 */
	private int increaseSendAttemptCtr(final long queueMsgId, final Integer sendCtr, final QueuedMessage qMessage) {
		final ContentValues args = new ContentValues();
		final int newSendCtr = sendCtr == null ? 1 : sendCtr + 1;
		args.put(QueuedMessage.FIELD_SEND_ATTEMPT_COUNTER, newSendCtr);

		if (qMessage != null) {
			qMessage.setSendAttemptCounter(newSendCtr);
		}

		return QueuedMessage.updateMessage(getContext().getContentResolver(), queueMsgId, args);
	}

    /**
     * Loads messages stored in the database stored for further processing.
	 */
	private Cursor loadMessagesToProcess(boolean incoming, boolean outgoing){
        final String SELECT_INCOMING = "(" + QueuedMessage.FIELD_IS_PROCESSED +"=0 AND "
				+ QueuedMessage.FIELD_IS_OUTGOING +  "=0 AND "
				+ QueuedMessage.FIELD_MESSAGE_PROTOCOL_TYPE+"!="+AmpDispatcher.PROTOCOLS.FTRANSFER+")";

        if (!incoming && !outgoing){
            return null;
        }

        Cursor cursorOutgoingTexts = null;
        Cursor cursorOutgoingNotifs = null;
        Cursor cursorIncoming = null;
		Cursor cursorIncomingTransfer = null;
		Cursor cursorOutgoingTransfer = null;

        Log.inf(TAG, "Load messages to process; incoming [%s], outgoing [%s]", String.valueOf(incoming), String.valueOf(outgoing));

        try {
            if (incoming){
                cursorIncoming = this.getContext().getContentResolver().query(
                        QueuedMessage.URI,
                        QueuedMessage.FULL_PROJECTION,
                        SELECT_INCOMING,
                        null,
                        QueuedMessage.FIELD_TIME + " ASC"
                );
            }

            if (outgoing){
                // TODO declarative way of setting how messages are sent
                // text messages are processed one by one, to preserve ordering
                cursorOutgoingTexts = this.getContext().getContentResolver().query(
                        QueuedMessage.NEWEST_MSG_PER_RECIPIENT_URI, // special raw query
                        QueuedMessage.FULL_PROJECTION,
                        "(" + QueuedMessage.FIELD_IS_OUTGOING +  "=1 AND " + QueuedMessage.FIELD_MESSAGE_PROTOCOL_TYPE + "=" + AmpDispatcher.PROTOCOLS.TEXT + ")",
                        null, null);
//
                if (cursorOutgoingTexts == null){
                    Log.w(TAG, "cursorOutgoingTexts is null");
                } else {
                    Log.wf(TAG, "cursorOutgoingTexts is not null, size is [%s]", cursorOutgoingTexts.getCount());
                }


                // notifications are processed in parallel
                // TODO we should differentiate between visible notifs like files and invisible like acknowledgments)
                // TODO processing FileNotifs in parallel means that they can be send in mix
                cursorOutgoingNotifs = this.getContext().getContentResolver().query(
                        QueuedMessage.URI,
                        QueuedMessage.FULL_PROJECTION,
                        "(" + QueuedMessage.FIELD_IS_OUTGOING +  "=1 AND "
                                + QueuedMessage.FIELD_MESSAGE_PROTOCOL_TYPE + "=" + AmpDispatcher.PROTOCOLS.NOTIFICATION + " AND "
                                + QueuedMessage.FIELD_IS_PROCESSED +"=0)",
                        null,
                        QueuedMessage.FIELD_TIME + " ASC"
                );

				// File transfer requests processed in parallel.
				cursorOutgoingTransfer = this.getContext().getContentResolver().query(
						QueuedMessage.URI,
						QueuedMessage.FULL_PROJECTION,
						"(" + QueuedMessage.FIELD_IS_OUTGOING +  "=1 AND "
								+ QueuedMessage.FIELD_MESSAGE_PROTOCOL_TYPE + "=" + AmpDispatcher.PROTOCOLS.FTRANSFER + " AND "
								+ QueuedMessage.FIELD_IS_PROCESSED +"=0)",
						null,
						QueuedMessage.FIELD_TIME + " ASC"
				);

				// File transfer requests processed in parallel.
				// Download is ingoing message but behaves like upload, connectivity is required, backoff & so on.
				cursorIncomingTransfer = this.getContext().getContentResolver().query(
						QueuedMessage.URI,
						QueuedMessage.FULL_PROJECTION,
						"(" + QueuedMessage.FIELD_IS_OUTGOING +  "=0 AND "
								+ QueuedMessage.FIELD_MESSAGE_PROTOCOL_TYPE + "=" + AmpDispatcher.PROTOCOLS.FTRANSFER + " AND "
								+ QueuedMessage.FIELD_IS_PROCESSED +"=0)",
						null,
						QueuedMessage.FIELD_TIME + " ASC"
				);
            }
        } catch (Exception e){
            Log.e(TAG, "Exception in message load", e);
        }

        return new MergeCursor(new Cursor[]{cursorIncoming, cursorOutgoingTexts, cursorOutgoingNotifs,
											cursorIncomingTransfer, cursorOutgoingTransfer});
    }

	/**
	 * Callback from ContentObserver.
	 * Should be executed on handler thread.
	 *
	 * Currently handles decryption and encryption.
	 *
	 * @param selfChange
	 */
	protected void databaseChanged(boolean selfChange, Uri uri){
		// Lock CPU so this procedure finishes.
		wakeLock.lock("DBChanged");

		// Identity is required for the operation, if does not
		// exist, try to load, if it is not possible, do nothing.
		if (this.identity.getStoredCredentials()==null){
			Log.d(TAG, "No identity loaded, loading...");
			if (loadIdentity(rand, getContext()) <= 0){
				Log.d(TAG, "Identity cannot be loaded now.");
				wakeLock.unlock();
				return;
			}
		}

		// Ignore changes caused by own processing.
		if (selfChange){
			Log.v(TAG, "Ignoring self-change.");
			wakeLock.unlock();
			return;
		}

        boolean connectivityValid = isConnected();
		Cursor c = loadMessagesToProcess(true, connectivityValid);

		if (c==null){
			Log.w(TAG, "Cursor is null.");
			wakeLock.unlock();
			return;
		}

		final int msgCount = c.getCount();
		if (msgCount==0){
			c.close();

			Log.vf(TAG, "Nothing to process, net=%s", connectivityValid);
			wakeLock.unlock();
			return;
		}

		Log.vf(TAG, "Messages waiting for processing: %s, net=%s", msgCount, connectivityValid);
		for(int cur = 0; c.moveToNext(); cur++){
            QueuedMessage msg = null;

			try {
                msg = new QueuedMessage(c);
				if (msg.getId()<=0){
					Log.ef(TAG, "Invalid QueuedMessage id [%s]", msg.getId());
				}

				Log.df(TAG, "processing message %s/%s; protocolType/Version=%s/%s; id=%s", cur, msgCount, msg.getTransportProtocolType(), msg.getTransportProtocolVersion(), msg.getId());

				// Obtain canonical contact name, required for loading a certificate.
				final String remoteContact = SipUri.getCanonicalSipContact(msg.getRemoteContact(), false);

                if (msg.getIsOutgoing()){
					processOutgoingMessage(remoteContact, msg);

                } else {
                    processIncomingMessage(remoteContact, msg);
                }

			} catch(Exception e){
				Log.e(TAG, "Exception during decrypting message", e);
			}
		}

		try {
			c.close();
		} catch(Exception e){
			Log.e(TAG, "Cannot close cursor.", e);
		}

		wakeLock.unlock();
	}

    /**
     * Removes metadata prefix added by MSILO Opensips module
     * Matching regex [Offline Message ... ] ...
     */
    public static String removeOfflineMessageMetadata(String body){
        return body.replaceAll("(?i)^\\s*\\[offline.+?\\]\\s*", "");
    }

    /**
     * Acknowledged received
     * @param statusErrorCode - status code (positive or negative) from PjSip
     */
    public void acknowledgmentFromPjSip(String to, String returnedFinalMessage, boolean statusOk, String reasonErrorText, int statusErrorCode){
        Log.df(TAG, "Receiving [%s] pjsip acknowledgment for message sent to [%s]", (statusOk ? "positive" : "negative"), to);

        String finalMessageHash = returnedFinalMessage;
        try {
            finalMessageHash = MessageDigest.generateMD5Hash(returnedFinalMessage, false);
            Log.d(TAG, "Acknowledgment MessageHash computed: [" + finalMessageHash + "]; to=[" + to + "]");
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Unable to generate hash from message body", e);
        }

        // Load sip message ID here.
        long msgID = -1; // invalid id
        Cursor c = getContext().getContentResolver().query(
                QueuedMessage.URI, QueuedMessage.SENDING_ACK_PROJECTION,
                        QueuedMessage.FIELD_TO + "=? AND " +
                        QueuedMessage.FIELD_FINAL_MESSAGE_HASH + "=? AND " +
                        QueuedMessage.FIELD_IS_PROCESSED + "= 1",
                new String[] {to, finalMessageHash}, null);

        if (c != null) {
            try {
                if (c.moveToFirst()){
                    msgID = c.getLong(c.getColumnIndex(QueuedMessage.FIELD_ID));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error while getting message ID", e);
            } finally {
                c.close();
            }
        }

        if (msgID == -1){
            Log.ef(TAG, "Received acknowledgment for non-existing message in message queue");
            return;
        }

        QueuedMessage queuedMessage = QueuedMessage.getById(getContext().getContentResolver(), msgID);
		if (statusErrorCode == 202){
			queuedMessage.setIsOffline(true);
		}

        if (statusOk){
            // error code may represent something like "Offline message"
            SendingState state = new SendingState(SendingState.Type.ACK_POSITIVE, statusErrorCode, reasonErrorText);
            // after positive ack, we can delete the message
            deleteAndReportToAppLayer(queuedMessage, state);
			Log.vf(TAG, "Message ACK_POSITIVE, msgId:[%s]", queuedMessage.getId());
        } else {
            // report negative ack back to app layer
            SendingState state = new SendingState(SendingState.Type.ACK_NEGATIVE, statusErrorCode, reasonErrorText);
            // statusInt - this has unknown function, do not report back
            AmpDispatcher ampDispatcher = new AmpDispatcher(service);
            ampDispatcher.reportState(queuedMessage, state);
			Log.vf(TAG, "Message ACK_NEGATIVE, msgId:[%s]", queuedMessage.getId());

            // Plan resend (if max hasnt been reached yet)
            // Mark as unprocessed (counter was already increased after message was sent)

            if (queuedMessage.getSendCounter() >= getMaxResendAttempts()){
                Log.wf(TAG, "Maximum number of resents has been reached [%s], marking as failed, id[%s]", queuedMessage.getSendCounter(), queuedMessage.getId());
                deleteAndReportToAppLayer(queuedMessage, SendingState.FAILED_REACHED_MAX_NUM_OF_RESENDS);
            } else if (queuedMessage.getSendCounter() < RESEND_BACKOFF_THRESHOLD){
                Log.df(TAG, "Planning immediate resend of message with id [%s]", queuedMessage.getId());
                setMessageProcessed(msgID, false);
            } else {
                setupBackoffResend(queuedMessage, false);
            }
        }
    }

    private int resendTimeDelay(int counter){
        // returns number of seconds
        switch (counter){
            case 0:
                return 10;
            case 1:
                return 15;
            case 2:
                return 20;
            default:
                return 30;
        }
    }

    private int resendStackTimeDelay(int counter){
        // returns number of seconds
		if (counter <= 5) {
			return 1;
		} else if (counter <= 10){
			return 3;
		} else if (counter <= 25){
			return 10;
		} else {
			return 30;
		}
    }

    private synchronized void setupBackoffResend(QueuedMessage msg, boolean stackError){
        // first check if there is a backoff planned for the same user
        // if so, synchronize backoff sending
        Long resendTime = futureResendAlarms.get(msg.getTo());

		long timeDelay;
		int backoffResendCounter;
		if (stackError){
			// Message could not be sent due to internal error.
			backoffResendCounter = msg.getSendAttemptCounter() != null ? msg.getSendAttemptCounter() - RESEND_STACK_BACKOFF_THRESHOLD : 0;
			timeDelay = resendStackTimeDelay(backoffResendCounter) * 1000;

		} else {
			// Normal sending backoff due to connectivity problems / remote side / server unreachability.
			backoffResendCounter = msg.getSendCounter() != null ? msg.getSendCounter() - RESEND_BACKOFF_THRESHOLD : 0;
			timeDelay = resendTimeDelay(backoffResendCounter) * 1000;
		}

        boolean alarmExists = true;

        if (resendTime == null){
            alarmExists = false;
            resendTime = System.currentTimeMillis() + timeDelay;
        }

        ContentValues cv = new ContentValues();
        cv.put(QueuedMessage.FIELD_IS_PROCESSED, false);
        cv.put(QueuedMessage.FIELD_RESEND_TIME, resendTime);

        int updated = QueuedMessage.updateMessage(getContext().getContentResolver(), msg.getId(), cv);
        if (updated != 1){
            Log.ef(TAG, "setupBackoffResend: Wrong number of updated messages [%s]", updated);
            return;
        }

        // update app layer
        AmpDispatcher ampDispatcher = new AmpDispatcher(service);
        ampDispatcher.reportState(msg, SendingState.setForBackoff(resendTime));
		Log.vf(TAG, "Backoff set delay[%s], alarmExists[%s], id[%s], to: %s", timeDelay, alarmExists, msg.getId(), msg.getTo());

        if (!alarmExists){
            // Let AlarmManager invoke Intent for db check in given timeout
            Log.inf(TAG, "Setting message with id [%s] to backoff resend; sendCounter [%s], resend in [%s] millis", msg.getId(), msg.getSendCounter(), timeDelay);
            futureResendAlarms.put(msg.getTo(), resendTime);
            service.setAlarm(Intents.ALARM_MESSAGE_RESEND, timeDelay, MapUtils.mapOf(EXTRA_TO, msg.getTo()));
        }
    }

    private int getMaxResendAttempts(){
        // Up to 16 resends is currently required - it means that resend happends up to 335 s (TCP connection of remote contact should be disconnected,
        // it should be at this time sent as offline message)
        int resendAttempts = RESEND_BACKOFF_THRESHOLD + 16;
        Log.vf(TAG, "Current maximum number of resend attempts is [%s]", resendAttempts);
        return resendAttempts;
    }

    private int getMaxResendStackAttempts(){
        // Up to 16 resends is currently required - it means that resend happends up to 335 s (TCP connection of remote contact should be disconnected,
        // it should be at this time sent as offline message)
        int resendAttempts = RESEND_BACKOFF_THRESHOLD + 100;
        Log.vf(TAG, "Current maximum number of stack resend attempts is [%s]", resendAttempts);
        return resendAttempts;
    }

	/**
	 * Processes incoming message
	 * Assumes identity is already loaded.
	 *
	 * @param remote		remote party identifier in canonical form.
	 * @param msg			Message to decrypt.
	 */
	protected void processIncomingMessage(final String remote, QueuedMessage msg){
        Log.df(TAG, "Processing incoming message from [%s], message [%s]", remote, msg);

		if (msg.getIsProcessed()){
			Log.vf(TAG, "Incoming message to process is marked as processed, do not send. msg=%s", msg);
			// processed messages - Do not give a fuck
			return;
		}

		// do not send messages that have planned future backoff - to avoid adding more unsuccessful trials and possibly extending backoff time
		final long curTime = System.currentTimeMillis();
		if (msg.getResendTime() != null && curTime < msg.getResendTime()) {
			Log.vf(TAG, "Outgoing message to process has resend time [%s] set in the future, do not send yet. msg=%s", msg.getResendTime(), msg);
			return;
		}

		// Try to encrypt/decrypt the given message.
		try {
			// Loads certificate for the sender.
			X509Certificate remoteCert = fetchCertificate(remote);

			if (remoteCert==null){
                // TODO propagate missing cert also to SipMessages
//				setMessageInvalid(msg.getId(), SipMessage.ERROR_MISSING_CERT);
                deleteMessage(msg.getId());
				Log.wf(TAG, "certificate is missing for user: %s", remote);
				return;
			}

			// Message is marked as processed so we are waiting for acknowledgment
			setMessageProcessed(msg.getId(), true);
			boolean processingOK = false;

            try {
                TransportProtocolDispatcher transportDispatcher = new TransportProtocolDispatcher(service, identity, remoteCert);
                transportDispatcher.receive(msg);
				processingOK = true;

            } catch (Exception e){
              Log.e(TAG, "processIncomingMessage()", e);

            } finally {
				// If this is filetransfer type, do not delete it, it will be deleted once
				// download is complete, if no fail happenned.
				if (!processingOK || AmpDispatcher.PROTOCOLS.FTRANSFER != msg.getMessageProtocolType()){
					deleteMessage(msg.getId());
				}
            }
		} catch (Exception e){
			Log.w(TAG, "Exception during decrypting message", e);
            deleteMessage(msg.getId());
		}
	}

	protected void processOutgoingMessage(final String remote, QueuedMessage msg){
        if (msg.getIsProcessed()){
            Log.i(TAG, "Outgoing message to process is marked as processed, do not send.");
            // processed messages - Do not give a fuck
            return;
        }
        // do not send messages that have planned future backoff - to avoid adding more unsuccessful trials and possibly extending backoff time
        else if (msg.getResendTime() > System.currentTimeMillis()) {
            Log.inf(TAG, "Outgoing message to process has resend time [%s] set in the future, do not send yet.", msg.getResendTime());
            return;
        }

		// Obtain user record for the local user.
		SipProfile sender = SipProfile.getProfileFromDbName(getContext(), msg.getFrom(), false, SipProfile.ACC_PROJECTION);
		if (sender==null || sender.getId() == SipProfile.INVALID_ID ){
            deleteAndReportToAppLayer(msg, SendingState.FAILED_INVALID_DESTINATION);
			Log.wf(TAG, "Cannot get local user for the sender: %s", msg.getFrom());
			return;
		}

        // Test if it is possible to send this message somehow.
        boolean accountValid = false;
        try {
            accountValid = service.getBinder().isAccountSipValid(remote, (int) sender.getId());
        } catch (RemoteException e) {
            Log.e(TAG, "Cannot verify if account is valid", e);
        }

        // If account is not valid, do not try to send message, it wouldn't succeed anyway.
        // Such message has to wait in queue until there is some valid account.
        if (!accountValid){
            Log.df(TAG, "Account [%s] is not valid to send to [%s]", sender.getId(), remote);
            return;
        }

		// Plaintext is in the body of the sip message.
		// try and synchronized block in case of troubles
		try {
        	// Loads certificate for the sender.
    		X509Certificate remoteCert = fetchCertificate(remote);
    		if (remoteCert==null){

                deleteAndReportToAppLayer(msg, SendingState.FAILED_MISSING_REMOTE_CERT);
//    			SipMessage.setMessageError(getContext().getContentResolver(), msg.getId(), SipMessage.MESSAGE_TYPE_ENCRYPT_FAIL, SipMessage.ERROR_MISSING_CERT, "");
    			Log.wf(TAG, "certificate is missing for user: %s", remote);
    			return;
    		}

            // Message is marked as processed, wait for ack
            setMessageProcessed(msg.getId(), true);
            AmpDispatcher ampDispatcher = new AmpDispatcher(service);
            ampDispatcher.reportState(msg, SendingState.SENDING);

            // transmit message
            TransportProtocolDispatcher transportDispatcher = new TransportProtocolDispatcher(service, identity, remoteCert);
            // TODO unify setters so we do not have to think of every possible setter that is required for transmit call
            transportDispatcher.setMessageQueueListener(this);
            transportDispatcher.transmit(msg);
		}
		catch (Exception e) {
			Log.e(TAG, "Exception in message fragment background task", e);
            deleteAndReportToAppLayer(msg, SendingState.FAILED_GENERIC);
			return;
		}
	}

	/**
	 * Tries to load remote certificate if available, otherwise try to fetch it from the server.
	 *
	 * @param remote
	 * @return
	 * @throws Exception
	 */
	protected X509Certificate fetchCertificate(final String remote) throws Exception{
		// Control variables telling whether is needed to perform
    	// certificate server re-check.
    	// Certificate may be old, missing or invalid. Check it to be sure, before
    	// starting encryption with it.
    	boolean recheckNeeded=false;
    	String existingCertHash2recheck=null;

    	// Certificate might be in the middle of synchronization right now,
    	// if it is, wait for sync finish.
		final long waitStarted = System.currentTimeMillis();
		final long waitDeadline = waitStarted + 1000*3;

		long curTime = waitStarted;
		boolean certSyncInProgress=false;
		while(curTime <= waitDeadline){
			throwIfCancelled();

			certSyncInProgress = isCertSyncInProgress(remote);
			if (certSyncInProgress==false) break;

			// Sleep 500 ms.
			Thread.sleep(500);
			curTime = System.currentTimeMillis();
		}

    	// load certificate for remoteSip, certificate update might just finished.
		UserCertificate sc = getSipCertificate(remote);
		if (sc!=null && sc.getCertificateStatus()== UserCertificate.CERTIFICATE_STATUS_OK){
			try {
				return sc.getCertificateObj();
			} catch(Exception e){
				Log.ef(TAG, "Certificate format is crippled for user %s", remote);
			}
		}

		// If remote certificate fetching is not permitted, quit.
		if (this.fetchCertIfMissing==false){
			return null;
		}

    	// certificate not found in database: re-query it
    	Log.d(TAG, "Certificate for remote user is not stored locally, loading from server");
    	existingCertHash2recheck=null;
    	throwIfCancelled();

    	Log.df(TAG, "Certificate re-check: %s; hash=%s", recheckNeeded, existingCertHash2recheck);

    	// Obtain domain part
    	ParsedSipContactInfos parsedUri = SipUri.parseSipContact(remote);
    	if (parsedUri==null || parsedUri.domain==null || TextUtils.isEmpty(parsedUri.domain)){
    		Log.wf(TAG, "Sip is invalid, no domain found: %s", remote);
    		return null;
    	}

		// re-use re-check certificate code
		CertificateRefreshCall refreshTask = new CertificateRefreshCall(getContext());
		refreshTask.recheckCertificate(remote, parsedUri.domain, existingCertHash2recheck, identity.getStoredCredentials().getUsrStoragePass().toCharArray());
		if (refreshTask.getX509Cert() != null){
			return refreshTask.getX509Cert();
		}

		return null;
	}

	/**
	 * Returns true if given user certificate is being checked at the moment.
	 * @param sip
	 * @return
	 */
	protected boolean isCertSyncInProgress(String sip){
		if (service==null) {
			Log.e(TAG, "Service is null");
			return false;
		}

		try {
			final long freshnessLimit = System.currentTimeMillis() - 1000*60*1;

			List<CertUpdateProgress> progress = service.getCertUpdateProgress();
			for(CertUpdateProgress u : progress){
				if (sip.equalsIgnoreCase(u.getUser())==false) continue;
				if (u.getState()!=CertUpdateStateEnum.DONE && u.getState()!=CertUpdateStateEnum.NONE) {
					Log.df(TAG, "CertSyncInProgress... %s", u.toString());

					// Only if the last action was taken few minutes ago, if it is too old
					// probably wont get fixed not and waiting is pointless.
					return (u.getWhen() >= freshnessLimit);
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "Problem in obtaining cert update status", e);
		}

		return false;
	}

    // delete message with reason for deleting (SendingState), which is reported back to application layer
    @Override
    public int deleteAndReportToAppLayer(QueuedMessage msg, SendingState state){
		if (msg == null){
			Log.e(TAG, "Message to delete is null!");
			return -1;
		}

        if (!msg.getIsProcessed()){
            setMessageProcessed(msg.getId(), true);
        }
        AmpDispatcher ampDispatcher = new AmpDispatcher(service);
        ampDispatcher.reportState(msg, state);
        return deleteMessage(msg.getId());
    }

    // in some cases (bad HMAC, do not even store the message, just delete)
    @Override
    public int deleteMessage(long messageId){
		Log.vf(TAG, "Deleting queue message id: %s", messageId);
        return getContext().getContentResolver().delete(QueuedMessage.URI,
				QueuedMessage.FIELD_ID + "=?",
				new String[]{String.valueOf(messageId)});
    }

    @Override
    public int setMessageProcessed(long messageId, boolean isProcessed) {
        ContentValues cv = new ContentValues();
        cv.put(QueuedMessage.FIELD_IS_PROCESSED, isProcessed);

        Log.df(TAG, "Updating message with id [%s], flag processed as [%s]", messageId, String.valueOf(isProcessed));

        return QueuedMessage.updateMessage(getContext().getContentResolver(), messageId, cv);
    }

    // when sending, we store final message for possible resend (so we do not have to compute all values again)
    @Override
    public int storeFinalMessageWithHash(long messageId, String finalMessage) {
        String hash = finalMessage;
        try {
            hash = MessageDigest.generateMD5Hash(finalMessage, false);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Weird MD5 missing error, instead storing all message as hash", e);
        }

        ContentValues cv = new ContentValues();
        cv.put(QueuedMessage.FIELD_FINAL_MESSAGE, finalMessage);
        cv.put(QueuedMessage.FIELD_FINAL_MESSAGE_HASH, hash);

        Log.df(TAG, "Storing hash for outgoing message id [%s], hash [%s]", messageId, hash);

        return getContext().getContentResolver().update(QueuedMessage.URI, cv,
				QueuedMessage.FIELD_ID + "=?",
				new String[]{String.valueOf(messageId)});
    }

    /**
	 * Returns context from service.
	 * @return
	 */
	protected Context getContext(){
		if (service==null) return null;
		return service.getApplicationContext();
	}

	/**
	 * Loads identity from global resources (in-memory database) into parameters provided.
	 * Context has to be set.
	 *
	 * @param rand
     * @param ctx
	 * @return <0 on error (-1 = problem with on-memory, -2 = problem with priv key).
	 */
	protected int loadIdentity(SecureRandom rand, Context ctx){
		return CertificatesAndKeys.IdentityLoader.loadIdentity(identity, rand, ctx);
	}

	/**
	 * Assumes normalized user name without scheme.
	 * @param user
	 * @return
	 */
	protected UserCertificate getSipCertificate(String user){
		// Is in LRU cache? If yes, return directly.
		synchronized(certCache){
			UserCertificate sc = certCache.get(user);
			if (sc!=null){
				return sc;
			}
		}

		// Load certificate for user from local database.
    	UserCertificate sipRemoteCert = CertificatesAndKeys.getRemoteCertificate(getContext(), user);
    	if (sipRemoteCert==null){
    		Log.df(TAG, "Certificate not found for user: %s", user);
    		sipRemoteCert = new UserCertificate();
    		sipRemoteCert.setCertificateStatus(UserCertificate.CERTIFICATE_STATUS_MISSING);
    	}

    	synchronized(certCache){
			certCache.put(user, sipRemoteCert);
		}

    	return sipRemoteCert;
	}

	/**
	 * Called from upload task when file name collision was detected and needs to be fixed in notification message.
	 */
	public static void fileNotificationMessageFnameUpdate(Context ctxt, UploadFileParams params, List<String> files) {
		final ContentResolver cr = ctxt.getContentResolver();
		final ContentValues cv = new ContentValues();
		final String newBody = getMsgBodyForFnames(params.getTitle(), params.getDesc(), files, false);
		cv.put(SipMessage.FIELD_BODY, newBody);
		cv.put(SipMessage.FIELD_BODY_DECRYPTED, newBody);
		SipMessage.updateMessage(cr, params.getMsgId(), cv);
	}

	/**
	 * Build message body for the file transfer.
	 * @param title
	 * @param desc
	 * @param files file storage uris
	 * @return
	 */
	public static String getMsgBodyForFnames(String title, String desc, List<String> files, boolean useUris) {
		final int cnt = MiscUtils.collectionSize(files);
		final StringBuffer msgBody = new StringBuffer();

		if (!MiscUtils.isEmpty(title)){
			msgBody.append(title);
			if (cnt > 0){
				msgBody.append("\n");
			}
		}

		if (cnt <= 0){
			return msgBody.toString();
		}


		int curCtr = 0;
		for(String fname : files){
			Log.df(TAG, "getMsgBodyForFnames, fname: [%s]", fname);
			if (useUris) {
				msgBody.append(FilenameUtils.getNameFromUri(fname));
			} else {
				msgBody.append(FilenameUtils.getName(fname));
			}
			curCtr += 1;

			if (curCtr != cnt){
				msgBody.append("\n");
			}
		}

		return msgBody.toString();
	}

	/**
	 * Build message body for the file transfer.
	 * @param title
	 * @param desc
	 * @param files
	 * @return
	 */
	public static String getMsgBodyForFiles(String title, String desc, List<File> files) {
		final ArrayList<String> paths = new ArrayList<>();
		for(File fe : files){
			paths.add(fe.getAbsolutePath());
		}

		return getMsgBodyForFnames(title, desc, paths, false);
	}

	/**
	 * send file to to, update progress of SipMessage (having sipMessageId) in MessageFragment
	 * Assuming all files exists (this should have been checked before)
	 * @param remoteContact
	 * @param fileUris formatted according to FileStorageUri
	 * @param message
	 */
	public void sendFiles(String remoteContact, final List<String> fileUris, String message) {
		final String destinationSip = SipUri.getCanonicalSipContact(remoteContact, false);
		final StoredCredentials creds = MemoryPrefManager.loadCredentials(getContext());
		if (creds.getUserSip() == null) {
			Log.e(TAG, "error getting stored credentials, cannot send file");
			return;
		}

		final String msgBody = MessageManager.getMsgBodyForFnames(message, null, fileUris, true);
		final Uri uri = FileTransferUtils.insertOutgoingFileMsg(creds.getUserSip(),
				destinationSip,
				msgBody,
				getContext());

		long sipMessageId = ContentUris.parseId(uri);

		// Enqueue for upload.
		final UploadFileParams params = new UploadFileParams();
		params.setDestinationSip(destinationSip);
		params.setMsgId(sipMessageId);
		params.setFileUris(fileUris);
		ftManager.enqueueUpload(sipMessageId, params);
	}

	/**
	 * Starts file download related to the given message ID.
	 * @param sipMessageId
	 * @param destinationDirectory
	 * @param deleteOnly
	 */
	public void downloadFile(long sipMessageId, String destinationDirectory, boolean deleteOnly){
		DownloadFileParams params = FileTransferManager.getDefaultDownloadParams(getContext(), null, sipMessageId);
		params.setMsgId(sipMessageId);
		params.setCreateDestinationDirIfNeeded(true);
		params.setConflictAction(FTHolder.FilenameConflictCopyAction.RENAME_NEW);
		params.setDownloadFullIfOnWifiAndUnderThreshold(true);

		if (!MiscUtils.isEmpty(destinationDirectory)){
			params.setDestinationDirectory(new File(destinationDirectory));
		}

		ftManager.enqueueFile2Download(params, true, deleteOnly);
	}

    /**
	 * Creates looper working thread lazily.
	 * @return
	 */
	private static Looper createLooper() {
        if (handlerThread == null) {
            Log.df(TAG, "Creating new HandlerThread [%s]", EXECUTOR_NAME);
            handlerThread = new HandlerThread(EXECUTOR_NAME);
            handlerThread.start();
        }
        return handlerThread.getLooper();
    }

	/**
	 * Returns handler object lazily.
	 * @return
	 */
    protected WorkerHandler getHandler() {
        if (msgHandler == null) {
        	msgHandler = new WorkerHandler(createLooper(), this);
            Log.i(TAG, "Handler was null, creating new WorkerHandler");
        }
        return msgHandler;
    }

	public void onLoginFinished() {
		Log.vf(TAG, "onLoginFinished - reloading identity");
		// Important - if MessageManager survives logout login process and different user is logged in, reload identity,
		// otherwise bad private key may be used when doing message transfer crypto
		reloadIdentity();
	}

	/**
     * Custom handler.
     * @author ph4r05
     */
	protected static class WorkerHandler extends Handler {
        WeakReference<MessageManager> sr;

        public WorkerHandler(Looper looper, MessageManager stateReceiver) {
            super(looper);
            Log.d(TAG, "Create async worker");
            sr = new WeakReference<MessageManager>(stateReceiver);
        }
        
        public void execute(Runnable task) {
        	MessageManager m = sr.get();
        	if (m!=null){
        		m.swakelock.lock(task);
        	}
        	
            Message.obtain(this, 0, task).sendToTarget();
        }

        public void handleMessage(Message msg) {
        	MessageManager stateReceiver = sr.get();
            if (stateReceiver == null) {
            	// weak reference probably does not exist anymore (was garbage collected)
                return;
            }
            
            // simple message handler
            if (msg.obj instanceof Runnable) {
                executeInternal((Runnable) msg.obj);
            } else {
                Log.wf(TAG, "can't handle msg: %s", msg);
            }
        }
        
	    private void executeInternal(Runnable task) {
	        try {
	            task.run();
	        } catch (Throwable t) {
	            Log.ef(TAG, t, "run task: %s", task);
	        } finally {
	        	MessageManager m = sr.get();
	        	if (m!=null){
	        		m.swakelock.unlock(task);
	        	}
	        }
	    }
    };
    
	/**
     * Observer for changes of message database.
     */
    private class MessageContentObserver extends ContentObserver {
        public MessageContentObserver(Handler h) {
            super(h);
        }
        
        /**
         * Implement the onChange(boolean) method to delegate the change notification to
         * the onChange(boolean, Uri) method to ensure correct operation on older versions
         * of the framework that did not have the onChange(boolean, Uri) method.
         */
        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        /**
         * Implement the onChange(boolean, Uri) method to take advantage of the new Uri argument.
         */
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            databaseChanged(selfChange, uri);
        }
    }
    
    /**
     * Content observer for cleaning certificate cache.
     * @author ph4r05
     */
    private class CertificateContentObserver extends ContentObserver {
    	 public CertificateContentObserver(Handler h) {
             super(h);
         }
         
         @Override
         public void onChange(boolean selfChange) {
             synchronized(certCache){
            	 certCache.evictAll();
             }
         }
    }

    /**
     * Handles unsuccessful message send attempt.
     *
     * @param mDesc
     */
    private void handleSendFailed(MessageSentDescriptor mDesc){
        if (mDesc.getMessageId() <= 0) {
            return;
        }

        QueuedMessage msg = QueuedMessage.getById(getContext().getContentResolver(), mDesc.getMessageId());
        if (msg==null){
            Log.wf(TAG, "Message is null, id=%s", mDesc.getMessageId());
            return;
        }

        ContentValues args = new ContentValues();

        // Get message send date.
        long msgDate = msg.getTime();
        final long curTime = System.currentTimeMillis();

        // Fix potentially incorrect date.
        if (msgDate<=0){
            msgDate = curTime;
            args.put(QueuedMessage.FIELD_TIME, msgDate);
        }

		// For monitoring lost messages (send, without feedback from PJSIP), blocking sending queue.
		args.put(QueuedMessage.FIELD_LAST_SEND_CALL, curTime);

		// Increase message send attempt.
		increaseSendAttemptCtr(msg.getId(), msg.getSendAttemptCounter(), msg);

		// If message is too old, switch to failed.
		if (msg.getSendAttemptCounter() == null || msg.getSendAttemptCounter() < RESEND_STACK_BACKOFF_THRESHOLD) {
			// If message is young, convert back to queued message.
			args.put(QueuedMessage.FIELD_IS_PROCESSED, 0);
			args.put(QueuedMessage.FIELD_FINAL_MESSAGE, "");	// Reset final ciphertext. so we generate a new message
			args.put(QueuedMessage.FIELD_FINAL_MESSAGE_HASH, "");	// Reset final ciphertext. so we generate a new message
			QueuedMessage.updateMessage(getContext().getContentResolver(), mDesc.getMessageId(), args);
			Log.vf(TAG, "Message set to queue again [%s]", msg);
			return;

		} else if (msg.getSendAttemptCounter() != null && msg.getSendAttemptCounter() > getMaxResendStackAttempts()){
			// Resend attempt number is way too high, mark message as failed.
			Log.vf(TAG, "Maximum number of stack resends has been reached [%s], marking as failed", msg.getSendAttemptCounter());
			deleteAndReportToAppLayer(msg, SendingState.FAILED_GENERIC);
			return;

		} else {
			// Message should be backed off and send later. Something may be wrong with the stack / connectivity.
			// This causes message is not re-sent immediatelly, preventing rapid sending of the message on msg update.
			Log.vf(TAG, "Message with id %s set to backoff. Attempt ctr: %s", msg.getId(), msg.getSendAttemptCounter());
			QueuedMessage.updateMessage(getContext().getContentResolver(), mDesc.getMessageId(), args);
			setupBackoffResend(msg, true);
		}
    }

    /**
     * Event triggered when message was passed to the message stack for sending.
     * @param mDesc
     */
    public void onMessageSent(MessageSentDescriptor mDesc){
        // Handle unsuccessful send message attempt.
        if (mDesc.getSendResult()==null) {
            Log.df(TAG, "Unable to send message to [%s]", mDesc.getRecipient());
            handleSendFailed(mDesc);
            return;
        }

        int counter = QueuedMessage.loadSendCounter(getContext().getContentResolver(), mDesc.getMessageId());

        // successful sending (but receiving may not be ACK yet) => update send counter
        if (mDesc.getMessageId() > 0){
            ContentValues args = new ContentValues();
            args.put(QueuedMessage.FIELD_SEND_COUNTER, ++counter);

			// For monitoring lost messages (send, without feedback from PJSIP), blocking sending queue.
			args.put(QueuedMessage.FIELD_LAST_SEND_CALL, System.currentTimeMillis());

			QueuedMessage.updateMessage(getContext().getContentResolver(), mDesc.getMessageId(), args);
            Log.df(TAG, "Updated msg [id=%s]", String.valueOf(mDesc.getMessageId()));
        } else {
            Log.ef(TAG, "onMessageSent(): QueuedMessage has negative ID [%s]", mDesc.getMessageId());
        }
    }

    private boolean isConnected(){
        return service.isConnectivityValid() && service.isConnectionValid();
    }

	protected void dbCheckOldProcessed() {
		// Select all processed outgoing messages, which are not subject to backoff (fire timer older than X seconds)
		// and which had last send call Y seconds ago (so expiration had chance to trigger a feedback, but feedback got lost).
		// Messages subject to backoff have processed flag set to 0. 1 is set just before sending.
		// Only messages older than Z seconds are processed (too recent messages might be just in processing).
		final long curTime = System.currentTimeMillis();
		final long createTimeThr = curTime - 5*60*1000L;
		final long resendTimeThr = curTime - 5*60*1000L;
		final long lastSendThr = curTime - 5*60*1000L;

		ContentValues cv = new ContentValues();
		cv.put(QueuedMessage.FIELD_IS_PROCESSED, false);

		// If there are such messages, set processed to 0 so they start over again.
		final int affected = service.getContentResolver().update(QueuedMessage.URI, cv,
				String.format("%s=1 AND %s=1 AND %s < ? AND %s < ? AND %s < ?",
						QueuedMessage.FIELD_IS_PROCESSED,
						QueuedMessage.FIELD_IS_OUTGOING,
						QueuedMessage.FIELD_RESEND_TIME,
						QueuedMessage.FIELD_LAST_SEND_CALL,
						QueuedMessage.FIELD_TIME),
				new String[] {Long.toString(resendTimeThr), Long.toString(lastSendThr), Long.toString(createTimeThr)});
		if (affected > 0) {
			Log.wf(TAG, "Messages recovered from blocked state: %s", affected);
		}
	}

	protected void dbCheckWithoutQueuedMsg() {
		// 1. Select all outgoing messages from PEXDbMessage in states (queued, queued backoff, pending) and are older than X seconds.
		//      Do not load messages just sent, or just delivered. Avoid race conditions.
		final long createTimeThr = System.currentTimeMillis() - 2*60*1000L;
		// Numeric timestamp representation suitable for SQL query.
		Cursor c = service.getContentResolver().query(SipMessage.MESSAGE_URI,
				SipMessage.FULL_PROJECTION,
				String.format("%s=1 AND %s IN(?,?,?) AND %s < ?",
						SipMessage.FIELD_IS_OUTGOING,
						SipMessage.FIELD_TYPE,
						SipMessage.FIELD_DATE),
				new String[] {
						Integer.toString(SipMessage.MESSAGE_TYPE_PENDING),
						Integer.toString(SipMessage.MESSAGE_TYPE_QUEUED),
						Integer.toString(SipMessage.MESSAGE_TYPE_QUEUED_BACKOFF),
						Long.toString(createTimeThr)},
				null);

		if (c == null){
			Log.e(TAG, "Cursor is nil");
			return;
		}

		final List<String> ids = new LinkedList<>();
		final Set<Long> idsToRemove = new HashSet<>();
		try {
			while(c.moveToNext()){
				final SipMessage cur = new SipMessage(c);
				ids.add(Long.toString(cur.getId()));
				idsToRemove.add(cur.getId());
			}
		} catch(Exception e) {
			Log.e(TAG, "Exception when dumping message queue", e);
		} finally {
			MiscUtils.closeCursorSilently(c);
		}

		// If there are no such messages -> nothing to do.
		Log.vf(TAG, "Messages with sending states to check: %s, %s", ids.size(), ids);
		if (ids.isEmpty()){
			return;
		}

		// 2. Select all message queue messages with reference id in given set of values from the previous query.
		final Cursor cq = service.getContentResolver().query(QueuedMessage.URI,
				QueuedMessage.FULL_PROJECTION,
				String.format("%s=1 AND %s IN (%s)",
						QueuedMessage.FIELD_IS_OUTGOING,
						QueuedMessage.FIELD_REFERENCED_ID,
						DBProvider.getInPlaceholders(ids.size())),
				ids.toArray(new String[ids.size()]),
				null);

		if (cq == null){
			Log.e(TAG, "Cursor is null");
			return;
		}

		try {
			while(cq.moveToNext()){
				final QueuedMessage cur = new QueuedMessage(cq);
				idsToRemove.remove(cur.getReferencedId().longValue());
			}
		} catch(Exception e) {
			Log.e(TAG, "Exception when dumping message queue", e);
		} finally {
			MiscUtils.closeCursorSilently(cq);
		}

		// No messages to set to failed.
		Log.vf(TAG, "Messages with missing sending queue record: %s, %s", idsToRemove.size(), idsToRemove);
		if (idsToRemove.isEmpty()){
			return;
		}

		// 3. Update such messages to state FAILED, if they still have given state and timing as in the first query (no race condition / update meanwhile)
		final ContentValues cv = new ContentValues();
		cv.put(SipMessage.FIELD_TYPE, SipMessage.MESSAGE_TYPE_FAILED);

		// Same arguments are repeated so if messages got updated in another thread we are not modifying it with old data.
		List<String> args = new LinkedList<>();
		args.add(Integer.toString(SipMessage.MESSAGE_TYPE_PENDING));
		args.add(Integer.toString(SipMessage.MESSAGE_TYPE_QUEUED));
		args.add(Integer.toString(SipMessage.MESSAGE_TYPE_QUEUED_BACKOFF));
		args.add(Long.toString(createTimeThr));
		for(Long cid : idsToRemove){
			args.add(Long.toString(cid));
		}

		final int affected = service.getContentResolver().update(SipMessage.MESSAGE_URI, cv,
				String.format("%s=1 AND %s IN(?,?,?) AND %s < ? AND %s IN (%s)",
						SipMessage.FIELD_IS_OUTGOING,
						SipMessage.FIELD_TYPE,
						SipMessage.FIELD_DATE,
						SipMessage.FIELD_ID,
						DBProvider.getInPlaceholders(idsToRemove.size())),
				args.toArray(new String[args.size()]));

		if (affected > 0){
			Log.wf(TAG, "There were %s messages without corresponding sending part", affected);
		}
	}

	protected void messageQueueLogReport() {
		final Cursor c = service.getContentResolver().query(QueuedMessage.URI, QueuedMessage.FULL_PROJECTION, "", null, null);
		if (c == null){
			Log.ef(TAG, "Null cursor");
			return;
		}

		try {
			int cntTotal = 0;
			int cntProcessed = 0;

			while(c.moveToNext()){
				final QueuedMessage cur = new QueuedMessage(c);
				cntTotal += 1;

				if (cur.getIsProcessed()){
					cntProcessed += 1;
				}
			}

			Log.inf(TAG, "Message queue contains %s messages, %s processed", cntTotal, cntProcessed);
		} catch(Exception e) {
			Log.e(TAG, "Exception when dumping message queue", e);
		} finally {
			MiscUtils.closeCursorSilently(c);
		}
	}

    /**
     * Task waits for connectivity to appear valid for specified amount of time.
     * After that another few seconds is paused for stacks to get registered.
     */
    private class WaitForConnectivityTask extends Thread {
        private static final int  MAX_RETRY_COUNT = 10;
        private static final long TIMEOUT = 5000;

        @Override
        public void run() {
            int retryCount = 0;
            while(retryCount < MAX_RETRY_COUNT && isConnected()==false){
                try {
                    Thread.sleep(TIMEOUT);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Waiting interrupted");
                    break;
                }
            }

            // Beware, here connectivity can or does not have to be valid.
            try {
                Thread.sleep(TIMEOUT);
            } catch (InterruptedException e) {
                Log.e(TAG, "Waiting interrupted");
            }

            onWaitingThreadFinish();
        }
    }

    public static class MessageSentDescriptor {
        private long messageId;
        private long accountId;
        private String message;
        private String msg2store;
        private String recipient;
        private boolean isResend;
        private XService.ToCall sendResult;

        public MessageSentDescriptor() {
        }

        public MessageSentDescriptor(long messageId, long accountId, String message, String msg2store, String recipient, boolean isResend, XService.ToCall sendResult) {
            this.messageId = messageId;
            this.accountId = accountId;
            this.message = message;
            this.msg2store = msg2store;
            this.recipient = recipient;
            this.isResend = isResend;
            this.sendResult = sendResult;
        }

        public long getMessageId() {
            return messageId;
        }

        public void setMessageId(long messageId) {
            this.messageId = messageId;
        }

        public long getAccountId() {
            return accountId;
        }

        public void setAccountId(long accountId) {
            this.accountId = accountId;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getMsg2store() {
            return msg2store;
        }

        public void setMsg2store(String msg2store) {
            this.msg2store = msg2store;
        }

        public String getRecipient() {
            return recipient;
        }

        public void setRecipient(String recipient) {
            this.recipient = recipient;
        }

        public boolean isResend() {
            return isResend;
        }

        public void setResend(boolean isResend) {
            this.isResend = isResend;
        }

        public XService.ToCall getSendResult() {
            return sendResult;
        }

        public void setSendResult(XService.ToCall sendResult) {
            this.sendResult = sendResult;
        }
    }

    /**
     * Returns whether current operation should be cancelled. 
     * Uses local canceller.
     * 
     * @return
     */
    protected boolean isCancelled(){
    	if (canceller==null) return false;
    	return canceller.isCancelled();
    }
    
    /**
     * Throws OperationCancelledException if canceller cancels.
     * @throws OperationCancelledException
     */
    protected void throwIfCancelled() throws OperationCancelledException{
    	if (isCancelled()) throw new OperationCancelledException();
    }

	public boolean isFetchCertIfMissing() {
		return fetchCertIfMissing;
	}

	public void setFetchCertIfMissing(boolean fetchCertIfMissing) {
		this.fetchCertIfMissing = fetchCertIfMissing;
	}

	public Canceller getCanceller() {
		return canceller;
	}

	public void setCanceller(Canceller canceller) {
		this.canceller = canceller;
	}

	public FileTransferManager getFtMgr() {
		return ftManager;
	}

	public void setFtMgr(FileTransferManager ftMgr) {
		this.ftManager = ftMgr;
	}
}

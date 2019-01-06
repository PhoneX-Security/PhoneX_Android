package net.phonex.service.messaging;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcelable;
import android.text.TextUtils;

import net.phonex.accounting.PermissionLimits;
import net.phonex.core.Constants;
import net.phonex.core.Intents;
import net.phonex.db.entity.SipClist;
import net.phonex.db.entity.SipProfile;
import net.phonex.core.SipUri;
import net.phonex.db.entity.CallLog;
import net.phonex.db.entity.QueuedMessage;
import net.phonex.db.entity.SipMessage;
import net.phonex.db.entity.TrialEventLog;
import net.phonex.ft.transfer.DownloadFileParams;
import net.phonex.ft.transfer.FileTransferManager;
import net.phonex.ft.transfer.TransferParameters;
import net.phonex.ft.transfer.UploadFileParams;
import net.phonex.pub.proto.FileTransfer;
import net.phonex.service.XService;
import net.phonex.soap.TrialEventSaveTask;
import net.phonex.util.FileTransferUtils;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.analytics.AnalyticsReporter;
import net.phonex.util.analytics.AppEvents;
import net.phonex.util.analytics.AppPassiveEvents;
import net.phonex.util.guava.Joiner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Application message protocol dispatcher
 * Created by miroc on 16.10.14.
 */
public class AmpDispatcher {
    private static final String TAG = "AmpDispatcher";

    public static class PROTOCOLS {
        public static final int TEXT = 1;
        public static final int TEXT_VERSION_PLAINTEXT = 1;
        public static final int TEXT_VERSION_AMP_SIMPLE = 2;
        public static final int NOTIFICATION = 2;
        public static final int NOTIFICATION_VERSION_GENERAL_MSG_NOTIFICATION = 1;
        public static final int FTRANSFER = 3;
        public static final int FTRANSFER_DOWNLOAD = 1;
        public static final int FTRANSFER_UPLOAD = 2;

    }

    private Context context;
    private XService service;

    public AmpDispatcher(XService service) {
        if (service == null){
            throw new IllegalArgumentException("Cannot create AmpDispatcher with null XService");
        }

        this.service = service;
        this.context = service.getApplicationContext();
    }

    /**
     * Put text message into MessageQueue
     * @param ctx
     * @param sipMessageId
     */
    public static void dispatchTextMessage(Context ctx, int sipMessageId){
        SipMessage sipMessage = SipMessage.getById(ctx.getContentResolver(), sipMessageId, SipMessage.FULL_PROJECTION);
        dispatchTextMessage(ctx, sipMessage);
    }

    /**
     * Put text message into MessageQueue
     * @param ctx
     * @param sipMessage
     */
    public static void dispatchTextMessage(Context ctx, SipMessage sipMessage){
        Log.df(TAG, "Dispatching SipMessage [%s]", sipMessage);

        QueuedMessage msg = new QueuedMessage(sipMessage.getFrom(), sipMessage.getTo(), true);
        // TODO unify int and long
        msg.setReferencedId((int) sipMessage.getId());

        msg.setMessageProtocolType(PROTOCOLS.TEXT);
        msg.setMessageProtocolVersion(PROTOCOLS.TEXT_VERSION_AMP_SIMPLE);

        msg.setTransportProtocolType(StpSimple.PROTOCOL_TYPE);
        msg.setTransportProtocolVersion(
                TransportProtocolDispatcher.PROTOCOLS.STP_SIMPLE_VERSION_3
        );

        try {
            msg.setTransportPayload(AmpSimple.buildSerializedMessage(sipMessage.getBody(), sipMessage.getOrEstablishRandNum(ctx.getContentResolver())));
        } catch (IOException e) {
            Log.ef(TAG, "Cannot build AmpSimple message with SipMessage [%s]", sipMessage);
            return;
        }

        enqueue(ctx, msg);
    }

    /**
     * Put NewFile notification into MessageQueue. Nonce2 and filename is retrieved from SipMessage
     * @param ctx
     * @param sipMessageId
     */
    public static void dispatchNewFileNotification(Context ctx, int sipMessageId){
        SipMessage sipMessage = SipMessage.getById(ctx.getContentResolver(), sipMessageId, SipMessage.FULL_PROJECTION);
        dispatchNewFileNotification(ctx, sipMessage);
    }

    /**
     * * Put NewFile notification into MessageQueue. Nonce2 and filename is retrieved from SipMessage
     * @param ctx
     * @param sipMessage
     */
    public static void dispatchNewFileNotification(Context ctx, SipMessage sipMessage){
        if (!sipMessage.getMimeType().equals(SipMessage.MIME_FILE)){
            Log.ef(TAG, "Trying to send SipMessage as NewFile notification but its MIME type is different from [%s], msg [%s] ", SipMessage.MIME_FILE, sipMessage);
            SipMessage.setMessageError(ctx.getContentResolver(), sipMessage.getId(), SipMessage.MESSAGE_TYPE_ENCRYPT_FAIL, SipMessage.ERROR_ENCRYPT_GENERIC, "");
            return;
        }
        int sipMessageNonce = sipMessage.getOrEstablishRandNum(ctx.getContentResolver());

        // body field stores filename
        FileTransfer.GeneralMsgNotification notification = FileTransferUtils.createFileNotification(sipMessage.getFileNonce(), sipMessage.getBody(), sipMessageNonce);

        Log.df(TAG, "Dispatching Notification [%s] to user [%s]", notification.toString(), sipMessage.getTo());

        QueuedMessage msg = new QueuedMessage(sipMessage.getFrom(), sipMessage.getTo(), true);
        // TODO unify int and long
        msg.setReferencedId((int) sipMessage.getId());

        msg.setMessageProtocolType(PROTOCOLS.NOTIFICATION);
        msg.setMessageProtocolVersion(PROTOCOLS.NOTIFICATION_VERSION_GENERAL_MSG_NOTIFICATION);

        msg.setTransportPayload(notification.toByteArray());
        msg.setTransportProtocolType(StpSimple.PROTOCOL_TYPE);
        msg.setTransportProtocolVersion(
                TransportProtocolDispatcher.PROTOCOLS.STP_SIMPLE_VERSION_3
        );
        enqueue(ctx, msg);
    }

    /**
     * Acknowledge that user has read the message (or multiple messages at once)
     * @param ctx
     * @param from
     * @param to
     * @param nonces Messages are identified by their nonce-s (aka randNum in SipMessage)
     */
    public static void dispatchReadAckNotification(Context ctx, String from, String to, List<Integer> nonces){
        Log.df(TAG, "Dispatching ReadAck notification for nonces [%s]", nonces);

        if (nonces == null || nonces.size() == 0){
            Log.w(TAG, "No nonces to acknowledge");
            return;
        }

        FileTransfer.GeneralMsgNotification notification = FileTransfer.GeneralMsgNotification.newBuilder()
                .setNotifType(FileTransfer.GeneralMsgNotification.NotificationType.MessageReadAck)
                .addAllAckNonces(nonces)
                .setTimestamp(System.currentTimeMillis())
                .setNonce(MiscUtils.randomInt())
                .build();

        QueuedMessage msg = new QueuedMessage(from, to, true); // No reference id required
        msg.setMessageProtocolType(PROTOCOLS.NOTIFICATION);
        msg.setMessageProtocolVersion(PROTOCOLS.NOTIFICATION_VERSION_GENERAL_MSG_NOTIFICATION);

        // Only authentication is required in this case, use StpSimpleAuth instead of StpSimple
        msg.setTransportProtocolType(StpSimpleAuth.PROTOCOL_TYPE);
        msg.setMessageProtocolSubType(FileTransfer.GeneralMsgNotification.NotificationType.MessageReadAck.getNumber());
        msg.setTransportProtocolVersion(
                TransportProtocolDispatcher.PROTOCOLS.STP_SIMPLE_AUTH_VERSION_2
//                supportedStpSimpleAuthVersion(ctx, to)
        );
        msg.setTransportPayload(notification.toByteArray());

        enqueue(ctx, msg);
    }

    /**
     * Dispatches a new missed call notification to a remote party.
     * Indicate that the user tried to reach the remote party while it was online / unavailable.
     *
     * @param ctx
     * @param from
     * @param to
     */
    public static void dispatchMissedCallNotification(Context ctx, String from, String to, String callId){
        Log.df(TAG, "Dispatching MissedCall notification from [%s] to [%s]", from, to);

        FileTransfer.GeneralMsgNotification notification = FileTransfer.GeneralMsgNotification.newBuilder()
                .setNotifType(FileTransfer.GeneralMsgNotification.NotificationType.MissedCall)
                .setTimestamp(System.currentTimeMillis())
                .setNonce(MiscUtils.randomInt())
                .setSipCallId(callId == null ? "" : callId)
                .build();

        QueuedMessage msg = new QueuedMessage(from, to, true); // No reference id required
        msg.setMessageProtocolType(PROTOCOLS.NOTIFICATION);
        msg.setMessageProtocolVersion(PROTOCOLS.NOTIFICATION_VERSION_GENERAL_MSG_NOTIFICATION);

        // Only authentication is required in this case, use StpSimpleAuth instead of StpSimple
        msg.setTransportProtocolType(StpSimpleAuth.PROTOCOL_TYPE);
        msg.setMessageProtocolSubType(FileTransfer.GeneralMsgNotification.NotificationType.MissedCall.getNumber());
        msg.setTransportProtocolVersion(
                // only v2 is supported
                TransportProtocolDispatcher.PROTOCOLS.STP_SIMPLE_AUTH_VERSION_2
        );
        msg.setTransportPayload(notification.toByteArray());

        enqueue(ctx, msg);
    }

    /**
     * Enqueues message that handles file transfer logic.
     *
     * @param ctxt
     * @param sipMessage
     * @param params
     */
    public static void dispatchNewFileDownload(Context ctxt, SipMessage sipMessage, DownloadFileParams params) {
        dispatchNewFileTransfer(ctxt, sipMessage, params);
    }

    /**
     * Enqueues message that handles file transfer logic.
     * @param ctxt
     * @param sipMessage
     * @param params
     */
    public static void dispatchNewFileUpload(Context ctxt, SipMessage sipMessage, UploadFileParams params) {
        dispatchNewFileTransfer(ctxt, sipMessage, params);
    }

    /**
     * Enqueues message that handles file transfer logic, message carries upload or download transfer itself.
     *
     * @param ctxt
     * @param sipMessage
     * @param params
     */
    public static void dispatchNewFileTransfer(Context ctxt, SipMessage sipMessage, TransferParameters params) {
        Log.df(TAG, "Dispatching file transfer, nonce2: %s", sipMessage.getFileNonce());

        QueuedMessage msg = new QueuedMessage(sipMessage.getFrom(), sipMessage.getTo(), sipMessage.isOutgoing()); // No reference id required
        msg.setReferencedId((int) sipMessage.getId());
        msg.setMessageProtocolType(PROTOCOLS.FTRANSFER);
        msg.setMessageProtocolVersion(sipMessage.isOutgoing() ? PROTOCOLS.FTRANSFER_UPLOAD : PROTOCOLS.FTRANSFER_DOWNLOAD);

        // Only authentication is required in this case, use StpSimpleAuth instead of StpSimple
        msg.setTransportProtocolType(TransportProtocolDispatcher.PROTOCOLS.FTRANSFER);
        msg.setTransportProtocolVersion(TransportProtocolDispatcher.PROTOCOLS.FTRANSFER_VERSION_1);

        // Serialize params to transport body.
        msg.setTransportPayload(MiscUtils.marshall(params));

        enqueue(ctxt, msg);
    }

    /**
     * Receive transport packet (containing incoming application message) from  transport layer, process
     * @param packet
     */
    public void receive(DecryptedTransportPacket packet){
        Log.df(TAG, "Receiving DecryptedTransportPacket [%s]", packet);
        switch (packet.ampType) {
            case PROTOCOLS.TEXT:
                receiveText(packet);
                break;
            case PROTOCOLS.NOTIFICATION:
                receiveNotification(packet);
                break;
            default:
                Log.ef(TAG, "receive() error: unknown amp type for packet [%s]", packet);
                break;
        }
    }

    /**
     * Report sending state of outgoing message (done by lower transport layer)
     * @param queuedMessage
     * @param sendingState
     */
    public void reportState(QueuedMessage queuedMessage, SendingState sendingState){
        Log.df(TAG, "Reporting state [%s] for queued message [%s]", sendingState, queuedMessage);
        switch (queuedMessage.getMessageProtocolType()){
            case PROTOCOLS.TEXT:
                reportSipMessageState(queuedMessage.getReferencedId(), sendingState, false);
                break;
            case PROTOCOLS.NOTIFICATION:

                try {
                    byte[] transportPayload = queuedMessage.getTransportPayload();
                    FileTransfer.GeneralMsgNotification notification = FileTransfer.GeneralMsgNotification.parseFrom(transportPayload);
                    reportNotificationState(queuedMessage.getReferencedId(), notification,sendingState);
                } catch (IOException e) {
                    Log.ef(TAG, e, "reportState: Cannot decode transport payload  from msg [%s]", queuedMessage);
                }
                break;
        }
    }

    public static boolean hasMessagingCapability(Context ctx, String remoteSip, String capability){
        try { // rather be defensive
            SipClist profile = SipClist.getProfileFromDbSip(ctx, remoteSip, SipClist.FULL_PROJECTION);
            return profile.hasCapability(capability);
        } catch (Exception ex){
            Log.e(TAG, "Error while determining user messaging capabilities", ex);
            return false;
        }
    }

    private void reportNotificationState(int sipMessageId, FileTransfer.GeneralMsgNotification notification, SendingState state){
        switch (notification.getNotifType()) {
            case NewFile:
                // new file is reported back to SipMessage
                reportSipMessageState(sipMessageId, state, true);
                break;
            case MessageReadAck:
                // After sending Ack, we do not care anymore
                break;
            default:
                Log.inf(TAG, "reportNotificationState: Currently unsupported notification type [%s]", notification.getNotifType());
                break;
        }
    }

    private void reportSipMessageState(int sipMessageId, SendingState sendingState, boolean isFile){
        Log.vf(TAG, "reportSipMessageState");
        if (context == null){
            Log.wf(TAG, "reportSipMessageState: context is NULL, cannot update state for messageId [%d]", sipMessageId);
            return;
        }

        ContentResolver resolver = context.getContentResolver();

        switch (sendingState.type){
            case SENDING:
                SipMessage.setMessageType(resolver, sipMessageId, SipMessage.MESSAGE_TYPE_PENDING);
                break;
            case ACK_POSITIVE:
            {
                Log.vf(TAG, "Positive acknowledgment, isFile=%s", isFile);
                ContentValues cv = new ContentValues();
                cv.put(SipMessage.FIELD_ERROR_CODE, sendingState.pjsipErrorCode);
                cv.put(SipMessage.FIELD_ERROR_TEXT, sendingState.pjsipErrorText);
                cv.put(SipMessage.FIELD_TYPE, SipMessage.MESSAGE_TYPE_SENT);
                SipMessage.updateMessage(resolver, sipMessageId, cv);
                if (!isFile){
                    if (PermissionLimits.getMessagesDayLimit(resolver) >= 0){
                        TrialEventSaveTask trialEventSaveTask = new TrialEventSaveTask(context, TrialEventLog.TYPE_OUTGOING_MESSAGE);
                        new Thread(trialEventSaveTask).start();
                    }
                } else {
                    service.getPermissionManager().asyncConsumeFiles(sipMessageId);
                }

                // report action
                AnalyticsReporter.fromApplicationContext(context.getApplicationContext()).event(isFile ? AppEvents.MESSAGE_FILE_SENT : AppEvents.MESSAGE_TEXT_SENT);

                break;
            }
            case ACK_NEGATIVE:
            {
                ContentValues cv = new ContentValues();
                // store what type of error happened
                cv.put(SipMessage.FIELD_ERROR_CODE, sendingState.pjsipErrorCode);
                cv.put(SipMessage.FIELD_ERROR_TEXT, sendingState.pjsipErrorText);
                SipMessage.updateMessage(resolver, sipMessageId, cv);

                // do not update type, reason:
                // we do not change PENDING message type until we switch to backoff resending and then reach max num of resends, so user is not disturbed by seeing message sending repeatedly fails
                // update to MESSAGE_TYPE_FAILED happens only after that
                break;
            }
            case SET_FOR_BACKOFF:
                ContentValues cv = new ContentValues();
                cv.put(SipMessage.FIELD_TYPE, SipMessage.MESSAGE_TYPE_QUEUED_BACKOFF);
                cv.put(SipMessage.FIELD_RESEND_TIME, sendingState.resendTime);
                SipMessage.updateMessage(resolver, sipMessageId, cv);
                break;
            case FAILED_INVALID_DESTINATION:
                SipMessage.setMessageError(resolver, sipMessageId, SipMessage.MESSAGE_TYPE_ENCRYPT_FAIL, 0, "");
                break;
            case FAILED_MISSING_REMOTE_CERT:
                SipMessage.setMessageError(resolver, sipMessageId, SipMessage.MESSAGE_TYPE_ENCRYPT_FAIL, SipMessage.ERROR_MISSING_CERT, "");
                break;
            case FAILED_REACHED_MAX_NUM_OF_RESENDS:
                SipMessage.setMessageType(resolver, sipMessageId, SipMessage.MESSAGE_TYPE_FAILED);
                break;
            case FAILED_GENERIC:
                SipMessage.setMessageError(resolver, sipMessageId, SipMessage.MESSAGE_TYPE_ENCRYPT_FAIL, SipMessage.ERROR_ENCRYPT_GENERIC, "");
                break;
            default:
                Log.vf(TAG, "Sending state [%s] is not propagated to SipMessage", sendingState);
                break;
        }
    }

    private void receiveNotification(DecryptedTransportPacket packet) {
        Log.df(TAG, "receiving Notification packet [%s]", packet);

        switch (packet.ampVersion) {
            case PROTOCOLS.NOTIFICATION_VERSION_GENERAL_MSG_NOTIFICATION:
                FileTransfer.GeneralMsgNotification notification = null;
                try {
                    if (packet.payloadIsString) { // legacy, data passed as String from S/MIME transport layer
                        String textPayload = new String(packet.payload);
                        notification = FileTransferUtils.parseNotification(textPayload);
                    } else {
                        notification = FileTransfer.GeneralMsgNotification.parseFrom(packet.payload);
                    }
                } catch (Exception e) {
                    Log.ef(TAG, e, "Cannot parse GeneralMsgNotification, data [%s]", packet);
                }
                processGeneralMsgNotification(notification, packet);
                break;
            default:
                Log.ef(TAG, "receiveNotification() error: unknown amp version for data [%s]", packet);
                break;
        }
    }

    private void receiveText(DecryptedTransportPacket packet) {
        Log.df(TAG, "receiving Text packet [%s]", packet);
        String messagePlaintext;

        if (isSipMessageDuplicate(packet.transportPacketHash, packet.from, packet.to)){
            Log.i(TAG, "Message is already stored");
            return;
        }

        AnalyticsReporter.fromApplicationContext(context.getApplicationContext()).passiveEvent(AppPassiveEvents.MESSAGE_TEXT_RECEIVED);

        // send information intent about received SipMessage
        Intent intent = new Intent(Intents.ACTION_SIP_MESSAGE_RECEIVED);
        intent.putExtra(SipMessage.FIELD_FROM, packet.from);
        intent.putExtra(SipMessage.FIELD_TO, packet.to);
        intent.putExtra(SipMessage.FIELD_IS_OUTGOING, false);
        MiscUtils.sendBroadcast(context, intent);

        int msgNonceId;

        // process message itself
        switch (packet.ampVersion) {
            case PROTOCOLS.TEXT_VERSION_PLAINTEXT:
                messagePlaintext = new String(packet.payload);
                msgNonceId = packet.nonce; // in case of plaintext, it stores no nonce-id, so we take nonce from lower transport layer (backward comp)
                break;

            case PROTOCOLS.TEXT_VERSION_AMP_SIMPLE:
                try {
                    AmpSimple ampSimple = AmpSimple.loadMessage(packet.payload);
                    messagePlaintext = ampSimple.getMessage();
                    msgNonceId = (ampSimple.getNonce() != null) ? ampSimple.getNonce() : packet.nonce; // if nonce is missing, take one from transport layer (backward comp)
                } catch (IOException e) {
                    Log.ef(TAG, e, "Cannot Decode AmpSimple message, data []", packet);
                    return;
                }
                break;
            default:
                Log.ef(TAG, "ReceiveNotification() error: unknown amp version data [%s]", packet);
                return;
        }
        if (TextUtils.isEmpty(messagePlaintext)){
            Log.w(TAG, "Received empty plaintext, suspicious, do not store such message.");
            return;
        }

        String from = SipUri.getCanonicalSipContact(packet.from, false);
        String to = SipUri.getCanonicalSipContact(packet.to, false);

        // insert plaintext as SipMessage
        SipMessage sipMessage = new SipMessage(from,
                to,
                null, // legacy field
                messagePlaintext, //cannot be null (See MessageAdapter), // legacy (plaintext is also stored in body_decrypted)
                Constants.SIP_SECURE_MSG_MIME,
                System.currentTimeMillis(),
                SipMessage.MESSAGE_TYPE_INBOX,
                from);

        sipMessage.setBodyHash(packet.transportPacketHash);
        sipMessage.setSendDate(packet.sendDate);
        sipMessage.setRead(false);
        sipMessage.setBodyDecrypted(messagePlaintext);
        sipMessage.setRandNum(msgNonceId);

        // Security properties
        sipMessage.setSignatureOK(packet.macValid && packet.signatureValid);

        if (!packet.isValid){
            Log.wf(TAG, "Received SipMessage is not valid [%s]", packet.toString());
            sipMessage.setDecryptionStatus(SipMessage.DECRYPTION_STATUS_DECRYPTION_ERROR);
            sipMessage.setErrorCode(SipMessage.ERROR_DECRYPT_GENERIC);

        } else {
            sipMessage.setDecryptionStatus(SipMessage.DECRYPTION_STATUS_OK);
        }

        Log.df(TAG, "Storing SipMessage in db [%s]", sipMessage.toString());
        if (context!=null){
            context.getContentResolver().insert(SipMessage.MESSAGE_URI, sipMessage.getContentValues());
        } else {
            Log.e(TAG, "receiveText(): Null context, cannot store SipMessage");
        }

        // Notify android OS of the new message - display in Notification bar
        notifyOfUnreadSipMessage(sipMessage);
    }

    /**
     * Handles transfer request from the TransportProtocolDispatcher.
     * @param msg
     */
    protected void receiveTransfer(QueuedMessage msg) {
        handleFtransfer(msg, false);
    }

    /**
     * Handles transfer request from the TransportProtocolDispatcher.
     * @param msg
     */
    protected void transmitTransfer(QueuedMessage msg) {
        handleFtransfer(msg, true);
    }

    /**
     * Handles transfer request from the TransportProtocolDispatcher.
     * @param msg
     * @param upload
     */
    protected void handleFtransfer(QueuedMessage msg, boolean upload){
        final ContentResolver cr    = context.getContentResolver();
        final Integer         refId = msg.getReferencedId();
        if (refId == null) {
            Log.e(TAG, "Referenced id is nil!");
            SendingState state = new SendingState(SendingState.Type.FAILED_GENERIC, -1, null);
            reportState(msg, state);
            return;
        }

        final long msgId = refId.longValue();

        // Try to fetch referenced message so we obtain nonce2 and additional transfer details.
        final SipMessage dbMsg = SipMessage.getById(cr, msgId, SipMessage.FULL_PROJECTION);
        if (dbMsg == null){
            Log.ef(TAG, "Referenced message could not be found, id=%s", msgId);
            SendingState state = new SendingState(SendingState.Type.FAILED_GENERIC, -1, null);
            reportState(msg, state);
            return;
        }

        // Deserialize upload parameters from transfer payload.
        TransferParameters params = null;
        try {
            Parcelable.Creator<? extends TransferParameters> creator = upload ? UploadFileParams.CREATOR : DownloadFileParams.CREATOR;
            params = MiscUtils.unmarshall(msg.getTransportPayload(), creator);
        } catch(Exception e){
            Log.e(TAG, "Could not deserialize upload parameters");
            SendingState state = new SendingState(SendingState.Type.FAILED_GENERIC, -1, null);
            reportState(msg, state);
            return;
        }

        params.setQueueMsgId((long) msg.getId());
        if (upload) {
            service.getFtManager().enqueueFile2Upload((UploadFileParams)params);
        } else {
            service.getFtManager().enqueueFile2Download((DownloadFileParams)params);
        }
    }

    private boolean isFileAckDuplicate(String nonce2){
        // Feature: do not save duplicated messages.
        // Sometimes may happen server sends offline messages multiple times.
        // File Ack should have unique nonce2
        try {
            Cursor c = context.getContentResolver().query(
                    SipMessage.MESSAGE_URI,
                    new String[]{SipMessage.FIELD_ID},
                    SipMessage.FIELD_FILE_NONCE + "=?" + " AND " +
                            SipMessage.FIELD_IS_OUTGOING + "=?",
                    new String[]{nonce2, "0"},
                    null);
            if (c!=null){
                boolean exists = c.getCount()>0;
                try {
                    c.close();
                } catch(Exception ex){
                    Log.e(TAG, "isFileAckDuplicate; Cannot close cursor", ex);
                }

                // Message is already in the database, do not store it again.
                if (exists){
                    return true;
                }
            }

        } catch (Exception e){
            Log.e(TAG, "Cannot determine if file ack message is already stored", e);
        }
        return false;
    }

    private boolean isSipMessageDuplicate(String transportPacketHash, String from, String to){
        // Feature: do not save duplicated messages.
        // Sometimes may happen server sends offline messages multiple times.
        try {
            Cursor c = context.getContentResolver().query(
                    SipMessage.MESSAGE_URI,
                    new String[]{SipMessage.FIELD_ID},
                    SipMessage.FIELD_BODY_HASH + "=?" + " AND " +
                            SipMessage.FIELD_FROM + "=?" + " AND " +
                            SipMessage.FIELD_TO + "=?",
                    new String[]{transportPacketHash, from, to,},
                    null);
            if (c!=null){
                boolean exists = c.getCount()>0;
                try {
                    c.close();
                } catch(Exception ex){
                    Log.e(TAG, "isSipMessageDuplicate; Cannot close cursor", ex);
                }

                // Message is already in the database, do not store it again.
                if (exists){
                    return true;
                }
            }

        } catch (Exception e){
            Log.e(TAG, "Cannot determine if message is already stored", e);
        }
        return false;
    }

    private void processGeneralMsgNotification(FileTransfer.GeneralMsgNotification notification, DecryptedTransportPacket packet){
        // only new file notification non valid packet will be processed and signalized as non valid
        if (!packet.isValid && notification.getNotifType() != FileTransfer.GeneralMsgNotification.NotificationType.NewFile){
            Log.wf(TAG, "processGeneralMsgNotification; packet is invalid, packet=%s", packet);
            //return;
        }


        switch (notification.getNotifType()) {
            case NewFile:
                processNewFileNotification(notification, packet);
                break;

            case MessageReadAck:
                processMessageReadAckNotification(notification);
                break;

            case MissedCall:
                processMissedCallNotification(notification, packet);
                break;

            case DhKeySyncRequest:
                // Re-check DH keys so user can continue sending new files.
                XService.triggerDHKeyUpdate(context, false);
            case FullMailbox:
            case Other:
                Log.wf(TAG, "Received currently unsupported GeneralMsgNotification type [%d]", notification.getNotifType().getNumber());
                break;

            default:
                Log.ef(TAG, "Received unknown GeneralMsgNotification type [%d]", notification.getNotifType().getNumber());
                break;
        }
    }

    private void processNewFileNotification(FileTransfer.GeneralMsgNotification notification, DecryptedTransportPacket packet){
        final String from = SipUri.getCanonicalSipContact(packet.from, false);
        final String to = SipUri.getCanonicalSipContact(packet.to, false);
        if (isFileAckDuplicate(notification.getFileTransferNonce())){
            Log.inf(TAG, "NewFile notification with nonce2 [%s] is already stored, dropping.", notification.getFileTransferNonce());
            return;
        }

        AnalyticsReporter.fromApplicationContext(context.getApplicationContext()).passiveEvent(AppPassiveEvents.MESSAGE_FILE_RECEIVED);

        // New encrypted file notification.
        SipMessage sipMessage = new SipMessage(from,
                to,
                null, // legacy field
                null, // legacy (using body_decrypted)
                Constants.SIP_SECURE_FILE_NOTIFY_MIME,
                System.currentTimeMillis(),
                SipMessage.MESSAGE_TYPE_QUEUED,
                to);

        sipMessage.setSendDate(packet.sendDate);
        sipMessage.setRead(false);

        // before messaging v2, nonce was not present in notification message, therefore we retrieve the one present in lower transport layer (for backward compatibility)
        sipMessage.setRandNum(notification.hasNonce() ? notification.getNonce() : packet.nonce);

        // filename is stored in title
        sipMessage.setBodyDecrypted(notification.getTitle());
        // nonce2 serves as unique file identifier
        sipMessage.setFileNonce(notification.getFileTransferNonce());

        // Security properties
        sipMessage.setSignatureOK(packet.macValid && packet.signatureValid);

        if (!packet.isValid){
            Log.wf(TAG, "Received SipMessage is not valid [%s]", packet.toString());
            sipMessage.setDecryptionStatus(SipMessage.DECRYPTION_STATUS_DECRYPTION_ERROR);
            sipMessage.setErrorCode(SipMessage.ERROR_DECRYPT_GENERIC);

        } else {
            sipMessage.setDecryptionStatus(SipMessage.DECRYPTION_STATUS_OK);
        }

        Log.df(TAG, "Storing in DB: NewFile GeneralMsgNotification [%s]", notification.toString());
        final Uri uri = context.getContentResolver().insert(SipMessage.MESSAGE_URI, sipMessage.getContentValues());

        // Automatically fetch meta information about the file transfer.
        sipMessage.setId(ContentUris.parseId(uri));
        FileTransferManager.dispatchDownloadTransfer(context, sipMessage, null);

        // Notify android OS of the new message - display in Notification bar
        notifyOfUnreadSipMessage(sipMessage);

        // Re-check DH keys so user can continue sending new files.
        XService.triggerDHKeyUpdate(context, false);
    }

    private void processMessageReadAckNotification(FileTransfer.GeneralMsgNotification notification){
        if (notification.getAckNoncesCount() <=0){
            Log.ef(TAG, "Received MessageReadAck with 0 acknowledged nonces");
            return;
        }

        // get nonces -> create array
        List<String> noncesList = new ArrayList<String>();
        for (Integer nonce : notification.getAckNoncesList()){
            noncesList.add(nonce.toString());
        }

        String[] binds = noncesList.toArray(new String[noncesList.size()]);

        // create placeholders for IN statement
        String[] placeholdersList = new String[noncesList.size()];
        Arrays.fill(placeholdersList, "?");
        String inPlaceholders = Joiner.on(",").join(placeholdersList);

        String where = SipMessage.FIELD_RANDOM_NUM + " IN (" + inPlaceholders + ") AND " + SipMessage.FIELD_READ + "=0";

        // check validity of read time
        long readTime = notification.getTimestamp();
        if (readTime > System.currentTimeMillis()){
            Log.wf(TAG, "Receiving MessageReadAck: read time [%d] is in future, possibly clocks are de-synced", readTime);
            readTime = System.currentTimeMillis();
        } else {
            Cursor c = context.getContentResolver().query(SipMessage.MESSAGE_URI,
                    new String[]{"MIN(" + SipMessage.FIELD_DATE + ")"}, where, binds, null);
            if (c.moveToNext()){
                long oldestMessageCreationTime = c.getLong(0); // One year tolerance 1000L*60L*60L*24L*365L.

                if (readTime <= oldestMessageCreationTime){
                    Log.wf(TAG, "Receiving MessageReadAck: read time [%d] is prior message creation time, possibly clocks are de-synced", readTime);
                    readTime = oldestMessageCreationTime;
                }
            }
            MiscUtils.closeCursorSilently(c);
        }

        // perform update - set Read to 1
        ContentValues cv = new ContentValues();
        cv.put(SipMessage.FIELD_READ, 1);
        cv.put(SipMessage.FIELD_READ_DATE, readTime);

        int updated = context.getContentResolver().update(SipMessage.MESSAGE_URI, cv, where, binds);

        Log.inf(TAG, "Receiving MessageReadAck notification for following nonces [%s], num of updated SipMessages in db [%d]", noncesList.toString(), updated);
    }

    private void processMissedCallNotification(FileTransfer.GeneralMsgNotification notification, DecryptedTransportPacket packet){
        final String from = SipUri.getCanonicalSipContact(packet.from, false);
        final String to = SipUri.getCanonicalSipContact(packet.to, false);

        // Load profile associated to "to".
        final SipProfile profile = SipProfile.getProfileFromDbName(this.context, to, false, SipProfile.ACC_PROJECTION);
        if (profile == null){
            Log.wf(TAG, "Profile for %s not found", to);
            return;
        }

        // Load contact list entry for this user.
        final SipClist clist = SipClist.getProfileFromDbSip(this.context, from);
        if (clist == null){
            Log.wf(TAG, "Contact list entry not found for user %s", from);
            return;
        }

        final Long eventTimestamp = notification.hasTimestamp() && notification.getTimestamp() > 100
                ? notification.getTimestamp() : null;
        final Long eventNonce = notification.hasNonce() && notification.getNonce() != 0
                ? (long)notification.getNonce() : null;
        final String sipCallId = notification.hasSipCallId() && !MiscUtils.isEmpty(notification.getSipCallId())
                ? notification.getSipCallId() : null;

        // Verify this is not already inserted in DB. Avoid duplicate notifications for the same event.
        final CallLog prevCl = CallLog.getLogByEventDescription(context.getContentResolver(),
                from,
                profile.getId(),
                eventTimestamp,
                eventNonce,
                sipCallId);

        if (prevCl != null){
            Log.vf(TAG, "Given callog already inserted in db. From %s, toId %s evtTime %s, evtNonce %s, sipCallId %s",
                    from,
                    profile.getAcc_id(),
                    eventTimestamp,
                    eventNonce,
                    sipCallId);
            return;
        }

        CallLog cli = new CallLog();
        cli.setRemoteContact(from);
        cli.setRemoteContactSip(from);
        cli.setRemoteAccountId(clist.getId());
        cli.setRemoteContactName(clist.getDisplayName());

        // Date extract.
        cli.setCallStart(packet.sendDate);
        cli.setDuration(0);

        // Missed call params
        cli.setNew(true);
        cli.setType(android.provider.CallLog.Calls.MISSED_TYPE);
        cli.setSeenByUser(false);
        cli.setAccountId(profile.getId());

        cli.setEventTimestamp(eventTimestamp);
        cli.setEventNonce(eventNonce);
        cli.setSipCallId(sipCallId);

        // Fill our own database
        cli.addToDatabase(this.context);
        Log.vf(TAG, "CallLog entry inserted: %s", cli.toString());

        // Notify user with status notification. Broadcast intent.
        final Intent intent = new Intent(Intents.ACTION_MISSED_CALL_RECEIVED);
        intent.putExtra(Intents.EXTRA_MISSED_CALL_RECEIVED, cli);
        MiscUtils.sendBroadcast(context, intent);
    }

    private void notifyOfUnreadSipMessage(SipMessage msg){
        try {
            service.getNotificationManager().notifyUnreadMessage(msg);
        } catch (Exception ex){
            Log.e(TAG, "Cannot send notification to StatusbarNotifications manager", ex);
        }
    }

    private static void enqueue(Context ctx, QueuedMessage msg){
        try {
            // just in case of there are any duplicates in the queue (usually in the case of resend), we remove them
            int count = QueuedMessage.deleteOutgoingDuplicates(ctx.getContentResolver(), msg);
            Log.vf(TAG, "Number of removed duplicates in the messagequeue [%d]", count);

            if (msg.getTime() == null)
                msg.setTime(System.currentTimeMillis());

            Log.df(TAG, "Putting new message in the messagequeue [%s]", msg);
            final Uri uriInsert = ctx.getContentResolver().insert(QueuedMessage.URI, msg.getDbContentValues());
            if (uriInsert == null){
                Log.wf(TAG, "Insert not successfull, null URI for msg: %s", msg);
            }
        } catch (Exception ex){
            Log.ef(TAG, ex, "Error while putting GeneralMessage to queue");
        }
    }
}

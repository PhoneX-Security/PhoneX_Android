package net.phonex.service.messaging;

import android.os.RemoteException;
import android.text.TextUtils;

import net.phonex.core.Constants;
import net.phonex.db.entity.SipProfile;
import net.phonex.db.entity.QueuedMessage;
import net.phonex.pub.parcels.SipMsgAux;
import net.phonex.service.XService;
import net.phonex.util.Log;
import net.phonex.util.crypto.CertificatesAndKeys.UserIdentity;
import net.phonex.util.crypto.MessageDigest;

import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

/**
 * Created by miroc on 16.10.14.
 */
public class TransportProtocolDispatcher {
    private static final String TAG = "TransportProtocolDispatcher";

    private XService service;

    private MessageQueueActions messageQueueListener;

    //private Context context;

    private UserIdentity userIdentity;

    private X509Certificate remoteCert;
    private AmpDispatcher ampDispatcher;
    public TransportProtocolDispatcher(XService service, UserIdentity userIdentity, X509Certificate remoteCert) {
        this.service = service;
        this.userIdentity = userIdentity;
        this.remoteCert = remoteCert;

        ampDispatcher = new AmpDispatcher(service);
    }
    public static class PROTOCOLS {
        public static final int S_MIME = 1;
        public static final int S_MIME_VERSION = 1;

        public static final int STP_SIMPLE = 2;
//        public static final int STP_SIMPLE_VERSION_1 = 1;
//        public static final int STP_SIMPLE_VERSION_2 = 2;
        public static final int STP_SIMPLE_VERSION_3 = 3;

        public static final int STP_SIMPLE_AUTH = 4;
//        public static final int STP_SIMPLE_AUTH_VERSION_1 = 1;
        public static final int STP_SIMPLE_AUTH_VERSION_2 = 2;

        public static final int FTRANSFER = 5;
        public static final int FTRANSFER_VERSION_1 = 1;
    }

    /*
     * Transmit data stored in transport payload of QueuedMessage
     * @param msg
     */
    public void transmit(QueuedMessage msg){
        Log.vf(TAG, "Transmitting queuedMessage [%s]", msg);

        // sendCounter is number of trials that happened before
        int sendCounter = msg.getSendCounter() != null ? msg.getSendCounter() : 0;
        String finalMessage = msg.getFinalMessage();

        if (!TextUtils.isEmpty(finalMessage)){ // resend

            Log.inf(TAG, "Resending message, trial number [%d]", sendCounter + 1);
            sendMessageToPjSip(msg, finalMessage);
        } else {
            switch (msg.getTransportProtocolType()) {
                case PROTOCOLS.STP_SIMPLE:
                case PROTOCOLS.STP_SIMPLE_AUTH:
                    transmitStpSimple(msg);
                    break;
                case PROTOCOLS.FTRANSFER:
                    transmitFtransfer(msg);
                    break;
                default:
                    Log.ef(TAG, "Error, unknown transport protocol type for data [%s]", msg);
                    break;
            }
        }
    }

    private void transmitStpSimple(QueuedMessage msg){
        if (!checkSupportedStpType(msg.getTransportProtocolType(), msg.getTransportProtocolVersion())){
            Log.ef(TAG, "Error, unsupported STP_SIMPLE transport protocol version for data [%s]", msg);
            if (messageQueueListener != null) {
                messageQueueListener.deleteAndReportToAppLayer(msg, SendingState.FAILED_GENERIC);
            }
            return;
        }

        try {
            Stp stp = null;
            if (msg.getTransportProtocolType() == PROTOCOLS.STP_SIMPLE){
                stp = new StpSimple(msg.getFrom(), userIdentity.getUserPrivateCredentials().getPk(), remoteCert);
            } else { // STP_SIMPLE_AUTH
                stp = new StpSimpleAuth(msg.getFrom(), userIdentity.getUserPrivateCredentials().getPk(), remoteCert);
            }
            stp.setVersion(msg.getTransportProtocolVersion());

            byte[] payload = stp.buildMessage(msg.getTransportPayload(), msg.getTo(), msg.getMessageProtocolType(), msg.getMessageProtocolVersion());

            MessageProtocolEnvelope envelope = MessageProtocolEnvelope.createEnvelope(payload, msg.getTransportProtocolType(), msg.getTransportProtocolVersion());
            String finalMsg = envelope.getBase64EncodedSerialized();

            // for potential resend
            messageQueueListener.storeFinalMessageWithHash(msg.getId(), finalMsg);
            sendMessageToPjSip(msg, finalMsg);

        } catch (Exception e) {
            Log.ef(TAG, e, "Error while creating StpSimple data [%s]", msg);
            messageQueueListener.deleteAndReportToAppLayer(msg, SendingState.FAILED_GENERIC);
            return;
        }
    }

    private void transmitFtransfer(QueuedMessage msg){
        try {
            ampDispatcher.transmitTransfer(msg);
        } catch (Exception e) {
            Log.ef(TAG, e, "Error while creating Ftransfer object data [%s]", msg);
            if (messageQueueListener != null){
                messageQueueListener.deleteAndReportToAppLayer(msg, SendingState.FAILED_GENERIC);
            }
        }
    }

    // send message via PJSIP (it consumes text payloads)
    private void sendMessageToPjSip(QueuedMessage msg, String finalMsg){
        SipProfile sender = SipProfile.getProfileFromDbName(service.getApplicationContext(), msg.getFrom(), false, SipProfile.ACC_PROJECTION);

        // currently all messages share the same MIME
        String mimeToSend = Constants.SIP_SECURE_MSG_MIME;

        try {
            service.getBinder().sendMessageImpl(finalMsg, finalMsg, msg.getRemoteContact(),
                    (int) sender.getId(), mimeToSend, msg.getId(), false,
                    new SipMsgAux(msg.getMessageProtocolType(), msg.getMessageProtocolSubType()));
        } catch (RemoteException e) {
            Log.ef(TAG, e, "Error reported by pjsip layer while sending message [%s]", msg);
            if (messageQueueListener != null) {
                messageQueueListener.deleteAndReportToAppLayer(msg, SendingState.FAILED_GENERIC);
            }
        }
    }

    public void receive(QueuedMessage msg){
        Log.vf(TAG, "receive() [%s]", msg);
        switch (msg.getTransportProtocolType()) {
            case PROTOCOLS.STP_SIMPLE:
            case PROTOCOLS.STP_SIMPLE_AUTH: // variant of STP_SIMPLE with authentication only
                receiveStpSimple(msg);
                break;
            case PROTOCOLS.FTRANSFER:
                receiveFtransfer(msg);
                break;
            default:
                Log.ef(TAG, "Receive() error: unknown transport protocol type for msg [%s]", msg);
                break;
        }
    }

    private void receiveStpSimple(QueuedMessage msg){
        if (!checkSupportedStpType(msg.getTransportProtocolType(), msg.getTransportProtocolVersion())){
            Log.ef(TAG, "Receive()  Error: unsupported STP version for msg [%s]", msg);
            return;
        }

        try {
            Stp stp = null;
            if (msg.getTransportProtocolType() == PROTOCOLS.STP_SIMPLE) {
                stp = new StpSimple(userIdentity.getUserPrivateCredentials().getPk(), remoteCert);
            } else { // STP_SIMPLE_AUTH
                stp = new StpSimpleAuth(userIdentity.getUserPrivateCredentials().getPk(), remoteCert);
            }

            StpProcessingResult processingResult = stp.readMessage(msg.getEnvelopePayload(), msg.getTransportProtocolType(), msg.getTransportProtocolVersion());

            DecryptedTransportPacket packet = DecryptedTransportPacket.createFrom(processingResult);
            // for msg identification in SipMessage table
            String payloadHash = MessageDigest.generateMD5Hash(msg.getEnvelopePayload());
            packet.transportPacketHash = payloadHash;

            ampDispatcher.receive(packet);

        } catch (Exception e){
            Log.ef(TAG, e, "Error while creating StpSimple message for data [%s]", msg);
            return;
        }
    }

    private void receiveFtransfer(QueuedMessage msg) {
        try {
            ampDispatcher.receiveTransfer(msg);
        } catch (Exception e){
            Log.ef(TAG, e, "Error while creating StpSimple message for data [%s]", msg);
        }
    }

    private boolean checkSupportedStpType(int stpType, int stpVersion){
        List<Integer> supportedStpSimpleVersions = Arrays.asList(PROTOCOLS.STP_SIMPLE_VERSION_3);
        List<Integer> supportedStpSimpleAuthVersions = Arrays.asList(PROTOCOLS.STP_SIMPLE_AUTH_VERSION_2);

        if (stpType == PROTOCOLS.STP_SIMPLE) {
            return supportedStpSimpleVersions.contains(stpVersion);
        } else if (stpType == PROTOCOLS.STP_SIMPLE_AUTH) {
            return supportedStpSimpleAuthVersions.contains(stpVersion);
        } else {
            return false;
        }
    }

    public void setMessageQueueListener(MessageQueueActions messageQueueListener) {
        this.messageQueueListener = messageQueueListener;
    }
}

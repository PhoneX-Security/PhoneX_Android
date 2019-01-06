package net.phonex.service.messaging;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import net.phonex.pub.proto.Messages;
import net.phonex.util.Log;
import net.phonex.util.crypto.CryptoHelper;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Secure transport protocol - authenticated only
 * Created by miroc on 5.10.14.
 */
public final class StpSimpleAuth extends StpBase{
    private static final String TAG = "StpSimpleAuth";

    // current protocol type & version in use
    public static final int PROTOCOL_TYPE = TransportProtocolDispatcher.PROTOCOLS.STP_SIMPLE_AUTH;
//    public static final int PROTOCOL_VERSION = TransportProtocolDispatcher.PROTOCOLS.STP_SIMPLE_AUTH_VERSION_2;

    private static AtomicInteger seed = new AtomicInteger();

    // version that we want to use (the newest one by default)
    protected int protocolVersion = TransportProtocolDispatcher.PROTOCOLS.STP_SIMPLE_AUTH_VERSION_2;

    /**
     * Setup which version we want to use (if not the default one)
     * @param transportProtocolVersion
     */
    @Override
    public void setVersion(Integer transportProtocolVersion) {
        protocolVersion = transportProtocolVersion;
    }

    // construct when sending messages
    public StpSimpleAuth(String sender, PrivateKey pk, X509Certificate remoteCert) {
        super(sender, pk, remoteCert);
    }

    // construct when receiving messages
    public StpSimpleAuth(PrivateKey pk, X509Certificate remoteCert) {
        super(pk, remoteCert);
    }

    /**
     * Use when sending message from upper layer
     * @param payload Amp serialized message
     * @return serialized StpSimpleAuth protobuf message
     * @throws CryptoHelper.CipherException
     */
    public byte[] buildMessage(byte[] payload, String destination, int ampType, int ampVersion) throws CryptoHelper.CipherException {
        Messages.STPSimple.Builder builder = Messages.STPSimple.newBuilder();
        builder.setAmpType(ampType);
        builder.setAmpVersion(ampVersion);
        builder.setDestination(destination);
        builder.setSender(sender);

        int nonce = random.nextInt();
        builder.setRandomNonce(nonce);

        long timestamp = System.currentTimeMillis();
        builder.setMessageSentMiliUTC(timestamp);

        int sequenceNumber = getSequenceNumber();
        builder.setSequenceNumber(sequenceNumber);

        // Store payload
        builder.setPayload(ByteString.copyFrom(payload));

        // Sign plaintext  + user identity
        byte[] signature = createSignature(
                getDataForSigning(destination, sender, sequenceNumber, timestamp, nonce, payload, ampType, ampVersion, PROTOCOL_TYPE, protocolVersion)
        );
        builder.setSignature(ByteString.copyFrom(signature));

        Messages.STPSimple msg = builder.build();
        return msg.toByteArray();
    }

    /**
     * Use when receiving message from lower layer
     * @param serializedStpMessage
     * @return
     */
    public StpProcessingResult readMessage(byte[] serializedStpMessage, int stpType, int stpVersion) throws CryptoHelper.CipherException {
        Messages.STPSimple msg = null;
        try {
            msg = Messages.STPSimple.parseFrom(serializedStpMessage);
        } catch (InvalidProtocolBufferException e) {
            throw new CryptoHelper.CipherException(e);
        }

        StpProcessingResult result = new StpProcessingResult();
        result.ampType = msg.getAmpType();
        result.ampVersion = msg.getAmpVersion();
        result.sendDate = msg.getMessageSentMiliUTC();
        result.nonce = msg.getRandomNonce();
        result.sequenceNumber = msg.getSequenceNumber();
        result.sender = msg.getSender();
        result.destination = msg.getDestination();
        result.payload = msg.getPayload().toByteArray();

        // we take this for granted (no hmac -> propagate as valid hmac)
        result.hmacValid = true;
        result.decryptionValid = true;

        // verify signature
        byte[] dataForVerification = getDataForSigning(
                msg.getDestination(),
                msg.getSender(),
                msg.getSequenceNumber(),
                msg.getMessageSentMiliUTC(),
                msg.getRandomNonce(),
                result.payload,
                msg.getAmpType(),
                msg.getAmpVersion(),
                stpType,
                stpVersion
        );

        boolean signatureValid = verifySignature(dataForVerification, msg.getSignature().toByteArray());
        if (!signatureValid) {
            Log.ef(TAG, "ALERT: Signature of received message is not valid [ %s ]", msg.toString());
        }

        result.signatureValid = signatureValid;

        Log.vf(TAG, "ReadMessage() ProcessingResult [ %s ]", result.toString());
        return result;
    }


    protected byte[] getDataForSigning(String destination, String sender, int sequenceNumber, long timestamp,
                                       int nonce, byte[] messagePayload, int ampType, int ampVersion, int stpType, int stpVersion){
        Messages.STPSimple.Builder builder = Messages.STPSimple.newBuilder();
        builder.setProtocolType(stpType);
        builder.setProtocolVersion(stpVersion);



        builder.setAmpVersion(ampVersion);
        builder.setAmpType(ampType);


        builder.setSequenceNumber(sequenceNumber);
        builder.setMessageSentMiliUTC(timestamp);
        builder.setRandomNonce(nonce);
        builder.setDestination(destination);
        builder.setSender(sender);

        builder.setPayload(ByteString.copyFrom(messagePayload));
        byte[] toSign = builder.build().toByteArray();
        return toSign;
    }

    protected static int getSequenceNumber(){
        return seed.getAndIncrement();
    }


}

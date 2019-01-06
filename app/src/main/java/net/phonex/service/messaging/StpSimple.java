package net.phonex.service.messaging;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import net.phonex.pub.proto.Messages;
import net.phonex.util.Base64;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.crypto.CryptoHelper;

import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Secure transport protocol
 * Created by miroc on 5.10.14.
 */
public class StpSimple extends StpBase{
    private static final String TAG = "StpSimple";

    public static final String AES ="AES/CTR/NoPadding";

    public static final int PROTOCOL_TYPE = TransportProtocolDispatcher.PROTOCOLS.STP_SIMPLE;

    private static AtomicInteger seed = new AtomicInteger();

    // version that we want to use (the newest one by default)
    protected int protocolVersion = TransportProtocolDispatcher.PROTOCOLS.STP_SIMPLE_VERSION_3;

    // construct when sending messages
    public StpSimple(String sender, PrivateKey pk, X509Certificate remoteCert) {
        super(sender, pk, remoteCert);
    }

    // construct when receiving messages
    public StpSimple(PrivateKey pk, X509Certificate remoteCert) {
        super(pk, remoteCert);
    }

    /**
     * Setup which version we want to use (if not the default one)
     * @param transportProtocolVersion
     */
    @Override
    public void setVersion(Integer transportProtocolVersion) {
        protocolVersion = transportProtocolVersion;
    }

    /**
     * Use when sending message from upper layer
     * @param payload Amp serialized message
     * @return serialized StpSimple protobuf message
     * @throws CryptoHelper.CipherException
     */
    public byte[] buildMessage(byte[] payload, String destination, int ampType, int ampVersion) throws CryptoHelper.CipherException {
        Messages.STPSimple.Builder builder = Messages.STPSimple.newBuilder();
        builder.setAmpType(ampType);
        builder.setAmpVersion(ampVersion);
        builder.setDestination(destination);
        builder.setSender(sender);

        int nonce = Integer.valueOf(random.nextInt());
        builder.setRandomNonce(nonce);

        long timestamp = System.currentTimeMillis();
        builder.setMessageSentMiliUTC(timestamp);

        int sequenceNumber = getSequenceNumber();
        builder.setSequenceNumber(sequenceNumber);

        // Generate random AES encryption key
        SecretKey encKey = generateAesEncKey();
        // Generate random HMAC key
        SecretKey macKey = generateHmacKey();
        SymmetricKeys symmetricKeys = new SymmetricKeys(encKey, macKey);

        // Generate initialization vector of  size of one AES block
        // WATCH OUT FOR THE AES MODE - some like CTR are deadly vulnerable to IV reuse
        IvParameterSpec iv = generateIvForAes();
        builder.setIv(ByteString.copyFrom(iv.getIV()));

        // Encrypt payload
        byte[] eSymBlock = encryptSymBlock(encKey, iv, payload);
        builder.setESymBlock(ByteString.copyFrom(eSymBlock));

        // Hybrid encrypt key
        byte[] eAsymBlock = encryptAsymBlock(symmetricKeys);
        builder.setEAsymBlock(ByteString.copyFrom(eAsymBlock));

        // Sign plaintext  + user identity        ;
        byte[] signature = createSignature(
                getDataForSigning(destination, sender, sequenceNumber, timestamp, nonce, iv, symmetricKeys, payload, ampType, ampVersion, PROTOCOL_TYPE, protocolVersion)
        );

//        Log.inf(TAG, "getDataForSigning eAsymBlock [%s]", Base64.encodeBytes(eAsymBlock));
        Log.inf(TAG, "getDataForSigning signature [%s]", Base64.encodeBytes(signature));

        builder.setSignature(ByteString.copyFrom(signature));

        byte[] dataForMac = getDataForMac(destination,
                sender,
                sequenceNumber,
                timestamp,
                nonce,
                iv,
                symmetricKeys,
                eAsymBlock,
                eSymBlock,
                ampType, ampVersion,
                PROTOCOL_TYPE, protocolVersion);
        byte[] mac = mac(dataForMac, macKey);
        builder.setHmac(ByteString.copyFrom(mac));

        Messages.STPSimple msg = builder.build();
        return msg.toByteArray();
    }

    /**
     * Use when receiving message from lower layer
     * @param serializedStpMessage
     * @return
     */
    public StpProcessingResult readMessage(byte[] serializedStpMessage, int stpType, int stpVersion) throws CryptoHelper.CipherException {
        Messages.STPSimple msg;
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

        // Decrypt asym block
        SymmetricKeys symmetricKeys;
        try {
            symmetricKeys = decryptAsymBlock(msg.getEAsymBlock().toByteArray());
            result.decryptionValid = true;
        } catch (CryptoHelper.CipherException e){
            Log.ef(TAG, e, "ALERT: Exception during decryption");
            result.decryptionValid = false;
            return result;
        }

        IvParameterSpec iv = new IvParameterSpec(msg.getIv().toByteArray());

        // verify hmac
        byte[] dataForMac = getDataForMac(msg.getDestination(),
                msg.getSender(),
                msg.getSequenceNumber(),
                msg.getMessageSentMiliUTC(),
                msg.getRandomNonce(),
                iv,
                symmetricKeys,
                msg.getEAsymBlock().toByteArray(),
                msg.getESymBlock().toByteArray(),
                msg.getAmpType(),
                msg.getAmpVersion(),
                stpType,
                stpVersion);

        boolean macValid = verifyMac(dataForMac, msg.getHmac().toByteArray(), symmetricKeys.macKey);
        if (!macValid) {
            Log.ef(TAG, "ALERT: HMAC of received message is not valid [ %s ]", msg.toString());
            return result;
        }
        result.hmacValid = macValid;

        // decrypt symBlock
        byte[] payload = decryptSymBlock(symmetricKeys.encKey, iv, msg.getESymBlock().toByteArray());

        // verify signature
        byte[] dataForVerification = getDataForSigning(msg.getDestination(),
                msg.getSender(),
                msg.getSequenceNumber(),
                msg.getMessageSentMiliUTC(),
                msg.getRandomNonce(),
                iv,
                symmetricKeys,
                payload,
                msg.getAmpType(),
                msg.getAmpVersion(),
                stpType,
                stpVersion);

        Log.inf(TAG, "getDataForSigning dataForVerification [%s]", Base64.encodeBytes(dataForVerification));
        Log.inf(TAG, "getDataForSigning receivedSignature [%s]", Base64.encodeBytes(msg.getSignature().toByteArray()));

        boolean signatureValid = verifySignature(dataForVerification, msg.getSignature().toByteArray());
        if (!signatureValid) {
            Log.ef(TAG, "ALERT: Signature of received message is not valid [ %s ]", msg.toString());
            return result;
        }

        result.signatureValid = signatureValid;
        result.payload = payload;

        Log.vf(TAG, "ReadMessage() ProcessingResult [ %s ]", result.toString());
        return result;
    }

    protected static int getSequenceNumber(){
        return seed.getAndIncrement();
    }

    protected IvParameterSpec generateIvForAes(){
        byte iv[] = new byte[CryptoHelper.AES_BLOCK_SIZE];
        random.nextBytes(iv);
        return new IvParameterSpec(iv);
    }

    // Generate random AES encryption key, 256b
    protected SecretKey generateAesEncKey() throws CryptoHelper.CipherException {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES", BC);
            keyGen.init(CryptoHelper.AES_KEY_SIZE * 8);
            return keyGen.generateKey();
        } catch (Exception e) {
            throw new CryptoHelper.CipherException("Exception during RSA encryption", e);
        }
    }

    // Generate HmacWithSha256 key
    protected SecretKey generateHmacKey() throws CryptoHelper.CipherException {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(CryptoHelper.HMAC, BC);
            keyGen.init(CryptoHelper.HMAC_KEY_SIZE * 8);
            return keyGen.generateKey();
        } catch (Exception e) {
            throw new CryptoHelper.CipherException("Exception during RSA encryption", e);
        }
    }

    // Encrypt the AES and HMAC key with RSA
    protected byte[] encryptAsymBlock(SymmetricKeys symmetricKeys) throws CryptoHelper.CipherException {
        try {
            // initialize block
            Messages.STPSimple.Builder builder = Messages.STPSimple.newBuilder();
            builder.setMacKey(ByteString.copyFrom(symmetricKeys.macKey.getEncoded()));
            builder.setEncKey(ByteString.copyFrom(symmetricKeys.encKey.getEncoded()));
            Messages.STPSimple keysWrapper = builder.build();

            // encrypt
            Cipher rsa = Cipher.getInstance(CryptoHelper.RSA, BC);
            rsa.init(Cipher.ENCRYPT_MODE, remoteCert.getPublicKey(), random);
            byte[] rsaCipherText = rsa.doFinal(keysWrapper.toByteArray());
            return rsaCipherText;
        } catch (Exception e) {
            throw new CryptoHelper.CipherException("Exception during RSA encryption", e);
        }
    }

    protected SymmetricKeys decryptAsymBlock(byte[] encryptedData) throws CryptoHelper.CipherException {
        try {
            // Decrypt encryption keys
            Cipher rsa = Cipher.getInstance(CryptoHelper.RSA, BC);
            rsa.init(Cipher.DECRYPT_MODE, pk, random);
            byte[] decryptedData = rsa.doFinal(encryptedData);

            Messages.STPSimple keysWrapper = Messages.STPSimple.parseFrom(decryptedData);

            SecretKeySpec encKey = new SecretKeySpec(keysWrapper.getEncKey().toByteArray(), "AES");
            SecretKeySpec macKey = new SecretKeySpec(keysWrapper.getMacKey().toByteArray(), CryptoHelper.HMAC);

            return new SymmetricKeys(encKey, macKey);

        } catch (Exception e) {
            Log.ef(TAG, e, "Exception");
            throw new CryptoHelper.CipherException("Exception during RSA encryption", e);
        }
    }

    protected byte[] encryptSymBlock(SecretKey encKey, IvParameterSpec iv, byte[] payload) throws CryptoHelper.CipherException {
        try {
            Cipher aes = Cipher.getInstance(AES, BC);
            aes.init(Cipher.ENCRYPT_MODE, encKey, iv);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ByteArrayInputStream is = new ByteArrayInputStream(payload);

            CryptoHelper.streamedEncryption(is, bos, aes);

            is.close();
            bos.close();
            return bos.toByteArray();

        } catch (Exception ex){
            throw new CryptoHelper.CipherException("Exception during encryption", ex);
        }
    }

    protected byte[] decryptSymBlock(SecretKey encKey, IvParameterSpec iv, byte[] encPayload) throws CryptoHelper.CipherException {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(encPayload);

            Cipher aes = Cipher.getInstance(AES, new BouncyCastleProvider());
            aes.init(Cipher.DECRYPT_MODE, encKey, iv);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();

            CryptoHelper.streamedDecryption(bis, bos, aes);

            MiscUtils.closeSilently("decrypt, bos", bos);
            MiscUtils.closeSilently("decrypt, dis", bis);
            return bos.toByteArray();
        } catch(Exception e){
            throw new CryptoHelper.CipherException("Exception during encryption", e);
        }
    }

    protected byte[] getDataForSigning(String destination, String sender, int sequenceNumber, long timestamp,
                                       int nonce, IvParameterSpec iv, SymmetricKeys symmetricKeys,
                                       byte[] messagePayload, int ampType, int ampVersion, int stpType, int stpVersion){
        Messages.STPSimple.Builder builder = Messages.STPSimple.newBuilder();
        builder.setProtocolType(stpType);
        builder.setProtocolVersion(stpVersion);

        builder.setAmpVersion(ampVersion);
        builder.setAmpType(ampType);

        // version 2+ includes timestamp as well
        builder.setMessageSentMiliUTC(timestamp);

        builder.setSequenceNumber(sequenceNumber);
        builder.setRandomNonce(nonce);
        builder.setDestination(destination);
        builder.setSender(sender);

        builder.setIv(ByteString.copyFrom(iv.getIV()));

        builder.setMacKey(ByteString.copyFrom(symmetricKeys.macKey.getEncoded()));
        builder.setEncKey(ByteString.copyFrom(symmetricKeys.encKey.getEncoded()));
        builder.setPayload(ByteString.copyFrom(messagePayload));
        Messages.STPSimple stpSimple = builder.build();
        byte[] toSign = stpSimple.toByteArray();

        Log.inf(TAG, "getDataForSigning payload [%s]", Base64.encodeBytes(messagePayload));
        Log.inf(TAG, "getDataForSigning macKey [%s]", Base64.encodeBytes(symmetricKeys.macKey.getEncoded()));
        Log.inf(TAG, "getDataForSigning encKey [%s]", Base64.encodeBytes(symmetricKeys.encKey.getEncoded()));

        Log.inf(TAG, "getDataForSigning data [%s]", stpSimple.toString());
        Log.inf(TAG, "getDataForSigning bytesToSign [%s]", Base64.encodeBytes(toSign));

        return toSign;
    }

    protected byte[] getDataForMac(String destination, String sender, int sequenceNumber, long timestamp, int nonce, IvParameterSpec iv,
                                   SymmetricKeys symmetricKeys, byte[] eAsymBlock, byte[] eSymBlock,
                                   int ampType, int ampVersion, int stpType, int stpVersion){
        Messages.STPSimple.Builder builder = Messages.STPSimple.newBuilder();
        builder.setProtocolType(stpType);
        builder.setProtocolVersion(stpVersion);

        builder.setAmpVersion(ampVersion);
        builder.setAmpType(ampType);

        builder.setMessageSentMiliUTC(timestamp);

        builder.setSequenceNumber(sequenceNumber);
        builder.setRandomNonce(nonce);
        builder.setDestination(destination);
        builder.setSender(sender);

        builder.setIv(ByteString.copyFrom(iv.getIV()));

        builder.setMacKey(ByteString.copyFrom(symmetricKeys.macKey.getEncoded()));
        builder.setEncKey(ByteString.copyFrom(symmetricKeys.encKey.getEncoded()));

        builder.setESymBlock(ByteString.copyFrom(eSymBlock));
        builder.setEAsymBlock(ByteString.copyFrom(eAsymBlock));
        Messages.STPSimple build = builder.build();
        return build.toByteArray();
    }

    protected byte[] mac(byte[] data, SecretKey key) throws CryptoHelper.CipherException {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(data);

            byte[] mac = CryptoHelper.hmac(bis, key.getEncoded());
            bis.close();

            return mac;
        } catch(Exception e){
            throw new CryptoHelper.CipherException("Exception during encryption", e);
        }
    }

    protected boolean verifyMac(byte[] data, byte[] mac, SecretKey key) throws CryptoHelper.CipherException {
        byte[] macExpected = mac(data, key);
        return Arrays.equals(macExpected, mac);
    }

    protected static class SymmetricKeys {
        public SymmetricKeys(SecretKey encKey, SecretKey macKey) {
            this.encKey = encKey;
            this.macKey = macKey;
        }

        public SecretKey encKey;
        public SecretKey macKey;

        @Override
        public String toString() {
            String r = "EncKey: " + Arrays.toString(encKey.getEncoded()) + "\n";
            r += "MacKey: " + Arrays.toString(macKey.getEncoded());
            return r;
        }
    }
}

package net.phonex.service.messaging;

import net.phonex.util.MiscUtils;
import net.phonex.util.crypto.CryptoHelper;

import java.io.ByteArrayInputStream;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * Base class for different Stp versions sharing same properties
 * Created by miroc on 1.11.14.
 *
 */
public abstract class StpBase implements Stp{
    public static final String BC = org.spongycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME;

    protected String sender;
    protected final PrivateKey pk;
    protected final X509Certificate remoteCert;

    protected final SecureRandom random;

    // construct when sending messages
    public StpBase(String sender, PrivateKey pk, X509Certificate remoteCert) {
        this.sender = sender;
        this.pk = pk;
        this.remoteCert = remoteCert;
        random = new SecureRandom();
    }

    // construct when receiving messages
    public StpBase(PrivateKey pk, X509Certificate remoteCert) {
        this.pk = pk;
        this.remoteCert = remoteCert;
        random = new SecureRandom();
    }


    protected byte[] createSignature(byte[] dataToSign) throws CryptoHelper.CipherException {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(dataToSign);
            byte[] signature = CryptoHelper.sign(bis, pk, random);

            MiscUtils.closeSilently(bis);
            return signature;
        } catch(Exception e){
            throw new CryptoHelper.CipherException("Exception during encryption", e);
        }
    }

    protected boolean verifySignature(byte[] dataToVerify, byte[] signature) throws CryptoHelper.CipherException {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(dataToVerify);
            boolean isValidSignature = CryptoHelper.verify(bis, signature, remoteCert.getPublicKey());
            MiscUtils.closeSilently(bis);
            return isValidSignature;
        } catch(Exception e){
            throw new CryptoHelper.CipherException("Exception during encryption", e);
        }
    }

    public String getSender() {
        return sender;
    }

    public PrivateKey getPk() {
        return pk;
    }

    public X509Certificate getRemoteCert() {
        return remoteCert;
    }

    public SecureRandom getRandom() {
        return random;
    }

    @Override
    public void setVersion(Integer transportProtocolVersion) {
        // by default does nothing
    }
}

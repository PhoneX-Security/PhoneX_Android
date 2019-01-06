package net.phonex.service.messaging;

import android.content.Context;
import android.test.AndroidTestCase;

import junit.framework.Assert;

import net.phonex.util.Log;

import org.junit.Test;
import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.lang.reflect.Method;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class StpSimpleTest extends AndroidTestCase {
    private final String TAG = "StpSimpleTest";

    private StpSimple stpSimple;

    private StpSimpleAuth stpSimpleAuth;
    private Context testContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Security.addProvider(new BouncyCastleProvider());

        // getTestContext is for some reason public but annotated as @Hide, therefore not accessible
        // using reflection to get it
        try {
            Method m = AndroidTestCase.class.getMethod("getTestContext", new Class[] {});
            testContext = (Context) m.invoke(this, (Object[]) null);
        } catch (Exception x) {
            Log.e(TAG, "Error getting test context: ", x);
            throw x;
        }

        // read keys and certificates
        // "Both sides" of communication uses the same cert + priv key
        X509Certificate certificate = TestUtils.readTestCertificate(testContext);
        PrivateKey privateKey = TestUtils.readTestPrivateKey(testContext);

        stpSimple = new StpSimple("miroc@phone-x.net", privateKey, certificate);
        stpSimpleAuth = new StpSimpleAuth("miroc@phone-x.net", privateKey, certificate);
    }

    @Test
    public void testSymmetricEncryption() throws Exception {
        String message = "magicString";
        byte[] payload = message.getBytes();

        SecretKey secretKey = stpSimple.generateAesEncKey();
        IvParameterSpec iv = stpSimple.generateIvForAes();

        byte[] encryptedPayload = stpSimple.encryptSymBlock(secretKey, iv, payload);
        Log.ef(TAG, "encryptedPayload.length %d", encryptedPayload.length );

        byte[] decryptedPayload = stpSimple.decryptSymBlock(secretKey, iv, encryptedPayload);

        String reconstructedMessage = new String(decryptedPayload);
        Assert.assertEquals(message, reconstructedMessage);
        Assert.assertFalse(message.equals(reconstructedMessage + "X"));
    }

    @Test
    public void testAsymmetricBlockEncryption() throws Exception {
        SecretKey macKey = stpSimple.generateHmacKey();
        SecretKey encKey = stpSimple.generateAesEncKey();

        byte[] bytes = stpSimple.encryptAsymBlock(new StpSimple.SymmetricKeys(encKey, macKey));
        StpSimple.SymmetricKeys decryptedBlock = stpSimple.decryptAsymBlock(bytes);

        SecretKey decryptedEncKey = decryptedBlock.encKey;
        SecretKey decryptedMacKey = decryptedBlock.macKey;

        Assert.assertEquals(macKey, decryptedMacKey);
        Assert.assertEquals(encKey, decryptedEncKey);
    }

    @Test
    public void testSignature() throws Exception {

        int sn = stpSimple.getSequenceNumber();
        int nonce = 1;
        IvParameterSpec iv = stpSimple.generateIvForAes();
        SecretKey encKey = stpSimple.generateAesEncKey();
        SecretKey hmacKey = stpSimple.generateHmacKey();
        StpSimple.SymmetricKeys keys = new StpSimple.SymmetricKeys(encKey, hmacKey);
        String payload = "magicPayload";
        String destination = "dusan@phone-x.net";
        String sender = "miro@phone-x.net";

        byte[] toSignAndVerify = stpSimple.getDataForSigning(destination, sender, sn, 0, nonce, iv, keys, payload.getBytes(), 1, 1, StpSimple.PROTOCOL_TYPE, stpSimple.protocolVersion);

        byte[] differentData = stpSimple.getDataForSigning(destination, sender, sn, 0, nonce, iv, keys, (payload + "!!!").getBytes(), 1, 1, StpSimple.PROTOCOL_TYPE, stpSimple.protocolVersion);

        byte[] signature = stpSimple.createSignature(toSignAndVerify);

        Assert.assertTrue(stpSimple.verifySignature(toSignAndVerify, signature));
        Assert.assertFalse(stpSimple.verifySignature(differentData, signature));
    }

    @Test
    public void testMac() throws Exception {

        String destination = "dusan@phone-x.net";
        String sender = "miro@phone-x.net";

        int sn = stpSimple.getSequenceNumber();
        int nonce = 1;
        IvParameterSpec iv = stpSimple.generateIvForAes();
        SecretKey encKey = stpSimple.generateAesEncKey();
        SecretKey hmacKey = stpSimple.generateHmacKey();
        StpSimple.SymmetricKeys keys = new StpSimple.SymmetricKeys(encKey, hmacKey);

        byte[] eAsymBlock = stpSimple.encryptAsymBlock(keys);
        byte[] eSymBlock = stpSimple.encryptSymBlock(encKey, iv, "Zlatinko".getBytes());
        byte[] eSymBlock2 = stpSimple.encryptSymBlock(encKey, iv, "Evi".getBytes());

        byte[] dataForMac = stpSimple.getDataForMac(destination, sender, sn, 0, nonce, iv, keys, eAsymBlock, eSymBlock, 1, 1, StpSimple.PROTOCOL_TYPE, stpSimple.protocolVersion);
        byte[] dataForMac2 = stpSimple.getDataForMac(destination, sender, sn, 0, nonce, iv, keys, eAsymBlock, eSymBlock2, 1, 1, StpSimple.PROTOCOL_TYPE, stpSimple.protocolVersion);

        byte[] mac = stpSimple.mac(dataForMac, hmacKey);
        Assert.assertTrue(stpSimple.verifyMac(dataForMac, mac, hmacKey));
        Assert.assertFalse(stpSimple.verifyMac(dataForMac2, mac, hmacKey));
    }


    @Test
    public void testAll() throws Exception {
        String myMsg = "Ahoj svete";
        String destination = "dusan@phone-x.net";

        int nonce = 12345;
        byte[] bytes = stpSimple.buildMessage(AmpSimple.buildSerializedMessage(myMsg, nonce), destination, 1 ,1);

        StpProcessingResult processingResult = stpSimple.readMessage(bytes, StpSimple.PROTOCOL_TYPE, stpSimple.protocolVersion);

        Assert.assertTrue(processingResult.hmacValid);
        Assert.assertTrue(processingResult.signatureValid);

        Log.inf(TAG, "Obj [%s]", processingResult);

        AmpSimple ampSimple = AmpSimple.loadMessage(processingResult.payload);
        Assert.assertEquals(myMsg, ampSimple.getMessage());
    }

    @Test
    public void testStpSimpleAuth() throws Exception {
        String myMsg = "This message is only authenticated, but it's no secret";
        String destination = "dusan@phone-x.net";

        int nonce = 12345;
        byte[] bytes = stpSimpleAuth.buildMessage(AmpSimple.buildSerializedMessage(myMsg, nonce), destination, 1 ,1);

        StpProcessingResult processingResult = stpSimpleAuth.readMessage(bytes, StpSimpleAuth.PROTOCOL_TYPE, stpSimpleAuth.protocolVersion);
        StpProcessingResult processingResult_bad = stpSimpleAuth.readMessage(bytes, StpSimpleAuth.PROTOCOL_TYPE, stpSimpleAuth.protocolVersion+1);//wrong protocol version passed

        // check for signature only
        Assert.assertTrue(processingResult.signatureValid);
        // should be invalid signature
        Assert.assertFalse(processingResult_bad.signatureValid);

        Log.inf(TAG, "Obj [%s]", processingResult);

        AmpSimple ampSimple = AmpSimple.loadMessage(processingResult.payload);
        Assert.assertEquals(myMsg, ampSimple.getMessage());
    }
}

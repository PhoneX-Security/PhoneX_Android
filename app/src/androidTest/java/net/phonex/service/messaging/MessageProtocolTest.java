package net.phonex.service.messaging;

import android.content.Context;
import android.test.AndroidTestCase;

import net.phonex.util.Log;

import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.lang.reflect.Method;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;

/**
 * Created by miroc on 10.10.14.
 */
public class MessageProtocolTest extends AndroidTestCase {
    private final String TAG = "MessageProtocolTest";

    private Context testContext;
    private X509Certificate certificate;
    private PrivateKey privateKey;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Security.addProvider(new BouncyCastleProvider());

        // getTestContext is for some reason public but annotated as @Hide, therefore not accessible
        // using reflection to get it
        try {
            Method m = AndroidTestCase.class.getMethod("getTestContext", new Class[]{});
            testContext = (Context) m.invoke(this, (Object[]) null);
        } catch (Exception x) {
            Log.e(TAG, "Error getting test context: ", x);
            throw x;
        }

        certificate = TestUtils.readTestCertificate(testContext);
        privateKey = TestUtils.readTestPrivateKey(testContext);
    }

//    @Test
//    public void testSipMessageProtocol() throws Exception {
//        String text = "Otcova rola";
//        String source = "miro";
//        String dest = "dusan";
//
//        String sipMessage = MessageProtocol.createSipMessage(text, source, dest, privateKey, certificate);
//        UserMessageOutput output = MessageProtocol.readSipMessage(sipMessage, privateKey, certificate);
//        Assert.assertEquals(text, output.getTextPart());
//    }
}

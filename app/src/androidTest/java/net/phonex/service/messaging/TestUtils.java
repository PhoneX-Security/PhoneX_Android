package net.phonex.service.messaging;

import android.content.Context;

import org.spongycastle.openssl.PEMReader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * Created by miroc on 9.10.14.
 */
public class TestUtils {
    private static final String TAG = "TestUtils";

    // read test X509 certificate containing self signed RSA 2048 public key
    public static X509Certificate readTestCertificate(Context ctx) throws Exception{
        InputStream is = ctx.getAssets().open("keys/certificate.pem");

        PEMReader pr = new PEMReader(new InputStreamReader(is));
        X509Certificate certificate = (X509Certificate) pr.readObject();

        pr.close();
        is.close();
        return certificate;
    }

    public static PrivateKey readTestPrivateKey(Context ctx) throws Exception{
        InputStream is = ctx.getAssets().open("keys/privKey.pem");

        PEMReader pr = new PEMReader(new InputStreamReader(is));

        PrivateKey key = (PrivateKey) pr.readObject();
        pr.close();
        is.close();
        return key;
    }


}

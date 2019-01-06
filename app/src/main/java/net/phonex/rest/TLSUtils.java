package net.phonex.rest;

import android.content.Context;
import android.text.TextUtils;

import net.phonex.core.MemoryPrefManager;
import net.phonex.pub.parcels.StoredCredentials;
import net.phonex.soap.SSLSOAP;
import net.phonex.soap.ServiceConstants;
import net.phonex.util.Log;
import net.phonex.util.crypto.CertificatesAndKeys;
import net.phonex.util.crypto.KeyPairGenerator;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLContext;

import retrofit.RestAdapter;

/**
 * All REST api should be retrieved here
 * Created by miroc on 12.5.15.
 */
public class TLSUtils {
    private static final String TAG = "TLSUtils";

    public static SSLContext prepareSSLContext(Context context) throws UnrecoverableKeyException, CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        KeyStore keyStore = SSLSOAP.loadWebTrustStore(context);
        SSLContext sslContext1 = SSLSOAP.getSSLContext(keyStore, null, context, new SecureRandom());
        SSLSOAP.setHostnameVerifier();
        return sslContext1;
    }

    /**
     * License server API with server and client authentication over TLS
     * @param context
     * @return
     * @throws UnrecoverableKeyException
     * @throws CertificateException
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
     * @throws IOException
     */
    public static LicenseServerAuthApi prepareLicServerApi(Context context) throws UnrecoverableKeyException, CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        StoredCredentials creds = MemoryPrefManager.loadCredentials(context);
        if (creds==null || TextUtils.isEmpty(creds.getUserSip()) || TextUtils.isEmpty(creds.getUsrStoragePass())) {
            throw new IllegalArgumentException("No stored credentials");
        }
        // Generate SSL context
        KeyPairGenerator kpg = new KeyPairGenerator();
        KeyStore ks = kpg.readKeyStore(CertificatesAndKeys.derivePkcs12Filename(creds.getUserSip()), context, creds.getUsrStoragePass().toCharArray());
        SSLContext sslContext = SSLSOAP.getSSLContext(ks, creds.getUsrStoragePass().toCharArray(), context, new SecureRandom());

        RestAdapter restAdapter = new RestAdapter.Builder()
                .setEndpoint("https://system.phone-x.net:1443")
                .setClient(new PhonexCertUrlConnectionClient(sslContext.getSocketFactory()))
                .build();

        return restAdapter.create(LicenseServerAuthApi.class);
    }

    /**
     * Web server API with server authentication over TLS
     * @return
     * @throws UnrecoverableKeyException
     * @throws CertificateException
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
     * @throws IOException
     */
    public static WebServerApi prepareWebServerApi() throws UnrecoverableKeyException, CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        RestAdapter restAdapter = new RestAdapter.Builder()
                .setEndpoint("https://www.phone-x.net")
                .build();

        return restAdapter.create(WebServerApi.class);
    }

    /**
     * Key (SOAP) server with server side authentication over TLS
     * @param context
     * @return
     * @throws UnrecoverableKeyException
     * @throws CertificateException
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
     * @throws IOException
     */
    public static KeyServerApi prepareKeyServerApi(Context context) throws UnrecoverableKeyException, CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        SSLContext sslContext = TLSUtils.prepareSSLContext(context);

        String defaultRESTURL = ServiceConstants.getKeyServerRESTUrl(false);
        Log.inf(TAG, "prepareKeyServerApi; REST url = %s", defaultRESTURL);

        RestAdapter restAdapter = new RestAdapter.Builder()
                .setEndpoint(defaultRESTURL)
                .setClient(new PhonexCertUrlConnectionClient(sslContext.getSocketFactory()))
                .build();
        return restAdapter.create(KeyServerApi.class);
    }
}

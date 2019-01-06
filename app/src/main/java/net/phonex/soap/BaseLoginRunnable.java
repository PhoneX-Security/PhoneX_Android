package net.phonex.soap;

import android.content.Context;

import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.ksoap2.transport.HttpTransportSE;
import net.phonex.util.Log;
import net.phonex.util.crypto.CertificatesAndKeys;
import net.phonex.util.crypto.KeyPairGenerator;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Matus on 11-Sep-15.
 */
public abstract class BaseLoginRunnable implements Runnable {

    private static final String TAG = "BaseLoginRunnable";

    protected Context context;

    private AtomicBoolean cancelled = new AtomicBoolean(false);

    protected synchronized void cancel() {
        cancelled.set(true);
    }

    protected synchronized boolean isCancelled() {
        return cancelled.get();
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    /**
     * Prepares SOAP channel
     * @throws IOException
     * @throws CertificateException
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
     * @throws UnrecoverableKeyException
     */
    protected SSLSOAP.SSLContextHolder prepareSoap() throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
        return prepareSoap(null, null);
    }

    /**
     * Prepares SOAP channel
     * @throws IOException
     * @throws CertificateException
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
     * @throws UnrecoverableKeyException
     */
    protected SSLSOAP.SSLContextHolder prepareSoap(KeyStore ks, char[] pass) throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
        /**
         * Preparing phase - initialize SSL connections
         *
         * Install HTTPS support with client credentials and trust verifier
         */
        return TaskSOAPHelper.prepareHTTPS(context, ks, pass);
    }
}

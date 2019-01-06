package net.phonex.soap;

import android.content.Context;

import net.phonex.db.entity.SipProfile;
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

/**
 * SOAP methods extracted from BaseAsyncTask.
 *
 * Motivation was duplicate code, when some login related tasks were changed
 * to extend BaseLoginRunnable instead of BaseAsyncTask.
 */
public class TaskSOAPHelper {
    /**
     * Simple debug call to SOAP service
     * static because it can be called from other tasks not extending BaseAsyncTask
     *
     * @param soapEnvelope
     * @param destination
     * @param action
     * @param returnBody
     * @return
     */
    public static Object simpleSOAPRequest(SoapSerializationEnvelope soapEnvelope, String destination, String action, boolean returnBody) throws Exception {
        return simpleSOAPRequest(soapEnvelope, destination, action, returnBody, null);
    }

    /**
     * Simple debug call to SOAP service
     * static because it can be called from other tasks not extending BaseAsyncTask
     *
     * @param soapEnvelope
     * @param destination
     * @param action
     * @param returnBody
     * @param sslHolder    SSL holder to be used with HTTPS connections.
     * @return
     */
    public static Object simpleSOAPRequest(SoapSerializationEnvelope soapEnvelope, String destination, String action, boolean returnBody, SSLSOAP.SSLContextHolder sslHolder) throws Exception {
        // For portability, we use generic HttpTransport here.
        // In future we may use TLS tunnel as a proxy and the use just plain HTTP connection,
        // thus this is more generic.
        // If HTTPS scheme is passed to the destination, openConnection() returns HttpsURLConnection
        // which uses default hostname verifier and default SSL socket factory.
        HttpTransportSE androidHttpTransport = null;
        androidHttpTransport = new HttpTransportSE(destination);
        androidHttpTransport.debug = true;

        try {
            if (sslHolder != null && sslHolder.hostVerifier != null && sslHolder.sslSocketFactory != null) {
                androidHttpTransport.setHostnameVerifier(sslHolder.hostVerifier);
                androidHttpTransport.setSslSocketFactory(sslHolder.sslSocketFactory);
                Log.v(BaseAsyncTask.TAG, "Custom hostname verifier & ssl socket factory set");
            }

            androidHttpTransport.call(action, soapEnvelope);
            Log.df(BaseAsyncTask.TAG, "ReqDump: %s", androidHttpTransport.requestDump);
            Log.df(BaseAsyncTask.TAG, "RespDump: %s", androidHttpTransport.responseDump);
            // first try - inspect response, but this does not contain umarshalled class, it is just
            // vector of strings.
            Object resp = soapEnvelope.getResponse();
            if (!returnBody) return resp;

            // touch this envelope in, should contain unmarshaled object (if there is mapping provided),
            // otherwise it should contain SoapObject/SoapPrimitive
            return soapEnvelope.bodyIn;
        } catch (Exception e) {
            Log.ef(BaseAsyncTask.TAG, "ReqDump: %s", androidHttpTransport.requestDump);
            Log.ef(BaseAsyncTask.TAG, "RespDump: %s", androidHttpTransport.responseDump);
            throw e;
        }
    }

    /**
     * Prepares HTTPS connection with custom keystore and custom TrustVerifier.
     * Uses default keystore path: CertificatesAndKeys.PKCS12Container
     *
     * @param context
     * @param pass
     * @throws KeyStoreException
     * @throws NoSuchAlgorithmException
     * @throws CertificateException
     * @throws IOException
     * @throws UnrecoverableKeyException
     */
    public static SSLSOAP.SSLContextHolder prepareSoapDefault(Context context, char[] pass) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, UnrecoverableKeyException {
        KeyStore ks = getDefaultKeyStore(context, pass);
        return prepareHTTPS(context, ks, pass);
    }

    /**
     * Returns default KeyStore used by SOAP methods.
     *
     * @param context
     * @return
     * @throws IOException
     * @throws CertificateException
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
     */
    public static KeyStore getDefaultKeyStore(Context context, char[] pass) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
        KeyPairGenerator kpg = new KeyPairGenerator();
        SipProfile currentProfile = SipProfile.getCurrentProfile(context);
        KeyStore ks = kpg.readKeyStore(CertificatesAndKeys.derivePkcs12Filename(currentProfile.getSip()), context, pass);
        return ks;
    }

    /**
     * Prepares HTTPS connection to be made, using custom keystore.
     *
     * @param ctxt
     * @param ks
     * @param pass
     * @throws IOException
     * @throws CertificateException
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
     * @throws UnrecoverableKeyException
     */
    public static SSLSOAP.SSLContextHolder prepareHTTPS(Context ctxt, KeyStore ks, char[] pass) throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
        /**
         * Preparing phase - initialize SSL connections
         *
         * Install HTTPS support with client credentials and trust verifier
         */
        try {
            final SSLSOAP.SSLContextHolder sslContextHolder = SSLSOAP.installTrustManager4HTTPS(ks, pass, ctxt);
            Log.v(BaseAsyncTask.TAG, "Initialized default ssl socket factory - with client certificate");

            return sslContextHolder;
        } catch (FileNotFoundException e) {
            Log.e(BaseAsyncTask.TAG, "Could not find file with certificate");
        }

        return null;
    }
}
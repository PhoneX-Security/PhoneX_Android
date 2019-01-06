package net.phonex.soap;

import android.content.Context;

import net.phonex.core.MemoryPrefManager;
import net.phonex.core.SipUri;
import net.phonex.db.entity.SipProfile;
import net.phonex.ksoap2.SoapEnvelope;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.ksoap2.transport.HttpTransportSE;
import net.phonex.pub.parcels.StoredCredentials;
import net.phonex.util.Log;
import net.phonex.util.crypto.CertificatesAndKeys;
import net.phonex.util.crypto.KeyPairGenerator;
import net.phonex.util.crypto.PRNGFixes;

import org.xmlpull.v1.XmlPullParserException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

/**
 * Class for performing SOAP actions.
 * @author ph4r05
 *
 */
public class SOAPHelper {
	protected static String TAG = "SOAPHelper";
    protected SSLSOAP.SSLContextHolder sslContextHolder = null;
    protected Context ctxt;
	protected SecureRandom rand;

	private String lastDestinationUrl = null;
	
	/**
	 * SOAP socket timeout.
	 */
	protected int timeout = 15000;
	
	/**
	 * Debugging logs?
	 */
	protected boolean debug = true;
	
	public SOAPHelper() {
		
	}
	
	public SOAPHelper(Context ctxt) {
		this.ctxt = ctxt;
	}
	
	/**
	 * Initializes soap for calls.
	 * 
	 * @param storagePassword
	 * @throws UnrecoverableKeyException
	 * @throws KeyStoreException
	 * @throws NoSuchAlgorithmException
	 * @throws CertificateException
	 * @throws IOException
	 */
	public void initSoap(char[] storagePassword) throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
		/**
		 * Init SecureRandom, it may take some time, thus it is initialized in
		 * background task. It is also needed prior first SSL connection
		 * or key generation.
		 */
		PRNGFixes.apply();
		rand = new SecureRandom();
		prepareSoapDefault(storagePassword);
	}

	public void init() throws UnrecoverableKeyException, CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
		StoredCredentials creds = MemoryPrefManager.loadCredentials(ctxt);
		lastDestinationUrl = ServiceConstants.getDefaultURL(creds.retrieveDomain());
		initSoap(creds.getUsrStoragePass());
	}

	public void initWithoutClientAuth(String userSip) throws UnrecoverableKeyException, CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
		lastDestinationUrl = ServiceConstants.getServiceURL(SipUri.getCanonicalSipContact(userSip, true), false) + ServiceConstants.getSOAPServiceEndpoint(ctxt);
		Log.df(TAG, "Using service url [%s]", lastDestinationUrl);
		PRNGFixes.apply();
		rand = new SecureRandom();
		//prepareSoap(null, null);
		prepareHTTPS(ctxt, null, null);
	}

	/**
	 * Initializes soap for calls.
	 * 
	 * @param storagePassword
	 * @throws UnrecoverableKeyException
	 * @throws KeyStoreException
	 * @throws NoSuchAlgorithmException
	 * @throws CertificateException
	 * @throws IOException
	 */
	public void initSoap(String storagePassword) throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
		initSoap(storagePassword == null ? null : storagePassword.toCharArray());
	}

    public SSLSOAP.SSLContextHolder getSslContextHolder() {
        return sslContextHolder;
    }

    /**
	 * Simple debug call to SOAP service
	 * static because it can be called from other tasks not extending BaseAsyncTask
	 * @param soapEnvelope
	 * @param destination
	 * @param action
	 * @param returnBody
	 * @return
	 */
    public static Object simpleSOAPRequest(SoapSerializationEnvelope soapEnvelope, String destination, String action, boolean returnBody) throws IOException{
        return simpleSOAPRequest(soapEnvelope, destination, action, returnBody, null);
    }

	public static Object simpleSOAPRequest(SoapSerializationEnvelope soapEnvelope, String destination, String action, boolean returnBody, SSLSOAP.SSLContextHolder sslHolder) throws IOException {
		HttpTransportSE androidHttpTransport = null;
	    androidHttpTransport = new HttpTransportSE(destination);
	    androidHttpTransport.debug = true;
       
	    try {
			if (sslHolder != null && sslHolder.hostVerifier != null && sslHolder.sslSocketFactory != null) {
				androidHttpTransport.setHostnameVerifier(sslHolder.hostVerifier);
				androidHttpTransport.setSslSocketFactory(sslHolder.sslSocketFactory);
				Log.v(TAG, "Custom hostname verifier & ssl socket factory set");
			}

			androidHttpTransport.call(action, soapEnvelope);
			Log.df(TAG, "ReqDump: %s", androidHttpTransport.requestDump);
			Log.df(TAG, "RespDump: %s", androidHttpTransport.responseDump);
			// first try - inspect response, but this does not contain umarshalled class, it is just
			// vector of strings.
			Object resp = soapEnvelope.getResponse();
			if (!returnBody) return resp;

			// touch this envelope in, should contain unmarshaled object (if there is mapping provided),
			// otherwise it should contain SoapObject/SoapPrimitive
			return soapEnvelope.bodyIn;
		} catch(XmlPullParserException pullParserException){
			Log.ef(TAG, "ReqDump: %s", androidHttpTransport.requestDump);
			Log.ef(TAG, "RespDump: %s", androidHttpTransport.responseDump);
			throw new SOAPParserException(pullParserException);

	    } catch(Throwable e){
	    	Log.ef(TAG, "ReqDump: %s", androidHttpTransport.requestDump);
	        Log.ef(TAG, "RespDump: %s", androidHttpTransport.responseDump);
	        throw e;
	    }
	}
	
	/**
	 * Simple debug call to SOAP service
	 * static because it can be called from other tasks not extending BaseAsyncTask.
	 * 
	 * Local variant of the method, enables to specify timeout.
	 * 
	 * @param soapEnvelope
	 * @param destination
	 * @param action
	 * @param returnBody
	 * @return
	 * @throws Exception
	 */
	public Object simpleSOAPRequestEx(SoapSerializationEnvelope soapEnvelope, String destination, String action, boolean returnBody) throws IOException {
		HttpTransportSE androidHttpTransport = null;
	    androidHttpTransport = new HttpTransportSE(destination, timeout);
	    androidHttpTransport.debug = debug;
       
	    try {
	        androidHttpTransport.call(action, soapEnvelope);
	        if (debug){
	        	Log.df(TAG, "ReqDump: %s", androidHttpTransport.requestDump);
	        	Log.df(TAG, "RespDump: %s", androidHttpTransport.responseDump);
	        }
	        
	        // first try - inspect response, but this does not contain umarshalled class, it is just
	        // vector of strings.
	        Object resp = soapEnvelope.getResponse();
	        if (!returnBody) return resp;
	        
	        // touch this envelope in, should contain unmarshaled object (if there is mapping provided),
	        // otherwise it should contain SoapObject/SoapPrimitive
	        return soapEnvelope.bodyIn;

		} catch(XmlPullParserException pullParserException){
			Log.ef(TAG, "ReqDump: %s", androidHttpTransport.requestDump);
			Log.ef(TAG, "RespDump: %s", androidHttpTransport.responseDump);
			throw new SOAPParserException(pullParserException);

	    } catch(Throwable e){
	    	Log.ef(TAG, "ReqDump: %s", androidHttpTransport.requestDump);
	        Log.ef(TAG, "RespDump: %s", androidHttpTransport.responseDump);
	        throw e;
	    }
	}

	public Object makeSoapRequest(SoapSerializationEnvelope soapEnvelope, String action) throws SOAPException {
		return makeSoapRequest(soapEnvelope, lastDestinationUrl, action, true);
	}

	public Object makeSoapRequest(SoapSerializationEnvelope soapEnvelope, String destination, String action, boolean returnBody) throws SOAPException {
		HttpTransportSE androidHttpTransport = null;
		androidHttpTransport = new HttpTransportSE(destination, timeout);
		androidHttpTransport.debug = debug;

		try {
			androidHttpTransport.call(action, soapEnvelope);
			if (debug){
				Log.df(TAG, "ReqDump: %s", androidHttpTransport.requestDump);
				Log.df(TAG, "RespDump: %s", androidHttpTransport.responseDump);
			}

			// first try - inspect response, but this does not contain umarshalled class, it is just
			// vector of strings.
			Object resp = soapEnvelope.getResponse();
			if (!returnBody) return resp;

			// touch this envelope in, should contain unmarshaled object (if there is mapping provided),
			// otherwise it should contain SoapObject/SoapPrimitive
			return soapEnvelope.bodyIn;
		} catch(Exception e){
			Log.ef(TAG, "ReqDump: %s", androidHttpTransport.requestDump);
			Log.ef(TAG, "RespDump: %s", androidHttpTransport.responseDump);
			throw new SOAPException(e);
		}
	}

	/**
	 * Register request and response and create SOAP envelope
	 * @param request
	 * @param responseClass
	 * @return
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 */
	public SoapSerializationEnvelope reqisterRequest(SoapEnvelopeRegisterable request, Class<? extends SoapEnvelopeRegisterable> responseClass) throws IllegalAccessException, InstantiationException {
		SoapSerializationEnvelope soapEnvelope = new SoapSerializationEnvelope(SoapEnvelope.VER11);
		request.register(soapEnvelope);
		SoapEnvelopeRegisterable response = responseClass.newInstance();
		response.register(soapEnvelope);
		soapEnvelope.setOutputSoapObject(request);
		return soapEnvelope;
	}
	
	/**
	 * Reads default key store in default container defined by CertificatesAndKeys.PKCS12Container
	 * 
	 * @param pass
	 * @throws KeyStoreException
	 * @throws NoSuchAlgorithmException
	 * @throws CertificateException
	 * @throws IOException
	 * @throws UnrecoverableKeyException
	 */
	protected void prepareSoapDefault(char[] pass) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, UnrecoverableKeyException{
		KeyPairGenerator kpg = new KeyPairGenerator();
		SipProfile currentProfile = SipProfile.getCurrentProfile(ctxt);
		KeyStore ks = kpg.readKeyStore(CertificatesAndKeys.derivePkcs12Filename(currentProfile.getSip()), ctxt, pass);
    	this.prepareSoap(ks, pass);
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
	public static KeyStore getDefaultKeyStore(Context context, char[] pass) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
		KeyPairGenerator kpg = new KeyPairGenerator();
		SipProfile currentProfile = SipProfile.getCurrentProfile(context);
		KeyStore ks = kpg.readKeyStore(CertificatesAndKeys.derivePkcs12Filename(currentProfile.getSip()), context, pass);
    	return ks;
	}
	
	/**
	 * Prepares SOAP channel 
	 * @throws IOException 
	 * @throws CertificateException 
	 * @throws NoSuchAlgorithmException 
	 * @throws KeyStoreException 
	 * @throws UnrecoverableKeyException 
	 */
	protected void prepareSoap(KeyStore ks, char[] pass) throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
		/**
		 * Preparing phase - initialize SSL connections
		 * 
		 * Install HTTPS support with client credentials and trust verifier
		 */
		prepareHTTPS(ctxt, ks, pass);
	}
	
	/**
	 * Prepares HTTPS connection to be made, using custom keystore.
	 * @param ctxt
	 * @param ks
	 * @param pass
	 * @throws IOException 
	 * @throws CertificateException 
	 * @throws NoSuchAlgorithmException 
	 * @throws KeyStoreException 
	 * @throws UnrecoverableKeyException 
	 */
	public void prepareHTTPS(Context ctxt, KeyStore ks, char[] pass) throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
		/**
		 * Preparing phase - initialize SSL connections
		 * 
		 * Install HTTPS support with client credentials and trust verifier
		 */
		try {
            sslContextHolder = SSLSOAP.installTrustManager4HTTPS(ks, pass, ctxt);
            Log.v(TAG, "Initialized default ssl socket factory - with client certificate");
		}catch(FileNotFoundException e){
			Log.e(TAG, "Could not find file with certificate");
		}
	}
	
	/**
	 * @return the ctxt
	 */
	public Context getCtxt() {
		return ctxt;
	}

	/**
	 * @param ctxt the ctxt to set
	 */
	public void setCtxt(Context ctxt) {
		this.ctxt = ctxt;
	}
	
	/**
	 * @return the rand
	 */
	public SecureRandom getRand() {
		return rand;
	}
	
	public int getTimeout() {
		return timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public boolean isDebug() {
		return debug;
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}
}

package net.phonex.soap;

import android.content.Intent;

import net.phonex.PhonexSettings;
import net.phonex.R;
import net.phonex.core.Intents;
import net.phonex.ksoap2.SoapEnvelope;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.pref.PhonexConfig;
import net.phonex.pref.PreferencesConnector;
import net.phonex.pref.PreferencesManager;
import net.phonex.pub.parcels.GenericError;
import net.phonex.pub.parcels.GenericTaskProgress;
import net.phonex.soap.SSLSOAP.SSLContextHolder;
import net.phonex.soap.entities.SignCertificateV2Request;
import net.phonex.soap.entities.SignCertificateV2Response;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.crypto.AESCipher;
import net.phonex.util.crypto.CertificatesAndKeys;
import net.phonex.util.crypto.CertificatesAndKeys.IdentityLoader;
import net.phonex.util.crypto.CertificatesAndKeys.KeyStoreHelper;
import net.phonex.util.crypto.CertificatesAndKeys.PemPasswordSchemeV2;
import net.phonex.util.crypto.CertificatesAndKeys.PkcsPasswordSchemeV2;
import net.phonex.util.crypto.KeyPairGenerator;
import net.phonex.util.crypto.PRNGFixes;
import net.phonex.util.crypto.pki.TrustVerifier;

import org.spongycastle.cert.X509CertificateHolder;
import org.spongycastle.cert.jcajce.JcaX509CertificateConverter;

import java.io.File;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;

/**
 * Task generating key pair, sending certificate signing request for signing.
 * 
 * @author ph4r05
 * docs: http://developer.android.com/reference/android/os/AsyncTask.html
 */
public class CertGenTask extends BaseLoginRunnable implements LoginCancelledBroadcastReceiver.LoginCancelledListener {
	public final static String TAG = "CertGenTask";		

	public CertGenTask(OnCertGenCompleted listener, CertGenParams params){
		onCompletedListener = listener;
		this.par = params;
	}

	@Override
	public void run() {
		init();
		Exception result = runInternal();
		if (!isCancelled()) {
			onPostExecute(result);
		}
	}

	public interface OnCertGenCompleted{
		void onCertGenCompleted(boolean success, Object result, Object error);
	}
	
	private OnCertGenCompleted onCompletedListener;
	private CertGenParams par;

	private LoginCancelledBroadcastReceiver receiver;

	@Override
	public void loginCancelled() {
		cancel();
	}

	protected Exception runInternal() {
		try {
			/**
			 * Init SecureRandom, it may take some time, thus it is initialized in
			 * background task. It is also needed prior first SSL connection
			 * or key generation.
			 */
			PRNGFixes.apply();
			SecureRandom rand = new SecureRandom();
			Log.vf(TAG, "SecureRand initialized, next int=%s", rand.nextInt());
			
			//Preparing phase - initialize SSL connections
			SSLContextHolder sslContextHolder = this.prepareSoap();
			/**
			* Key pair phase
		 	*/
			
			KeyPairGenerator kpg = new KeyPairGenerator();
			// key pair generator to generate key pair and CertificateSigningRequest
			// generate certificate signing request
			Log.i(TAG, "Generating key pair");
			publishProgress(getContext().getString(R.string.p_generating_keypair));
		
			kpg.generateKeyPair();
			if (this.isCancelled()){
				return null;
			}
		
			Log.i(TAG, "Generating CSR");
			publishProgress("Generating CSR");
			
			kpg.generateCSR(par.getUserSIP());
			if (this.isCancelled()){
				return null;
			} 
			
    		//one time token
			Log.i(TAG, "Obtaining server token");
     	    publishProgress(getContext().getString(R.string.p_getting_token));
     	
			OneTimeToken ott = new OneTimeToken();
			ott.setType(1);
			ott.setUser(par.getUserSIP());
    		ott.setServiceEndpoint(par.getServiceURL() + ServiceConstants.getSOAPServiceEndpoint(context));
			ott.generateUserToken();
			String serverToken = ott.callGetOneTimeToken();			
		
			Log.inf(TAG, "Tokens: ServerToken; %s; UserToken: %s", serverToken, ott.getUserToken());
			
			// request signing of certificate
			String HA1 = AuthHashGenerator.getHA1(par.getUserSIP(), par.getUserPass());
			Log.inf(TAG, "HA1: %s", HA1);
			
			String authHash = AuthHashGenerator.generateUserAuthToken(par.getUserSIP(), HA1, 
					ott.getUserToken(), ott.getOneTimeToken(), 
					1000 * 60 * 10, 0);
			String encKey = AuthHashGenerator.generateUserEncToken(par.getUserSIP(), HA1, 
					ott.getUserToken(), ott.getOneTimeToken(), 
					1000 * 60 * 10, 0);
			Log.inf(TAG, "authHash: %s", authHash);
    		
			/**
			 * CSR phase
			 */
			// Get PEM format.
			byte[] csrPem = kpg.getCSRAsPEM();
			
			// Write as string - PEM encoded.
			final String pemStr = new String(csrPem);
			Log.inf(TAG, "CSRPEM: %s", pemStr);
			publishProgress(getContext().getString(R.string.p_encrypting_csr));
			
			// Encrypt CSR with defined encryption key - important step, prevents attacker to sign his certificate.
			csrPem = AESCipher.encrypt(csrPem, encKey.toCharArray(), rand);
			Log.inf(TAG, "Encrypted CSRPEM: %s", pemStr);
			
			// Submit certificate signing request.
			SignCertificateV2Request req = new SignCertificateV2Request();
			req.setUser(par.getUserSIP());
			req.setUsrToken(ott.getUserToken());
			req.setServerToken(ott.getOneTimeToken());
			req.setAuthHash(authHash);
            req.setCsr(csrPem);
            req.setVersion(2);
			
			// Create envelope.
			SoapSerializationEnvelope soapEnvelope = new SoapSerializationEnvelope(SoapEnvelope.VER11);
			soapEnvelope.implicitTypes=false;
			
			req.register(soapEnvelope);		
						
			// Add request as object to envelope.
			soapEnvelope.setOutputSoapObject(req);
			new SignCertificateV2Response().register(soapEnvelope);
		
			// Send request to server.
			if (this.isCancelled()){
				return null;
			} else {
				Log.i(TAG, "Sending CSR to server to sign");
				publishProgress(getContext().getString(R.string.p_sending_csr));        		
			}
			
			Object obj = TaskSOAPHelper.simpleSOAPRequest(soapEnvelope,
					par.getServiceURL() + ServiceConstants.getSOAPServiceEndpoint(context),
					"", true, sslContextHolder);
			
			Log.inf(TAG, "Pure object response retrieved, class: %s; %s", obj.getClass().getCanonicalName(), obj.toString());
			
	        if (!(obj instanceof SignCertificateV2Response)){
	          	Log.w(TAG, "Bad format of response from server - probably problem during unmarshaling");
	           	throw new BaseAsyncTask.InvalidServerResponseException("Bad format of response");
	        }
	        
	        final SignCertificateV2Response certResp = (SignCertificateV2Response) obj;
         	byte[] certEnc = certResp.getCertificate().getCertificate();
         	
         	// Decode certificate.
         	X509CertificateHolder certHolder = new X509CertificateHolder(certEnc);
         	X509Certificate realCert = new JcaX509CertificateConverter().setProvider(KeyPairGenerator.getProv()).getCertificate(certHolder);
         	Log.inf(TAG, "X509Certificate init done; to string: %s", realCert.toString());
         	
         	// Check public key match.
         	if (!kpg.getKeyPair().getPublic().equals(realCert.getPublicKey())){
         		Log.e(TAG, "Security alert! Public key does not match!");
         		throw new CertificateSigningException("Server returned different certificate!", CertificateSigningException.CODE_DIFFERENT_PUBKEY);
         	} else {
         		Log.v(TAG, "Public key matches");
         	}
         	
         	// check CN match
         	String CNfromCert = CertificatesAndKeys.getCertificateCN(realCert);
         	if (!par.getUserSIP().equals(CNfromCert)){
         		Log.e(TAG, "Security alert! Server returned certificate with different CN!");
         		throw new CertificateSigningException("Server returned certificate with different CN!", CertificateSigningException.CODE_DIFFERENT_CN);
         	} else {
         		Log.v(TAG, "Certificate CN matches");
         	}
         	
         	// check validity against local trust store
         	TrustVerifier tv = SSLSOAP.getDefaultTrustManager(getContext());
         	try {
         		// Check certificate validity, tolerate 5 minutes clock drift
         		// server may be ahead and it would make this check fail.
         		tv.checkTrusted(realCert, new Date(System.currentTimeMillis() + 5*60*1000));
         		Log.v(TAG, "Certificate verification OK");
             } catch(Exception e){
            	Log.e(TAG, "Security alert! Server returned invalid certificate !");
             	throw new CertificateSigningException("Security alert! Server returned invalid certificate !", e, CertificateSigningException.CODE_INVALID_CERTIFICATE);
             }
         	

    		/**
    		 * PKCS12 phase
    		 */
    		if (par.isStoreResult()){
    			// 1. Generate strong encryption keys
    			Log.i(TAG, "Going to generate StoragePassword");         	
    			publishProgress(getContext().getString(R.string.p_generating_key));
    			//   1.1 Create a new salt
    			Log.i(TAG, "Initializing new salt and saving to SharedPrefs");
    			PkcsPasswordSchemeV2.generateNewSalt(context, par.getUserSIP(), rand);
    			//   1.2 Generate Enc Keys
    			Log.i(TAG, "Generating new encryption keys");
    			final String storagePass = PkcsPasswordSchemeV2.getStoragePass(context, par.getUserSIP(), par.getUserPass());
				//   1.3 Generate PEM keys for PJSIP library
				PemPasswordSchemeV2.generateNewSalt(context, par.getUserSIP(), rand);
				final String pemStoragePass2 = PemPasswordSchemeV2.getStoragePass(context, par.getUserSIP(), storagePass, true);

				//   1.4 Update new passwords in structure
    			par.setStoragePass(storagePass);
    			par.setPemPass(pemStoragePass2);
    			
    			// 2. Initialize KeyStores
    			Log.i(TAG, "Going to init and save KeyStores");
    			publishProgress(getContext().getString(R.string.p_storing_csr));
    			final File ksFile1 = CertificatesAndKeys.getKeyStoreFile(context, CertificatesAndKeys.derivePkcs12Filename(par.getUserSIP()));
    			// PKCS12 KeyStore
    			KeyStoreHelper.initNewKeyStore(
    					ksFile1, 								// KeyStore file
    					storagePass, 							// StoragePassword
    					CertificatesAndKeys.KEYSTORE_PKCS12, 	// PKCS12 KeyStore
    					realCert, 								// Certificate
    					kpg.getKeyPair().getPrivate(), 			// Private Key
    					new Certificate[] { realCert });		// Certificate chain for private key             	
             	
    			//
             	// 3. Generate PEM format CA list, certificate, private key for use in PJSIP with OpenSSL
             	//
             	KeyPairGenerator.createPemFiles(getContext(), realCert, kpg.getKeyPair().getPrivate(), pemStoragePass2.toCharArray(), par.getUserSIP());
             	
             	//
             	// Change TLS settings for PJSIP
             	//
             	PreferencesConnector prefs = new PreferencesConnector(getContext());
             	prefs.setBoolean(PhonexConfig.ENABLE_TLS, PhonexSettings.useTLS());
             	prefs.setString(PhonexConfig.CA_LIST_FILE, context.getFileStreamPath(CertificatesAndKeys.CA_LIST_FILE).getAbsolutePath());

				prefs.setString(PhonexConfig.CERT_FILE, context.getFileStreamPath(CertificatesAndKeys.deriveUserCertFilename(par.getUserSIP())).getAbsolutePath());
				prefs.setString(PhonexConfig.PRIVKEY_FILE, context.getFileStreamPath(CertificatesAndKeys.derivePrivKeyFilename(par.getUserSIP())).getAbsolutePath());

             	prefs.setString(PhonexConfig.TLS_USER, par.getUserSIP());
             	
             	Log.df(TAG, "P12, PEM written, TLS set; enabled: %s",
             	        prefs.getBoolean(PhonexConfig.ENABLE_TLS));
             	
             	// Apply identity loader here.
             	IdentityLoader il = new IdentityLoader();
             	int certCode = il.loadIdentityKeys(context, par, false);
            	if (certCode == IdentityLoader.CODE_EXISTS){
            		par.setServiceURL(ServiceConstants.getServiceURL(par.getUserDomain(), true));
            		par.setPrivateCredentials(il.getPrivateCredentials());
            	} else {
            		par.setServiceURL(ServiceConstants.getServiceURL(par.getUserDomain(), false));
            		par.setPrivateCredentials(null);
            		Log.ef(TAG, "Cannot open already created KeyStore. code=%s", certCode);
            		
            		throw new RuntimeException("Cannot open already created KeyStore."); 
            	}
    		}
    		
         	Log.i(TAG, "Key storage writen to internal storage. Process finished");

            // Remove ZRTP cache directory with cached secrets from previous ZRTP sessions.
            File zrtpDir = PreferencesManager.getZrtpFolder(context);
            if (zrtpDir != null){
                MiscUtils.deleteRecursive(zrtpDir);
                Log.vf(TAG, "ZRTP directory has been cleaned: %s", zrtpDir.toString());
            }

         	// Delete all DH keys from the server - certificate change invalidates all existing keys.
         	if (par.isRemoveDHKeys()){
         		Log.d(TAG, "Going to delete keys - we have new certificate.");
         		
         		GenKeyParams p2 = new GenKeyParams();
         		p2.setUserList(null); // will remove all keys
         		p2.setMySip(par.getUserSIP());
         		p2.setStoragePass(par.getStoragePass());
         		
         		GenKeyCall t2 = new GenKeyCall();
         		t2.setContext(getContext());
         		t2.deleteKeys(p2, rand);
         	}
			
			par.setCertificateJustCreated(true);
		} catch (SecurityException se){
			Log.e(TAG, "Security Exception", se);
			MiscUtils.reportExceptionToAcra(se);
			return se;
		} catch (Exception e) {
			Log.e(TAG, "Exception in certGenTask", e);
			MiscUtils.reportExceptionToAcra(e);
			return e;
		}
		return null;	
	}

	public void publishProgress(String... values) {
		Intent intent = new Intent(Intents.ACTION_LOGIN_PROGRESS);
		intent.putExtra(Intents.EXTRA_GENERIC_PROGRESS, GenericTaskProgress.progressInstance(values != null && values.length > 0 ? values[0] : null));
		MiscUtils.sendBroadcast(context, intent);
	}

	protected void init() {
		receiver = new LoginCancelledBroadcastReceiver(this);
		receiver.register(context);
	}

	protected void onPostExecute(Exception result) {

		context.unregisterReceiver(receiver);
		if (result == null){
            Log.vf(TAG, "onPostExecute; error result is null (success)");

			if (onCompletedListener!=null){
				onCompletedListener.onCertGenCompleted(true, null, null);
			}
		
		} else {
			int errResId = R.string.p_problem_nonspecific;
			if (result instanceof BaseAsyncTask.InvalidServerResponseException){
				errResId = R.string.p_problem_bad_response;
			} else if (result instanceof CertificateSigningException){
				errResId = R.string.p_problem_bad_certificate;
			}

            Log.wf(TAG, result, "onPostExecute; error result is not null (some problem occurred)");
			publishError(GenericError.GENERIC_ERROR, getContext().getString(errResId));

			if (onCompletedListener!=null){
				onCompletedListener.onCertGenCompleted(false, null, result);
			}
		}
	}

	private void publishError(GenericError error, String message) {
		Intent intent = new Intent(Intents.ACTION_LOGIN_PROGRESS);
		intent.putExtra(Intents.EXTRA_GENERIC_PROGRESS, GenericTaskProgress.errorInstance(error, message));
		MiscUtils.sendBroadcast(context, intent);
	}

    /**
     * Special exception to report particular error conditions during certificate signing.
     *  
     * @author ph4r05
     */
    public static class CertificateSigningException extends Exception {
    	public static final long serialVersionUID=7L;
    	public static final int CODE_NONE=0;
    	public static final int CODE_DIFFERENT_PUBKEY=1;
    	public static final int CODE_DIFFERENT_CN=2;
    	public static final int CODE_INVALID_CERTIFICATE=3;
    	private int code=0;

		public CertificateSigningException() {
			super();
		}

		public CertificateSigningException(String detailMessage, Throwable throwable) {
			super(detailMessage, throwable);
		}
		
		public CertificateSigningException(String detailMessage, Throwable throwable, int code) {
			super(detailMessage, throwable);
			this.code = code;
		}
		
		public CertificateSigningException(String detailMessage, int code) {
			super(detailMessage);
			this.code = code;
		}

		public CertificateSigningException(String detailMessage) {
			super(detailMessage);
		}

		public CertificateSigningException(Throwable throwable) {
			super(throwable);
		}

		public int getCode() {
			return code;
		}
    }
	
}

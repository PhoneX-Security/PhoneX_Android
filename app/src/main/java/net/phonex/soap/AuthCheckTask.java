package net.phonex.soap;

import android.content.Intent;
import android.support.annotation.NonNull;

import net.phonex.PhonexSettings;
import net.phonex.R;
import net.phonex.core.Constants;
import net.phonex.core.Intents;
import net.phonex.db.DBHelper;
import net.phonex.ksoap2.SoapEnvelope;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.pub.parcels.GenericError;
import net.phonex.pub.parcels.GenericTaskProgress;
import net.phonex.soap.SSLSOAP.SSLContextHolder;
import net.phonex.soap.entities.AuthCheckV3Request;
import net.phonex.soap.entities.AuthCheckV3Response;
import net.phonex.soap.entities.TrueFalse;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.crypto.CertificatesAndKeys;
import net.phonex.util.crypto.CertificatesAndKeys.IdentityLoader;
import net.phonex.util.crypto.CertificatesAndKeys.PemPasswordSchemeV2;
import net.phonex.util.crypto.CertificatesAndKeys.PkcsPasswordSchemeV2;
import net.phonex.util.crypto.CertificatesAndKeys.XmppPasswordSchemeV1;
import net.phonex.util.crypto.PRNGFixes;
import net.phonex.util.guava.Files;

import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Async service for checking the password.
 * 
 * @author ph4r05
 */
public class AuthCheckTask extends BaseLoginRunnable implements LoginCancelledBroadcastReceiver.LoginCancelledListener {
	public final static String TAG = "AuthCheckTask";

	public interface OnAuthCheckCompleted{
		void onAuthCheckCompleted(AuthCheckV3Response response);
	}

	private OnAuthCheckCompleted onAuthCheckCompletedListener;
	private AuthCheckV3Response authResponse = null;
	private final CertGenParams params;

	private LoginCancelledBroadcastReceiver receiver;

	@Override
	public void loginCancelled() {
		cancel();
	}

	public AuthCheckTask(OnAuthCheckCompleted listener, @NonNull CertGenParams params) {
		this.onAuthCheckCompletedListener = listener;
		this.params = params;
	}

	@Override
	public void run() {
		receiver = new LoginCancelledBroadcastReceiver(this);
		receiver.register(context);

		Exception result = runInternal();

		if (!isCancelled()) {
			onPostExecute(result);
		}
	}

    public Exception runInternal() {
		try {
			/**
			 * Init SecureRandom, it may take some time, thus it is initialized in
			 * background task. It is also needed prior first SSL connection
			 * or key generation.
			 */
			PRNGFixes.apply();
			SecureRandom rand = new SecureRandom();
			Log.vf(TAG, "SecureRand initialized, next int=%s", rand.nextInt());
			
			/**
			 * In this phase derive key material necessary to unlock potential 
			 * KeyStore containing private key needed for TLS connection.
			 * 
			 * Determine certificate existence for this user before first check.
			 * We have to also check our certificate revocation status, what is done
			 * during auth check, for this we need our certificate.
			 */
			publishProgress(getContext().getString(R.string.p_generating_key));

			// Jan 2016 - do migration to new filenames (keys, cert, db) before authentication starts
			try {
				migrateToNewFilenames(params.getUserSIP());
			} catch (NoSuchAlgorithmException e) {
				Log.ef(TAG, e, "Unable to migrate filenames");
			}

			try {
    			// 1. Generate strong encryption keys
				params.setStoragePass(null);
				params.setPemPass(null);
				
    			Log.v(TAG, "Going to generate storagePassword");
    			// Has to be salt presented, otherwise identity files cannot be read 
    			//   a) old version
    			//   b) no files 
    			if (!PkcsPasswordSchemeV2.saltExists(context, params.getUserSIP())
    					|| !PemPasswordSchemeV2.saltExists(context, params.getUserSIP())){
    				Log.w(TAG, "Salt for password schemes is missing, cannot generate password.");
    			} else {
	    			//   1.1 Generate Encryption Keys for KeyStore and PEM files.
	    			final String storagePass = PkcsPasswordSchemeV2.getStoragePass(context, params.getUserSIP(), params.getUserPass());
	    			//   1.2 Generate PEM keys for PJSIP library
	    			final String pemPass = PemPasswordSchemeV2.getStoragePass(context, params.getUserSIP(), storagePass, true);
					
	    			params.setStoragePass(storagePass);
	    			params.setPemPass(pemPass);
	    			Log.v(TAG, "Encryption keys generated");
    			}
    			
    			// Generate XMPP password
    			final String xmppPassword = XmppPasswordSchemeV1.getStoragePass(context, params.getUserSIP(), params.getUserPass());
    			params.setXmppPass(xmppPassword);
    			
			} catch(Exception e){
				Log.e(TAG, "Cannot generate strong encryption keys.");
				throw new KeyDerivationException(e);
			}
			
			/**
			 * Exists certificate check.
			 * Loads user identity, performing potential password version upgrade. 
			 * If there is no user identity stored, another service URL is 
			 * used (requiring no client certificate with thin functionality).
			 */
			publishProgress(getContext().getString(R.string.p_authentication));
			
			IdentityLoader il = new IdentityLoader();
			il.setRand(rand);
			
			int certCode = il.loadIdentityKeys(context, params, true);
        	if (certCode == IdentityLoader.CODE_EXISTS){
        		params.setServiceURL(ServiceConstants.getServiceURL(params.getUserDomain(), true));
        		params.setPrivateCredentials(il.getPrivateCredentials());
        	} else {
        		params.setServiceURL(ServiceConstants.getServiceURL(params.getUserDomain(), false));
        		params.setPrivateCredentials(null);
        	}
			
			/**
			 * Preparing phase - initialize SSL connections
			 * 
			 * Install HTTPS support with client credentials and trust verifier.
			 * If we have some private credentials, use them. We have to test our certificate. 
			 */
			UserPrivateCredentials priv = params.getPrivateCredentials();
            SSLContextHolder sslContextHolder = null;
			if (priv!=null){
                sslContextHolder = this.prepareSoap(priv.getKs(), priv.getPkKey());
            } else {
                sslContextHolder = this.prepareSoap();
			}
    		
			// request signing of certificate
			String HA1 = AuthHashGenerator.getHA1(params.getUserSIP(), params.getUserPass());
			Log.inf(TAG, "HA1: %s", HA1);		
			String authHash = AuthHashGenerator.generateUserAuthToken(params.getUserSIP(), HA1, "", "",1000 * 60 * 10, 0);
			Log.inf(TAG, "authHash: %s", authHash);
			
			
			AuthCheckV3Request req = new AuthCheckV3Request();
			req.setTargetUser(params.getUserSIP());
			req.setAuthHash(authHash);
			req.setIgnoreNullWrappers(false);
			req.setUnregisterIfOK(TrueFalse.TRUE);
            req.setVersion(3);
            req.setAppVersion(PhonexSettings.getUniversalApplicationDesc(context));
			
			// create envelope
			SoapSerializationEnvelope soapEnvelope = new SoapSerializationEnvelope(SoapEnvelope.VER11);
			req.register(soapEnvelope);
			
			// add request as object to envelope
			soapEnvelope.setOutputSoapObject(req);
			new AuthCheckV3Response().register(soapEnvelope);
			new AuthCheckV3Request().register(soapEnvelope);
			
			if (this.isCancelled()){
    			return null;
    		}
			
			Object obj = TaskSOAPHelper.simpleSOAPRequest(soapEnvelope,
    				params.getServiceURL() + ServiceConstants.getSOAPServiceEndpoint(context),
    				"", true, sslContextHolder);
			
			Log.inf(TAG, "Pure object response retrieved, class: %s; %s", obj.getClass().getCanonicalName(), obj.toString());
			
			if (this.isCancelled()){
    			return null;
    		}
			
			if (obj instanceof AuthCheckV3Response){
				authResponse = (AuthCheckV3Response) obj;
			} else {
	          	Log.w(TAG, "Bad format of the response from server - probably problem during unmarshaling");
	           	throw new IllegalArgumentException("Bad format of response");
	        }

			Log.i(TAG, "Finished properly");
		} catch (Exception e) {
			Log.e(TAG, "Exception in auth check process", e);			
			
			return e;
		}
		
		return null;
	}

	/**
	 * Migrate old filenames to sip-derived ones, to enable users to switch between accounts (Jan 2016)
	 * @param sip
	 * @throws NoSuchAlgorithmException
	 */
	private void migrateToNewFilenames(String sip) throws NoSuchAlgorithmException {
		Log.df(TAG, "Checking if to migrate to new filenames");
		// 1. Migrate DB
		String oldDbFilename = Constants.AUTHORITY;
		String newDbFilename = CertificatesAndKeys.deriveDbFilename(sip);
		File oldDbFile = DBHelper.DatabaseHelper.getDatabaseFile(context, oldDbFilename);
		File newDbFile = DBHelper.DatabaseHelper.getDatabaseFile(context, newDbFilename);

		if (oldDbFile.exists()){
			migrateAndDelete(oldDbFile, newDbFile);
		}

		// 2. Migrate pkcs12 file, cert, private and public keys
		File certFileOld = CertificatesAndKeys.getKeyStoreFile(context, CertificatesAndKeys.CERT_FILE_OLD);
		File certFileNew = CertificatesAndKeys.getKeyStoreFile(context, CertificatesAndKeys.deriveUserCertFilename(sip));
		if (certFileOld.exists()){
			migrateAndDelete(certFileOld, certFileNew);
		}

		File pubkeyOld = CertificatesAndKeys.getKeyStoreFile(context, CertificatesAndKeys.PUBKEY_FILE_OLD);
		File pubkeyNew = CertificatesAndKeys.getKeyStoreFile(context, CertificatesAndKeys.derivePubKeyFilename(sip));
		if (pubkeyOld.exists()){
			migrateAndDelete(pubkeyOld, pubkeyNew);
		}

		File privkeyOld = CertificatesAndKeys.getKeyStoreFile(context, CertificatesAndKeys.PRIVKEY_FILE_OLD);
		File privkeyNew = CertificatesAndKeys.getKeyStoreFile(context, CertificatesAndKeys.derivePrivKeyFilename(sip));
		if (privkeyOld.exists()){
			migrateAndDelete(privkeyOld, privkeyNew);
		}

		File pkcs12Old = CertificatesAndKeys.getKeyStoreFile(context, CertificatesAndKeys.PKCS12Container_OLD);
		File pkcs12New = CertificatesAndKeys.getKeyStoreFile(context, CertificatesAndKeys.derivePkcs12Filename(sip));
		if (pkcs12Old.exists()){
			migrateAndDelete(pkcs12Old, pkcs12New);
		}
	}

	private void migrateAndDelete(File from, File to){
		try {
			Files.copy(from, to);
			boolean oldDeleted = from.delete();
			Log.inf(TAG, "File was was migrated, from=%s, to=%s, deleted=%s", from, to, oldDeleted);
		} catch(Exception e){
			Log.ef(TAG, "Unable to migrate file, from=%s, to=%s", from, to);
		}
	}

	public void publishProgress(String... values) {

		Intent intent = new Intent(Intents.ACTION_LOGIN_PROGRESS);
		intent.putExtra(Intents.EXTRA_GENERIC_PROGRESS, GenericTaskProgress.progressInstance(values != null && values.length > 0 ? values[0] : null));
		MiscUtils.sendBroadcast(context, intent);
	}

	private void publishError(GenericError error, String message) {
		Intent intent = new Intent(Intents.ACTION_LOGIN_PROGRESS);
		intent.putExtra(Intents.EXTRA_GENERIC_PROGRESS, GenericTaskProgress.errorInstance(error, message));
		MiscUtils.sendBroadcast(context, intent);
	}

	protected void onPostExecute(Exception result) {

		context.unregisterReceiver(receiver);
		if (result == null) {
			if (onAuthCheckCompletedListener != null) {
				onAuthCheckCompletedListener.onAuthCheckCompleted(authResponse);
			}
			return;
		}

		Log.wf(TAG, result, "onPostExecute; exception received");
		if (result instanceof java.net.UnknownHostException){
			// UnknownHostException - probably DNS problem, no Internet connection.
			//
			publishError(GenericError.GENERIC_ERROR, getContext().getString(R.string.p_problem_resolv));
		} else if (result instanceof java.net.SocketException
				|| result instanceof java.io.IOException){
			// SocketException - probably problem with connecting to the service. In most cases:
			// java.net.SocketException: No route to host

			publishError(GenericError.GENERIC_ERROR, getContext().getString(R.string.p_problem_socket));
		} else if ((result instanceof IllegalArgumentException && "Bad format of response".equalsIgnoreCase(result.getMessage()))
				|| result instanceof org.xmlpull.v1.XmlPullParserException){
			// Exception from main body - illegal response from the server
			publishError(GenericError.GENERIC_ERROR, getContext().getString(R.string.p_problem_auth_bad_response));
		} else {
			publishError(GenericError.GENERIC_ERROR, getContext().getString(R.string.p_problem_nonspecific));
		}
	}

    /**
     * Special exception to report error in generating encryption key for KeyStores.
     *  
     * @author ph4r05
     */
    public static class KeyDerivationException extends Exception {
    	public static final long serialVersionUID=7L;
    	private int code=0;

		public KeyDerivationException() {
			super();
		}

		public KeyDerivationException(String detailMessage, Throwable throwable) {
			super(detailMessage, throwable);
		}

		public KeyDerivationException(String detailMessage, Throwable throwable, int code) {
			super(detailMessage, throwable);
			this.code = code;
		}

		public KeyDerivationException(String detailMessage, int code) {
			super(detailMessage);
			this.code = code;
		}

		public KeyDerivationException(String detailMessage) {
			super(detailMessage);
		}

		public KeyDerivationException(Throwable throwable) {
			super(throwable);
		}

		public int getCode() {
			return code;
		}
    }
    
    /**
     * Special exception to report error in KeyStore.
     *  
     * @author ph4r05
     */
    public static class KeyStoreException extends Exception {
    	public static final long serialVersionUID=7L;
    	private int code=0;

		public KeyStoreException() {
			super();
		}

		public KeyStoreException(String detailMessage, Throwable throwable) {
			super(detailMessage, throwable);
		}
		
		public KeyStoreException(String detailMessage, Throwable throwable, int code) {
			super(detailMessage, throwable);
			this.code = code;
		}
		
		public KeyStoreException(String detailMessage, int code) {
			super(detailMessage);
			this.code = code;
		}

		public KeyStoreException(String detailMessage) {
			super(detailMessage);
		}

		public KeyStoreException(Throwable throwable) {
			super(throwable);
		}

		public int getCode() {
			return code;
		}
    }
    
    
}

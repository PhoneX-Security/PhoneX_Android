package net.phonex.soap;

import net.phonex.R;
import net.phonex.core.SipUri;
import net.phonex.core.SipUri.ParsedSipContactInfos;
import net.phonex.db.DBHelper.DatabaseHelper;
import net.phonex.db.DBProvider;
import net.phonex.ksoap2.SoapEnvelope;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.pub.parcels.StoredCredentials;
import net.phonex.soap.AuthCheckTask.KeyDerivationException;
import net.phonex.soap.entities.PasswordChangeV2Request;
import net.phonex.soap.entities.PasswordChangeV2Response;
import net.phonex.ui.dialogs.AlertDialogFragment;
import net.phonex.ui.interfaces.OnPasswordChangeCompleted;
import net.phonex.util.Log;
import net.phonex.util.crypto.AESCipher;
import net.phonex.util.crypto.CertificatesAndKeys.IdentityLoader;
import net.phonex.util.crypto.CertificatesAndKeys.PemPasswordSchemeV2;
import net.phonex.util.crypto.CertificatesAndKeys.PkcsPasswordSchemeV2;
import net.phonex.util.crypto.CertificatesAndKeys.XmppPasswordSchemeV1;

import java.security.SecureRandom;


public class ChangePasswordTask extends BaseAsyncTask<PasswordChangeParams>{
	private static final String TAG = "ChangePasswordTask";	
	public final static int CHECKPOINTS_NUMBER = 7; //how many progress checkpoints it needs
	
	// Callback interface called after task is finished.
	private OnPasswordChangeCompleted onPasswordChangeCompletedListener;
	
	// Response for change password request from the server.
	private PasswordChangeV2Response response = null;
	private PasswordChangeParams params = null;
	
	/**
	 * Constructor. 
	 * Callback on action finished has to be provided.
	 * 
	 * @param listener
	 */
	public ChangePasswordTask(OnPasswordChangeCompleted listener) {
		this.onPasswordChangeCompletedListener = listener;
	}	
	
	@Override
	protected Exception doInBackground(PasswordChangeParams... arg0) {
		if (arg0.length==0){
			throw new IllegalArgumentException("Empty configuration");
		}
		
		PasswordChangeParams par = arg0[0];
		
		try {
			/**
			 * Init SecureRandom, it may take some time, thus it is initialized in
			 * background task. It is also needed prior first SSL connection
			 * or key generation.
			 */
			SecureRandom rand = new SecureRandom();
			Log.vf(TAG, "SecureRand initialized, next int=%s", rand.nextInt());
			
			/**
			 * Preparing phase - initialize SSL connections
			 * 
			 * Install HTTPS support with client credentials and trust verifier
			 */
            final SSLSOAP.SSLContextHolder sslContextHolder = this.prepareSoap();

            /**
			 * One time token phase
			 */
			// prepare objects to get one time token
			OneTimeToken ott = new OneTimeToken();
			ott.setType(1);
			ott.setUser(par.getUserSIP());
			
			// obtain one time token from server to be able to submit valid certificate signing request
			Log.i(TAG, "Obtaining server token");		
			publishProgress(getContext().getString(R.string.p_getting_token));

			
			ott.setServiceEndpoint(par.getServiceURL() + ServiceConstants.getSOAPServiceEndpoint(context));
			ott.generateUserToken();
			ott.callGetOneTimeToken();		
			
			// request signing of certificate
			String tmpDomain;
			try {
        		ParsedSipContactInfos in = SipUri.parseSipContact(par.getUserSIP().trim());
        		tmpDomain = in.domain;
        	} catch(Exception e){
        		Log.e(TAG, "Exception: cannot parse domain from SIP name", e);
        		return e;
        	}
			
			final String domain = tmpDomain;
			final String HA1 = AuthHashGenerator.getHA1(par.getUserSIP(), par.getUserOldPass());
			final String HA1B = AuthHashGenerator.getHA1(par.getUserSIP(), domain, par.getUserOldPass());
			final String authHash = AuthHashGenerator.generateUserAuthToken(par.getUserSIP(), HA1, ott.getUserToken(), ott.getOneTimeToken(), 1000 * 60 * 10, 0);
			final String encKey = AuthHashGenerator.generateUserEncToken(par.getUserSIP(), HA1,
					ott.getUserToken(), ott.getOneTimeToken(), 
					1000 * 60 * 10, 0);
						
			publishProgress(getContext().getString(R.string.p_encrypting_pass));
			
			//new password
			Log.df(TAG, "generating new password for domain: [%s] for user: [%s] ", domain, par.getUserSIP());
			final String newHA1String = AuthHashGenerator.getHA1(par.getUserSIP(), par.getUserNewPass());
			final String newHA1BString = AuthHashGenerator.getHA1(par.getUserSIP(), domain, par.getUserNewPass());
			
			final byte[] newHA1 = AESCipher.encrypt(newHA1String.getBytes("UTF-8"), encKey.toCharArray(), rand);
			final byte[] newHA1B = AESCipher.encrypt(newHA1BString.getBytes("UTF-8"), encKey.toCharArray(), rand);
			
			//set password request
			PasswordChangeV2Request req = new PasswordChangeV2Request();
			req.setTargetUser(par.getUserSIP());
			req.setUser(par.getUserSIP());
            req.setVersion(2);
			
			req.setServerToken(ott.getOneTimeToken());
			req.setUsrToken(ott.getUserToken());
			req.setAuthHash(authHash);
			
			req.setNewHA1(newHA1);
			req.setNewHA1B(newHA1B);		
				
			// create envelope
			SoapSerializationEnvelope soapEnvelope = new SoapSerializationEnvelope(SoapEnvelope.VER11);
			req.register(soapEnvelope);
			
			// add request as object to envelope
			soapEnvelope.setOutputSoapObject(req);
			new PasswordChangeV2Response().register(soapEnvelope);
			
			if (this.isCancelled()){
				return null;
			} else {
				Log.i(TAG, "Authentication");
				publishProgress(getContext().getString(R.string.p_getting_response));        		
			}
			
			Object obj = TaskSOAPHelper.simpleSOAPRequest(soapEnvelope,
					par.getServiceURL() + ServiceConstants.getSOAPServiceEndpoint(context),
					"", true, sslContextHolder);

	        if (!(obj instanceof PasswordChangeV2Response)){
	          	Log.w(TAG, "Bad format of response from server - probably problem during unmarshaling");
	           	throw new IllegalArgumentException("Bad format of response");
	        }
	        
	        response = (PasswordChangeV2Response) obj;
	        params = par;
	        Log.v(TAG, "Password change process ended. ");
	        
	        //
	        // Key-regeneration, proceed only if new password was set correctly
	        //
	        if (response.getResult() != 1){
	        	return null;
	        }
	        
	        String oldStoragePass = null;
	        String oldPemPass = null;

	        // Generate passwords if desired.
	        if (par.isDerivePasswords()){
	        	publishProgress(getContext().getString(R.string.p_generating_key));
				try {
	    			// 1. Generate strong encryption keys
	    			Log.v(TAG, "Going to generate storagePassword and pemPassword");
	    			//   1.1 Generate Enc Keys
	    			final String storagePass = PkcsPasswordSchemeV2.getStoragePass(context, par.getUserSIP(), par.getUserNewPass());
	    			//   1.2 Generate old storage password in order to re-key KeyStore
	    			oldStoragePass           = PkcsPasswordSchemeV2.getStoragePass(context, par.getUserSIP(), par.getUserOldPass());
	    			//   1.3 Generate PEM keys for PJSIP library
	    			final String pemStoragePass2 = PemPasswordSchemeV2.getStoragePass(context, par.getUserSIP(), storagePass, true);
	    			//   1.4 Old PEM keys
	    			oldPemPass                   = PemPasswordSchemeV2.getStoragePass(context, par.getUserSIP(), oldStoragePass, true);
					final String xmppPass         = XmppPasswordSchemeV1.getStoragePass(context, par.getUserSIP(), par.getUserNewPass());
	    			
	    			par.setStoragePass(storagePass);
	    			par.setPemPass(pemStoragePass2);
	    			par.setXmppPass(xmppPass);
				} catch(Exception e){
					Log.e(TAG, "Cannot generate strong encryption keys.");
					throw new KeyDerivationException(e);
				}
	        }
	        
	        // Re-key KeyStore if desired
	        if (par.isRekeyKeyStore()){
	        	publishProgress(getContext().getString(R.string.p_rekey_keystore));
	        	Log.v(TAG, "Going to re-key key store");
	        	
	        	// Need to generate CertGen
	        	StoredCredentials creds = new StoredCredentials();
	        	creds.setUserSip(par.getUserSIP());
	        	creds.setUsrPass(par.getUserOldPass());
	        	creds.setUsrPemPass(oldPemPass);
	        	creds.setUsrStoragePass(oldStoragePass);
	        	
	        	// Load key store saved with old password.
	        	IdentityLoader il = new IdentityLoader();
	        	int ksload = il.loadIdentityKeys(context, creds, false);
	        	if (ksload == IdentityLoader.CODE_EXISTS){
	        		Log.v(TAG, "Managed to open key store with old password");
	        		
	        		// Save key store with the new key and regenerate PEM files
	        		creds.setUsrPass(par.getUserNewPass());
	        		creds.setUsrStoragePass(par.getStoragePass());
	        		creds.setUsrPemPass(par.getPemPass());
	        		
	        		Log.v(TAG, "Going to re-key key store");
	        		il.storeKSToFile(context, creds);
	        		
	        		// Re-generate PEM files
	        		Log.v(TAG, "Going to re-generate PEM files with new password");
	        		il.generatePEMFromKS(context, creds, true);
	        	}
	        }
	        
	        // Re-key the whole database
	        if (par.isRekeyDB()){
	        	Log.v(TAG, "Going to re-key database");
	        	publishProgress(getContext().getString(R.string.p_rekey_database));
	        	
	        	DBProvider.setNewEncKey(context.getContentResolver(), DatabaseHelper.formatDbPassword(par.getUserSIP(), par.getUserNewPass()), true);
	        	Log.v(TAG, "Database re-keying done");
	        }
	        
	        if (this.isCancelled()){
    			return null;
    		}
             
			Log.i(TAG, "Finished properly");
			publishProgress(getContext().getString(R.string.p_success));
		} catch (Exception e) {
			Log.e(TAG, "Exception", e);					
			return e;
		}		
		return null;
	}
	
	/**
	 * Converts PasswordChangeParams to CertGenParams
	 * @param par
	 * @return
	 */
	protected CertGenParams conv(PasswordChangeParams par){
		CertGenParams p = new CertGenParams();
		p.setPemPass(par.getPemPass());
		p.setServiceURL(par.getServiceURL());
		p.setStoragePass(par.getStoragePass());
		p.setUserSIP(par.getUserSIP());
		p.setUserPass(par.getUserNewPass());
		
		try {
    		ParsedSipContactInfos in = SipUri.parseSipContact(par.getUserSIP());
    		p.setUserDomain(in.domain);
    	} catch(Exception e){
    		Log.w(TAG, "Exception: cannot parse domain from SIP name", e);
    	}
		
		return p;
	}
	
    @Override
	protected void onPostExecute(Exception result) {
    	if (mFragment==null) return;    	
    	
		// some exception returned -> problem during process
		if (result == null){
			mFragment.taskFinished(result);  //dismiss
			
			onPasswordChangeCompletedListener.onPasswordChangeCompleted(response, params);
		} else {
			//TODO this throws an exception when screen is rotated before
			//this bug http://stackoverflow.com/questions/10114324/show-dialogfragment-from-onactivityresult

			// dirty fix detecting that entered old password is not correct
			if (result.getMessage().contains("Not authorized")){
				AlertDialogFragment.newInstance(getContext().getString(R.string.p_problem), getContext().getString(R.string.old_password_incorrect)).showAllowingStateLoss(fragmentManager, "alert");
			} else {
				AlertDialogFragment.newInstance(getContext().getString(R.string.p_problem), getContext().getString(R.string.p_problem_nonspecific)).showAllowingStateLoss(fragmentManager, "alert");
			}

			mFragment.taskFinished(result);  //dismiss
		}

	}
}

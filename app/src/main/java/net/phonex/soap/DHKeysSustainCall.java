package net.phonex.soap;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;

import net.phonex.core.Constants;
import net.phonex.db.entity.SipClist;
import net.phonex.core.SipUri;
import net.phonex.core.SipUri.ParsedSipUriInfos;
import net.phonex.db.entity.DHOffline;
import net.phonex.db.entity.UserCertificate;
import net.phonex.ksoap2.SoapEnvelope;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.pub.parcels.KeyGenProgress;
import net.phonex.pub.parcels.KeyGenProgress.KeyGenStateEnum;
import net.phonex.service.XService.KeyGenProgressUpdatable;
import net.phonex.soap.GenKeyParams.GenKeyForUser;
import net.phonex.soap.entities.FtDHKeyUserInfo;
import net.phonex.soap.entities.FtDHKeyUserInfoArr;
import net.phonex.soap.entities.FtDHkeyState;
import net.phonex.soap.entities.FtGetStoredDHKeysInfoRequest;
import net.phonex.soap.entities.FtGetStoredDHKeysInfoResponse;
import net.phonex.ft.DHKeyHelper;
import net.phonex.util.Log;
import net.phonex.util.crypto.CertificatesAndKeys;
import net.phonex.util.crypto.MessageDigest;
import net.phonex.util.crypto.pki.TrustVerifier;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;


/**
 * DH keys maintenance task. 
 * 
 * Main target of this task: for each user in contact list keep
 * number of valid DH keys on the server at given level.
 * 
 * Removes old DH keys from the server - w.r.t. certificate of the user.
 * Keys generated before current certificate validity are removed. 
 * 
 * Is not AsyncTask since it requires to be initialized and runned 
 * on UI thread. 
 *  
 * @author ph4r05
 *
 */
public class DHKeysSustainCall extends DefaultSOAPCall {
	public final static String TAG = "DHKeysSustainTask";
	public final static int CHECKPOINTS_NUMBER = 6; //how many progress checkpoints it needs
	
	//callback interface
	public interface OnDHKeySustainCompleted{
		void onDHKeySustainCompleted(int code);
	}
	
	public DHKeysSustainCall(){
		super();
	}
	
	public DHKeysSustainCall(Context context){
		super(context);
	}
	
	public DHKeysSustainCall(OnDHKeySustainCompleted listener){
		super();
		onCompletedListener = listener;
	}
	
	/**
	 * Listener called on task finished.
	 */
	private OnDHKeySustainCompleted onCompletedListener;
	private KeyGenProgressUpdatable progress;
	private TrustVerifier tv;
	private DHKeyHelper dhelper;
	private Map<String, UserCert> userCerts = new HashMap<String, UserCert>();
	
	/**
	 * Result from main work procedure.
	 * @author ph4r05
	 */
	public static class Result{
		public Exception ex;
		public int code;
	}
	
	/**
	 * Signalizes cancellation of the task.
	 */
	private volatile boolean manuallyCanceled = false;
	
	/**
	 * Key generation task, initialized in doTask. 
	 * Declared here mainly due to manual cancellation.
	 */
	private GenKeyCall genKeyTask = null;
	
	/**
	 * Delete old keys based on certificate details for an user.
	 * Stores information about certificate for each user to userCerts.
	 * 
	 * SOAP, rand, tv, dhelper has to be initialized prior this call.
	 * 
	 * @param par
	 */
	protected Result deleteInvalidKeys(DHKeysSustainParams par){
		Result mres = new Result();
		mres.code = 0;
		mres.ex = null;
		
		Cursor c = getContext().getContentResolver().query(
				SipClist.CLIST_URI,
				SipClist.FULL_PROJECTION,
				null,
				null,
				null);
		
		// Store sip -> date, delete keys older than date for a given user.
		userCerts.clear();
		Map<String, Date> deleteOlderForUser = new HashMap<String, Date>();
		
		// Iterate over each contact, remove invalid DH keys 
		// from local database and mark for deletion from server.
        if (c != null) {
            try {
				ArrayList<String> sipArr = new ArrayList<>();
				ArrayList<Date> notBeforeArr = new ArrayList<>();
				ArrayList<ContentProviderOperation> operations = new ArrayList<>();
            	while(c.moveToNext()){
            		SipClist clist = new SipClist(c);
            		
            		// Load certificate for remote contact to determine current certificate hash & validity date (from).
            		Date notBefore = null;
            		String certHash = null;
            		
            		UserCert uc = new UserCert();
            		uc.certHash = null;
            		uc.notBefore = null;
            		uc.cert = null;
            		
            		// TODO: fix this in near future. Contacts have false in whitelist...
            		uc.inWhitelist = true; //clist.isInWhitelist(); 
            		
            		try {
	            		UserCertificate sipcert = CertificatesAndKeys.getRemoteCertificate(getContext(), clist.getSip());
	            		if (sipcert != null && sipcert.getCertificateStatus() == UserCertificate.CERTIFICATE_STATUS_OK && sipcert.getCertificateObj()!=null){
	            			X509Certificate curCert = sipcert.getCertificateObj();
	            			
	            			notBefore = curCert.getNotBefore();
	            			certHash = MessageDigest.getCertificateDigest(curCert);
	            			
	            			uc.notBefore = notBefore;
	            			uc.certHash = certHash;
	            			uc.cert = curCert;
	            		}
            		} catch(Exception e){
            			Log.wf(TAG, e, "Problem with user certificate for user %s", clist.getSip());
            		}
            		
            		// Store loaded information about user certificate for later use.
            		userCerts.put(clist.getSip(), uc);
            		
            		// Delete all invalid DH keys (w.r.t. current certificate). 
            		// If some were deleted, mark time notBefore and delete it from the server as well.
            		try {
        				// Remove only some, based on certificate data.
            			// If both parameters (notBefore, certHash) happen to be null, all
            			// DHkeys for particular user will be removed.
        				int removedNum = 0;
        				
        				// If want to expire old keys, calls key removal with current date (expiration point).
						final DHOffline.RemoveDhKeyQuery qr = DHOffline.removeDHKeys(clist.getSip(), notBefore, certHash, par.isExpireKeys() ? new Date() : null);
						if (qr == null){
							continue;
						}

						ContentProviderOperation curOp = ContentProviderOperation
								.newDelete(DHOffline.DH_OFFLINE_URI)
								.withSelection(qr.where, qr.whereArgs)
								.withYieldAllowed(true)
								.build();

						operations.add(curOp);
						sipArr.add(clist.getSip());
						notBeforeArr.add(notBefore);
                		Log.d(TAG, String.format("Phase 1, usr[%s] notBefore[%s] removed[%d]", clist.getSip(), uc.notBefore, removedNum));

					} catch(Exception e){
                     	Log.e(TAG, "Exception during removing invalid DHKeys", e);
					}
            	}

				// Apply all remove operation in one transaction.
				final ContentProviderResult[] res = ctxt.getContentResolver().applyBatch(Constants.AUTHORITY, operations);
				if (res == null) {
					Log.ef(TAG, "Invalid batch response = null");

				} else if (res.length != notBeforeArr.size()){
					Log.ef(TAG, "Inconsistent size of the result and number of operations, %s vs %s", res.length, notBeforeArr.size());

				} else {
					for(int i = 0, ln = res.length; i < ln; i++){
						ContentProviderResult cres = res[i];
						if (cres == null){
							continue;
						}

						if (cres.count != null && cres.count > 0){
							// There were some stored keys, remove them also from the server.
							// notBefore can be null if cert is not valid, in that case all keys will be
							// removed.
							deleteOlderForUser.put(sipArr.get(i), notBeforeArr.get(i));
							Log.vf(TAG, "Phase 1, delete op res %s, sip %s", cres.count, sipArr.get(i));
						}
					}

					Log.vf(TAG, "Phase completed");
				}

            } catch (Exception e) {
                Log.e(TAG, "Error while getting SipClist from DB", e);
            } finally {
                c.close();
            }
        }
        
        // If task was canceled, do no SOAP communication.
        if (manuallyCanceled){
        	mres.code=2;
        	return mres;
        }
        
        // If delete map is not empty, make a SOAP call to delete some entries.
        try {
	        if (!deleteOlderForUser.isEmpty()){
	        	Log.i(TAG, "Removing DHkeys from server");
         		
         		// Initialize task an invoke main work method.
         		GenKeyParams gkp = new GenKeyParams();
         		gkp.setMySip(par.getCreds().getUserSip());
         		gkp.setStoragePass(par.getCreds().getUsrStoragePass());
         		
         		// Uses already initialized task (private).
         		genKeyTask.deleteKeys(gkp, deleteOlderForUser, getRand());
	        }
        } catch(Exception e){
        	Log.e(TAG, "Exception in removing DH keys from the server", e);
        }
		
		return mres;
	}

    public void run(){
        // missing
    }
	
	/**
	 * Main entry point for this task.
	 * 
	 * @param par
	 * @return
	 */
	public Exception doTask(DHKeysSustainParams par){
		// HTTP transport - declare before TRY block to be able to 
		// extract response in catch block for debugging
		manuallyCanceled=false;
		
		try {
			/**
			 * Preparing phase - initialize SSL connections
			 * 
			 * Install HTTPS support with client credentials and trust verifier
			 */
			initSoap(par.getCreds().getUsrStoragePass());
			
			this.tv = SSLSOAP.getDefaultTrustManager(getContext());
			this.dhelper = new DHKeyHelper(getContext(), getRand());
			
			// Get my domain - for SOAP connection.
			ParsedSipUriInfos mytsinfo = SipUri.parseSipUri(SipUri.getCanonicalSipContact(par.getCreds().getUserSip(), true));
			final String domain = mytsinfo.domain;
			
			// Generate keys task init.     		
     		genKeyTask = new GenKeyCall();
     		genKeyTask.setContext(getContext());
			
			// 
			// Phase 1 - delete old keys based on certificate details for a user.
			//
			Log.inf(TAG, "Phase 1 - delete invalid keys; domain=%s", domain);
			this.deleteInvalidKeys(par);

			// Task may got canceled.
	        if (manuallyCanceled){
	        	Log.d(TAG, "Task was canceled");
	        	return null;
	        }
			
			//
			// Phase 2 - get all DH keys from the server, remove those not in local
			// 			 database, generate new keys and upload them to the server.
			// 
			Log.i(TAG, "Phase 2 - sync & generate keys");
			
			// SOAP Request to load all stored keys for all contacts.
			FtGetStoredDHKeysInfoRequest req = new FtGetStoredDHKeysInfoRequest();
			req.setDetailed(true);
        	req.setUsers(null);
        	req.setIgnoreNullWrappers(true);
        	
    		// Create SOAP envelope.
    		SoapSerializationEnvelope soapEnvelope = new SoapSerializationEnvelope(SoapEnvelope.VER11);
    		req.register(soapEnvelope); 
    		new FtGetStoredDHKeysInfoResponse().register(soapEnvelope);
    		soapEnvelope.setOutputSoapObject(req);
    		
    	    // Call remote SOAP function.
    		Object obj = null;
    		
    		try {
    			obj = SOAPHelper.simpleSOAPRequest(soapEnvelope, 
    					ServiceConstants.getDefaultURL(domain, getContext()),
    					"ftGetStoredDHKeysInfoRequest", true);
    			
    		} catch(ArrayIndexOutOfBoundsException ex){
    			// Result list is probably empty.
    			obj = new FtGetStoredDHKeysInfoResponse();
    			Log.i(TAG, "Keys are null (ArrayIndexOutOfBoundsException)");
    		} catch(Exception ex){
    			// Unspecified error during download.
    			obj=null;
    			Log.e(TAG, "Cannot parse response", ex);
    			
    			return ex;
    		}
    		
            Log.inf(TAG, "Pure object response retrieved, class: %s; %s", obj.getClass().getCanonicalName(), obj.toString());
            if (!(obj instanceof FtGetStoredDHKeysInfoResponse)){
            	Log.w(TAG, "Bad format of response from server - probably problem during unmarshaling");
            	throw new IllegalArgumentException("Bad format of response");
            }
            
            // Task may got canceled.
	        if (manuallyCanceled){
	        	Log.d(TAG, "Task was canceled");
	        	return null;
	        }
            
            final FtGetStoredDHKeysInfoResponse resp = (FtGetStoredDHKeysInfoResponse) obj;
            
            // Get set of all Nonce2 in database (function returns list, make a set).
            Set<String> nonceSet = new HashSet<String>(dhelper.getReadyDHKeysNonce2(null));
            // Set of nonce2 that are on server but not in local database.
            Set<String> deleteServerNonce = new HashSet<String>();
            // Set of nonce2 that are not on server but in local database.
            Set<String> deleteLocalNonce = new HashSet<String>();
            // Number of ready DH keys for each user
            Map<String, Integer> readyKeys = new HashMap<String, Integer>();
            // Set of nonces present on server.
            Set<String> serverNonceSet = new HashSet<String>();
            
            // Count READY DH keys for a given user. If less than given threshold, generate new ones & upload them.
            FtDHKeyUserInfoArr keyInfo = resp.getInfo();
            if (keyInfo!=null && keyInfo.isEmpty()==false){
            	for(FtDHKeyUserInfo info : keyInfo){
            		final String cnonce2 = info.getNonce2();
            		final FtDHkeyState cstate = info.getStatus();
            		final String csip = info.getUser();
            		
            		// If this nonce2 is not in local database, mark for deletion.
            		if (nonceSet.contains(cnonce2)==false){
            			deleteServerNonce.add(cnonce2);
            			continue;
            		}
            		
            		if (!readyKeys.containsKey(csip)){
            			readyKeys.put(csip, Integer.valueOf(0));
            		}
            		
            		// Increment number of ready keys for a given user.
            		if (cstate == FtDHkeyState.READY){
            			readyKeys.put(csip, readyKeys.get(csip) + 1);
            		}
            		
            		serverNonceSet.add(cnonce2);
            	}
            	
            	// If there are some keys that are on the server but not locally, delete
            	// them since there is no usage for them.
            	if (deleteServerNonce.isEmpty()==false){
            		Log.vf(TAG, "Removing server-only keys: %s", deleteServerNonce.size());
            		
            		// Initialize task an invoke main work method.
             		GenKeyParams gkp2 = new GenKeyParams();
             		gkp2.setMySip(par.getCreds().getUserSip());
             		gkp2.setStoragePass(par.getCreds().getUsrStoragePass());
             		gkp2.setDeleteNonce2List(new ArrayList<String>(deleteServerNonce));
             		
             		genKeyTask.deleteKeys(gkp2, getRand());
             		Log.v(TAG, "Server-only keys removed.");
            	}
            } else {
            	Log.v(TAG, "We have no keys on the server side.");
            }
            
            // Task may got canceled.
	        if (manuallyCanceled){
	        	Log.d(TAG, "Task was canceled");
	        	return null;
	        }
            
            // Scan for nonce2 that are stored locally but not on the server.
        	for(String dbNonce : nonceSet){
        		// Nonce is in local database but is not on the server, mark for deletion.
        		if (serverNonceSet.contains(dbNonce)==false){
        			deleteLocalNonce.add(dbNonce);
        		}
        	}
        	
        	// Delete local-only nonce2 right now.
        	if (deleteLocalNonce.isEmpty()==false){
        		Log.vf(TAG, "Deleting local-only keys: %s", deleteLocalNonce.size());
        		dhelper.removeDHKeys(new LinkedList<String>(deleteLocalNonce));
        	}
            
            // Generate DH keys part.
            // Only for users having valid certificate and in whitelist (can contact me).
            List<GenKeyForUser> ukList = new LinkedList<GenKeyForUser>(); 
            
            for(Entry<String, UserCert> e : userCerts.entrySet()){
            	final UserCert uc = e.getValue();
            	final String sip = e.getKey();
            	int numKeys = readyKeys.containsKey(sip) ? readyKeys.get(sip) : 0;
            	
            	Log.d(TAG, String.format("Phase 2, usr[%s] keys2gen[%d]", sip, par.getDhkeys() - numKeys));
            	
            	// If not in white-list or has invalid certificate - no DHkeys.
            	if (uc.inWhitelist==false || uc.certHash==null || uc.notBefore==null){
            		Log.vf(TAG, "Skipping, whitelist=%s; probably null cert", uc.inWhitelist);
            		continue;
            	}
            	
            	// If some keys are missing, generate new ones.
            	if (numKeys >= par.getDhkeys()){
            		Log.vf(TAG, "Skipping, enough keys, numKeys=%s", numKeys);
            		continue;
            	}
            	
            	GenKeyForUser uKey = new GenKeyForUser();
            	uKey.setNumKeys(par.getDhkeys() - numKeys);
            	uKey.setUserSip(sip);
            	uKey.setUserCert(uc.cert);
            	ukList.add(uKey);
            }
            
            // Task may got canceled.
	        if (manuallyCanceled){
	        	Log.d(TAG, "Task was canceled");
	        	return null;
	        }
     		
            // Param init
            GenKeyParams gkp = new GenKeyParams();
     		gkp.setMySip(par.getCreds().getUserSip());
     		gkp.setUserList(ukList);
     		
     		// Publishing start of generating process.
     		publishDHProgress(ukList, KeyGenStateEnum.STARTED);
     		
     		// If no identity is provided, load it from global data.
     		boolean identityOk = true;
 			int code = genKeyTask.loadIdentity(gkp, getRand());
 			if (code < 0){
 				identityOk=false;
 				Log.wf(TAG, "Cannot generate DH keys, identity cannot be loaded, code=%s", code);
 			}
     		
     		// Only if our identity is loaded properly.
     		if(identityOk){
     			// Main method for generating keys & storing to DB & upload.
     			Log.v(TAG, "Going to generate DH keys");
     			genKeyTask.setProgress(progress);
     			genKeyTask.genKeys(gkp);
     		} else {
     			publishDHProgress(ukList, KeyGenStateEnum.DONE);
     		}
            
            // this.publishProgress(new DefaultAsyncProgress(1.0, "Done"));
			Log.i(TAG, "Finished properly");
		} catch (Exception e) {
			Log.e(TAG, "Exception", e);			

			return e;
		}
		
		return null;
	}

	/**
	 * @return the onCompletedListener
	 */
	public OnDHKeySustainCompleted getOnCompletedListener() {
		return onCompletedListener;
	}

	/**
	 * @param onCompletedListener the onCompletedListener to set
	 */
	public void setOnCompletedListener(OnDHKeySustainCompleted onCompletedListener) {
		this.onCompletedListener = onCompletedListener;
	}

	/**
	 * @return the tv
	 */
	public TrustVerifier getTv() {
		return tv;
	}

	/**
	 * @param tv the tv to set
	 */
	public void setTv(TrustVerifier tv) {
		this.tv = tv;
	}    
    
	/**
	 * Class for storage in deleteInvalidKeys. 
	 * @author ph4r05
	 *
	 */
	private static class UserCert {
		public String certHash;
		public Date notBefore;
		public boolean inWhitelist;
		public X509Certificate cert;
		
		/* (non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result
					+ ((certHash == null) ? 0 : certHash.hashCode());
			result = prime * result
					+ ((notBefore == null) ? 0 : notBefore.hashCode());
			return result;
		}
		/* (non-Javadoc)
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			UserCert other = (UserCert) obj;
			if (certHash == null) {
				if (other.certHash != null)
					return false;
			} else if (!certHash.equals(other.certHash))
				return false;
			if (notBefore == null) {
				if (other.notBefore != null)
					return false;
			} else if (!notBefore.equals(other.notBefore))
				return false;
			return true;
		}
	}
	
	/**
	 * Sets cancel flag to true.
	 * Cancels key task if is not null.
	 */
	public void manualCancel(){
		this.manuallyCanceled = true;
		
		// If there is some key task initialized, cancel it also.
		if (genKeyTask!=null){
			genKeyTask.manualCancel();
		}
	}

	/**
	 * @return the progress
	 */
	public KeyGenProgressUpdatable getProgress() {
		return progress;
	}

	/**
	 * @param progress the progress to set
	 */
	public void setProgress(KeyGenProgressUpdatable progress) {
		this.progress = progress;
	}
	
	protected void publishDHProgress(KeyGenProgress prog){
		if (progress==null) return;
		progress.updateDHProgress(prog);
	}
	
	protected void publishDHProgress(List<KeyGenProgress> prog){
		if (progress==null) return;
		progress.updateDHProgress(prog);
	}
	
	protected void publishDHProgress(List<GenKeyForUser> list, KeyGenStateEnum state){
		if (progress==null) return;
		
		List<KeyGenProgress> prg = new LinkedList<KeyGenProgress>();
		final long when = System.currentTimeMillis();
		for(GenKeyForUser ul : list){
			final String sip = ul.getUserSip();
			if (TextUtils.isEmpty(sip)) continue;
			
			prg.add(new KeyGenProgress(sip, state, when));
		}
		
		publishDHProgress(prg);
	}
	
	protected void publishDHProgress(Collection<String> sips, KeyGenStateEnum state){
		if (progress==null) return;
		
		List<KeyGenProgress> prg = new LinkedList<KeyGenProgress>();
		final long when = System.currentTimeMillis();
		for(String sip : sips){
			if (TextUtils.isEmpty(sip)) continue;
			prg.add(new KeyGenProgress(sip, state, when));
		}
		
		publishDHProgress(prg);
	}
}


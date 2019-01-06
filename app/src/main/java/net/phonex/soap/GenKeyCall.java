package net.phonex.soap;

import android.content.ContentResolver;
import android.text.TextUtils;

import net.phonex.core.MemoryPrefManager;
import net.phonex.core.SipUri;
import net.phonex.core.SipUri.ParsedSipUriInfos;
import net.phonex.db.entity.DHOffline;
import net.phonex.ksoap2.SoapEnvelope;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.pub.parcels.KeyGenProgress;
import net.phonex.pub.parcels.KeyGenProgress.KeyGenStateEnum;
import net.phonex.pub.parcels.StoredCredentials;
import net.phonex.service.XService.KeyGenProgressUpdatable;
import net.phonex.soap.GenKeyParams.GenKeyForUser;
import net.phonex.soap.entities.FtAddDHKeysRequest;
import net.phonex.soap.entities.FtAddDHKeysResponse;
import net.phonex.soap.entities.FtNonceList;
import net.phonex.soap.entities.FtRemoveDHKeysRequest;
import net.phonex.soap.entities.FtRemoveDHKeysResponse;
import net.phonex.soap.entities.SipDatePair;
import net.phonex.soap.entities.SipDatePairList;
import net.phonex.soap.entities.SipList;
import net.phonex.ft.DHKeyHelper;
import net.phonex.ft.misc.DHKeyHolder;
import net.phonex.util.Log;
import net.phonex.util.crypto.CertificatesAndKeys.IdentityLoader;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;


/**
 * DH Key generation task.
 * Not 
 * @author ph4r05
 *
 */
public class GenKeyCall extends DefaultSOAPCall {
	public final static int CHECKPOINTS_NUMBER = 2; //how many progress checkpoints it needs
    public final static int MAX_KEYS_IN_ONE_MESSAGE = 12; // Maximal number of keys to upload in 1 SOAP message. Bucketing...
	public final static String TAG = "GenkeyTask";
	private KeyGenProgressUpdatable progress;
	private GenKeyParams par;
	
	/**
	 * Notifier interface on finished task.
	 * @author ph4r05
	 */
	public interface OnGenKeyTaskFinishedListener{
		public void onGenKeyTaskFinished(int errCode, Exception ex);
	}
	
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
	 * Empty constructor, without specifying callback listener.
	 */
	public GenKeyCall() {
		super();
	}

    @Override
    public void run() {
        // missing
    }

    /**
	 * Main work method:
	 * 	- generates new DH key
	 *  - stores DH key to the database
	 *  - uploads given key to the server (if upload failed, remove from database).
	 * @param mpar
	 * @return
	 */
	public Result genKeys(GenKeyParams mpar){
		Result mres = new Result();
		mres.ex=null;
		mres.code=0;
		
		this.manuallyCanceled = false;
		this.par = mpar;
		List<DHKeyHolder> keys = null;
		
		// Test configuration validity.
		if (par.getStoragePass()==null
				|| TextUtils.isEmpty(par.getMySip())
				|| par.getMyCert()==null
				|| par.getPrivKey()==null){
			
			throw new IllegalArgumentException("Invalid configuration provided, some fields are empty.");
		}
		
		final ArrayList<GenKeyForUser> uList = new ArrayList<GenKeyForUser>(mpar.getUserList());
		if (uList==null || uList.isEmpty()){
			return mres;
		}
		
		// HTTP transport - declare before TRY block to be able to 
		// extract response in catch block for debugging.
		try {
			/**
			 * Init SecureRandom, it may take some time, thus it is initialized in
			 * background task. It is also needed prior first SSL connection
			 * or key generation.
			 */
			initSoap(par.getStoragePass());
			
			// Get my domain
			ParsedSipUriInfos mytsinfo = SipUri.parseSipUri(SipUri.getCanonicalSipContact(par.getMySip(), true));
			final String domain = mytsinfo.domain;
			
    		//
    		// Generate given number of keys.
    		//
    		keys = new ArrayList<DHKeyHolder>();
            final List<GenKeyForUser> uDone = new ArrayList<GenKeyForUser>();
    		for(int cUser=0, mUser=uList.size(); cUser < mUser; cUser++){
                GenKeyForUser uKey = uList.get(cUser);
    			if (TextUtils.isEmpty(uKey.getUserSip())){
    				Log.w(TAG, "Empty user in genKeys");
    				continue;
    			}
    			
    			// Empty certificate -> cannot generate DH keys for him.
    			if (uKey.getUserCert()==null){
    				Log.w(TAG, "Empty certificate in genKeys");
    				publishDHProgress(new KeyGenProgress(uKey.getUserSip(), KeyGenStateEnum.DONE, System.currentTimeMillis(), true));
    				continue;
    			}
    			
    			// Task canceled?
    			if (manuallyCanceled){
    				break;
    			}
    			
    			Log.v(TAG, String.format("GenKey user[%s] numKeys[%d]", uKey.getUserSip(), uKey.getNumKeys()));
    			
    			// Generate DH keys per user.
	    		for(int curKey=0; curKey < uKey.getNumKeys() && !manuallyCanceled; curKey++){
	    			
	    			DHKeyHelper helper = new DHKeyHelper();
	    			helper.setCtxt(getContext());
	    			helper.setRand(getRand());
	    			
	    			helper.setMyCert(par.getMyCert());
	    			helper.setMySip(par.getMySip());
	    			helper.setPrivKey(par.getPrivKey());
	    			
	    			helper.setUserSip(uKey.getUserSip());
	    			helper.setSipCert(uKey.getUserCert());
	    			
	    			// Progress
	    			KeyGenProgress prg = new KeyGenProgress(uKey.getUserSip(), KeyGenStateEnum.GENERATING_KEY, System.currentTimeMillis());
	    			prg.setAlreadyGeneratedKeys(curKey);
	    			prg.setMaxKeysToGen(uKey.getNumKeys());
	    			publishDHProgress(prg);
	    			
	    			// Generates DH key and stores it to the database.
	    			DHKeyHolder keyHolder = helper.generateDHKey();
	    			keys.add(keyHolder);
	    			
	    			// Finish? -> move to generated state
	    			if ((curKey+1) >= uKey.getNumKeys()){
	    				prg = new KeyGenProgress(uKey.getUserSip(), KeyGenStateEnum.GENERATED, System.currentTimeMillis());
	    				prg.setAlreadyGeneratedKeys(curKey);
	    				prg.setMaxKeysToGen(uKey.getNumKeys());
	    				publishDHProgress(prg);
                        uDone.add(uKey);
	    			}
	    		}

                // Check if we have enough keys for sending or everything was done.
                if ((cUser+1) >= mUser || keys.size() >= MAX_KEYS_IN_ONE_MESSAGE){
                    Log.vf(TAG, "Uploading keys to the server, keys=%s", keys.size());

                    // If there were no keys generated, query makes no sense...
                    if (keys.isEmpty()){
                        Log.w(TAG, "Cannot add 0 keys, aborting.");
                        publishDHProgress(uList, KeyGenStateEnum.DONE);

                        mres.code=-3;
                        return mres;
                    }

                    uploadKeys(domain, keys);

                    // If here, everything is OK, mark given users as done.
                    publishDHProgress(uDone, KeyGenStateEnum.DONE);

                    // Clear keys buffer for next iteration.
                    keys.clear();
                    uDone.clear();
                }
    		}
    		
    		// Was canceled?
    		if (manuallyCanceled){
				dbDeleteKeys(keys);
				publishDHProgress(uList, KeyGenStateEnum.DONE);
				
				mres.code=2;
    			return mres;
			}

			Log.i(TAG, "Finished properly");
			
			// Publish server call state
    		publishDHProgress(uList, KeyGenStateEnum.DONE);
		} catch (Exception e) {
			Log.e(TAG, "Exception in genKeys", e);		
			
			// If some keys were generated, delete them.
			if (keys!=null && keys.isEmpty()==false){
				Log.vf(TAG, "Removing keys from DB, size=%s", keys.size());	
				dbDeleteKeys(keys);
			}
			
			// Publish server call state
    		publishDHProgress(uList, KeyGenStateEnum.DONE);

			mres.ex = e;
			return mres;
		}
		
		return mres;
	}

    /**
     * Upload given keys to the server.
     * @param domain
     * @param keys
     * @throws Exception
     */
    public void uploadKeys(String domain, List<DHKeyHolder> keys) throws Exception {
        // Fill in the request structure
        Set<String> srvrUsers = new HashSet<String>();
        FtAddDHKeysRequest dhReq = new FtAddDHKeysRequest();
        for(DHKeyHolder h : keys){
            dhReq.add(h.serverKey);
            srvrUsers.add(h.dbKey.getSip());
        }

        // Publish server call state
        publishDHProgress(srvrUsers, KeyGenStateEnum.SERVER_CALL_SAVE);

        // Create SOAP envelope
        SoapSerializationEnvelope soapEnvelope = new SoapSerializationEnvelope(SoapEnvelope.VER11);
        dhReq.register(soapEnvelope);
        new FtAddDHKeysResponse().register(soapEnvelope);
        soapEnvelope.setOutputSoapObject(dhReq);

        Object obj = SOAPHelper.simpleSOAPRequest(soapEnvelope,
                ServiceConstants.getDefaultURL(domain, getContext()),
                "ftAddDHKeysRequest", true);

        Log.inf(TAG, "Pure object response retrieved, class: %s; %s", obj.getClass().getCanonicalName(), obj.toString());
        if (!(obj instanceof FtAddDHKeysResponse)){
            Log.w(TAG, "Bad format of response from server - probably problem during unmarshaling");
            throw new IllegalArgumentException("Bad format of response");
        }

        final FtAddDHKeysResponse resp = (FtAddDHKeysResponse) obj;

        // If error code is somewhat wrong
        if (resp.getErrCode() < 0){
            Log.wf(TAG, "Error code signalizes some error, code: %s", resp.getErrCode());
            throw new IllegalArgumentException("Error code signalizes error.");
        }
    }
	
	/**
	 * Deletes keys from database using DHkeyHolder list.
	 * 
	 * @param keys
	 */
	public void dbDeleteKeys(List<DHKeyHolder> keys){
		ContentResolver cr = getContext().getContentResolver();
		for(DHKeyHolder h : keys){
			cr.delete(DHOffline.DH_OFFLINE_URI, 
					DHOffline.FIELD_NONCE2 + "=? AND " + DHOffline.FIELD_SIP + "=?", 
					new String[] {h.dbKey.getNonce2(), h.dbKey.getSip()});
		}
	}
	
	/**
	 * Loads identity from global resources (in-memory database) into parameters provided.
	 * Context has to be set.
	 * 
	 * @param par
	 * @return <0 on error (-1 = problem with on-memory, -2 = problem with priv key).
	 */
	public int loadIdentity(GenKeyParams par, SecureRandom rand){
		// 1. load shared info from in-memory database
		StoredCredentials creds = MemoryPrefManager.loadCredentials(getContext());
		if (creds.getUserSip()==null){
			return -1;
		}
		
		// 2. load identity
		IdentityLoader il = new IdentityLoader();
		il.setRand(rand);
		
		int code = il.loadIdentityKeys(getContext(), creds, false);
		if (code!=IdentityLoader.CODE_EXISTS){
			return -2;
		}
		
		// 3. transfer necessary data to parameters
		UserPrivateCredentials ucreds = il.getPrivateCredentials();
		
		par.setMySip(creds.getUserSip());
		par.setMyCert(ucreds.getCert());
		par.setPrivKey(ucreds.getPk());
		par.setStoragePass(creds.getUsrStoragePass());
		
		return 1;
	}
	
	/**
	 * Deletes DH keys from server for particular user.
	 * Fields needed to be initialized in mpar:
	 * 	- my sip.
	 *  - destination sip to delete keys - in user list (OPTIONAL).
	 *  - storage password for SSL. 
	 *  
	 *  If destination SIP is empty and nonce2 list is empty, all keys are removed.
	 *  
	 * @param mpar
	 * @param rand
	 * @return
	 */
	public Result deleteKeys(GenKeyParams mpar, SecureRandom rand){
		Result mres = new Result();
		mres.ex=null;
		mres.code=0;
		this.manuallyCanceled = false;
		
		// Check validity of input parameter.
		if (TextUtils.isEmpty(mpar.getMySip())
				|| TextUtils.isEmpty(mpar.getStoragePass())){
			
			throw new IllegalArgumentException("Invalid configuration provided, some fields are empty.");
		}
		
		// HTTP transport - declare before TRY block to be able to 
		// extract response in catch block for debugging.
		try {
			initSoap(mpar.getStoragePass());
			
			// Get my domain
			ParsedSipUriInfos mytsinfo = SipUri.parseSipUri(SipUri.getCanonicalSipContact(mpar.getMySip(), true));
			final String domain = mytsinfo.domain;
			
    		// Fill in the request structure
    		FtRemoveDHKeysRequest dhReq = new FtRemoveDHKeysRequest();
    		dhReq.setVersion(1);
    		
    		final List<GenKeyForUser> uList = mpar.getUserList();
    		final List<String> nList = mpar.getDeleteNonce2List();
    		
    		if ((uList==null || uList.isEmpty()) && (nList==null || nList.isEmpty())){
    			Log.v(TAG, "Going to delete all DH keys");
    			dhReq.setDeleteAll(true);
    			
    		} else {
    			// Will not delete all keys, there are more specific criteria.
    			dhReq.setDeleteAll(false);
    			
    			// User list is not empty?
    			if (uList!=null && uList.isEmpty()==false){
	    			SipList uList2 = new SipList();
	    			for(GenKeyForUser kUser : uList){
	    				Log.vf(TAG, "Going to delete all DH keys for user: %s", kUser.getUserSip());
	    				uList2.add(kUser.getUserSip());
	    			}
	    			
	    			dhReq.setUsers(uList2);
    			}
    			
    			// Nonce list is not empty?
    			if (nList!=null && nList.isEmpty()==false){
    				FtNonceList regNList = new FtNonceList();
    				for(String cn : nList){
    					Log.vf(TAG, "Going to delete nonce2: %s", cn);
    					regNList.add(cn);
    				}
    				
    				dhReq.setNonceList(regNList);
    			}
    		}
    		
    		// Create SOAP envelope
    		SoapSerializationEnvelope soapEnvelope = new SoapSerializationEnvelope(SoapEnvelope.VER11);
    		dhReq.register(soapEnvelope);
    		new FtRemoveDHKeysResponse().register(soapEnvelope);
    	    soapEnvelope.setOutputSoapObject(dhReq);
    	    
    	    Object obj = simpleSOAPRequest(soapEnvelope, 
    	    		ServiceConstants.getDefaultURL(domain, getContext()),
    				"ftRemoveDHKeysRequest", true);
            
            Log.inf(TAG, "Pure object response retrieved, class: %s; %s", obj.getClass().getCanonicalName(), obj.toString());
            if (!(obj instanceof FtRemoveDHKeysResponse)){
            	Log.w(TAG, "Bad format of response from server - probably problem during unmarshaling");
            	throw new IllegalArgumentException("Bad format of response");
            }
    		
            final FtRemoveDHKeysResponse resp = (FtRemoveDHKeysResponse) obj;
            Log.df(TAG, "Result code=%s", resp.getErrCode());
                        
			Log.i(TAG, "Finished properly");
		} catch (Exception e) {
			Log.e(TAG, "Exception in deleteKeys", e);

			mres.ex = e;
			return mres;
		}
		
		return mres;
	}
	
	/**
	 * Deletes DH keys from the server based on a given mapping UserSip -> Date.
	 * 
	 * Each entry in the map is inspected, if date for a given user is null,
	 * all DH keys for particular user will be removed, otherwise only 
	 * keys older (created) than specified date will be removed. 
	 * 
	 * Fields needed to be initialized in mpar:
	 * 	- my sip.
	 *  - storage password for SSL. 
	 *  
	 * @param mpar
	 * @param deleteOlderForUser
	 * @param rand
	 * @return
	 */
	public Result deleteKeys(GenKeyParams mpar, Map<String, Date> deleteOlderForUser, SecureRandom rand){
		Result mres = new Result();
		mres.ex=null;
		mres.code=0;
		this.manuallyCanceled = false;
		
		// Check validity of input parameter.
		if (TextUtils.isEmpty(mpar.getMySip())
				|| TextUtils.isEmpty(mpar.getStoragePass())){
			
			throw new IllegalArgumentException("Invalid configuration provided, some fields are empty.");
		}
		
		// If map is empty, do nothing.
		if (deleteOlderForUser==null || deleteOlderForUser.isEmpty()){
			return mres;
		}
		
		// HTTP transport - declare before TRY block to be able to 
		// extract response in catch block for debugging.
		try {
			initSoap(mpar.getStoragePass());
			
			// Get my domain
			ParsedSipUriInfos mytsinfo = SipUri.parseSipUri(SipUri.getCanonicalSipContact(mpar.getMySip(), true));
			final String domain = mytsinfo.domain;
			
			FtRemoveDHKeysRequest removeReq = new FtRemoveDHKeysRequest();
        	removeReq.setVersion(1);
        	removeReq.setDeleteAll(false);
        	
        	// Fill delete conditions w.r.t. map.
        	// If date is null, delete all DH keys for a given user
        	// otherwise delete only keys older than specified date.
        	SipList sipList         = new SipList();
        	SipDatePairList sipdate = new SipDatePairList();
        	for(Entry<String, Date> ce : deleteOlderForUser.entrySet()){
        		if (ce.getValue()==null){
        			// Null date -> remove all keys
        			sipList.add(ce.getKey());
        		} else {
        			// Date not null, remove only older than for a given user
        			SipDatePair p = new SipDatePair();
        			Calendar cal = Calendar.getInstance();
        			cal.setTime(ce.getValue());
        			
        			p.setSip(ce.getKey());
        			p.setDt(cal);
        			
        			sipdate.add(p);
        		}
        	}
        	
        	// At least one delete condition has to be non-empty.
        	if (sipList.isEmpty()==false || sipdate.isEmpty()==false){
        		if (sipList.isEmpty()==false){
        			removeReq.setUsers(sipList);
        		}
        		
        		if (sipdate.isEmpty()==false){
        			removeReq.setUserDateList(sipdate);
        		}
        		
        		// Create SOAP envelope
        		SoapSerializationEnvelope soapEnvelope = new SoapSerializationEnvelope(SoapEnvelope.VER11);
        		removeReq.register(soapEnvelope);
        		new FtRemoveDHKeysResponse().register(soapEnvelope);
        	    soapEnvelope.setOutputSoapObject(removeReq);
        	    
        	    Object obj = simpleSOAPRequest(soapEnvelope, 
        	    		ServiceConstants.getDefaultURL(domain, getContext()),
        				"ftRemoveDHKeysRequest", true);
                
                Log.inf(TAG, "Pure object response retrieved, class: %s; %s", obj.getClass().getCanonicalName(), obj.toString());
                if (!(obj instanceof FtRemoveDHKeysResponse)){
                	Log.w(TAG, "Bad format of response from server - probably problem during unmarshaling");
                	throw new IllegalArgumentException("Bad format of response");
                }
        		
                final FtRemoveDHKeysResponse resp = (FtRemoveDHKeysResponse) obj;
                Log.df(TAG, "Result code=%s", resp.getErrCode());
                
                mres.code = resp.getErrCode();
        	}
		} catch (Exception e) {
			Log.e(TAG, "Exception in deleteKeys time map", e);

			mres.ex = e;
			return mres;
		}
		
		return mres;
	}	
	
	/**
	 * Sets cancel flag to true.
	 */
	public void manualCancel(){
		this.manuallyCanceled = true;
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
}

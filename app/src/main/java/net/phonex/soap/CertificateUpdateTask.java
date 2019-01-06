package net.phonex.soap;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import net.phonex.core.Intents;
import net.phonex.core.MemoryPrefManager;
import net.phonex.db.entity.SipClist;
import net.phonex.core.SipUri;
import net.phonex.core.SipUri.ParsedSipUriInfos;
import net.phonex.db.DBBulkInserter;
import net.phonex.db.DBBulkUpdater;
import net.phonex.db.entity.UserCertificate;
import net.phonex.pub.parcels.CertUpdateParams;
import net.phonex.pub.parcels.CertUpdateProgress;
import net.phonex.pub.parcels.CertUpdateProgress.CertUpdateStateEnum;
import net.phonex.pub.parcels.StoredCredentials;
import net.phonex.service.XService;
import net.phonex.soap.entities.CertificateRequestElement;
import net.phonex.soap.entities.CertificateStatus;
import net.phonex.soap.entities.CertificateWrapper;
import net.phonex.soap.entities.GetCertificateRequest;
import net.phonex.soap.entities.GetCertificateResponse;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.android.StatusbarNotifications;
import net.phonex.util.crypto.CertificatesAndKeys;
import net.phonex.util.crypto.MessageDigest;
import net.phonex.util.crypto.pki.TrustVerifier;

import java.lang.ref.WeakReference;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;


/**
 * Class for updating certificates.
 * Supposed to be executed in XService using executor (runs on background).
 * 
 * @author ph4r05
 */
public class CertificateUpdateTask implements Runnable {
	private static final String THIS_FILE = "CertificateUpdateTask";
	public static final int CERT_CHECK_SIMUL = 50;
	
	final private WeakReference<XService> svc;
	
	private Context ctxt;
	private TrustVerifier tv;
	private String domain;
	
	private Map<String, CertCheckListEntry> usrMap;
	private Map<String, UserCertificate> certsMap;
	private Map<String, SipClist> dbusersMap;
	private Map<String, String> certificateHashes;
	private Set<String> checkSkip;
	
	private boolean showNotifications = false;
	private volatile boolean manualCancel = false;
	
	/**
     * List of user names for certificate check.
     */
    final private Queue<CertCheckListEntry> certCheckList = new ConcurrentLinkedQueue<CertCheckListEntry>();
    final private Map<String, CertUpdateProgress> certCheckProgress = new ConcurrentHashMap<String, CertUpdateProgress>();
	
	/**
     * Entry for certificate check for one user.
     * @author ph4r05
     *
     */
    public static class CertCheckListEntry {
    	public String usr;
    	public boolean policyCheck=true;
    	public CertUpdateParams params;
    	
    	// Temporary helper variables
    	public volatile boolean byPushNotification=false;
    	public volatile boolean cancelled=false;
    }
    	
    /**
     * Returns recent progress for certificate update.
     * @return
     */
    public List<CertUpdateProgress> getCertUpdateProgress(){
    	List<CertUpdateProgress> lst = new LinkedList<CertUpdateProgress>();    	
    	for(Iterator<Map.Entry<String, CertUpdateProgress>> it = certCheckProgress.entrySet().iterator(); it.hasNext(); ){
    		Map.Entry<String, CertUpdateProgress> e = it.next();
    		
    		final CertUpdateProgress le = e.getValue();
    		
    		lst.add(le);
    	}
    	
    	return lst;
    }
    
    /**
     * Adds users to the ToDo check list.
     * 
     * @param paramsList
     */
    public void addToCheckList(List<CertUpdateParams> paramsList){
    	// Add all params to the check list.
    	for(CertUpdateParams params : paramsList){
	    	CertCheckListEntry e = new CertCheckListEntry();
	    	e.usr = params.getUser();
	    	e.policyCheck = !params.isForceCheck();
	    	e.params = params;
	    	
	    	// Add this check entry to the queue. 
	    	certCheckList.add(e);
	    	
	    	// Add to progress monitor.
	    	certCheckProgress.put(e.usr, new CertUpdateProgress(e.usr, CertUpdateStateEnum.IN_QUEUE, System.currentTimeMillis()));
    	}
    }
    
	/**
	 * Initializes CertificateUpdateTask with XService (keeps in WeakReference).
	 * @param xService
	 */
	public CertificateUpdateTask(XService xService) {
		this.svc = new WeakReference<XService>(xService);
		ctxt = xService.getApplicationContext();
	}
	

	/**
	 * Update certificate update state for a single user.
	 * 
	 * @param user
	 * @param state
	 */
	protected void updateState(String user, CertUpdateStateEnum state){		
		certCheckProgress.put(user, new CertUpdateProgress(user, state, System.currentTimeMillis()));
	}
	
	protected void updateState(List<String> user, CertUpdateStateEnum state){
		final long when = System.currentTimeMillis();			
		for(String u : user){
			certCheckProgress.put(u, new CertUpdateProgress(u, state, when));
		}
	}
	
	/**
	 * Reset state of all updates to done.
	 */
	protected void resetState(){
		final long when = System.currentTimeMillis();
		
		for (Entry<String, CertUpdateProgress> e : certCheckProgress.entrySet()){
			e.getValue().setState(CertUpdateStateEnum.DONE);
			e.getValue().setWhen(when);
		}
	}
	
	/**
	 * Broadcast certificate update state.
	 */
	protected void bcastState(){
		final XService s = svc.get();
		if (s==null){
			return;
		}
		
		final ArrayList<CertUpdateProgress> cup = new ArrayList<CertUpdateProgress>(getCertUpdateProgress());
		Intent i = new Intent(Intents.ACTION_CERT_UPDATE_PROGRESS);
		i.putParcelableArrayListExtra(Intents.CERT_INTENT_PROGRESS, cup);
		MiscUtils.sendBroadcast(ctxt, i);
	}
	
	/**
	 * Generates list of users that should be skipped (to avoid DoS, too often checks)
	 * from the internal state.
	 */
	protected void generateSkipList(){
		//
		// Check certificate for a given users.
		// Build ignore list of the users to skip certificate check.
		//
		for(Entry<String, CertCheckListEntry> e : usrMap.entrySet()){
			final String sip = e.getKey();
			CertCheckListEntry ve = e.getValue();
			
			// Cancelled?
			if (ve.cancelled){
				Log.vf(THIS_FILE, "User cancelled: %s", sip);
				
				checkSkip.add(sip);
				continue;
			}
			
			if (ve.policyCheck==false) continue;
			if (ve.params!=null && ve.params.isForceCheck()) continue;
			if (certsMap.containsKey(sip)==false) continue;
			if (dbusersMap.containsKey(sip)==false) continue;
			
			// Load certificate and verify its correctness.
			final UserCertificate crt = certsMap.get(sip);
			if (crt.getCertificateStatus() == UserCertificate.CERTIFICATE_STATUS_OK){
				// If certificate is marked as OK but it not, do check it!
				if (!crt.isValidCertObj()) continue;
				// Cert is valid, store cert hash and validate stored certificate with its hash.
				certificateHashes.put(sip, crt.getCertificateHash());
			}
			
			// Mark this update as caused by push notification. (For alter updating last update time w.r.t. push).
			ve.byPushNotification=ve.params!=null && ve.params.isPushNotification();
			
			// If is from push, check anti-DoS policy.
			final SipClist cl = dbusersMap.get(sip);
			if (ve.params!=null && ve.params.isPushNotification() && cl.getPresenceLastCertUpdate()!=null){
				final Date lPresUpd = cl.getPresenceLastCertUpdate();
				final Integer numUpd = cl.getPresenceNumCertUpdate();
				
				// Too early certificate update ?
				Date boudnary = new Date(System.currentTimeMillis() - CertificatesAndKeys.CERTIFICATE_PUSH_TIMEOUT);
				if (lPresUpd.after(boudnary)){
					Log.d(THIS_FILE, String.format("Too early certificate push update for user [%s], last: %s", sip, lPresUpd));
					
					checkSkip.add(sip);
					continue;
				}
				
				// Too many times certificate update was performed during this day?
				if (numUpd!=null && MiscUtils.isToday(lPresUpd) && numUpd >= CertificatesAndKeys.CERTIFICATE_PUSH_MAX_UPDATES){
					Log.d(THIS_FILE, String.format("Too often certificate push update for user [%s], last: %s num: %d", sip, lPresUpd, numUpd));
					
					checkSkip.add(sip);
					continue;
				}
				
				// Do certificate update only if current cert differs.
				// If cert is OK and push matches, nothing to do.
				if (crt.getCertificateStatus() != UserCertificate.CERTIFICATE_STATUS_OK) continue;
				final String certHash = ve.params.getCertHash();
				
				// CertHash is not empty && matches -> skip
				if (TextUtils.isEmpty(certHash)==false){
					if (crt.getCertificateHash().startsWith(certHash)){
						Log.d(THIS_FILE, String.format("No need to update for user [%s], certhash holds", sip));
					
						checkSkip.add(sip);
						continue;
					} else {
						Log.d(THIS_FILE, String.format("Cert has does not match for user [%s]", sip));
						continue;
					}
				}
			} // End of if pushNotification valid
			
			// Check last query policy - if current check is too early from previous one, skip it.
			boolean recheck = CertificatesAndKeys.recheckCertificateForUser(crt);
			if (recheck==false){
				Log.df(THIS_FILE, "No need to re-check for user %s", sip);
				
				checkSkip.add(sip);
				continue;
			}
		} // End of foreach(), skipCheck init.
	}
	
	/**
	 * Builds get certificate request from internal state.
	 * @return
	 */
	protected GetCertificateRequest buildRequest(){
		GetCertificateRequest certReq = new GetCertificateRequest();
		for(Entry<String, CertCheckListEntry> e : usrMap.entrySet()){
			final String sip = e.getKey();
			if (checkSkip.contains(sip) || e.getValue().cancelled){
				// Certificate wont be updated.
				updateState(sip, CertUpdateStateEnum.DONE);
				continue;
			}
			
			CertificateRequestElement cre = new CertificateRequestElement();
			cre.setUser(sip);
			
			// Do we have certificate hash to verify against?
			if (certificateHashes.containsKey(sip)){
				cre.setCertificateHash(certificateHashes.get(sip));
				Log.vf(THIS_FILE, "SIP: %s; found among stored hashes", sip);
			}
			
			updateState(sip, CertUpdateStateEnum.SERVER_CALL);
			certReq.add(cre);
		}
		
		return certReq;
	}
	
	/**
	 * Cancels ongoing task.
	 * @param manualCancel the manualCancel to set
	 */
	public void setManualCancel(boolean manualCancel) {
		this.manualCancel = manualCancel;
		final XService s = svc.get();
		if (s==null){
			return;
		}
		
		if (manualCancel && showNotifications){
			s.getNotificationManager().cancelCertUpd();
		}
	}

	/**
	 * Main entry point for this task. 
	 */
	@Override
	public void run() {
		XService s = svc.get();
		if (s==null){
			return;
		}
		
		try {
			runInternal();
		} catch(Throwable e){
			Log.e(THIS_FILE, "Exception in certificate refresh.", e);
		}
		
		// Finished, finalize all internal statuses.
		resetState();
		bcastState();
		
		s = svc.get();
		if (s==null){
			return;
		}
		
		// Cancel Android notification.
		if (showNotifications){
			s.getNotificationManager().cancelCertUpd();
		}
	}
	
	private void runInternal() throws Exception {
		final XService s = svc.get();
		if (s==null){
			return;
		}
				
		tv = SSLSOAP.getDefaultTrustManager(ctxt);
		manualCancel = false;
		
		// Load shared info from in-memory database
		StoredCredentials creds = MemoryPrefManager.loadCredentials(ctxt);
		if (TextUtils.isEmpty(creds.getUserSip())){
			Log.e(THIS_FILE, "Cannot execute certificate update, empty credentials.");
			return;
		}
		
		// Get my domain - for SOAP connection.
		ParsedSipUriInfos mytsinfo = SipUri.parseSipUri(SipUri.getCanonicalSipContact(creds.getUserSip(), true));
		domain = mytsinfo.domain;
		
		// Display notification to the status bar
		if (showNotifications){
			final StatusbarNotifications notificationManager = s.getNotificationManager();
			synchronized(notificationManager){
				notificationManager.notifyCertificateUpdate();
			}
		}
		
		// Reset existing progress information.
		resetState();
		
		// Do while there are still some user names in certCheckList
		while(certCheckList.isEmpty()==false && manualCancel==false){
			if (svc.get()==null){
				return;
			}
			
			// Process X certificates simultaneously. By default process 10 certificates.
			final int qsize = certCheckList.size();
			final int toProcess = Math.min(qsize, CERT_CHECK_SIMUL);
			Log.vf(THIS_FILE, "CertSync: started, toProcess=%s", toProcess);
			
			List<String> users = new ArrayList<String>(toProcess);
			usrMap = new HashMap<String, CertCheckListEntry>(toProcess);
			certsMap = new HashMap<String, UserCertificate>(toProcess);
			dbusersMap = new HashMap<String, SipClist>(toProcess);
			certificateHashes = new HashMap<String, String>(toProcess);
			checkSkip = new HashSet<String>(toProcess);
			
			// Take toProcess number of users from waiting list to the separate list.
			for(int i=0; i<toProcess; i++){
				final CertCheckListEntry curUsr = certCheckList.poll();
				if (curUsr==null) break;
				
				usrMap.put(curUsr.usr, curUsr);
				users.add(curUsr.usr);
				updateState(curUsr.usr, CertUpdateStateEnum.STARTED);
			}
			
			// Load stored certificates for given users.
			for(UserCertificate c : CertificatesAndKeys.getRemoteCertificates(ctxt, users)){
				certsMap.put(c.getOwner(), c);
			}
			
			// Load contact list entries for given users.
			for(SipClist u : SipClist.getProfileFromDbSip(ctxt, users, SipClist.FULL_PROJECTION)){
				dbusersMap.put(u.getSip(), u);
			}
			
			// Generate list of SIPs to skip in this, w.r.t. DoS policy.
			generateSkipList();
			
			//
			// Real certificate check for given entries.
			//
			// Get all certificates for users.
            // If certificate is already stored in database, validate it with hash only
            // to save bandwidth.
        	GetCertificateRequest certReq = buildRequest();
        	bcastState();
			
			// Do not invoke SOAP call if there is nothing to check.
			if (certReq.isEmpty()){
				Log.v(THIS_FILE, "Nothing to check.");
				continue;
			}
			
			// Manual cancellation -> set users as done & exit.
			if (manualCancel){
				updateState(users, CertUpdateStateEnum.DONE);
				break;
			}
			
			// SOAP GetCertificateRequest.
			Log.vf(THIS_FILE, "CertSync: going to call server, size=%s", certReq.size());
			CertificateRefreshCall crtTask = new CertificateRefreshCall(ctxt);
			crtTask.initSoap(creds.getUsrStoragePass());
			GetCertificateResponse respc = (GetCertificateResponse) crtTask.soapGetCertificateRequest(certReq, domain);
			if (respc==null){
				Log.w(THIS_FILE, "SOAP response is null or invalid.");
				break;
			}
    		
			StringBuilder sb = new StringBuilder();	
			sb.append("Certificates returned: ").append(respc.size()).append("\n");
			bcastState();
			
			// Real certificate update. If yes, trigger DH Key update later.
			boolean newCert = false;

			// Prepare DB bulk updater object for certificate query time update. This operation is invoked
			// quite often but SQLite is not performing good for updating large number of records one by one.
			final Date lastUpdateDate = new Date();
			ContentValues dataUpdate = new ContentValues();
			dataUpdate.put(UserCertificate.FIELD_DATE_LAST_QUERY, UserCertificate.formatDate(lastUpdateDate));
			dataUpdate.put(UserCertificate.FIELD_CERTIFICATE_STATUS, UserCertificate.CERTIFICATE_STATUS_OK);
			DBBulkUpdater certQueryTimeUpdater = new DBBulkUpdater(ctxt.getContentResolver(), UserCertificate.CERTIFICATE_URI, UserCertificate.FIELD_OWNER, dataUpdate);

			// Prepare DB bulk inserter object for inserting a new certicate to the database.
			DBBulkInserter certInserter = new DBBulkInserter(ctxt.getContentResolver(), UserCertificate.CERTIFICATE_URI);
			certInserter.setOperationThreshold(50);

			// Processing SOAP response one-by-one.
			for (CertificateWrapper wr : respc) {
				if (wr == null) continue;
				String user = wr.getUser();
				final CertCheckListEntry ve = usrMap.get(user);
				final SipClist cl = dbusersMap.get(user);
				
				updateState(user, CertUpdateStateEnum.POST_SERVER_CALL);
				
				// If updated by push notification, update database anti-DoS statistics.
				if (ve!=null && ve.byPushNotification){
					int newUpdateNum = 0;
					
					// If update is in the same day as the previous one, increment update counter.
					if (cl.getPresenceLastCertUpdate()!=null && cl.getPresenceNumCertUpdate()!=null){
						if (MiscUtils.isToday(cl.getPresenceLastCertUpdate())){
							newUpdateNum = cl.getPresenceNumCertUpdate() + 1;
						}
					}
					
					ContentValues dataToInsert = new ContentValues();  
					dataToInsert.put(SipClist.FIELD_PRESENCE_LAST_CERT_UPDATE, lastUpdateDate.getTime());
					dataToInsert.put(SipClist.FIELD_PRESENCE_NUM_CERT_UPDATE, newUpdateNum);
					ctxt.getContentResolver().update(SipClist.CLIST_URI, dataToInsert, SipClist.FIELD_SIP + "=?", new String[] { user });
					Log.vf(THIS_FILE, "Certificate for user: %s; updated push statistics.", user);
				}
				
				sb.append("CRT: user: ").append(user).append("; status: ").append(wr.getStatus()).append("\n");
				updateState(user, CertUpdateStateEnum.SAVING);
				
				// Test if we provided some certificate. If yes, look on certificate status. 
				// If status = OK then update database (last query), otherwise delete record
				// because the new one with provided answer will be inserted afterwards.
				if (certificateHashes.containsKey(user)){
					CertificateStatus providedStatus = wr.getProvidedCertStatus();
					Log.vf(THIS_FILE, "Provided status for user: %s; status: %s", user, providedStatus);
					
					// invalid? Delete certificate then
					if (providedStatus == CertificateStatus.OK){
						certQueryTimeUpdater.add(user);
						Log.vf(THIS_FILE, "Certificate for user: %s; updated in database (query time also)", user);
						
						// We don't have to continue, certificate is valid -> move to next user.
						updateState(user, CertUpdateStateEnum.DONE);
						bcastState();
						continue;
					} else {
						// something is wrong with stored certificate, 
						// deleting from certificate database.
						try {
							int deleteResult = CertificatesAndKeys.removeRemoteCertificate(ctxt, user);
							Log.df(THIS_FILE, "Certificate for user [%s] removed; int: %s", user, deleteResult);
						} catch(Exception e){
							Log.ef(THIS_FILE, e, "Exception during removing invalid certificate for: %s", user);
						}
					}
				}
				
				// If we are here then 
				//	a) user had no certificate stored
				//	b) or user had certificate stored, but was invalid
				// thus process this result - new certificate should be provided or error code if
				// something is wrong with certificate on server side (missing, invalid, revoked).
				byte[] cert = wr.getCertificate();
				try {
					// Store certificate to database in each case (invalid vs. ok), both is
					// useful to know. We than have fresh data stored in database (no need to re-query
					// in case of error).
					UserCertificate crt2db = new UserCertificate();
					crt2db.setDateCreated(lastUpdateDate);
					crt2db.setDateLastQuery(lastUpdateDate);
					crt2db.setCertificateStatus(wr.getStatus());
					crt2db.setOwner(user);
					
					// Returned certificate is valid, process & store it.
					if (wr.getStatus() == CertificateStatus.OK && cert != null && cert.length > 0) {
						X509Certificate realCert = CertificatesAndKeys.buildCertificate(cert);
						sb.append("Certificate: ").append(realCert.toString()).append("\n\n");
						
						// check CN match
			         	String CNfromCert = CertificatesAndKeys.getCertificateCN(realCert);
			         	if (!user.equals(CNfromCert)){
			         		Log.e(THIS_FILE, "Security alert! Server returned certificate with different CN!");
			         		
			         		updateState(user, CertUpdateStateEnum.DONE);
			         		bcastState();
			         		continue;
			         	} else {
			         		Log.w(THIS_FILE, "Certificate CN matches");
			         	}
						
						// Verify new certificate with trust verifier
						try {
							tv.checkTrusted(realCert);
						} catch(Exception e){
							Log.w(THIS_FILE, "Certificate was not verified", e);
							
							updateState(user, CertUpdateStateEnum.DONE);
							bcastState();
							continue;
						}
						
						// Store certificate to database.
						// We now need to compute certificate digest.
						String certificateHash = MessageDigest.getCertificateDigest(realCert);
						Log.vf(THIS_FILE, "Certificate digest computed: %s", certificateHash);
						
						crt2db.setCertificate(realCert.getEncoded());
						crt2db.setCertificateHash(certificateHash);
						
						newCert=true;
					}
					
					// store result of this query to DB. Can also have error code - usable not to query for
					// certificate too often.
					certInserter.add(crt2db.getDbContentValues());
				} catch (Exception e) {
					sb.append("[cannot decode certificate]");
				}
				
				updateState(user, CertUpdateStateEnum.DONE);
				bcastState();
			} // End of foreach(SoapGetCertResponse)

			// Perform certificate update in a bulk.
			certInserter.finish();
			certQueryTimeUpdater.finish();
			
			// If new certificate was provided, invoke DH key update
			if (newCert){
				Log.d(THIS_FILE, "CertRefresh, cert modified, trigger dh key update");
				MiscUtils.sendBroadcast(ctxt, new Intent(Intents.ACTION_TRIGGER_DHKEY_SYNC));
			}
			
			Log.vf(THIS_FILE, "CertSync: log=%s", sb.toString());
		} // End of while(certQueue==empty)
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
	 * @return the showNotifications
	 */
	public boolean isShowNotifications() {
		return showNotifications;
	}

	/**
	 * @param showNotifications the showNotifications to set
	 */
	public void setShowNotifications(boolean showNotifications) {
		this.showNotifications = showNotifications;
	}
	
	/**
	 * @return the certCheckList
	 */
	public Queue<CertCheckListEntry> getCertCheckList() {
		return certCheckList;
	}

	/**
	 * @return the certCheckProgress
	 */
	public Map<String, CertUpdateProgress> getCertCheckProgress() {
		return certCheckProgress;
	}
}


package net.phonex.soap;

import android.content.ContentValues;
import android.content.Context;
import android.text.TextUtils;

import net.phonex.core.SipUri;
import net.phonex.core.SipUri.ParsedSipContactInfos;
import net.phonex.db.entity.UserCertificate;
import net.phonex.ksoap2.SoapEnvelope;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.service.XService;
import net.phonex.soap.entities.CertificateRequestElement;
import net.phonex.soap.entities.CertificateStatus;
import net.phonex.soap.entities.CertificateWrapper;
import net.phonex.soap.entities.GetCertificateRequest;
import net.phonex.soap.entities.GetCertificateResponse;
import net.phonex.util.Log;
import net.phonex.util.crypto.CertificatesAndKeys;
import net.phonex.util.crypto.MessageDigest;
import net.phonex.util.crypto.pki.TrustVerifier;

import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;


public class CertificateRefreshCall extends DefaultSOAPCall {
	protected static final String THIS_FILE="CertificateRefreshCall";
    
    /**
     * Current task status for certificate refresh.
     * Used for initialization in cert check phases.
     */
    private LoadTaskStatus taskStatus = null;

	public CertificateRefreshCall(Context ctxt) {
		super(ctxt);
		taskStatus = new LoadTaskStatus();
	}

    @Override
    public void run() {
        // missing
    }

    /**
	 * Re-check certificate 
	 * 
	 * @param remoteSip
	 * @param domain
	 * @param existingCertHash2recheck
	 * @return
	 * @throws Exception 
	 */
	public Boolean recheckCertificate(String remoteSip, String domain, String existingCertHash2recheck, char[] storagePass) throws Exception{
		return recheckCertificate(remoteSip, domain, existingCertHash2recheck, storagePass, true);
	}
	
	/**
	 * Re-check certificate of a particular user for validity. 
	 * If certificate hash is provided, certificate freshness is checked against this 
	 * given hash thereby saving bandwidth (if cert. is OK, server responds with "OK" message).
	 * If hash is not provided, server sends user certificate and freshness / validity is 
	 * checked against stored local copy, if exists. 
	 * 
	 * @param remoteSip	User whose certificate should be checked.
	 * @param domain					
	 * @param existingCertHash2recheck	OPTIONAL 
	 * @param storagePass	Storage password for SOAP KeyStore (HTTPS)
	 * @param allowDhKey	If true && certificate was changed, DH Key service is invoked. 
	 * @return
	 * @throws Exception 
	 */
	public Boolean recheckCertificate(String remoteSip, String domain, String existingCertHash2recheck, char[] storagePass, boolean allowDhKey) throws Exception{
		boolean certModified = false;
		
		getSoap().initSoap(storagePass);
		
		//
		// prepare SOAP GetCertificateRequest
		//
    	GetCertificateRequest certReq = new GetCertificateRequest();
    	CertificateRequestElement cre = new CertificateRequestElement();
    	cre.setUser(remoteSip);
    	
    	// If certificate hash is provided, it is checked against its hash (saves bandwidth).
    	if (!TextUtils.isEmpty(existingCertHash2recheck)){
    		cre.setCertificateHash(existingCertHash2recheck);
    	}
    	certReq.add(cre);
    		
    	//
		// do SOAP call
    	//
    	final GetCertificateResponse respc = soapGetCertificateRequest(certReq, domain);
		if (respc == null){
			return null;
		}
			
		//
		// Process response
		//
		for (CertificateWrapper wr : respc) {
			if (wr == null) continue;
			String user = wr.getUser();
			if (!user.equalsIgnoreCase(remoteSip)) continue;
			
			// test if we provided some certificate. If yes, look on certificate status.
			// If status = OK then update database (last query), otherwise delete record
			// because the new one with provided answer will be inserted afterwards.
			if (existingCertHash2recheck!=null){
				CertificateStatus providedStatus = wr.getProvidedCertStatus();
				Log.vf(THIS_FILE, "Provided status for user: %s; status: %s", user, providedStatus);
				
				// invalid? Delete certificate then
				if (providedStatus == CertificateStatus.OK){
					ContentValues dataToInsert = new ContentValues();  
					dataToInsert.put(UserCertificate.FIELD_DATE_LAST_QUERY, UserCertificate.formatDate(new Date()));
					dataToInsert.put(UserCertificate.FIELD_CERTIFICATE_STATUS, UserCertificate.CERTIFICATE_STATUS_OK);
					String where = UserCertificate.FIELD_OWNER + "=?";
					String[] whereArgs = new String[] { user };
					this.getContext().getContentResolver().update(UserCertificate.CERTIFICATE_URI, dataToInsert, where, whereArgs);
					Log.vf(THIS_FILE, "Certificate for user: %s; updated in database (query time also)", user);
					
					// We don't have to continue, certificate is valid -> move to next user.
					break;
				} else {
					// something is wrong with stored certificate, 
					// deleting from certificate database.
					try {
						certModified = true;
						taskStatus.remoteCertX509 = null;
						String selection = UserCertificate.FIELD_OWNER + "=?";
						String[] selectionArgs = new String[] { user };
						int deleteResult = this.getContext().getContentResolver().delete(UserCertificate.CERTIFICATE_URI, selection, selectionArgs);
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
				crt2db.setDateCreated(new Date());
				crt2db.setDateLastQuery(new Date());
				crt2db.setCertificateStatus(wr.getStatus());
				crt2db.setOwner(user);
				
				// Returned certificate is valid, process & store it.
				if (wr.getStatus() == CertificateStatus.OK && cert != null && cert.length > 0) {
					X509Certificate realCert = CertificatesAndKeys.buildCertificate(cert);
					
					// Verify new certificate with trust verifier
					TrustVerifier tv = SSLSOAP.getDefaultTrustManager(getContext());
		         	boolean certOK = CertificatesAndKeys.verifyClientCertificate(realCert, false, tv, null, user);
		         	if (!certOK){
		         		Log.w(THIS_FILE, "Certificate verification failed, cannot add!");
		         		return false;
		         	}
					
					// Store certificate to database.
					// We now need to compute certificate digest.
					String certificateHash = MessageDigest.getCertificateDigest(realCert);
					crt2db.setCertificate(realCert.getEncoded());
					crt2db.setCertificateHash(certificateHash);
					taskStatus.remoteCertX509 = realCert;
					Log.df(THIS_FILE, "New certificate provided by server, valid: %s", realCert);
				}
				
				// store result of this query to DB. Can also have error code - usable not to query for
				// certificate too often. 
				certModified = true;
				this.getContext().getContentResolver().insert(UserCertificate.CERTIFICATE_URI, crt2db.getDbContentValues());
				Log.d(THIS_FILE, "New certificate stored");
			} catch (Exception e) {
				taskStatus.remoteCertX509 = null;
				Log.d(THIS_FILE, "Certificate processing error: ", e);
			}
		}
		
		// Certificate modified - trigger DH key update task
		// TODO: refactor to listeners.
		if (certModified && allowDhKey){
			Log.d(THIS_FILE, "CertRefresh, cert modified, trigger dh key update");
			
			XService.triggerDHKeyUpdate(getContext());
		}
		
		return true;
	}
	
	/**
	 * SOAP request for GetCertificateRequest.
	 * 
	 * @param certReq
	 * @param domain
	 * 
	 * @return GetCertificateResponse
	 * @throws Exception
	 */
	public GetCertificateResponse soapGetCertificateRequest(GetCertificateRequest certReq, String domain) throws Exception {
		
		// Create SOAP envelope.
    	SoapSerializationEnvelope soapEnvelope = new SoapSerializationEnvelope(SoapEnvelope.VER11);
		certReq.register(soapEnvelope); 
		new GetCertificateResponse().register(soapEnvelope);
	    soapEnvelope.setOutputSoapObject(certReq);
	    
        // Soap call.
		Object obj = SOAPHelper.simpleSOAPRequest(soapEnvelope, 
				ServiceConstants.getDefaultURL(domain, ctxt),
				"certificateGetRequest", true);
		
		if (obj==null || !(obj instanceof GetCertificateResponse)){
			Log.ef(THIS_FILE, "Unexpected soap response: %s", obj);
			return null;
		}
		
		return (GetCertificateResponse) obj;
	}
	
	/**
	 * Determines whether certificate re-check is needed.
	 * If yes, certificate is pre-loaded to the internal state of this
	 * object to continue with certificate refresh.
	 * 
	 * Certificate may be old, missing or invalid.
	 * 
	 * @return
	 * @throws CertificateException 
	 * @throws CertificateEncodingException 
	 */
	public boolean isCertificateRecheckNeeded(final CertificateRefreshParams par) throws CertificateEncodingException, CertificateException{
		final String remoteSip = SipUri.getCanonicalSipContact(par.getSip(), false);
	    final ParsedSipContactInfos parsedUri = SipUri.parseSipContact(remoteSip);
	    if (parsedUri==null || parsedUri.domain==null || TextUtils.isEmpty(parsedUri.domain)){
	    	Log.wf(THIS_FILE, "Invalid parameters in isCertificateRecheckNeeded() for sip [%s]", par.getSip());
	    	return false;
	    }
	
    	boolean recheckNeeded=false;
    	taskStatus.params = par;
    	taskStatus.existingCertHash2recheck = par.getExistingCertHash2recheck();
    	taskStatus.sip = remoteSip;
    	taskStatus.domain = parsedUri.domain;
    	
    	// load certificate for remoteFrom
    	UserCertificate sipRemoteCert = CertificatesAndKeys.getRemoteCertificate(getContext(), remoteSip);
    	if (sipRemoteCert!=null){
    		taskStatus.remoteCert = sipRemoteCert;
    		Date lastQuery = sipRemoteCert.getDateLastQuery();
    		
        	// is certificate stored in database OK?
        	if (sipRemoteCert.getCertificateStatus() == UserCertificate.CERTIFICATE_STATUS_OK){
        		// certificate is valid, maybe we still need some re-check (revocation status for example)
        		Date boudnary = new Date(System.currentTimeMillis() - CertificatesAndKeys.CERTIFICATE_OK_RECHECK_PERIOD);
        		recheckNeeded = lastQuery.before(boudnary) || par.isForceRecheck();
        		if (!recheckNeeded){
        			try {
						taskStatus.remoteCertX509 = sipRemoteCert.getCertificateObj();
        				Log.d(THIS_FILE, "Certificate stored locally, valid, recheck: false");
        			} catch(Exception ex){
        				Log.e(THIS_FILE, "Cannot parse stored certificate");
        				recheckNeeded=true;
        				taskStatus.existingCertHash2recheck=null;
        			}
        		} else {
        			// re-check is needed, time of trust expired, provide certificate hash to verify
        			Log.d(THIS_FILE, "Certificate stored locally, valid, recheck: true");
        			taskStatus.existingCertHash2recheck = sipRemoteCert.getCertificateHash();
        			
        			// set this now, if something is wrong with certificate, null will be set
        			taskStatus.remoteCertX509 = sipRemoteCert.getCertificateObj();
        		}
        	} else {
        		// Certificate is invalid, missing or revoked or broken somehow.
        		// should re-check be performed?
        		Date boudnary = new Date(System.currentTimeMillis() - CertificatesAndKeys.CERTIFICATE_NOK_RECHECK_PERIOD);
        		recheckNeeded = lastQuery.before(boudnary) || par.isForceRecheck();
        		taskStatus.existingCertHash2recheck=null;
        		
        		Log.df(THIS_FILE, "Certificate stored locally, invalid, recheck: %s; record=%s; certificateStatus=%s", 
        		        recheckNeeded, 
        		        sipRemoteCert.toString(), 
        		        sipRemoteCert.getCertificateStatus());
        	}
    	} else {
    		// certificate not found in database: re-query it
    		Log.d(THIS_FILE, "Certificate for remote user is not stored locally, loading from server");
    		recheckNeeded = true;
    		taskStatus.existingCertHash2recheck=null;
    	}
    	
    	return recheckNeeded;
	}
	
	/**
	 * Performs certificate check, uses initialized state stored in
	 * taskStatus, initialized by {@link #isCertificateRecheckNeeded(CertificateRefreshParams)}.
	 * @throws Exception 
	 */
	public Boolean checkCertificate() throws Exception{
		return this.recheckCertificate(
				taskStatus.sip, 
				taskStatus.domain, 
				taskStatus.existingCertHash2recheck, 
				taskStatus.params.getStoragePass().toCharArray(),
				taskStatus.params.isAllowDhKeyRefreshOnCertChange());
	}
	
	/**
	 * @return the taskStatus
	 */
	public LoadTaskStatus getTaskStatus() {
		return taskStatus;
	}
	
	public String getRemoteSip() {
		if (taskStatus==null) return null;
		return taskStatus.sip;
	}
	
	public UserCertificate getRemoteCert(){
		if (taskStatus==null) return null;
		return taskStatus.remoteCert;
	}
	
	public X509Certificate getX509Cert(){
		if (taskStatus==null) return null;
		return this.taskStatus.remoteCertX509;
	}
	
	public int getStatusCode(){
		return this.taskStatus.statusCode;
	}

	/**
  	 * Results from async task are stored here (user certificate, decryption).
  	 * Internal state. 
  	 * 
  	 */
    public static class LoadTaskStatus{
    	public boolean canceled=false;
    	public int statusCode=0;
    	public UserCertificate remoteCert=null;
    	public X509Certificate remoteCertX509=null;
    	public String existingCertHash2recheck;
    	public String sip;
    	public String domain;
    	
    	public CertificateRefreshParams params;
    }
}

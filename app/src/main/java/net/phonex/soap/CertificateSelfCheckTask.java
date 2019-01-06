package net.phonex.soap;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import net.phonex.core.Intents;
import net.phonex.core.MemoryPrefManager;
import net.phonex.core.SipUri;
import net.phonex.core.SipUri.ParsedSipUriInfos;
import net.phonex.pub.parcels.StoredCredentials;
import net.phonex.service.XService;
import net.phonex.soap.entities.CertificateRequestElement;
import net.phonex.soap.entities.CertificateStatus;
import net.phonex.soap.entities.CertificateWrapper;
import net.phonex.soap.entities.GetCertificateRequest;
import net.phonex.soap.entities.GetCertificateResponse;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.crypto.pki.TrustVerifier;

import java.lang.ref.WeakReference;

/**
 * Class for self certificate check (check if user has authenticated on other device)
 * 
 * @author miroc
 */
public class CertificateSelfCheckTask implements Runnable {
	private static final String TAG = "CertificateSelfCheckTask";

    final private WeakReference<XService> svc;
    private final String certHash;
    private final String sip;

    private Context ctxt;
	private TrustVerifier tv;
	private String domain;

	/**
	 * Initializes CertificateUpdateTask with XService (keeps in WeakReference).
     * @param xService
     * @param certHash
     * @param sip
     */
	public CertificateSelfCheckTask(XService xService, String certHash, String sip) {
        this.certHash = certHash;
        this.sip = sip;
        this.svc = new WeakReference<>(xService);
		ctxt = xService.getApplicationContext();
	}
	
	/**
	 * Builds get certificate request from internal state.
	 * @return
	 */
	protected GetCertificateRequest buildRequest(String sip, String certificateHash){
		GetCertificateRequest certReq = new GetCertificateRequest();

		CertificateRequestElement cre = new CertificateRequestElement();
		cre.setUser(sip);
        cre.setCertificateHash(certificateHash);
		certReq.add(cre);
		
		return certReq;
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
			Log.e(TAG, "Exception in certificate refresh.", e);
		}
	}

    private void runInternal() throws Exception {
        Log.vf(TAG, "runInternal");
        GetCertificateRequest certificateRequest = buildRequest(sip, certHash);

        StoredCredentials creds = MemoryPrefManager.loadCredentials(ctxt);
		if (TextUtils.isEmpty(creds.getUserSip())){
			Log.e(TAG, "Cannot execute certificate update, empty credentials.");
			return;
		}

		ParsedSipUriInfos mySipInfo = SipUri.parseSipUri(SipUri.getCanonicalSipContact(creds.getUserSip(), true));
		domain = mySipInfo.domain;

        CertificateRefreshCall crtTask = new CertificateRefreshCall(ctxt);
		crtTask.initSoap(creds.getUsrStoragePass());
		GetCertificateResponse response = crtTask.soapGetCertificateRequest(certificateRequest, domain);
        Log.vf(TAG, "Received soap response; [%s]", response);

		if (response==null){
			Log.w(TAG, "SOAP response is null or invalid.");
			return;
		}

        if (response.size() != 1){
            Log.ef(TAG, "SOAP response contains no certificate");
            return;
        }

        CertificateWrapper certificateWrapper = response.get(0);
        CertificateStatus status = certificateWrapper.getProvidedCertStatus();
        if (status != CertificateStatus.OK){
            // current certificate we have is not valid anymore (someone else has logged in on other device), we have to log out
            sendLogoutIntent();
        }
    }

    private void sendLogoutIntent() {
        // TODO not working properly yet!!!!!



        Log.inf(TAG, "sendLogoutIntent");
//        PreferencesManager preferencesManager = new PreferencesManager(ctxt);
//        preferencesManager.setBoolean(PhonexConfig.APP_CLOSED_BY_USER, true);

        XService xService = svc.get();
        if (xService!=null){
            Intent intent = new Intent(Intents.ACTION_LOGOUT);
            intent.putExtra(Intents.EXTRA_LOGOUT_SECOND_DEVICE_DETECTED, true);
			MiscUtils.sendBroadcast(xService, intent);
        }

    }
}
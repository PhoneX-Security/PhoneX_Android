package net.phonex.soap;

import android.content.ContentUris;
import android.content.Context;

import net.phonex.core.MemoryPrefManager;
import net.phonex.db.entity.SipProfile;
import net.phonex.inapp.ReloadPurchasesService;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.license.LicenseInformation;
import net.phonex.login.AuxJsonData;
import net.phonex.pub.parcels.StoredCredentials;
import net.phonex.service.XService;
import net.phonex.soap.entities.AccountInfoV1Request;
import net.phonex.soap.entities.AccountInfoV1Response;
import net.phonex.util.Log;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

/**
 * Task for retrieving current account info (license type + expiration)
 * 
 * @author miroc
 */
public class LicenseCheckCall implements Runnable {
	private static final String TAG = "LicenseCheckCall";

    final private WeakReference<XService> svc;

	private SOAPHelper soapHelper;
    private Context ctxt;

	public LicenseCheckCall(XService xService) {
        this.svc = new WeakReference<>(xService);
		ctxt = xService.getApplicationContext();

		soapHelper = new SOAPHelper(ctxt);
	}
	
	/**
	 * Builds get certificate request from internal state.
	 * @return
	 */
	protected AccountInfoV1Request buildRequest(){
		AccountInfoV1Request req = new AccountInfoV1Request();
		// targetUser null means current user is taken
		return req;
	}

	@Override
	public void run() {
		XService s = svc.get();
		if (s==null){
			return;
		}
		
		try {
			runInternal();
		} catch (SOAPException e){
			Log.wf(TAG, e, "SoapException in license check task, rescheduling.");
			// as we can recover from soap exception, do it and reschedule the task
			s = svc.get();
			if (s!=null){
				s.scheduleRunnableAfterConnectivityIsOn(this);
			}
		} catch(Exception e){
			Log.e(TAG, "Exception in license check task", e);
		}
	}

    private void runInternal() throws SOAPException, InstantiationException, IllegalAccessException, UnrecoverableKeyException, CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        Log.vf(TAG, "runInternal");
		StoredCredentials credentials = MemoryPrefManager.loadCredentials(ctxt);
		soapHelper.initSoap(credentials.getUsrStoragePass());

		AccountInfoV1Request request = buildRequest();
		SoapSerializationEnvelope envelope = soapHelper.reqisterRequest(request, AccountInfoV1Response.class);

		AccountInfoV1Response response = (AccountInfoV1Response) soapHelper.makeSoapRequest(envelope, ServiceConstants.getDefaultURL(credentials.retrieveDomain()), "licenseCheck", true);
		LicenseInformation licenseInformation = new LicenseInformation(response);

		// process auxJson permission policies
		boolean policyUpdated = AuxJsonData.fromAuxJsonString(response.getAuxJSON()).processAppServerPolicy(ctxt);
		Log.df(TAG, "policyUpdater = %s", policyUpdated);
		if (policyUpdated){
			SipProfile account = SipProfile.getProfileFromDbId(ctxt, SipProfile.USER_ID, SipProfile.FULL_PROJECTION);

			if (account == null) {
				Log.wf(TAG, "runInternal; account is null");
				return;
			}


			// show notification + make recurrent subscription check
			XService s = svc.get();
			if (s!=null){
				ReloadPurchasesService.initCheck(s.getPrefs(), s, false);

				s.getNotificationManager().notifyLicenseUpdated(account);
				s.getLicenseExpirationCheckManager().removeWarningFlags();
				s.getLicenseExpirationCheckManager().makeExpirationCheck(false);
			}
		}



//		if (!account.getLicenseExpiresOn().equals(licenseInformation.getLicenseExpiresOn())
//				||
//				!account.getLicenseType().equals(licenseInformation.getLicenseType())){
//
//
//
//			account.setLicenseExpiresOn(licenseInformation.getLicenseExpiresOn());
//			account.setLicenseIssuedOn(licenseInformation.getLicenseIssuedOn());
//			account.setLicenseType(licenseInformation.getLicenseType());
//			account.setLicenseExpired(licenseInformation.isLicenseExpired());
//
//			ctxt.getContentResolver().update(ContentUris.withAppendedId(SipProfile.ACCOUNT_ID_URI_BASE, account.getId()), account.getDbContentValues(), null, null);
//			Log.inf(TAG, "Updated account with new license information [%s] id=[%s], licenseInf=[%s]", account.getDisplay_name(), account.getId(), licenseInformation);
//
//
//
//		} else {
//			Log.inf(TAG, "Account license information has been checked but expiration date or license type has not been changed, not updating.");
//		}
	}
}
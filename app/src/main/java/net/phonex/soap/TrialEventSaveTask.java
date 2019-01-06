package net.phonex.soap;

import android.content.Context;

import net.phonex.core.MemoryPrefManager;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.pub.parcels.StoredCredentials;
import net.phonex.soap.entities.TrialEventSaveRequest;
import net.phonex.soap.entities.TrialEventSaveResponse;
import net.phonex.util.Log;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

/**
 * Save trial event on
 * 
 * @author miroc
 */
public class TrialEventSaveTask implements Runnable {
	private static final String TAG = "TrialEventSaveTask";

	private SOAPHelper soapHelper;
    private Context context;
	private int eventType;

	public TrialEventSaveTask(Context context, int eventType) {
		this.context = context;
		soapHelper = new SOAPHelper(context);
		this.eventType = eventType;
	}

	@Override
	public void run() {
		try {
			runInternal();
		} catch(Exception e){
			Log.e(TAG, "Exception in saving trial event", e);
		}
	}

    private void runInternal() throws InstantiationException, IllegalAccessException, UnrecoverableKeyException, CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        Log.vf(TAG, "runInternal");
		StoredCredentials creds = MemoryPrefManager.loadCredentials(context);
		soapHelper.initSoap(creds.getUsrStoragePass());

		// create request
		TrialEventSaveRequest request = new TrialEventSaveRequest();
		request.setEtype(eventType);

		// make request
		SoapSerializationEnvelope envelope = soapHelper.reqisterRequest(request, TrialEventSaveResponse.class);
		TrialEventSaveResponse response = (TrialEventSaveResponse) soapHelper.makeSoapRequest(envelope, ServiceConstants.getDefaultURL(creds.retrieveDomain()), "trialEventSave", true);

		if (response == null){
			Log.ef(TAG, "runInternal(); received response is null");
			return;
		}

		Log.inf(TAG, "runInternal(); response received, errCode [%d]", response.getErrCode());
	}
}
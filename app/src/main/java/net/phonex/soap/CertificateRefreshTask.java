package net.phonex.soap;

import android.content.Context;

import net.phonex.R;
import net.phonex.db.entity.UserCertificate;
import net.phonex.util.Log;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;


public class CertificateRefreshTask extends BaseAsyncTask<CertificateRefreshParams>{
	@SuppressWarnings("unused")
	private static final String THIS_FILE = "CertificateRefreshTask";
	
	public static final int CHECKPOINTS_NUMBER = 3;
	
	/**
	 * SOAP client logic.
	 */
	private CertificateRefreshCall soap = null;
    
	public CertificateRefreshTask() {
		super();
	}
	
	public CertificateRefreshTask(Context ctxt) {
		super();
		this.context = ctxt;
		this.soap = new CertificateRefreshCall(ctxt);
	}

	/**
	 * Initializes soap for calls.
	 * 
	 * @param storagePassword
	 * @throws UnrecoverableKeyException
	 * @throws KeyStoreException
	 * @throws NoSuchAlgorithmException
	 * @throws CertificateException
	 * @throws IOException
	 */
	public void initSoap(String storagePassword) throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
		soap.initSoap(storagePassword);	
	}
	
	@Override
	protected Exception doInBackground(CertificateRefreshParams... params) {
		if (params.length==0){
			throw new IllegalArgumentException("Empty configuration");
		}
		
		final CertificateRefreshParams par = params[0];
		
		// HTTP transport - declare before TRY block to be able to 
		// extract response in catch block for debugging
		//HttpTransportSE androidHttpTransport = null;
		try {
			publishProgress(getContext().getString(R.string.p_refreshing_certificate));
			
			// Try and synchronized block in case of troubles.
			// Get the current thread's token
			synchronized (this) 
			{
				boolean recheckNeeded = soap.isCertificateRecheckNeeded(par);
            	if (isCancelled()) return null;
			
            	// obtain now personal data for decryption and eventual SOAP communication
				//publishProgress(new DefaultAsyncProgress(0, "Loading personal credentials"));
            	publishProgress(getContext().getString(R.string.p_loading_personal_credential));
				if (isCancelled()) return null;
				
				if (recheckNeeded || par.isForceRecheck()){
					// SOAP query for certificate
					//publishProgress(new DefaultAsyncProgress(0, "Synchronizing certificate"));
					publishProgress(getContext().getString(R.string.p_synchronizing_csr));
					soap.checkCertificate();
				}
			}
			
			Log.i(TAG, "Finished properly");
		} catch (Exception e) {
			Log.e(TAG, "Exception", e);			

			return e;
		}
		
		return null;
	}
	
	@Override
	protected void onPreExecute() {
		super.onPreExecute();
	}

	@Override
	protected void onPostExecute(Exception result) {
		super.onPostExecute(result);
		
		if (result == null){
			if (mFragment!=null)
				mFragment.taskFinished(result);
	
			if (onTaskCompleted!=null)
				onTaskCompleted.onCertificateRefreshTaskCompleted(getRemoteCert(), true);
		
		} else {
			if (mFragment!=null)
				mFragment.taskFinished(result);
			
			if (onTaskCompleted!=null)
				onTaskCompleted.onCertificateRefreshTaskCompleted(getRemoteCert(), false);
		}
	}
	
	
	@Override
	public void setContext(Context context) {
		super.setContext(context);
		if (soap==null){
			this.soap = new CertificateRefreshCall(context);
		} else {
			this.soap.setContext(context);
		}
	}
	
	public String getRemoteSip() {
		return soap.getRemoteSip();
	}
	
	public UserCertificate getRemoteCert(){
		return soap.getRemoteCert();
	}
	
	/*
	public boolean getCanceled(){
		return this.taskStatus.canceled;
	}
	
	public UserCertificate getRemoteCert(){
		return this.taskStatus.remoteCert;
	}
	
	public X509Certificate getX509Cert(){
		return this.taskStatus.remoteCertX509;
	}
	
	public int getStatusCode(){
		return this.taskStatus.statusCode;
	}*/
	
	public interface OnCertificateRefreshTaskCompleted{
		void onCertificateRefreshTaskCompleted(UserCertificate newCert, boolean success);
	}
	
	private OnCertificateRefreshTaskCompleted onTaskCompleted;
	
	public OnCertificateRefreshTaskCompleted getOnTaskCompleted() {
		return onTaskCompleted;
	}
	
	public void setOnTaskCompleted(OnCertificateRefreshTaskCompleted onTaskCompleted) {
		this.onTaskCompleted = onTaskCompleted;
	}
}

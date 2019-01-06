/**
 * 
 */
package net.phonex.soap;

import android.app.FragmentManager;
import android.content.Context;
import android.os.AsyncTask;

import net.phonex.db.entity.SipProfile;
import net.phonex.soap.SSLSOAP.SSLContextHolder;
import net.phonex.ui.dialogs.ProgressDialogFragment;
import net.phonex.util.Log;
import net.phonex.util.crypto.CertificatesAndKeys;
import net.phonex.util.crypto.KeyPairGenerator;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

/**
 * Base asynchronous progress task with DialogFragment to extend
 *
 * Usage:
 * ProgressDialogFragment progressFragment = new ProgressDialogFragment();
 * progressFragment.setMessage("Some Message");
 * progressFragment.setTitle("Some Title");
 *
 * SomeTask task = new SomeTask(getActivity(), getActivity().getSupportFragmentManager(), progressFragment);
 * task.showFragmentAndExecute(parameters...);
 *
 * To close associated fragment, call closeFragment(possibleException) in onPostExecute() method
 *
 * @author miroc
 */
public abstract class BaseAsyncTask<TTaskParams> extends AsyncTask<TTaskParams, BaseAsyncProgress, Exception>
    implements DefaultSOAPCall.ProgressEventListener {
	public static String TAG = "baseAsync2";

    // where to publish progress
    protected ProgressDialogFragment mFragment = null;
    protected int mProgress = 0;

    // context
    protected Context context = null;
    protected FragmentManager fragmentManager = null;

    // Invalid response exception
	public static class InvalidServerResponseException extends Exception {
		public static final long serialVersionUID=5L;

		public InvalidServerResponseException() {
			super();
		}

		public InvalidServerResponseException(String detailMessage, Throwable throwable) {
			super(detailMessage, throwable);
		}

		public InvalidServerResponseException(String detailMessage) {
			super(detailMessage);
		}

		public InvalidServerResponseException(Throwable throwable) {
			super(throwable);
		}
	}

    protected BaseAsyncTask() {
        super();
    }

    protected BaseAsyncTask(Context context) {
        super();
        this.context = context;
    }

    protected BaseAsyncTask(Context context, FragmentManager fragmentManager, ProgressDialogFragment mFragment) {
        super();
        this.context = context;
        this.fragmentManager = fragmentManager;
        this.mFragment = mFragment;
        this.mFragment.setTask(this);
    }

    /**
     * Preferred method to call instead of super.execute() - it automatically shows progress dialog and starts task execution
     * @param params
     * @return
     */
    public android.os.AsyncTask<TTaskParams,BaseAsyncProgress,Exception> showFragmentAndExecute(TTaskParams... params){
        if (mFragment == null){
            Log.e(TAG, "showFragmentAndExecute(): ProgressDialogFragment is null");
        }
        if (fragmentManager == null){
            Log.e(TAG, "showFragmentAndExecute(): FragmentManager is null");
        }
        mFragment.show(fragmentManager,"ProgressFragment");
        return super.execute(params);
    }

    /**
     * Tries to close Fragment, does null check
     */
    public void closeFragment(Exception result) {
        if (mFragment != null) {
            mFragment.taskFinished(result);
        }
    }

    /**
	 * onPreExecute(), invoked on the UI thread before the task is executed. 
	 * This step is normally used to setup the task, for instance by showing a progress bar in the user interface.
	 */
	@Override
	protected void onPreExecute() {
        super.onPreExecute();
		if (mFragment!=null && mFragment.getDialog()!=null){
			
			//mFragment.setProgress(0);
			//mFragment.setMessage("Initialized");
		}
	}
	
	/**
	 * Backward compatible progress update.
	 */
	public void publishProgress(String... values) {
		BaseAsyncProgress p = new BaseAsyncProgress();
	
		if (values!=null && values.length>0){
			p.setMessage(values[0]);
            Log.vf(TAG, "Progress: %s", values[0]);
		}
		
		publishProgress(p);
	}
	
	@Override
	protected void onProgressUpdate(BaseAsyncProgress... values) {
		// setup dialog		
		if (mFragment!=null){			
			if(values.length==0){
				mFragment.updateProgress();	
			} else {
				mFragment.updateProgress(values[0]);
			}			
		}
	}

	@Override
	protected void onCancelled(Exception result) {
		if (mFragment==null) return; 
		
		mFragment.taskFinished(result);  //dismiss
	
	}

	/**
	 * Prepares SOAP channel 
	 * @throws IOException 
	 * @throws CertificateException 
	 * @throws NoSuchAlgorithmException 
	 * @throws KeyStoreException 
	 * @throws UnrecoverableKeyException 
	 */
	protected SSLContextHolder prepareSoap() throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
		return prepareSoap(null, null);
	}
	
	/**
	 * Prepares SOAP channel 
	 * @throws IOException 
	 * @throws CertificateException 
	 * @throws NoSuchAlgorithmException 
	 * @throws KeyStoreException 
	 * @throws UnrecoverableKeyException 
	 */
	protected SSLContextHolder prepareSoap(char[] pass) throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
		return prepareSoap(null, pass);
	}
	
	/**
	 * Reads default key store in default container
	 * 
	 * @param pass
	 * @throws KeyStoreException
	 * @throws NoSuchAlgorithmException
	 * @throws CertificateException
	 * @throws IOException
	 * @throws UnrecoverableKeyException
	 */
	protected SSLContextHolder prepareSoapDefault(char[] pass) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, UnrecoverableKeyException{
		KeyPairGenerator kpg = new KeyPairGenerator();
		SipProfile currentProfile = SipProfile.getCurrentProfile(context);
		if (currentProfile == null){
			Log.wf(TAG, "prepareSoapDefault; null sip profile");
			return null;
		}
		KeyStore ks = kpg.readKeyStore(CertificatesAndKeys.derivePkcs12Filename(currentProfile.getSip()), context, pass);
    	return this.prepareSoap(ks, pass);
	}

	/**
	 * Prepares SOAP channel 
	 * @throws IOException 
	 * @throws CertificateException 
	 * @throws NoSuchAlgorithmException 
	 * @throws KeyStoreException 
	 * @throws UnrecoverableKeyException 
	 */
	protected SSLContextHolder prepareSoap(KeyStore ks, char[] pass) throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
		/**
		 * Preparing phase - initialize SSL connections
		 * 
		 * Install HTTPS support with client credentials and trust verifier
		 */
		return TaskSOAPHelper.prepareHTTPS(context, ks, pass);
	}

	protected FragmentManager getFragmentManager() {
        return fragmentManager;
    }

    public void setFragmentManager(FragmentManager fragmentManager) {
		this.fragmentManager = fragmentManager;
	}

	protected ProgressDialogFragment getFragment() {
		return mFragment;
	}

	public void setDialogFragment(ProgressDialogFragment fragment) {
		mFragment = fragment;		
	}

	protected Context getContext() {
		return context;
	}

	public void setContext(Context context) {
		this.context = context;
	}

}

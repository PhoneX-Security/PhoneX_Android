package net.phonex.ui.preferences;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.phonex.R;
import net.phonex.db.entity.SipProfile;
import net.phonex.db.entity.UserCertificate;
import net.phonex.ui.lock.activity.LockActionBarActivity;
import net.phonex.util.DefaultServiceConnector;
import net.phonex.util.LayoutUtils;
import net.phonex.util.Log;
import net.phonex.util.crypto.CertificatesAndKeys;
import net.phonex.util.crypto.KeyPairGenerator;
import net.phonex.util.crypto.MessageDigest;

import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;

import butterknife.ButterKnife;
import butterknife.InjectView;

public class MyCertificateActivity extends LockActionBarActivity implements DefaultServiceConnector.ServiceConnectorListener {
	private static final String THIS_FILE = "MyCertificateActivity";
	
	private DefaultServiceConnector connector;
	private ProgressDialog progressDialog;
	
	private SipProfile profile;
	private KeyStore ks;
	private Certificate[] certChain;

	@InjectView(R.id.main_container) LinearLayout mainContainer;
	@InjectView(R.id.my_toolbar) Toolbar toolbar;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// Create default service connector
		connector = new DefaultServiceConnector();
		connector.setDefCtxt(this);
		connector.setListener(this);
		
		// Set default view
		setContentView(R.layout.activity_my_certificate);
		ButterKnife.inject(this);

        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(R.string.my_certificate);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Intent intent = getIntent();
        profile = (SipProfile) intent.getParcelableExtra("SipProfile");
        
        // Show progress dialog until service is loaded
        connector.connectService(this);
	}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

	@Override
	protected void onStart() {
		super.onStart();
		
		if (connector.isConnected()==false){
			Log.d(THIS_FILE, "Service=null, starting waiting progressbar dialog");
			
			// Init progress loader
			progressDialog=new ProgressDialog(this);
			progressDialog.setTitle(getString(R.string.progress_loading_title));
			progressDialog.setMessage(getString(R.string.progress_loading_msg));
			progressDialog.setCancelable(true);
			progressDialog.setCanceledOnTouchOutside(false);
			progressDialog.setIndeterminate(true);
			progressDialog.setOnCancelListener(new OnCancelListener() {
				@Override
				public void onCancel(DialogInterface dialog) {
					MyCertificateActivity.this.finish();
				}
			});
			
			progressDialog.show();
		}
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		if (connector!=null && connector.isConnected()){
			connector.disconnectService(this);
		}
	}
	

	@Override
	public void onXServiceConnected(ComponentName arg0, IBinder arg1) {
		try {			
			PreloadTask pt = new PreloadTask();
			pt.passwd = connector.getService().getStoragePassword();
			pt.ctxt   = getApplicationContext();
			pt.execute();
			
		} catch (Exception e) {
			Log.e(THIS_FILE, "Cannot obtain password", e);
			if (progressDialog!=null && progressDialog.isShowing()){
				progressDialog.dismiss();
			}
			
			finish();
		}
	}

	@Override
	public void onXServiceDisconnected(ComponentName arg0) {
		
	}

	/**
	 * Service is connected, certificate loaded, initialize view
	 */
	public void loaded(){
		LayoutInflater inflater = getLayoutInflater();
    	populateView(mainContainer, inflater);
		progressDialog.dismiss();
	}
	
	public void populateView(LinearLayout v, LayoutInflater inflater){
    	View cCert          = inflater.inflate(R.layout.mycategory, null, false);
    	LinearLayout cCertL = (LinearLayout) cCert.findViewById(R.id.container);
    	((TextView) cCert.findViewById(R.id.category_title)).setText(getString(R.string.warn_sig_detail_cat_cert));
    	
    	if (certChain == null || certChain.length==0 || !(certChain[0] instanceof X509Certificate)){
    		return;
    	}
    	
    	final X509Certificate crt = (X509Certificate) certChain[0];
		try {
			cCertL.addView(this.createCategoryChild(inflater, null, R.string.cert_serial, 
        			crt.getSerialNumber().toString()));
			cCertL.addView(this.createCategoryChild(inflater, null, R.string.cert_validity, 
        			UserCertificate.formatDate(crt.getNotBefore()) + " - " + UserCertificate.formatDate(crt.getNotAfter())));
			cCertL.addView(this.createCategoryChild(inflater, null, R.string.cert_cn, 
        			crt.getSubjectDN().toString()));
			cCertL.addView(this.createCategoryChild(inflater, null, R.string.cert_issuer,
        			crt.getIssuerDN().toString()));
			cCertL.addView(this.createCategoryChild(inflater, null, R.string.cert_hash, 
					MessageDigest.getCertificateDigest(crt)));
			cCertL.addView(this.createCategoryChild(inflater, null, R.string.cert_public_key_alg, 
					crt.getPublicKey().getAlgorithm()));
			if (crt.getPublicKey() instanceof RSAPublicKey){
				final RSAPublicKey rsa = (RSAPublicKey) crt.getPublicKey();
				cCertL.addView(this.createCategoryChild(inflater, null, R.string.cert_public_key_length, 
						Integer.toString(rsa.getModulus().bitLength())));
			}
			
			cCertL.addView(this.createCategoryChild(inflater, null, R.string.cert_public_key, 
					crt.getPublicKey().toString()));
			cCertL.addView(this.createCategoryChild(inflater, null, R.string.cert_raw, 
					crt.toString()));
			
			
		} catch(Exception e){
			Log.wf(THIS_FILE, e, "Cannot read certificate for [%s]", profile.getDisplayName());
		}
    	
    	v.addView(cCert);
	}
	
	public View createCategoryChild(LayoutInflater inflater, ViewGroup container, int title, String body){
    	return LayoutUtils.createCategoryChild(inflater, container, getString(title), body);
    }
    
    public View createCategoryChild(LayoutInflater inflater, ViewGroup container, String title, String body){
    	return LayoutUtils.createCategoryChild(inflater, container, title, body);
    }
	
	/**
	 * Async task for pre-loading additional data
	 * 
	 * @author ph4r05
	 */
	private class PreloadTask extends AsyncTask<Void,Void,Void>{
		public Context ctxt;
		public String passwd;
                
        @Override
		protected Void doInBackground(Void... arg0) {
        	try {
        		// Load default certificate form chain
        		KeyPairGenerator kpg = new KeyPairGenerator();
				SipProfile currentProfile = SipProfile.getCurrentProfile(ctxt);
				if (currentProfile == null){
					throw new Exception("current profile is null");
				}
				ks = kpg.readKeyStore(CertificatesAndKeys.derivePkcs12Filename(currentProfile.getSip()), ctxt, passwd.toCharArray());

            	// Obtain certificate chain stored with private key.
            	certChain = ks.getCertificateChain(CertificatesAndKeys.DEFAULT_KEY_ALIAS);
        	} catch(Exception e){
        		Log.e(THIS_FILE, "Exception during certificate load", e);
        	}
        	
			return null;
		}
        
        @Override
        protected void onPostExecute(Void v){
            super.onPostExecute(v);
            loaded();
        }
     }

	@Override
	protected String activityAnalyticsName() {
		return this.getClass().getSimpleName();
	}
}
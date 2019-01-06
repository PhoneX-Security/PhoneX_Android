package net.phonex.ui.cl;

import android.app.Dialog;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;

import net.phonex.R;
import net.phonex.db.entity.SipClist;
import net.phonex.core.SipUri;
import net.phonex.db.entity.UserCertificate;
import net.phonex.soap.CertificateRefreshParams;
import net.phonex.soap.CertificateRefreshTask;
import net.phonex.soap.CertificateRefreshTask.OnCertificateRefreshTaskCompleted;
import net.phonex.ui.dialogs.AlertDialogFragment;
import net.phonex.ui.dialogs.ProgressDialogFragment;
import net.phonex.util.LayoutUtils;
import net.phonex.util.Log;
import net.phonex.util.crypto.CertificatesAndKeys;
import net.phonex.util.crypto.MessageDigest;

import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;

/**
 * DialogFragment class for displaying user's details
 */
public class ContactInfoDialogFragment extends AlertDialogFragment implements OnClickListener{
	private static final String THIS_FILE = "ContactInfoDialogFragment";
	
	private UserCertificate sipCert = null;
	private FrameLayout fl = null;
	private SipClist profile;		
	private String storagePass;
	
	public static ContactInfoDialogFragment newInstance(String title, SipClist profile) {
		ContactInfoDialogFragment fragment = new ContactInfoDialogFragment();	        
    	Bundle args = new Bundle();
    	args.putCharSequence("title", title);	    	
    	fragment.setArguments(args);
    	fragment.profile = profile;
        return fragment;
    }	
	
	public void setStoragePass(String storagePass) {
		this.storagePass = storagePass;
	}

	/**
	 * Preload additional data required, in separate async task
	 * 
	 * @param fragmentManager
	 * @param string
	 */
	public void preLoad(FragmentManager fragmentManager, Context ctxt, String string) {
		PreloadTask t = new PreloadTask();
		t.fragmentManager = fragmentManager;
		t.tag = string;
        t.ctxt = ctxt;
        t.execute();
	}
	
	private void loadFinished(FragmentManager fragmentManager2, String tag2) {
		this.show(fragmentManager2, tag2);
	}
	
	protected void loadFinished(){
		// refresh view
		if (fl!=null){
			fl.removeAllViews();	// remove all views from container
			fl.addView(prepareContainer(getActivity().getLayoutInflater(), null, null));
		}
	}
	
	public Dialog onCreateDialog(Bundle savedInstanceState) {			   
        CharSequence title = getArguments().getCharSequence("title");	        
        
		LayoutInflater inflater = getActivity().getLayoutInflater();

		View bodyView = prepareContainer(inflater, null, savedInstanceState);

		MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
				.title(title)
//				.iconRes(R.drawable.svg_user)
				.iconRes(R.drawable.ic_person_black_24px)
				.customView(bodyView, true)
//				.accentColorRes(R.color.phonex_dialog_title_background)
//				.titleBackgroundColorRes(R.color.phonex_dialog_title_background)
//				.titleColorRes(R.color.phonex_dialog_background)
				.negativeText(R.string.cancel);

		return builder.build();
    }
	
	public View prepareContainer(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.clist_contact_info, container, false);
    	LinearLayout mContainerView = (LinearLayout) v.findViewById(R.id.main_container);
    	
//    	int paddingLeftRight = LayoutUtils.dp2Pix(getResources(), 24);
    	int paddingLeftRight = LayoutUtils.dp2Pix(getResources(), 0);
    	int paddingTopBottom = LayoutUtils.dp2Pix(getResources(), 10);
    	
    	// Category - certificate result
    	View cUser          = inflater.inflate(R.layout.mycategory, container, false);
    	LinearLayout cUserL = (LinearLayout) cUser.findViewById(R.id.container);
    	((TextView) cUser.findViewById(R.id.category_title)).setText(getString(R.string.clist_cat_user));
    	cUserL.addView(this.createCategoryChild(inflater, container, getString(R.string.clist_display_name), profile.getDisplayName()));
    	cUserL.addView(this.createCategoryChild(inflater, container, getString(R.string.clist_sip), profile.getSip()));
    	
    	cUserL.setPadding(paddingLeftRight, 0, paddingLeftRight, paddingTopBottom);
    	mContainerView.addView(cUser);
    	
    	// Category - certificate
    	View cCert          = inflater.inflate(R.layout.mycategory, container, false);
    	LinearLayout cCertL = (LinearLayout) cCert.findViewById(R.id.container);
    	((TextView) cCert.findViewById(R.id.category_title)).setText(getString(R.string.warn_sig_detail_cat_cert));
    	
    	cCertL.addView(this.createCategoryChild(inflater, container, R.string.has_certificate, sipCert != null ? getString(R.string.cert_yes) : getString(R.string.cert_no)));
    	if (sipCert!=null){
    		cCertL.addView(this.createCategoryChild(inflater, container, R.string.cert_status, 
        			UserCertificate.getCertificateErrorString(sipCert.getCertificateStatus(), getActivity())));
    		cCertL.addView(this.createCategoryChild(inflater, container, R.string.cert_owner, 
    				sipCert.getOwner()));
    		cCertL.addView(this.createCategoryChild(inflater, container, R.string.cert_date_created, 
    				sipCert.getDateCreatedFormatted()));
    		cCertL.addView(this.createCategoryChild(inflater, container, R.string.cert_date_last_refresh, 
    				sipCert.getDateLastQueryFormatted()));
    		cCertL.addView(this.createCategoryChild(inflater, container, R.string.cert_hash, 
    				sipCert.getCertificateHash()));
    		
    		try {
				X509Certificate crt = sipCert.getCertificateObj();
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
//				cCertL.addView(this.createCategoryChild(inflater, null, R.string.cert_raw, 
//						crt.toString()));
				
			} catch(Exception e){
				Log.wf(THIS_FILE, e, "Cannot read certificate for [%s]", profile.getDisplayName());
			}
    	}
    	
    	cCertL.setPadding(paddingLeftRight, 0, paddingLeftRight, paddingTopBottom); 
    	mContainerView.addView(cCert);
    	
    	// Add last category - actions, button bar
    	View cAct          = inflater.inflate(R.layout.mycategory, container, false);
    	LinearLayout cActL = (LinearLayout) cAct.findViewById(R.id.container);
        //TODO
    	((TextView) cAct.findViewById(R.id.category_title)).setText(getString(R.string.warn_sig_detail_cat_action));
    	
    	View bBar          = inflater.inflate(R.layout.clist_contact_info_buttons, container, false);
    	Button bCert       = (Button) bBar.findViewById(R.id.check_certificate);
    	bCert.setOnClickListener(this);
    	
    	cActL.addView(bBar);
    	mContainerView.addView(cAct);
        
        return v;
	}	
	
	public View createCategoryChild(LayoutInflater inflater, ViewGroup container, int title, String body){
    	return LayoutUtils.createCategoryChild(inflater, container, getString(title), body);
    }
    
    public View createCategoryChild(LayoutInflater inflater, ViewGroup container, String title, String body){
    	return LayoutUtils.createCategoryChild(inflater, container, title, body);
    }
    
    @Override
	public void onClick(View v) {
		if (v != null && v.getId() == R.id.check_certificate){
			
			// Display simple fragment showing progress & blocking UI
			ProgressDialogFragment fragment = new ProgressDialogFragment();  
			fragment.setMessage("");
			fragment.setTitle(getString(R.string.p_refreshing_certificate));
			fragment.setCheckpointsNumber(CertificateRefreshTask.CHECKPOINTS_NUMBER);
			
			// Initialize task configuration
			CertificateRefreshParams p = new CertificateRefreshParams();
			p.setForceRecheck(true);
			p.setSip(profile.getSip());
			p.setStoragePass(storagePass);
            
			// Start task
			CertificateRefreshTask task = new CertificateRefreshTask();
			task.setContext(getActivity());
			task.setOnTaskCompleted(new OnCertificateRefreshTaskCompleted() {
				@Override
				public void onCertificateRefreshTaskCompleted(UserCertificate newCert, boolean success) {
					PreloadTask t = new PreloadTask();
			        t.ctxt = getActivity();
			        t.justRefresh = true;
			        t.execute();
				}
			});
			
          	FragmentManager fm = getActivity().getFragmentManager();
           	task.setFragmentManager(fm);
           	fragment.setTask(task);
           	fragment.show(fm,"");
           	task.execute(p);
		}
	}
	
	/**
	 * Async task for pre-loading additional data
	 * 
	 * @author ph4r05
	 */
	private class PreloadTask extends AsyncTask<Void,Void,Void>{
		public Context ctxt;
        ProgressDialog pDialog;
        public FragmentManager fragmentManager;
        public String tag;
        public boolean justRefresh=false;
        
        @Override
         protected void onPreExecute(){
            pDialog = new ProgressDialog(ctxt);
            // TODO
//            pDialog.setMessage(ctxt.getString(R.string.warn_sig_detail_preloading));
            pDialog.show();
         }
        
        @Override
		protected Void doInBackground(Void... arg0) {
        	try {
        		String sip = SipUri.getCanonicalSipContact(profile.getSip(), false);
        		sipCert = CertificatesAndKeys.getRemoteCertificate(ctxt, sip);
        	} catch(Exception e){
        		Log.e(THIS_FILE, "Error during sleep", e);
        	}
        	
			return null;
		}
        @Override
        protected void onPostExecute(Void v){
            super.onPostExecute(v);
            pDialog.dismiss();
            
            if (justRefresh){ 
            	loadFinished();
            } else {
            	loadFinished(this.fragmentManager, this.tag);
            }
        }
     }
}


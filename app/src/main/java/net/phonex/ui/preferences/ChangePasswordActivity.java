package net.phonex.ui.preferences;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.Toast;

import net.phonex.R;
import net.phonex.core.IService;
import net.phonex.core.Intents;
import net.phonex.core.MemoryPrefManager;
import net.phonex.db.entity.SipProfile;
import net.phonex.pub.parcels.StoredCredentials;
import net.phonex.soap.PasswordChangeParams;
import net.phonex.soap.ServiceConstants;
import net.phonex.ui.dialogs.AlertDialogFragment;
import net.phonex.ui.interfaces.OnPassChangedListener;
import net.phonex.ui.intro.ChangePasswordFragment;
import net.phonex.ui.lock.activity.LockActionBarActivity;
import net.phonex.util.DefaultServiceConnector;
import net.phonex.util.DefaultServiceConnector.ServiceConnectorListener;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.analytics.AnalyticsReporter;
import net.phonex.util.analytics.AppEvents;

import butterknife.ButterKnife;
import butterknife.InjectView;

public class ChangePasswordActivity extends LockActionBarActivity implements OnPassChangedListener, ServiceConnectorListener{
	private static final String THIS_FILE = "ChangePasswordActivity";
	private ChangePasswordFragment fragment;
	private SipProfile profile;
	
	private DefaultServiceConnector connector;
	private ProgressDialog progressDialog;

	@InjectView(R.id.my_toolbar) Toolbar toolbar;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.change_password_activity);

		ButterKnife.inject(this);

		setSupportActionBar(toolbar);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setTitle(R.string.change_password);

		// Create default service connector
		connector = new DefaultServiceConnector();
		connector.setDefCtxt(this);
		connector.setListener(this);

        
        fragment = (ChangePasswordFragment) getFragmentManager().findFragmentById(R.id.changePassFragment);
        
        Intent intent = getIntent();
        profile = intent.getParcelableExtra("SipProfile");
        
        PasswordChangeParams params = new PasswordChangeParams();        
        params.setUserSIP(profile.getUsername());
        params.setTargetUserSIP(profile.getUsername());

//        params.setUserOldPass(profile.getPassword());
        params.setServiceURL(ServiceConstants.getServiceURL(profile.getSipDomain(), false));
        
        // Changing password from the application -> re-key everything.
        params.setDerivePasswords(true);
        params.setRekeyKeyStore(true);
        params.setRekeyDB(true);
        
        fragment.setParams(params);
        
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
			
			// Initialize progress loader waiting service to be connected to the activity.
			progressDialog=new ProgressDialog(this);
			progressDialog.setTitle(getString(R.string.progress_loading_title));
			progressDialog.setMessage(getString(R.string.progress_loading_msg));
			progressDialog.setCancelable(true);
			progressDialog.setCanceledOnTouchOutside(false);
			progressDialog.setIndeterminate(true);
			progressDialog.setOnCancelListener(new OnCancelListener() {
				@Override
				public void onCancel(DialogInterface dialog) {
					ChangePasswordActivity.this.finish();
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
			progressDialog.dismiss();			
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
	
	@Override
	public void onPassChanged(PasswordChangeParams params) {
		int result = 0;
		
		// 1. Update password stored in service.
		final IService service = getService();
		if (service==null){		
			Log.e(THIS_FILE,"XService should not be null here, cannot save storage password", new NullPointerException("XService is null"));
			AlertDialogFragment.newInstance(getString(R.string.p_problem), getString(R.string.p_unknown_error)).show(getFragmentManager(), "TAG");
			finish();
            return;
		}	
		try {
			service.setStoragePassword(params.getStoragePass());
		} catch (RemoteException e1) {			
			Log.e(THIS_FILE, "Cannot save storage password to the XService" ,e1);
			AlertDialogFragment.newInstance(getString(R.string.p_problem), getString(R.string.p_unknown_error)).show(getFragmentManager(), "TAG");
            finish();
            return;
		}
		
		// 2. Update password stored in SafeNet.
		// Only if new passwords were derived.
		if (params.isDerivePasswords()){
			StoredCredentials creds = new StoredCredentials(params.getUserSIP(), params.getUserNewPass(), params.getPemPass(), params.getStoragePass());
			MemoryPrefManager.updateCredentials(this, creds);
		}
		
		// Update password in the DB.
		// This call may not work if encrypted database is not yet connected = user sets his password for the first time.
        profile.setData(params.getUserNewPass());
        try {
        	getContentResolver().update(ContentUris.withAppendedId(SipProfile.ACCOUNT_ID_URI_BASE, profile.getId()), profile.getDbContentValues(), null, null);
        	Log.df(THIS_FILE, "Password in account updated [%s] id=[%s]", profile.getDisplay_name(), profile.getId());
        } catch(Exception e){
        	Log.w(THIS_FILE, "Exception in updating database stored password", e);
        }

        // restart of password may require sip stack to be restarted
        Intent intent = new Intent(Intents.ACTION_SIP_REQUEST_RESTART);
		MiscUtils.sendBroadcast(this, intent);

		// analytics
		AnalyticsReporter.from(this).event(AppEvents.PASSWORD_CHANGED);

		//this code is returned in onActivityResult
		Toast.makeText(this, R.string.p_password_change_ok, Toast.LENGTH_SHORT).show();
		setResult(result);		
		finish();       
	}
	
	/**
	 * 
	 * @return service
	 */
	public IService getService() {
		return connector==null ? null : connector.getService();
	}

	@Override
	protected String activityAnalyticsName() {
		return this.getClass().getSimpleName();
	}
}

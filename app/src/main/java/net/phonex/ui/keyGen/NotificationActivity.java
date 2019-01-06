package net.phonex.ui.keyGen;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.MenuItem;

import net.phonex.R;
import net.phonex.core.IService;
import net.phonex.core.Intents;
import net.phonex.ui.ServiceConnected;
import net.phonex.ui.lock.activity.LockActionBarActivity;
import net.phonex.util.DefaultServiceConnector;
import net.phonex.util.DefaultServiceConnector.ServiceConnectorListener;
import net.phonex.util.Log;

import butterknife.ButterKnife;
import butterknife.InjectView;

public class NotificationActivity extends LockActionBarActivity implements ServiceConnectorListener, ServiceConnected {
	private static final String THIS_FILE = "NotificationActivity";
	
	// Service connecting stuff.
	private DefaultServiceConnector connector;
	private ProgressDialog progressDialog;
	private Bundle savedInstanceState;
	
	// Tags for fragments sitting in this activity.
	private static final int FRAGMENT_CERT = 0;
	private static final int FRAGMENT_KEY = 1;
	private static final String[] FRAGMENTS_TAGS = new String[] {"certUpdateFragment", "genKeyFragment"};
	private int fragment2display = FRAGMENT_CERT;

	@InjectView(R.id.my_toolbar) Toolbar toolbar;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.savedInstanceState = savedInstanceState;
		
		// Create default service connector
		connector = new DefaultServiceConnector();
		connector.setDefCtxt(this);
		connector.setListener(this);
		
		// Init content view from XML layout
        setContentView(R.layout.activity_with_fragment_and_toolbar);
		ButterKnife.inject(this);
        
        // Get intent and handle it
        Intent intent = getIntent();
        handleIntents(intent);
        
        // Show progress dialog until service is loaded
        connector.connectService(this);

		setSupportActionBar(toolbar);

		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setDisplayShowHomeEnabled(true);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()){
			case android.R.id.home:
				finish();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	public Toolbar getToolbar() {
		return toolbar;
	}

	/**
	 * Adds main fragment to the body.
	 * @param savedInstanceState
	 */
	protected void addMainFragment(Bundle savedInstanceState) {
		final String fragTag = FRAGMENTS_TAGS[fragment2display];

        // http://stackoverflow.com/questions/7223166/oncreate-and-oncreateview-invokes-a-lot-more-than-required-fragments
        // Restore fragment's state if some stored
    	FragmentTransaction ft = getFragmentManager().beginTransaction();
    	Fragment fragmentToAdd = null;
    	
        if(savedInstanceState != null && savedInstanceState.containsKey(fragTag)){
        	fragmentToAdd = getFragmentManager().getFragment(savedInstanceState, fragTag);
        	Log.vf(THIS_FILE, "restore saved fragment instance; savedInstance=%s", savedInstanceState);
        } else {
        	// Manual construction of supported fragments.
        	switch(fragment2display){
        	case FRAGMENT_CERT:
        		Log.v(THIS_FILE, "Going to initialize CertUpdate fragment");
        		fragmentToAdd = new CertUpdateFragment();
        		break;
        	case FRAGMENT_KEY:
        		Log.v(THIS_FILE, "Going to initialize key gen fragment");
        		fragmentToAdd = new KeyGenFragment();
        		break;
        	default:
        		throw new IllegalArgumentException("Unknonw fragment to display: " + fragment2display);
        	}
        }

        ft.add(R.id.fragment_content, fragmentToAdd, fragTag).commit();
	}

	private void handleIntents(Intent intent) {
		fragment2display = FRAGMENT_CERT;
		
		final String action = intent.getAction();
		if (TextUtils.isEmpty(action)) {
			Log.e(THIS_FILE, "Empty intent action");
			return;
		}
		
		// Select which fragment to display according to intent action string.
		if (Intents.ACTION_CERT_UPD_NOTIFICATION.equals(action)){
			fragment2display = FRAGMENT_CERT;
		} else if (Intents.ACTION_KEY_GEN_NOTIFICATION.equals(action)){
			fragment2display = FRAGMENT_KEY;
		} else {
			Log.ef(THIS_FILE, "Unknown fragment to display. action=%s", action);
		}
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		
		final int frags = FRAGMENTS_TAGS.length;
		for(int i=0; i<frags; i++){
			Fragment f = getFragmentManager().findFragmentByTag(FRAGMENTS_TAGS[i]);
			if (f!=null) {
                getFragmentManager().putFragment(outState, FRAGMENTS_TAGS[i], f);
			}
		}
				
		Log.vf(THIS_FILE, "Saving to bundle; bundle=%s", outState);
	}

	@Override
	protected void onStart() {
		super.onStart();
		
		if (!connector.isConnected()){
			Log.d(THIS_FILE, "Service=null, starting waiting progressbar dialog");
			
			// Initialize progress loader waiting service to be connected to the activity.
			progressDialog=new ProgressDialog(this);
			progressDialog.setTitle(getString(R.string.progress_loading_title));
			progressDialog.setMessage(getString(R.string.progress_loading_msg));
			progressDialog.setCancelable(true);
			progressDialog.setCanceledOnTouchOutside(false);
			progressDialog.setIndeterminate(true);
			progressDialog.setOnCancelListener(dialog -> NotificationActivity.this.finish());
			
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
		
		// Add main fragment.
		Log.v(THIS_FILE, "Service connected, going to add main fragment.");
        addMainFragment(savedInstanceState);
	}

	@Override
	public void onXServiceDisconnected(ComponentName arg0) {
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

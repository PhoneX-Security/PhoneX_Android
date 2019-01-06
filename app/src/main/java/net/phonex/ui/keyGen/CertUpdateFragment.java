package net.phonex.ui.keyGen;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import net.phonex.R;
import net.phonex.core.Intents;
import net.phonex.core.SipUri;
import net.phonex.pub.parcels.CertUpdateProgress;
import net.phonex.ui.ServiceConnected;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CertUpdateFragment extends Fragment {
	private static final String THIS_FILE = "CertUpdateFragment";		
	private Bundle savedState = null;

	private ServiceConnected svc;
	private Activity mParent;

	private CertUpdateAdapter sAdapter;
	private BroadcastReceiver iReceiver;
	private volatile boolean initialized=false;
	
	@Override
	public void onAttach(Activity activity) {
	  super.onAttach(activity);
	  Log.vf(THIS_FILE, "onAttach, activity: %s", activity);
	  
	  mParent = activity;
	  if (mParent instanceof ServiceConnected){
		  svc = (ServiceConnected)mParent;
	  } else {
		  throw new ClassCastException("Activity has to implement ServiceConnected interface");
	  }
	  
	  registerServiceBroadcasts();
	}
	@Override
	public void onDetach() {
		super.onDetach();
		unregisterServiceBroadcasts();
	}
	@Override
	public void onCreate(Bundle savedInstanceState) {	
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {    
		super.onActivityCreated(savedInstanceState);
		mParent = getActivity();
	}
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		Log.vf(THIS_FILE, "onCreateView; savedInstance=%s; this=%s", savedInstanceState, this);
		View view = inflater.inflate(R.layout.list_simple, container, false);
		View headerView = inflater.inflate(R.layout.list_single_line_header, null, false);
		TextView desc = (TextView) headerView.findViewById(R.id.warn_desc);
		desc.setText(R.string.certupd_header_description);

		// Get service
		if (svc==null || svc.getService()==null){
			Log.w(THIS_FILE, "Service not available in onCreateView()");
			return view;
		}
		
		List<CertUpdateProgress> progress;
		try {
			Log.v(THIS_FILE, "Going to call for progress");
			progress = svc.getService().getCertUpdateProgress();
			Log.vf(THIS_FILE, "Progress loaded: %s", progress);
			
			// initialize adapter, connect to list view
	        sAdapter = new CertUpdateAdapter(mParent, progress);
	        sAdapter.sort(comp);
	        sAdapter.notifyDataSetChanged();

			ListView mListView = (ListView) view.findViewById(R.id.list);
	        mListView.addHeaderView(headerView);
	        mListView.setAdapter(sAdapter);
	        
	        initialized=true;
		} catch (RemoteException e) {
			Log.e(THIS_FILE, "Exception in setting up the certificate update fragment from service.");
		}

		Activity activity = getActivity();
		if (activity != null && activity instanceof  NotificationActivity){
			NotificationActivity notificationActivity = (NotificationActivity) activity;
			Toolbar toolbar = notificationActivity.getToolbar();
			if (toolbar !=null){
				toolbar.setTitle(R.string.certupd_header);
			}
		}
		
		/* If the Fragment was destroyed in between (screen rotation), we need to recover the savedState first */
	    /* However, if it was not, it stays in the instance from the last onDestroyView() and we don't want to overwrite it */
	    if(savedInstanceState != null && savedState == null){
	        //savedState = savedInstanceState.getBundle(KEY_STATE);		    
		    return view;
	    }
	    
	    return view;
	}
	
	/**
	 * Sort comparator for certificate update status.
	 */
	final private Comparator<CertUpdateProgress> comp = (e1, e2) -> {
        if (e1==null && e2==null) return 0;
        if (e1==null) return 1;
        if (e2==null) return -1;

        final long time1 = e1.getWhen()/1000;
        final long time2 = e2.getWhen()/1000;

        if (time1 == time2){
            return e1.getUser().compareTo(e2.getUser());
        }

        return e1.getWhen() > e2.getWhen() ? -1 : 1;
    };
	
	private void registerServiceBroadcasts() {
	    if(iReceiver == null && mParent!=null) {
	        IntentFilter intentfilter = new IntentFilter();
            intentfilter.addAction(Intents.ACTION_CERT_UPDATE_PROGRESS);
            iReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    final String action = intent.getAction();
                    if (!Intents.ACTION_CERT_UPDATE_PROGRESS.equals(action)){
                        throw new IllegalArgumentException("Unknown intent received");
                    }
                    
                    if (!intent.hasExtra(Intents.CERT_INTENT_PROGRESS)){
                    	Log.w(THIS_FILE, "Intent does not have extra for progress");
                    	return;
                    }
                    
                    if (!initialized){
                    	Log.w(THIS_FILE, "Was not initialized, cannot continue");
                    	return;
                    }
                    
                    ArrayList<CertUpdateProgress> progress = intent.getParcelableArrayListExtra(Intents.CERT_INTENT_PROGRESS);
                    Log.v(THIS_FILE, "Update progress: " + intent + "; progress.size()=" + progress.size());
                    
                    sAdapter.clear();
                    sAdapter.setData(progress);
                    sAdapter.sort(comp);
                    sAdapter.notifyDataSetChanged();
                }
                
            };

			MiscUtils.registerReceiver(mParent, iReceiver, intentfilter);
	   }
	}
	
	private void unregisterServiceBroadcasts() {
	    if(iReceiver != null && mParent!=null) {
	        mParent.unregisterReceiver(iReceiver);
	        iReceiver = null;
	    }
	}
	
	@Override
	public void onDestroyView() {
	   super.onDestroyView();
	   savedState = saveState();
	}
	
	private Bundle saveState() { /* called either from onDestroyView() or onSaveInstanceState() */
		return new Bundle();
	}

	
	@Override
	public void onPause() {
		Log.vf(THIS_FILE, "onPause; this=%s", this);
		super.onPause();
	}
	
	@Override
	public void onResume() {
		Log.vf(THIS_FILE, "onResume; this=%s", this);
		super.onResume();
	}
	
	/**
	 * My custom adapter using progress information obtained from service.
	 * @author ph4r05
	 *
	 */
	private class CertUpdateAdapter extends ArrayAdapter<CertUpdateProgress> {
		private Activity context;
		private SimpleDateFormat dateFormat;
		
		public CertUpdateAdapter(Context context, List<CertUpdateProgress> objects) {
			super(context, R.layout.cert_check_row, objects);
			this.context = (Activity)context;
			this.dateFormat = new SimpleDateFormat("HH:mm:ss dd-MM-yyyy", Locale.getDefault());
		}
		
		@TargetApi(11)
		public void setData(List<CertUpdateProgress> data) {
		    clear();
		    if (data != null) {
	            for(CertUpdateProgress item: data) {
	                add(item);
	            }
		    }
		}
		
		private class ViewHolder {
		    public TextView userName;
		    public TextView tstamp;
		    public TextView status;
		    public ProgressBar progress;
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View rowView = convertView;
			
		    // Reuse views.
		    if (rowView == null) {
		      LayoutInflater inflater = context.getLayoutInflater();
		      rowView = inflater.inflate(R.layout.cert_check_row, null);
		      
		      // Configure view holder.
		      ViewHolder viewHolder = new ViewHolder();
		      viewHolder.userName = (TextView) rowView.findViewById(R.id.userName);
		      viewHolder.tstamp = (TextView) rowView.findViewById(R.id.tstamp);
		      viewHolder.status = (TextView) rowView.findViewById(R.id.status);
		      viewHolder.progress = (ProgressBar) rowView.findViewById(R.id.progress);
		      rowView.setTag(viewHolder);
		    }

		    // fill data
		    ViewHolder holder = (ViewHolder) rowView.getTag();
		    final CertUpdateProgress cProgress = this.getItem(position);
		    if (cProgress==null) {
		    	Log.w(THIS_FILE, "Empty cert update progress");
		    	return rowView;
		    }

			String username = SipUri.parseSipContact(cProgress.getUser()).userName;
			holder.userName.setText(username);
		    holder.tstamp.setText(dateFormat.format(new Date(cProgress.getWhen())));
		    
		    int statusText = R.string.certupd_none;
		    int progressState=1;
		    switch(cProgress.getState()){
		    	default:
		    	case DONE:
		    		progressState=0;
		    		statusText = R.string.certupd_done;
		    		break;
		    	case NONE:
		    		progressState=0;
		    		statusText = R.string.certupd_none;
		    		break;
		    	case IN_QUEUE:
		    		statusText = R.string.certupd_in_queue;
		    		break;
		    	case STARTED:
		    		statusText = R.string.certupd_started;
		    		break;
		    	case LOCAL_LOADING:
		    		statusText = R.string.certupd_local_loading;
		    		break;
		    	case SERVER_CALL:
		    		statusText = R.string.certupd_server_call;
		    		break;
		    	case POST_SERVER_CALL:
		    		statusText = R.string.certupd_post_server_call;
		    		break;
		    	case SAVING:
		    		statusText = R.string.certupd_saving;
		    		break;
		    }
		    
		    // Progress bar change
		    if (progressState==0){
		    	holder.progress.setIndeterminate(false);
	    		holder.progress.setProgress(holder.progress.getMax());
		    } else {
		    	holder.progress.setIndeterminate(true);
	    		holder.progress.setProgress(1);
		    }
		    
		    // Status text
		    holder.status.setText(statusText);
		    

		    return rowView;
		}

		@Override
		public void notifyDataSetChanged() {
			super.notifyDataSetChanged();
		}		
	}	
}


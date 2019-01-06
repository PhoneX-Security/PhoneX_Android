package net.phonex.ui.keyGen;

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
import net.phonex.pub.parcels.KeyGenProgress;
import net.phonex.pub.parcels.KeyGenProgress.KeyGenStateEnum;
import net.phonex.ui.ServiceConnected;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;


/**
 * Simple fragment showing progress information about key generation.
 *  
 * @author ph4r05
 */
public class KeyGenFragment extends Fragment {
	private static final String THIS_FILE = "KeyGenFragment";		
	private Bundle savedState = null;

	private ServiceConnected svc;
	private Activity mParent;

	private KeyUpdateAdapter adapter;
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
		desc.setText(R.string.keygen_header_description);
		
		// Get service
		if (svc==null || svc.getService()==null){
			Log.w(THIS_FILE, "Service not available in onCreateView()");
			return view;
		}
		
		List<KeyGenProgress> progress;
		try {
			Log.v(THIS_FILE, "Going to call for progress");
			progress = svc.getService().getDHKeyProgress();
			Log.vf(THIS_FILE, "Progress loaded: %s", progress);
			
			// initialize adapter, connect to list view
	        adapter = new KeyUpdateAdapter(mParent, progress);
	        adapter.sort(comp);
	        adapter.notifyDataSetChanged();

			ListView listView = (ListView) view.findViewById(R.id.list);
	        listView.addHeaderView(headerView);
	        listView.setAdapter(adapter);
	        
	        initialized=true;
		} catch (RemoteException e) {
			Log.e(THIS_FILE, "Exception in setting up the certificate update fragment from service.");
		}

		Activity activity = getActivity();
		if (activity != null && activity instanceof  NotificationActivity){
			NotificationActivity notificationActivity = (NotificationActivity) activity;
			Toolbar toolbar = notificationActivity.getToolbar();
			if (toolbar !=null){
				toolbar.setTitle(R.string.keygen_header);
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
	final private Comparator<KeyGenProgress> comp = (e1, e2) -> {
        if (e1==null && e2==null) return 0;
        if (e1==null) return 1;
        if (e2==null) return -1;

        // done/error = uninteresting, end of the list.
        boolean e1Done = e1.getState()==KeyGenStateEnum.DONE || e1.getState()==KeyGenStateEnum.NONE;
        boolean e2Done = e2.getState()==KeyGenStateEnum.DONE || e2.getState()==KeyGenStateEnum.NONE;
        if ( e1Done && !e2Done) return 1;
        if (!e1Done &&  e2Done) return -1;

        // Generating vs. Generated.
        // Prefer active progress being top.
        boolean e1Gen  = e1.getState()==KeyGenStateEnum.GENERATING_KEY;
        boolean e2Gen  = e2.getState()==KeyGenStateEnum.GENERATING_KEY;
        boolean e1GenD = e1.getState()==KeyGenStateEnum.GENERATED;
        boolean e2GenD = e2.getState()==KeyGenStateEnum.GENERATED;
        if (e1Gen  && e2GenD) return -1;
        if (e1GenD && e2Gen ) return  1;

        // Same -> time check.
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
            intentfilter.addAction(Intents.ACTION_KEYGEN_UPDATE_PROGRESS);
            iReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    final String action = intent.getAction();
                    if (!Intents.ACTION_KEYGEN_UPDATE_PROGRESS.equals(action)){
                        throw new IllegalArgumentException("Unknown intent received");
                    }
                    
                    if (!intent.hasExtra(Intents.KEYGEN_INTENT_PROGRESS)){
                    	Log.w(THIS_FILE, "Intent does not have extra for progress");
                    	return;
                    }
                    
                    if (!initialized){
                    	Log.w(THIS_FILE, "Was not initialized, cannot continue");
                    	return;
                    }
                    
                    ArrayList<KeyGenProgress> progress = intent.getParcelableArrayListExtra(Intents.KEYGEN_INTENT_PROGRESS);
                    Log.v(THIS_FILE, "Update progress: " + intent + "; progress.size()=" + progress.size());
                    
                    adapter.clear();
                    adapter.setData(progress);
                    adapter.sort(comp);
                    adapter.notifyDataSetChanged();
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
	   Bundle state = new Bundle();
	   return state;
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
	private class KeyUpdateAdapter extends ArrayAdapter<KeyGenProgress> {
		private Activity context;
		private SimpleDateFormat dateFormat;
		
		public KeyUpdateAdapter(Context context, List<KeyGenProgress> objects) {
			super(context, R.layout.cert_check_row, objects);
			this.context = (Activity)context;
			this.dateFormat = new SimpleDateFormat("HH:mm:ss dd-MM-yyyy", Locale.getDefault());
		}
		
		public void setData(List<KeyGenProgress> data) {
		    clear();
		    if (data != null) {
	            for(KeyGenProgress item: data) {
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
		    final KeyGenProgress cProgress = this.getItem(position);
		    if (cProgress==null) {
		    	Log.w(THIS_FILE, "Empty cert update progress");
		    	return rowView;
		    }

			String username = SipUri.parseSipContact(cProgress.getUser()).userName;
		    holder.userName.setText(username);
		    holder.tstamp.setText(dateFormat.format(new Date(cProgress.getWhen())));
		    
		    int progressState=1;
		    Integer statusText = R.string.keygen_done;
		    String progressString = null;
		    
		    switch(cProgress.getState()){
		    	default:
		    	case DONE:
		    		progressState=100;
		    		statusText = R.string.keygen_done;
		    		break;
		    	case NONE:
		    		progressState=100;
		    		statusText = R.string.keygen_none;
		    		break;
		    	case IN_QUEUE:
		    		progressState=5;
		    		statusText = R.string.keygen_in_queue;
		    		break;
		    	case STARTED:
		    		progressState=10;
		    		statusText = R.string.keygen_started;
		    		break;
		    	case CLEANING:
		    		progressState=15;
		    		statusText = R.string.keygen_cleaning;
		    		break;
		    	case GENERATING_KEY:
		    		progressState=20;
		    		statusText = null; 
		    		progressString = String.format(context.getString(R.string.keygen_generating_key), cProgress.getAlreadyGeneratedKeys()+1, cProgress.getMaxKeysToGen());
		    		
		    		// Scale down progress from generation to 50%
		    		progressState += (int) Math.ceil(((double)cProgress.getAlreadyGeneratedKeys()/(double)cProgress.getMaxKeysToGen()) * 50.0);
		    		break;
		    	case GENERATED:
		    		progressState=70;
		    		statusText = R.string.keygen_generated; 
		    		break;
		    	case SERVER_CALL_SAVE:
		    		progressState=75;
		    		statusText = R.string.keygen_server_call_save;
		    		break;
		    	case POST_SERVER_CALL_SAVE:
		    		progressState=95;
		    		statusText = R.string.keygen_post_server_call_save;
		    	case DELETING:
		    		progressState=10;
		    		statusText = R.string.keygen_deleting;
		    	case SERVER_CALL_DELETE:
		    		progressState=50;
		    		statusText = R.string.keygen_server_call_delete;
		    	case POST_SERVER_CALL_DELETE:
		    		progressState=90;
		    		statusText = R.string.keygen_post_server_call_delete;
		    }
		    
		    holder.progress.setMax(100);
		    holder.progress.setProgress(progressState);
		    
		    // Status text
		    if (statusText!=null){
		    	holder.status.setText(statusText);
		    } else {
		    	holder.status.setText(progressString);
		    }
		    
		    return rowView;
		}

		@Override
		public void notifyDataSetChanged() {
			super.notifyDataSetChanged();
		}		
	}	
}


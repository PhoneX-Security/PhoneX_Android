package net.phonex.soap;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.widget.Toast;

import net.phonex.R;
import net.phonex.db.entity.SipClist;
import net.phonex.db.entity.SipProfile;
import net.phonex.db.entity.CallFirewall;
import net.phonex.db.scheme.CallFirewallScheme;
import net.phonex.db.scheme.CallLogScheme;
import net.phonex.ksoap2.SoapEnvelope;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.soap.entities.ContactlistAction;
import net.phonex.soap.entities.ContactlistChangeRequest;
import net.phonex.soap.entities.ContactlistChangeRequestElement;
import net.phonex.soap.entities.ContactlistChangeResponse;
import net.phonex.soap.entities.ContactlistReturn;
import net.phonex.soap.entities.UserIdentifier;
import net.phonex.ui.dialogs.AlertDialogFragment;
import net.phonex.util.ContactListUtils;
import net.phonex.util.Log;

import java.util.ArrayList;

/**
 * Async service for adding new contact to contact list.
 * 
 * @author ph4r05
 * docs: http://developer.android.com/reference/android/os/AsyncTask.html
 */
public class ClistRenameTask extends BaseAsyncTask<ClistRenameTaskParams>{
	public final static int CHECKPOINTS_NUMBER = 2; //how many progress checkpoints it needs

	public final static String TAG = "ClistAddTask";

	
	public ClistRenameTask(){		
	}
	public ClistRenameTask(OnClistAddTaskCompleted listener){
		onCompletedListener = listener;
	}

	public interface OnClistAddTaskCompleted {
	  void onClistAddTaskCompleted();
	}

	private OnClistAddTaskCompleted onCompletedListener;	
	private ClistRenameTaskParams taskParams;



	@Override
	protected Exception doInBackground(ClistRenameTaskParams... arg0) {
		if (arg0.length==0){
			throw new IllegalArgumentException("Empty configuration");
		}
		
		taskParams = arg0[0];

		// HTTP transport - declare before TRY block to be able to 
		// extract response in catch block for debugging
		//HttpTransportSE androidHttpTransport = null;
		try {
            final SSLSOAP.SSLContextHolder sslContextHolder = prepareSoapDefault(taskParams.getStoragePass().toCharArray());

            /**
			 * Get current logged user - for suffix completion
			 */
			ArrayList<SipProfile> profiles = SipProfile.getAllProfiles(this.getContext(), true);
			if (profiles==null || profiles.isEmpty()){
				Log.i(TAG, "empty active profile list, cannot add new user");
				return null;
			}
			
			SipProfile profile = profiles.get(0);
			final String domain = profile.getSipDomain();
			Log.df(TAG, "Default domain determined: [%s]", domain);
			
			/**
			 * Obtaining client certificate
			 */
    		if (this.isCancelled()){
    			return null;
    		}
    	    		
    		// is contact already in database?
    		String selection = SipClist.FIELD_SIP + "=?";
            String[] selectionArgs = new String[] { taskParams.getUserName() };
    		Cursor c = this.context.getContentResolver().query(SipClist.CLIST_URI, SipClist.LIGHT_PROJECTION, selection, selectionArgs, null);
    		if (c==null || c.getCount() <= 0){
    			Log.i(TAG, "There is no such user in database.");
    			//this.publishProgress(new DefaultAsyncProgress(1, "Done"));
    			return null;
    		}
    		
    		// set request to modify server side contact-list    		
    		publishProgress(getContext().getString(R.string.p_sending_request));
    		
    		ContactlistChangeRequest clChangeReq = new ContactlistChangeRequest();
    		ContactlistChangeRequestElement cElem = new ContactlistChangeRequestElement();
    		UserIdentifier ui2 = new UserIdentifier();
    		ui2.setUserSIP(taskParams.getUserName());
    		cElem.setAction(ContactlistAction.UPDATE);
    		cElem.setUser(ui2);
    		cElem.setDisplayName(taskParams.getNewDisplayName());
    		clChangeReq.add(cElem);
    		
    		// create envelope
    		SoapSerializationEnvelope soapEnvelope = new SoapSerializationEnvelope(SoapEnvelope.VER11);
    		clChangeReq.register(soapEnvelope);
    		new ContactlistChangeResponse().register(soapEnvelope);
    	    soapEnvelope.setOutputSoapObject(clChangeReq);
    	       	    
    	    Object obj = null;
    	    obj = TaskSOAPHelper.simpleSOAPRequest(soapEnvelope,
					ServiceConstants.getDefaultURL(domain, this.context),
					"contactlistChangeRequest", true, sslContextHolder);
			            
            Log.inf(TAG, "Pure object response retrieved, class: %s; %s", obj.getClass().getCanonicalName(), obj.toString());
            if (!(obj instanceof ContactlistChangeResponse)){
            	Log.w(TAG, "Bad format of response from server - probably problem during unmarshaling");
            	throw new IllegalArgumentException("Bad format of response");
            }
    		
            final ContactlistChangeResponse clresp = (ContactlistChangeResponse) obj;
            if (clresp.getPropertyCount()<1){
            	Log.w(TAG, "Empty response");
            	throw new IllegalArgumentException("Empty response");
            }
            
            ContactlistReturn clreturn = clresp.get(0); 
            Log.inf(TAG, "Integer response: %s", clreturn);
            if (clreturn.getResultCode() < 0){
            	Log.w(TAG, "Something wrong during updating the contact");
            	throw new IllegalArgumentException("Cannot remove from contactlist on server");
            }            
           
            publishProgress(getContext().getString(R.string.p_renaming_contact_localy));

            // Rename in contact list.
            ContentValues args = new ContentValues();
            args.put(SipClist.FIELD_DISPLAY_NAME, taskParams.getNewDisplayName());
        	String selection2 = SipClist.FIELD_SIP + "=?";
            String[] selectionArgs2 = new String[] { taskParams.getUserName() };
            int rowsRenamed = this.context.getContentResolver().update(SipClist.CLIST_URI, args, selection2, selectionArgs2);
            Log.inf(TAG, "Number of contacts renamed: %d", rowsRenamed);

            // Rename in call logs.
            args = new ContentValues();
            args.put(CallLogScheme.FIELD_REMOTE_ACCOUNT_NAME, taskParams.getDiplayName());
            selection2 = CallLogScheme.FIELD_REMOTE_ACCOUNT_ADDRESS + "=?";
            selectionArgs2 = new String[] { taskParams.getUserName() };
            rowsRenamed = this.context.getContentResolver().update(CallLogScheme.URI, args, selection2, selectionArgs2);
            Log.inf(TAG, "Number of calllogs renamed: %d", rowsRenamed);

            // Update Call firewall rules.
            resyncFirewallRules(context, profile.getId());
			Log.i(TAG, "Finished properly");
		} catch (Exception e) {
			Log.e(TAG, "Exception", e);			

			return e;
		}
		
		return null;
	}
	
	/**
	 * Re-synchronizes filters after edit operation
	 */
	public static void resyncFirewallRules(Context context, long accountId){
		// get whole contactlist for given account and add to filters
		ArrayList<String> sips = new ArrayList<String>();
		try {
			Cursor c = context.getContentResolver().query(SipClist.CLIST_URI, SipClist.LIGHT_PROJECTION, 
				SipClist.FIELD_ACCOUNT + "=" + accountId, 
				null, null);
			if (c==null){
				Log.w(TAG, "Cursor is null, cannot resync, better not to continue;");
				return;
			}
			
			while(c.moveToNext()){
				try {
					SipClist curCl = new SipClist(c);
					final String sip = curCl.getSip();
					if (sip==null || sip.length()==0) continue;
					
					Log.df(TAG, "Contactlist person: [%s] will be added to filter", sip);
					sips.add(sip);
				} catch(Exception e){
					Log.w(TAG, "Cannot convert cursor to sip?", e);
				}
			}
		}catch(Exception e){
			Log.w(TAG, "Cannot load contactlist for user, exception; ", e);
			return;
		}
		
		// delete all filters having canAnswer as action
		context.getContentResolver().delete(CallFirewallScheme.URI,
				CallFirewallScheme.FIELD_ACTION + "=" + CallFirewall.ACTION_CAN_ANSWER, null);
		
		// resync
		for(String sip:sips){
			ContactListUtils.addToFilterWhitelist(context, accountId, sip);
		}
	}
	
	@Override
	protected void onPostExecute(Exception result) {
		if (mFragment==null) return;
		
		if (result == null){
            String oldName = taskParams != null ? taskParams.getDiplayName() : "";
            String newName = taskParams != null ? taskParams.getNewDisplayName() : "";
            Toast.makeText(getContext(), String.format(getContext().getString(R.string.p_rename_success), oldName, newName), Toast.LENGTH_LONG).show();
			mFragment.taskFinished(result);
	
			if (onCompletedListener!=null)
				onCompletedListener.onClistAddTaskCompleted();			
		
		} else {
			AlertDialogFragment.newInstance(getContext().getString(R.string.p_problem), getContext().getString(R.string.p_problem_nonspecific)).show(fragmentManager, "alert");
			mFragment.taskFinished(result);  
		}
	}

	
}


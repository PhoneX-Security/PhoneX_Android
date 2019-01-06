package net.phonex.soap;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.widget.Toast;

import net.phonex.R;
import net.phonex.db.entity.SipClist;
import net.phonex.db.entity.SipProfile;
import net.phonex.db.entity.CallFirewall;
import net.phonex.db.entity.SipMessage;
import net.phonex.db.scheme.CallFirewallScheme;
import net.phonex.db.scheme.CallLogScheme;
import net.phonex.ksoap2.SoapEnvelope;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.pref.PreferencesConnector;
import net.phonex.pref.PreferencesManager;
import net.phonex.soap.GenKeyParams.GenKeyForUser;
import net.phonex.soap.entities.ContactlistAction;
import net.phonex.soap.entities.ContactlistChangeRequest;
import net.phonex.soap.entities.ContactlistChangeRequestElement;
import net.phonex.soap.entities.ContactlistChangeResponse;
import net.phonex.soap.entities.ContactlistReturn;
import net.phonex.soap.entities.UserIdentifier;
import net.phonex.ft.DHKeyHelper;
import net.phonex.ui.dialogs.AlertDialogFragment;
import net.phonex.util.ContactListUtils;
import net.phonex.util.Log;
import net.phonex.util.crypto.PRNGFixes;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;


/**
 * Async service for removing contact from contact list.
 * 
 * @author ph4r05
 * docs: http://developer.android.com/reference/android/os/AsyncTask.html
 */
public class ClistRemoveTask extends BaseAsyncTask<ClistAddTaskParams>{
	public final static int CHECKPOINTS_NUMBER = 2; //how many progress checkpoints it needs

	public final static String TAG = "ClistAddTask";
	private ClistAddTaskParams taskParams;
	
	public ClistRemoveTask(){		
	}
	public ClistRemoveTask(ClistRenameTask.OnClistAddTaskCompleted listener){
		onCompletedListener = listener;
	}
	
	private ClistRenameTask.OnClistAddTaskCompleted onCompletedListener;
	
	@Override
	protected Exception doInBackground(ClistAddTaskParams... arg0) {
		if (arg0.length==0){
			throw new IllegalArgumentException("Empty configuration");
		}
		
		taskParams = arg0[0];
		PreferencesConnector prefs = new PreferencesConnector(getContext());

		// HTTP transport - declare before TRY block to be able to 
		// extract response in catch block for debugging
		Exception toThrow = null;
		try {
			/**
			 * Init SecureRandom, it may take some time, thus it is initialized in
			 * background task. It is also needed prior first SSL connection
			 * or key generation.
			 */
			PRNGFixes.apply();
			SecureRandom rand = new SecureRandom();
			Log.vf(TAG, "SecureRand initialized, next int=%s", rand.nextInt());

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
    		
    		// Set clist in sync - avoid presence updates.
    		prefs.setBoolean(PreferencesManager.CLIST_IN_SYNC, true);
    		
    		// is contact already in database?
    		String selection = SipClist.FIELD_SIP + "=?";
            String[] selectionArgs = new String[] { taskParams.getUserName() };
    		Cursor c = this.context.getContentResolver().query(SipClist.CLIST_URI, SipClist.LIGHT_PROJECTION, selection, selectionArgs, null);
    		if (c==null || c.getCount() <= 0){
    			Log.i(TAG, "There is no such user in database.");
    			//this.publishProgress(new DefaultAsyncProgress(1, "Done"));
    			
    			prefs.setBoolean(PreferencesManager.CLIST_IN_SYNC, false);
    			return null;
    		}
    		
    		// set request to modify server side contact-list    		
    		publishProgress(getContext().getString(R.string.p_deleting_contact));
    		
    		ContactlistChangeRequest clChangeReq = new ContactlistChangeRequest();
    		ContactlistChangeRequestElement cElem = new ContactlistChangeRequestElement();
    		UserIdentifier ui2 = new UserIdentifier();
    		ui2.setUserSIP(taskParams.getUserName());
    		cElem.setAction(ContactlistAction.REMOVE);
    		cElem.setUser(ui2);
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
            	Log.w(TAG, "Something wrong during removing from contact list");
            	throw new IllegalArgumentException("Cannot remove from contactlist on server");
            }            
            
            // Start deleting local artifacts.
            publishProgress(new BaseAsyncProgress(getContext().getString(R.string.p_deleting_contact_localy)).setIndeterminate(Boolean.TRUE));

            // Delete from contact list.
        	String selection2 = SipClist.FIELD_SIP + "=?";
            String[] selectionArgs2 = new String[] { taskParams.getUserName() };
            int rowsDeleted = this.context.getContentResolver().delete(SipClist.CLIST_URI, selection2, selectionArgs2);
            Log.inf(TAG, "Number of contacts deleted: %d", rowsDeleted);

            // Delete from messages.
            Uri.Builder threadUriBuilder = SipMessage.THREAD_ID_URI_BASE.buildUpon();
            threadUriBuilder.appendEncodedPath(taskParams.getUserName());
            this.context.getContentResolver().delete(threadUriBuilder.build(), null, null);

            // Delete from call log.
            selection2 = CallLogScheme.FIELD_REMOTE_ACCOUNT_ADDRESS + "=?";
            selectionArgs2 = new String[] { taskParams.getUserName() };
            rowsDeleted = this.context.getContentResolver().delete(CallLogScheme.URI, selection2, selectionArgs2);
            Log.inf(TAG, "Number of calllogs deleted: %d", rowsDeleted);
    		
            // update filters table
            resyncFilters(context, profile.getId());
            
            // Update final progress
            publishProgress(new BaseAsyncProgress(getContext().getString(R.string.p_deleting_contact_artifacts)).setIndeterminate(Boolean.TRUE).setDeltaStep(0));
            
            // Delete DH keys associated with this user from server and from local database.
            GenKeyParams gkp = taskParams.getGenKeyParams();
         	if (gkp!=null){
         		Log.i(TAG, "Removing DHkeys from server");
         		
         		// Key task initialization.
         		GenKeyCall genKeyTask = new GenKeyCall();
         		genKeyTask.setContext(context);
         		
         		// If no identity is provided, load it from global data.
         		boolean identityOk = true;
         		if (taskParams.isLoadIdentity()){
         			int code = genKeyTask.loadIdentity(gkp, rand);
         			if (code < 0){
         				identityOk=false;
         				Log.wf(TAG, "Cannot generate DH keys, identity cannot be loaded, code=%s", code);
         			}
         		}
         		
         		// Is no user is specified, current user will be filled in.
         		// If no user is specified, all keys would be removed.
         		if (gkp.getUserList()==null || gkp.getUserList().isEmpty()){
         			GenKeyForUser uKey = new GenKeyForUser();
         			uKey.setUserSip(taskParams.getUserName());
         			
         			List<GenKeyForUser> uList = new LinkedList<GenKeyForUser>();
         			uList.add(uKey);
         			 
         			gkp.setUserList(uList);
         		}
         		
         		// Initialize task an invoke main work method.
         		if (identityOk){
         			genKeyTask.deleteKeys(gkp, rand);
         		}
         		
         		// Delete also from database
         		Log.i(TAG, "Removing DHkeys from database");
         		DHKeyHelper dhhelper = new DHKeyHelper(getContext());
         		dhhelper.removeDHKeysForUser(taskParams.getUserName());
         	}
            
			Log.i(TAG, "Finished properly");
		} catch (Exception e) {
			Log.e(TAG, "Exception", e);			

			toThrow = e;
		} finally {
			prefs.setBoolean(PreferencesManager.CLIST_IN_SYNC, false);
		}
		
		return toThrow;
	}
	
	/**
	 * Re-synchronizes filters after edit operation
	 */
	public static void resyncFilters(Context context, long accountId){
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
		ContactListUtils.addToFilterWhitelist(context, accountId, sips);
	}
	
	@Override
	protected void onPostExecute(Exception result) {
		if (mFragment==null) return;
		
		if (result == null){
            String text = taskParams != null ? String.format(getContext().getString(R.string.p_delete_success), taskParams.getDiplayName()) : "";
            Toast.makeText(getContext(), text, Toast.LENGTH_LONG).show();
			
			mFragment.taskFinished(result);
	
			if (onCompletedListener!=null)
				onCompletedListener.onClistAddTaskCompleted();			
		
		} else {
			AlertDialogFragment.newInstance(getContext().getString(R.string.p_problem), getContext().getString(R.string.p_problem_nonspecific)).show(fragmentManager, "alert");
			mFragment.taskFinished(result);  
		}
	}

	
}


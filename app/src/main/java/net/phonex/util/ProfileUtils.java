package net.phonex.util;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;

import net.phonex.autologin.AutoLoginManager;
import net.phonex.autologinQuick.PreferencesLoginStorage;
import net.phonex.core.Intents;
import net.phonex.core.MemoryPrefManager;
import net.phonex.db.entity.SipClist;
import net.phonex.db.entity.SipProfile;
import net.phonex.service.MessageSecurityManager;
import net.phonex.soap.TaskWithCallback;
import net.phonex.soap.accountSettings.SetUserAsLoggedOffCall;
import net.phonex.ui.PhonexActivity;
import net.phonex.util.android.StatusbarNotifications;

import java.util.ArrayList;
import java.util.Arrays;


public class ProfileUtils {
	private static final String THIS_FILE = "ProfileUtils";

    public static void logout(Activity activity){
        Log.wf(THIS_FILE, "Sending intent to logout");

        Intent i = new Intent(activity, PhonexActivity.class);
        i.setAction(Intents.ACTION_LOGOUT);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP );
        activity.startActivity(i);
    }

    public static void sendUnregisterIntent(Activity activity) {
        Log.d(THIS_FILE, "True disconnection...");
        Intent intent = new Intent(Intents.ACTION_OUTGOING_UNREGISTER);
        intent.putExtra(Intents.EXTRA_OUTGOING_ACTIVITY, new ComponentName(activity, activity.getClass()));
		MiscUtils.sendBroadcast(activity, intent);
    }


	/**
	 * Called when quitting app, deletes all sensitive data on the device
	 * @param
	 */
    public static void secureQuit(Context ctx, boolean cannotBeAutoStarted){
		Log.df(THIS_FILE, "Secure quit procedure started; cannotBeAutoStarted = %s", cannotBeAutoStarted);

		if (cannotBeAutoStarted) {
			Log.df(THIS_FILE, "secureQuit; setting user as logged out remotely");
			SetUserAsLoggedOffCall call = new SetUserAsLoggedOffCall(ctx);
			call.run();
			Exception thrownException = call.getThrownException();
			if (thrownException != null) {
				Log.wf(THIS_FILE, "Unable to set user as logged of on remote server");
			}
			MessageSecurityManager.deleteExpiredMessages(ctx);
			MessageSecurityManager.deleteLeftoversFromMessageQueue(ctx);
		}

		StatusbarNotifications notifications = new StatusbarNotifications(ctx);
		notifications.cancelAll();

    	try {
			if (cannotBeAutoStarted){
				deleteProfileAndContacts(ctx.getContentResolver());
				// If we delete local login data, app won't be started by gcm
				PreferencesLoginStorage.deleteLogin(ctx);
			}
			// delete TLS password from shared preferences
			MemoryPrefManager.clearCredentials(ctx);

    	} catch (Exception e){
    		Log.e(THIS_FILE, "Cannot delete password on user exit", e);
    	}
    }

	public static void deleteProfileAndContacts(ContentResolver cr){
		cr.delete(SipProfile.ACCOUNT_URI, null, null);
		cr.delete(SipClist.CLIST_URI, null, null);
		Log.d(THIS_FILE, "Account, messages and calllog deleted");
	}
	
    /**
     * Returns array of profiles having defined accountManager.
     * 
     * @param wizard
     * @param projection - must contain SipProfile.FIELD_ID
     * @param ctxt
     * @return
     */
    public static SipProfile[] getProfilesByManager(String wizard, String[] projection, Context ctxt){
    	return getProfilesByManager(wizard, projection, null, null, ctxt);
    }
    
	public static SipProfile[] getProfilesByManager(String wizard, String[] projection, String selection, String[] selectionArgs, Context ctxt){
		ArrayList<SipProfile> tmpList = new ArrayList<SipProfile>();
		
		// projection ID check
		boolean projectionOK = false;
		for(int i=0; i<projection.length && projectionOK==false; i++){
			if (SipProfile.FIELD_ID.equals(projection[i])) projectionOK = true;
		}
		
		if (!projectionOK){
			final int n   = projection.length;
			projection    = Arrays.copyOf(projection, n + 1);
			projection[n] = SipProfile.FIELD_ID;
			Log.w(THIS_FILE, "Projection didn't contain FIELD_ID");
		}
		
		String[] finalSelectionArgs = new String[] { wizard };
		if (selectionArgs != null && selectionArgs.length > 0){
			finalSelectionArgs = DatabaseUtils.appendSelectionArgs(finalSelectionArgs, selectionArgs);
		}
		
    	Cursor c = ctxt.getContentResolver().query(SipProfile.ACCOUNT_URI, 
    			projection, 
    			DatabaseUtils.concatenateWhere(SipProfile.FIELD_ACCOUNT_MANAGER + "=?", selection),
    			finalSelectionArgs, 
    			null);
        
        if (c != null) {
            try {
            	while(c.moveToNext()){
            		SipProfile sP = new SipProfile(c);
            		if (sP.getId() == SipProfile.INVALID_ID) continue;
            		
            		tmpList.add(sP);
            	}
            } catch (Exception e) {
                Log.e(THIS_FILE, "Error while getting accountManager", e);
            } finally {
                c.close();
            }
        }          
		
        SipProfile[] toReturn = new SipProfile[tmpList.size()]; 
		return tmpList.toArray(toReturn);
	}
    
}

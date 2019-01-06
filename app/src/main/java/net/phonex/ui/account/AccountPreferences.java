package net.phonex.ui.account;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import net.phonex.R;
import net.phonex.db.entity.SipProfile;
import net.phonex.ui.preferences.BasePreferences;
import net.phonex.util.Log;
import net.phonex.util.account.AccountManagerDesc;
import net.phonex.util.account.AccountManagerFactory;
import net.phonex.util.account.IAccountManager;

/**
 * Also see PhonexAccountManager - the main account preference class
 */
public class AccountPreferences extends BasePreferences {
    private static final String THIS_FILE = "AccountPreferences";

    private static final String ACCOUNT_MANAGER_PREF_ID = "AccountManager";

    private static final int BASE_CODE = 0;
    public static int CHANGE_PASSWORD_REQ_CODE = BASE_CODE + 1;
    public static int MY_CERTIFICATE_REQ_CODE = BASE_CODE + 2;
    public static int CHANGE_RECOVERY_EMAIL_REQ_CODE = BASE_CODE + 3;
    private static final int MAX_ACTIVITY_ID = CHANGE_RECOVERY_EMAIL_REQ_CODE;

	protected SipProfile account = null;
	private String accountManagerId = "";
	private IAccountManager accountManager = null;
	private boolean isResumed = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Intent intent = getIntent();
		long accountId = intent.getLongExtra(SipProfile.FIELD_ID, SipProfile.INVALID_ID);

		setManagerId(intent.getStringExtra(SipProfile.FIELD_ACCOUNT_MANAGER));

		account = SipProfile.getProfileFromDbId(this, accountId, SipProfile.FULL_PROJECTION);

		super.onCreate(savedInstanceState);

		final String accName = account != null ? account.getDisplayName() : "???";
        accountManager.fillLayout(account);
        setTitle(getString(R.string.account_name) + ": " + accName);
	}

	@Override
	protected void onResume() {
		super.onResume();
        isResumed = true;
		updateDescriptions();
		accountManager.onStart();
	}

	@Override
	protected void onPause() {
	    super.onPause();
	    isResumed = false;
	    accountManager.onStop();
	}

	private boolean setManagerId(String accMgrId) {
		if (accountManagerId == null) {
			return setManagerId(AccountManagerFactory.getDefaultManagerId());
		}

		AccountManagerDesc accountManagerDesc = AccountManagerFactory.getManager(accMgrId);
		if (accountManagerDesc == null) {
            return setManagerId(AccountManagerFactory.getDefaultManagerId());
		}

		try {
			accountManager = (IAccountManager) accountManagerDesc.getClassRef().newInstance();
		} catch (IllegalAccessException e) {
			Log.ef(THIS_FILE, e, "Account manager not found for %s", accMgrId);
            return setManagerId(AccountManagerFactory.getDefaultManagerId());
		} catch (InstantiationException e) {
            Log.ef(THIS_FILE, e, "Account manager not found for %s", accMgrId);
            return setManagerId(AccountManagerFactory.getDefaultManagerId());
		}

		accountManagerId = accMgrId;
		accountManager.setParent(this);
		if(getActionBar() != null) {
            getActionBar().setIcon(AccountManagerFactory.getManager(accMgrId).getIcon());
		}

		return true;
	}



	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
	    if(isResumed) {
    		updateDescriptions();
	    }
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == CHANGE_PASSWORD_REQ_CODE || requestCode == CHANGE_RECOVERY_EMAIL_REQ_CODE){
//			finish();
		}

		if (requestCode == MY_CERTIFICATE_REQ_CODE){
//			finish();
		}

		if(requestCode > MAX_ACTIVITY_ID) {
		    accountManager.onActivityResult(requestCode, resultCode, data);
		}
	}

	public SipProfile getAccount() {
		return account;
	}
	
	@Override
	protected void onDestroy() {
	    super.onDestroy();
	    getSharedPreferences(ACCOUNT_MANAGER_PREF_ID, MODE_PRIVATE).edit().clear().commit();
	}

	@Override
	protected int getXmlPreferences() {
		return accountManager.getBasePreferenceResource();
	}

	@Override
	protected void updateDescriptions() {
		accountManager.updateDescriptions();
	}
	
	@Override
	protected String getDefaultFieldSummary(String fieldName) {
		return accountManager.getDefaultFieldSummary(fieldName);
	}
	
	@Override
	public SharedPreferences getSharedPreferences(String name, int mode) {
	    return super.getSharedPreferences(ACCOUNT_MANAGER_PREF_ID, mode);
	}

	@Override
	protected String activityAnalyticsName() {
		return this.getClass().getSimpleName();
	}

}

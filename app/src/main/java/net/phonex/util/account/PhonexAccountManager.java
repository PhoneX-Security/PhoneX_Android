
package net.phonex.util.account;

import android.content.Intent;
import android.preference.Preference;
import android.widget.Toast;

import net.phonex.BuildConfig;
import net.phonex.PhonexSettings;
import net.phonex.R;
import net.phonex.core.Constants;
import net.phonex.core.Intents;
import net.phonex.db.entity.SipProfile;
import net.phonex.pref.PhonexConfig;
import net.phonex.pref.PreferencesManager;
import net.phonex.service.XService;
import net.phonex.ui.account.AccountPreferences;
import net.phonex.ui.dialogs.AlertDialogFragment;
import net.phonex.ui.logUpload.LogUploadActivity;
import net.phonex.ui.preferences.ChangePasswordActivity;
import net.phonex.ui.preferences.ChangeRecoveryEmailActivity;
import net.phonex.ui.preferences.MyCertificateActivity;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.ProfileUtils;
import net.phonex.util.system.ProcKiller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;


public class PhonexAccountManager extends AbstractAccountManager {
	private static final String TAG = "PhonexAccountManager";
	
	protected Preference changePassword;
	protected Preference recoveryEmail;
	protected Preference myCertificate;
	protected Preference syncCertificates;
	protected Preference syncDHKeys;
	protected Preference restartStack;
	protected Preference sendLogs;
	protected Preference logOut;

	protected static final String CHANGE_PASSWORD = "change_password";
	protected static final String RECOVERY_EMAIL = "recovery_email";
	protected static final String MY_CERTIFICATE  = "my_certificate";
	protected static final String SYNC_CERTIFICATES  = "sync_certificates";
	protected static final String SYNC_DHKEYS     = "sync_dhkeys";
	protected static final String RESTART_STACK   = "restart_stack";
	protected static final String SEND_LOGS   = "send_logs";
	protected static final String LOG_OUT     = "log_out";

	// store account locally after save - for ZRTP change
	protected SipProfile localAccount;
	protected boolean localAccountValid=false;

    /**
     * Returns true if manager id matches this manager.
     * @param id
     * @return
     */
    static public boolean idMatches(String id){
        return getId().equalsIgnoreCase(id) || getLabel().equalsIgnoreCase(id);
    }

    /**
     * Returns preferred system identifier of this manager.
     *
     * @return
     */
    static public String getId() {
        return "PHOENIX";
    }

    /**
     * Returns preferred human readable label for this manager.
     *
     * @return
     */
    static public String getLabel() {
        return "PhoneX";
    }

	protected String getDomain() {
		return "PhoneX";
	}
	
	protected void bindFields() {
		// Run change password activity
		changePassword = parent.findPreference(CHANGE_PASSWORD);
		changePassword.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(parent, ChangePasswordActivity.class);
            intent.putExtra("SipProfile", parent.getAccount());
            parent.startActivityForResult(intent, AccountPreferences.CHANGE_PASSWORD_REQ_CODE); // onActivityResult in parent will be called after finish
            return true;
        });

		// Run change recovery email activity
		recoveryEmail = parent.findPreference(RECOVERY_EMAIL);
		recoveryEmail.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(parent, ChangeRecoveryEmailActivity.class);
            parent.startActivityForResult(intent, AccountPreferences.CHANGE_RECOVERY_EMAIL_REQ_CODE);// onActivityResult in parent will be called after finish
            return true;
        });

		// My certificate activity
		myCertificate = parent.findPreference(MY_CERTIFICATE);
		myCertificate.setOnPreferenceClickListener(preference -> {

            Intent intent = new Intent(parent, MyCertificateActivity.class);
            intent.putExtra("SipProfile", parent.getAccount());
            parent.startActivityForResult(intent, AccountPreferences.MY_CERTIFICATE_REQ_CODE);// onActivityResult in parent will be called after finish
            return true;
        });
		
		// Sync certificates
		syncCertificates = parent.findPreference(SYNC_CERTIFICATES);
		syncCertificates.setOnPreferenceClickListener(arg0 -> {
            XService.triggerCertUpdate(parent, new ArrayList<>(), true);
            Toast t = Toast.makeText(parent, R.string.sync_certificates_toast, Toast.LENGTH_LONG);
            t.show();
            return true;
        });
		
		// Sync dhkeys
		syncDHKeys = parent.findPreference(SYNC_DHKEYS);
		syncDHKeys.setOnPreferenceClickListener(arg0 -> {
            XService.triggerDHKeyUpdate(parent);
            Toast t = Toast.makeText(parent, R.string.sync_dhkeys_toast, Toast.LENGTH_LONG);
            t.show();
            return true;
        });
		
		// Restart stack
		restartStack = parent.findPreference(RESTART_STACK);
		restartStack.setOnPreferenceClickListener(arg0 -> {
            Log.v(TAG, "Going to restart app engine");

            // Give service a chance to restart itself using intent.
            MiscUtils.sendBroadcast(parent, new Intent(Intents.ACTION_SIP_REQUEST_BRUTAL_RESTART));

            Toast t = Toast.makeText(parent, R.string.restart_stack_toast, Toast.LENGTH_SHORT);
            t.show();

            // If service is deadlocked, try to kill it from this task.
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
					ProcKiller.killPhoneX(parent, true, true, true, true, true);
                } catch(Exception e){
                    Log.e(TAG, "Cannot restart svc", e);
                }
            }).start();

            parent.finish();
            return true;
        });

		sendLogs = parent.findPreference(SEND_LOGS);
		if (!BuildConfig.DEBUG){
			parent.hidePrefEntry(null, SEND_LOGS);
		} else {
			sendLogs.setOnPreferenceClickListener(preference -> {
				Intent intent = new Intent(parent, LogUploadActivity.class);
				parent.startActivity(intent);
				return true;
			});
		}

		logOut = parent.findPreference(LOG_OUT);
		logOut.setOnPreferenceClickListener(preference -> {
			AlertDialogFragment.newInstance(null, parent.getString(R.string.quit_info))
					.setPositiveButton(parent.getString(R.string.ok), (dialog, which) -> {
						ProfileUtils.logout(parent);
						parent.finish();
					})
					.setNegativeButton(parent.getString(R.string.cancel), null)
					.show(parent.getFragmentManager(), "");
			return false;
		});
	}
	
	public void fillLayout(final SipProfile account) {
		bindFields();
		fillLayoutExt(account);
	}

	public void updateDescriptions() {
	}
	
	private static HashMap<String, Integer> SUMMARIES = new  HashMap<String, Integer>(){
		private static final long serialVersionUID = -2319815745564L;
		{}
	};
	
	@Override
	public String getDefaultFieldSummary(String fieldName) {
		Integer res = SUMMARIES.get(fieldName);
		if(res != null) {
			return parent.getString( res );
		}
		return "";
	}

	public boolean canSave() {
		return true;
	}

	public void fillLayoutExt(final SipProfile account) {
		this.localAccount = account;
		this.localAccountValid = true;
	}
    
	/**
	 * Builds account without relation to activity
	 * 
	 * @param account
	 * @return
	 */
	public SipProfile buildAccountClean(SipProfile account, PreferencesManager prefs){
		account.setUse_srtp(0);
		account.setUse_zrtp(1);
		account.setTransport(PhonexSettings.useTLS() ? SipProfile.TRANSPORT_TLS : SipProfile.TRANSPORT_TCP);
		account.setAcc_id(account.getDisplay_name() + (account.getTransport() == SipProfile.TRANSPORT_TLS ? " <sips:" : "<sip:") + account.getUsername() + ">");
	
		if (Pattern.matches("[^<]*<sip(s)?:[^@]*@[^@]*>", account.getAcc_id())==false){
			Log.e(TAG, "Illegal username format");
		}
		
		// Default URI is important for calling and messaging.
		// If contact has empty scheme (no sip:, sips:), this scheme
		// is automatically prepended.
		account.setDefault_uri_scheme(PhonexSettings.useTLS() ? Constants.PROTOCOL_SIPS : Constants.PROTOCOL_SIP);
		
		account.setPublish_enabled(1);
		account.setIce_cfg_use(1);
		account.setIce_cfg_enable(1);
		account.setMedia_stun_use(1);
		account.setSip_stun_use(1);
		account.setTurn_cfg_enable(1);
		account.setTurn_cfg_use(1);
		account.setTurn_cfg_server(PhonexSettings.getTurnServer());

		//Ensure registration timeout value
		final String domain = account.getUsername().split("@")[1];
		
		// Just 5 minutes for SIP REGISTER, long registration causes problems
		// problems in case of connection drops - blind registrations
		account.setReg_timeout(300);
		account.setProxies(null);
		account.setTry_clean_registers(1);
		account.setScheme(SipProfile.CRED_SCHEME_DIGEST);
		account.setDatatype(SipProfile.CRED_DATA_PLAIN_PASSWD);
		account.setRealm(domain);
		
		// reg uri depends on port choosen
		if (account.getTransport() == SipProfile.TRANSPORT_TLS){
			account.setReg_uri("sips:"+domain+":5061");
		} else {
			account.setReg_uri("sip:"+domain+":5060");
		}
		
		// store account locally
		this.localAccount = account;
		this.localAccountValid = true;

		Log.df(TAG, "buildAccount(); acc: %s", account);
		Log.df(TAG, "c. regUri: %s", account.getReg_uri());
		Log.df(TAG, "c. accId: %s", account.getAcc_id());

		this.setDefaultParams(prefs);
		Log.d(TAG, "Prefs set");
		
		return account;
	}
	
    /**
     * Builds account - corrects some values, reflect change from GUI
     */
	public SipProfile buildAccount(SipProfile account) {

        // this is not retrieved from UI
        account.setDisplay_name(localAccount.getDisplay_name());

		account.setUse_srtp(0);
		account.setUse_zrtp(1);
		account.setTransport(PhonexSettings.useTLS() ? SipProfile.TRANSPORT_TLS : SipProfile.TRANSPORT_TCP);
		account.setAcc_id(account.getDisplay_name() + " <sips:" + account.getUsername() + ">");
	
		if (!Pattern.matches("[^<]*<sip(s)?:[^@]*@[^@]*>", account.getAcc_id())){
			Log.e(TAG, "Illegal username format");
		}

		//Ensure registration timeout value
		final String domain = account.getUsername().split("@")[1];
		account.setReg_timeout(300);
		account.setProxies(null);
		account.setTry_clean_registers(1);
		account.setScheme(SipProfile.CRED_SCHEME_DIGEST);
		account.setDatatype(SipProfile.CRED_DATA_PLAIN_PASSWD);
		//account.data = getText(accountPassword);
		account.setRealm(domain);
		
		// Default URI is important for calling and messaging.
		// If contact has empty scheme (no sip:, sips:), this scheme
		// is automatically prepended.
		account.setDefault_uri_scheme(PhonexSettings.useTLS() ? Constants.PROTOCOL_SIPS : Constants.PROTOCOL_SIP);
		
		// reg uri depends on port choosen
		if (account.getTransport() == SipProfile.TRANSPORT_TLS){
			account.setReg_uri("sips:"+domain+":5061");
		} else {
			account.setReg_uri("sip:"+domain+":5060");
		}
		
		// store account locally
		this.localAccount = account;
		this.localAccountValid = true;

		// some logging rubbish
		Log.df(TAG, "buildAccount(); acc: %s", account);
		Log.df(TAG, "trans: %s", account.getTransport());
		Log.df(TAG, "regUri: %s", account.getReg_uri());
		Log.df(TAG, "accId: %s", account.getAcc_id());
		Log.df(TAG, "srtp (-1=def, 0=off, 1=opt, 2=mand): %s", account.getUse_srtp());
		Log.df(TAG, "zrtp (1=off, 2=on): %s", account.getUse_zrtp());
		return account;
	}
	
	@Override
	public void setDefaultParams(PreferencesManager prefs) {
		//super.setDefaultParams(prefs);
		Log.d(TAG, "DefaultParams setting...");
		prefs.setBoolean(PhonexConfig.USE_COMPACT_FORM, true);
		prefs.setBoolean(PhonexConfig.ENABLE_ICE, true);
		prefs.setBoolean(PhonexConfig.ENABLE_STUN, true);
		prefs.setBoolean(PhonexConfig.ENABLE_TURN, true);
		prefs.setBoolean(PhonexConfig.ENABLE_TLS, PhonexSettings.useTLS());
		prefs.setBoolean(PhonexConfig.TLS_VERIFY_SERVER, true);
		// TODO: enable TLS_VERIFY_CLIENT after cert chain sending
		//prefs.setBoolean(PhonexConfig.TLS_VERIFY_CLIENT, true);
		prefs.setBoolean(PhonexConfig.ENABLE_DNS_SRV, false);
		//prefs.setBoolean(PhonexConfig.ENABLE_DNS_SRV, true);
		//prefs.setString(PhonexConfig.OVERRIDE_NAMESERVER, "8.8.8.8");
		prefs.setString(PhonexConfig.TLS_TRANSPORT_PORT, "5061");
		prefs.setString(PhonexConfig.USER_AGENT, "PhoenixTel");
		prefs.setString(PhonexConfig.LOG_LEVEL, PhonexSettings.debuggingRelease() ? "5" : "0");
		
		// Also allow TCP switch: if message is too big, it is sent via TCP instead of UDP
		prefs.setBoolean(PhonexConfig.ENABLE_UDP, true);
		prefs.setBoolean(PhonexConfig.ENABLE_TCP, true);
		prefs.setBoolean(PhonexConfig.DISABLE_TCP_SWITCH, false);
		
		// Keep-alive
		prefs.setString(PhonexConfig.TLS_KEEP_ALIVE_INTERVAL_WIFI, "120");
		prefs.setString(PhonexConfig.TLS_KEEP_ALIVE_INTERVAL_MOBILE, "90");
		
		// Connectivity change polling - enable polling for routes, units in minutes
		prefs.setString(PhonexConfig.NETWORK_ROUTES_POLLING, "2");
		
		// Watchdog for registration - do not use, timers are OK, should be enough
		prefs.setString(PhonexConfig.NETWORK_WATCHDOG, "0");
		
		// For Narrowband
		prefs.setCodecPriority("PCMU/8000/1",    PhonexConfig.CODEC_NB, "60");
		prefs.setCodecPriority("PCMA/8000/1",    PhonexConfig.CODEC_NB, "50");
		prefs.setCodecPriority("speex/8000/1",   PhonexConfig.CODEC_NB, "240");
		prefs.setCodecPriority("speex/16000/1",  PhonexConfig.CODEC_NB, "220");
		prefs.setCodecPriority("speex/32000/1",  PhonexConfig.CODEC_NB, "0");
		prefs.setCodecPriority("GSM/8000/1",     PhonexConfig.CODEC_NB, "0");
		prefs.setCodecPriority("G722/16000/1",   PhonexConfig.CODEC_NB, "0");
		prefs.setCodecPriority("G729/8000/1",    PhonexConfig.CODEC_NB, "0");
		//prefs.setCodecPriority("iLBC/8000/1",    PhonexConfig.CODEC_NB, "0");
		prefs.setCodecPriority("SILK/8000/1",    PhonexConfig.CODEC_NB, "0");
		prefs.setCodecPriority("SILK/12000/1",   PhonexConfig.CODEC_NB, "0");
		prefs.setCodecPriority("SILK/16000/1",   PhonexConfig.CODEC_NB, "0");
		prefs.setCodecPriority("SILK/24000/1",   PhonexConfig.CODEC_NB, "0");
		prefs.setCodecPriority("CODEC2/8000/1",  PhonexConfig.CODEC_NB, "0");
		/*prefs.setCodecPriority("G7221/16000/1",  PhonexConfig.CODEC_NB, "0");
		prefs.setCodecPriority("G7221/32000/1",  PhonexConfig.CODEC_NB, "0");
		prefs.setCodecPriority("ISAC/16000/1",   PhonexConfig.CODEC_NB, "0");
		prefs.setCodecPriority("ISAC/32000/1",   PhonexConfig.CODEC_NB, "0");
		prefs.setCodecPriority("AMR/8000/1",     PhonexConfig.CODEC_NB, "0");
		prefs.setCodecPriority("opus/8000/1",    PhonexConfig.CODEC_NB, "210");
		prefs.setCodecPriority("opus/12000/1",   PhonexConfig.CODEC_NB, "210");
		prefs.setCodecPriority("opus/16000/1",   PhonexConfig.CODEC_NB, "210");
		prefs.setCodecPriority("opus/24000/1",   PhonexConfig.CODEC_NB, "210");
		prefs.setCodecPriority("opus/48000/1",   PhonexConfig.CODEC_NB, "210");
		prefs.setCodecPriority("G726-16/8000/1", PhonexConfig.CODEC_NB, "0");
		prefs.setCodecPriority("G726-24/8000/1", PhonexConfig.CODEC_NB, "0");
		prefs.setCodecPriority("G726-32/8000/1", PhonexConfig.CODEC_NB, "0");
		prefs.setCodecPriority("G726-40/8000/1", PhonexConfig.CODEC_NB, "0");*/

		// For Wideband
		prefs.setCodecPriority("PCMU/8000/1",    PhonexConfig.CODEC_WB, "60");
		prefs.setCodecPriority("PCMA/8000/1",    PhonexConfig.CODEC_WB, "50");
		prefs.setCodecPriority("speex/8000/1",   PhonexConfig.CODEC_WB, "240");
		prefs.setCodecPriority("speex/16000/1",  PhonexConfig.CODEC_WB, "250");
		prefs.setCodecPriority("speex/32000/1",  PhonexConfig.CODEC_WB, "0");
		prefs.setCodecPriority("GSM/8000/1",     PhonexConfig.CODEC_WB, "0");
		prefs.setCodecPriority("G722/16000/1",   PhonexConfig.CODEC_WB, "0");
		prefs.setCodecPriority("G729/8000/1",    PhonexConfig.CODEC_WB, "0");
		//prefs.setCodecPriority("iLBC/8000/1",    PhonexConfig.CODEC_WB, "0");
		prefs.setCodecPriority("SILK/8000/1",    PhonexConfig.CODEC_WB, "0");
		prefs.setCodecPriority("SILK/12000/1",   PhonexConfig.CODEC_WB, "0");
		prefs.setCodecPriority("SILK/16000/1",   PhonexConfig.CODEC_WB, "0");
		prefs.setCodecPriority("SILK/24000/1",   PhonexConfig.CODEC_WB, "70");
		prefs.setCodecPriority("CODEC2/8000/1",  PhonexConfig.CODEC_WB, "0");
		/*prefs.setCodecPriority("G7221/16000/1",  PhonexConfig.CODEC_WB, "0");
		prefs.setCodecPriority("G7221/32000/1",  PhonexConfig.CODEC_WB, "0");
		prefs.setCodecPriority("ISAC/16000/1",   PhonexConfig.CODEC_WB, "0");
		prefs.setCodecPriority("ISAC/32000/1",   PhonexConfig.CODEC_WB, "0");
		prefs.setCodecPriority("AMR/8000/1",     PhonexConfig.CODEC_WB, "0");
		prefs.setCodecPriority("opus/8000/1",    PhonexConfig.CODEC_WB, "235");
		prefs.setCodecPriority("opus/12000/1",   PhonexConfig.CODEC_WB, "235");
		prefs.setCodecPriority("opus/16000/1",   PhonexConfig.CODEC_WB, "235");
		prefs.setCodecPriority("opus/24000/1",   PhonexConfig.CODEC_WB, "235");
		prefs.setCodecPriority("opus/48000/1",   PhonexConfig.CODEC_WB, "235");
		prefs.setCodecPriority("G726-16/8000/1", PhonexConfig.CODEC_WB, "0");
		prefs.setCodecPriority("G726-24/8000/1", PhonexConfig.CODEC_WB, "0");
		prefs.setCodecPriority("G726-32/8000/1", PhonexConfig.CODEC_WB, "0");
		prefs.setCodecPriority("G726-40/8000/1", PhonexConfig.CODEC_WB, "0");*/
		Log.d(TAG, "Codec priority set");
	}

    @Override
	public int getBasePreferenceResource() {
		return R.xml.phonex_account;
	}
	
	@Override
	public boolean needRestart() {
		return true;
	}
}
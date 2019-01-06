package net.phonex.login;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.RemoteException;

import net.phonex.PhonexSettings;
import net.phonex.R;
import net.phonex.accounting.AccountingLogFetchCall;
import net.phonex.core.IService;
import net.phonex.core.Intents;
import net.phonex.core.MemoryPrefManager;
import net.phonex.db.entity.SipProfile;
import net.phonex.license.LicenseInformation;
import net.phonex.pref.PreferencesManager;
import net.phonex.pub.parcels.StoredCredentials;
import net.phonex.service.XService;
import net.phonex.service.xmpp.XmppManager;
import net.phonex.soap.AuthCheckTask;
import net.phonex.soap.CertGenParams;
import net.phonex.soap.CertGenTask;
import net.phonex.soap.ClistFetchParams;
import net.phonex.soap.ClistFetchTask;
import net.phonex.soap.PasswordChangeParams;
import net.phonex.soap.ServiceConstants;
import net.phonex.soap.TaskWithCallback;
import net.phonex.soap.UserPrivateCredentials;
import net.phonex.soap.WaitingTask;
import net.phonex.soap.WaitingTaskParams;
import net.phonex.soap.entities.AuthCheckV3Response;
import net.phonex.soap.entities.TrueFalse;
import net.phonex.soap.entities.TrueFalseNA;
import net.phonex.ui.intro.DbCheckKeyTask;
import net.phonex.ui.lock.util.PinHelper;
import net.phonex.util.Log;
import net.phonex.util.LoginUtils;
import net.phonex.util.MiscUtils;
import net.phonex.util.ProfileUtils;
import net.phonex.util.account.AccountManagerDesc;
import net.phonex.util.account.AccountManagerFactory;
import net.phonex.util.account.PhonexAccountManager;
import net.phonex.util.analytics.AnalyticsReporter;
import net.phonex.util.analytics.AppEvents;
import net.phonex.util.crypto.CertificatesAndKeys;
import net.phonex.util.crypto.MessageDigest;

import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Main login object for standard login procedure
 * Created by miroc on 13.3.15.
 */
public class FullLogin implements AuthCheckTask.OnAuthCheckCompleted,
        CertGenTask.OnCertGenCompleted,
        ClistFetchTask.OnClistFetchTaskCompleted,
        DbCheckKeyTask.OnDbKeyCheckCompleted {

    private static final String TAG = "FullLogin";

    private Context context;
    private IService service;
    private UserPrivateCredentials privateCredentials = null;
    private boolean certificateExistsForUser = false;
    private LicenseInformation licenseInformation;
    private SipProfile account;

    private AuxJsonData auxJsonData;
    private boolean lockPinAfterLogin = false;

    private LoginEventsListener eventsListener;
    private CertGenParams params;

    private Executor executor;

    public FullLogin(Context context, IService service, boolean lockPinAfterLogin) {
        this.context = context;
        this.service = service;
        executor = Executors.newSingleThreadExecutor();
        this.lockPinAfterLogin = lockPinAfterLogin;
    }

    public void setLoginEventsListener(LoginEventsListener eventsListener) {
        this.eventsListener = eventsListener;
    }

    private void loginCancelled(String localizedErrorMessage){
        if (eventsListener!=null){
            eventsListener.onLoginCancelled(localizedErrorMessage);
        } else {
            Log.wf(TAG, "loginCancelled; eventsListener is null");
        }
    }

    /**
     * Main method, starts login procedure
     * @param sip
     * @param password
     * @param userDomain
     */
    public void login(String sip, CharSequence password, String userDomain){
        Log.vf(TAG, "login;");
        try {
            params = new CertGenParams();
            params.setUserSIP(sip);
            params.setUserPass((String) password);
            params.setUserDomain(userDomain);

            // Parameters are updated during the task
            AuthCheckTask task = new AuthCheckTask(this, params);
            task.setContext(context);
            executor.execute(task);

        } catch(Exception e){
            Log.w(TAG, "Exception in login procedure initialization", e);
        }
    }

    private void loginCancelledWithInternalError(){
        String error = context.getString(R.string.p_unknown_error);
        loginCancelled(error);
    }

    /**
     * Callback - check if login was correct and do necessary actions afterwards.
     */
    @Override
    public void onAuthCheckCompleted(AuthCheckV3Response response) {
        Log.vf(TAG, "onAuthCheckCompleted");

        // PrivKey data from auth check - identity files were loaded during auth check.
        this.privateCredentials = params.getPrivateCredentials();
        this.certificateExistsForUser = (privateCredentials != null
                && privateCredentials.getCert() != null
                && privateCredentials.getPk() != null);

        // Check time drift - one minute tolerated, otherwise report time drift
        // to the user.
        Calendar cal = response.getServerTime();
        if (cal!=null){
            Date dt   = cal.getTime();
            long drift = System.currentTimeMillis() - dt.getTime();
            long diff = Math.abs(drift);

            if (diff > 5*60*1000){
                String clockDriftError = String.format(
                        context.getString(R.string.p_problem_time_drift),
                        dt.toString());

                String driftString  = context.getString(drift > 0 ? R.string.p_problem_time_drift_forward : R.string.p_problem_time_drift_backward);
                Log.wf(TAG, "drift=%d, diff=%d, driftString=%s", drift, diff, driftString);
                clockDriftError += String.format(driftString, formatMillisDuration(diff, context.getResources()));

                Log.wf(TAG, "Clock drift; msg=%s", clockDriftError);
                loginCancelled(clockDriftError);
                return;
            }
        }

        // Check auth hash response
        if (response.getAuthHashValid() == TrueFalse.FALSE){
            loginCancelled(context.getString(R.string.p_problem_badcredential));
            return;
        }

        // Check if account is disabled on the server
        Calendar expCal = response.getAccountExpires();
        if (response.getAccountDisabled()!=null && response.getAccountDisabled()){
            // One reason for disabled account can be trial version expiration, verify it.
            // If expiration date is in the past, report expired trial problem.
            if (expCal != null){
                Date expDate = expCal.getTime();
                long diff = expDate.getTime() - System.currentTimeMillis();
                if (diff < 0){
                    loginCancelled(context.getString(R.string.p_problem_trial_expired));
                    return;
                }
            }

            // Generic reason for disabled account (not expired trial).
            loginCancelled(context.getString(R.string.p_problem_account_disabled));
            return;
        }

        // Check our certificate validity - the server view.
        final TrueFalseNA certValid = response.getCertValid();
        if(certValid==TrueFalseNA.TRUE){
            Log.d(TAG, "Certificate valid OK.");
        } else {
            if (certValid==TrueFalseNA.NA) {
                Log.wf(TAG, "Certificate valid N/A. Probably certificate was not provided.");
            } else {
                // Certificate is not valid (invalidated, may be revoked), thus ignore it as I would not have it
                Log.wf(TAG, "Certificate not valid. CertificateStatus: %s", response.getCertStatus().toString());
            }

            params.setPrivateCredentials(null);
            params.setServiceURL(ServiceConstants.getServiceURL(params.getUserDomain(), false));
            certificateExistsForUser=false;
            privateCredentials=null;
        }

        // Force password change (usually on first login).
        if(response.getForcePasswordChange() == TrueFalse.TRUE){
            PasswordChangeParams parChange = new PasswordChangeParams();
            parChange.setServiceURL(ServiceConstants.getServiceURL(params.getUserDomain(), false));
            parChange.setUserSIP(params.getUserSIP());
            parChange.setTargetUserSIP(params.getUserSIP());
            parChange.setUserOldPass(params.getUserPass());

            if (eventsListener != null){
                eventsListener.passwordChangeRequired(parChange);
            } else {
                Log.ef(TAG, "onAuthCheckCompleted; eventsListener is null, cannot change password");
            }
            return;
        }

        // Retrieve license information
        licenseInformation = new LicenseInformation(response);
        Log.inf(TAG, "onAuthCheckCompleted; licInf [%s]", licenseInformation);

        // Parse trial event data
        auxJsonData = AuxJsonData.fromAuxJsonString(response.getAuxJSON());

        // Password is now valid, encrypted database can be opened or created.
        // Test database password is OK.
        try {
            DbCheckKeyTask task1 = new DbCheckKeyTask(this, params.getUserSIP(), params.getUserPass());
            task1.setContext(context);
            executor.execute(task1);
        } catch(Exception e){
            Log.wf(TAG, e, "Exception in setting up DB key check task");
        }
    }


    /**
     * Callback called after DB encryption password was checked.
     */
    public void onDbKeyCheckCompleted(int checkResult) {
        Log.vf(TAG, "onDbKeyCheckCompleted");
        // Save account if has not been initialized yet
        saveAccount(params, licenseInformation);
        // Save all auxJson data
        auxJsonData.saveAllData(context);

        if (service==null){
            Log.e(TAG, "XService should not be null here, cannot save storage password", new NullPointerException("XService is null"));
            loginCancelledWithInternalError();
            return;
        }

        // TODO duplicate?
//        try {
//            // Set user credentials to SafeNet
//            StoredCredentials creds = new StoredCredentials(params);
//            creds.setSet(true);
//            MemoryPrefManager.updateCredentials(context, creds);
//
//            service.setStoragePassword(params.getStoragePass());
//        } catch (Exception e1) {
//            Log.ef(TAG, e1, "Cannot save storage password to the XService");
//            loginCancelledWithInternalError();
//            return;
//        }

        // First login, call first login callback, generate and sign the certificate
        if(!certificateExistsForUser){
            LoginUtils.onFirstLogin(context);
            Log.inf(TAG, "Generating certificate for user: %s", params.getUserSIP());

            try {
                params.setRemoveDHKeys(true); // Remove DH keys if any.

                CertGenTask task = new CertGenTask(this, params);
                task.setContext(context);

                executor.execute(task);
            } catch(Exception e){
                Log.w(TAG, "Exception in CertGenTask", e);

                // Trigger error to the server, this is bug
                MiscUtils.reportExceptionToAcra(e);
            }
        } else {
            onCertGenCompleted(true, null, null);
        }
    }

    @Override
    public void onCertGenCompleted(boolean success, Object result, Object error) {
        Log.df(TAG, "onCertGenCompleted; certGenParams [%s]", params);

        if (!success){
            Log.ef(TAG, "Certgen failed with error: %s", error);
            loginCancelledWithInternalError();
            return;
        }

        // Get contact list and certificates
        // If the certificate was just created, refresh privateCredentials from
        // provided parameters.
        if (params.isCertificateJustCreated()){
            this.privateCredentials = params.getPrivateCredentials();
        }

        // Storage password may be re-generated
        if (service == null){
            Log.e(TAG,"XService should not be null here, cannot save storage password" , new NullPointerException("XService is null"));
            loginCancelledWithInternalError();
            return;
        }

        try {
            // Set user credentials to SafeNet
            Log.vf(TAG, "onCertGenCompleted; Set user credentials to SafeNet");
            StoredCredentials creds = new StoredCredentials(params);
            creds.setSet(true);
            MemoryPrefManager.updateCredentials(context, creds);
            service.setStoragePassword(params.getStoragePass());
        } catch (Exception e1) {
            Log.e(TAG, "Cannot save storage password to the XService", e1);
            loginCancelledWithInternalError();
            return;
        }



        // Store certificate information to sip profile.
        updateCertificateInfo(params.getUserSIP());

        try{
            // Fix problem with server time ahead of my time - generated
            // certificate might be not valid yet so we have to wait until
            // it will be valid.
            if (params.isCertificateJustCreated() && this.privateCredentials!=null && privateCredentials.cert!=null){
                Date notBefore = privateCredentials.cert.getNotBefore();
                long diff      = notBefore.getTime() - System.currentTimeMillis();

                // Let say user is patient enough to wait up to 45 seconds for login.
                if (diff > 0 && diff < 1000*45){
                    WaitingTaskParams waitingParams = new WaitingTaskParams();
                    waitingParams.milli = diff;
                    waitingParams.reasonText = context.getString(R.string.p_wait_for_cert);
                    waitingParams.reasonAddRemainingSeconds = true;

                    WaitUserData uData = new WaitUserData();
                    uData.params = params;
                    uData.diff = diff;

                    WaitingTask waitingTask = new WaitingTask(waitingParams);
                    waitingTask.setUserData(uData);
                    waitingTask.setContext(context);
                    waitingTask.setListener((userData, cancelled) -> {
                        WaitUserData uData1 = (WaitUserData) userData;
                        onCertificateGenValid(uData1.params, cancelled ? uData1.diff : 0);
                    });

                    executor.execute(waitingTask);

                    return;
                }
                else if (diff > 0){
                    showCertificateNotValid(diff);

                    // and return
                    return;
                }
            }

            // Everything was OK, certificate is valid right now.
            onCertificateGenValid(params, 0);

        } catch(Exception e){
            Log.e(TAG, "Exception in certGenCompleted", e);
        }
    }

    private void lockPinIfRequired(){
        if (PinHelper.isEnabled(context)){
            // Lock pin if required (in case of autologin), otherwise start app with unlocked pin (user just entered password, no need to require pin)
            Log.inf(TAG, "lockPinIfRequired; lock=%s", lockPinAfterLogin);
            PinHelper.lock(context, lockPinAfterLogin);
        }
    }

    /**
     * On certificate gets valid.
     * Can be immediately (certificate was generated in previous login)
     * or can be after some waiting (due to time sync).
     *
     * @param params
     */
    public void onCertificateGenValid(CertGenParams params, long diff){
        Log.vf(TAG, "onCertificateGenValid");

        loadSpentPermissions();

        // pin locking after startup
        lockPinIfRequired();

        // If waiting was cancelled, we cannot proceed...
        if (diff > 0){
            showCertificateNotValid(diff);
            return;
        }

        try{
            ClistFetchParams cListParams = new ClistFetchParams();
            cListParams.setDbId(1);
            cListParams.setStoragePass(params.getStoragePass());
            cListParams.setSip(params.getUserSIP());
            cListParams.setServiceURL(ServiceConstants.getServiceURL(params.getUserDomain(), true));
            cListParams.setClistTableUpdateStrategy(ClistFetchParams.UpdateStrategy.DROP_AND_UPDATE);

            ClistFetchTask task = new ClistFetchTask(this, cListParams);
            task.setContext(context);

            executor.execute(task);
        } catch(Exception e){
            Log.e(TAG, "Exception in certGenCompleted", e);
        }
    }

    private void loadSpentPermissions(){

        AccountingLogFetchCall call =  new AccountingLogFetchCall(context, XmppManager.getXmppResourceString(context));
        new TaskWithCallback(call, new TaskWithCallback.Callback() {
            @Override
            public void onCompleted() {
                Log.inf(TAG, "loadSpentPermissions success");
            }

            @Override
            public void onFailed(Exception e) {
                Log.ef(TAG, e, "loadSpentPermissions error");
            }
        }).run();
    }

    /**
     * Show certificate is not yet valid warning.
     *
     * @param diff
     */
    private void showCertificateNotValid(long diff){
        Log.vf(TAG, "showCertificateNotValid");
        // not Before is after current time so we have to wait...
        long minutes = diff / (60 * 1000);
        long seconds = diff / 1000 % 60;
        String errorString = String.format(
                context.getString(R.string.p_not_before_certificate),
                String.format("%01d", minutes),
                String.format("%01d", seconds));

        Log.wf(TAG, "Clock drift & invalid certificate; msg=%s", errorString);

        // display warning dialog
        if (eventsListener!=null){
            eventsListener.onLoginCancelled(context.getString(R.string.p_almost_there), errorString);
        }
    }

    /**
     * callback - intent to main PhoneX activity
     */
    @Override
    public void onClistFetchTaskCompleted(boolean success, Object result, Object error) {
        Log.vf(TAG, "onClistFetchTaskCompleted");

        if (!success){
            Log.ef(TAG, "ClistFetchTask failed with error: %s", error);
            loginCancelledWithInternalError();
            return;
        }

        try {
            service.triggerLoginStateSave();
        } catch (RemoteException e) {
            Log.ef(TAG, e, "Failed to triggerLoginStateSave");
        }

        // Finally start the application.
        redirectToApplication();
    }

    /**
     * Redirects to application, close this fragment.
     */
    public void redirectToApplication(){
        Log.vf(TAG, "redirectToApplication");

        // Sync DH keys, generate new one if needed.
        try {
            triggerDHKeys();
        } catch (RemoteException e) {
            Log.e(TAG, "Cannot trigger DH Keys sync.");
        }

        // Broadcast login successful, includes SIP stack restarting.
        Log.v(TAG, "Broadcasting login event");
        MiscUtils.sendBroadcast(context.getApplicationContext(), new Intent(Intents.ACTION_LOGIN_SUCCESSFUL));
        AnalyticsReporter.fromApplicationContext(context.getApplicationContext()).event(AppEvents.LOGIN);

        // Show main contact list, close this one.
        if (eventsListener != null){
            eventsListener.onLoginFinished();
        }
    }

    /**
     * save account to DB
     * @param params
     */
    public void saveAccount(CertGenParams params, LicenseInformation licenseInformation){
        Log.vf(TAG, "saveAccount");
        AccountManagerDesc accountManager = AccountManagerFactory.getDefaultManager();

        if (licenseInformation == null){
            Log.ef(TAG, "saveAccount; LicenseInformation is null, cannot continue");
            return;
        }
        Long accountId = SipProfile.INVALID_ID;

        // Load all profiles stored for distribution accountManager
        SipProfile[] profiles = ProfileUtils.getProfilesByManager(
                accountManager.getId(),
                new String[]{SipProfile.FIELD_ID, SipProfile.FIELD_DISPLAY_NAME},
                context);

        Log.df(TAG, "Number of saved accounts=%s", profiles.length);
        if (profiles.length > 0){
            accountId = profiles[0].getId();
        }

        // Load account from database to private attribute (will be used later).
        account = SipProfile.getProfileFromDbId(context, accountId, SipProfile.FULL_PROJECTION);
        if (account == null){
            Log.inf(TAG, "No profile found in DB, creating a new one.");
            account = new SipProfile();
        } else if (!account.getSip().equals(params.getUserSIP())){
            Log.inf(TAG, "Old profile found in DB, storing a new one; old=%s, new=%s", account.getSip(), params.getUserSIP());
            account = new SipProfile();
        }

        account.setAccountManager(accountManager.getId());
        account.setUsername(params.getUserSIP());
        account.setDisplay_name(account.getUsername().split("@")[0]);
        account.setDatatype(SipProfile.CRED_DATA_PLAIN_PASSWD);
        account.setData(params.getUserPass());
        account.setXmpp_password(params.getXmppPass());
        account.setXmpp_service(params.getUserDomain());
        account.setXmpp_server(params.getUserDomain());
        account.setXmpp_user(params.getUserSIP());
        account.setActive(true);

        Log.inf(TAG, "License information [%s]", licenseInformation);
        account.setLicenseExpiresOn(licenseInformation.getLicenseExpiresOn());
        account.setLicenseIssuedOn(licenseInformation.getLicenseIssuedOn());
        account.setLicenseType(licenseInformation.getLicenseType());
        account.setLicenseExpired(licenseInformation.isLicenseExpired());

        // Message Waiting Indicator events are disabled
        // if SIP presence is disabled (creates SUBSCRIBE/NOTIFY transactions).
        account.setMwi_enabled(PhonexSettings.enabledSipPresence());

        // Enable TURN authentication, profile-specific.
        // TURN uses realm as domain. Patched version of restund
        // checks full user name.
        account.setTurn_cfg_user(account.getUsername());
        account.setTurn_cfg_password(account.getData()); // TODO: remove one day ;-)

        // If turn password is provided in authCheckRequest, use that.
        if (!MiscUtils.isEmpty(auxJsonData.getTurnPassword())){
            Log.vf(TAG, "Using new turn password");
            account.setTurn_cfg_password(auxJsonData.getTurnPassword());
        } else {
            Log.ef(TAG, "Turn password is empty");
        }

        // First time user
        if (accountId == SipProfile.INVALID_ID){
            try {
                // shared prefs
                PreferencesManager prefs = new PreferencesManager(context);

                // init account settings
                PhonexAccountManager phonexAccountManager = new PhonexAccountManager();
                phonexAccountManager.buildAccountClean(account, prefs);
                account.setId(SipProfile.USER_ID);

                // also set default application preferences
                phonexAccountManager.setDefaultParams(prefs);
            } catch(Exception e){
                Log.e(TAG, "Exception during initial account setup", e);
            }

            context.getContentResolver().insert(SipProfile.ACCOUNT_ID_URI_BASE, account.getDbContentValues());
            Log.df(TAG, "Inserted account [%s] id=[%s]", account.getDisplay_name(), accountId);
        } else {

            //update
            if(accountId != 1) {
                Log.w(TAG, "Account id != 1");
                account.setId(1);
            }

            context.getContentResolver().update(ContentUris.withAppendedId(SipProfile.ACCOUNT_ID_URI_BASE, account.getId()), account.getDbContentValues(), null, null);
            Log.df(TAG, "Updated account [%s] id=[%s]", account.getDisplay_name(), accountId);
        }
    }

    /**
     * Stores certificate information (freshness, will be published in presence text).
     * Uses loaded {@link #account} attribute in {@link #(net.phonex.soap.CertGenParams)}.
     *
     * @param userSIP
     */
    public void updateCertificateInfo(String userSIP){
        Log.vf(TAG, "updateCertificateInfo");
        if (privateCredentials==null || privateCredentials.cert==null){
            Log.w(TAG, "Certificate is null, cannot set certHash.");
        }

        try {
            final X509Certificate cert = privateCredentials.cert;
            account.setCert_path(CertificatesAndKeys.derivePkcs12Filename(userSIP));
            account.setCert_not_before(cert.getNotBefore());
            account.setCert_hash(MessageDigest.getCertificateDigest(cert));
            context.getContentResolver().update(ContentUris.withAppendedId(SipProfile.ACCOUNT_ID_URI_BASE, account.getId()), account.getDbContentValues(), null, null);

        } catch (Exception e) {
            Log.e(TAG, "Was not able to set certificate freshness data to profile.", e);
        }
    }

    /**
     * Triggers DH Key sync task.
     * @return
     * @throws android.os.RemoteException
     */
    private boolean triggerDHKeys() throws RemoteException{

        if (service!=null){
            Log.d(TAG, "Going to trigger DH key sync - service call");
            service.triggerDHKeySync(16000);
        } else {
            Log.d(TAG, "Going to trigger DH key sync - sending intent");
            XService.triggerDHKeyUpdate(context);
        }
        return true;
    }

    public static String formatMillisDuration(long millis, Resources resources)
    {
        if(millis < 0){
            throw new IllegalArgumentException("Duration must be greater than zero!");
        }

        long daysMillis = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(daysMillis);
        long hoursMillis = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hoursMillis);
        long minutesMillis = TimeUnit.MILLISECONDS.toMinutes(millis);

        int days = (int) daysMillis;
        int minutes = (int) minutesMillis;
        int hours = (int) hoursMillis;

        StringBuilder sb = new StringBuilder();
        if (days > 0){
            sb.append(days);
            sb.append(" ").append(resources.getQuantityString(R.plurals.day, days)).append(" ");
        }
        if (hours > 0){
            sb.append(hours);
            sb.append(" ").append(resources.getQuantityString(R.plurals.hour, hours)).append(" ");
        }
        if (minutes > 0){
            sb.append(minutes);
            sb.append(" ").append(resources.getQuantityString(R.plurals.minute, minutes)).append(" ");
        }
        return(sb.toString().trim());
    }
}

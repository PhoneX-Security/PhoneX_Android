package net.phonex.login;

import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.support.annotation.NonNull;

import net.phonex.R;
import net.phonex.accounting.AccountingLogFetchCall;
import net.phonex.core.IService;
import net.phonex.core.Intents;
import net.phonex.core.MemoryPrefManager;
import net.phonex.db.entity.SipProfile;
import net.phonex.pub.parcels.StoredCredentials;
import net.phonex.service.XService;
import net.phonex.service.xmpp.XmppManager;
import net.phonex.soap.ClistFetchParams;
import net.phonex.soap.ClistFetchTask;
import net.phonex.soap.ServiceConstants;
import net.phonex.soap.TaskWithCallback;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.crypto.CertificatesAndKeys;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Quick login procedure - no network calls required
 * If user has stored password, we can redirect him to the application directly
 * Created by miroc on 24.2.16.
 */
public class QuickLogin {
    private static final String TAG = "QuickLogin";

    private final Context context;
    private final IService service;
    private final LoginEventsListener eventsListener;
    private final Executor executor;

    private StoredCredentials storedCredentials;

    public static final int CODE_INVALID_IDENTITY = -2;
    public static final int CODE_INVALID_PROFILE = -3;
    public static final int CODE_OK = 0;

    public QuickLogin(@NonNull Context context, @NonNull IService service, @NonNull LoginEventsListener listener) {
        this.context = context;
        this.service = service;
        eventsListener = listener;
        executor = Executors.newSingleThreadExecutor();
    }

    public int login(String sip, String password, String sipDomain){
        Log.vf(TAG, "login initiated");
        try {
            storedCredentials = deriveStoredCredentials(context, sip, password);
        } catch (GeneralSecurityException | IOException e) {
            Log.ef(TAG, e, "Exception in setting up DB key check task");
            return -1;
        }

        MemoryPrefManager.updateCredentials(context, storedCredentials);
        try {
            service.setStoragePassword(storedCredentials.getUsrStoragePass());
        } catch (RemoteException e) {
            Log.ef(TAG, e, "Cannot save storage password to the XService");
            loginCancelledWithInternalError();
        }

        final CertificatesAndKeys.IdentityLoader il = new CertificatesAndKeys.IdentityLoader();
        il.setRand(new SecureRandom());

        final int certCode = il.loadIdentityKeys(context, storedCredentials, false);
        if (certCode != CertificatesAndKeys.IdentityLoader.CODE_EXISTS){
            return CODE_INVALID_IDENTITY;
        }

        // Test profile load - required condition for QuickLogin.
        final SipProfile profile = SipProfile.getCurrentProfile(context);
        if (profile == null || profile.getSip() == null){
            Log.ef(TAG, "QuickLogin seems OK but profile could not be loaded");
            return CODE_INVALID_PROFILE;
        }

        MiscUtils.sendBroadcast(context.getApplicationContext(), new Intent(Intents.ACTION_LOGIN_SUCCESSFUL));
        eventsListener.onLoginFinished();

        dispatchClistFetch();
        dispatchSpentPermissionsLoad();
        dispatchDHKeyUpdate();

        // Disabled - DB unlock is performed in DBProvider#onCreate

        // The only task we need to execute - DB key check
//        DbCheckKeyTask task1 = new DbCheckKeyTask(this, sip, password);
//        task1.setContext(context);
//        executor.execute(task1);
        return CODE_OK;
    }

    public static StoredCredentials deriveStoredCredentials(Context context, String sip, String password) throws IOException, GeneralSecurityException {
        final String storagePass = CertificatesAndKeys.PkcsPasswordSchemeV2.getStoragePass(context, sip, password);
        final String pemPass = CertificatesAndKeys.PemPasswordSchemeV2.getStoragePass(context, sip, storagePass, true);

        StoredCredentials storedCredentials = new StoredCredentials();
        storedCredentials.setUserSip(sip);
        storedCredentials.setUsrPass(password);
        storedCredentials.setUsrPemPass(pemPass);
        storedCredentials.setUsrStoragePass(storagePass);
        return storedCredentials;
    }

    private void dispatchSpentPermissionsLoad(){
        AccountingLogFetchCall fetchCall =  new AccountingLogFetchCall(context, XmppManager.getXmppResourceString(context));
        TaskWithCallback task = new TaskWithCallback(fetchCall, new TaskWithCallback.Callback() {
            @Override
            public void onCompleted() {
                Log.inf(TAG, "loadSpentPermissions success");
            }

            @Override
            public void onFailed(Exception e) {
                Log.ef(TAG, e, "loadSpentPermissions error");
            }
        });
        executor.execute(task);
    }

    private void dispatchClistFetch(){
        ClistFetchParams cListParams = new ClistFetchParams();
        cListParams.setDbId(1);
        cListParams.setStoragePass(storedCredentials.getUsrStoragePass());
        cListParams.setSip(storedCredentials.getUserSip());
        cListParams.setServiceURL(ServiceConstants.getServiceURL(storedCredentials.retrieveDomain(), true));
        cListParams.setClistTableUpdateStrategy(ClistFetchParams.UpdateStrategy.UPDATE);

        ClistFetchTask task = new ClistFetchTask((boolean success, Object result, Object error) ->  {
            Log.inf(TAG, "ClistFetch completed, success: %s", success);
        }, cListParams);
        task.setContext(context);
        executor.execute(task);
    }

    private void dispatchDHKeyUpdate(){
        // Sync DH keys, generate new one if needed.
        Log.d(TAG, "Going to trigger DH key sync - sending intent");
        XService.triggerDHKeyUpdate(context);
    }

    private void loginCancelled(String localizedErrorMessage){
        eventsListener.onLoginCancelled(localizedErrorMessage);
    }

    private void loginCancelledWithInternalError(){
        final String error = context.getString(R.string.p_unknown_error);
        loginCancelled(error);
    }
}

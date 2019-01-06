package net.phonex.inapp;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;

import net.phonex.BuildConfig;
import net.phonex.pref.PreferencesConnector;
import net.phonex.pref.PreferencesManager;
import net.phonex.rest.LicenseServerAuthApi;
import net.phonex.rest.TLSUtils;
import net.phonex.ui.PhonexActivity;
import net.phonex.ui.inapp.PurchaseUtils;
import net.phonex.util.Log;
import net.phonex.util.guava.Lists;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reload and save recurrent subscriptions on license server
 * Created by miroc on 14.1.16.
 */
public class ReloadPurchasesService extends IntentService{
    private static final String TAG = "ReloadSubscriptionsService";

    private IabHelper iabHelper;
    private ExecutorService executor = Executors.newSingleThreadExecutor();;

    /**
     * Init reload by firing Intent (if last check was not sooner than given time limit - 2 hours)
     * @param connector
     * @param context
     */
    public static void initCheck(PreferencesConnector connector, Context context){
        initCheck(connector, context, true);
    }
    public static void initCheck(PreferencesConnector connector, Context context, boolean checkLimit){
        if (checkLimit){
            long lastSubscriptionCheck = connector.getLong(PreferencesManager.LAST_REC_SUBSCRIPTION_CHECK, 0);
            if (lastSubscriptionCheck != 0){
                long subscriptionCheckLimit = System.currentTimeMillis() - (2 * 60 * 60 * 1000);
                if (lastSubscriptionCheck > subscriptionCheckLimit){
                    Log.df(TAG, "Last recurrent was earlier than 2 hours ago, skipping (lastSubscriptionCheck=%d)", lastSubscriptionCheck);
                    return;
                }
            }
        }
        Log.df(TAG, "Starting ReloadPurchasesService");
        connector.setLong(PreferencesManager.LAST_REC_SUBSCRIPTION_CHECK, System.currentTimeMillis());
        context.startService(new Intent(context, ReloadPurchasesService.class));
    }

    public ReloadPurchasesService() {
        super("ReloadSubscriptionsService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.vf(TAG, "onHandleIntent");
        try {
            iabHelper = new IabHelper(this, PurchaseUtils.retrievePublicKey());
        } catch (Base64DecoderException e) {
            Log.ef(TAG, e, "Unable to retrieve public key, not checking subscriptions");
            return;
        }

        iabHelper.enableDebugLogging(BuildConfig.DEBUG);
        iabHelper.startSetup(result -> {
            if (!result.isSuccess()) {
                Log.ef(TAG, "Problem setting up in-app billing: " + result);
                return;
            }
            if (iabHelper == null) return;
            try {
                Inventory inventory = iabHelper.queryInventory(true, null);
                if (inventory == null) {
                    Log.wf(TAG, "null inventory");
                    return;
                }
                // code in this lambda returned from IabHelper is executed on the main thread,
                // savePurchases does networking stuff, therefore pass it to a new thread
                executor.submit(() -> savePurchases(inventory.getAllPurchases()));

            } catch (Exception e){
                Log.ef(TAG, e, "Query inventory async error");
            }
        });
    }

    private void savePurchases(List<Purchase> purchases){
        Log.vf(TAG, "savePurchases %s", purchases);
        LicenseServerAuthApi api;
        try {
            if (purchases == null){
                Log.ef(TAG, "null purchases");
                return;
            }
            if (purchases.size() == 0){
                Log.wf(TAG, "No purchases to process");
                return;
            }

            List<Purchase> subscriptionPurchases = filterSubscriptionPurchases(purchases);
            if (subscriptionPurchases.size() == 0){
                Log.wf(TAG, "No subscription purchases to process");
                return;
            }

            api = TLSUtils.prepareLicServerApi(this);
            SavePurchase.processPurchases(this, api, subscriptionPurchases, new SavePurchase.BatchCallback() {
                @Override
                public void onFatalError(String errorMessage) {
                    Log.ef(TAG, "fatalError; msg=%s", errorMessage);
                }

                @Override
                public void onResults(List<Purchase> purchasesOk, List<Purchase> alreadySavedPurchases, List<Purchase> errorPurchases) {
                    Log.inf(TAG, "purchase saved; okCount=%s, alreadySavedCount=%d, errorCount=%d", purchasesOk.size(), alreadySavedPurchases.size(), errorPurchases.size());
                }
            });

        } catch (Exception e) {
            Log.ef(TAG, e, "Cannot save purchases");
        }
    }

    private List<Purchase> filterSubscriptionPurchases(List<Purchase> purchases){
        List<Purchase> subscriptionPurchases = Lists.newArrayList();
        for(Purchase p : purchases){
            if (!p.isConsumable()){
                subscriptionPurchases.add(p);
            }
        }
        return subscriptionPurchases;
    }
}

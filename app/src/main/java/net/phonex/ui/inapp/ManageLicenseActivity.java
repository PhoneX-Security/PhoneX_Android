package net.phonex.ui.inapp;

import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import net.phonex.BuildConfig;
import net.phonex.R;
import net.phonex.core.Intents;
import net.phonex.db.entity.SipProfile;
import net.phonex.inapp.Base64DecoderException;
import net.phonex.inapp.IabHelper;
import net.phonex.inapp.IabResult;
import net.phonex.inapp.Inventory;
import net.phonex.inapp.Purchase;
import net.phonex.inapp.SavePurchase;
import net.phonex.pub.parcels.GenericError;
import net.phonex.pub.parcels.GenericTaskProgress;
import net.phonex.rest.entities.auth.products.Product;
import net.phonex.ui.lock.activity.LockActionBarActivity;
import net.phonex.util.Log;
import net.phonex.util.guava.Lists;

import org.json.JSONException;

import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;

/**
 * @author miroc
 */
public class ManageLicenseActivity extends LockActionBarActivity {
    private static final String TAG = "ManageLicenseActivity";

    // (arbitrary) request code for the purchase flow
    private static final int RC_REQUEST = 10001;

    @InjectView(R.id.my_toolbar) Toolbar toolbar;
    private IabHelper iabHelper;
    private Inventory inventory;

    public static void redirectFrom(Context context){
        if (context != null){
            Intent intent = new Intent(context, ManageLicenseActivity.class);
            context.startActivity(intent);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_with_fragment_and_toolbar);

        if (savedInstanceState == null) {
            // During initial setup, plug in the details fragment.
            ManageLicenseFragment f = new ManageLicenseFragment();
            f.setArguments(getIntent().getExtras());
            getFragmentManager().beginTransaction().add(
                    R.id.fragment_content, f, FragmentTags.MANAGE_LICENSE).commit();
        }

        ButterKnife.inject(this);

        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        try {
            loadIabHelper();
        } catch (Base64DecoderException e) {
            Log.ef(TAG, e, "error while loading IabHelper");
        }
    }

    // We're being destroyed. It's important to dispose of the helper here!
    @Override
    public void onDestroy() {
        super.onDestroy();

        // very important:
        Log.d(TAG, "Destroying helper.");
        if (iabHelper != null) {
            iabHelper.dispose();
            iabHelper = null;
        }
    }

    /**
     * Load in-app billing helper
     */
    private void loadIabHelper() throws Base64DecoderException {
        // Create the helper, passing it our context and the public key to verify signatures with
        Log.d(TAG, "Creating IAB helper.");
        iabHelper = new IabHelper(this, PurchaseUtils.retrievePublicKey());

        // enable debug logging (for a production application, you should set this to false).
        iabHelper.enableDebugLogging(BuildConfig.DEBUG);

        // Start setup. This is asynchronous and the specified listener
        // will be called once setup completes.
        Log.df(TAG, "Starting setup.");
        iabHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                Log.d(TAG, "Setup finished.");

                if (!result.isSuccess()) {
                    // Oh noes, there was a problem.
                    Log.ef(TAG, "Problem setting up in-app billing: " + result);
                    return;
                }

                // Have we been disposed of in the meantime? If so, quit.
                if (iabHelper == null) return;
                initInventoryLoad();
            }
        });
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case android.R.id.home: {
                FragmentManager fm = getFragmentManager();
                if (fm.getBackStackEntryCount() > 0) {
                    fm.popBackStack();
                } else {
                    finish();
                }
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onTitleChanged(CharSequence title, int color) {
        super.onTitleChanged(title, color);
        if (toolbar != null){
            toolbar.setTitle(title);
        }
    }

    /* Purchase related listeners */

    // Listener that's called when we finish querying the items and subscriptions we own
    IabHelper.QueryInventoryFinishedListener gotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
            Log.df(TAG, "Query inventory finished. inventory=%s", inventory);

            // Have we been disposed of in the meantime? If so, quit.
            if (iabHelper== null) return;

            ManageLicenseActivity.this.inventory = inventory;
            // TODO
//             pass this only to purchase
            ProductDetailFragment productDetailFragment = (ProductDetailFragment) getFragmentManager().findFragmentByTag(FragmentTags.PRODUCT_DETAILS);
            if (productDetailFragment != null && productDetailFragment.isVisible()){
                productDetailFragment.onInventoryLoaded(result, inventory);
            }
        }
    };

    // Callback for when a purchase is finished
    IabHelper.OnIabPurchaseFinishedListener purchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            Log.d(TAG, "Purchase finished: " + result + ", purchase: " + purchase);
            ProductDetailFragment productDetailFragment = (ProductDetailFragment) getFragmentManager().findFragmentByTag(FragmentTags.PRODUCT_DETAILS);

            if (result.isFailure()) {
                Log.ef(TAG, "Error purchasing: " + result);
                if (productDetailFragment != null && productDetailFragment.isVisible()){
                    productDetailFragment.onPurchaseError(result);
                }
                return;
            }
            if (!PurchaseUtils.verifyDeveloperPayload(ManageLicenseActivity.this, purchase)) {
                if (productDetailFragment != null && productDetailFragment.isVisible()){
                    productDetailFragment.onPurchaseError(result);
                }
            }

            // TODO do not quit, rather store unprocessed payments and replay them
            // Have we been disposed of in the meantime? If so, quit.
            if (iabHelper== null) return;

            // After purchase, reload inventory
            initInventoryLoad();

            // Show dialog and listen for broadcast results
            if (productDetailFragment != null && productDetailFragment.isVisible()){
                productDetailFragment.onSavePurchaseStarted();
            }

            // Save purchase on our server, has to be executed asynchronously (networking, etc)
            new Thread(() -> {
                savePurchase(purchase);
            }).start();

//            Intent serviceIntent = new Intent(ManageLicenseActivity.this, SavePurchaseService.class);
//            Bundle extras = new Bundle();
//            extras.putParcelable(SavePurchaseService.EXTRA_PURCHASE, purchase);
//            serviceIntent.putExtras(extras);
//            startService(serviceIntent);

            Log.d(TAG, "Purchase successful.");
        }
    };
    public void savePurchase(Purchase purchase){
        SavePurchase.init(ManageLicenseActivity.this).save(purchase, new SavePurchase.Callback() {
            @Override
            public void onSuccess() {
                Log.vf(TAG, "save purchase success");
                // has to be called from main ui thread

                if (purchase.isConsumable()){
                    // subscriptions are not consumable, only report the success
                    runOnUiThread(() -> consumePurchase(purchase));
                } else {
                    broadcastPurchaseSaveResult(GenericTaskProgress.doneInstance());
                }
            }

            @Override
            public void onError(String errorMessage) {
                Log.vf(TAG, "save purchase error");
                broadcastPurchaseSaveResult(GenericTaskProgress.errorInstance(GenericError.GENERIC_ERROR, errorMessage));
            }
        });
    }

    /**
     * Purchases should not be empty or null
     *
     * @param purchases
     */
    public void restorePurchases(List<Purchase> purchases){
        SavePurchase.init(ManageLicenseActivity.this).save(purchases, new SavePurchase.BatchCallback() {
            @Override
            public void onFatalError(String errorMessage) {
                Log.vf(TAG, "restorePurchases error");
                broadcastPurchaseRestoreResult(GenericTaskProgress.errorInstance(GenericError.GENERIC_ERROR, errorMessage));
            }

            @Override
            public void onResults(List<Purchase> purchasesOk, List<Purchase> alreadySavedPurchases, List<Purchase> errorPurchases) {

                if (purchasesOk.size() > 0 || alreadySavedPurchases.size() > 0){
                    List<Purchase> purchases1 = filterConsumablePurchases(purchasesOk);
                    List<Purchase> purchases2 = filterConsumablePurchases(alreadySavedPurchases);
                    if (purchases1.size()>0 || purchases2.size() > 0){
                        // merge consumable purchases
                        purchases1.addAll(purchases2);
                        if (iabHelper == null){
                            Log.ef(TAG, "consume purchase iabhelper is null");
                            broadcastPurchaseRestoreResult(GenericTaskProgress.errorInstance(GenericError.GENERIC_ERROR, getString(R.string.restore_error_generic)));
                            return;
                        }
                        // consume them!
                        // consumeAsync should be initiated from ui thread
                        runOnUiThread(() -> {
                            iabHelper.consumeAsync(purchases1, (pp, results) -> {
                                Log.inf(TAG, "purchase consumed successfully, purchases=%s", pp);
                                broadcastPurchaseRestoreResult(GenericTaskProgress.errorInstance(GenericError.GENERIC_ERROR, getString(R.string.restore_success_with_no_change)));
                            });
                        });
                        return;
                    }
                    // if some products were saved but there are no consumables
                    if (purchasesOk.size() == 0){
                        broadcastPurchaseRestoreResult(GenericTaskProgress.genericErrorInstance(getString(R.string.restore_success_with_no_change)));
                    } else {
                        broadcastPurchaseRestoreResult(GenericTaskProgress.genericErrorInstance(getString(R.string.restore_new_licenses_loaded, purchasesOk.size())));
                    }

                } else {
                    // only error purchases
                    broadcastPurchaseRestoreResult(GenericTaskProgress.errorInstance(GenericError.GENERIC_ERROR, getString(R.string.restore_error_generic)));
                }
            }
        });
    }

    private List<Purchase> filterConsumablePurchases(List<Purchase> purchases){
        List<Purchase> consumablePurchases = Lists.newArrayList();
        for(Purchase p : purchases){
            if (p.isConsumable()){
                consumablePurchases.add(p);
            }
        }
        return consumablePurchases;
    }

    /**
     * Save "unsaved" products from inventory
     */
    public void saveInventory() {
        if (inventory == null || inventory.getAllPurchases().size() == 0){
            Log.wf(TAG, "saveInventory; null inventory");
            try {
                // just wait before broadcast
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            broadcastPurchaseRestoreResult(GenericTaskProgress.genericErrorInstance(getString(R.string.restore_error_nothing_to_restore)));
            return;
        }
        restorePurchases(inventory.getAllPurchases());
        // After restore, reload inventory
//        runOnUiThread(() -> initInventoryLoad());
    }

    private void consumePurchase(Purchase purchase){
        if (iabHelper == null){
            Log.ef(TAG, "consume purchase iabhelper is null");
            broadcastPurchaseSaveResult(GenericTaskProgress.doneInstance());
            return;
        }
        iabHelper.consumeAsync(purchase, (purchase1, result) -> {
            Log.inf(TAG, "purchase consumed successfully, purchase1=%s", purchase1);
            broadcastPurchaseSaveResult(GenericTaskProgress.doneInstance());
        });
    }

    private void broadcastPurchaseRestoreResult(GenericTaskProgress progress){
        Log.df(TAG, "broadcastPurchaseRestoreResult = %s", progress);
        Intent intent = new Intent(Intents.ACTION_RESTORE_PURCHASE_PROGRESS);
        intent.putExtra(Intents.EXTRA_GENERIC_PROGRESS, progress);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void broadcastPurchaseSaveResult(GenericTaskProgress progress){
        Log.df(TAG, "broadcastPurchaseSaveResult = %s", progress);
        Intent intent = new Intent(Intents.ACTION_SAVE_PURCHASE_PROGRESS);
        intent.putExtra(Intents.EXTRA_GENERIC_PROGRESS, progress);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.df(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data);
        if (iabHelper == null) return;

        // Pass on the activity result to the helper for handling
        if (!iabHelper.handleActivityResult(requestCode, resultCode, data)) {
            // not handled, so handle it ourselves (here's where you'd
            // perform any handling of activity results not related to in-app
            // billing...
            super.onActivityResult(requestCode, resultCode, data);
        }
        else {
            Log.df(TAG, "onActivityResult handled by IABUtil.");
        }
    }

    @Override
    public void onBackPressed() {
        FragmentManager fm = getFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected String activityAnalyticsName() {
        return this.getClass().getSimpleName();
    }

    public void initInventoryLoad() {
        if (iabHelper == null){
            return;
        }
        iabHelper.queryInventoryAsync(true, null, gotInventoryListener);
    }

    public void initPurchase(Product product) throws PurchaseException, JSONException {
        if (iabHelper == null){
            throw new PurchaseException("iabHelper null, unable to make a purchase");
        }

        SipProfile currentProfile = SipProfile.getCurrentProfile(this);

        String sku = product.getSkuDetails().getSku();
        String type = product.getSkuDetails().getType();

        String developerPayload = new DeveloperPayload(type, sku, currentProfile.getSip(false)).toJson();
//        iabHelper.launchPurchaseFlow(this, TEST_SKU_PURCHASED, RC_REQUEST, purchaseFinishedListener, developerPayload);
        iabHelper.launchPurchaseFlow(this, sku, type, RC_REQUEST, purchaseFinishedListener, developerPayload);
    }

    /**
     * Called from product detail fragment
     */
    public void onProductPurchaseCompleted() {
        // return from product detail fragment and rel
        ManageLicenseFragment manageLicenseFragment = (ManageLicenseFragment) getFragmentManager().findFragmentByTag(FragmentTags.MANAGE_LICENSE);
        if (manageLicenseFragment != null){
            manageLicenseFragment.setReloadAvailableProducts();
        }
        getFragmentManager().popBackStack();

    }

    public IabHelper getIabHelper() {
        return iabHelper;
    }

    private static final String TEST_SKU_PURCHASED = "android.test.purchased";
    private static final String TEST_SKU_CANCELED = "android.test.canceled";
    private static final String TEST_SKU_REFUNDED = "android.test.refunded";
    private static final String TEST_SKU_ITEM_UNAVAILABLE = "android.test.item_unavailable";
}

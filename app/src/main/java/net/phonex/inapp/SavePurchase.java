package net.phonex.inapp;

import android.content.Context;

import net.phonex.R;
import net.phonex.rest.LicenseServerAuthApi;
import net.phonex.rest.TLSUtils;
import net.phonex.rest.entities.auth.purchase.Response;
import net.phonex.util.Log;
import net.phonex.util.guava.Lists;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Saving purchases on license server and processing results
 * Created by miroc on 12.11.15.
 */
public class SavePurchase {
    private static final String TAG = "SavePurchase";

    public static final int RESULT_OK = 0;
    public static final int RESULT_ERR_JSON_PARSING = 1;
    public static final int RESULT_ERR_MISSING_FIELDS = 2;
    public static final int RESULT_ERR_INVALID_USER = 3;
    public static final int RESULT_ERR_INVALID_PRODUCT = 4;
    public static final int RESULT_ERR_TEST = 5;
    public static final int RESULT_ERR_INVALID_SIGNATURE = 6;
    public static final int RESULT_ERR_EXISTING_ORDER_ID = 7;
    public static final int RESULT_MULTIPLE_PURCHASES = 8; // when saving multiple purchases

    public interface Callback {
        void onSuccess();
        void onError(String errorMessage);

    }

    public interface BatchCallback {
        void onFatalError(String errorMessage);
        void onResults(List<Purchase> purchasesOk, List<Purchase> alreadySavedPurchases, List<Purchase> errorPurchases);
    }

    private final Context context;

    public static SavePurchase init(Context context) {
        return new SavePurchase(context);
    }

    private SavePurchase(Context context){
        this.context = context;
    }

    public void save(Purchase purchase, Callback callback){
        LicenseServerAuthApi api;
        try {
            if (purchase == null){
                Log.ef(TAG, "null purchase");
                return;
            }

            api = TLSUtils.prepareLicServerApi(context);
            processPurchase(context, api, purchase, callback);

        } catch (Exception e) {
            Log.ef(TAG, e, "Cannot prepare license server api");
            callback.onError(context.getString(R.string.error_purchase_generic));
        }
    }

    public void save(List<Purchase> purchases, BatchCallback callback){
        LicenseServerAuthApi api;
        try {
            if (purchases == null || purchases.size() == 0){
                Log.ef(TAG, "null purchases");
                return;
            }

            api = TLSUtils.prepareLicServerApi(context);
            processPurchases(context, api, purchases, callback);

        } catch (Exception e) {
            Log.ef(TAG, e, "Cannot prepare license server api");
            callback.onFatalError(context.getString(R.string.error_purchase_generic));
        }
    }

    static void processPurchases(Context context, LicenseServerAuthApi api, List<Purchase> purchases, BatchCallback callback) {
        try {
            JSONArray jsonArr = new JSONArray();
            for (Purchase purchase: purchases) {
                JSONObject jsonObject = purchase.toJsonWithAppVersion(context);
                jsonArr.put(jsonObject);
            }

            Response response = api.verifyPayments(jsonArr.toString());
            if (response == null || response.getResponseCode() == null){
                Log.ef(TAG, "processPurchases; null response");
                callback.onFatalError(context.getString(R.string.error_purchase_generic));
                return;
            }
            Log.inf(TAG, "verify payments response received [%s]", response);

            switch (response.getResponseCode()){
                case RESULT_MULTIPLE_PURCHASES:{
                    // Response code is mapped to each purchase passed for processing (in the same order)
                    List<Integer> purchasesResponseCodes = response.getPurchasesResponseCodes();
                    List<Purchase> purchasesOk = Lists.newArrayList();
                    List<Purchase> purchasesAlreadyStored = Lists.newArrayList();
                    List<Purchase> purchasesError = Lists.newArrayList();

                    for (int i=0; i<purchasesResponseCodes.size(); i++){
                        switch (purchasesResponseCodes.get(i)){
                            case RESULT_OK:
                                purchasesOk.add(purchases.get(i));
                                break;
                            case RESULT_ERR_EXISTING_ORDER_ID:
                                purchasesAlreadyStored.add(purchases.get(i));
                                break;
                            default:
                                purchasesError.add(purchases.get(i));
                                break;
                        }
                    }
                    callback.onResults(purchasesOk, purchasesAlreadyStored, purchasesError);
                    break;
                }
                default:
                    // Any other response code results in error message
                    callback.onFatalError(context.getString(R.string.error_purchase_generic));
                    break;
            }
        } catch (JSONException e) {
            Log.ef(TAG, "Unable to serialize object to json");
            callback.onFatalError("TODO: Unable to save purchase");
        }

    }

    static void processPurchase(Context context, LicenseServerAuthApi api, Purchase purchase, Callback callback){
        try {
            Response response = api.verifyPayment(purchase.toJsonWithAppVersion(context).toString());
            if (response == null || response.getResponseCode() == null){
                Log.ef(TAG, "processPurchase; null response");
                callback.onError(context.getString(R.string.error_purchase_generic));
                return;
            }
            Log.inf(TAG, "verify payment response received [%s]", response);

            switch (response.getResponseCode()){
                case RESULT_OK:
                case RESULT_ERR_EXISTING_ORDER_ID: // treat existing order id as successful purchase
                    callback.onSuccess();
                    break;
                case RESULT_ERR_INVALID_PRODUCT:
                case RESULT_ERR_INVALID_SIGNATURE:
                case RESULT_ERR_INVALID_USER:
                case RESULT_ERR_JSON_PARSING:
                case RESULT_ERR_MISSING_FIELDS:
                default:
                    callback.onError(context.getString(R.string.error_purchase_generic));
                    // anything else, display error message
                    break;
            }
        } catch (JSONException e) {
            Log.ef(TAG, "Unable to serialize object to json");
            callback.onError("TODO: Unable to save purchase");
        }
    }
}

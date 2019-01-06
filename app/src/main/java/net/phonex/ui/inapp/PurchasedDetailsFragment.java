package net.phonex.ui.inapp;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.support.v4.util.ArrayMap;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;

import net.phonex.PhonexSettings;
import net.phonex.R;
import net.phonex.core.Intents;
import net.phonex.db.entity.AccountingPermission;
import net.phonex.pub.parcels.GenericTaskProgress;
import net.phonex.rest.LicenseServerAuthApi;
import net.phonex.rest.TLSUtils;
import net.phonex.rest.entities.auth.products.Product;
import net.phonex.ui.dialogs.AlertDialogFragment;
import net.phonex.ui.dialogs.GenericProgressDialogFragment;
import net.phonex.util.DateUtils;
import net.phonex.util.LayoutUtils;
import net.phonex.util.Log;
import net.phonex.util.guava.Lists;
import net.phonex.util.guava.Tuple;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import butterknife.ButterKnife;
import butterknife.InjectView;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

/**
 * @author miroc
 */
public class PurchasedDetailsFragment extends Fragment {
    private static final String TAG = "PurchasedDetailsFragment";
    private static final String EXTRA_LAST_PERMISSIONS = "last_permissions";
    private static final String EXTRA_LIC_IDS = "lic_ids";
    private static final String EXTRA_LIC_PRODUCTS = "lic_products";

    @InjectView(R.id.content) View contentView;
    @InjectView(R.id.loading_view) View loadingView;
    @InjectView(R.id.subscriptions_list) LinearLayout subscriptionList;
    @InjectView(R.id.packages_list) LinearLayout packagesList;

    @InjectView(R.id.packages_desc) TextView packagesDescText;

    @InjectView(R.id.restore_button) Button restoreButton;

    // 4099680000 seconds timestamp = ~year 2100 (specifically Mon, 30 Nov 2099 00:00:00 GMT) is effectively "unlimited date"
    private final Date unlimitedDate = new Date(4099680000l * 1000);


    // VVV Persist these attributes
    private List<AccountingPermission> permissions;
    private AggregatedPermissions aggregatedPermissions;
    private Map<Integer, Product> licenseToProductMap;
    // ^^^ Persist these attributes

    public static PurchasedDetailsFragment newInstance() {
        return new PurchasedDetailsFragment();
    }

    public PurchasedDetailsFragment() {
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (!(activity instanceof ManageLicenseActivity)){
            throw new ClassCastException("Fragment can only be attached to ManageLicenseActivity");
        }
    }

    private void recreateState(Bundle savedInstanceState){
        if(savedInstanceState != null){
            permissions = savedInstanceState.getParcelableArrayList(EXTRA_LAST_PERMISSIONS);
            // recompute aggregated permissions as they were too complex to store
            aggregatedPermissions = aggregate(permissions);
            // recreate map
            ArrayList<Integer> licIds = savedInstanceState.getIntegerArrayList(EXTRA_LIC_IDS);
            ArrayList<Product> products = savedInstanceState.getParcelableArrayList(EXTRA_LIC_PRODUCTS);
            // both lists should have the same size
            if (licIds == null || products == null || licIds.size() != products.size()){
                return;
            }

            Map<Integer, Product> map = new HashMap<>();
            for (int i = 0; i<licIds.size(); i++){
                map.put(licIds.get(i), products.get(i));
            }
            licenseToProductMap = map;
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList(EXTRA_LAST_PERMISSIONS, (ArrayList<AccountingPermission>) permissions);

        // cannot directly store map, convert to lists)
        ArrayList<Integer> licIds = Lists.newArrayList();
        ArrayList<Product> products = Lists.newArrayList();
        for (Map.Entry<Integer, Product> entry : licenseToProductMap.entrySet()){
            licIds.add(entry.getKey());
            products.add(entry.getValue());
        }
        outState.putIntegerArrayList(EXTRA_LIC_IDS, licIds);
        outState.putParcelableArrayList(EXTRA_LIC_PRODUCTS, products);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().setTitle(R.string.details);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_purchased_details, container, false);
        ButterKnife.inject(this, view);

        restoreButton.setOnClickListener(v -> initPurchasesRestoration());
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        recreateState(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (aggregatedPermissions == null || licenseToProductMap == null){
            showLoading();
            new Thread(this::loadData).start();
        } else {
            updateProductsUi();
        }
    }

    private void initPurchasesRestoration(){
        ManageLicenseActivity activity = (ManageLicenseActivity) getActivity();
        if (activity == null) {
            Log.wf(TAG, "null activity");
            return;
        }

        // Show progress and listen for broadcasts
        GenericProgressDialogFragment.newInstance(new GenericProgressDialogFragment.EventListener() {
            @Override
            public void onComplete() {
            } // just close

            @Override
            public void onError(GenericTaskProgress progress) {
                AlertDialogFragment.alert(getActivity(), null, progress.getMessage());
            }
        }, Intents.ACTION_RESTORE_PURCHASE_PROGRESS, true).show(getFragmentManager(), "tag");

        // Run action (which emits broadcasts)
        // Has to be executed asynchronously (networking, etc)
        new Thread(activity::saveInventory).start();
    }

    private void loadData(){
        permissions = AccountingPermission.getLocalPermissions(getActivity().getContentResolver(), null, new Date());
        aggregatedPermissions = aggregate(permissions);

        try {
            String localeText = "en";
            Locale locale = PhonexSettings.loadDefaultLanguage(getActivity());
            if (locale != null){
                localeText = locale.getLanguage();
            }


            LicenseServerAuthApi api = TLSUtils.prepareLicServerApi(getActivity());
            api.listProductsFromLicenseIds(localeText, concatenateLicenseIds(permissions), new Callback<Map<Integer, Product>>() {

                @Override
                public void success(Map<Integer, Product> licenseToProduct, Response response) {
                    Log.df(TAG, "success");
                    licenseToProductMap = licenseToProduct;
                    updateProductsUi();
                }

                @Override
                public void failure(RetrofitError error) {
                    Log.ef(TAG, error, "error");
                    showLoadingError();
                }
            });
        } catch (Exception e) {
            Log.ef(TAG, "Unable to load license server api");
            showLoadingError();
        }
    }

    private void showLoading() {
        Log.vf(TAG, "showLoading");

        loadingView.setVisibility(View.VISIBLE);
        contentView.setVisibility(View.GONE);

    }

    private void showLoadingError() {
        hideLoading();
        new MaterialDialog.Builder(getActivity())
                .content(R.string.error_loading_purchased_products)
                .positiveText(R.string.ok)
                .show();
    }

    private void hideLoading() {
        Log.vf(TAG, "hideLoading");
        loadingView.setVisibility(View.GONE);
        contentView.setVisibility(View.VISIBLE);
    }

    private void updateProductsUi() {
        Log.vf(TAG, "updateProductsUi");

        if (aggregatedPermissions.getConsumables().isEmpty()){
            packagesDescText.setText(R.string.no_purchased_packages);
        }

        fillPermissionList(aggregatedPermissions.getSubscriptions(), true);
        fillPermissionList(aggregatedPermissions.getConsumables(), false);


        hideLoading();
    }

    private void fillPermissionList(Map<Long, List<AccountingPermission>> aggregatedPermissions, boolean isSubscriptions) {
        if (getActivity() == null){
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(getActivity());

        for (List<AccountingPermission> permissions : aggregatedPermissions.values()){
            if (permissions.size() == 0){
                continue;
            }

            ViewGroup subscriptionRow = (ViewGroup) inflater.inflate(R.layout.fragment_purchased_package_row, null);
            TextView validUntilText = (TextView) subscriptionRow.findViewById(R.id.valid_until);
            TextView productNameText = (TextView) subscriptionRow.findViewById(R.id.product_name);
            ViewGroup permissionList = (ViewGroup) subscriptionRow.findViewById(R.id.permission_list);

            String productName = getProductName(permissions.get(0));
            productNameText.setText(productName);

            if (!isSubscriptions){
                // Consumables do not have expiration date
                validUntilText.setVisibility(View.GONE);
            } else {
                // Aggregated permissions by license id should all have the same expiration date
                String vtt = getValidToText(permissions.get(0));
                validUntilText.setText(vtt);
            }

            for (AccountingPermission permission : permissions){
                Log.df(TAG, "[%d, %s]", permission.getLicId(), permission);

                if (permission.getValue() == 0){
                    // No need to display 0 permission value
                    continue;
                }

                String desc = getPermissionDescription(permission);
                if (desc == null){
                    // unknown permission, cannot provide description, skip
                    continue;
                }

                TextView tv = new TextView(getActivity());
                tv.setTextColor(LayoutUtils.getSecondaryTextColor(getActivity()));
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                tv.setText(desc);
                permissionList.addView(tv);
            }

            if (isSubscriptions){
                subscriptionList.addView(subscriptionRow);
            } else {
                packagesList.addView(subscriptionRow);
            }
        }
    }

    private String getProductName(AccountingPermission permission){
        if (permission.getLicId() == AccountingPermission.DEFAULT_LIC_ID){
            return getActivity().getString(R.string.default_permission);
        }
        Product product = licenseToProductMap.get((int) permission.getLicId());
        // if cannot retrieve product or its name, display full license (legacy naming)
        if (product == null || TextUtils.isEmpty(product.getDisplayName()))  return getActivity().getString(R.string.product_full_license_name);
        return product.getDisplayName();
    }

    private String getValidToText(AccountingPermission permission){
        Date validTo = permission.validTo();
        if (validTo == null || validTo.after(unlimitedDate)){
            return String.format(getString(R.string.valid_until), getString(R.string.unlimited));
        } else {
            Locale locale = PhonexSettings.loadDefaultLanguage(getActivity());
            String stringDate = DateUtils.formatToDate(validTo, locale);
            return String.format(getString(R.string.valid_until), stringDate);
        }
    }

    private String concatenateLicenseIds(List<AccountingPermission> permissions){
        StringBuilder ids = new StringBuilder();
        for (int i=0; i < permissions.size(); i++){
            ids.append(String.valueOf(permissions.get(i).getLicId()));
            if (i != permissions.size() - 1){
                ids.append(",");
            }
        }
        return ids.toString();
    }

    /**
     * @param permission
     * @return Permission text description
     */
    private String getPermissionDescription(AccountingPermission permission){
        switch (permission.getPermissionType()){
            case CALLS_OUTGOING_SECONDS:{
                if (permission.getValue() < 0){
                    return getString(R.string.calls_limit) + " " + getString(R.string.unlimited);
                } else {
                    String value = DateUtils.formatTime(permission.getValue() * 1000, true);
                    String spent = DateUtils.formatTime(permission.getSpent() * 1000, true);
                    return getString(R.string.calls_limit) + " " + spent + "/" + value;
                }
            }
            case FILES_OUTGOING_FILES: {
                if (permission.getValue() < 0) {
                    return getString(R.string.files_limit) + " " + getString(R.string.unlimited);
                } else {
                    String value = String.valueOf(permission.getValue());
                    String spent = String.valueOf(permission.getSpent());
                    return getString(R.string.files_limit) + " " + spent + "/" + value;
                }
            }
            case MESSAGES_OUTGOING_DAY_LIMIT: {
                if (permission.getValue() < 0) {
                    return getString(R.string.messages_daily_limit) + " " + getString(R.string.unlimited);
                } else {
                    String value = String.valueOf(permission.getValue());
                    return getString(R.string.messages_daily_limit) + " " + value;
                }
            }
            case MESSAGES_OUTGOING_LIMIT:{
                if (permission.getValue() < 0) {
                    return getString(R.string.messages_limit) + " " + getString(R.string.unlimited);
                } else {
                    String value = String.valueOf(permission.getValue());
                    String spent = String.valueOf(permission.getSpent());
                    return getString(R.string.messages_limit) + " " + spent + "/" + value;
                }
            }
            default:
                return null;
        }
    }


    /**
     * Load permission from DB and aggregate them by license id into two sets (subscriptions, consumables)
     * @return tuple(subscriptions, consumables)
     */
    private AggregatedPermissions aggregate(List<AccountingPermission> permissions){
        // Number of purchased licenses is generally low, therefore ArrayMap
        Map<Long, List<AccountingPermission>> aggregatedSubscriptions = new ArrayMap<>();
        Map<Long, List<AccountingPermission>> aggregatedConsumables = new ArrayMap<>();

        for (AccountingPermission perm : permissions){

            long licId = perm.getLicId();
            List<AccountingPermission> list;
            if (perm.isSubscription()){
                if ((list = aggregatedSubscriptions.get(licId))  == null){
                    list = new ArrayList<>();
                    aggregatedSubscriptions.put(licId, list);
                }
            } else {
                if ((list = aggregatedConsumables.get(licId))  == null){
                    list = new ArrayList<>();
                    aggregatedConsumables.put(licId, list);
                }
            }

            list.add(perm);
        }

        // TODO not working ATM
        // Avoid displaying default permissions if others are purchased
        // Remove license id 0 from the list if this is the only permission
//        if(aggregatedSubscriptions.size() == 1 && aggregatedConsumables.isEmpty() && aggregatedSubscriptions.get(DEFAULT_LIC_ID) != null){
//            aggregatedSubscriptions.remove(DEFAULT_LIC_ID);
//        }

        return AggregatedPermissions.from(Tuple.of(aggregatedSubscriptions, aggregatedConsumables));
    }

    private static class AggregatedPermissions {
        public static AggregatedPermissions from(Tuple<Map<Long, List<AccountingPermission>>,
                Map<Long, List<AccountingPermission>>> data){
            AggregatedPermissions aggregatedPermissions = new AggregatedPermissions();
            aggregatedPermissions.data = data;
            return aggregatedPermissions;
        }

        private Tuple<Map<Long, List<AccountingPermission>>,
                Map<Long, List<AccountingPermission>>> data;

        public Map<Long, List<AccountingPermission>> getSubscriptions(){
            return data.getFirst();
        }
        public Map<Long, List<AccountingPermission>> getConsumables(){
            return data.getSecond();
        }
    }
}

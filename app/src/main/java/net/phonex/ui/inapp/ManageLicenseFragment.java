package net.phonex.ui.inapp;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import net.phonex.PhonexSettings;
import net.phonex.R;
import net.phonex.accounting.PermissionLimits;
import net.phonex.db.entity.AccountingPermission;
import net.phonex.inapp.IabException;
import net.phonex.inapp.IabHelper;
import net.phonex.inapp.IabResult;
import net.phonex.inapp.Inventory;
import net.phonex.inapp.Purchase;
import net.phonex.inapp.SkuDetails;
import net.phonex.rest.LicenseServerAuthApi;
import net.phonex.rest.TLSUtils;
import net.phonex.rest.entities.auth.products.Products;
import net.phonex.rest.entities.auth.products.Product;
import net.phonex.ui.customViews.EmptyRecyclerView;
import net.phonex.util.DateUtils;
import net.phonex.util.LayoutUtils;
import net.phonex.util.Log;
import net.phonex.util.SimpleContentObserver;
import net.phonex.util.guava.Lists;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import butterknife.ButterKnife;
import butterknife.InjectView;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

/**
 * @author miroc
 */
public class ManageLicenseFragment extends Fragment{
    private static final String TAG = "ManageLicenseFragment";

    private static final String EXTRA_LAST_PRODUCTS = "last_products";
    private static final String PLATFORM = "google";

    @InjectView(R.id.remaining_calls) TextView remainingCalls;
    @InjectView(R.id.remaining_messages) TextView remainingMessages;
    @InjectView(R.id.remaining_files) TextView remainingFiles;
    @InjectView(R.id.details_button) Button detailsButton;

    @InjectView(R.id.recycler_view) EmptyRecyclerView recyclerView;
    @InjectView(R.id.empty_view) TextView emptyView;
    @InjectView(R.id.loading_view) View loadingView;

    private final Handler handler = new Handler();
    private SimpleContentObserver permissionObserver = new SimpleContentObserver(handler, this::loadLimits);

    private LicenseServerAuthApi licenseServerApi;
    private ProductsAdapter productsAdapter;
    private List<Product> lastProducts;
    private boolean reloadAvailableProducts = false;

    public static ManageLicenseFragment newInstance(){
        return new ManageLicenseFragment();
    }

    public ManageLicenseFragment() {
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (!(activity instanceof ManageLicenseActivity)){
            throw new ClassCastException("Fragment can only be attached to ManageLicenseActivity");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstance) {
        View view = inflater.inflate(R.layout.fragment_manage_license, container, false);
        ButterKnife.inject(this, view);

        getActivity().setTitle(R.string.manage_license);

        LinearLayoutManager recyclerLayoutManager = new LinearLayoutManager(getActivity());
        recyclerLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(recyclerLayoutManager);

        loadLimits();

        detailsButton.setOnClickListener(v -> {
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            Fragment f = PurchasedDetailsFragment.newInstance();
            ft.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
            ft.replace(R.id.fragment_content, f, FragmentTags.PURCHASED_DETAILS);
            ft.addToBackStack(FragmentTags.PURCHASED_DETAILS);
            ft.commit();
        });

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null){
            this.lastProducts = savedInstanceState.getParcelableArrayList(EXTRA_LAST_PRODUCTS);
        }

        if (reloadAvailableProducts){
            this.lastProducts = null;
            reloadAvailableProducts = false;
        }

        loadProducts();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // products are store in ArrayList, just cast
        ArrayList<Product> lastProducts = (ArrayList<Product>) this.lastProducts;
        outState.putParcelableArrayList(EXTRA_LAST_PRODUCTS, lastProducts);
    }

    private void loadProducts(){
        if (lastProducts == null){
            new Thread(() -> {
                try {

                    String localeText = "en";
                    Locale locale = PhonexSettings.loadDefaultLanguage(getActivity());
                    if (locale != null){
                        localeText = locale.getLanguage();
                    }

                    licenseServerApi = TLSUtils.prepareLicServerApi(getActivity());
                    licenseServerApi.availableProducts(localeText, PLATFORM, new Callback<Products>() {
                        @Override
                        public void success(Products products, Response response) {
                            onAvailableProductsLoaded(products);
                        }

                        @Override
                        public void failure(RetrofitError error) {
                            Log.ef(TAG, error, "fail during loading of available products");
                            showLoadingError();
                        }
                    });

                } catch (Exception e) {
                    Log.ef(TAG, e, "Error in preparing license server api");
                }
            }).start();
        } else {
            // otherwise load last products
            productsAdapter.replace(lastProducts);
        }
    }

    private void showLoadingError(){
        loadingView.setVisibility(View.GONE);
        emptyView.setText(R.string.error_loading_available_products);
        emptyView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        productsAdapter = new ProductsAdapter(Lists.newArrayList(), getActivity());
        productsAdapter.setActionListener(new ProductsAdapter.ActionListener() {
            @Override
            public void onProductSelected(Product product) {
                FragmentTransaction ft = getFragmentManager().beginTransaction();
                ProductDetailFragment fragment = ProductDetailFragment.newInstance(product);
                ft.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
                ft.replace(R.id.fragment_content, fragment, FragmentTags.PRODUCT_DETAILS);
                ft.addToBackStack(FragmentTags.PRODUCT_DETAILS);
                ft.commit();
            }

            @Override
            public void onProductsSet(int size) {
                // when some products are set, stop loading animation
                loadingView.setVisibility(View.GONE);
                if (size == 0) {
                    emptyView.setVisibility(View.VISIBLE);
                }
            }
        });
        recyclerView.setAdapter(productsAdapter);
    }

    private void onAvailableProductsLoaded(Products products) {
        Log.inf(TAG, "onAvailableProductsLoaded, size=%d", products.getProducts().size());

        // Load prices from google play
        ManageLicenseActivity activity = (ManageLicenseActivity) getActivity();
        if (activity == null){
            return;
        }
        IabHelper iabHelper = activity.getIabHelper();
        if (iabHelper == null){
            Log.ef(TAG, "onAvailableProductsLoaded; iabHelper is null");
            showLoadingError();
            return;
        }


        //TODO load inventory in activity
        // Name of product represents sku in the store
        List<String> skuList = new ArrayList<>();
        for(Product p : products.getProducts()){
            skuList.add(p.getName());
        }
        Inventory inventory = null;
        try {
            inventory = iabHelper.queryInventory(true, skuList);
        } catch (IabException | IllegalStateException e) {
            Log.ef(TAG, e, "query inventory error");
            showLoadingError();
            return;
        }
        if (inventory == null){
            return;
        }

        // Show only products we can load details for
        List<Product> availableProducts = Lists.newArrayList();
        for (Product p: products.getProducts()){
            SkuDetails skuDetails = inventory.getSkuDetails(p.getName());
            if (skuDetails != null){
                p.setSkuDetails(skuDetails);
                availableProducts.add(p);
            }
        }

        // when products and prices are loaded, display them
        productsAdapter.replace(products.getProducts());
        loadingView.setVisibility(View.GONE);
        lastProducts = availableProducts;
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().getContentResolver().registerContentObserver(AccountingPermission.URI, true, permissionObserver);
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().getContentResolver().unregisterContentObserver(permissionObserver);
    }

    private void loadLimits(){
        int callLimit = PermissionLimits.getCallLimit(getActivity().getContentResolver());
        int filesLimit = PermissionLimits.getFilesLimit(getActivity().getContentResolver());
        int messagesDayLimit = PermissionLimits.getMessagesDayLimit(getActivity().getContentResolver());
        int messagesLimit = PermissionLimits.getMessagesLimit(getActivity().getContentResolver());

        Log.vf(TAG, "loadLimits, callLimit=%d, filesLimit=%d", callLimit, filesLimit);

        int warningColor = getResources().getColor(R.color.material_red_500);
        int normalColor = LayoutUtils.getSecondaryTextColor(getActivity());

        if (callLimit < 0){
            remainingCalls.setText(R.string.unlimited);
        } else {
            remainingCalls.setText(DateUtils.formatTime(callLimit * 1000, true));
            remainingCalls.setTextColor(callLimit == 0 ? warningColor : normalColor);
        }

        // if there is a day limit, ignore total limit
        if (messagesDayLimit != -1){
            String dayLimit = getActivity().getText(R.string.day_limit).toString();
            remainingMessages.setText(String.format(dayLimit, messagesDayLimit));
        } else if (messagesLimit == -1) {
            remainingMessages.setText(R.string.unlimited);
        } else {
            remainingMessages.setText(String.valueOf(messagesDayLimit));
        }

        if (filesLimit < 0){
            remainingFiles.setText(R.string.unlimited);
        } else {
            remainingFiles.setText(String.valueOf(filesLimit));
            remainingFiles.setTextColor(filesLimit == 0 ? warningColor : normalColor);
        }
    }

//    @Override
    public void onInventoryLoaded(IabResult result, Inventory inventory) {
        Log.df(TAG, "onInventoryLoaded");
        // Is it a failure?
        if (result.isFailure()) {
            Log.ef(TAG, "Failed to query inventory: " + result);
            showLoadingError();
            return;
        }

        Log.df(TAG, "Query inventory was successful, [%s]", inventory);
    }

    public void setReloadAvailableProducts() {
        reloadAvailableProducts = true;
    }

//    @Override
//    public void onPurchaseFinished(IabResult result, Purchase purchase) {
//        Log.df(TAG, "onPurchaseFinished");
//    }
}

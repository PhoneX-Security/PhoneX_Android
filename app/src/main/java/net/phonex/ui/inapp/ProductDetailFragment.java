package net.phonex.ui.inapp;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import net.phonex.R;
import net.phonex.core.Intents;
import net.phonex.inapp.IabHelper;
import net.phonex.inapp.IabResult;
import net.phonex.inapp.Inventory;
import net.phonex.inapp.Purchase;
import net.phonex.pub.parcels.GenericTaskProgress;
import net.phonex.rest.entities.auth.products.Product;
import net.phonex.ui.dialogs.AlertDialogFragment;
import net.phonex.ui.dialogs.GenericProgressDialogFragment;
import net.phonex.util.Log;

import org.json.JSONException;

import butterknife.ButterKnife;
import butterknife.InjectView;

/**
 * @author miroc
 */
public class ProductDetailFragment extends Fragment implements PurchaseCallbacks{
    private static final String TAG = "ProductDetailFragment";
    private static final String EXTRA_PRODUCT = "product";
    private Product product;

    @InjectView(R.id.title) TextView title;
    @InjectView(R.id.subtitle) TextView subtitle;
    @InjectView(R.id.text) TextView text;
    @InjectView(R.id.price) TextView price;
    @InjectView(R.id.fab) FloatingActionButton purchaseButton;

    public static ProductDetailFragment newInstance(Product product) {
        ProductDetailFragment f = new ProductDetailFragment();
        Bundle args = new Bundle();
        args.putParcelable(EXTRA_PRODUCT, product);
        f.setArguments(args);
        return f;
    }

    public ProductDetailFragment() {
    }


    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (!(activity instanceof ManageLicenseActivity)){
            throw new ClassCastException("Fragment can only be attached to ManageLicenseActivity");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        product = getArguments().getParcelable(EXTRA_PRODUCT);
        getActivity().setTitle(getString(R.string.product_detail));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_product_detail, container, false);
        ButterKnife.inject(this, view);

        purchaseButton.setOnClickListener(v -> initPurchase());

        if (product != null){
            title.setText(product.getDisplayName());
            text.setText(product.getDescription());

            subtitle.setText(getString(product.getTypeDescriptionResId()));

            if (product.getSkuDetails() != null){
                price.setText(product.getSkuDetails().getPrice());
            }
        }

        return view;
    }

    private void initPurchase(){
        ManageLicenseActivity parentActivity = getParentActivity();
        if (parentActivity != null){
            try {
                parentActivity.initPurchase(product);
            } catch (PurchaseException | JSONException e) {
                Log.ef(TAG, e, "purchase error");
                PurchaseDialogs.genericPurchaseErrorDialog(parentActivity);
            }
        }
    }

    private ManageLicenseActivity getParentActivity() {
        ManageLicenseActivity activity = (ManageLicenseActivity) getActivity();
        if (activity == null || activity.isFinishing()) {
            Log.wf(TAG, "getParentActivity; activity is null or finishing");
            return null;
        }
        return activity;
    }

    @Override
    public void onInventoryLoaded(IabResult result, Inventory inventory) {
        Log.df(TAG, "onInventoryLoaded");
    }

    @Override
    public void onPurchaseFinished(IabResult result, Purchase purchase) {
        Log.df(TAG, "onPurchaseFinished;");
    }

    @Override
    public void onPurchaseError(IabResult result) {
        // TODO process IabResult
        switch (result.getResponse()) {
            case IabHelper.IABHELPER_USER_CANCELLED:
                // normal situation, shown no error
                break;
            case IabHelper.IABHELPER_ERROR_BASE:
            case IabHelper.IABHELPER_REMOTE_EXCEPTION:
            case IabHelper.IABHELPER_BAD_RESPONSE:
            case IabHelper.IABHELPER_VERIFICATION_FAILED:
            case IabHelper.IABHELPER_SEND_INTENT_FAILED:
            case IabHelper.IABHELPER_UNKNOWN_PURCHASE_RESPONSE:
            case IabHelper.IABHELPER_MISSING_TOKEN:
            case IabHelper.IABHELPER_UNKNOWN_ERROR:
            case IabHelper.IABHELPER_SUBSCRIPTIONS_NOT_AVAILABLE:
            case IabHelper.IABHELPER_INVALID_CONSUMPTION:
            default:
                PurchaseDialogs.genericPurchaseErrorDialog(getActivity());
                break;
        }
    }

    @Override
    public void onSavePurchaseStarted() {
        Log.df(TAG, "onSavePurchaseStarted");
        // Show dialog + detect progress
        GenericProgressDialogFragment dialogFragment = GenericProgressDialogFragment.newInstance(new GenericProgressDialogFragment.EventListener() {
            @Override
            public void onComplete() {
                if (getActivity() != null && !getActivity().isFinishing()){
                    Toast.makeText(getActivity(), getString(R.string.product_purchase_success, product.getDisplayName()), Toast.LENGTH_LONG).show();

                    ManageLicenseActivity manageLicenseActivity = (ManageLicenseActivity) getActivity();
                    manageLicenseActivity.onProductPurchaseCompleted();

                    getFragmentManager().popBackStack();
                    // TODO
                }
            }

            @Override
            public void onError(GenericTaskProgress progress) {
                if (getFragmentManager() != null){
                    AlertDialogFragment.newInstance(getString(R.string.p_problem), progress.getMessage()).show(getFragmentManager(), "alert");
                }
            }

        }, Intents.ACTION_SAVE_PURCHASE_PROGRESS, true);
        dialogFragment.show(getFragmentManager(), "tag");
    }
}

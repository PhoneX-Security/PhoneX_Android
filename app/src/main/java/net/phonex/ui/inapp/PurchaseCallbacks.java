package net.phonex.ui.inapp;

import net.phonex.inapp.IabResult;
import net.phonex.inapp.Inventory;
import net.phonex.inapp.Purchase;

/**
 * Created by miroc on 2.11.15.
 */
public interface PurchaseCallbacks {

    void onInventoryLoaded(IabResult result, Inventory inventory);

    void onPurchaseFinished(IabResult result, Purchase purchase);

    void onPurchaseError(IabResult result);

    void onSavePurchaseStarted();
}

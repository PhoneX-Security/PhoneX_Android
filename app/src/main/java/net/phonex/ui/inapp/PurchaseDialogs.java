package net.phonex.ui.inapp;

import android.content.Context;

import com.afollestad.materialdialogs.MaterialDialog;

import net.phonex.R;

/**
 * Created by miroc on 11.11.15.
 */
public class PurchaseDialogs {
    public static void genericPurchaseErrorDialog(Context context){
        if (context == null){
            return;
        }
        MaterialDialog.Builder builder = new MaterialDialog.Builder(context);
        builder
                .positiveText(R.string.ok)
                .content(R.string.error_purchase_generic);
        builder.build().show();
    }
}

package net.phonex.ui.inapp;

import android.content.Context;

import com.afollestad.materialdialogs.MaterialDialog;

import net.phonex.R;

/**
 * Created by miroc on 4.11.15.
 */
public class ExpiredLicenseDialogs {

    public static void showFilesLimitPrompt(Context context){
        showPrompt(context, R.string.limit_reached_outgoing_files);
    }

    public static void showMessageLimitPrompt(Context context){
        showPrompt(context, R.string.limit_reached_outgoing_msgs);
    }

    public static void showCalLimitPrompt(Context context){
        showPrompt(context, R.string.limit_reached_outgoing_calls);
    }

    private static void showPrompt(Context context, int contentResId){
        MaterialDialog.Builder builder = new MaterialDialog.Builder(context);
        builder
                .positiveText(R.string.see_offer)
                .content(contentResId)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        ManageLicenseActivity.redirectFrom(context);

                    }
                });
        builder.build().show();
    }
}

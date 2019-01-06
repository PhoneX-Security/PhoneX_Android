package net.phonex.ui.inapp;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Context;
import android.os.Bundle;

import com.afollestad.materialdialogs.MaterialDialog;

import net.phonex.R;
import net.phonex.pref.PhonexConfig;
import net.phonex.db.entity.SipProfile;
import net.phonex.license.LicenseInformation;

/**
 * Created by miroc on 10.2.15.
 */
public class LicenseExpiredDialogFragment extends DialogFragment{
    private static final String EXTRA_PROFILE = "extra_profile";
    private SipProfile profile;
    private int messageLimit;


    public static LicenseExpiredDialogFragment newInstance(SipProfile profile) {
        LicenseExpiredDialogFragment fr = new LicenseExpiredDialogFragment();
        Bundle args = new Bundle();
        fr.profile = profile;
        args.putParcelable(EXTRA_PROFILE, profile);
        fr.setArguments(args);
        return fr;
    }

    public void showIfExpired(FragmentManager fm, Context context){
        if (profile == null){
            // not showing
            return;
        }
        boolean warningShown = PhonexConfig.getBooleanPref(context, PhonexConfig.TRIAL_EXPIRED_WARNING_SHOWN, false);
        if(!warningShown && profile.getLicenseInformation().isLicenseExpired()){
            PhonexConfig.setBooleanPref(context, PhonexConfig.TRIAL_EXPIRED_WARNING_SHOWN, true);
            show(fm, "");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        profile = getArguments().getParcelable(EXTRA_PROFILE);
        messageLimit = LicenseInformation.getTextLimitPerDay(getActivity());
    }

    public Dialog onCreateDialog(Bundle savedInstanceState) {
        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                .title(R.string.dialog_license_expired_title)
                .content(
                        String.format(getString(R.string.dialog_license_expired_msg), messageLimit)
                )
                .positiveText(R.string.purchase_license)
                .negativeText(R.string.dialog_license_expired_negative);

        builder.callback(new MaterialDialog.ButtonCallback() {
            @Override
            public void onPositive(MaterialDialog dialog) {
                ManageLicenseActivity.redirectFrom(getActivity());
                dialog.dismiss();
            }
        });
        return builder.build();
    }
}

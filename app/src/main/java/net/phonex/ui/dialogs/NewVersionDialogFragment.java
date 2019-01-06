package net.phonex.ui.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;

import com.afollestad.materialdialogs.AlertDialogWrapper;

import net.phonex.R;
import net.phonex.pref.PreferencesConnector;
import net.phonex.ui.versionUpdate.VersionChecker;
import net.phonex.util.Log;

/**
 * Created by miroc on 23.1.15.
 */
public class NewVersionDialogFragment extends DialogFragment {
    private static final String EXTRA_VERSION_CODE="versionCode";
    private static final String EXTRA_VERSION_NAME="versionName";
    private static final String EXTRA_RELEASE_NOTES="releaseNotes";
    private static final String EXTRA_UPDATE_AVAILABLE="updateAvailable";
    private static final String TAG = "NewVersionDialogFragment";


    public static NewVersionDialogFragment updateAvailableInstance(int versionCode, String versionName, String releaseNotes) {
        return newInstance(versionCode, versionName, releaseNotes, true);
    }

    public static NewVersionDialogFragment afterUpdateInstance(int versionCode, String versionName, String releaseNotes) {
        return newInstance(versionCode, versionName, releaseNotes, false);
    }

    private static NewVersionDialogFragment newInstance(int versionCode, String versionName, String releaseNotes, boolean showUpdateAvailable) {
        NewVersionDialogFragment fragment = new NewVersionDialogFragment();

        Bundle args = new Bundle();
        args.putCharSequence(EXTRA_RELEASE_NOTES, releaseNotes);
        args.putCharSequence(EXTRA_VERSION_NAME, versionName);
        args.putInt(EXTRA_VERSION_CODE, versionCode);
        args.putBoolean(EXTRA_UPDATE_AVAILABLE, showUpdateAvailable);
        fragment.setArguments(args);
        fragment.setCancelable(false);
        fragment.setRetainInstance(true);

        return fragment;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Log.inf(TAG, "onCreateDialog [%s]", getArguments());

        final int versionCode = getArguments().getInt(EXTRA_VERSION_CODE);
        final String versionName = getArguments().getString(EXTRA_VERSION_NAME);
        final String releaseNotes = getArguments().getString(EXTRA_RELEASE_NOTES, "");
        final boolean updateAvailable = getArguments().getBoolean(EXTRA_UPDATE_AVAILABLE);


        if (updateAvailable){
            // First version of dialog - update available
            AlertDialogButtonListener listener = new AlertDialogButtonListener();
            String rn;
            if (TextUtils.isEmpty(releaseNotes)){
                // if not release notes available, use generic
                rn = getActivity().getString(R.string.app_update_generic_release_notes, versionName, versionCode);
            } else {
                rn = prepareReleaseNotes(releaseNotes);
            }

            AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(getActivity());
            builder.setIcon(R.drawable.svg_logo_medium)
                    .setTitle(R.string.app_update_available_title)
                    .setMessage(Html.fromHtml(rn))
                    .setPositiveButton(R.string.app_update_get_now, listener)
                    .setNeutralButton(R.string.app_update_remind_me_later, listener)
                    .setNegativeButton(R.string.app_update_ignore_this_version_label, listener);

            builder.setCancelable(false);
            Dialog dialog = builder.create();
            dialog.setCanceledOnTouchOutside(false);
            return dialog;
        } else {
            String title = String.format(getActivity().getString(R.string.app_update_updated_to), versionCode);

            // Second version of dialog - just show news
            AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(getActivity());
            builder.setIcon(R.drawable.svg_logo_medium)
                    .setTitle(title)
                    .setMessage(Html.fromHtml(prepareReleaseNotes(releaseNotes)))
                    .setNegativeButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dismiss();
                        }
                    });

            builder.setCancelable(false);
            Dialog dialog = builder.create();
            dialog.setCanceledOnTouchOutside(false);
            return dialog;

        }
    }

    private String prepareReleaseNotes(String rawNotes){
        StringBuilder sb = new StringBuilder();
        sb.append("<h5>").append(getActivity().getString(R.string.app_update_release_notes)).append("</h5>");

        String lines[] = rawNotes.split("\\r?\\n");
        int i = 0;
        for (String line : lines){
            // add unordered bullet list entity at the beginning
            line = line.replaceFirst("^\\s*[-]*\\s*","");
            sb.append("&#8226; ").append(line);
            if (i < lines.length - 1){
                sb.append("<br/>");
            }
            i++;
        }
        return sb.toString();
    }

    private void updateNow(){
        try{
            String url = getUpdateUrl();
            Uri uri = Uri.parse(url);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);

            // do not show What's new again if update happens within 15 mins
            PreferencesConnector preferencesConnector = new PreferencesConnector(getActivity());
            preferencesConnector.setString(VersionChecker.PREF_UPDATE_NOW_TIMESTAMP, String.valueOf(System.currentTimeMillis()));

            getActivity().startActivity(intent);
        }catch(Exception e){
            Log.ef(TAG, e, "updateNow; cannot update");
        }
        dismiss();
    }

    private void remindMeLater() {
        // Just dismiss, app will try again in VersionChecker.CHECK_THRESHOLD ms
        dismiss();
    }

    private void ignoreThisVersion() {
        try {
            final int versionCode = getArguments().getInt(EXTRA_VERSION_CODE);
            PreferencesConnector preferencesConnector = new PreferencesConnector(getActivity());
            preferencesConnector.setInteger(VersionChecker.PREF_IGNORE_VERSION_CODE, versionCode);

        } catch (Exception e){
            Log.ef(TAG, e, "ignoreThisVersion; cannot ignore");
        }
        dismiss();
    }

    private String getUpdateUrl(){
        String id = getActivity().getApplicationInfo().packageName; // current google play is using package name as id
        return "market://details?id=" + id;
    }
    private class AlertDialogButtonListener implements DialogInterface.OnClickListener{

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case AlertDialog.BUTTON_POSITIVE:
                    updateNow();
                    break;
                case AlertDialog.BUTTON_NEUTRAL:
                    remindMeLater();
                    break;
                case AlertDialog.BUTTON_NEGATIVE:
                    ignoreThisVersion();
                    break;
            }
        }

    }


}

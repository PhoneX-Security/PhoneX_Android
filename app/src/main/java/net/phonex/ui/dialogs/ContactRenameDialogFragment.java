package net.phonex.ui.dialogs;

import android.app.Dialog;
import android.app.FragmentManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.afollestad.materialdialogs.MaterialDialog;

import net.phonex.R;
import net.phonex.db.entity.SipClist;
import net.phonex.soap.ClistRenameTask;
import net.phonex.soap.ClistRenameTaskParams;
import net.phonex.util.analytics.AnalyticsReporter;
import net.phonex.util.analytics.AppEvents;

/**
 * Created by miroc on 10.2.15.
 */
public class ContactRenameDialogFragment extends AlertDialogFragment {
    private static final String EXTRA_SIP_CLIST = "extra_sip_clist";
    private static final String EXTRA_STORAGE_PASS = "extra_storage_pass";
    private SipClist profile;
    private String storagePass;

    public static ContactRenameDialogFragment newInstance(SipClist clist, String storagePass) {
        ContactRenameDialogFragment fr = new ContactRenameDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(EXTRA_SIP_CLIST, clist);
        args.putString(EXTRA_STORAGE_PASS, storagePass);
        fr.setArguments(args);
        return fr;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        profile = getArguments().getParcelable(EXTRA_SIP_CLIST);
        storagePass = getArguments().getString(EXTRA_STORAGE_PASS);
    }

    public Dialog onCreateDialog(Bundle savedInstanceState) {
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View bodyView = inflater.inflate(R.layout.clist_rename, null);

        final EditText newDisplayName = (EditText) bodyView.findViewById(R.id.newDisplayName);
        newDisplayName.setText(profile.getDisplayName());

        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                .title(R.string.rename_account_title)
                .customView(bodyView, false)
                .positiveText(R.string.save)
                .negativeText(R.string.cancel)
                .autoDismiss(false);

        builder.callback(new MaterialDialog.ButtonCallback() {
            @Override
            public void onPositive(MaterialDialog dialog) {
                String newName = newDisplayName.getText().toString();

                if (TextUtils.isEmpty(newName)) {
                    newDisplayName.requestFocus();
                    newDisplayName.setError(getString(R.string.add_contact_empty_alias));
                    return;
                }

                ProgressDialogFragment pfragment = new ProgressDialogFragment();
                pfragment.setMessage("");
                pfragment.setTitle(getString(R.string.p_renaming));
                pfragment.setCheckpointsNumber(ClistRenameTask.CHECKPOINTS_NUMBER);

                ClistRenameTaskParams p = new ClistRenameTaskParams(profile);
                p.setNewDisplayName(newName);
                p.setStoragePass(storagePass);

                ClistRenameTask task = new ClistRenameTask(() -> {
                    AnalyticsReporter.from(ContactRenameDialogFragment.this).event(AppEvents.CONTACT_RENAMED);
                });

                FragmentManager fm = getActivity().getFragmentManager();
                task.setContext(getActivity());
                task.setFragmentManager(fm);
                pfragment.setTask(task);

                // execute async task
                pfragment.show(fm, "");
                task.execute(p);
                dialog.dismiss();
            }

            @Override
            public void onNegative(MaterialDialog dialog) {
                dialog.dismiss();

            }
        });

        return builder.build();
    }
}

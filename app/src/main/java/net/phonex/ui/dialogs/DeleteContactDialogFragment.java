package net.phonex.ui.dialogs;

import android.app.Dialog;
import android.os.Bundle;

import com.afollestad.materialdialogs.MaterialDialog;

import net.phonex.R;
import net.phonex.db.entity.SipClist;
import net.phonex.soap.ClistAddTaskParams;
import net.phonex.soap.ClistRemoveTask;
import net.phonex.soap.ClistRenameTask;
import net.phonex.soap.GenKeyParams;
import net.phonex.util.analytics.AnalyticsReporter;
import net.phonex.util.analytics.AppEvents;

/**
 * Created by miroc on 10.2.15.
 */
public class DeleteContactDialogFragment extends AlertDialogFragment {
    private static final String EXTRA_SIP_CLIST = "extra_sip_clist";
    private static final String EXTRA_STORAGE_PASS = "extra_storage_pass";
    private SipClist clist;
    private String storagePass;

    public static DeleteContactDialogFragment newInstance(SipClist clist, String storagePass) {
        DeleteContactDialogFragment fr = new DeleteContactDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(EXTRA_SIP_CLIST, clist);
        args.putString(EXTRA_STORAGE_PASS, storagePass);
        fr.setArguments(args);
        return fr;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        clist = getArguments().getParcelable(EXTRA_SIP_CLIST);
        storagePass = getArguments().getString(EXTRA_STORAGE_PASS);
    }

    public Dialog onCreateDialog(Bundle savedInstanceState) {

        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
//                .iconRes(android.R.drawable.ic_dialog_alert)
                .title(R.string.delete_account)
                .content(R.string.p_rename_confirm)
                .positiveText(R.string.ok)
                .negativeText(R.string.cancel)
                .autoDismiss(false);
        builder.callback(new MaterialDialog.ButtonCallback() {
            @Override
            public void onNegative(MaterialDialog dialog) {
                dialog.dismiss();
            }

            @Override
            public void onPositive(final MaterialDialog dialog) {
                ProgressDialogFragment pfragment = new ProgressDialogFragment();
                pfragment.setMessage("");

                pfragment.setTitle(getString(R.string.p_deleting));
                pfragment.setCheckpointsNumber(ClistRemoveTask.CHECKPOINTS_NUMBER);

                GenKeyParams gkp = new GenKeyParams();

                ClistAddTaskParams p = new ClistAddTaskParams(clist);
                p.setStoragePass(storagePass);
                p.setLoadIdentity(true); // Load identity for key operation.
                p.setGenKeyParams(gkp);

                ClistRemoveTask task = new ClistRemoveTask(new ClistRenameTask.OnClistAddTaskCompleted() {
                    @Override
                    public void onClistAddTaskCompleted() {
                        if (getActivity()!=null){
                            AnalyticsReporter.event(AnalyticsReporter.getTracker(getActivity()), AppEvents.CONTACT_DELETED);
                        }
                        dialog.dismiss();
                    }
                });

                task.setContext(getActivity());
                task.setFragmentManager(getFragmentManager());
                pfragment.setTask(task);

                // execute async task
                pfragment.show(getFragmentManager(),"");
                task.execute(p);
                dialog.dismiss();
            }
        });

        return builder.build();
    }
}

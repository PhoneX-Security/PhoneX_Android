package net.phonex.ui.dialogs;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;

import net.phonex.R;
import net.phonex.core.Intents;
import net.phonex.ft.storage.FileStorageUri;
import net.phonex.pub.parcels.FileDecryptProgress;
import net.phonex.pub.parcels.FileTransferError;
import net.phonex.ui.fileManager.FileManager;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dialog fragment that show progress for one task and total progress for a queue.
 * <p/>
 * Created by Matus on 7/7/2015.
 */
public class DualProgressDialogFragment extends AlertDialogFragment {
    private static final String TAG = "DualProgressDialogFragment";

    private ProgressReceiver progressReceiver;

    private Map<String, Boolean> progresses;

    private int completedActionCount;

    private FileManager fileManager;

    private boolean openOnComplete;

    private FileStorageUri uriToOpen;

    private List<FileStorageUri> completed;

    private List<FileStorageUri> failed;

    private boolean receivedBroadcast;

    public DualProgressDialogFragment() {
        failed = new ArrayList<>();
        completed = new ArrayList<>();
    }

    private String getProgressText(FileStorageUri uri, FileDecryptProgress progress) {
        String status;
        if (progress.isDone()) {
            if (progress.getError() == FileTransferError.NONE) {
                status = getString(R.string.done);
            } else {
                // TODO
                status = progress.getError().toString();
            }
        } else {
            status = progress.getProgress() + " %";
        }
        return uri.getFilename() + " ... " + status;
    }

    public static DualProgressDialogFragment newInstance(FileManager fileManager,
                                                         List<FileStorageUri> uris,
                                                         boolean openOnComplete) {
        DualProgressDialogFragment fragment = new DualProgressDialogFragment();
        if (uris.size() == 1) {
            fragment.uriToOpen = uris.get(0);
        }
        fragment.progresses = new HashMap<>();
        for (FileStorageUri uri : uris) {
            fragment.progresses.put(uri.toString(), false);
        }
        fragment.fileManager = fileManager;
        fragment.openOnComplete = openOnComplete;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (getDialog() == null) {

            LayoutInflater inflater = getActivity().getLayoutInflater();
            View bodyView = inflater.inflate(R.layout.dialog_progress, null, false);

            MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                    .title(R.string.file_dialog_title)
                    .customView(bodyView, false)
                            //.positiveText(R.string.file_dialog_hide)
                    .negativeText(R.string.cancel)
                    .autoDismiss(false);
            //.cancelable(false);
            builder.callback(new MaterialDialog.ButtonCallback() {
                @Override
                public void onPositive(MaterialDialog dialog) {
                    onComplete();
                }

                @Override
                public void onNegative(MaterialDialog dialog) {
                    if (fileManager != null) {
                        fileManager.cancelDecrypt();
                    } else {
                        Log.df(TAG, "Failed to signal onNegative() to FileManager");
                    }
                    dismiss();
                }
            });
            MaterialDialog dialog = builder.build();

            dialog.setCanceledOnTouchOutside(false);

            return dialog;
        }
        return getDialog();
    }

    @Override
    public void onStart() {
        super.onStart();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intents.ACTION_SECURE_STORAGE_DECRYPT_PROGRESS);
        if (progressReceiver == null) {
            progressReceiver = new ProgressReceiver();
        }
        MiscUtils.registerReceiver(getActivity(), progressReceiver, filter);

        // check in 500 ms, if all tasks are done
        // this is done to overcome the issue when fragment is created after task has finished
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                dismissIfLate();
            }
        }, 500);
    }

    @Override
    public void onStop() {
        if (progressReceiver != null) {
            getActivity().unregisterReceiver(progressReceiver);
            progressReceiver = null;
        }
        super.onStop();
    }

    private void dismissIfLate() {
        if (!receivedBroadcast && fileManager.isTaskDone()) {
            // assume this fragment missed all broadcasts and finish
            Log.wf(TAG, "dismissIfLate() Dismissing because of empty decrypt queue");
            dismiss();
        } else {
            Log.d(TAG, "dismissIfLate() receivedBroadcast == " + receivedBroadcast);
        }
    }

    private void onComplete() {
        if (fileManager != null) {
            if (!failed.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append(getActivity().getString(R.string.file_decrypt_fail));
                for (FileStorageUri uri : failed) {
                    sb.append("\n" + uri.getFilename());
                }
                AlertDialogFragment.alert(getActivity(), getActivity().getString(R.string.file_decrypt_error), sb.toString());

                fileManager.hideDecrypt();
            } else if (openOnComplete && uriToOpen != null) {
                fileManager.openFile(uriToOpen, getActivity());
            } else {
                fileManager.hideDecrypt();
            }
        } else {
            Log.df(TAG, "Failed to signal onPositive() to FileManager");
        }
        dismiss();
    }

    private class ProgressReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (!Intents.ACTION_SECURE_STORAGE_DECRYPT_PROGRESS.equals(intent.getAction())) {
                return;
            }

            receivedBroadcast = true;

            FileDecryptProgress progress = intent.getExtras().getParcelable(Intents.SECURE_STORAGE_DECRYPT_INTENT_PROGRESS);

            FileStorageUri uri = new FileStorageUri(progress.getUri());

            String progressText = getProgressText(uri, progress);

            boolean isRunning = false;

            if (progress.isDone() && progress.getError() == FileTransferError.NONE) {
                completedActionCount++;
                progresses.put(progress.getUri(), true);
            } else {
                isRunning = true;
            }

            MaterialDialog d = (MaterialDialog) getDialog();
            if (d != null) {
                LinearLayout layout = (LinearLayout) d.getCustomView();
                if (layout == null) {
                    return;
                }
                // current progress
                ((TextView) layout.findViewById(R.id.progress_current_file)).setText(progressText);
                ProgressBar current = (ProgressBar) layout.findViewById(R.id.progress_current_bar);
                current.setIndeterminate(false);
                current.setMax(100);
                current.setProgress(progress.getProgress());

                // total progress
                int totalTasks = completedActionCount + progress.getWaitingTasksCount() + (isRunning ? 1 : 0);
                int processedTasks = completedActionCount + (isRunning ? 1 : 0);

                if (progress.getWaitingTasksCount() == 0 && progresses.containsValue(false)) {
                    Log.d(TAG, "Some items were not completed during existence of this dialog");
                    totalTasks = Math.max(completedActionCount + (isRunning ? 1 : 0), progresses.size());
                    processedTasks = totalTasks;
                }

                if (progress.getError() != FileTransferError.NONE && progress.getError() != FileTransferError.CANCELLED) {
                    failed.add(uri);
                    if (progress.getWaitingTasksCount() == 0) {
                        onComplete();
                    }
                } else {
                    completed.add(uri);
                    ((TextView) layout.findViewById(R.id.progress_total_file)).setText(processedTasks + " / " + totalTasks);
                }

                ProgressBar total = (ProgressBar) layout.findViewById(R.id.progress_total_bar);
                total.setIndeterminate(false);
                total.setMax(totalTasks);
                total.setProgress(processedTasks);

                if (progress.getNewPath() != null) {
                    ((TextView) layout.findViewById(R.id.progress_target_file)).setText(progress.getNewPath());
                } else {
                    layout.findViewById(R.id.progress_to).setVisibility(View.GONE);
                }
            }

            if (!isRunning && progress.getWaitingTasksCount() == 0) {
                onComplete();
            }
        }
    }
}

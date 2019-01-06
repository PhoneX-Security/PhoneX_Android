package net.phonex.ui.dialogs;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;

import com.afollestad.materialdialogs.MaterialDialog;

import net.phonex.R;
import net.phonex.core.Intents;
import net.phonex.pub.parcels.GenericTaskProgress;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;

/**
 * Simple class for showing dialog fragment that listens to broadcasts for showing/stopping/updates
 */
public class GenericProgressDialogFragment extends DialogFragment {
    private static final String TAG = "GenericProgressDialogFragment";

    private ProgressReceiver progressReceiver;
    private EventListener listener;
    private String intentAction;
    private boolean useLocalBroadcastReceiver;


    public GenericProgressDialogFragment() {
    }

    public interface EventListener{
        void onComplete();
        void onError(GenericTaskProgress progress);
    }

    public static GenericProgressDialogFragment newInstance(EventListener listener, String progressIntentAction) {
        return newInstance(listener, progressIntentAction, false);
    }

    public static GenericProgressDialogFragment newInstance(EventListener listener, String progressIntentAction, boolean localBroadcastReceiver) {
        GenericProgressDialogFragment df = new GenericProgressDialogFragment();
        df.useLocalBroadcastReceiver = localBroadcastReceiver;
        df.listener = listener;
        df.intentAction = progressIntentAction;
        return df;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (getDialog() == null) {

            MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                    .progress(true, 0)
                    .content(R.string.processing)
                    .autoDismiss(false);

//            builder.callback(new MaterialDialog.ButtonCallback() {
//                @Override
//                public void onPositive(MaterialDialog dialog) {
//                    onComplete();
//                }
//
//                @Override
//                public void onNegative(MaterialDialog dialog) {
//                    if (fileManager != null) {
//                        fileManager.cancelDecrypt();
//                    } else {
//                        Log.df(TAG, "Failed to signal onNegative() to FileManager");
//                    }
//                    dismiss();
//                }
//            });
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
        filter.addAction(intentAction);
        if (progressReceiver == null) {
            progressReceiver = new ProgressReceiver();
        }

        if (useLocalBroadcastReceiver){
            LocalBroadcastManager.getInstance(getActivity()).registerReceiver(progressReceiver, filter);
//            MiscUtils.registerReceiver(getActivity(), progressReceiver, filter);
        } else {
            MiscUtils.registerReceiver(getActivity(), progressReceiver, filter);
        }


//        // check in 500 ms, if all tasks are done
//        // this is done to overcome the issue when fragment is created after task has finished
//        final Handler handler = new Handler();
//        handler.postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                dismissIfLate();
//            }
//        }, 500);
    }

    @Override
    public void onStop() {
        if (progressReceiver != null) {
            if (useLocalBroadcastReceiver){
                LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(progressReceiver);
            } else {
                getActivity().unregisterReceiver(progressReceiver);
            }

            progressReceiver = null;
        }
        super.onStop();
    }

//    private void dismissIfLate() {
////        if (!receivedBroadcast && fileManager.isTaskDone()) {
////            // assume this fragment missed all broadcasts and finish
////            Log.wf(TAG, "dismissIfLate() Dismissing because of empty decrypt queue");
////            dismiss();
////        } else {
////            Log.d(TAG, "dismissIfLate() receivedBroadcast == " + receivedBroadcast);
////        }
//    }
//

    private void onProgress(GenericTaskProgress progress) {

    }
    private class ProgressReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null){
                Log.ef(TAG, "Intent is null");
                return;
            }

            GenericTaskProgress progress = intent.getParcelableExtra(Intents.EXTRA_GENERIC_PROGRESS);
            if (progress == null){
                Log.ef(TAG, "Progress is null");
                return;
            }

            switch (progress.getError()){
                case NONE:
                    if (progress.isDone()){
                        dismiss();
                        if (listener!=null){
                            listener.onComplete();
                        }
                    } else {
                        onProgress(progress);
                    }
                    break;
                default:
                    dismiss();
                    if (listener!=null){
                        listener.onError(progress);
                    }
                    break;
            }
        }
    }
}

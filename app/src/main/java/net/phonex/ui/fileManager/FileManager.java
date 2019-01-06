package net.phonex.ui.fileManager;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;

import net.phonex.R;
import net.phonex.core.IService;
import net.phonex.db.entity.ReceivedFile;
import net.phonex.ft.storage.FileStorageUri;
import net.phonex.ft.storage.DeleteFilesTask;
import net.phonex.ft.storage.FileActionListener;
import net.phonex.ui.chat.MessageFragment;
import net.phonex.ui.dialogs.AlertDialogFragment;
import net.phonex.ui.dialogs.DualProgressDialogFragment;
import net.phonex.ui.gallery.GalleryActivity;
import net.phonex.ui.sendFile.FilePickerFragment;
import net.phonex.ui.sendFile.FileUtils;
import net.phonex.util.FileTransferUtils;
import net.phonex.util.Log;
import net.phonex.util.StorageUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Matus on 7/7/2015.
 */
public class FileManager implements FileActionDialogFragment.FileActionListener, FileActionListener {
    private static final String TAG = "FileManager";

    private IService service;
    private Activity activity;
    private FileActionListener listener;

    public FileManager(IService service, Activity activity, FileActionListener listener) {
        this.service = service;
        this.activity = activity;
        this.listener = listener;
    }

    private void promptOpenEncryptedFile(FileStorageUri uri) {
        ArrayList<FileStorageUri> uris = new ArrayList<>();
        uris.add(uri);
        FileActionDialogFragment fragment = FileActionDialogFragment.newInstance(uris, FilePickerFragment.Action.DECRYPT_AND_OPEN, this, true);
        fragment.show(activity.getFragmentManager(), "");
    }

    public static void openSlideshow(List<FileStorageUri> uris, int startingPosition, Activity activity) {
        Intent it = new Intent(activity, GalleryActivity.class);
        ArrayList<FileStorageUri> images = new ArrayList<>();
        for (FileStorageUri uri : uris) {
            if (FileUtils.isImage(uri.getFilename()) && (new File(uri.getAbsolutePath()).exists())) {
                images.add(uri);
            }
        }
        int realStartingPosition = images.indexOf(uris.get(startingPosition));
        it.putExtra(GalleryActivity.EXTRA_START_POSITION, realStartingPosition);
        it.putExtra(GalleryActivity.EXTRA_URIS, images);
        activity.startActivity(it);
    }

    public void openFile(FileStorageUri info, Activity context) {
        if (context == null || info == null || info.getUri() == null) {
            return;
        }

        if (!StorageUtils.existsUri(info.getUri().toString(), context.getContentResolver())) {
            AlertDialogFragment.alert(context, context.getString(R.string.dwn_p_error),
                    String.format(context.getString(R.string.dwn_file_not_found), info.getFilename()));
            return;
        }

        File file;
        if (info.isSecureStorage()) {
            Log.df(TAG, "Opening secure file %s", info.getAbsolutePath());
            String clonePath = StorageUtils.getClonePathIfExists(info, context.getContentResolver());
            if (clonePath == null) {
                Log.df(TAG, "Prompting the user");
                promptOpenEncryptedFile(info);
                return;
            } else {
                Log.df(TAG, "Opening clone [%s] of file [%s]", clonePath, info.getAbsolutePath());
                file = new File(clonePath);
            }
        } else {
            Log.df(TAG, "Opening normal file %s", info.getAbsolutePath());
            file = new File(info.getAbsolutePath());
        }
        FileTransferUtils.openFile(context, file.getAbsolutePath(), true);
    }

    public static void openDirectory(FileStorageUri info, Context context) {
        FileTransferUtils.openFolder(context, info.getParentPath());
    }

    public void promptDeleteFile(FileStorageUri uri) {
        ArrayList<FileStorageUri> uris = new ArrayList<>();
        uris.add(uri);
        promptDeleteFiles(uris);
    }

    public void promptDecryptFile(FileStorageUri uri, boolean showProgressDialog) {
        ArrayList<FileStorageUri> uris = new ArrayList<>();
        uris.add(uri);
        promptDecryptFiles(uris, showProgressDialog);
    }

    private ArrayList<FileStorageUri> dropDeleted(List<FileStorageUri> infos) {
        ArrayList<FileStorageUri> newInfos = new ArrayList<>();
        for (FileStorageUri uri : infos) {
            if (StorageUtils.existsUri(uri.toString(), activity.getContentResolver())) {
                newInfos.add(uri);
            }
        }
        return newInfos;
    }

    private ArrayList<FileStorageUri> dropInsecure(List<FileStorageUri> infos) {
        ArrayList<FileStorageUri> newInfos = new ArrayList<>();
        for (FileStorageUri uri : infos) {
            if (uri.isSecureStorage()) {
                newInfos.add(uri);
            }
        }
        return newInfos;
    }

    private void alertFilesDeleted(List<FileStorageUri> infos) {
        StringBuilder sb = new StringBuilder();
        sb.append(activity.getString(R.string.upd_failed_some_files_missing_desc));
        for (FileStorageUri uri : infos) {
            sb.append(uri.getFilename() + "\n");
        }
        AlertDialogFragment.alert(activity, activity.getString(R.string.upd_failed_some_files_missing_title), sb.toString());
    }

    public void promptDeleteFiles(List<FileStorageUri> infos) {
        promptDeleteFiles(infos, null);
    }

    public void promptDeleteFiles(List<FileStorageUri> infos, Long msgId) {
        ArrayList<FileStorageUri> existingInfos = dropDeleted(infos);
        if (existingInfos.isEmpty()) {
            alertFilesDeleted(infos);
            return;
        }

        FileActionDialogFragment fragment = FileActionDialogFragment.newInstance(existingInfos, FilePickerFragment.Action.DELETE, this, false, msgId);
        fragment.show(activity.getFragmentManager(), "");
    }

    public void promptDecryptFiles(List<FileStorageUri> infos, boolean showProgressDialog) {
        ArrayList<FileStorageUri> existingInfos = dropDeleted(dropInsecure(infos));
        if (existingInfos.isEmpty()) {
            alertFilesDeleted(infos);
            return;
        }

        FileActionDialogFragment fragment = FileActionDialogFragment.newInstance(existingInfos, FilePickerFragment.Action.DECRYPT, this, showProgressDialog);
        fragment.show(activity.getFragmentManager(), "");
    }

    public void promptDeleteFilesByMessageId(Long messageId) {
        try {
            final List<ReceivedFile> rf = ReceivedFile.getFilesByMsgId(activity.getContentResolver(), messageId);

            promptDeleteFiles(FileStorageUri.fromReceivedFiles(rf), messageId);
        } catch (Exception e) {
            Log.ef(TAG, e, "Exception in removing files, messageId = %s", messageId);
        }
    }

    public void deleteFiles(List<FileStorageUri> uris, Long msgId) {
        DeleteFilesTask t = new DeleteFilesTask(activity, listener, msgId);
        t.execute(uris.toArray(new FileStorageUri[uris.size()]));
    }

    public void deleteFilesByMessageId(Long messageId) {
        final List<ReceivedFile> rf = ReceivedFile.getFilesByMsgId(activity.getContentResolver(), messageId);
        List<FileStorageUri> uris = FileStorageUri.fromReceivedFiles(rf);
        deleteFiles(dropDeleted(uris), messageId);
    }

    public void decryptFiles(List<FileStorageUri> files, boolean showProgressDialog, boolean openOnComplete) {
        Log.df(TAG, "decryptFiles openOnComplete==" + openOnComplete);
        List<String> uriStrings = new ArrayList<>();
        for (FileStorageUri info : files) {
            if (info.isSecureStorage()) {
                uriStrings.add(info.toString());
            } else {
                files.remove(info);
            }
        }
        try {
            service.decryptFiles(uriStrings);
            // NOTE: always show dialog for now, can be confusing if decryption starts from chat
            if (true || showProgressDialog) {
                DualProgressDialogFragment fragment = DualProgressDialogFragment.newInstance(this, files, openOnComplete);
                fragment.show(activity.getFragmentManager(), "DualProgressDialogFragment");
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in decryption", e);
        }
    }

    public boolean cancelDecrypt() {
        try {
            service.cancelDecrypt();
            Log.df(TAG, "Successfully called cancelDecrypt()");
            signalPostAction();
            return true;
        } catch (RemoteException e) {
            Log.df(TAG, "Failed to call cancelDecrypt()", e);
            signalPostAction();
            return false;
        }
    }

    public boolean hideDecrypt() {
        // this is currently only used after completion
        signalPostAction();
        return true;
    }

    public boolean cancelDelete() {
        return false;
    }

    public void deleteClone(FileStorageUri uri) {
        if (!uri.isSecureStorage()) {
            return;
        }

        // TODO clones should be deleted under certain circumstances,
        // but this work has been postponed see PHON-558

        //DeleteFileCloneTask task = new DeleteFileCloneTask(contentResolver, listener);
        //task.execute(uri.toString());
    }

    @Override
    public void onDialogPositiveClick(List<FileStorageUri> files, FilePickerFragment.Action action,
                                      boolean showProgressDialog, Long msgId) {
        if (FilePickerFragment.Action.DELETE == action) {
            deleteFiles(files, msgId);
        } else if (FilePickerFragment.Action.DECRYPT == action) {
            decryptFiles(files, showProgressDialog, false);
        } else if (FilePickerFragment.Action.DECRYPT_AND_OPEN == action) {
            decryptFiles(files, showProgressDialog, true);
        }
    }

    @Override
    public void onDialogNegativeClick(List<FileStorageUri> files, FilePickerFragment.Action action) {
        signalPostAction();
    }

    @Override
    public boolean actionFinished() {
        return signalPostAction();
    }

    private boolean signalPostAction() {
        boolean success = false;
        FilePickerFragment fragment = (FilePickerFragment) activity.getFragmentManager().findFragmentByTag(FilePickerFragment.THIS_FRAGMENT_TAG);
        if (fragment != null) {
            Log.d(TAG, "Found FilePickerFragment");
            success = fragment.actionFinished();
        }
        if (!success) {
            MessageFragment messageFragment = (MessageFragment) activity.getFragmentManager().findFragmentById(R.layout.message_fragment);
            if (messageFragment != null) {
                success = messageFragment.actionFinished();
                Log.d(TAG, "MessageFragment refreshed");
            }
        }
        if (!success) {
            Log.d(TAG, "Dialog result signal not successful");
        }
        return success;
    }

    public boolean isTaskDone() {
        try {
            Log.d(TAG, "isTaskDone()");
            return !service.isTaskRunningOrPending();
        } catch (RemoteException e) {
            Log.e(TAG, "Cannot determine isTaskRunningOrPending()", e);
            return true;
        }
    }
}

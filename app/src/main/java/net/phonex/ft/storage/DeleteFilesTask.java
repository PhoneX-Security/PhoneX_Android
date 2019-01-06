package net.phonex.ft.storage;

import android.app.Activity;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;

import net.phonex.R;
import net.phonex.db.entity.ReceivedFile;
import net.phonex.ui.dialogs.AlertDialogFragment;
import net.phonex.util.Log;
import net.phonex.util.StorageUtils;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Created by Matus on 7/9/2015.
 */
public class DeleteFilesTask extends AsyncTask<FileStorageUri, Integer, Boolean> {
    private static final String TAG = "DeleteFilesTask";

    private Activity activity;
    private FileActionListener listener;
    private Long msgId;
    private ArrayList<FileStorageUri> deletedUris;

    public DeleteFilesTask(Activity activity, FileActionListener listener, Long msgId) {
        this.activity = activity;
        this.listener = listener;
        this.msgId = msgId;
        this.deletedUris = new ArrayList<>();
    }

    @Override
    protected Boolean doInBackground(FileStorageUri... params) {
        if (params==null || params.length == 0){
            Log.wf(TAG, "DeleteFilesTask with no Uris");
            return null;
        }

        Boolean success = true;

        ArrayList<FileStorageUri> failed = new ArrayList<>();

        for (int i = 0; i < params.length; i++) {
            boolean actionSuccess = StorageUtils.deleteFile(activity.getContentResolver(), params[i].getUri(), true);

            if (!actionSuccess) {
                failed.add(params[i]);
            } else {
                deletedUris.add(params[i]);
            }

            Log.df(TAG, "File [%s] %s deleted", params[i], actionSuccess ? "was" : "was not");
            success &= actionSuccess;
            publishProgress((int) (100 * (double) i / params.length));
        }

        if (!failed.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append(activity.getString(R.string.file_delete_error_message));
            for (FileStorageUri uri : failed) {
                sb.append("\n" + uri.getFilename());
            }
            AlertDialogFragment.alert(activity, activity.getString(R.string.file_delete_error), sb.toString());
        }

        if (msgId != null && success) {
            // Delete received files.
            activity.getContentResolver().delete(ReceivedFile.URI, ReceivedFile.FIELD_MSG_ID + "=?", new String[]{msgId.toString()});
        }

        return success;
    }

    @Override
    protected void onPostExecute(Boolean aBoolean) {
        listener.actionFinished();

        ArrayList<String> directories = new ArrayList<>();

        Iterator iterator = deletedUris.iterator();
        while(iterator.hasNext()) {
            FileStorageUri uri = (FileStorageUri) iterator.next();
            if (!uri.isSecureStorage()) {
                if (!directories.contains(uri.getParentPath())) {
                    directories.add(uri.getParentPath());
                }
            }
        }

        if (!directories.isEmpty()) {
            String[] directoriesArray = new String[directories.size()];
            directoriesArray = directories.toArray(directoriesArray);
            MediaScannerConnection.scanFile(activity, directoriesArray, null, new MediaScannerConnection.OnScanCompletedListener() {
                @Override
                public void onScanCompleted(String path, Uri uri) {
                    Log.df(TAG, "File delete - media scan completed [%s]", path);
                }
            });
        }
    }
}

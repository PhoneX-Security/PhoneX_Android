package net.phonex.ft.storage;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.AsyncTask;

import net.phonex.db.entity.FileStorage;
import net.phonex.util.Log;

/**
 * Created by Matus on 7/10/2015.
 */
public class DeleteFileCloneTask extends AsyncTask<String, Void, Void> {
    private static final String TAG = "DeleteFileCloneTask";

    ContentResolver contentResolver;
    FileActionListener listener;

    public DeleteFileCloneTask(ContentResolver contentResolver, FileActionListener listener) {
        this.contentResolver = contentResolver;
        this.listener = listener;
    }

    @Override
    protected Void doInBackground(String... params) {
        if (params.length != 1) {
            throw new IllegalArgumentException("params.length != 1");
        }
        String uri = params[0];
        FileStorage fileStorage = null;
        try {
            fileStorage = FileStorage.getFileStorageByUri(Uri.parse(uri), contentResolver);
            if (fileStorage != null) {
                fileStorage.deleteCleartextClone(contentResolver);
            }
        } catch (FileStorage.FileStorageException e) {
            Log.w(TAG, "Exception at delete clone", e);
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        listener.actionFinished();
    }
}

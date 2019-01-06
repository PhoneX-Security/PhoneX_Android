package net.phonex.camera.util;

import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import net.phonex.R;
import net.phonex.camera.interfaces.PhotoSavedListener;
import net.phonex.db.entity.FileStorage;
import net.phonex.ft.storage.FileStorageUri;
import net.phonex.pref.PreferencesManager;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by Matus on 21-Jul-15.
 */
public class SavingPhotoTask extends AsyncTask<Void, Integer, FileStorageUri> {

    private static final String TAG = "SavingPhotoTask";

    private static final String IMG_PREFIX = "IMG_";
    private static final String IMG_POSTFIX = ".jpg";
    private static final String TIME_FORMAT = "yyyyMMdd_HHmmss";

    private byte[] data;
    private PhotoSavedListener callback;
    private Context context;
    private Context applicationContext; // for showing toast, context may be destroyed

    public SavingPhotoTask(Context context, byte[] data, PhotoSavedListener callback) {
        this.data = data;
        this.callback = callback;
        this.context = context;
        this.applicationContext = context != null ? context.getApplicationContext() : null;
    }

    private String createName() {
        String timeStamp = new SimpleDateFormat(TIME_FORMAT).format(new Date());
        return IMG_PREFIX + timeStamp + IMG_POSTFIX;
    }

    @Override
    protected FileStorageUri doInBackground(Void... params) {
        FileStorage storage;
        try {
            File photos = PreferencesManager.getSecureCameraFolder(context);
            if (photos == null) {
                Log.e(TAG, "Failed to obtain photos path");
                return null;
            }
            storage = FileStorage.newFileStorageResolveNameConflicts(photos.getAbsolutePath(), createName(), context.getContentResolver());
            if (storage == null) {
                Log.e(TAG, "null file storage obtained");
                return null;
            }
        } catch (FileStorage.FileStorageException e) {
            Log.e(TAG, "Failed to save photo", e);
            return null;
        }
        OutputStream os = null;
        try {
            os = storage.getOutputStream(context.getContentResolver(), false);
            long time = System.currentTimeMillis();

            int chunkSize = data.length/100;
            int written = 0;
            int oldProgress = -1;

            while (written < data.length) {
                int toWrite = Math.min(chunkSize, data.length - written);
                os.write(data, written, toWrite);
                written += toWrite;
                int progress = (int) (100 * ((double) written / (double) data.length));
                if (oldProgress < progress) {
                    oldProgress = progress;
                    publishProgress(progress);
                }
            }

            Log.df(TAG, "photo saved in %1d ms", System.currentTimeMillis() - time);
        } catch (FileStorage.FileStorageException | IOException e) {
            Log.e(TAG, "Failed to save photo", e);
            return null;
        } finally {
            MiscUtils.closeSilently(os);
        }
        return new FileStorageUri(storage.getUri());
    }

    @Override
    protected void onPostExecute(FileStorageUri uri) {
        super.onPostExecute(uri);
        if (callback != null) {
            if (uri != null) {
                callback.photoSaved(uri);
            } else {
                callback.photoError();
            }
        } else {
            if (applicationContext != null) {
                Toast.makeText(applicationContext, String.format(applicationContext.getString(R.string.camera_photo_saved), uri.getFilename()), Toast.LENGTH_SHORT).show();
            }
            Log.e(TAG, "Failed to signal task completion to callback, showing at least to user");
        }
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        if (values != null && values.length > 0) {
            if (callback != null) {
                callback.photoProgress(values[0]);
            }
        }
    }
}

package net.phonex.util.glide;

import android.content.Context;
import android.net.Uri;
import android.os.OperationCanceledException;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.data.DataFetcher;

import net.phonex.db.entity.FileStorage;
import net.phonex.ft.storage.FileStorageUri;
import net.phonex.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * Created by Matus on 10.6.2015.
 */
public class FileStorageUriStreamDataFetcher implements DataFetcher<InputStream> {

    private static final String TAG = "FileStorageUriStreamDataFetcher";

    private Uri uri;
    private Context context;
    private boolean cancelled;

    public FileStorageUriStreamDataFetcher(FileStorageUri uri, Context context) {
        this.uri = uri.getUri();
        this.context = context;
    }

    @Override
    public InputStream loadData(Priority priority) throws Exception {
        if (cancelled) {
            throw new Exception("cancelled");
        }
        Log.d(TAG, "Loading by uri " + uri);
        if (FileStorageUri.STORAGE_SCHEME_SECURE.equals(uri.getScheme())) {
            try {
                FileStorage read = FileStorage.getFileStorageByFileSystemName(
                        uri.getQueryParameter(FileStorageUri.STORAGE_FILESYSTEM_NAME),
                        context.getContentResolver());
                if (read == null) {
                    throw new FileNotFoundException();
                }
                Log.d(TAG, "Secure loaded " + read);
                return read.getInputStream();
            } catch (FileStorage.FileStorageException e) {
                Log.e(TAG, "Secure storage exception", e);
                throw new FileNotFoundException();
            }
        } else if (FileStorageUri.STORAGE_SCHEME_NORMAL.equals(uri.getScheme())) {
            Log.d(TAG, "Normal storage " + uri.getPath() + File.separator +
                    uri.getQueryParameter(FileStorageUri.STORAGE_FILESYSTEM_NAME));
            return new BufferedInputStream(new FileInputStream(
                       new File(uri.getPath() + File.separator + uri.getQueryParameter(FileStorageUri.STORAGE_FILESYSTEM_NAME))));
        }
        Log.e(TAG, "Wrong scheme " + uri.getScheme());
        throw new FileNotFoundException();
    }

    @Override
    public void cleanup() {

    }

    @Override
    public String getId() {
        return uri.toString();
    }

    @Override
    public void cancel() {
        cancelled = true;
    }
}

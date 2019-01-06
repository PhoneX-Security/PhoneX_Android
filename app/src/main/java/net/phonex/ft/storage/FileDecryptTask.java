package net.phonex.ft.storage;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;

import net.phonex.db.entity.FileStorage;
import net.phonex.ft.misc.Canceller;
import net.phonex.pub.parcels.FileDecryptProgress;
import net.phonex.pub.parcels.FileTransferError;
import net.phonex.pub.parcels.FileTransferProgressEnum;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * This task must run on single thread executor because of decrypt queue handling.
 *
 * Created by Matus on 25.6.2015.
 */
public class FileDecryptTask implements Runnable {

    public final static String TAG = "DecryptFileTask";

    private Context context;

    /**
     * Global task cancellation.
     */
    private Canceller canceller;

    /**
     * The file from secure storage that is being decrypted.
     */
    private FileStorage currFileStorage;

    private Object currFileLock;

    private FileDecryptManager mgr;

    private boolean isCancelled() {
        return (canceller != null && canceller.isCancelled());
    }

    public void setMgr(FileDecryptManager mgr) {
        this.mgr = mgr;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public void setCanceller(Canceller canceller) {
        this.canceller = canceller;
    }

    @Override
    public void run() {
        if (context == null) {
            publishError(FileTransferError.GENERIC_ERROR);
            return;
        }
        while (!isCancelled()) {
            String uriString = mgr.pollDecryptQueue();
            if (uriString == null) {
                Log.d(TAG, "mgr.pollDecryptQueue() == null");
                break;
            }
            Uri uri = Uri.parse(uriString);
            Log.d(TAG, "Going to decrypt " + uri.toString());
            File outputFile = null;
            try {
                currFileStorage = FileStorage.getFileStorageByUri(uri, context.getContentResolver());
                if (currFileStorage == null) {
                    Log.w(TAG, "currFileStorage == null for uri " + uri);
                    continue;
                }
                String clonePath = currFileStorage.createCleartextClone(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        null,
                        context.getContentResolver());

                InputStream input = currFileStorage.getInputStream();
                OutputStream output = null;
                outputFile = new File(clonePath);
                boolean cancelled = false;
                boolean error = false;
                try {
                    output = new BufferedOutputStream(new FileOutputStream(outputFile));
                    byte[] buf = new byte[8096];
                    int bytesRead;
                    int totalRead = 0;
                    while ((bytesRead = input.read(buf)) > 0) {
                        output.write(buf, 0, bytesRead);
                        if (isCancelled()) {
                            Log.d(TAG, "Decryption cancelled during read " + uri);
                            publishError(FileTransferError.CANCELLED);
                            cancelled = true;
                            return;
                        }
                        totalRead += bytesRead;

                        // Broadcast progress
                        FileDecryptProgress progress = new FileDecryptProgress(
                                currFileStorage.getUri().toString(),
                                FileTransferProgressEnum.DECRYPTING_FILES,
                                (int) (100 * (double) totalRead / currFileStorage.getFileSize()),
                                false,
                                FileTransferError.NONE,
                                mgr.getQueueSize(),
                                clonePath);

                        publishProgress(progress);
                    }

                    // decryption complete, tell MediaScanner to scan this file
                    scanFile(outputFile);

                    FileDecryptProgress progress = new FileDecryptProgress(
                            uri.toString(),
                            FileTransferProgressEnum.DONE,
                            100,
                            true,
                            FileTransferError.NONE,
                            mgr.getQueueSize(),
                            clonePath);

                    publishProgress(progress);
                } catch(IOException e) {
                    Log.d(TAG, uri + " copying caused exception", e);
                    error = true;
                } finally {
                    mgr.setCurrentlyRunning(false);
                    MiscUtils.closeSilently(input);
                    MiscUtils.closeSilently(output);
                    if (cancelled || error) {
                        outputFile.delete();
                        // also delete the record about having a clone
                        currFileStorage.deleteCleartextClone(context.getContentResolver());
                    }
                    if (error) {
                        publishError(FileTransferError.GENERIC_ERROR);
                    }
                }
            } catch (FileStorage.FileStorageException | IOException e) {
                mgr.setCurrentlyRunning(false);
                Log.d(TAG, uri + " caused exception", e);
                publishError(FileTransferError.SECURITY_ERROR);

                if (outputFile != null) outputFile.delete();
                // also delete the record about having a clone
                currFileStorage.deleteCleartextClone(context.getContentResolver());
            }
        }

    }

    private void publishError(FileTransferError error) {
        if (currFileStorage == null || currFileStorage.getUri() == null) {
            Log.w(TAG, "Trying to publish error for null currFileStorage or null uri");
        }
        FileDecryptProgress progress = new FileDecryptProgress(
                currFileStorage.getUri().toString(),
                FileTransferProgressEnum.DECRYPTING_FILES,
                0,
                false,
                error,
                mgr.getQueueSize(),
                null);

        publishProgress(progress);
    }

    private void publishProgress(FileDecryptProgress progress) {
        if (mgr != null) {
            mgr.publishProgress(progress);
        }
    }

    private void scanFile(File file) {
        MediaScannerConnection.scanFile(context, new String[] {file.getAbsolutePath()}, null, new MediaScannerConnection.OnScanCompletedListener() {
            @Override
            public void onScanCompleted(String path, Uri uri) {
                Log.df(TAG, "File decrytped - media scan completed [%s]", path);
            }
        });
    }
}

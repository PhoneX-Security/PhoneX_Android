package net.phonex.ft.storage;

import android.content.Context;
import android.content.Intent;

import net.phonex.core.Intents;
import net.phonex.ft.misc.Canceller;
import net.phonex.pub.parcels.FileDecryptProgress;
import net.phonex.pub.parcels.FileTransferError;
import net.phonex.pub.parcels.FileTransferProgressEnum;
import net.phonex.service.XService;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Matus on 25.6.2015.
 */
public class FileDecryptManager {
    private static final String TAG = "FileDecryptManager";

    private XService svc;

    private ExecutorService executor;

    private DecryptCanceller canceller;

    private Context getContext(){
        return svc.getApplicationContext();
    }

    private AtomicBoolean currentlyRunning;

    public FileDecryptManager(XService svc) {
        this.svc = svc;
        executor = Executors.newSingleThreadExecutor();
        canceller = new DecryptCanceller();
        currentlyRunning = new AtomicBoolean(false);
    }

    private class DecryptCanceller implements Canceller {

        private volatile boolean cancelled;

        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    /**
     * Stores decryption progress.
     * Progress of file decryption is stored here. String is URI of the file
     */
    final private Map<String, FileDecryptProgress> fileDecryptProgress = new ConcurrentHashMap<>();

    final private Queue<String> decryptQueue = new ConcurrentLinkedQueue<>();

    public synchronized void decryptFiles(List<String> uris) {
        List<String> pendingUris = new ArrayList<>();
        for (String uri : uris) {
            if (decryptQueue.contains(uri)) {
                Log.d(TAG, "uri already in queue " + uri);
            } else {
                pendingUris.add(uri);
                decryptQueue.add(uri);
            }
        }

        if (pendingUris.isEmpty()) {
            return;
        }

        for (String uri : pendingUris) {
            publishProgress(new FileDecryptProgress(uri, FileTransferProgressEnum.IN_QUEUE, -1, false, FileTransferError.NONE, decryptQueue.size(), uri));
        }

        FileDecryptTask task = new FileDecryptTask();
        task.setMgr(this);
        task.setContext(svc);
        if (canceller == null || canceller.isCancelled()) {
            Log.d(TAG, "Creating new canceller");
            canceller = new DecryptCanceller();
        }
        task.setCanceller(canceller);

        Log.d(TAG, "file decrypt task added to the queue");
        executor.submit(task);
        expireProgress();
    }

    public synchronized void decryptFile(String uri) {
        Log.d(TAG, "decryptFile(String uri) " + uri);
        ArrayList<String> uris = new ArrayList<>();
        uris.add(uri);
        decryptFiles(uris);
    }

    public synchronized String pollDecryptQueue() {
        String inQueue = decryptQueue.poll();
        if (inQueue != null) currentlyRunning.set(true);
        return inQueue;
    }

    public synchronized void setCurrentlyRunning(boolean currentlyRunning) {
        this.currentlyRunning.set(currentlyRunning);
    }

    public synchronized boolean taskRunningOrPending() {
        return currentlyRunning.get() || decryptQueue.size() > 0;
    }

    public synchronized int getQueueSize() {
        return decryptQueue.size();
    }

    /**
     * Cancels all running and pending decrypt tasks.
     * Only new tasks created by call to decryptFile() after this call will be executed.
     */
    public synchronized void cancelDecrypt() {
        canceller.setCancelled(true);
        Log.df(TAG, "cancelDecrypt()");
        String uri = decryptQueue.poll();
        if (uri == null) {
            Log.df(TAG, "cancelDecrypt() empty queue");
        }
        while (uri != null) {
            Log.df(TAG, "Removed %s from decrypt queue", uri);
            publishProgress(new FileDecryptProgress(uri, FileTransferProgressEnum.CANCELLED, 0, true, FileTransferError.CANCELLED, decryptQueue.size(), uri));
            uri = decryptQueue.poll();
        }
        setCurrentlyRunning(false);
    }

    public void publishProgress(FileDecryptProgress progress){
        // Check if the event actually changed, if not (same progress update), do not broadcast event.
        // Useful for upload process (e.g., upload 48%, upload 48%, ...)
        if (progress == null){
            return;
        }

        FileDecryptProgress prevPr = fileDecryptProgress.put(progress.getUri(), progress);
        if (prevPr!=null && progress.equals(prevPr)){
            // If the previous progress object is the same as current, do not notify
            // end user about this.
            return;
        }

        /*
        if (pr.getTitle() == null){
            pr.setTitle(FileTransferProgress.getTextFromCode(getContext(), pr.getProgressCode()));
        }
        */

        // Broadcast intent informing something changed.
        final String action = Intents.ACTION_SECURE_STORAGE_DECRYPT_PROGRESS;
        final String extra  = Intents.SECURE_STORAGE_DECRYPT_INTENT_PROGRESS;
        final Intent i = new Intent(action);
        i.putExtra(extra, progress);
        MiscUtils.sendBroadcast(getContext(), i);
        Log.inf(TAG, "progress:%s", progress.toString());

        // Progress cleanup if final.
        if (progress != null && progress.isDone()){
            expireProgress();
        }
    }

    public void expireProgress(){
        final int size = fileDecryptProgress.size();
        if (size < 25){
            return;
        }

        final long curTime = System.currentTimeMillis();
        for (Map.Entry<String, FileDecryptProgress> entry : fileDecryptProgress.entrySet()) {
            try {
                if (entry == null){
                    continue;
                }

                final String key = entry.getKey();
                final FileDecryptProgress value = entry.getValue();
                if (key == null || value == null){
                    continue;
                }

                final long when = value.getWhen();
                if (curTime - when > 1000*60*60*3){
                    fileDecryptProgress.remove(key);
                }
            } catch(Exception e){

            }
        }
    }
}

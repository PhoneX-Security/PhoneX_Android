package net.phonex.ft.transfer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.os.Environment;
import android.util.LruCache;

import net.phonex.PhonexSettings;
import net.phonex.core.Intents;
import net.phonex.db.entity.FileTransfer;
import net.phonex.db.entity.ReceivedFile;
import net.phonex.db.entity.SipMessage;
import net.phonex.ft.DHKeyHelper;
import net.phonex.ft.misc.Canceller;
import net.phonex.pref.PreferencesManager;
import net.phonex.pub.parcels.DownloadResult;
import net.phonex.pub.parcels.FileTransferError;
import net.phonex.pub.parcels.FileTransferProgress;
import net.phonex.pub.parcels.FileTransferProgressEnum;
import net.phonex.service.SvcRunnable;
import net.phonex.service.XService;
import net.phonex.service.messaging.AmpDispatcher;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.Registerable;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by dusanklinec on 08.04.15.
 */
public class FileTransferManager implements Registerable {
    private static final String TAG = "TransferManager";

    public static final String ACTION_UPDATE_PROGRESS_DB = "net.phonex.phonex.ft.action.progress";
    public static final String EXTRA_UPDATE_PROGRESS_DB = "net.phonex.phonex.ft.extra.progress";
    public static final String ACTION_DO_CANCEL_TRANSFER = "net.phonex.phonex.ft.action.cancel";
    public static final String EXTRA_DO_CANCEL_TRANSFER = "net.phonex.phonex.ft.extra.cancel";

    /**
     * Cancellation.
     */
    private Canceller canceller;

    /**
     * Whether to write error codes to the SIP message
     */
    private boolean writeErrorToMessage = true;

    /**
     * Operation queue for download and upload tasks.
     * Serial queue for execution in background.
     */
    protected ExecutorService uploadExecutor;
    protected ExecutorService downloadExecutor;

    /**
     * Main scheduling structure for file downloads.
     * Stores PEXFtDownloadEntry.
     */
    final private Queue<DownloadFileListEntry> downloadList = new ConcurrentLinkedQueue<DownloadFileListEntry>();

    /**
     * Main scheduling structure for file uploads.
     * Stores PEXFtUploadEntry.
     */
    final private Queue<UploadFileListEntry> uploadList = new ConcurrentLinkedQueue<UploadFileListEntry>();

    /**
     * Stores download / upload progress.
     * Progress of file transfer is stored here. Long is id of SipMessagefiles
     */
    final private Map<Long, FileTransferProgress> fileTransferProgress = new ConcurrentHashMap<Long, FileTransferProgress>();

    /**
     * Cache to store results of the download process.
     * User may ask for the results, only last X are kept.
     */
    final private LruCache<String, DownloadResult> downloadResult = new LruCache<String, DownloadResult>(10);

    private ManagerReceiver receiver;
    private boolean registered;
    private boolean shouldStartTaskOnConnectionRecovered;
    private boolean lastUploadTaskNoConnection;

    private XService svc;
    // TODO: @property(nonatomic) NSError * lastUploadTaskError;
    // TODO: @property(nonatomic) NSError * lastCheckTaskError;

    public FileTransferManager(XService xService) {
        registered = false;
        uploadExecutor = Executors.newSingleThreadExecutor();
        downloadExecutor = Executors.newSingleThreadExecutor();
        setSvc(xService);
    }

    public void destroy() {
        uploadExecutor.shutdown();
        downloadExecutor.shutdown();

        downloadList.clear();
        uploadList.clear();
    }

    /**
     * Receive connectivity changes so we can react on this.
     */
    public void onConnectivityChangeNotification(Intent intent) {
        if (!Intents.ACTION_CONNECTIVITY_CHANGE.equals(intent.getAction())){
            Log.ef(TAG, "Unknown action %s", intent.getAction());
            return; // Notification not for us.
        }

        // IP changed?
        final boolean recovered = svc.isConnectionValid();
        final WeakReference<FileTransferManager> wMgr = new WeakReference<FileTransferManager>(this);

        svc.executeJob(new SvcRunnable("FtMgrConnChange") {
            @Override
            protected void doRun() throws XService.SameThreadException {
                FileTransferManager mgr = wMgr.get();
                if (mgr == null) {
                    return;
                }

                if (recovered && mgr.shouldStartTaskOnConnectionRecovered) {
                    Log.v(TAG, "Connectivity recovered & previous task failed.");

                    // TODO: start previously interrupted tasks.
                    // ...

                    mgr.shouldStartTaskOnConnectionRecovered = false;
                } else if (recovered) {
                    // Connectivity recovered -> may check DH keys if the last check happened long time ago.
                    // TODO: consider starting depending of the queue size.
                }
            }
        });
    }

    public void onCertUpdated(Intent intent) {

    }

    public void onUserUpdated(Intent intent) {

    }

    public void onAppState(Intent intent) {

    }

    /**
     * Settings has been changed by user, for example language. Reload.
     * @param intent
     */
    public void onSettingsModified(Intent intent){
        final Locale lang = PhonexSettings.loadDefaultLanguageNoThrow(getContext());
        Log.vf(TAG, "onSettingsModified, current locale=%s", lang);
    }

    public synchronized void register(){
        if (registered) {
            Log.w(TAG, "Already registered");
            return;
        }

        receiver = new ManagerReceiver(this);
        IntentFilter intentfilter = new IntentFilter();
        intentfilter.addAction(Intents.ACTION_CONNECTIVITY_CHANGE);
        intentfilter.addAction(Intents.ACTION_REQUEST_TRANSFER_PROGRESS);
        intentfilter.addAction(Intents.ACTION_INIT_DOWNLOAD_PROCESS);
        intentfilter.addAction(Intents.ACTION_UPDATE_FT_PROGRESS);
        intentfilter.addAction(Intents.ACTION_SETTINGS_MODIFIED);

//        intentfilter.addAction(PEX_ACTION_CERT_UPDATED);
//        intentfilter.addAction(PEX_ACTION_CONTACT_ADDED);
//        intentfilter.addAction(PEX_ACTION_CONTACT_REMOVED);
//        intentfilter.addAction(PEX_ACTION_APPSTATE_CHANGE);
        MiscUtils.registerReceiver(getContext(), receiver, intentfilter);

        PhonexSettings.loadDefaultLanguageNoThrow(getContext());
        Log.v(TAG, "FTTransfer Manager registered");
        registered = true;
    }

    public synchronized void unregister() {
        if (!registered) {
            Log.w(TAG, "Already unregistered");
            return;
        }

        getContext().unregisterReceiver(receiver);
        receiver = null;

        Log.v(TAG, "Transfer manager unregistered");
        registered = false;
    }

    public void doCancel() {
        // TODO: implement cancellation of all tasks.
    }

    public static void dispatchDownloadTransfer(Context ctxt, long msgId, Boolean accept) {
        dispatchDownloadTransfer(ctxt,
                SipMessage.getById(ctxt.getContentResolver(), msgId, SipMessage.FULL_PROJECTION),
                accept);
    }

    public static void dispatchDownloadTransfer(Context ctxt, SipMessage msg, Boolean accept) {
        DownloadFileParams params = getDefaultDownloadParams(ctxt, msg.getFileNonce(), msg.getId());

        if (accept != null){
            if (accept){
                params.setFileTypeIdx(DHKeyHelper.ARCH_IDX);
                params.setDownloadFullArchiveNow(true);
                params.setDeleteOnSuccess(true);
            } else {
                params.setDeleteOnly(true);
                params.setFileTypeIdx(DHKeyHelper.META_IDX);
                params.setDownloadFullArchiveNow(false);
                params.setDeleteOnSuccess(true);
            }
        }

        initDownloadProcessProgress(ctxt, msg.getId(), accept);
        AmpDispatcher.dispatchNewFileDownload(ctxt, msg, params);
    }

    /**
     * Initiates download progress in UI.
     *
     * @param ctxt
     * @param msgId
     * @param accept
     */
    public static void initDownloadProcessProgress(Context ctxt, long msgId, Boolean accept){
        final Intent intent = new Intent(Intents.ACTION_INIT_DOWNLOAD_PROCESS);
        intent.putExtra(Intents.EXTRA_INIT_DOWNLOAD_PROCESS, msgId);
        if (accept != null){
            intent.putExtra(Intents.EXTRA_INIT_DOWNLOAD_PROCESS_ACCEPT, accept);
        }

        MiscUtils.sendBroadcast(ctxt, intent);
    }

    /**
     * Returns directory where to store final files from file transfer.
     * @return
     */
    public static File getDownloadStorageFolder(Context ctxt){
        // If destination directory is null, use Android download dir.
        File pubDir = PreferencesManager.getUserSecureStorageFolder(ctxt);
        if (pubDir==null || !pubDir.exists() || !pubDir.canWrite()){
            Log.inf(TAG, "Destination directory invalid: %s", pubDir);

            pubDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        }

        return pubDir;
    }

    public static DownloadFileParams getDefaultDownloadParams(Context ctxt, String nonce2, Long msgId){
        DownloadFileParams params = new DownloadFileParams();

        params.setDestinationDirectory(getDownloadStorageFolder(ctxt));
        params.setMsgId(msgId);
        params.setCreateDestinationDirIfNeeded(true);
        params.setNonce2(nonce2);
        params.setFileTypeIdx(DHKeyHelper.META_IDX);
        params.setDownloadFullArchiveNow(false);
        params.setDeleteOnSuccess(false);
        params.setDownloadFullIfOnWifiAndUnderThreshold(true);
        return params;
    }

    public void enqueueFile2Download(DownloadFileParams params) {
        enqueueFile2Download(params, true, false);
    }

    /**
     * Enqueues file upload request as queued message via AMP dispatcher.
     *
     * @param sipMessageId
     * @param params
     */
    public void enqueueUpload(long sipMessageId, UploadFileParams params){
        SipMessage.setMessageType(getContext().getContentResolver(), sipMessageId, SipMessage.MESSAGE_TYPE_FILE_UPLOADING);
        publishProgress(sipMessageId, FileTransferProgressEnum.IN_QUEUE, -1, true);

        AmpDispatcher.dispatchNewFileUpload(getContext(),
                SipMessage.getById(getContext().getContentResolver(), sipMessageId, SipMessage.FULL_PROJECTION),
                params);
    }

    /**
     * Enqueues file upload request as queued message via AMP dispatcher.
     *
     * @param ctxt
     * @param sipMessageId
     * @param params
     */
    public static void enqueueUpload(Context ctxt, final long sipMessageId, final UploadFileParams params){
        SipMessage.setMessageType(ctxt.getContentResolver(), sipMessageId, SipMessage.MESSAGE_TYPE_FILE_UPLOADING);

        final FileTransferProgress p = new FileTransferProgress(sipMessageId, FileTransferProgressEnum.IN_QUEUE, -1, true);
        publishProgress(ctxt, p);

        AmpDispatcher.dispatchNewFileUpload(ctxt,
                SipMessage.getById(ctxt.getContentResolver(), sipMessageId, SipMessage.FULL_PROJECTION),
                params);
    }

    public void enqueueDownload(String nonce2, Long msgId) {
        DownloadFileParams params = getDefaultDownloadParams(getContext(), nonce2, msgId);

        params.setNonce2(nonce2);
        params.setMsgId(msgId);
        params.setDeleteOnly(false);
        params.setFileTypeIdx(DHKeyHelper.ARCH_IDX);
        params.setDownloadFullArchiveNow(true);
        params.setDeleteOnSuccess(true);
        enqueueFile2Download(params);
    }

    public void enqueueDownloadAccept(String nonce2, Long msgId) {
        DownloadFileParams params = getDefaultDownloadParams(getContext(), nonce2, msgId);
        params.setNonce2(nonce2);
        params.setMsgId(msgId);
        params.setDeleteOnly(false);
        params.setFileTypeIdx(DHKeyHelper.ARCH_IDX);
        params.setDownloadFullArchiveNow(true);
        params.setDeleteOnSuccess(true);
        enqueueFile2Download(params);
    }

    public void enqueueDownloadReject(String nonce2, Long msgId) {
        DownloadFileParams params = getDefaultDownloadParams(getContext(), nonce2, msgId);
        params.setNonce2(nonce2);
        params.setMsgId(msgId);
        params.setDeleteOnly(true);
        params.setFileTypeIdx(DHKeyHelper.META_IDX);
        params.setDownloadFullArchiveNow(false);
        params.setDeleteOnSuccess(true);
        enqueueFile2Download(params);
    }

    /**
     * Add new file to download queue.
     *
     * @param params
     * @param storeResult
     * @param deleteOnly
     */
    public void enqueueFile2Download(DownloadFileParams params, boolean storeResult, boolean deleteOnly) {
        if (params == null){
            Log.e(TAG, "Null parameters");
            return;
        }

        // Set default storage directory if not set.
        if (params.getDestinationDirectory() == null){
            params.setDestinationDirectory(getDownloadStorageFolder(getContext()));
        }

        DownloadFileListEntry le = new DownloadFileListEntry();
        le.storeResult = storeResult;
        le.params = params;
        le.deleteOnly = deleteOnly || params.isDeleteOnly();

        // TODO: Check if not already present.
        downloadList.add(le);

        // Start new task.
        DownloadFileTask task = new DownloadFileTask(svc);
        task.setMgr(this);
        task.setCanceller(canceller);

        // Start task if it is not running.
        // Wait some amount of time in order to group multiple users in one check (optimization).
        // Schedules new task only if there is none scheduled or previous has finished.
        Log.v(TAG, "Download task added to the queue");
        downloadExecutor.submit(task);

        // Add to progress monitor.
        publishProgress(params.getMsgId(), FileTransferProgressEnum.IN_QUEUE, -1, false);
        expireProgress();
    }

    /**
     * Add new file to upload queue.
     *
     * @param params
     */
    public void enqueueFile2Upload(UploadFileParams params) {
        UploadFileListEntry le = new UploadFileListEntry();
        le.params = params;

        // TODO: Check if not already present.
        uploadList.add(le);

        // Add to progress monitor.
        publishProgress(new FileTransferProgress(params.getMsgId(), FileTransferProgressEnum.IN_QUEUE, -1, true));

        // Start new task.
        UploadFileTask task = new UploadFileTask();
        task.setService(svc);
        task.setContext(svc);
        task.setMgr(this);
        task.setWriteErrorToMessage(writeErrorToMessage);
        task.setCanceller(canceller);

        // Start task if it is not running.
        // Wait some amount of time in order to group multiple users in one check (optimization).
        // Schedules new task only if there is none scheduled or previous has finished.
        Log.d(TAG, "Upload task added to the queue");
        uploadExecutor.submit(task);
        expireProgress();
    }

    /**
     * Returns thumbnail directory.
     * @param ctxt
     * @return
     */
    public static File getThumbDirectory(Context ctxt) throws IOException {
        return DHKeyHelper.getThumbDirectory(ctxt);
    }

    /**
     * Deletes all file transfer records associated with given message id.
     */
    public static void deleteTransferRecords(Context ctxt, long dbMessageId) {
        try {
            final File thumbDir = getThumbDirectory(ctxt);
            ReceivedFile.deleteThumbs(ctxt.getContentResolver(), dbMessageId, thumbDir);

        } catch(IOException ex){
            Log.e(TAG, "Problem with removing thumbs", ex);
        }

        // Delete transfer files.
        ReceivedFile.deleteByDbMessageId(ctxt.getContentResolver(), dbMessageId);

        // Delete temporary files (meta, pack).
        FileTransfer.deleteTempFileByDbMessageId(dbMessageId, ctxt);

        // Delete file transfer record.
        FileTransfer.deleteByDbMessageId(dbMessageId, ctxt.getContentResolver());
    }

    public static void deleteTransferRecords(Context ctxt, String username) {
        deleteTransferRecords(ctxt, SipMessage.getAllFileMsgIdsRelatedToUser(username, ctxt.getContentResolver()));
    }

    public static void deleteTransferRecords(Context ctxt, Collection<Long> ids) {
        for(Long id : ids){
            if (id == null){
                Log.e(TAG, "Invalid id, nil!");
                continue;
            }

            deleteTransferRecords(ctxt, id);
        }
    }

    public boolean isDownloadQueueEmpty() {
        return downloadList.isEmpty();
    }

    public DownloadFileListEntry peekDownloadQueue() {
        return downloadList.peek();
    }

    public DownloadFileListEntry pollDownloadQueue() {
        return downloadList.poll();
    }

    public boolean isUploadQueueEmpty() {
        return uploadList.isEmpty();
    }

    public UploadFileListEntry peekUploadQueue() {
        return uploadList.peek();
    }

    public UploadFileListEntry pollUploadQueue() {
        return uploadList.poll();
    }

    /**
     * Cancel transfer related to the given message.
     * @param message SipMessage instance to delete transfer to.
     */
    public void cancelTransfer(SipMessage message){
        if (message == null || message.getId() != -1){
            Log.e(TAG, "Message to cancel transfer is nil");
            return;
        }

        if (message.isOutgoing()){
            cancelUpload(message.getId());
        } else {
            cancelDownload(message.getId());
        }
    }

    /**
     * Cancels transfer in queue.
     * @param messageId
     */
    public void cancelDownload(long messageId){
        boolean foundInQueue = false;
        Iterator<DownloadFileListEntry> iterator = downloadList.iterator();
        for(; iterator.hasNext(); ) {
            DownloadFileListEntry e = iterator.next();
            if (e == null || e.params == null || e.params.getMsgId() == null) {
                continue;
            }

            if (e.params.getMsgId().equals(messageId)) {
                e.cancelled = true;
                foundInQueue = true;

                publishProgress(messageId, FileTransferProgressEnum.CANCELLED, 100, false);
                break;
            }
        }

        // Transmit cancellation event message;
        final Intent i = new Intent(ACTION_DO_CANCEL_TRANSFER);
        i.putExtra(EXTRA_DO_CANCEL_TRANSFER, messageId);
        MiscUtils.sendBroadcast(getContext(), i);

        // TODO: should we return here? Not update? Setting to cancelled may be race conditioning.
        if (foundInQueue){
            return;
        }

        // If message is not found in this queue, it may be probably some old message.
        Log.vf(TAG, "Message to cancel was not found in queue, id=%d", messageId);

        SipMessage msg = SipMessage.getById(getContext().getContentResolver(), messageId,
                new String[] {SipMessage.FIELD_ID, SipMessage.FIELD_TYPE});

        if (msg == null){
            Log.d(TAG, "Message is null");
            return;
        }

        // If in uploading state, move to the ready state.
        // Download probably crashed at some point.
        if (msg.getType() == SipMessage.MESSAGE_TYPE_FILE_DOWNLOADING || msg.getType() == SipMessage.MESSAGE_TYPE_FILE_DOWNLOADING_META){
            Log.v(TAG, "Going to remove message");
            final FileTransfer ft = FileTransfer.initWithNonce2(null, messageId, getContext().getContentResolver());
            final int msgType = ft != null && ft.isMetaDone() ? SipMessage.MESSAGE_TYPE_FILE_DOWNLOADED_META : SipMessage.MESSAGE_TYPE_FILE_READY;

            SipMessage.setMessageType(getContext().getContentResolver(), messageId, msgType);
        }
    }

    /**
     * Cancels transfer in queue.
     * @param messageId SipMessage ID.
     */
    public void cancelUpload(long messageId){
        boolean foundInQueue = false;

        Iterator<UploadFileListEntry> iterator = uploadList.iterator();
        for(; iterator.hasNext(); ) {
            UploadFileListEntry e = iterator.next();
            if (e == null || e.params == null || e.params.getMsgId() == null) {
                continue;
            }

            if (e.params.getMsgId().equals(messageId)) {
                e.cancelled = true;
                foundInQueue = true;

                publishProgress(messageId, FileTransferProgressEnum.CANCELLED, 100, true);
                break;
            }
        }

        // Transmit cancellation event message;
        final Intent i = new Intent(ACTION_DO_CANCEL_TRANSFER);
        i.putExtra(EXTRA_DO_CANCEL_TRANSFER, messageId);
        MiscUtils.sendBroadcast(getContext(), i);

        // TODO: should we return here? Not update? Setting to cancelled may be race conditioning.
        if (foundInQueue){
            return;
        }

        // If message is not found in this queue, it may be probably some old message.
        Log.vf(TAG, "Message to cancel was not found in queue, id=%d", messageId);

        SipMessage msg = SipMessage.getById(getContext().getContentResolver(), messageId,
                new String[] {SipMessage.FIELD_ID, SipMessage.FIELD_TYPE});

        if (msg == null){
            Log.d(TAG, "Message is null");
            return;
        }

        // If in uploading state, move to the ready state.
        // Upload probably crashed at some point.
        if (msg.getType() == SipMessage.MESSAGE_TYPE_FILE_UPLOADING || msg.getType() == SipMessage.MESSAGE_TYPE_FILE_UPLOADING_FILES){
            Log.v(TAG, "Going to remove message");
            SipMessage.deleteById(getContext().getContentResolver(), messageId);
        }
    }

    public void checkForFailedTransfers() {
        // TODO: call this after login to check for failed transfers and to re-queue them.
        // TODO: consider calling on connectivity change.
    }

    /**
     * Publishes error occurred in transfer.
     * @param msgid message ID.
     * @param error
     */
    public void publishError(long msgid, FileTransferError error, boolean isUpload){
        publishError(msgid, error, null, null, isUpload);
    }

    /**
     * Publishes error occurred in transfer.
     * @param msgid SipMessage ID.
     * @param error Error which ocurred in the transfer.
     * @param errCode Integer error code.
     * @param errString Error String.
     */
    public void publishError(long msgid, FileTransferError error, Integer errCode, String errString, boolean isUpload){
        FileTransferProgress p = new FileTransferProgress(msgid, FileTransferProgressEnum.ERROR, 100);
        p.setError(error);
        p.setErrorCode(errCode);
        p.setErrorString(errString);
        p.setDone(true);
        p.setUpload(isUpload);
        publishProgress(p);

        if (writeErrorToMessage){
            SipMessage.setMessageError(getContext().getContentResolver(),
                    msgid,
                    isUpload ? SipMessage.MESSAGE_TYPE_FILE_UPLOAD_FAIL : SipMessage.MESSAGE_TYPE_FILE_DOWNLOAD_FAIL,
                    error.ordinal(),
                    String.format("%s|%s", errCode == null ? "0" : errCode, errString == null ? "" : errString));
        }
    }

    /**
     * Goes through transfer progress structure and removes old records.
     */
    public void expireProgress(){
        final int size = fileTransferProgress.size();
        if (size < 25){
            return;
        }

        final long curTime = System.currentTimeMillis();
        for (Map.Entry<Long, FileTransferProgress> entry : fileTransferProgress.entrySet()) {
            try {
                if (entry == null){
                    continue;
                }

                final Long key = entry.getKey();
                final FileTransferProgress value = entry.getValue();
                if (key == null || value == null){
                    continue;
                }

                final long when = value.getWhen();
                if (curTime - when > 1000*60*60*3){
                    fileTransferProgress.remove(key);
                }
            } catch(Exception e){

            }
        }
    }

    /**
     * Broadcast publish progress via intents.
     * @param ctxt
     * @param pr
     */
    public static void publishProgress(Context ctxt, FileTransferProgress pr){
        final Intent intent = new Intent(Intents.ACTION_UPDATE_FT_PROGRESS);
        intent.putExtra(Intents.EXTRA_UPDATE_FT_PROGRESS, pr);
        MiscUtils.sendBroadcast(ctxt, intent);
    }

    /**
     * Broadcast certificate update state.
     */
    public void publishProgress(FileTransferProgress pr){
        // Check if the event actually changed, if not (same progress update), do not broadcast event.
        // Useful for upload process (e.g., upload 48%, upload 48%, ...)
        if (pr == null){
            return;
        }

        FileTransferProgress prevPr = fileTransferProgress.put(pr.getMessageId(), pr);
        if (prevPr!=null && pr.porgressEquals(prevPr)){
            // If the previous progress object is the same as current, do not notify
            // end user about this.
            return;
        }

        if (pr.getTitle() == null){
            pr.setTitle(FileTransferProgress.getTextFromCode(getContext(), pr.getProgressCode()));
        }

        final boolean isUpload = pr.isUpload();

        // Broadcast intent informing something changed.
        final String action = isUpload ? Intents.ACTION_FILEUPLOAD_PROGRESS : Intents.ACTION_FILEDOWNLOAD_PROGRESS;
        final String extra  = isUpload ? Intents.FILEUPLOAD_INTENT_PROGRESS : Intents.FILEDOWNLOAD_INTENT_PROGRESS;
        final Intent i = new Intent(action);
        i.putExtra(extra, pr);
        MiscUtils.sendBroadcast(getContext(), i);
        Log.inf(TAG, "sending progress:%s", pr.toString());

        // Progress cleanup if final.
        if (pr != null && pr.isDone()){
            expireProgress();
        }
    }

    /**
     * Publishes progress by specifying progress details.
     * @param msgid message ID.
     * @param title
     * @param progress
     */
    public void publishProgress(long msgid, FileTransferProgressEnum title, int progress, boolean isUpload){
        publishProgress(new FileTransferProgress(msgid, title, progress, isUpload));
    }

    /**
     * Publishes DONE progress event.
     * @param msgid message ID.
     */
    public void publishDone(long msgid, boolean isUpload){
        final FileTransferProgress progress = new FileTransferProgress(msgid, FileTransferProgressEnum.DONE, 100, isUpload);
        progress.setDone(true);
        publishProgress(progress);
    }

    /**
     * Informs message manager about finished transfer processing.
     *
     * @param msgId
     * @param queueMsgId
     * @param statusOk
     * @param recoverable
     */
    public void onTransferFinished(final long msgId, final long queueMsgId, final boolean statusOk, final boolean recoverable){
        svc.getMsgManager().onTransferFinished(msgId, queueMsgId, statusOk, recoverable);
    }

    /**
     * Method called when application requests a last transfer progress to be broadcasted.
     * @param intent
     */
    public void requestTransferProgress(Intent intent) {
        Log.v(TAG, "Requesting transfer progress");

        ArrayList<Integer> msgIds = new ArrayList<>();
        if (intent.hasExtra(Intents.EXTRA_REQUEST_TRANSFER_PROGRESS)){
            for (Integer integer : intent.getIntegerArrayListExtra(Intents.EXTRA_REQUEST_TRANSFER_PROGRESS)) {
                msgIds.add(integer);
            }
        }

        if (msgIds.isEmpty()){
            final Set<Long> longs = fileTransferProgress.keySet();
            for(Long lng : longs){
                msgIds.add(lng.intValue());
            }
        }

        for(Integer msgId : msgIds){
            final Long key = msgId.longValue();
            final FileTransferProgress progress = this.fileTransferProgress.get(key);
            if (progress == null){
                continue;
            }

            publishProgress(progress);
        }
    }

    /**
     * Intent broadcasted when message is going to be downloaded.
     * @param intent
     */
    public void initDownloadProcessProgress(Intent intent){
        if (!intent.hasExtra(Intents.EXTRA_INIT_DOWNLOAD_PROCESS)){
            Log.e(TAG, "Invalid intent, no msg id");
            return;
        }

        final long msgId = intent.getLongExtra(Intents.EXTRA_INIT_DOWNLOAD_PROCESS, -1);
        final Boolean accepted = intent.hasExtra(Intents.EXTRA_INIT_DOWNLOAD_PROCESS_ACCEPT) ?
                intent.getBooleanExtra(Intents.EXTRA_INIT_DOWNLOAD_PROCESS_ACCEPT, true) : null;

        getSvc().executeJob(new SvcRunnable() {
            @Override
            protected void doRun() throws XService.SameThreadException {
                int newMsgType;
                if (accepted != null && !accepted) {
                    newMsgType = SipMessage.MESSAGE_TYPE_FILE_REJECTED;
                } else if (accepted != null) {
                    newMsgType = SipMessage.MESSAGE_TYPE_FILE_DOWNLOADING;
                } else {
                    newMsgType = SipMessage.MESSAGE_TYPE_FILE_DOWNLOADING_META;
                }

                SipMessage.setMessageType(getContext().getContentResolver(), msgId, newMsgType);
                publishProgress(msgId, FileTransferProgressEnum.IN_QUEUE, -1, false);
            }
        });
    }

    /**
     * Process progress update.
     * @param intent
     */
    public void processProgressUpdate(final Intent intent){
        if (intent == null || !intent.hasExtra(Intents.EXTRA_UPDATE_FT_PROGRESS)){
            Log.e(TAG, "progress update intent is not properly configured.");
            return;
        }

        final FileTransferProgress p = (FileTransferProgress) intent.getParcelableExtra(Intents.EXTRA_UPDATE_FT_PROGRESS);
        publishProgress(p);
    }

    /**
     * Static method to request progress update for all messages stored in msgIds.
     * If null / empty, all messages stored in progress map are broadcasted.
     */
    public static void requestTransferProgress(Context ctxt, List<Long> msgIds){
        if (ctxt == null){
            Log.w(TAG, "Requesting transfer progress with null context");
            return;
        }

        ArrayList<Integer> ids = new ArrayList<>();
        if (msgIds != null){
            for (Long msgId : msgIds) {
                ids.add(msgId.intValue());
            }
        }

        final Intent intent = new Intent(Intents.ACTION_REQUEST_TRANSFER_PROGRESS);
        intent.putExtra(Intents.EXTRA_REQUEST_TRANSFER_PROGRESS, ids);
        MiscUtils.sendBroadcast(ctxt, intent);
    }

    /**
     * Returns true if the current network connection is valid.
     * @return
     */
    public boolean isConnected(){
        return svc.isConnectionValid() && svc.isConnectivityValid();
    }

    /**
     * Returns current active network networkInfo.
     * @return
     */
    public NetworkInfo getCurrentNetworkInfo(){
        return svc.getCurrentNetworkInfo();
    }

    /**
     * Stores download result to the LRU cache for later retrieval.
     * @param key
     * @param res
     */
    public void setDownloadResult(String key, DownloadResult res){
        downloadResult.put(key, res);
    }

    /**
     * Returns download result from a download process, if is in a cache.
     *
     * @param nonce2
     * @return
     */
    public synchronized DownloadResult getDownloadResult(final String nonce2, boolean freeResult){
        DownloadResult res = downloadResult.get(nonce2);
        if (res!=null){
            downloadResult.remove(nonce2);
        }

        return res;
    }

    /**
     * Returns stored progress for the given transfer.
     * @param sipMessageId
     * @return
     */
    public Object getFileTransferProgress(long sipMessageId) {
        return fileTransferProgress.get(sipMessageId);
    }

    private Context getContext(){
        return svc.getApplicationContext();
    }

    public XService getSvc() {
        return svc;
    }

    public void setSvc(XService svc) {
        this.svc = svc;
    }

    /**
     * Broadcast receiver for intents.
     */
    public static class ManagerReceiver extends BroadcastReceiver {
        final private WeakReference<FileTransferManager> wMgr;

        public ManagerReceiver(FileTransferManager wMgr) {
            this.wMgr = new WeakReference<FileTransferManager>(wMgr);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (MiscUtils.isEmpty(action)){
                return;
            }

            FileTransferManager mgr = wMgr.get();
            if (mgr == null){
                Log.d(TAG, "Manager is null");
                return;
            }

            switch (action){
                case "abc":
                    mgr.onAppState(intent);
                    break;

                case "def":
                    mgr.onCertUpdated(intent);
                    break;

                case Intents.ACTION_CONNECTIVITY_CHANGE:
                    mgr.onConnectivityChangeNotification(intent);
                    break;

                case "jkl":
                    mgr.onUserUpdated(intent);
                    break;

                case Intents.ACTION_REQUEST_TRANSFER_PROGRESS:
                    mgr.requestTransferProgress(intent);
                    break;

                case Intents.ACTION_INIT_DOWNLOAD_PROCESS:
                    mgr.initDownloadProcessProgress(intent);
                    break;

                case Intents.ACTION_UPDATE_FT_PROGRESS:
                    mgr.processProgressUpdate(intent);
                    break;

                case Intents.ACTION_SETTINGS_MODIFIED:
                    mgr.onSettingsModified(intent);
                    break;

                default:
                    Log.ef(TAG, "Unknown action: %s", action);
            }
        }
    }
}

package net.phonex.ft.transfer;

import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;

import net.phonex.R;
import net.phonex.core.SipUri;
import net.phonex.db.entity.FileTransfer;
import net.phonex.db.entity.ReceivedFile;
import net.phonex.db.entity.SipMessage;
import net.phonex.db.entity.SipProfile;
import net.phonex.ksoap2.SoapEnvelope;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.pub.parcels.FileTransferProgress;
import net.phonex.pub.parcels.FileTransferError;
import net.phonex.pub.proto.FileTransfer.GetDHKeyResponseBodySCip;
import net.phonex.service.XService;
import net.phonex.soap.SOAPException;
import net.phonex.soap.SOAPHelper;
import net.phonex.soap.SSLSOAP;
import net.phonex.soap.ServiceConstants;
import net.phonex.soap.entities.FtGetDHKeyPart2Request;
import net.phonex.soap.entities.FtGetDHKeyPart2Response;
import net.phonex.soap.entities.FtGetDHKeyRequest;
import net.phonex.soap.entities.FtGetDHKeyResponse;
import net.phonex.ft.DHKeyHelper;
import net.phonex.ft.misc.Canceller;
import net.phonex.ft.FTHolder;
import net.phonex.ft.misc.OperationCancelledException;
import net.phonex.ft.misc.TransmitProgress;
import net.phonex.ft.FTHolder.UploadResult;
import net.phonex.util.Base64;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.crypto.CertificatesAndKeys;
import net.phonex.util.crypto.CryptoHelper;
import net.phonex.util.crypto.KeyPairGenerator;
import net.phonex.util.crypto.MessageDigest;
import net.phonex.util.crypto.PRNGFixes;
import net.phonex.service.messaging.AmpDispatcher;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Date;

import net.phonex.pub.parcels.FileTransferProgressEnum;

/**
 * File upload task
 *
 * @author ph4r05
 * @author miroc
 */	
public class UploadFileTask implements Runnable {
	public final static String TAG = "UploadFileTask";

	private Context context;
    private volatile boolean wasCancelled = false;
    private UploadFileListEntry curEntry = null;
    private final Object curEntryLock = new Object();

    /**
     * Global task cancellation.
     */
    private Canceller canceller;

	/**
	 * Whether to write error codes to the SIP message
	 */
	private boolean writeErrorToMessage = true;

    /**
     * Service instance.
     */
    private XService service;

    /**
     * File transfer manager to use.
     */
    private FileTransferManager mgr;

    /**
     * Upload state of the task.
     */
    private UploadState uState;

    private SecureRandom srand;

    /**
     * Identity of the sender.
     */
    final private CertificatesAndKeys.UserIdentity identity = new CertificatesAndKeys.UserIdentity();

    /**
     * Broadcast receiver.
     */
    private Receiver receiver;

    /**
     * Returns true if current connection can be used.
     * @return
     */
    protected boolean isConnectivityWorking(){
        return mgr.isConnected();
    }

    /**
     * Loads identity from global resources (in-memory database) into parameters provided.
     * Context has to be set.
     *
     * @param rand
     * @param ctx
     * @return <0 on error (-1 = problem with on-memory, -2 = problem with priv key).
     */
    protected int loadIdentity(SecureRandom rand, Context ctx){
        return CertificatesAndKeys.IdentityLoader.loadIdentity(identity, rand, ctx);
    }

    /**
     * Reacts on cancellation event broadcasted via intents.
     * @param intent
     */
    protected void onCancelEvent(Intent intent) {
        if (intent == null) {
            return;
        }

        if (!FileTransferManager.ACTION_DO_CANCEL_TRANSFER.equals(intent.getAction())){
            Log.ef(TAG, "Unknown action %s", intent);
            return; // Notification not for us.
        }

        long cancelId = intent.getLongExtra(FileTransferManager.EXTRA_DO_CANCEL_TRANSFER, -1);
        if (cancelId == -1) {
            return;
        }

        synchronized (curEntryLock){
            if (curEntry != null && curEntry.params != null && curEntry.params.getMsgId() != null && cancelId == curEntry.params.getMsgId()) {
                curEntry.cancelled = true;
            }
        }
    }

	@Override
	public void run() {
        srand = new SecureRandom();
        loadIdentity(srand, getContext());

        Log.v(TAG, "Download task started");
        receiver = new Receiver(this);
        IntentFilter intentfilter = new IntentFilter();
        intentfilter.addAction(FileTransferManager.ACTION_DO_CANCEL_TRANSFER);
        MiscUtils.registerReceiver(getContext(), receiver, intentfilter);

		/**
		 * while there are some files to send, initiate upload method
		 */
		while (!wasCancelled() && !mgr.isUploadQueueEmpty()) {
			final UploadFileListEntry e = mgr.pollUploadQueue();
            if (e == null || e.params == null){
                Log.w(TAG, "Upload entry with null content or parameters");
                continue;
            }

			if (!e.cancelled){
                synchronized (curEntryLock) {
                    curEntry = e;
                }

				upload(e);

                synchronized (curEntryLock) {
                    curEntry = null;
                }

                // Signalize to messaging framework upload was successful or not.
                mgr.onTransferFinished(uState.msgId, uState.queueMsgId, uState.operationSuccessful, uState.recoverableFault);
			} else {
				// Remove message - it is canceled, no sending...
                Log.vf(TAG, "Upload task cancelled: %s", e.params.getMsgId());
				SipMessage.deleteById(getContext().getContentResolver(), e.params.getMsgId());
			}
        }

        getContext().unregisterReceiver(receiver);
        Log.v(TAG, "Upload task finished");
	}

	/**
	 * Main working method for file upload.
	 *
	 * @param par Upload queue entry.
	 */
    protected void upload(final UploadFileListEntry par){
		try {
            uploadFile(par);

		} catch (OperationCancelledException cex){
			// Cancelled.
			Log.i(TAG, "Operation was cancelled");
			SipMessage.deleteById(getContext().getContentResolver(), par.params.getMsgId());

		} catch (Exception e) {
			Log.e(TAG, "Exception", e);
		}
	}

    /**
     * Whole uplaod file logic (dhkeys fetch, computation, upload).
     *
     * @param par Upload queue entry.
     * @throws OperationCancelledException
     */
    protected void uploadFile(final UploadFileListEntry par) throws OperationCancelledException {
        /**
         * Initialize SecureRandom, it may take some time, thus it is initialized in
         * background task. It is also needed prior first SSL connection
         * or key generation.
         */
        PRNGFixes.apply();
        srand = new SecureRandom();
        uState = new UploadState();
        uState.params = par.params;// createUploadParams(par.fileAbsolutePaths, par.destinationSip, srand);
        uState.msgId = par.params.getMsgId();
        uState.queueMsgId = par.params.getQueueMsgId();
        uState.operationSuccessful = false;
        publishProgress(new FileTransferProgress(uState.msgId, FileTransferProgressEnum.INITIALIZATION, -1));

        // Canceller - current parameter.
        uState.canceller = new Canceller() {
            @Override
            public boolean isCancelled() {
                return par.cancelled;
            }
        };

        Log.vf(TAG, "Starting upload task for msgId: %s", uState.msgId);
        try {
            // Fetch message from database.
            uState.msg = SipMessage.getById(getContext().getContentResolver(), uState.msgId, SipMessage.FULL_PROJECTION);

            // Load necessary key information & prepare for remote calls.
            prepareSoapDefault(identity.getStoredCredentials().getUsrStoragePass().toCharArray());
            uState.domain = SipUri.parseSipUri(SipUri.getCanonicalSipContact(identity.getStoredCredentials().getUserSip(), true)).domain;

            // Try to recover file transfer state so we can support upload resumption.
            FileTransfer ftTmp = FileTransfer.initWithNonce2(uState.nonce2, uState.msgId, context.getContentResolver());
            if (ftTmp != null){
                uState.transferRecord   = ftTmp;
                uState.transferRecordId = ftTmp.getId();

                // Recover crypto state from file transfer record.
                uState.throwIfCancel();
                recoverFromTransferRecord();
                Log.vf(TAG, "FT record restored from DB for %s, msgId: %d", uState.nonce2, uState.msgId);
            }

            // Do key compute if necessary.
            if (uState.transferRecord == null || !uState.transferRecord.isKeyComputingDone()) {
                Log.v(TAG, "Upload key computation phase started");
                publishProgress(new FileTransferProgress(uState.msgId, FileTransferProgressEnum.DOWNLOADING_KEYS, -1));
                uState.throwIfCancel();

                loadCrt();
                prepareDhHelper();

                // Get DH key part 1.
                getDHKeyPart1(par);

                // Was operation cancelled?
                uState.throwIfCancel();

                // Get DH key part 2
                getDHKeyPart2(par);

                // Was operation cancelled?
                uState.throwIfCancel();

                Log.v(TAG, "Key verification started");
                publishProgress(new FileTransferProgress(uState.msgId, FileTransferProgressEnum.KEY_VERIFICATION, -1));

                // Verify key correctness.
                sigVerify();

                //
                // Key is loaded from the server, can continue with upload.
                //

                // AT first, initialize FTKey holder, it generates encryption keys
                // for the file transfer protocol.
                Log.v(TAG, "Generating encryption keys");
                publishProgress(new FileTransferProgress(uState.msgId, FileTransferProgressEnum.COMPUTING_ENC_KEYS, -1));
                uState.ftHolder = uState.dhelper.initFTHolder(uState.resp1, uState.nonce2b);

                // Was operation cancelled?
                uState.throwIfCancel();

                // Process files to send (holder gets initialized by those).
                Log.v(TAG, "Going to proces input files");
                publishProgress(new FileTransferProgress(uState.msgId, FileTransferProgressEnum.ENCRYPTING_FILES, -1));

                uState.dhelper.setTxprogress(new ProcessingProgressMonitor(uState.msgId));
                uState.preUploadHolder = uState.dhelper.ftSetFilesToSend(uState.ftHolder, uState.params);

                // Store transfer record after all key material was derived. Checkpoint for state recovery.
                storeTransferRecord();

                // Store all transfer files to database so they can be forwarded.
                // It has to be done with preUploadHandler, after recovery on backof this information is
                // not available anymore.
                storeUploadFiles();
            }

            // If file was not uploaded successfully.
            uState.throwIfCancel();
            if (uState.transferRecord == null || !uState.transferRecord.isPackDone()) {
                SipMessage.setMessageType(getContext().getContentResolver(), uState.msgId, SipMessage.MESSAGE_TYPE_FILE_UPLOADING_FILES);

                uState.updResult = new FTHolder.UploadResult();
                try {
                    // Upload files to the server.
                    Log.v(TAG, "Going to upload file");
                    publishProgress(new FileTransferProgress(uState.msgId, FileTransferProgressEnum.UPLOADING, -1));

                    // Was operation cancelled?
                    uState.throwIfCancel();
                    final String pass = identity.getStoredCredentials().getUsrStoragePass();
                    throwIfOperationCancelled(par);

                    // Set progress monitor to the DHKeyHelper.
                    uState.dhelper.setTxprogress(new UploadProgressMonitor(uState.msgId));
                    uState.dhelper.setDebug(false);
                    uState.dhelper.uploadFile(uState.ftHolder, SOAPHelper.getDefaultKeyStore(context, pass.toCharArray()), pass, uState.updResult);

                    if (!DHKeyHelper.wasUploadSuccessful(uState.updResult)){
                        uState.recoverableFault = true;
                        uploadFailed(uState.msgId, uState.updResult);
                        return;
                    }

                    // Remove temporary files
                    uState.dhelper.cleanFiles(uState.ftHolder);

                    // store nonce2 in SipMessage (may be useful when resending)
                    final ContentValues cv = new ContentValues();
                    cv.put(SipMessage.FIELD_FILE_NONCE, uState.nonce2);
                    cv.put(SipMessage.FIELD_RANDOM_NUM, srand.nextInt()); // also set SipMessage nonce id (required for read-acknowledgment)
                    int suc = SipMessage.updateMessage(getContext().getContentResolver(), uState.msgId, cv);
                    Log.vf(TAG, "SipMessage for upload updated: %s", suc);

                    // Store FileTransfer & ReceivedFiles records.
                    // File upload is over here.
                    storeTransferSuccessful();
                    uState.operationSuccessful = true;

                } catch (OperationCancelledException ccex) {
                    // Operation was cancelled - will be handled later
                    uState.updResult.cancelDetected = true;
                    publishError(uState.msgId, FileTransferError.CANCELLED);
                    throw ccex;

                } catch (UploadException ue) {
                    // Upload exception - error was published already, do nothing.

                } catch (IOException ioex){
                    // IOexception ocurred probably due to connectivity error when reading/writing socket data.
                    Log.ef(TAG, ioex, "IOException during upload, may be recoverable - connectivity error");
                    uState.recoverableFault = true;
                    uState.updResult.ioErrorDetected = true;
                    publishError(uState.msgId, FileTransferError.TIMEOUT);

                    // Re-throw new exception so we signalize error hapenned and was already handled.
                    throw new UploadException(ioex);

                } catch (Exception ex) {
                    // Generic exception.
                    Log.ef(TAG, ex, "Exception in a upload process");
                    publishError(uState.msgId, FileTransferError.GENERIC_ERROR);
                }
            }

            // If everything went OK, set upload as correctly finished.
            if (uState.operationSuccessful) {
                finishUploadCorrectly(uState.msgId);
            }

        } catch(OperationCancelledException ccex){
            // Operation was cancelled - will be handled later
            throw ccex;

        } catch(UploadException ue){
            // Upload exception - error was already published.

        } catch(Exception ex){
            // Generic exception.
            Log.e(TAG, "Exception in a upload process.", ex);
            publishError(uState.msgId, FileTransferError.GENERIC_ERROR);
        }
    }

    /**
     * For reference and possible resending, store files in ReceivedFile table (legacy name, but now stores downloaded and also uploaded files).
     */
    protected void storeUploadFiles() {
        final Date date = new Date();

        // Now store new record for each sent file.
        for(FTHolder.FileEntry fe : uState.preUploadHolder.files2send){
            ReceivedFile recvFile = new ReceivedFile();
            recvFile.setFileNonce(uState.nonce2);
            recvFile.setMsgId(uState.msgId);
            recvFile.setTransferId(uState.transferRecordId);
            recvFile.setFilename(fe.originalFilename);
            recvFile.setMimeType(fe.metaMsg.getMimeType());
            Log.df(TAG, "Was file modified? [%b] SHA256 of original was set? [%b] SHA256 of original file and new file differs? [%b]",
                    fe.tempUri != null, fe.originalSha256 != null, !Arrays.equals(fe.sha256, fe.originalSha256));
            if (fe.originalSha256 == null) {
                // file was not modified (e.g. photo resized) during upload, hash is the same
                fe.originalSha256 = fe.sha256;
            }
            recvFile.setFileHash(Base64.encodeBytes(fe.originalSha256));
            recvFile.setTitle(fe.metaMsg.getTitle());
            recvFile.setDesc(fe.metaMsg.getDesc());
            recvFile.setPath(fe.originalFile.getAbsolutePath());
            recvFile.setSize(fe.originalFileSize);
            recvFile.setFileOrder(fe.metaMsg.getPrefOrder());
            recvFile.setDateReceived(date);
            recvFile.setThumbnailName(null);
            recvFile.setRecordType(ReceivedFile.PEX_RECV_FILE_FULL);
            recvFile.setStorageUri(fe.originalUri.toString());

            final Uri fileUri = getContext().getContentResolver().insert(ReceivedFile.URI, recvFile.getDbContentValues());
            if (fileUri == null){
                Log.ef(TAG, "DbFileTransferFile insert failed, obj=%s", recvFile);
            } else {
                Log.vf(TAG, "Uploaded file stored to DbFileTransferFile, id=%s, obj=%s", fileUri, recvFile);
            }
        }
    }

    /**
     * Load all certificates needed for file upload.
     */
    protected void loadCrt(){
        uState.senderCrt = CertificatesAndKeys.getRemoteCertificate(context, uState.params.getDestinationSip());
    }

    /**
     * Prepares DHKeyHelper instance for this particular upload.
     */
    protected void prepareDhHelper() throws CertificateException {
        uState.dhelper = new DHKeyHelper();
        uState.dhelper.setCtxt(context);
        uState.dhelper.setConnectionTimeoutMilli(30000);
        uState.dhelper.setReadTimeoutMilli(32000);
        uState.dhelper.setMySip(identity.getStoredCredentials().getUserSip());
        uState.dhelper.setMyCert(identity.getUserPrivateCredentials().getCert());
        uState.dhelper.setPrivKey(identity.getUserPrivateCredentials().getPk());
        uState.dhelper.setRand(srand);
        uState.dhelper.setUserSip(uState.params.getDestinationSip()); // user B
        uState.dhelper.setSipCert(uState.senderCrt.getCertificateObj()); //user B certificate
        uState.dhelper.setCanceller(new UploadFileListEntryCanceller(curEntry, this));
    }

    /**
     * Performs getDHKeyPart1 SOAP call itself, with 3 retries (connectivity problems).
     * @return
     * @throws IOException
     */
    protected Object getDHKeyPart1Call() throws SOAPException {
        final SoapSerializationEnvelope soapEnvelope = createSoapEnvelopeForGetDHKeyPart1(uState.params);
        Object toReturn = null;
        IOException ioex2throw = null;

        for(int i=0; i<3; i++){
            try {
                uState.throwIfCancel();
                toReturn = SOAPHelper.simpleSOAPRequest(
                        soapEnvelope,
                        ServiceConstants.getDefaultURL(uState.domain, this.context),
                        "ftGetDHKeyRequest",
                        true);
                break;
            } catch(IOException ioexc) {
                Log.v(TAG, "IOException in DHKeyPart1Call()", ioexc);
                ioex2throw = ioexc;
            }
        }

        if (ioex2throw != null){
            throw new SOAPException(ioex2throw);
        }

        return toReturn;
    }

    /**
     * Performs getDHKeyPart2 SOAP call itself, with 3 retries (connectivity problems).
     * @return
     * @throws IOException
     */
    protected Object getDHKeyPart2Call(final GetDHKeyResponseBodySCip response1) throws SOAPException, NoSuchAlgorithmException, UnsupportedEncodingException {
        // Build SOAP request.
        final FtGetDHKeyPart2Request req2 = new FtGetDHKeyPart2Request();
        req2.setNonce1(Base64.encodeBytes(MessageDigest.hashSha256(response1.getNonce1())));
        req2.setUser(uState.params.getDestinationSip());

        // Build SOAP message envelope.
        final SoapSerializationEnvelope soapEnvelope2 = createSoapEnvelopeForGetDHKeyPart2(uState.params, response1);

        Object toReturn = null;
        IOException ioex2throw = null;

        for(int i=0; i<3; i++){
            try {
                uState.throwIfCancel();
                toReturn = SOAPHelper.simpleSOAPRequest(soapEnvelope2,
                        ServiceConstants.getDefaultURL(uState.domain, this.context),
                        "ftGetDHKeyPart2Request", true);
                break;
            } catch(IOException ioexc) {
                Log.v(TAG, "IOException in DHKeyPart2Call()", ioexc);
                ioex2throw = ioexc;
            }
        }

        if (ioex2throw != null){
            throw new SOAPException(ioex2throw);
        }

        return toReturn;
    }

    /**
     * Calls GetDhKeyPart1.
     * @param par
     * @return
     * @throws OperationCancelledException
     * @throws Exception
     */
    protected void getDHKeyPart1(final UploadFileListEntry par) throws IOException, UploadException, CryptoHelper.CipherException, CertificateEncodingException, CryptoHelper.SignatureException, NoSuchAlgorithmException {
        throwIfOperationCancelled(par);
        publishProgress(new FileTransferProgress(uState.msgId, FileTransferProgressEnum.DOWNLOADING_KEYS, -1));

        Object obj = null;
        try {
            obj = getDHKeyPart1Call();
        } catch(SOAPException se){
            Log.w(TAG, "SOAP exception, recoverable error");
            uState.recoverableFault = true;

            publishError(uState.msgId, FileTransferError.TIMEOUT);
            throw new UploadException("SOAP exception", se);
        }

        if (!(obj instanceof FtGetDHKeyResponse)){
            Log.w(TAG, "Bad format of response from server - probably problem during unmarshaling");

            publishError(uState.msgId, FileTransferError.BAD_RESPONSE);
            throw new UploadException("Bad format of response");
        }

        final FtGetDHKeyResponse getKeyResponse = (FtGetDHKeyResponse) obj;
        if (getKeyResponse.getErrCode() < 0){
            Log.wf(TAG, "Received negative error code (%s)", getKeyResponse.getErrCode());

            publishError(uState.msgId, FileTransferError.UPD_NO_AVAILABLE_DHKEYS);
            throw new UploadException("Cannot get response keys");
        }

        throwIfOperationCancelled(par);

        // get msg1(dh_group_id, g^x, nonce_1, sig_1)
        uState.resp1 = uState.dhelper.getDhKeyResponse(getKeyResponse.getAEncBlock());

        // verify sig_1
        if (!uState.dhelper.verifySig1(uState.resp1, null, uState.resp1.getSig1().toByteArray())){
            Log.w(TAG, "sig_1 verification failed");

            publishError(uState.msgId, FileTransferError.SECURITY_ERROR);
            throw new UploadException("sig_1 is not correct");
        }
    }

    /**
     * Calls GetDhKeyPart2.
     * @param par
     * @return
     * @throws OperationCancelledException
     * @throws Exception
     */
    protected void getDHKeyPart2(final UploadFileListEntry par) throws IOException, NoSuchAlgorithmException, UploadException {
        Object obj2 = null;
        try {
            obj2 = getDHKeyPart2Call(uState.resp1);
        } catch(SOAPException se){
            Log.w(TAG, "SOAP exception, recoverable error");
            uState.recoverableFault = true;

            publishError(uState.msgId, FileTransferError.TIMEOUT);
            throw new UploadException("SOAP exception", se);
        }

        // process part 2
        if (!(obj2 instanceof FtGetDHKeyPart2Response)){
            Log.w(TAG, "Bad format of response2 from server - probably problem during unmarshaling");

            publishError(uState.msgId, FileTransferError.BAD_RESPONSE);
            throw new UploadException("Bad format of response2");
        }

        throwIfOperationCancelled(par);

        Log.v(TAG, "Key verification started");
        publishProgress(new FileTransferProgress(uState.msgId, FileTransferProgressEnum.KEY_VERIFICATION, -1));

        uState.getKeyResponse2 = (FtGetDHKeyPart2Response) obj2;
        if (uState.getKeyResponse2.getErrCode() < 0){
            Log.wf(TAG, "Received negative error code (%s)", uState.getKeyResponse2.getErrCode());

            publishError(uState.msgId, FileTransferError.GENERIC_ERROR);
            throw new UploadException("Cannot get response key part 2");
        }
    }

    /**
     * Throws OperationCancelledException if given upload entry was set to cancelled.
     * @param par
     * @throws OperationCancelledException
     */
    protected static void throwIfOperationCancelled(final UploadFileListEntry par) throws OperationCancelledException {
        if(par.cancelled){
            throw new OperationCancelledException();
        }
    }

    /**
     * Creates SOAP message for GetDhKeyPart1.
     * @param params
     * @return
     */
    protected static SoapSerializationEnvelope createSoapEnvelopeForGetDHKeyPart1(final UploadFileParams params) {

        final FtGetDHKeyRequest keyRequest = new FtGetDHKeyRequest();
        keyRequest.setIgnoreNullWrappers(true);
        keyRequest.setUser(params.getDestinationSip());

        final SoapSerializationEnvelope result = new SoapSerializationEnvelope(SoapEnvelope.VER11);
        keyRequest.register(result);
        new FtGetDHKeyResponse().register(result);
        result.setOutputSoapObject(keyRequest);

        return result;
    }

    /**
     * Creates SOAP message for GetDhKeyPart2.
     * @param params
     * @param responseFromParOne
     * @return
     * @throws NoSuchAlgorithmException
     * @throws UnsupportedEncodingException
     */
    protected static SoapSerializationEnvelope createSoapEnvelopeForGetDHKeyPart2(final UploadFileParams params, final GetDHKeyResponseBodySCip responseFromParOne) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        final FtGetDHKeyPart2Request request = new FtGetDHKeyPart2Request();
        request.setNonce1(Base64.encodeBytes(MessageDigest.hashSha256(responseFromParOne.getNonce1())));
        request.setUser(params.getDestinationSip());

        final SoapSerializationEnvelope result = new SoapSerializationEnvelope(SoapEnvelope.VER11);
        request.register(result);
        new FtGetDHKeyPart2Response().register(result);
        result.setOutputSoapObject(request);

        return result;
    }

    /**
     * Reads nonce2 from response, verifies signature2 with nonce2.
     * @throws IOException
     * @throws UploadException
     * @throws CryptoHelper.CipherException
     * @throws CertificateEncodingException
     * @throws CryptoHelper.SignatureException
     * @throws NoSuchAlgorithmException
     */
    protected void sigVerify()
            throws IOException, UploadException, CryptoHelper.CipherException,
            CertificateEncodingException, CryptoHelper.SignatureException, NoSuchAlgorithmException {

        // Read nonce2 from last message of the GetKey protocol (will be file identifier).
        uState.nonce2  = uState.getKeyResponse2.getNonce2();
        uState.nonce2b = Base64.decode(uState.nonce2);

        // Verification of the Signature2 (contains also nonce2).
        byte[] sig2 = uState.dhelper.getDhPart2Response(uState.getKeyResponse2.getSig2());
        if (!uState.dhelper.verifySig1(uState.resp1, uState.nonce2, sig2)){
            Log.w(TAG, "sig_2 verification failed");

            publishError(uState.msgId, FileTransferError.SECURITY_ERROR);
            throw new UploadException("sig_2 is not correct");
        }
    }

    /**
     * Stores current upload state to the database so it can be recovered on crash recovery event.
     */
    protected void storeTransferRecord() throws IOException {
        FileTransfer tr = new FileTransfer();
        tr.setNonce2(uState.nonce2);
        tr.setMessageId(uState.msgId);
        tr.setIsOutgoing(true);

        // No crypto material is needed since we do not support.
        if (uState.ftHolder != null && uState.ftHolder.c != null) {
            tr.setNonce1(uState.ftHolder.nonce1);
            tr.setNonceb(Base64.encodeBytes(uState.ftHolder.nonceb));
            tr.setSalt1( Base64.encodeBytes(uState.ftHolder.salt1));
            tr.setSaltb( Base64.encodeBytes(uState.ftHolder.saltb));
            tr.setC(     Base64.encodeBytes(uState.ftHolder.c));
        }

        // Number of files, title, description.
        if (uState.preUploadHolder != null) {
            tr.setNumOfFiles(MiscUtils.collectionSize(uState.preUploadHolder.files2send));
            if (uState.preUploadHolder.mf != null && uState.preUploadHolder.mf.hasTitle()) {
                tr.setTitle(uState.preUploadHolder.mf.getTitle());
            }
            if (uState.preUploadHolder.mf != null && uState.preUploadHolder.mf.hasDescription()) {
                tr.setDescr(uState.preUploadHolder.mf.getDescription());
            }
        } else {
            tr.setNumOfFiles(null);
            tr.setTitle(null);
            tr.setDescr(null);
        }

        tr.setThumbDir(DHKeyHelper.getThumbDirectory(getContext()).getAbsolutePath());
        tr.setDeletedFromServer(false);
        tr.setDateCreated(new Date());
        tr.setDateFinished(null);
        tr.setStatusCode(0);

        // Fields for upload resumption.
        tr.setuKeyData(uState.ftHolder.ukeyData);
        tr.setMetaPrepRec(uState.ftHolder.filePrepRec[DHKeyHelper.META_IDX]);
        tr.setPackPrepRec(uState.ftHolder.filePrepRec[DHKeyHelper.ARCH_IDX]);

        tr.setMetaFile(uState.ftHolder.filePath[DHKeyHelper.META_IDX]);
        tr.setPackFile(uState.ftHolder.filePath[DHKeyHelper.ARCH_IDX]);

        tr.setMetaHash(Base64.encodeBytes(uState.ftHolder.fileHash[DHKeyHelper.META_IDX]));
        tr.setPackHash(Base64.encodeBytes(uState.ftHolder.fileHash[DHKeyHelper.ARCH_IDX]));

        tr.setMetaSize(uState.ftHolder.fileSize[DHKeyHelper.META_IDX]);
        tr.setPackSize(uState.ftHolder.fileSize[DHKeyHelper.ARCH_IDX]);

        tr.setMetaState(FileTransfer.FILEDOWN_TYPE_NONE);
        tr.setPackState(FileTransfer.FILEDOWN_TYPE_NONE);
        tr.setShouldDeleteFromServer(false);

        Uri insUri = context.getContentResolver().insert(FileTransfer.URI, tr.getDbContentValues());
        uState.transferRecord = tr;
        uState.transferRecordId = ContentUris.parseId(insUri);
        if (uState.transferRecordId == -1){
            Log.ef(TAG, "Could not store transfer record, %s", tr);
            return;
        }

        Log.vf(TAG, "FileTransfer record stored. id=%s, obj=%s", insUri, tr);
    }

    /**
     * Recovers upload state from stored file transfer record.
     * @throws IOException
     */
    protected void recoverFromTransferRecord() throws IOException, GeneralSecurityException {
        FileTransfer tr = uState.transferRecord;
        FTHolder     ft = new FTHolder();

        uState.nonce2 = tr.getNonce2();
        ft.nonce2 = Base64.decode(uState.nonce2);
        ft.c      = tr.getC()      == null ? null : Base64.decode(tr.getC());
        ft.nonce1 = tr.getNonce1() == null ? null : tr.getNonce1();
        ft.nonceb = tr.getNonceb() == null ? null : Base64.decode(tr.getNonceb());
        ft.salt1  = tr.getSalt1()  == null ? null : Base64.decode(tr.getSalt1());
        ft.saltb  = tr.getSaltb()  == null ? null : Base64.decode(tr.getSaltb());
        uState.destination = SipUri.getCanonicalSipContact(uState.msg.getTo(), false);

        // Fields for upload resumption.
        ft.ukeyData = tr.getuKeyData();
        ft.filePrepRec[DHKeyHelper.META_IDX] = tr.getMetaPrepRec();
        ft.filePrepRec[DHKeyHelper.ARCH_IDX] = tr.getPackPrepRec();

        ft.filePath[DHKeyHelper.META_IDX]    = tr.getMetaFile();
        ft.filePath[DHKeyHelper.ARCH_IDX]    = tr.getPackFile();

        ft.fileHash[DHKeyHelper.META_IDX]    = tr.getMetaHash() == null ? null : Base64.decode(tr.getMetaHash());
        ft.fileHash[DHKeyHelper.ARCH_IDX]    = tr.getPackHash() == null ? null : Base64.decode(tr.getPackHash());

        ft.fileSize[DHKeyHelper.META_IDX]    = tr.getMetaSize();
        ft.fileSize[DHKeyHelper.ARCH_IDX]    = tr.getPackSize();

        loadCrt();
        prepareDhHelper();

        // Compute all symmetric keys from master secret.
        uState.dhelper.computeCi(ft);
        uState.ftHolder = ft;
    }

    /**
     * Store successful transfer to the database - so state is not recovered again.
     */
    protected void storeTransferSuccessful() {
        if (uState.transferRecordId == null){
            Log.e(TAG, "Cannot store transfer successful record, transfer record ID is nil");
            return;
        }

        uState.transferRecord.setMetaState(FileTransfer.FILEDOWN_TYPE_DONE);
        uState.transferRecord.setPackState(FileTransfer.FILEDOWN_TYPE_DONE);
        uState.transferRecord.setPackHash("");
        uState.transferRecord.setMetaHash("");
        uState.transferRecord.setDateFinished(new Date());

        // Clear crypto material, not needed in DB anymore.
        uState.transferRecord.clearCryptoMaterial();
        boolean success = updateFtRecord();
        if(!success){
            Log.ef(TAG, "Could not update transfer record. %s", uState.transferRecord);
        }
    }

    /**
     * Updates database file transfer record from current state.
     * @return
     */
    protected boolean updateFtRecord() {
        if (uState.transferRecord == null || uState.transferRecordId == null){
            Log.e(TAG, "Cannot update file transfer progress, nil encountered");
            return false;
        }

        return context.getContentResolver().update(FileTransfer.URI,
                uState.transferRecord.getDbContentValues(),
                FileTransfer.FIELD_ID+"=?",
                new String[] { uState.transferRecordId.toString() }) > 0;
    }

    protected void uploadFailed(final long sipMessageId, final UploadResult uploadResult) {
        Log.i(TAG, "Upload was not successful!");

        // Get error code
        Integer errCode = DHKeyHelper.getUploadErrorCode(uploadResult);
        if (Integer.valueOf(-2).equals(errCode)){
            // No such key on the server side.
            publishError(sipMessageId, FileTransferError.UPD_UPLOAD_ERROR);

        } else if (Integer.valueOf(-8).equals(errCode)){
            // Quota exceeded.
            publishError(sipMessageId, FileTransferError.UPD_QUOTA_EXCEEDED);
            uState.recoverableFault = false;

        } else if (Integer.valueOf(-10).equals(errCode)){
            // Quota exceeded.
            publishError(sipMessageId, FileTransferError.UPD_FILE_TOO_BIG);
            uState.recoverableFault = false;

        } else {
            // Generic error
            publishError(sipMessageId, FileTransferError.UPD_UPLOAD_ERROR);
        }
    }

    protected void finishUploadCorrectly(final long sipMessageId) {

        publishProgress(new FileTransferProgress(sipMessageId, FileTransferProgressEnum.SENDING_NOTIFICATION, 100));
        postUpload(sipMessageId);

        // Final progress update.
        final FileTransferProgress progress =
                new FileTransferProgress(sipMessageId, FileTransferProgressEnum.DONE, 100);

        progress.setDone(true);
        publishProgress(progress);

        // Change state if no error.
        SipMessage.setMessageType(getContext().getContentResolver(), sipMessageId, SipMessage.MESSAGE_TYPE_FILE_UPLOADED);
    }

	/**
	 * send notification message
	 * @param messageId SipMessage id where nonce2 and filename is stored
	 */
    protected void postUpload(long messageId) {
        try {
            AmpDispatcher.dispatchNewFileNotification(getContext(), (int) messageId);
        } catch (Exception e) {
            Log.e(TAG, "Notification message failed", e);
        }
	}

	/**
	 * Publishes error occurred in transfer.
	 * @param msgid
	 * @param error
	 */
	protected void publishError(long msgid, FileTransferError error){
		publishError(msgid, error, null, null);
	}

	/**
	 * Publishes error occurred in transfer.
	 * @param msgid SipMessage ID.
	 * @param error Error which ocurred in the transfer.
	 * @param errCode Integer error code.
	 * @param errString Error String.
	 */
	protected void publishError(long msgid, FileTransferError error, Integer errCode, String errString){
		mgr.publishError(msgid, error, errCode, errString, true);
	}

	/**
	 * Broadcast certificate update state.
	 */
    protected void publishProgress(FileTransferProgress pr){
        pr.setUpload(true);
		mgr.publishProgress(pr);
	}

	/**
	 * Reads default key store in default container defined by CertificatesAndKeys.PKCS12Container
	 * 
	 * @param pass password to keystore.
	 * @throws KeyStoreException
	 * @throws NoSuchAlgorithmException
	 * @throws CertificateException
	 * @throws IOException
	 * @throws UnrecoverableKeyException
	 */
    protected void prepareSoapDefault(char[] pass) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, UnrecoverableKeyException{
		KeyPairGenerator kpg = new KeyPairGenerator();
        SipProfile currentProfile = SipProfile.getCurrentProfile(context);
        KeyStore ks = kpg.readKeyStore(CertificatesAndKeys.derivePkcs12Filename(currentProfile.getSip()), context, pass);
    	this.prepareSoap(ks, pass);
	}
	
	/**
	 * Prepares SOAP channel 
	 * @throws IOException 
	 * @throws CertificateException 
	 * @throws NoSuchAlgorithmException 
	 * @throws KeyStoreException 
	 * @throws UnrecoverableKeyException 
	 */
    protected void prepareSoap(KeyStore ks, char[] pass) throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
		/**
		 * Preparing phase - initialize SSL connections
		 * 
		 * Install HTTPS support with client credentials and trust verifier
		 */
		try {
			SSLSOAP.installTrustManager4HTTPS(ks, pass, this.context);
	    	Log.v(TAG, "Initialized default ssl socket factory - with client certificate");
		}catch(FileNotFoundException e){
			Log.e(TAG, "Could not find file with certificate");
		}
	}

    /**
     * Returns true if the local canceller signalizes a canceled state.
     * @return true if task was cancelled.
     */
    private boolean wasCancelled(){
        return wasCancelled || (canceller != null && canceller.isCancelled());
    }

    /**
     * Cancel current task.
     */
    public void doCancel(){
        wasCancelled = true;
    }

	public Context getContext() {
		return context;
	}

	public void setContext(Context context) {
		this.context = context;
	}
	
	public boolean isWriteErrorToMessage() {
		return writeErrorToMessage;
	}

	public void setWriteErrorToMessage(boolean writeErrorToMessage) {
		this.writeErrorToMessage = writeErrorToMessage;
	}

    public XService getService() {
        return service;
    }

    public void setService(XService service) {
        this.service = service;
    }

    public FileTransferManager getMgr() {
        return mgr;
    }

    public void setMgr(FileTransferManager mgr) {
        this.mgr = mgr;
    }

    public Canceller getCanceller() {
        return canceller;
    }

    public void setCanceller(Canceller canceller) {
        this.canceller = canceller;
    }

    /**
     * Base class for upload progress monitor.
     */
    private static abstract class ProgressMonitor extends TransmitProgress {
        protected final long sipMessageId;

        public ProgressMonitor(final long sipMessageId) {
            this.sipMessageId = sipMessageId;
        }
    }

    /**
     * Progress monitor for upload process.
     */
    private class UploadProgressMonitor extends ProgressMonitor {

        public UploadProgressMonitor(final long sipMessageId){super(sipMessageId);}

        @Override
        public void updateTxProgress(Double partial, double total) {
            final int pcnts = Math.min((int)Math.ceil(total*100.0), 100);
            final FileTransferProgress progress = new FileTransferProgress(sipMessageId, FileTransferProgressEnum.UPLOADING, pcnts);
            progress.setTitle(getContext().getString(R.string.upd_p_uploading) + ": " + pcnts + "%");

            publishProgress(progress);
        }
    }

    /**
     * Progress monitor for processing (enc).
     */
    private class ProcessingProgressMonitor extends ProgressMonitor {

        public ProcessingProgressMonitor(final long sipMessageId){super(sipMessageId);}

        @Override
        public void updateTxProgress(Double partial, double total) {
            final int pcnts = Math.min((int)Math.ceil(total*100.0), 100);
            final FileTransferProgress progress = new FileTransferProgress(sipMessageId, FileTransferProgressEnum.ENCRYPTING_FILES, pcnts);
            progress.setTitle(getContext().getString(R.string.upd_p_processing_input) + ": " + pcnts + "%");

            publishProgress(progress);
        }
    }

    /**
     * Canceller used for upload process cancellation.
     */
    private static class UploadFileListEntryCanceller implements Canceller {
        private final WeakReference<UploadFileListEntry> par;
        private final WeakReference<UploadFileTask> task;

        public UploadFileListEntryCanceller(final UploadFileListEntry par, final UploadFileTask task) {
            this.par = new WeakReference<UploadFileListEntry>(par);
            this.task = new WeakReference<UploadFileTask>(task);
        }

        @Override
        public boolean isCancelled() {
            final UploadFileListEntry uploadFileListEntry = par.get();
            if (uploadFileListEntry != null && uploadFileListEntry.cancelled){
                return true;
            }

            final UploadFileTask uploadFileTask = task.get();
            return (uploadFileTask != null && uploadFileTask.wasCancelled()) ? true : false;
        }
    }

    /**
     * Broadcast receiver for intents.
     */
    protected static class Receiver extends BroadcastReceiver {
        final private WeakReference<UploadFileTask> task;

        public Receiver(UploadFileTask wMgr) {
            this.task = new WeakReference<UploadFileTask>(wMgr);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (MiscUtils.isEmpty(action)){
                return;
            }

            UploadFileTask tsk = task.get();
            if (tsk == null){
                Log.d(TAG, "Manager is null");
                return;
            }

            switch (action){
                case FileTransferManager.ACTION_DO_CANCEL_TRANSFER:
                    tsk.onCancelEvent(intent);
                    break;
                default:
                    Log.ef(TAG, "Unknown action: %s", action);
            }
        }
    }
}


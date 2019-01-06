package net.phonex.ft.transfer;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.CancellationSignal;
import android.text.TextUtils;

import net.phonex.R;
import net.phonex.core.SipUri;
import net.phonex.core.SipUri.ParsedSipUriInfos;
import net.phonex.db.entity.DHOffline;
import net.phonex.db.entity.FileTransfer;
import net.phonex.db.entity.ReceivedFile;
import net.phonex.db.entity.SipMessage;
import net.phonex.db.entity.Thumbnail;
import net.phonex.ft.DHKeyHelper;
import net.phonex.ft.FTHolder;
import net.phonex.ft.FTHolder.UnpackingOptions;
import net.phonex.ft.misc.Canceller;
import net.phonex.ft.misc.OperationCancelledException;
import net.phonex.ft.misc.TransmitProgress;
import net.phonex.ksoap2.SoapEnvelope;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.pub.parcels.DownloadResult;
import net.phonex.pub.parcels.FileTransferError;
import net.phonex.pub.parcels.FileTransferProgress;
import net.phonex.pub.parcels.FileTransferProgressEnum;
import net.phonex.service.XService;
import net.phonex.soap.DefaultSOAPCall;
import net.phonex.soap.SOAPException;
import net.phonex.soap.SOAPHelper;
import net.phonex.soap.ServiceConstants;
import net.phonex.soap.entities.FtDeleteFilesRequest;
import net.phonex.soap.entities.FtDeleteFilesResponse;
import net.phonex.soap.entities.FtGetStoredFilesRequest;
import net.phonex.soap.entities.FtGetStoredFilesResponse;
import net.phonex.soap.entities.FtNonceList;
import net.phonex.soap.entities.FtStoredFileList;
import net.phonex.util.Base64;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.crypto.CertificatesAndKeys;
import net.phonex.util.crypto.CertificatesAndKeys.UserIdentity;
import net.phonex.util.crypto.CryptoHelper;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import javax.crypto.NoSuchPaddingException;


/**
 * File download task.
 * 
 * @author miroc
 * @author ph4r05
 */
public class DownloadFileTask extends DefaultSOAPCall {
	public final static String TAG = "DownloadFileTask";
	public static final long DownloadOnWifiThreshold = 1024 * 1024 * 5;
	
	/**
	 * Whether to show Android Notifications during download.
	 */
	private boolean showNotifications = false;
	
	/**
	 * Whether to write error codes to the SIP message
	 */
	private boolean writeErrorToMessage = true;
	
	/**
	 * Delete files from server (e.g., after successful download) ?
	 */
	private boolean deleteFromServer = true;
	
	/**
	 * Manual cancellation of the download process. 
	 */
	private volatile boolean manualCancel = false;

	/**
	 * Identity of the sender.
	 */
	final private UserIdentity identity = new UserIdentity();

	private Canceller canceller;
	private CancellationSignal cancelSignal;

	/**
	 * File transfer manager instance.
	 */
	private FileTransferManager mgr;

	/**
	 * All state related information is stored here.
	 */
	private DownloadState dState;

	/**
	 * Current download entry being processed.
	 */
	private DownloadFileListEntry curEntry = null;
	private final Object curEntryLock = new Object();

	/**
	 * Broadcast receiver.
	 */
	private Receiver receiver;

	public DownloadFileTask(Context context) {
		super();
		this.setContext(context);
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
		SecureRandom rand = new SecureRandom();
		loadIdentity(rand, getContext());

		Log.v(TAG, "Download task started");
		receiver = new Receiver(this);
		final IntentFilter intentfilter = new IntentFilter();
		intentfilter.addAction(FileTransferManager.ACTION_DO_CANCEL_TRANSFER);
		MiscUtils.registerReceiver(getContext(), receiver, intentfilter);

		// Initialize SOAP connections.
		try {
			this.initSoap(identity.getStoredCredentials().getUsrStoragePass());
		} catch(Exception e){
			Log.e(TAG, "Cannot init soap", e);
			return;
		}
		
		/**
		 * while there are some files to download.
		 */
		while (!wasCancelled() && !mgr.isDownloadQueueEmpty()){
			final DownloadFileListEntry e = mgr.pollDownloadQueue();
			if (e == null || e.params == null){
				Log.w(TAG, "Upload entry with null content or parameters");
				continue;
			}

			if (!e.cancelled){
				synchronized (curEntryLock) {
					curEntry = e;
				}

				try {
					processMessage(e);
				} catch (IOException e1) {
					Log.ef(TAG, "Exception in processing message %s", e);
				}

				synchronized (curEntryLock) {
					curEntry = null;
				}

				// Signalize to messaging framework upload was successful or not.
				mgr.onTransferFinished(dState.msgId, dState.queueMsgId, !dState.didErrorOccurr, dState.recoverableFault);

			} else {
				// Just mark cancelled - transition to a ready state.
				Log.vf(TAG, "Download task cancelled: %s", e.params.getMsgId());
				SipMessage.setMessageType(getContext().getContentResolver(), e.params.getMsgId(), SipMessage.MESSAGE_TYPE_FILE_READY);
			}
		}

		getContext().unregisterReceiver(receiver);
		Log.v(TAG, "Download task finished");
	}
	
	/**
	 * Main method that actually downloads the file specified by the parameter.
	 * 
	 * @param par
	 * @return
	 */
	protected void processMessage(final DownloadFileListEntry par) throws IOException {
		dState = new DownloadState();
		dState.msgId = par.params.getMsgId();
		dState.queueMsgId = par.params.getQueueMsgId();
		dState.nonce2 = par.params.getNonce2();
		dState.didErrorOccurr = true;
		dState.recoverableFault = false;
		dState.params = par.params;
		dState.deleteOnly = par.deleteOnly || par.params.isDeleteOnly();
		dState.downloadPackRightNow |= par.params.isDownloadFullArchiveNow();
		dState.didTimeout = false;
		dState.didCancel = false;
		dState.didMacFail = false;
		
		publishProgress(dState.msgId, FileTransferProgressEnum.RETRIEVING_FILE, 0);
		
		// Load msg from DB if message is not null.
		if (!loadDbMessage()){
			Log.w(TAG, "Message could not be loaded");
			return;
		}
		
		final List<String> filePaths = new LinkedList<String>();
		try {
			// Get my domain
			ParsedSipUriInfos mytsinfo = SipUri.parseSipUri(SipUri.getCanonicalSipContact(identity.getStoredCredentials().getUserSip(), true));
			dState.domain = mytsinfo.domain;

			// Set SOAP timeout.
			this.getSoap().setTimeout(30000);

			// Detect if the record was processed at some point and try to recover from the stored state.
			FileTransfer ftTmp = FileTransfer.initWithNonce2(dState.nonce2, dState.msgId, getContext().getContentResolver());
			if (ftTmp != null) {
				dState.transferRecord = ftTmp;
				dState.transferRecordId = ftTmp.getId();

				// Recover crypto state from file transfer record.
				dState.throwIfCancel();
				recoverFromTransferRecord();
				Log.vf(TAG, "FT record restored from DB for %s, msgId: %s", dState.nonce2, dState.msgId);
			}

			// If the file should be only deleted from the server.
			if (par.deleteOnly && (dState.transferRecord == null || !dState.deletedFromServer)) {
				// Store transfer information so we have marked it for deletion.
				Log.vf(TAG, "FT record marked as delete-only: %s", dState.nonce2);
				dState.throwIfCancel();
				storeTransferRecord();

				deleteOnly(par);
				return;
			}

			// If the record is nil or key was not computed yet, download has to proceed this step.
			dState.throwIfCancel();
			if (dState.transferRecord == null || !dState.transferRecord.isKeyComputingDone()) {
				// Soap call to load meta data from server - list of files with given nonce.
				SipMessage.setMessageType(getContext().getContentResolver(), dState.msgId, SipMessage.MESSAGE_TYPE_FILE_DOWNLOADING_META);
				publishProgress(dState.msgId, FileTransferProgressEnum.LOADING_INFORMATION, -1);
				loadStoredFilesFromServer();

				// Load corresponding DH key and sender certificate.
				loadKey();
				loadCert();

				// Was transfer cancelled?
				dState.throwIfCancel();

				// initialize DHKeyHelper for processing the protocol
				publishProgress(dState.msgId, FileTransferProgressEnum.COMPUTING_ENC_KEYS, -1);

				// Initialize DHKeyHelper and compute FTHolder data (enc keys). If security error occurs, exception is thrown.
				computeHolder();
				// setting message id and sender for thumbnail
				dState.ftHolder.messageId = dState.msgId;
				dState.ftHolder.sender = dState.sender;

				// Store all transfer related info so we can decrypt archive file with this information.
				storeTransferRecord();

				// Remove DHKeys from database.
				// After previous step all data needed for archive file decryption is stored in filetransfer DB record.
				DHOffline.delete(getContext().getContentResolver(), dState.nonce2, dState.sender);
			}

			// Meta file processing.
			// Meta file fetch - is it needed?
			// TODO: handle partial downloads of the meta/pack files.
			if (!dState.transferRecord.isMetaDone()) {
				dState.throwIfCancel();
				Log.vf(TAG, "Going to fetch meta file for %s", dState.nonce2);

				// Download & decrypt meta file so we have detailed information about transmitted files, including thumbs.
				// After this step there should be TransferRecord and ReceivedFile records in database with info.
				publishProgress(dState.msgId, FileTransferProgressEnum.CONNECTING_TO_SERVER, -1);
				dState.transferRecord.setMetaState(FileTransfer.FILEDOWN_TYPE_STARTED);
				updateFtRecord();

				fetchAndProcessMeta();

				// Meta file is considered processed at this point
				dState.transferRecord.setMetaState(FileTransfer.FILEDOWN_TYPE_DONE);
				updateFtRecord();

				// Store downloaded meta state = we have meta information now. Try to load thumbs in view.
				SipMessage.setMessageType(getContext().getContentResolver(), dState.msgId, SipMessage.MESSAGE_TYPE_FILE_DOWNLOADED_META);
			}

			if (!dState.transferRecord.isPackDone() && !dState.downloadPackRightNow) {
				// File is ready to be downloaded, archive file was not fetched now.
				setFinalMessageOKType();
			}

			// Archive / pack file processing.
			// If meta indicates pack should be downloaded right now, do it.
			// TODO: handle partial downloads of the meta/pack files.
			if (!dState.transferRecord.isPackDone() && dState.downloadPackRightNow) {
				dState.throwIfCancel();
				Log.vf(TAG, "Going to fetch archive file for %s", dState.nonce2);
				SipMessage.setMessageType(getContext().getContentResolver(), dState.msgId, SipMessage.MESSAGE_TYPE_FILE_DOWNLOADING);

				// This downloads, decrypts, verifies and extracts archive file content.
				fetchAndProcessPack();
				dState.transferRecord.setPackState(FileTransfer.FILEDOWN_TYPE_DONE);
				dState.transferRecord.clearCryptoMaterial();
				updateFtRecord();

				// Mark message as downloaded. Process finished with this message.
				SipMessage.setMessageType(getContext().getContentResolver(), dState.msgId, SipMessage.MESSAGE_TYPE_FILE_DOWNLOADED);
			}

			// File downloaded, delete it from the server if successful to save inbox space and server resources.
			if (dState.transferRecord.isPackDone() && deleteFromServer) {
				publishProgress(dState.msgId, FileTransferProgressEnum.DELETING_FROM_SERVER, -1);

				// Mark for deletion so it is deleted even after the succeding delete attempt fails.
				dState.transferRecord.setShouldDeleteFromServer(true);
				updateFtRecord();

				// Clean download artifacts.
				dState.dhelper.cleanFiles(dState.ftHolder);

				// Delete from server call.
				dState.deletedFromServer = deleteFileFromServer(dState.nonce2, null);

				// If delete process was successful, mark deletion to the DB so manager knows it succeeds and
				// it does not have to repeat it.
				if (dState.deletedFromServer) {
					dState.transferRecord.setDeletedFromServer(true);
					dState.transferRecord.setDateFinished(new Date());
					updateFtRecord();

					// Mark message as downloaded. Process finished with this message.
					SipMessage.setMessageType(getContext().getContentResolver(), dState.msgId, SipMessage.MESSAGE_TYPE_FILE_DOWNLOADED);
				}
			}

			// Finished.
			dState.didErrorOccurr = false;
			publishDone(dState.msgId);

		} catch (OperationCancelledException cex){
			// Cancelled.
			Log.i(TAG, "Operation was cancelled");
			dState.didCancel = true;
			publishProgress(dState.msgId, FileTransferProgressEnum.CANCELLED, 100);
			setFinalMessageOKType();
			
		} catch (DownloadException de){
			// Download exception - error was as progress status.
            Log.e(TAG, "DownloadException", de);

		} catch(CryptoHelper.MACVerificationException macexc){
			Log.i(TAG, "MAC verification exception during download, try to re-download several times", macexc);
			dState.didMacFail = true;

			// TODO: allow only several MAC fails in a row. May be caused by re-download or crippled download.
			dState.recoverableFault = true;
			publishError(dState.msgId, FileTransferError.DOWN_DOWNLOAD_ERROR);

		} catch (IOException tex){
			// Timeout exception.
			Log.i(TAG, "IOexception.", tex);
			dState.didTimeout = true;
			dState.recoverableFault = true;
			publishError(dState.msgId, FileTransferError.TIMEOUT);

		} catch (Exception e) {
			// Generic exception.
			Log.e(TAG, "Exception in a download process.", e);
			
			publishError(dState.msgId, FileTransferError.GENERIC_ERROR);
		}
		
		// Store result of the download to the LRU cache. 
		if (par.storeResult){
			mgr.setDownloadResult(dState.nonce2,
					new DownloadResult(
							dState.didErrorOccurr ? FileTransferError.GENERIC_ERROR : FileTransferError.NONE,
							System.currentTimeMillis(),
							filePaths));
		}

		// Error handling block.
		if (dState.didErrorOccurr) {
			if (dState.recoverableFault) {
				// Recoverable fault - set message state appropriately so it can be tried later.
				Log.i(TAG, "Seems error is recoverable, we should try it later, several times");
				setFinalMessageOKType();

			} else if (!dState.deletedFromServer && !dState.didCancel && !dState.didTimeout) {
				// Non-recoverable file, delete from server to free space.
				dState.deletedFromServer = deleteFileFromServer(dState.nonce2, null);
			}
		}
		
		// After download, trigger DH key resync.
		// Download means somebody used our key, thus we
		// have to generate new ones.
		XService.triggerDHKeyUpdate(getContext());
	}

	/**
	 * Sets message to READY state, or if meta was downloaded, to DOWNLOADED_META.
	 */
	private void setFinalMessageOKType(){
		int messageType = SipMessage.MESSAGE_TYPE_FILE_READY;
		if (dState.transferRecord != null && dState.transferRecord.isMetaDone()){
			messageType = SipMessage.MESSAGE_TYPE_FILE_DOWNLOADED_META;
		}

		SipMessage.setMessageType(getContext().getContentResolver(), dState.msgId, messageType);
	}

	/**
	 * Loads SipMessage related to the file transfer in state to the state.
	 * @return
	 */
	protected boolean loadDbMessage(){
		SipMessage msg = null;
		if (dState.msgId != null){
			msg = SipMessage.getById(ctxt.getContentResolver(), dState.msgId, SipMessage.FULL_PROJECTION);
			if (msg==null){
				Log.ef(TAG, "No such message in DB with id:%s", dState.msgId);
				return false;

			} else if (MiscUtils.isEmpty(msg.getFileNonce())){
				Log.e(TAG, "No nonce stored within SipMessage, it does not correspont to any uploaded file");
				publishError(dState.msgId, FileTransferError.DOWN_NO_SUCH_FILE_FOR_NONCE);
				return false;
			}

			// If nonce2 specified by parameter is null, take the one from message.
			if (TextUtils.isEmpty(dState.nonce2)){
				dState.nonce2 = msg.getFileNonce();
			} else {
				// If nonce2 is non-null, it has to correspond to the one in message ID.
				if (!dState.nonce2.equals(msg.getFileNonce())){
					Log.w(TAG, String.format("Nonce2 from parameter [%s] does not correspond to nonce in msg [%s]", dState.nonce2, msg.getFileNonce()));
				}
			}
		}

		dState.msg = msg;
		return true;
	}

	/**
	 * Load stored file info from server keyed by nonce2 and stores to the state.
	 * @throws DownloadException
	 */
	protected void loadStoredFilesFromServer() throws DownloadException {
		Object obj = null;
		try {
			obj = getStoredFilesCall(dState.nonce2);
		} catch(SOAPException se){
			Log.w(TAG, "SOAP exception, set fault to recoverable");
			dState.recoverableFault = true;

			publishError(dState.msgId, FileTransferError.TIMEOUT);
			throw new DownloadException("SOAP exception", se);
		}

		// Series of controls.
		final FtGetStoredFilesResponse storedFilesResponse = (FtGetStoredFilesResponse) obj;
		if (storedFilesResponse.getErrCode()<0){
			Log.wf(TAG, "Received negative error code (%s)", storedFilesResponse.getErrCode());

			publishError(dState.msgId, FileTransferError.GENERIC_ERROR);
			throw new DownloadException("Cannot get list of stored files");
		}

		// Response from server - stored files with given nonce2.
		FtStoredFileList storedFiles = storedFilesResponse.getStoredFile();
		Log.inf(TAG, "%s file(s) stored on server for given nonce [nonce2=%s]", storedFiles.size(), dState.nonce2);
		if (storedFiles.size()<=0){

			publishError(dState.msgId, FileTransferError.DOWN_NO_SUCH_FILE_FOR_NONCE);
			throw new DownloadException("No stored file(s) for given nonce2="+dState.nonce2);
		}

		dState.storedFile = storedFiles.firstElement();
		dState.sender = SipUri.getCanonicalSipContact(dState.storedFile.getSender(), false);
	}

	/**
	 * Performs basic get stored files request, with retry counter.
	 * Returns plain object returned from SOAP call.
	 * @param nonce2
	 * @return
	 * @throws IOException
	 */
	protected Object getStoredFilesCall(final String nonce2) throws SOAPException {
		final SoapSerializationEnvelope soapEnvelope = createEnvelopeGetStoredFiles(nonce2);

		Object obj = null;
		IOException ioex2throw = null;
		for(int i=0; i<3; i++){
			try {
				obj = this.getSoap().simpleSOAPRequestEx(soapEnvelope,
						ServiceConstants.getDefaultURL(dState.domain, ctxt),
						"ftGetStoredFilesRequest", true);
				break;
			} catch(IOException ioexc) {
				Log.v(TAG, "IOException in getStoredFilesCall()", ioexc);
				ioex2throw = ioexc;
			}
		}

		if (ioex2throw != null){
			throw new SOAPException(ioex2throw);
		}

		return obj;
	}

	/**
	 * Creates SOAP envelope for SOAP request getStoredFiles.
	 * @param nonce2
	 * @return
	 */
	protected SoapSerializationEnvelope createEnvelopeGetStoredFiles(final String nonce2){
		FtNonceList nonceList = new FtNonceList();
		nonceList.add(nonce2);
		FtGetStoredFilesRequest req = new FtGetStoredFilesRequest();
		req.setNonceList(nonceList);

		SoapSerializationEnvelope soapEnvelope = new SoapSerializationEnvelope(SoapEnvelope.VER11);
		req.register(soapEnvelope);
		new FtGetStoredFilesResponse().register(soapEnvelope);
		soapEnvelope.setOutputSoapObject(req);

		return soapEnvelope;
	}

	/**
	 * Deletes file from the server to save server mailbox capacity. 
	 * @param nonce2
	 * @param domain 		Domain of the user for SOAP call, if null, uses loaded identity.
	 * @throws Exception  
	 */
	protected boolean deleteFileFromServer(final String nonce2, final String domain) throws IOException {
		String domain2use = domain;
		if (domain2use==null){
			if (identity==null || identity.getStoredCredentials()==null){
				Log.e(TAG, "Both domain2use and identity are null");
				return false;
			}
			
			// Get my domain
			ParsedSipUriInfos mytsinfo = SipUri.parseSipUri(SipUri.getCanonicalSipContact(identity.getStoredCredentials().getUserSip(), true));
			domain2use = mytsinfo.domain;
		}

		return deleteFileFromServerCall(nonce2, domain2use);
	}

	/**
	 * Deletes file from the server to save server mailbox capacity.
	 * @param nonce2
	 * @param domain 		Domain of the user for SOAP call, if null, uses loaded identity.
	 * @throws Exception
	 */
	protected boolean deleteFileFromServerCall(final String nonce2, final String domain) throws IOException {
		final SoapSerializationEnvelope soapEnvelope = createEnvelopeDeleteFromServer(nonce2);
		boolean success = false;

		IOException ioex2throw = null;
		for(int i=0; i<3; i++){
			try {
				this.getSoap().simpleSOAPRequestEx(soapEnvelope,
						ServiceConstants.getDefaultURL(domain, ctxt),
						"ftDeleteFilesRequest", true);
				success = true;
				break;
			} catch(IOException ioexc) {
				Log.v(TAG, "IOException in ftDeleteFilesRequest()", ioexc);
				ioex2throw = ioexc;
			}
		}

		if (ioex2throw != null){
			throw ioex2throw;
		}

		return success;
	}

	/**
	 * Creates the whole SOAP envelope for sending delete from server request.
	 * @param nonce2
	 * @return
	 */
	protected SoapSerializationEnvelope createEnvelopeDeleteFromServer(final String nonce2){
		FtNonceList nonceList = new FtNonceList();
		nonceList.add(nonce2);

		FtDeleteFilesRequest req = new FtDeleteFilesRequest();
		req.setDeleteAll(false);
		req.setNonceList(nonceList);

		SoapSerializationEnvelope soapEnvelope = new SoapSerializationEnvelope(SoapEnvelope.VER11);
		req.register(soapEnvelope);
		new FtDeleteFilesResponse().register(soapEnvelope);
		soapEnvelope.setOutputSoapObject(req);

		return soapEnvelope;
	}

	/**
	 * Loads certificate for the sender.
	 */
	protected void loadCert() throws DownloadException {
		// Loads remote party certificate.
		dState.senderCrt = CertificatesAndKeys.getRemoteCertificate(getContext(), dState.sender);
		if(dState.senderCrt ==null){
			publishError(dState.msgId, FileTransferError.CERTIFICATE_MISSING);
			throw new DownloadException("No stored certificate for user="+dState.sender);
		}
	}

	/**
	 * Loads DH key for given nonve2.
	 */
	protected void loadKey() throws DownloadException {
		// Loads DHkeys for given nonce2.
		dState.dhOffline = DHOffline.getByNonce2(getContext().getContentResolver(), dState.nonce2);
		if (dState.dhOffline == null) {
			publishError(dState.msgId, FileTransferError.DHKEY_MISSING);
			throw new DownloadException("No stored dhkey for given nonce2=" + dState.nonce2);
		}
	}

	/**
	 * Prepares DHKeyHelper instance for this particular upload.
	 */
	protected void prepareDhHelper() throws CertificateException {
		dState.dhelper = new DHKeyHelper();
		dState.dhelper.setCtxt(getContext());
		dState.dhelper.setConnectionTimeoutMilli(30000);
		dState.dhelper.setReadTimeoutMilli(32000);
		dState.dhelper.setMySip(identity.getStoredCredentials().getUserSip());
		dState.dhelper.setMyCert(identity.getUserPrivateCredentials().getCert());
		dState.dhelper.setPrivKey(identity.getUserPrivateCredentials().getPk());
		dState.dhelper.setRand(new SecureRandom());
		dState.dhelper.setUserSip(dState.sender); // user B
		dState.dhelper.setSipCert(dState.senderCrt.getCertificateObj()); //user B certificate
		dState.dhelper.setCanceller(new DownloadFileListEntryCanceller(curEntry, this));
	}

	protected void computeHolder() throws GeneralSecurityException, CryptoHelper.SignatureException, IOException, CryptoHelper.MACVerificationException {
		prepareDhHelper();

		// Create holder for file dependent data
		// Encryption keys will be derived.
		dState.ftHolder = dState.dhelper.processFileTransfer(dState.dhOffline, dState.storedFile.getKey());
	}

	/**
	 * Stores current upload state to the database so it can be recovered on crash recovery event.
	 */
	protected void storeTransferRecord() throws IOException {
		FileTransfer tr = new FileTransfer();
		tr.setNonce2(dState.nonce2);
		tr.setMessageId(dState.msgId);
		tr.setIsOutgoing(false);

		// No crypto material is needed since we do not support.
		if (dState.ftHolder != null && dState.ftHolder.c != null) {
			tr.setNonce1(dState.ftHolder.nonce1);
			tr.setNonceb(Base64.encodeBytes(dState.ftHolder.nonceb));
			tr.setSalt1( Base64.encodeBytes(dState.ftHolder.salt1));
			tr.setSaltb( Base64.encodeBytes(dState.ftHolder.saltb));
			tr.setC(     Base64.encodeBytes(dState.ftHolder.c));
		}

		tr.setNumOfFiles(dState.transferFiles == null ? null : dState.transferFiles.size());
		if (dState.metaFile != null) {
			tr.setTitle(dState.metaFile.hasTitle()       ? dState.metaFile.getTitle() : null);
			tr.setDescr(dState.metaFile.hasDescription() ? dState.metaFile.getDescription() : null);
		}

		tr.setThumbDir(DHKeyHelper.getThumbDirectory(getContext()).getAbsolutePath());
		tr.setDeletedFromServer(false);
		tr.setDateCreated(new Date());
		tr.setDateFinished(null);
		tr.setStatusCode(0);

		tr.setMetaState(FileTransfer.FILEDOWN_TYPE_NONE);
		tr.setPackState(FileTransfer.FILEDOWN_TYPE_NONE);
		tr.setShouldDeleteFromServer(false);

		if (dState.deleteOnly){
			tr.setShouldDeleteFromServer(true);
		}

		final Uri insUri = getContext().getContentResolver().insert(FileTransfer.URI, tr.getDbContentValues());
		dState.transferRecord = tr;
		dState.transferRecordId = ContentUris.parseId(insUri);
		if (dState.transferRecordId == -1){
			Log.ef(TAG, "Could not store transfer record, %s", tr);
			return;
		}

		Log.vf(TAG, "FileTransfer record stored. id=%s, obj=%s", insUri, tr);
	}

	/**
	 * Recovers upload state from stored file transfer record.
	 * @throws IOException
	 */
	protected void recoverFromTransferRecord() throws IOException, GeneralSecurityException, DownloadException {
		FileTransfer tr = dState.transferRecord;
		FTHolder     ft = new FTHolder();

		dState.nonce2 = tr.getNonce2();
		ft.nonce2 = Base64.decode(dState.nonce2);
		ft.c      = tr.getC()      == null ? null : Base64.decode(tr.getC());
		ft.nonce1 = tr.getNonce1() == null ? null : tr.getNonce1();
		ft.nonceb = tr.getNonceb() == null ? null : Base64.decode(tr.getNonceb());
		ft.salt1  = tr.getSalt1()  == null ? null : Base64.decode(tr.getSalt1());
		ft.saltb  = tr.getSaltb()  == null ? null : Base64.decode(tr.getSaltb());
		dState.sender = SipUri.getCanonicalSipContact(dState.msg.getFrom(), false);

		loadCert();
		prepareDhHelper();
		dState.dhelper.computeCi(ft);

		// Compute all symmetric keys from master secret.
		dState.dhelper.computeCi(ft);
		dState.ftHolder = ft;
	}

	/**
	 * Updates database file transfer record from current state.
	 * @return
	 */
	protected boolean updateFtRecord() {
		if (dState.transferRecord == null || dState.transferRecordId == null) {
			Log.e(TAG, "Cannot update file transfer progress, nil encountered");
			return false;
		}

		return getContext().getContentResolver().update(FileTransfer.URI,
				dState.transferRecord.getDbContentValues(),
				FileTransfer.FIELD_ID+"=?",
				new String[] { dState.transferRecordId.toString() }) > 0;
	}

	/**
	 * Performs server side file deletion. Stores information to state.
	 * @param e
	 */
	protected void deleteOnly(DownloadFileListEntry e) {
		try {
			publishProgress(dState.msgId, FileTransferProgressEnum.DELETING_FROM_SERVER, -1);
			boolean delSuccess = deleteFileFromServer(dState.nonce2, dState.domain);
			if (delSuccess){
				publishProgress(dState.msgId, FileTransferProgressEnum.DELETED_FROM_SERVER, 100);
				dState.didErrorOccurr = false;

				if (dState.transferRecord == null){
					storeTransferRecord();
				}

				// If delete process was successful, mark deletion to the DB so manager knows it succeeds and
				// it does not have to repeat it.
				dState.transferRecord.setDeletedFromServer(true);
				updateFtRecord();
			} else {
				publishProgress(dState.msgId, FileTransferProgressEnum.ERROR, 100);
			}

			// Delete corresponding DH keys from the local database (server gets updated also
			// during next dhsync).
			final String user = SipUri.getCanonicalSipContact(dState.msg.getRemoteNumber(), false);
			DHOffline.delete(getContext().getContentResolver(), dState.nonce2, user);

		} catch (SocketTimeoutException tex){
			// Timeout exception.
			Log.i(TAG, "Socket timeout exception.");
			publishError(dState.msgId, FileTransferError.TIMEOUT);

		} catch (OperationCancelledException cex){
			// Cancelled.
			Log.i(TAG, "Operation was cancelled");
			publishProgress(dState.msgId, FileTransferProgressEnum.CANCELLED, 100);
			setFinalMessageOKType();

		} catch (Exception ex) {
			// Generic exception.
			Log.e(TAG, "Exception in a download process.", ex);

			publishError(dState.msgId, FileTransferError.GENERIC_ERROR);
		}

		// Store result of the download to the LRU cache.
		if (e.storeResult){
			mgr.setDownloadResult(dState.nonce2,
					new DownloadResult(
							dState.didErrorOccurr ? FileTransferError.GENERIC_ERROR : FileTransferError.NONE,
							System.currentTimeMillis(),
							dState.filePaths));
		}
	}

	/**
	 * Downloads a file with given index from the server.
	 *
	 * @param fileTypeIdx
	 * @param allowRedownload
	 * @throws IOException
	 * @throws DownloadException
	 * @throws CertificateException
	 * @throws NoSuchAlgorithmException
	 * @throws KeyStoreException
	 */
	protected void downloadFile(int fileTypeIdx, boolean allowRedownload) throws IOException, DownloadException, CertificateException, NoSuchAlgorithmException, KeyStoreException {
		// Key is loaded from the server, can continue with upload.
		dState.dhelper.setDebug(false);
		dState.dhelper.setTxprogress(new DownloadProgressMonitor(dState.msgId));

		// Was transfer cancelled?
		dState.throwIfCancel();

		publishProgress(dState.msgId, FileTransferProgressEnum.LOADING_PRIVATE_FILES, -1);
		final String pass = identity.getStoredCredentials().getUsrStoragePass();
		final KeyStore ks = SOAPHelper.getDefaultKeyStore(getContext(), pass.toCharArray());
		dState.throwIfCancel();

		//
		// Start download.
		publishProgress(dState.msgId, FileTransferProgressEnum.CONNECTING_TO_SERVER, -1);
		final int returnCode = dState.dhelper.downloadFile(dState.ftHolder, ks, pass, fileTypeIdx, allowRedownload);
		dState.dhelper.setTxprogress(null);

		// Cancellation check - download may failed on cancel.
		dState.throwIfCancel();
		if (returnCode == 206){
			Log.df(TAG, "Partial content returned");
		}

		// Handle 404 errors
		if (returnCode / 100 == 4){
			dState.recoverableFault = false;
			Log.df(TAG, "Return code after download: %s, 400 family", returnCode);
			dState.dhelper.cleanFiles(dState.ftHolder);

			publishError(dState.msgId, FileTransferError.DOWN_NO_SUCH_FILE_FOR_NONCE, returnCode, null);
			throw new DownloadException("Download failed");
		}

		// Download result check.
		if ((returnCode / 100) != 2){
			dState.recoverableFault = true;
			Log.df(TAG, "Return code after download: %s", returnCode);
			dState.dhelper.cleanFiles(dState.ftHolder);

			publishError(dState.msgId, FileTransferError.DOWN_DOWNLOAD_ERROR, returnCode, null);
			throw new DownloadException("Download failed");
		}
	}

	/**
	 * Processes meta information of a pack (meta + encrytped archive). Check MAC of archive.
	 * Sets dState.ftHolder.archiveOffset to the start of the encrypted archive.
	 *
	 * @param fileTypeIdx
	 * @throws NoSuchPaddingException
	 * @throws CryptoHelper.MACVerificationException
	 * @throws NoSuchAlgorithmException
	 * @throws IOException
	 * @throws InvalidAlgorithmParameterException
	 * @throws NoSuchProviderException
	 * @throws InvalidKeyException
	 * @throws DownloadException
	 */
	protected void partialDecryptFile(int fileTypeIdx) throws NoSuchPaddingException, CryptoHelper.MACVerificationException, NoSuchAlgorithmException, IOException, InvalidAlgorithmParameterException, NoSuchProviderException, InvalidKeyException, DownloadException {
		// Try to decrypt it.
		dState.dhelper.setTxprogress(new DecryptProgressMonitor(dState.msgId));
		boolean decryptionOk = false;

		// Check if exception happens, if yes, delete partially downloaded file. (?? already downloaded file)
		try {
			decryptionOk = dState.dhelper.partialDecryptFile(dState.ftHolder, fileTypeIdx);
		} catch(CryptoHelper.MACVerificationException gex){

			// Specific MAC verification, continued download may crippled file.
			Log.ef(TAG, gex, "MAC verification error");
			DHKeyHelper.cleanAllFiles(getContext(), dState.ftHolder, fileTypeIdx);

			throw gex;
		} finally {
			//dState.dhelper.setTxprogress(null);
		}

		// Generic decryption problem.
		if (!decryptionOk){
			Log.v(TAG, "Decryption was not successful");
			DHKeyHelper.cleanAllFiles(getContext(), dState.ftHolder, fileTypeIdx);

			publishError(dState.msgId, FileTransferError.DOWN_DECRYPTION_ERROR);
			throw new DownloadException("Decryption error");
		}
	}

	/**
	 * Decrypts and unzips archive into protected storage.
	 *
	 * @param fileTypeIdx
	 * @param options
	 * @return
	 * @throws NoSuchPaddingException
	 * @throws CryptoHelper.MACVerificationException
	 * @throws NoSuchAlgorithmException
	 * @throws IOException
	 * @throws InvalidAlgorithmParameterException
	 * @throws NoSuchProviderException
	 * @throws InvalidKeyException
	 * @throws DownloadException
	 */
	protected FTHolder.UnpackingResult decryptAndUnzipArchive(int fileTypeIdx, FTHolder.UnpackingOptions options) throws NoSuchPaddingException, CryptoHelper.MACVerificationException, NoSuchAlgorithmException, IOException, InvalidAlgorithmParameterException, NoSuchProviderException, InvalidKeyException, DownloadException {
		// Try to decrypt it.
		dState.dhelper.setTxprogress(new DecryptProgressMonitor(dState.msgId));
		//boolean decryptionOk = false;
    	try {
			partialDecryptFile(fileTypeIdx); // check the MAC
			return dState.dhelper.unzipEncryptedArchive(dState.ftHolder, fileTypeIdx, options);
		} catch(CryptoHelper.MACVerificationException gex){
			// other Exception is supposed to propagate

			// Specific MAC verification, continued download may crippled file.
			Log.ef(TAG, gex, "MAC verification error");
			DHKeyHelper.cleanAllFiles(getContext(), dState.ftHolder, fileTypeIdx);

			throw gex;
		} finally {
			dState.dhelper.setTxprogress(null);
		}
/*
		// Generic decryption problem.
		if (!decryptionOk){
			Log.v(TAG, "Decryption was not successful");
			DHKeyHelper.cleanAllFiles(getContext(), dState.ftHolder, fileTypeIdx);

			publishError(dState.msgId, FileTransferError.DOWN_DECRYPTION_ERROR);
			throw new DownloadException("Decryption error");
		}*/
	}

	/**
	 * Downloads, decrypts and processes meta file, loading meta file structure, thumbs.
	 * @throws IOException
	 * @throws KeyStoreException
	 * @throws DownloadException
	 * @throws NoSuchAlgorithmException
	 * @throws CertificateException
	 * @throws NoSuchProviderException
	 * @throws InvalidAlgorithmParameterException
	 * @throws CryptoHelper.MACVerificationException
	 * @throws InvalidKeyException
	 * @throws NoSuchPaddingException
	 */
	protected void fetchAndProcessMeta() throws IOException, KeyStoreException, DownloadException, NoSuchAlgorithmException, CertificateException, NoSuchProviderException, InvalidAlgorithmParameterException, CryptoHelper.MACVerificationException, InvalidKeyException, NoSuchPaddingException {
		// Download meta file from server. Should be fast, it is a quite small file.
		// One can also stop download after fetching meta object, to limit only to meta file
		// or limit meta file by size.
		Log.vf(TAG, "Going to download meta for %s", dState.nonce2);
		downloadFile(DHKeyHelper.META_IDX, false);

		Log.v(TAG, String.format("Downloaded file name=[%s] length=[%s]",
				dState.ftHolder.filePath[DHKeyHelper.META_IDX],
				dState.ftHolder.fileSize[DHKeyHelper.META_IDX]));
		dState.throwIfCancel();

		// Try to decrypt already downloaded file.
		Log.vf(TAG, "Going to decrypt meta for %s", dState.nonce2);
		partialDecryptFile(DHKeyHelper.META_IDX);
		Log.v(TAG, String.format("Decrypted file name=[%s] length=[%s]",
				dState.ftHolder.filePath[DHKeyHelper.META_IDX],
				dState.ftHolder.fileSize[DHKeyHelper.META_IDX]));

		dState.metaFile = dState.dhelper.reconstructEncryptedMetaFile(dState.ftHolder);
		Log.vf(TAG, "Meta file fetched: %s", dState.metaFile);

		processEncryptedMetaFile();
	}

	protected void processEncryptedMetaFile() throws IOException {
		final ContentResolver cr = getContext().getContentResolver();

		// Read meta file, extract information about files and construct its representation.
		int prefOrder = 0;
		List<net.phonex.pub.proto.FileTransfer.MetaFileDetail> filesList = dState.metaFile.getFilesList();
		for(net.phonex.pub.proto.FileTransfer.MetaFileDetail fDetail : filesList){
			FTHolder.DownloadFile downFile = FTHolder.DownloadFile.buildFromMeta(fDetail);

			if (MiscUtils.isEmpty(downFile.fileName)){
				Log.e(TAG, "Downloaded file has empty file name; Cannot process...");
				continue;
			}

			// If prefOrder is not defined, define it here.
			if (downFile.prefOrder == null){
				downFile.prefOrder = prefOrder;
			}

			dState.transferFiles.add(downFile);
			prefOrder += 1;
		}

		// Extract meta file thumb zip if there is such zip file.
		if (dState.metaFile != null && MiscUtils.fileExistsAndIsAfile(dState.ftHolder.filePath[DHKeyHelper.META_IDX])) {
			publishProgress(dState.msgId, FileTransferProgressEnum.FILE_EXTRACTION, -1);
			unpackEncryptedMetaThumbs();
		} else {
			if (dState.metaFile == null) {
				Log.vf(TAG, "No meta file thumb zip");
			} else if (!MiscUtils.fileExistsAndIsAfile(dState.ftHolder.filePath[DHKeyHelper.META_IDX])) {
				Log.vf(TAG, "No meta file thumb zip - %s does not exist or is not a file", dState.ftHolder.filePath[DHKeyHelper.META_IDX]);
			}
		}

		// Store results for meta files, with potential thumbs.
		long totalFileSize = 0;
		Log.vf(TAG, "Meta indicates %s files, metaFile: %s", MiscUtils.collectionSize(dState.transferFiles), dState.metaFile);
		for(FTHolder.DownloadFile fldwn : dState.transferFiles){
			try {
				ReceivedFile recvFile = new ReceivedFile();
				recvFile.setFileNonce(dState.nonce2);
				recvFile.setMsgId(dState.msgId);
				recvFile.setTransferId(dState.transferRecordId);
				recvFile.setDateReceived(new Date());
				recvFile.setRecordType(ReceivedFile.PEX_RECV_FILE_META);
				recvFile.setThumbnailName(fldwn.thumbFname);
				recvFile.setFilename(fldwn.fileName);
				recvFile.setSize(fldwn.fileSize);
				recvFile.setTitle(fldwn.title);
				recvFile.setDesc(fldwn.desc);
				recvFile.setFileHash(fldwn.xhash == null ? null : Base64.encodeBytes(fldwn.xhash));
				recvFile.setMimeType(fldwn.mimeType);
				recvFile.setFileOrder(fldwn.prefOrder);
				recvFile.setStorageUri(fldwn.thumbFileStorageUriString);

				totalFileSize += fldwn.fileSize;

				final Uri insUri = cr.insert(ReceivedFile.URI, recvFile.getDbContentValues());
				if (insUri != null){
					fldwn.receivedFileId = ContentUris.parseId(insUri);
				} else {
					Log.ef(TAG, "Problem with saving received file to DB, %s", recvFile);
				}

			} catch (Exception e) {
				Log.e(TAG, "Cannot store downloaded files, exception.", e);
			}
		}

		// Decide whether to continue with file download - depending on the file size and connection type.
		if(dState.params.isDownloadFullIfOnWifiAndUnderThreshold() && mgr.isConnected()){
			NetworkInfo netInfo = mgr.getCurrentNetworkInfo();
			if ("wifi".equalsIgnoreCase(netInfo.getTypeName()) && totalFileSize < DownloadOnWifiThreshold){
				Log.vf(TAG, "Reachable via WIFI and total file size is less than threshold. Size=%s", totalFileSize);
				dState.downloadPackRightNow = true;
			}
		}
	}

	protected void unpackEncryptedMetaThumbs() throws IOException {
		UnpackingOptions unpackOptions = new UnpackingOptions();

		// Overwrite existing thumbs (in the DB)
		unpackOptions.actionOnConflict          = FTHolder.FilenameConflictCopyAction.OVERWRITE;
		unpackOptions.createDirIfMissing        = true;
		unpackOptions.deleteArchiveOnSuccess    = false;
		unpackOptions.deleteMetaOnSuccess       = true;
		unpackOptions.deleteNewFilesOnException = true;
		unpackOptions.fnamePrefix               = DHKeyHelper.getFilenameFromBase64(dState.nonce2) + "_";
		unpackOptions.destinationDirectory      = DHKeyHelper.getThumbDirectory(getContext()).getAbsolutePath();

		try {
			dState.dhelper.unzipEncryptedMetaArchiveAtFile(dState.ftHolder, dState.transferFiles, unpackOptions);
		} catch(Exception e){
			Log.e(TAG, "Exception during thumbs unpacking, exception", e);
		}
	}

	protected void fetchAndProcessPack() throws KeyStoreException, DownloadException, NoSuchAlgorithmException, CertificateException, IOException, NoSuchProviderException, InvalidAlgorithmParameterException, CryptoHelper.MACVerificationException, InvalidKeyException, NoSuchPaddingException {
		// Download pack itself.
		Log.vf(TAG, "Going to download pack for %s", dState.nonce2);
		publishProgress(dState.msgId, FileTransferProgressEnum.CONNECTING_TO_SERVER, -1);
		downloadFile(DHKeyHelper.ARCH_IDX, true); // TODO: add support for re-downloading...

		Log.v(TAG, String.format("Downloaded file name=[%s] length=[%s]",
				dState.ftHolder.filePath[DHKeyHelper.ARCH_IDX],
				dState.ftHolder.fileSize[DHKeyHelper.ARCH_IDX]));
		dState.throwIfCancel();

		// Extract downloaded files. Obeys policy specified by a parameter.
		publishProgress(dState.msgId, FileTransferProgressEnum.FILE_EXTRACTION, -1);
		UnpackingOptions unpackOptions = new UnpackingOptions();
		unpackOptions.actionOnConflict = dState.params.getConflictAction();
		unpackOptions.createDirIfMissing = dState.params.isCreateDestinationDirIfNeeded();
		unpackOptions.deleteArchiveOnSuccess = true;
		unpackOptions.deleteMetaOnSuccess = true;
		unpackOptions.deleteNewFilesOnException = true;
		unpackOptions.destinationDirectory = dState.params.getDestinationDirectory().getAbsolutePath();
		dState.unpackResult = decryptAndUnzipArchive(DHKeyHelper.ARCH_IDX, unpackOptions);

		// Store extracted file paths to the result structures.
		int prefOrder = 0;
		for (FTHolder.UnpackingFile fl : dState.unpackResult.getFiles()) {
			Log.vf(TAG, "Extracted file: %s for uri %s", fl.destination, fl.uri);
			dState.filePaths.add(fl.destination);

			// Look for corresponding file.
			Long recvFileId = null;
			for(FTHolder.DownloadFile fldwn : dState.transferFiles) {
				if (fl.originalFname == null || !fl.originalFname.equals(fldwn.fileName)){
					continue;
				}

				if (fldwn.receivedFileId == null){
					continue;
				}

				recvFileId = fldwn.receivedFileId;
				break;
			}

			// Try to fetch this file
			ReceivedFile recvFile = recvFileId == null ? null : ReceivedFile.getById(getContext().getContentResolver(), recvFileId);

			// If is nil, try to fetch via message id and original file name.
			if (recvFile == null){
				recvFile = ReceivedFile.getByMsgIdAndFileName(getContext().getContentResolver(), dState.msgId, fl.originalFname);
			}

			// If exists, update existing record created during meta upload. If not, something went wrong with meta (skipped?).
			if (recvFile != null){
				recvFile.setPath(fl.destination);
				if (fl.size == 0) {
					recvFile.setSize(MiscUtils.getFileLength(fl.destination));
				} else {
					recvFile.setSize(fl.size);
				}
				recvFile.setRecordType(ReceivedFile.PEX_RECV_FILE_FULL);
				if (recvFile.getStorageUri() != null) {
					int deleted = Thumbnail.deleteByUri(getContext().getContentResolver(), recvFile.getStorageUri());
					Log.vf(TAG, "ReceivedFile %s with thumbnail, deleted %d thumbnails", recvFile.getFilename(), deleted);
				} else {
					Log.vf(TAG, "ReceivedFile %s with no thumbnail", recvFile.getFilename());
				}
				// this will set correct storage uri
				recvFile.setFromStorageUri(fl.uri);

				getContext().getContentResolver().update(ReceivedFile.URI, recvFile.getDbContentValues(),
						ReceivedFile.FIELD_ID+"=?", new String[]{recvFile.getId().toString()});

			} else {
				// Store received file to the database.
				try {
					recvFile = new ReceivedFile();
					recvFile.setMsgId(dState.msgId);
					recvFile.setTransferId(dState.transferRecordId);
					recvFile.setDateReceived(new Date());
					recvFile.setFilename(fl.originalFname);
					recvFile.setFileNonce(dState.nonce2);
					recvFile.setPath(fl.destination);
					recvFile.setRecordType(ReceivedFile.PEX_RECV_FILE_FULL);
					if (fl.size == 0) {
						recvFile.setSize(MiscUtils.getFileLength(fl.destination));
					} else {
						recvFile.setSize(fl.size);
					}
					recvFile.setFileOrder(prefOrder);
					recvFile.setFromStorageUri(fl.uri);

					getContext().getContentResolver().insert(ReceivedFile.URI, recvFile.getDbContentValues());
				} catch (Exception e) {
					Log.e(TAG, "Cannot store downloaded files, exception", e);
				}
			}

			prefOrder += 1;
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
	 * @param msgid
	 * @param error
	 * @param errCode
	 * @param errString
	 */
	protected void publishError(long msgid, FileTransferError error, Integer errCode, String errString){
		mgr.publishError(msgid, error, errCode, errString, false);
	}

	/**
	 * Publishes progress by specifying progress details.
	 * @param msgid
	 * @param progressCode
	 * @param progress
	 */
	protected void publishProgress(long msgid, FileTransferProgressEnum progressCode, int progress){
		publishProgress(new FileTransferProgress(msgid, progressCode, progress));
	}
	
	/**
	 * Publishes DONE progress event.
	 * @param msgid
	 */
	protected void publishDone(long msgid){
		mgr.publishDone(msgid, false);
	}
	
	/**
	 * Method for publishing a download progress.
	 * If the progress is changed, it is broadcasted by intent. 
	 * @param progress
	 */
	protected void publishProgress(FileTransferProgress progress){
		progress.setUpload(false);
		mgr.publishProgress(progress);
	}

	/**
	 * Cancels ongoing task.
	 * @param manualCancel the manualCancel to set
	 */
	public void setManualCancel(boolean manualCancel) {
		this.manualCancel = manualCancel;
		//final XService s = svc.get();
		//if (s==null){
		//	return;
		//}
		//
		//if (manualCancel && showNotifications){
		//	s.getNotificationManager().cancelCertUpd();
		//}
	}

	/**
	 * @return the showNotifications
	 */
	public boolean isShowNotifications() {
		return showNotifications;
	}
	

	/**
	 * @param showNotifications the showNotifications to set
	 */
	public void setShowNotifications(boolean showNotifications) {
		this.showNotifications = showNotifications;
	}
	
	
	public boolean isWriteErrorToMessage() {
		return writeErrorToMessage;
	}

	public void setWriteErrorToMessage(boolean writeErrorToMessage) {
		this.writeErrorToMessage = writeErrorToMessage;
	}

	/**
	 * Returns true if the local canceller signalizes a canceled state.
	 * @return true if task was cancelled.
	 */
	private boolean wasCancelled(){
		return manualCancel || (canceller != null && canceller.isCancelled());
	}

	public boolean isDeleteFromServer() {
		return deleteFromServer;
	}

	public void setDeleteFromServer(boolean deleteFromServer) {
		this.deleteFromServer = deleteFromServer;
	}

	public CancellationSignal getCancelSignal() {
		return cancelSignal;
	}

	public void setCancelSignal(CancellationSignal cancelSignal) {
		this.cancelSignal = cancelSignal;
	}

	public Canceller getCanceller() {
		return canceller;
	}

	public void setCanceller(Canceller canceller) {
		this.canceller = canceller;
	}

	public FileTransferManager getMgr() {
		return mgr;
	}

	public void setMgr(FileTransferManager mgr) {
		this.mgr = mgr;
	}

	/**
	 * Canceller used for download process cancellation.
	 */
	private static class DownloadFileListEntryCanceller implements Canceller {
		private final WeakReference<DownloadFileListEntry> par;
		private final WeakReference<DownloadFileTask> task;

		public DownloadFileListEntryCanceller(final DownloadFileListEntry par, final DownloadFileTask task) {
			this.par = new WeakReference<DownloadFileListEntry>(par);
			this.task = new WeakReference<DownloadFileTask>(task);
		}

		@Override
		public boolean isCancelled() {
			final DownloadFileListEntry downloadFileListEntry = par.get();
			if (downloadFileListEntry != null && downloadFileListEntry.cancelled){
				return true;
			}

			final DownloadFileTask downloadFileTask = task.get();
			return (downloadFileTask != null && downloadFileTask.wasCancelled()) ? true : false;
		}
	}

	/**
	 * Progress monitor for upload process.
	 */
	private class DownloadProgressMonitor extends TransmitProgress {
		protected final long sipMessageId;

		public DownloadProgressMonitor(final long sipMessageId){
			this.sipMessageId = sipMessageId;
		}

		@Override
		public void updateTxProgress(Double partial, double total) {
			final int pcnts = Math.min((int) Math.ceil(total * 100.0), 100);
			final FileTransferProgress progress = new FileTransferProgress(sipMessageId, FileTransferProgressEnum.DOWNLOADING, pcnts);
			progress.setTitle(String.format("%s: %d %%", getContext().getString(R.string.dwn_p_downloading), pcnts));
			publishProgress(progress);
		}
	}

	/**
	 * Progress monitor for upload process.
	 */
	private class DecryptProgressMonitor extends TransmitProgress {
		protected final long sipMessageId;

		public DecryptProgressMonitor(final long sipMessageId){
			this.sipMessageId = sipMessageId;
		}

		@Override
		public void updateTxProgress(Double partial, double total) {
			final int pcnts = Math.min((int) Math.ceil(total * 100.0), 100);
			final FileTransferProgress progress = new FileTransferProgress(sipMessageId, FileTransferProgressEnum.DECRYPTING_FILES, pcnts);
			progress.setTitle(String.format("%s: %d %%", getContext().getString(R.string.dwn_p_decryption), pcnts));
			publishProgress(progress);
		}
	}

	/**
	 * Broadcast receiver for intents.
	 */
	protected static class Receiver extends BroadcastReceiver {
		final private WeakReference<DownloadFileTask> task;

		public Receiver(DownloadFileTask wMgr) {
			this.task = new WeakReference<DownloadFileTask>(wMgr);
		}

		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (MiscUtils.isEmpty(action)){
				return;
			}

			DownloadFileTask tsk = task.get();
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


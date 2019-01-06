package net.phonex.core;
import net.phonex.db.entity.SipProfileState;
import net.phonex.db.entity.SipCallSession;
import net.phonex.db.entity.SipCallSessionInfo;
import net.phonex.core.MediaState;
import net.phonex.pub.parcels.CertUpdateParams;
import net.phonex.pub.parcels.CertUpdateProgress;
import net.phonex.pub.parcels.KeyGenProgress;
import net.phonex.pub.parcels.FileTransferProgress;
import net.phonex.pub.parcels.MakeCallResult;
import net.phonex.pub.parcels.SipMsgAux;

interface IService{
    /**
     * Returns true if account id
     */
    boolean isAccountSipValid(in String callee, int accountId);

    /**
     * Cancels all android notifications.
     */
    void cancelAllNotifications();

	/**
	 * Get registration state for a given account id
	 * @param accountId the account/profile id for which we'd like to get the info (in database)
	 * @return the Profile state
	 */
	SipProfileState getSipProfileState(int accountId);
	
	//Call control
	/**
	 * Place a call.
	 * 
	 * @param callee The sip uri to call. 
	 * It can also be a simple number, in which case the app will autocomplete.
	 * If you add the scheme, take care to fill completely else it could be considered as a call
	 * to a sip IP/domain
	 * @param accountId The id of the account to use for this call. 
	 */
	MakeCallResult makeCall(in String callee, int accountId);

	/**
	 * Place a call.
	 * 
	 * @param callee The sip uri to call. 
	 * It can also be a simple number, in which case the app will autocomplete.
	 * If you add the scheme, take care to fill completely else it could be considered as a call
	 * to a sip IP/domain
	 * @param accountId The id of the account to use for this call. 
	 * @param options The options you'd like to apply for this calls {@link SipCallSession#OPT_CALL_VIDEO}, {@link SipCallSession#OPT_CALL_EXTRA_HEADERS}
	 */
	MakeCallResult makeCallWithOptions(in String callee, int accountId, in Bundle options);
	
	/**
	 * Answer a call.
	 * 
	 * @param callId The id of the call to answer.
	 * @param status The sip status code you'd like to answer with. 200 to take the call.  400 <= status < 500 if refusing.
	 */
	int answer(int callId, int status);
	/**
	 * Hangup a call.
	 *
	 * @param callId The id of the call to hangup.
	 * @param status The sip status code you'd like to hangup with.
	 */
	int hangup(int callId, int status);

    /**
     * Terminate a call.
     *
     * @param callId The id of the call to hangup.
     * @param status The sip status code you'd like to hangup with.
     */
    int terminateCall(int callId, int status);

	int hold(int callId);
	int reinvite(int callId, boolean unhold);
	int xfer(int callId, in String callee);
	int xferReplace(int callId, int otherCallId, int options);
	SipCallSession getCallInfo(int callId);
	SipCallSession[] getCalls();
	String showCallInfosDialog(int callId);
	
	//Media control
	void setMicrophoneMute(boolean on);
	void setSpeakerphoneOn(boolean on);
	void setBluetoothOn(boolean on);
	void confAdjustTxLevel(int port, float value);
	void confAdjustRxLevel(int port, float value);
	/**
	 * Get Rx and Tx sound level for a given port.
	 *
	 * @param port Port id we'd like to have the level
	 * @return The RX and TX (0-255) level encoded as RX << 8 | TX
	 */
	long confGetRxTxLevel(int port);
	void setEchoCancellation(boolean on);
	void adjustVolume(in SipCallSessionInfo callInfo, int direction, int flags);
	MediaState getCurrentMediaState();
	int startLoopbackTest();
	int stopLoopbackTest();

	// Messaging
	void sendMessage(String msg, String toNumber, long accountId);
	void sendMessageMime(String msg, String msg2store, String toNumber, long accountId, String mime);
	void resendMessageMime(String msg, String msg2store, String toNumber, long accountId, String mime, long messageId);
	void sendNotificationMessage(String msg, String msg2store, String toNumber, long accountId, String mime, long messageId);
	void sendMessageImpl(String message, String msg2store, String callee, long accountId, String mime,
		long messageId, boolean isResend, in SipMsgAux msgAux);

	//Secure
	void zrtpSASVerified(int callId);
	
	// Video
	void updateCallOptions(int callId, in Bundle options);

	/**
	 * Revoke a ZRTP SAS
	 */ 
	void zrtpSASRevoke(int callId);

	/**
	 * Get nat type detected by the sip stack
	 * @return String representing nat type detected by the stack. Empty string if nothing detected yet.
	 */
	 String getLocalNatType();

	// Storage password
	void setStoragePassword(in String pass);
	String getStoragePassword();
	
	/**
	 * Triggers DH key update task.
	 */
	void triggerDHKeySync(long timeout);
	
    /**
     * Cert update for one particular user.
     */
    void triggerCertUpdate(in List<CertUpdateParams> params);
    void triggerCertUpdateEx(in List<CertUpdateParams> params, boolean allUsers);

    /**
     * Returns current state of the certificate update.
     */
    List<CertUpdateProgress> getCertUpdateProgress();

    /**
     * Returns current state of the DH key generation.
     */
    List<KeyGenProgress> getDHKeyProgress();

    /**
     * upload file to destinationSip user
     * fileUris formatted according to FileStorageUri
     */
    //void sendFiles(long sipMessageId, String destinationSip, in List<String> fileAbsolutePaths);

    void sendFiles(String destinationSip, in List<String> fileUris, String message);

    /**
     * upload file to destinationSip user
     */
    void downloadFile(long sipMessageId, String destinationDirectory);

    /**
     * Delete sent file from the server.
     */
    void deleteFileFromServer(long sipMessageId);

    /**
     * Marks particular file transfer as canceled
     */
    void cancelDownload(long sipMessageId);

    /**
     * Marks particular file transfer as canceled
     */
    void cancelUpload(long sipMessageId);

    /**
     * Returns current state of the certificate update.
     */
    FileTransferProgress getFileTransferProgress(long sipMessageId);

    /**
     * Decrypt a file in secure storage.
     */
    void decryptFile(String uri);

    /**
     * Decrypt list of files in secure storage.
     */
    void decryptFiles(in List<String> uris);

    /**
     * Cancel decryption from secure storage of the entire queue.
     */
    void cancelDecrypt();

    /**
     * Check if there are any decrypt tasks queued or running.
     */
    boolean isTaskRunningOrPending();

    /**
     * Trigger saving of login for after update auto login.
     */
    void triggerLoginStateSave();

    void quickLogin(String sip, String password, String domain);
}

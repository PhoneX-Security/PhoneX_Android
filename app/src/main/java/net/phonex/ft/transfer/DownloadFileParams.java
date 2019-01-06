package net.phonex.ft.transfer;

import android.os.Parcel;
import android.os.Parcelable;

import net.phonex.ft.FTHolder.FilenameConflictCopyAction;
import net.phonex.ft.FTHolder;

import java.io.File;
import java.io.Serializable;

/**
 * Class for passing parameters to this task.
 * @author ph4r05
 */
public class DownloadFileParams implements TransferParameters {
	/**
	 * Nonce2 corresponding to this file download.
	 */
	private String nonce2;
	
	/**
	 * Notification message ID associated with this file.
	 */
	private Long msgId;
	private Long queueMsgId;

	/**
	 * Destination directory where to extract new files.
	 */
	private File destinationDirectory;
	
	/**
	 * Whether to create destination directory if does not exist.
	 */
	private boolean createDestinationDirIfNeeded = true;
	
	/**
	 * Specifies policy for newly created files if filename conflict occurs.
	 */
	private FilenameConflictCopyAction conflictAction = FTHolder.FilenameConflictCopyAction.RENAME_NEW;

	/**
	 * destination SIP.
	 */
	private String destinationSip;

	/**
	 * Signalizes delete request / file rejection.
	 */
	private boolean deleteOnly = false;

	/**
	 * Delete all artifacts when operation succeeds.
	 */
	private boolean deleteOnSuccess;

	/**
	 * If YES the download operation downloads the whole file right after
	 * downloading meta file.
	 */
	private boolean downloadFullArchiveNow = false;

	/**
	 * Download full archive if we are on WIFI connection and the file has size under defined
	 * threshold so it is automatically prepared for the user.
	 */
	private boolean downloadFullIfOnWifiAndUnderThreshold = true;

	private int fileTypeIdx;

	public String getDestinationSip() {
		return destinationSip;
	}

	public void setDestinationSip(String destinationSip) {
		this.destinationSip = destinationSip;
	}

	public String getNonce2() {
		return nonce2;
	}

	public void setNonce2(String nonce2) {
		this.nonce2 = nonce2;
	}

	public File getDestinationDirectory() {
		return destinationDirectory;
	}

	public void setDestinationDirectory(File destinationDirectory) {
		this.destinationDirectory = destinationDirectory;
	}

	public boolean isCreateDestinationDirIfNeeded() {
		return createDestinationDirIfNeeded;
	}

	public void setCreateDestinationDirIfNeeded(boolean createDestinationDirIfNeeded) {
		this.createDestinationDirIfNeeded = createDestinationDirIfNeeded;
	}

	public FilenameConflictCopyAction getConflictAction() {
		return conflictAction;
	}

	public void setConflictAction(
			FilenameConflictCopyAction conflictAction) {
		this.conflictAction = conflictAction;
	}

	public Long getMsgId() {
		return msgId;
	}

	public void setMsgId(Long msgId) {
		this.msgId = msgId;
	}

	public Long getQueueMsgId() {
		return queueMsgId;
	}

	public void setQueueMsgId(Long queueMsgId) {
		this.queueMsgId = queueMsgId;
	}

	public boolean isDeleteOnly() {
		return deleteOnly;
	}

	public void setDeleteOnly(boolean deleteOnly) {
		this.deleteOnly = deleteOnly;
	}

	public boolean isDeleteOnSuccess() {
		return deleteOnSuccess;
	}

	public void setDeleteOnSuccess(boolean deleteOnSuccess) {
		this.deleteOnSuccess = deleteOnSuccess;
	}

	public boolean isDownloadFullArchiveNow() {
		return downloadFullArchiveNow;
	}

	public void setDownloadFullArchiveNow(boolean downloadFullArchiveNow) {
		this.downloadFullArchiveNow = downloadFullArchiveNow;
	}

	public boolean isDownloadFullIfOnWifiAndUnderThreshold() {
		return downloadFullIfOnWifiAndUnderThreshold;
	}

	public void setDownloadFullIfOnWifiAndUnderThreshold(boolean downloadFullIfOnWifiAndUnderThreshold) {
		this.downloadFullIfOnWifiAndUnderThreshold = downloadFullIfOnWifiAndUnderThreshold;
	}

	public int getFileTypeIdx() {
		return fileTypeIdx;
	}

	public void setFileTypeIdx(int fileTypeIdx) {
		this.fileTypeIdx = fileTypeIdx;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(this.nonce2);
		dest.writeValue(this.msgId);
		dest.writeValue(this.queueMsgId);
		dest.writeSerializable(this.destinationDirectory);
		dest.writeByte(createDestinationDirIfNeeded ? (byte) 1 : (byte) 0);
		dest.writeInt(this.conflictAction == null ? -1 : this.conflictAction.ordinal());
		dest.writeString(this.destinationSip);
		dest.writeByte(deleteOnly ? (byte) 1 : (byte) 0);
		dest.writeByte(deleteOnSuccess ? (byte) 1 : (byte) 0);
		dest.writeByte(downloadFullArchiveNow ? (byte) 1 : (byte) 0);
		dest.writeByte(downloadFullIfOnWifiAndUnderThreshold ? (byte) 1 : (byte) 0);
		dest.writeInt(this.fileTypeIdx);
	}

	public DownloadFileParams() {
	}

	private DownloadFileParams(Parcel in) {
		this.nonce2 = in.readString();
		this.msgId = (Long) in.readValue(Long.class.getClassLoader());
		this.queueMsgId = (Long) in.readValue(Long.class.getClassLoader());
		this.destinationDirectory = (File) in.readSerializable();
		this.createDestinationDirIfNeeded = in.readByte() != 0;
		int tmpConflictAction = in.readInt();
		this.conflictAction = tmpConflictAction == -1 ? null : FilenameConflictCopyAction.values()[tmpConflictAction];
		this.destinationSip = in.readString();
		this.deleteOnly = in.readByte() != 0;
		this.deleteOnSuccess = in.readByte() != 0;
		this.downloadFullArchiveNow = in.readByte() != 0;
		this.downloadFullIfOnWifiAndUnderThreshold = in.readByte() != 0;
		this.fileTypeIdx = in.readInt();
	}

	public static final Parcelable.Creator<DownloadFileParams> CREATOR = new Parcelable.Creator<DownloadFileParams>() {
		public DownloadFileParams createFromParcel(Parcel source) {
			return new DownloadFileParams(source);
		}

		public DownloadFileParams[] newArray(int size) {
			return new DownloadFileParams[size];
		}
	};
}
package net.phonex.pub.parcels;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import net.phonex.R;


/**
 * @generated-with: http://parcelabler.com/
 */
public class FileTransferProgress implements Parcelable {

	/**
	 * ID of message display in MessageFragment that should be updated
	 * each file being uploaded is associated with one such message
	 */
	private long messageId;

	/**
	 * When was this entry updated for the last time.
	 */
	private long when = 0;

	/**
	 * progress message
	 */
	private String title;

	/**
	 * Current progress code.
	 */
	private FileTransferProgressEnum progressCode;

	/**
	 * numeric progress,
	 * from 0-100 determinate progress bar is shown
	 * otherwise indeterminate progress bar
	 */
	private int progress;

	/**
	 * Specifies if the progress is finished for this entry.
	 */
	private boolean done=false;

	/**
	 * Error condition occurred in the progress.
	 * If error is set, it is terminal state, no further state change
	 * should be signaled any further.
	 */
	private FileTransferError error = FileTransferError.NONE;

	/**
	 * Additional information about occurred error.
	 * Can be used to be more specific in error report - may be used
	 * to resolve particular issue.
	 */
	private Integer errorCode = null;

	/**
	 * Additional error string, describing particular error condition.
	 * In most cases is null/empty. May be used to resolve particular issue.
	 */
	private String errorString = null;

	/**
	 * True if is upload, else is download.
	 */
	private boolean upload;
	
	/**
	 * Constructor initializes the object. 
	 * When is set to the current time in milliseconds.
	 * 
	 * @param messageId
	 * @param title
	 * @param progress
	 */
	public FileTransferProgress(long messageId, String title, int progress) {
		this.messageId = messageId;
		this.title = title;
		this.progress = progress;
		this.when = System.currentTimeMillis(); 
	}

	/**
	 * Constructor initializes the object.
	 * When is set to the current time in milliseconds.
	 *
	 * @param messageId
	 * @param progressCode
	 * @param progress
	 */
	public FileTransferProgress(long messageId, FileTransferProgressEnum progressCode, int progress) {
		this.messageId = messageId;
		this.progressCode = progressCode;
		this.progress = progress;
		this.when = System.currentTimeMillis();
	}

	/**
	 * Constructor initializes the object.
	 * When is set to the current time in milliseconds.
	 *
	 * @param messageId
	 * @param progressCode
	 * @param progress
	 * @param upload
	 */
	public FileTransferProgress(long messageId, FileTransferProgressEnum progressCode, int progress, boolean upload) {
		this.messageId = messageId;
		this.progressCode = progressCode;
		this.progress = progress;
		this.upload = upload;
		this.when = System.currentTimeMillis();
	}

	public static String getTextFromCode(Context ctxt, FileTransferProgressEnum code){
		switch(code){
			case CANCELLED:
				return ctxt.getString(R.string.upd_p_cancelled);
			case ERROR:
				return ctxt.getString(R.string.dwn_p_error);
			case DONE:
				return ctxt.getString(R.string.upd_p_done);
			case IN_QUEUE:
				return ctxt.getString(R.string.upd_p_in_queue);
			case INITIALIZATION:
				return ctxt.getString(R.string.upd_p_initialization);
			case COMPUTING_ENC_KEYS:
				return ctxt.getString(R.string.upd_p_gen_enc_keys);
			case RETRIEVING_FILE:
				return ctxt.getString(R.string.dwn_p_retrieving_details);
			case DELETING_FROM_SERVER:
				return ctxt.getString(R.string.dwn_p_deleting);
			case DELETED_FROM_SERVER:
				return ctxt.getString(R.string.dwn_p_deleted);
			case LOADING_INFORMATION:
				return ctxt.getString(R.string.dwn_p_loading_info);
			case DOWNLOADING:
				return ctxt.getString(R.string.dwn_p_downloading);
			case LOADING_PRIVATE_FILES:
				return ctxt.getString(R.string.dwn_p_loading_info);
			case CONNECTING_TO_SERVER:
				return ctxt.getString(R.string.dwn_p_connecting);
			case FILE_EXTRACTION:
				return ctxt.getString(R.string.dwn_p_extracting);
			case DECRYPTING_FILES:
				return ctxt.getString(R.string.dwn_p_decryption);
			case DOWNLOADING_KEYS:
				return ctxt.getString(R.string.dwn_p_key_load);
			case KEY_VERIFICATION:
				return ctxt.getString(R.string.upd_p_key_verification);
			case UPLOADING:
				return ctxt.getString(R.string.upd_p_uploading);
			case ENCRYPTING_FILES:
				return ctxt.getString(R.string.upd_p_enc_notif);
			case SENDING_NOTIFICATION:
				return ctxt.getString(R.string.upd_p_sending_notif);
			default: return null;
		}
	}
	
	//
	// Getters, setters, constructors, equals, hashCode.
	//
	public long getMessageId() {
		return messageId;
	}
	public void setMessageId(long messageId) {
		this.messageId = messageId;
	}
	public long getWhen() {
		return when;
	}
	public void setWhen(long when) {
		this.when = when;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public int getProgress() {
		return progress;
	}
	public void setProgress(int progress) {
		this.progress = progress;
	}
	public FileTransferError getError() {
		return error;
	}
	public void setError(FileTransferError error) {
		this.error = error;
	}
	public Integer getErrorCode() {
		return errorCode;
	}
	public void setErrorCode(Integer errorCode) {
		this.errorCode = errorCode;
	}
	public String getErrorString() {
		return errorString;
	}
	public void setErrorString(String errorString) {
		this.errorString = errorString;
	}
	public boolean isDone() {
		return done;
	}
	public void setDone(boolean done) {
		this.done = done;
	}
	public FileTransferProgressEnum getProgressCode() {
		return progressCode;
	}
	public void setProgressCode(FileTransferProgressEnum progressCode) {
		this.progressCode = progressCode;
	}
	public boolean isUpload() {
		return upload;
	}
	public void setUpload(boolean upload) {
		this.upload = upload;
	}

	/**
	 * Returns whether given error is temporary/may be fixed
	 * by trying again, from error code.
	 * 
	 * @param error
	 * @return
	 */
	public static boolean errorTryAgain(FileTransferError error){
		return 
				error==FileTransferError.NONE ||
				error==FileTransferError.CERTIFICATE_MISSING ||
				error==FileTransferError.GENERIC_ERROR ||
				error==FileTransferError.UPD_QUOTA_EXCEEDED ||
				error==FileTransferError.UPD_NO_AVAILABLE_DHKEYS ||
				error==FileTransferError.UPD_UPLOAD_ERROR ||
				error==FileTransferError.DOWN_DOWNLOAD_ERROR ||
				error==FileTransferError.TIMEOUT;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((error == null) ? 0 : error.hashCode());
		result = prime * result
				+ ((errorCode == null) ? 0 : errorCode.hashCode());
		result = prime * result
				+ ((errorString == null) ? 0 : errorString.hashCode());
		result = prime * result + (int) (messageId ^ (messageId >>> 32));
		result = prime * result + progress;
		result = prime * result + ((title == null) ? 0 : title.hashCode());
		result = prime * result + (int) (when ^ (when >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		FileTransferProgress other = (FileTransferProgress) obj;
		if (error != other.error)
			return false;
		if (errorCode == null) {
			if (other.errorCode != null)
				return false;
		} else if (!errorCode.equals(other.errorCode))
			return false;
		if (errorString == null) {
			if (other.errorString != null)
				return false;
		} else if (!errorString.equals(other.errorString))
			return false;
		if (messageId != other.messageId)
			return false;
		if (progress != other.progress)
			return false;
		if (title == null) {
			if (other.title != null)
				return false;
		} else if (!title.equals(other.title))
			return false;
		if (when != other.when)
			return false;
		return true;
	}
	
	public boolean porgressEquals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		FileTransferProgress other = (FileTransferProgress) obj;
		if (error != other.error)
			return false;
		if (errorCode == null) {
			if (other.errorCode != null)
				return false;
		} else if (!errorCode.equals(other.errorCode))
			return false;
		if (errorString == null) {
			if (other.errorString != null)
				return false;
		} else if (!errorString.equals(other.errorString))
			return false;
		if (messageId != other.messageId)
			return false;
		if (progress != other.progress)
			return false;
		if (title == null) {
			if (other.title != null)
				return false;
		} else if (!title.equals(other.title))
			return false;
		return true;
	}
	
	//
	// Parcelable interface implementation.
	//
	protected FileTransferProgress(Parcel in) {
        messageId = in.readLong();
        when = in.readLong();
        title = in.readString();
        progress = in.readInt();
        done = in.readByte() != 0x00;
        error = (FileTransferError) in.readValue(FileTransferError.class.getClassLoader());
        errorCode = in.readByte() == 0x00 ? null : in.readInt();
        errorString = in.readString();
		upload = in.readByte() != 0x00;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(messageId);
        dest.writeLong(when);
        dest.writeString(title);
        dest.writeInt(progress);
        dest.writeByte((byte) (done ? 0x01 : 0x00));
        dest.writeValue(error);
        if (errorCode == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeInt(errorCode);
        }
        dest.writeString(errorString);
		dest.writeByte((byte) (upload ? 0x01 : 0x00));
    }

    public static final Parcelable.Creator<FileTransferProgress> CREATOR = new Parcelable.Creator<FileTransferProgress>() {
        @Override
        public FileTransferProgress createFromParcel(Parcel in) {
            return new FileTransferProgress(in);
        }

        @Override
        public FileTransferProgress[] newArray(int size) {
            return new FileTransferProgress[size];
        }
    };

	@Override
	public String toString() {
		return "FileTransferProgress [messageId=" + messageId + ", when=" + when
				+ ", title=" + title + ", progress=" + progress + "]";
	}
}
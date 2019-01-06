package net.phonex.ft.transfer;

import android.os.Parcel;
import android.os.Parcelable;

import net.phonex.pub.parcels.StoredCredentials;
import net.phonex.soap.IUserIdentityHolder;
import net.phonex.soap.UserPrivateCredentials;

import java.io.File;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;


/**
 * Class for passing parameters to this task.
 * @author ph4r05
 */
public class UploadFileParams implements TransferParameters {
	/**
	 * file uris to send formatted as FileStorageUri prescribes
	 */
	private List<String> fileUris;

	public List<String> getFileUris() {
		return fileUris;
	}

	public void setFileUris(List<String> fileUris) {
		this.fileUris = fileUris;
	}

	/**
	 * destination SIP.
	 */
	private String destinationSip;

	/**
	 * Notification message ID associated with this file.
	 */
	private Long msgId;
	private Long queueMsgId;

	/**
	 * Title & description of the message.
	 */
	private String title;
	private String desc;

	public String getDestinationSip() {
		return destinationSip;
	}

	public void setDestinationSip(String destinationSip) {
		this.destinationSip = destinationSip;
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

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDesc() {
		return desc;
	}

	public void setDesc(String desc) {
		this.desc = desc;
	}


	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(destinationSip);
		if (msgId == null) {
			dest.writeByte((byte) (0x00));
		} else {
			dest.writeByte((byte) (0x01));
			dest.writeLong(msgId);
		}
		if (queueMsgId == null) {
			dest.writeByte((byte) (0x00));
		} else {
			dest.writeByte((byte) (0x01));
			dest.writeLong(queueMsgId);
		}
		dest.writeString(title);
		dest.writeString(desc);
		if (fileUris == null) {
			dest.writeByte((byte) (0x00));
		} else {
			dest.writeByte((byte) (0x01));
			dest.writeList(fileUris);
		}
	}

	public UploadFileParams() {
	}

	private UploadFileParams(Parcel in) {
		destinationSip = in.readString();
		msgId = in.readByte() == 0x00 ? null : in.readLong();
		queueMsgId = in.readByte() == 0x00 ? null : in.readLong();
		title = in.readString();
		desc = in.readString();
		if (in.readByte() == 0x01) {
			fileUris = new ArrayList<String>();
			in.readList(fileUris, String.class.getClassLoader());
		} else {
			fileUris = null;
		}
	}

	public static final Parcelable.Creator<UploadFileParams> CREATOR = new Parcelable.Creator<UploadFileParams>() {
		public UploadFileParams createFromParcel(Parcel source) {
			return new UploadFileParams(source);
		}

		public UploadFileParams[] newArray(int size) {
			return new UploadFileParams[size];
		}
	};
}
package net.phonex.pub.parcels;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

/**
* Class describes result of the download process.
* Implements parcelable so it can be loaded by user on request.
*
* @author ph4r05
*
*/
public class DownloadResult implements Parcelable {
    /**
     * Error condition occurred in the progress.
     * If error is set, it is terminal state, no further state change
     * should be signaled any further.
     */
    private FileTransferError error = FileTransferError.NONE;

    /**
     * Timestamp of the event.
     */
    private long when;

    /**
     * List of extracted files on successful download.
     */
    private List<String> extractedFiles;

    public DownloadResult() {
    }

    public DownloadResult(FileTransferError error, long when, List<String> extractedFiles) {
        this.error = error;
        this.when = when;
        this.extractedFiles = extractedFiles;
    }

    public FileTransferError getError() {
        return error;
    }

    public void setError(FileTransferError error) {
        this.error = error;
    }

    public long getWhen() {
        return when;
    }

    public void setWhen(long when) {
        this.when = when;
    }

    public List<String> getExtractedFiles() {
        return extractedFiles;
    }

    public void setExtractedFiles(List<String> extractedFiles) {
        this.extractedFiles = extractedFiles;
    }

    DownloadResult(Parcel in) {
        error = (FileTransferError) in.readValue(FileTransferError.class.getClassLoader());
        when = in.readLong();
        if (in.readByte() == 0x01) {
            extractedFiles = new ArrayList<String>();
            in.readList(extractedFiles, String.class.getClassLoader());
        } else {
            extractedFiles = null;
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeValue(error);
        dest.writeLong(when);
        if (extractedFiles == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeList(extractedFiles);
        }
    }

    public static final Creator<DownloadResult> CREATOR = new Creator<DownloadResult>() {
        @Override
        public DownloadResult createFromParcel(Parcel in) {
            return new DownloadResult(in);
        }

        @Override
        public DownloadResult[] newArray(int size) {
            return new DownloadResult[size];
        }
    };
}

package net.phonex.pub.parcels;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by Matus on 25.6.2015.
 * TODO javadoc
 */
public class FileDecryptProgress implements Parcelable {

    private String uri;

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
    private boolean done = false;

    /**
     * Error condition occurred in the progress.
     * If error is set, it is terminal state, no further state change
     * should be signaled any further.
     */
    private FileTransferError error = FileTransferError.NONE;

    private long when = 0;

    private String newPath;

    private int waitingTasks;

    public FileDecryptProgress(String uri, FileTransferProgressEnum progressCode, int progress, boolean done, FileTransferError error, int waitingTasks, String newPath) {
        this.uri = uri;
        this.progressCode = progressCode;
        this.progress = progress;
        this.done = done;
        this.error = error;
        this.when = System.currentTimeMillis();
        this.newPath = newPath;
        this.waitingTasks = waitingTasks;
    }

    public long getWhen() {
        return when;
    }

    @Override
    public String toString() {
        return "FileDecryptProgress{" +
                "uri='" + uri + '\'' +
                ", progressCode=" + progressCode +
                ", progress=" + progress +
                ", done=" + done +
                ", error=" + error +
                ", when=" + when +
                ", newPath='" + newPath + '\'' +
                ", waitingTasks=" + waitingTasks +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FileDecryptProgress that = (FileDecryptProgress) o;

        if (progress != that.progress) return false;
        if (done != that.done) return false;
        if (waitingTasks != that.waitingTasks) return false;
        if (!uri.equals(that.uri)) return false;
        if (progressCode != that.progressCode) return false;
        if (error != that.error) return false;
        return !(newPath != null ? !newPath.equals(that.newPath) : that.newPath != null);

    }

    @Override
    public int hashCode() {
        int result = uri.hashCode();
        result = 31 * result + progressCode.hashCode();
        result = 31 * result + progress;
        result = 31 * result + (done ? 1 : 0);
        result = 31 * result + error.hashCode();
        result = 31 * result + waitingTasks;
        return result;
    }

    public String getUri() {
        return uri;
    }

    public FileTransferProgressEnum getProgressCode() {
        return progressCode;
    }

    public int getProgress() {
        return progress;
    }

    public boolean isDone() {
        return done;
    }

    public String getNewPath() {
        return newPath;
    }

    public int getWaitingTasksCount() {
        return waitingTasks;
    }

    public FileTransferError getError() {
        return error;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.uri);
        dest.writeInt(this.progressCode == null ? -1 : this.progressCode.ordinal());
        dest.writeInt(this.progress);
        dest.writeByte(done ? (byte) 1 : (byte) 0);
        dest.writeInt(this.error == null ? -1 : this.error.ordinal());
        dest.writeLong(this.when);
        dest.writeString(this.newPath);
        dest.writeInt(this.waitingTasks);
    }

    private FileDecryptProgress(Parcel in) {
        this.uri = in.readString();
        int tmpProgressCode = in.readInt();
        this.progressCode = tmpProgressCode == -1 ? null : FileTransferProgressEnum.values()[tmpProgressCode];
        this.progress = in.readInt();
        this.done = in.readByte() != 0;
        int tmpError = in.readInt();
        this.error = tmpError == -1 ? null : FileTransferError.values()[tmpError];
        this.when = in.readLong();
        this.newPath = in.readString();
        this.waitingTasks = in.readInt();
    }

    public static final Creator<FileDecryptProgress> CREATOR = new Creator<FileDecryptProgress>() {
        public FileDecryptProgress createFromParcel(Parcel source) {
            return new FileDecryptProgress(source);
        }

        public FileDecryptProgress[] newArray(int size) {
            return new FileDecryptProgress[size];
        }
    };
}

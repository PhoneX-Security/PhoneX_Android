package net.phonex.pub.parcels;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by miroc on 29.8.15.
 */
public class GenericTaskProgress implements Parcelable {

    private long timestamp;
    /**
     * progress 0-100
     */
    private int progress = 0;
    private String message;
    private boolean done = false;
    private GenericError error = GenericError.NONE;

    protected GenericTaskProgress() {
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getProgress() {
        return progress;
    }

    public String getMessage() {
        return message;
    }

    public boolean isDone() {
        return done;
    }

    public GenericError getError() {
        return error;
    }

    public static GenericTaskProgress doneInstance(){
        GenericTaskProgress progress = new GenericTaskProgress();
        progress.progress = 100;
        progress.done = true;
        return progress;
    }

    public static GenericTaskProgress genericErrorInstance(String message){
        return errorInstance(GenericError.GENERIC_ERROR, message);
    }

    public static GenericTaskProgress errorInstance(GenericError error, String message){
        GenericTaskProgress progress = new GenericTaskProgress();
        progress.done = false;
        progress.error = error;
        progress.message = message;
        return progress;
    }

    public static GenericTaskProgress progressInstance(String message){
        GenericTaskProgress progress = new GenericTaskProgress();
        progress.message = message;
        progress.done = false;
        return progress;
    }

    protected GenericTaskProgress(Parcel in) {
        timestamp = in.readLong();
        progress = in.readInt();
        message = in.readString();
        done = in.readByte() != 0x00;
        error = (GenericError) in.readValue(GenericError.class.getClassLoader());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(timestamp);
        dest.writeInt(progress);
        dest.writeString(message);
        dest.writeByte((byte) (done ? 0x01 : 0x00));
        dest.writeValue(error);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<GenericTaskProgress> CREATOR = new Parcelable.Creator<GenericTaskProgress>() {
        @Override
        public GenericTaskProgress createFromParcel(Parcel in) {
            return new GenericTaskProgress(in);
        }

        @Override
        public GenericTaskProgress[] newArray(int size) {
            return new GenericTaskProgress[size];
        }
    };

    @Override
    public String toString() {
        return "GenericTaskProgress{" +
                "timestamp=" + timestamp +
                ", progress=" + progress +
                ", message='" + message + '\'' +
                ", done=" + done +
                ", error=" + error +
                '}';
    }
}
package net.phonex.pub.parcels;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by miroc on 14.1.15.
 */
public class MakeCallResult implements Parcelable {
    private int status;
    private int callId;

    public MakeCallResult(int status, int callId) {
        this.status = status;
        this.callId = callId;
    }

    public static MakeCallResult create(int status){
        return create(status, 0);
    }

    public static MakeCallResult create(int status, int callId){
        return new MakeCallResult(status, callId);
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getCallId() {
        return callId;
    }

    public void setCallId(int callId) {
        this.callId = callId;
    }

    protected MakeCallResult(Parcel in) {
        status = in.readInt();
        callId = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(status);
        dest.writeInt(callId);
    }

    @Override
    public String toString() {
        return "MakeCallResult{" +
                "status=" + status +
                ", callId=" + callId +
                '}';
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<MakeCallResult> CREATOR = new Parcelable.Creator<MakeCallResult>() {
        @Override
        public MakeCallResult createFromParcel(Parcel in) {
            return new MakeCallResult(in);
        }

        @Override
        public MakeCallResult[] newArray(int size) {
            return new MakeCallResult[size];
        }
    };
}
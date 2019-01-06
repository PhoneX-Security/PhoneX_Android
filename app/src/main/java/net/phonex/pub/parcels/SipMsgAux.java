package net.phonex.pub.parcels;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Carries aux data for sending a message via SIP.
 * Created by dusanklinec on 12.11.15.
 */
public class SipMsgAux implements Parcelable {
    private Integer msgType;
    private Integer msgSubType;

    public SipMsgAux() {
    }

    public SipMsgAux(Integer msgType, Integer msgSubType) {
        this.msgType = msgType;
        this.msgSubType = msgSubType;
    }

    public Integer getMsgType() {
        return msgType;
    }

    public void setMsgType(Integer msgType) {
        this.msgType = msgType;
    }

    public Integer getMsgSubType() {
        return msgSubType;
    }

    public void setMsgSubType(Integer msgSubType) {
        this.msgSubType = msgSubType;
    }

    @Override
    public String toString() {
        return "SipMsgAux{" +
                "msgType=" + msgType +
                ", msgSubType=" + msgSubType +
                '}';
    }

    protected SipMsgAux(Parcel in) {
        msgType = in.readByte() == 0x00 ? null : in.readInt();
        msgSubType = in.readByte() == 0x00 ? null : in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (msgType == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeInt(msgType);
        }
        if (msgSubType == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeInt(msgSubType);
        }
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<SipMsgAux> CREATOR = new Parcelable.Creator<SipMsgAux>() {
        @Override
        public SipMsgAux createFromParcel(Parcel in) {
            return new SipMsgAux(in);
        }

        @Override
        public SipMsgAux[] newArray(int size) {
            return new SipMsgAux[size];
        }
    };
}

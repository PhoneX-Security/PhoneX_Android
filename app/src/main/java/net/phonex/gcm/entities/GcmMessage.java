package net.phonex.gcm.entities;

//import javax.annotation.Generated;
import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

//@Generated("org.jsonschema2pojo")

/**
 * Short variables in json - we save characters
 */
public class GcmMessage implements Parcelable, Serializable {
    private static final long serialVersionUID = 0L;

    @SerializedName("t")
    @Expose
    private Long timestamp;
    @SerializedName("b")
    @Expose
    private Integer badgeCount;
    @SerializedName("p")
    @Expose
    private String push;

    /**
     *
     * @return
     *     The timestamp
     */
    public Long getTimestamp() {
        return timestamp;
    }

    /**
     *
     * @param t
     *     The timestamp
     */
    public void setTimestamp(Long t) {
        this.timestamp = t;
    }

    /**
     *
     * @return
     *     The badgeCount
     */
    public Integer getBadgeCount() {
        return badgeCount;
    }

    /**
     *
     * @param b
     *     The badgeCount
     */
    public void setBadgeCount(Integer b) {
        this.badgeCount = b;
    }

    /**
     * Push message itself
     * @return
     *     The push
     */
    public String getPush() {
        return push;
    }

    /**
     *
     * @param p
     *     The push
     */
    public void setPush(String p) {
        this.push = p;
    }




    protected GcmMessage(Parcel in) {
        timestamp = in.readByte() == 0x00 ? null : in.readLong();
        badgeCount = in.readByte() == 0x00 ? null : in.readInt();
        push = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (timestamp == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeLong(timestamp);
        }
        if (badgeCount == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeInt(badgeCount);
        }
        dest.writeString(push);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<GcmMessage> CREATOR = new Parcelable.Creator<GcmMessage>() {
        @Override
        public GcmMessage createFromParcel(Parcel in) {
            return new GcmMessage(in);
        }

        @Override
        public GcmMessage[] newArray(int size) {
            return new GcmMessage[size];
        }
    };
}
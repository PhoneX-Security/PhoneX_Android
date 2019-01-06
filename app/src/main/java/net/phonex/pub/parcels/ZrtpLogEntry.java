package net.phonex.pub.parcels;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Represents ZRTP message (from ZRTPCPP) log entry. 
 * @author ph4r05
 */
public class ZrtpLogEntry implements Parcelable {
	private long time;
	private int zrtpState;
	private int hcode;
	private int severity;
	private int subcode;

	/**
     * Construct from parcelable <br/>
     * Only used by {@link #CREATOR}
     * 
     * @param in parcelable to build from
     */
    private ZrtpLogEntry(Parcel in) {
    	time = in.readLong();
    	zrtpState = in.readInt();
    	hcode = in.readInt();
    	severity = in.readInt();
    	subcode = in.readInt();
    }

    /**
     * Constructor <br/>
     */
    public ZrtpLogEntry() {
        // Nothing to do in default constructor
    }
    
    public ZrtpLogEntry(long time, int zrtpState, int hcode, int severity, int subcode) {
		super();
		this.time = time;
		this.zrtpState = zrtpState;
		this.hcode = hcode;
		this.severity = severity;
		this.subcode = subcode;
	}

	/**
     * @see Parcelable#describeContents()
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * @see Parcelable#writeToParcel(Parcel, int)
     */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(time);
        dest.writeInt(zrtpState);
        dest.writeInt(hcode);
        dest.writeInt(severity);
        dest.writeInt(subcode);
    }

    /**
     * Parcelable creator. So that it can be passed as an argument of the aidl
     * interface
     */
    public static final Parcelable.Creator<ZrtpLogEntry> CREATOR = new Parcelable.Creator<ZrtpLogEntry>() {
        public ZrtpLogEntry createFromParcel(Parcel in) {
            return new ZrtpLogEntry(in);
        }

        public ZrtpLogEntry[] newArray(int size) {
            return new ZrtpLogEntry[size];
        }
    };

	public long getTime() {
		return time;
	}

	public void setTime(long time) {
		this.time = time;
	}

	public int getZrtpState() {
		return zrtpState;
	}

	public void setZrtpState(int zrtpState) {
		this.zrtpState = zrtpState;
	}

	public int getHcode() {
		return hcode;
	}

	public void setHcode(int hcode) {
		this.hcode = hcode;
	}

	public int getSeverity() {
		return severity;
	}

	public void setSeverity(int severity) {
		this.severity = severity;
	}

	public int getSubcode() {
		return subcode;
	}

	public void setSubcode(int subcode) {
		this.subcode = subcode;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + hcode;
		result = prime * result + severity;
		result = prime * result + subcode;
		result = prime * result + (int) (time ^ (time >>> 32));
		result = prime * result + zrtpState;
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
		ZrtpLogEntry other = (ZrtpLogEntry) obj;
		if (hcode != other.hcode)
			return false;
		if (severity != other.severity)
			return false;
		if (subcode != other.subcode)
			return false;
		if (time != other.time)
			return false;
		if (zrtpState != other.zrtpState)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "ZrtpLogEntry [time=" + time + ", zrtpState=" + zrtpState
				+ ", hcode=" + hcode + ", severity=" + severity + ", subcode="
				+ subcode + "]";
	}
}

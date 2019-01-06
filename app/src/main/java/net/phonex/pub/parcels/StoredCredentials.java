package net.phonex.pub.parcels;

import android.os.Parcel;
import android.os.Parcelable;

import net.phonex.core.SipUri;
import net.phonex.soap.CertGenParams;

/**
 * Stored credentials in secure in memory database.
 * 
 * @author ph4r05
 */
public class StoredCredentials implements Parcelable {
	private String userSip;
	private String usrPass;
	private String usrPemPass;
	private String usrStoragePass;
    private boolean set;
	
	/**
	 * Empty constructor.
	 */
	public StoredCredentials() {
		
	}
	
	/**
	 * Constructor using fields. 
	 * 
	 * @param userSip
	 * @param usrPass
	 * @param usrPemPass
	 * @param usrStoragePass
	 */
	public StoredCredentials(String userSip, String usrPass, String usrPemPass, String usrStoragePass) {
		super();
		this.userSip = userSip;
		this.usrPass = usrPass;
		this.usrPemPass = usrPemPass;
		this.usrStoragePass = usrStoragePass;
	}
	
	/**
	 * Constructor from CertGenParams. Loads all fields (name, pass, pem, storage).
	 * @param params
	 */
	public StoredCredentials(CertGenParams params){
		super();
		this.userSip = params.getUserSIP();
		this.usrPass = params.getUserPass();
		this.usrPemPass = params.getPemPass();
		this.usrStoragePass = params.getStoragePass();
	}

	/**
     * Construct from parcelable <br/>
     * Only used by {@link #CREATOR}
     * 
     * @param in parcelable to build from
     */
    public StoredCredentials(Parcel in) {
    	userSip = in.readString();
    	usrPass = in.readString();
    	usrPemPass = in.readString();
    	usrStoragePass = in.readString();
        set = in.readInt() == 1;
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
        dest.writeString(userSip);
        dest.writeString(usrPass);
        dest.writeString(usrPemPass);
        dest.writeString(usrStoragePass);
        dest.writeInt(set ? 1 : 0);
    }
    
    /**
     * Parcelable creator. So that it can be passed as an argument of the aidl
     * interface.
     */
    public static final Parcelable.Creator<StoredCredentials> CREATOR = new Parcelable.Creator<StoredCredentials>() {
        public StoredCredentials createFromParcel(Parcel in) {
            return new StoredCredentials(in);
        }

        public StoredCredentials[] newArray(int size) {
            return new StoredCredentials[size];
        }
    };
    
	/**
	 * @return the userSip in sip form (username@domain)
	 */
	public String getUserSip() {
		return userSip;
	}
	/**
	 * @param userSip the userSip to set
	 */
	public void setUserSip(String userSip) {
		this.userSip = userSip;
	}
	/**
	 * @return the usrPass
	 */
	public String getUsrPass() {
		return usrPass;
	}
	/**
	 * @param usrPass the usrPass to set
	 */
	public void setUsrPass(String usrPass) {
		this.usrPass = usrPass;
	}
	/**
	 * @return the usrPemPass
	 */
	public String getUsrPemPass() {
		return usrPemPass;
	}
	/**
	 * @param usrPemPass the usrPemPass to set
	 */
	public void setUsrPemPass(String usrPemPass) {
		this.usrPemPass = usrPemPass;
	}
	/**
	 * @return the usrStoragePass
	 */
	public String getUsrStoragePass() {
		return usrStoragePass;
	}
	/**
	 * @param usrStoragePass the usrStoragePass to set
	 */
	public void setUsrStoragePass(String usrStoragePass) {
		this.usrStoragePass = usrStoragePass;
	}

    public boolean isSet() {
        return set;
    }

    public void setSet(boolean set) {
        this.set = set;
    }

	public String retrieveDomain(){
		return SipUri.parseSipUri(SipUri.getCanonicalSipContact(getUserSip(), true)).domain;
	}

    /* (non-Javadoc)
         * @see java.lang.Object#hashCode()
         */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((userSip == null) ? 0 : userSip.hashCode());
		result = prime * result + ((usrPass == null) ? 0 : usrPass.hashCode());
		result = prime * result
				+ ((usrPemPass == null) ? 0 : usrPemPass.hashCode());
		result = prime * result
				+ ((usrStoragePass == null) ? 0 : usrStoragePass.hashCode());
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		StoredCredentials other = (StoredCredentials) obj;
		if (userSip == null) {
			if (other.userSip != null)
				return false;
		} else if (!userSip.equals(other.userSip))
			return false;
		if (usrPass == null) {
			if (other.usrPass != null)
				return false;
		} else if (!usrPass.equals(other.usrPass))
			return false;
		if (usrPemPass == null) {
			if (other.usrPemPass != null)
				return false;
		} else if (!usrPemPass.equals(other.usrPemPass))
			return false;
		if (usrStoragePass == null) {
			if (other.usrStoragePass != null)
				return false;
		} else if (!usrStoragePass.equals(other.usrStoragePass))
			return false;
		return true;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "StoredCredentials [userSip=" + userSip + ", usrPass=" + usrPass
				+ ", usrPemPass=" + usrPemPass + ", usrStoragePass="
				+ usrStoragePass + "]";
	}
	
	
}

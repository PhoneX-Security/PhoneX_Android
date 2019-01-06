package net.phonex.pub.parcels;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Parameters for certificate check/update task. 
 * 
 * @author ph4r05
 */
public class CertUpdateParams implements Parcelable {
	private String user;
	private boolean forceCheck=false;
	private boolean pushNotification=false;
	private Long notBefore;
	private String certHash;
	private String callbackId;
	
	/**
     * Construct from parcelable <br/>
     * Only used by {@link #CREATOR}
     * 
     * @param in parcelable to build from
     */
    private CertUpdateParams(Parcel in) {
    	user = in.readString();
    	forceCheck = in.readInt() == 1;
    	pushNotification = in.readInt() == 1;
    	notBefore = (Long) in.readValue(Long.class.getClassLoader());
    	certHash = (String) in.readValue(String.class.getClassLoader());
    	callbackId = (String) in.readValue(String.class.getClassLoader());
    }

    /**
     * Constructor <br/>
     */
    public CertUpdateParams() {
        // Nothing to do in default constructor
    }
    
    public CertUpdateParams(String user, boolean forceCheck) {
		super();
		this.user = user;
		this.forceCheck = forceCheck;
	}

	public CertUpdateParams(String user, boolean forceCheck, boolean pushNotification, Long notBefore, String certHash) {
		super();
		this.user = user;
		this.forceCheck = forceCheck;
		this.pushNotification = pushNotification;
		this.notBefore = notBefore;
		this.certHash = certHash;
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
    	dest.writeString(user);
    	dest.writeInt(forceCheck ? 1 : 0);
    	dest.writeInt(pushNotification ? 1 : 0);
    	dest.writeValue(notBefore);
    	dest.writeValue(certHash);
    	dest.writeValue(callbackId);
    }

    /**
     * Parcelable creator. So that it can be passed as an argument of the aidl
     * interface.
     */
    public static final Parcelable.Creator<CertUpdateParams> CREATOR = new Parcelable.Creator<CertUpdateParams>() {
        public CertUpdateParams createFromParcel(Parcel in) {
            return new CertUpdateParams(in);
        }

        public CertUpdateParams[] newArray(int size) {
            return new CertUpdateParams[size];
        }
    };

	/**
	 * @return the user
	 */
	public String getUser() {
		return user;
	}

	/**
	 * @param user the user to set
	 */
	public void setUser(String user) {
		this.user = user;
	}

	/**
	 * @return the forceCheck
	 */
	public boolean isForceCheck() {
		return forceCheck;
	}

	/**
	 * @param forceCheck the forceCheck to set
	 */
	public void setForceCheck(boolean forceCheck) {
		this.forceCheck = forceCheck;
	}

	/**
	 * @return the pushNotification
	 */
	public boolean isPushNotification() {
		return pushNotification;
	}

	/**
	 * @param pushNotification the pushNotification to set
	 */
	public void setPushNotification(boolean pushNotification) {
		this.pushNotification = pushNotification;
	}

	/**
	 * @return the notBefore
	 */
	public Long getNotBefore() {
		return notBefore;
	}

	/**
	 * @param notBefore the notBefore to set
	 */
	public void setNotBefore(Long notBefore) {
		this.notBefore = notBefore;
	}

	/**
	 * @return the certHash
	 */
	public String getCertHash() {
		return certHash;
	}

	/**
	 * @param certHash the certHash to set
	 */
	public void setCertHash(String certHash) {
		this.certHash = certHash;
	}

	/**
	 * @return the callbackId
	 */
	public String getCallbackId() {
		return callbackId;
	}

	/**
	 * @param callbackId the callbackId to set
	 */
	public void setCallbackId(String callbackId) {
		this.callbackId = callbackId;
	}
    
}

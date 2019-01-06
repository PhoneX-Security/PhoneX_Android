package net.phonex.pub.parcels;

import android.os.Parcel;
import android.os.Parcelable;

import net.phonex.util.crypto.CertificatesAndKeys;

import java.security.cert.X509Certificate;

/**
 * 
 * @author ph4r05
 * @generated-with: http://parcelabler.com/
 */
public class CertUpdateProgress implements Parcelable {
	public enum CertUpdateStateEnum {NONE, IN_QUEUE, STARTED, LOCAL_LOADING, SERVER_CALL, POST_SERVER_CALL, WAITING_FINAL_CONFIRMATION, SAVING, DONE};
	
	/**
	 * User for which this update process takes place.
	 */
	private String user;
	
	/**
	 * State of the updating process right now.
	 */
	private CertUpdateStateEnum state = CertUpdateStateEnum.NONE;
	
	/**
	 * When was this entry updated for the last time.
	 */
	private long when = 0;
	
	/**
	 * Denotes whether certificate was changed (new was loaded).
	 * Is valid only if state is DONE.
	 */
	private boolean certChanged=false;
	
	/**
	 * New certificate loaded. Is valid only in states {WAITING_FINAL_CONFIRMATION, DONE}.
	 * Serves mainly for confirmation purposes.
	 */
	private X509Certificate newCertificate;

    public CertUpdateProgress(String user, CertUpdateStateEnum state, long when) {
		super();
		this.user = user;
		this.state = state;
		this.when = when;
	}

	public CertUpdateProgress() {
		super();
	}
	
	protected CertUpdateProgress(Parcel in) {
        user = in.readString();
        state = (CertUpdateStateEnum) in.readValue(CertUpdateStateEnum.class.getClassLoader());
        when = in.readLong();
        certChanged = in.readByte() != 0x00;
        
        byte[] certByteArray = in.createByteArray();
        if (certByteArray!=null && certByteArray.length>0){
        	try {
        		newCertificate = CertificatesAndKeys.buildCertificate(certByteArray);
        	} catch(Exception e){
        		newCertificate = null;
        	}
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(user);
        dest.writeValue(state);
        dest.writeLong(when);
        dest.writeByte((byte) (certChanged ? 0x01 : 0x00));
        
        try {
        	dest.writeByteArray(newCertificate != null ? newCertificate.getEncoded() : new byte[0]);
        }catch(Exception e){
        	dest.writeByteArray(new byte[0]);
        }
    }

    public static final Parcelable.Creator<CertUpdateProgress> CREATOR = new Parcelable.Creator<CertUpdateProgress>() {
        @Override
        public CertUpdateProgress createFromParcel(Parcel in) {
            return new CertUpdateProgress(in);
        }

        @Override
        public CertUpdateProgress[] newArray(int size) {
            return new CertUpdateProgress[size];
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
	 * @return the state
	 */
	public CertUpdateStateEnum getState() {
		return state;
	}

	/**
	 * @param state the state to set
	 */
	public void setState(CertUpdateStateEnum state) {
		this.state = state;
	}

	/**
	 * @return the when
	 */
	public long getWhen() {
		return when;
	}

	/**
	 * @param when the when to set
	 */
	public void setWhen(long when) {
		this.when = when;
	}

	/**
	 * @return the newCertificate
	 */
	public X509Certificate getNewCertificate() {
		return newCertificate;
	}

	/**
	 * @param newCertificate the newCertificate to set
	 */
	public void setNewCertificate(X509Certificate newCertificate) {
		this.newCertificate = newCertificate;
	}

	/**
	 * @return the certChanged
	 */
	public boolean isCertChanged() {
		return certChanged;
	}

	/**
	 * @param certChanged the certChanged to set
	 */
	public void setCertChanged(boolean certChanged) {
		this.certChanged = certChanged;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "CertUpdateProgress [user=" + user + ", state=" + state
				+ ", when=" + when + ", certChanged=" + certChanged
				+ ", newCertificate=" + newCertificate + "]";
	}
}

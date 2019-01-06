package net.phonex.pub.parcels;

import android.os.Parcel;
import android.os.Parcelable;


public class KeyGenProgress implements Parcelable {
	public enum KeyGenStateEnum {NONE, IN_QUEUE, STARTED, CLEANING, OBTAINING_STATE, 
		GENERATING_KEY, GENERATED, SERVER_CALL_SAVE, POST_SERVER_CALL_SAVE, DONE,
		DELETING, SERVER_CALL_DELETE, POST_SERVER_CALL_DELETE};
	
	/**
	 * User for which this update process takes place.
	 */
	private String user;
	
	/**
	 * State of the updating process right now.
	 */
	private KeyGenStateEnum state = KeyGenStateEnum.NONE;
	
	/**
	 * When was this entry updated for the last time.
	 */
	private long when = 0;
	
	/**
	 * Whether error occurred.
	 */
	private boolean error=false;
	private Integer errorCode;
	private Integer errorCodeAux;
	private String errorReason;
	
	/**
	 * Maximum number of keys that will be generated in the single generating cycle.
	 * Valid only if state=GENERATING_KEY.
	 */
	private Integer maxKeysToGen;
	
	/**
	 * Number of generated keys so far.
	 * Valid only if state=GENERATING_KEY.
	 */
	private Integer alreadyGeneratedKeys;
	
	public KeyGenProgress(String user, KeyGenStateEnum state, long when) {
		super();
		this.user = user;
		this.state = state;
		this.when = when;
	}

	public KeyGenProgress(String user, KeyGenStateEnum state, long when,
			boolean error) {
		super();
		this.user = user;
		this.state = state;
		this.when = when;
		this.error = error;
	}

	
	public KeyGenProgress(String user, KeyGenStateEnum state, long when,
			boolean error, Integer errorCode) {
		super();
		this.user = user;
		this.state = state;
		this.when = when;
		this.error = error;
		this.errorCode = errorCode;
	}

	protected KeyGenProgress(Parcel in) {
        user = in.readString();
        state = (KeyGenStateEnum) in.readValue(KeyGenStateEnum.class.getClassLoader());
        when = in.readLong();
        error = in.readByte() != 0x00;
        errorCode = in.readByte() == 0x00 ? null : in.readInt();
        errorCodeAux = in.readByte() == 0x00 ? null : in.readInt();
        errorReason = in.readString();
        maxKeysToGen = in.readByte() == 0x00 ? null : in.readInt();
        alreadyGeneratedKeys = in.readByte() == 0x00 ? null : in.readInt();
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
        dest.writeByte((byte) (error ? 0x01 : 0x00));
        if (errorCode == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeInt(errorCode);
        }
        if (errorCodeAux == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeInt(errorCodeAux);
        }
        dest.writeString(errorReason);
        if (maxKeysToGen == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeInt(maxKeysToGen);
        }
        if (alreadyGeneratedKeys == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeInt(alreadyGeneratedKeys);
        }
    }

    public static final Parcelable.Creator<KeyGenProgress> CREATOR = new Parcelable.Creator<KeyGenProgress>() {
        @Override
        public KeyGenProgress createFromParcel(Parcel in) {
            return new KeyGenProgress(in);
        }

        @Override
        public KeyGenProgress[] newArray(int size) {
            return new KeyGenProgress[size];
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
	public KeyGenStateEnum getState() {
		return state;
	}

	/**
	 * @param state the state to set
	 */
	public void setState(KeyGenStateEnum state) {
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
	 * @return the error
	 */
	public boolean isError() {
		return error;
	}

	/**
	 * @param error the error to set
	 */
	public void setError(boolean error) {
		this.error = error;
	}

	/**
	 * @return the errorCode
	 */
	public Integer getErrorCode() {
		return errorCode;
	}

	/**
	 * @param errorCode the errorCode to set
	 */
	public void setErrorCode(Integer errorCode) {
		this.errorCode = errorCode;
	}

	/**
	 * @return the errorCodeAux
	 */
	public Integer getErrorCodeAux() {
		return errorCodeAux;
	}

	/**
	 * @param errorCodeAux the errorCodeAux to set
	 */
	public void setErrorCodeAux(Integer errorCodeAux) {
		this.errorCodeAux = errorCodeAux;
	}

	/**
	 * @return the errorReason
	 */
	public String getErrorReason() {
		return errorReason;
	}

	/**
	 * @param errorReason the errorReason to set
	 */
	public void setErrorReason(String errorReason) {
		this.errorReason = errorReason;
	}

	/**
	 * @return the maxKeysToGen
	 */
	public Integer getMaxKeysToGen() {
		return maxKeysToGen;
	}

	/**
	 * @param maxKeysToGen the maxKeysToGen to set
	 */
	public void setMaxKeysToGen(Integer maxKeysToGen) {
		this.maxKeysToGen = maxKeysToGen;
	}

	/**
	 * @return the alreadyGeneratedKeys
	 */
	public Integer getAlreadyGeneratedKeys() {
		return alreadyGeneratedKeys;
	}

	/**
	 * @param alreadyGeneratedKeys the alreadyGeneratedKeys to set
	 */
	public void setAlreadyGeneratedKeys(Integer alreadyGeneratedKeys) {
		this.alreadyGeneratedKeys = alreadyGeneratedKeys;
	}
}

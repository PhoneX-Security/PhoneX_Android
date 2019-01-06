package net.phonex.core;

import android.os.Parcel;
import android.os.Parcelable;

import net.phonex.db.entity.SipCallSession;

/**
 * Represents state of the media <b>device</b> layer <br/>
 * This class helps to serialize/deserialize the state of the media layer <br/>
 * The fields it contains are direclty available. <br/>
 * <b>Changing these fields has no effect on the device audio</b> : it's only a
 * structured holder for datas <br/>
 * This class is not related to SIP media state this is managed by
 * {@link SipCallSession.MediaState}
 */
public class MediaState implements Parcelable {

    /**
     * Primary key for the parcelable object
     */
    public int primaryKey = -1;

    /**
     * Whether the microphone is currently muted
     */
    public boolean isMicrophoneMute = false;

    /**
     * Whether the audio routes to the speaker
     */
    public boolean isSpeakerphoneOn = false;
    /**
     * Whether the audio routes to Bluetooth SCO
     */
    public boolean isBluetoothScoOn = false;
    /**
     * Gives phone capability to mute microphone
     */
    public boolean canMicrophoneMute = true;
    /**
     * Gives phone capability to route to speaker
     */
    public boolean canSpeakerphoneOn = true;
    /**
     * Gives phone capability to route to bluetooth SCO
     */
    public boolean isBluetoothSupported = false;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MediaState that = (MediaState) o;

        if (primaryKey != that.primaryKey) return false;
        if (isMicrophoneMute != that.isMicrophoneMute) return false;
        if (isSpeakerphoneOn != that.isSpeakerphoneOn) return false;
        if (isBluetoothScoOn != that.isBluetoothScoOn) return false;
        if (canMicrophoneMute != that.canMicrophoneMute) return false;
        if (canSpeakerphoneOn != that.canSpeakerphoneOn) return false;
        return isBluetoothSupported == that.isBluetoothSupported;

    }

    @Override
    public int hashCode() {
        int result = primaryKey;
        result = 31 * result + (isMicrophoneMute ? 1 : 0);
        result = 31 * result + (isSpeakerphoneOn ? 1 : 0);
        result = 31 * result + (isBluetoothScoOn ? 1 : 0);
        result = 31 * result + (canMicrophoneMute ? 1 : 0);
        result = 31 * result + (canSpeakerphoneOn ? 1 : 0);
        result = 31 * result + (isBluetoothSupported ? 1 : 0);
        return result;
    }

    /**
     * Constructor for a media state object <br/>
     * It will contains default values for all flags This class as no
     * setter/getter for members flags <br/>
     * It's aim is to allow to serialize/deserialize easily the state of media
     * layer, <n>not to modify it</b>
     */
    public MediaState() {
        // Nothing to do in default constructor
    }

    /**
     * Construct from parcelable <br/>
     * Only used by {@link #CREATOR}
     * 
     * @param in parcelable to build from
     */
    private MediaState(Parcel in) {
        primaryKey = in.readInt();
        isMicrophoneMute = (in.readInt() == 1);
        isSpeakerphoneOn = (in.readInt() == 1);
        isBluetoothScoOn = (in.readInt() == 1);
        canMicrophoneMute = (in.readInt() == 1);
        canSpeakerphoneOn = (in.readInt() == 1);
        isBluetoothSupported = (in.readInt() == 1);
    }

    /**
     * Parcelable creator. So that it can be passed as an argument of the aidl
     * interface
     */
    public static final Parcelable.Creator<MediaState> CREATOR = new Parcelable.Creator<MediaState>() {
        public MediaState createFromParcel(Parcel in) {
            return new MediaState(in);
        }

        public MediaState[] newArray(int size) {
            return new MediaState[size];
        }
    };

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
        dest.writeInt(primaryKey);
        dest.writeInt(isMicrophoneMute ? 1 : 0);
        dest.writeInt(isSpeakerphoneOn ? 1 : 0);
        dest.writeInt(isBluetoothScoOn ? 1 : 0);
        dest.writeInt(canMicrophoneMute ? 1 : 0);
        dest.writeInt(canSpeakerphoneOn ? 1 : 0);
        dest.writeInt(isBluetoothSupported ? 1 : 0);
    }

    @Override
    public String toString() {
        return "MediaState{" +
                "primaryKey=" + primaryKey +
                ", isMicrophoneMute=" + isMicrophoneMute +
                ", isSpeakerphoneOn=" + isSpeakerphoneOn +
                ", isBluetoothScoOn=" + isBluetoothScoOn +
                ", canMicrophoneMute=" + canMicrophoneMute +
                ", canSpeakerphoneOn=" + canSpeakerphoneOn +
                ", isBluetoothSupported=" + isBluetoothSupported +
                '}';
    }


}

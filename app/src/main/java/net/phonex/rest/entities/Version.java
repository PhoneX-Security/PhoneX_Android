package net.phonex.rest.entities;

/**
 * Created by miroc on 17.6.15.
 */
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;
//import javax.annotation.Generated;
import com.google.gson.annotations.Expose;

//@Generated("org.jsonschema2pojo")
public class Version implements Parcelable {

    @Expose
    private Integer versionCode;
    @Expose
    private String versionName;
    @Expose
    private String releaseNotes;
    @Expose
    private Boolean availableAtMarket;
    @Expose
    private List<String> fields = new ArrayList<String>();
    @Expose
    private Integer responseCode;

    /**
     *
     * @return
     * The versionCode
     */
    public Integer getVersionCode() {
        return versionCode;
    }

    /**
     *
     * @param versionCode
     * The versionCode
     */
    public void setVersionCode(Integer versionCode) {
        this.versionCode = versionCode;
    }

    /**
     *
     * @return
     * The versionName
     */
    public String getVersionName() {
        return versionName;
    }

    /**
     *
     * @param versionName
     * The versionName
     */
    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    /**
     *
     * @return
     * The releaseNotes
     */
    public String getReleaseNotes() {
        return releaseNotes;
    }

    /**
     *
     * @param releaseNotes
     * The releaseNotes
     */
    public void setReleaseNotes(String releaseNotes) {
        this.releaseNotes = releaseNotes;
    }

    /**
     *
     * @return
     * The availableAtMarket
     */
    public Boolean getAvailableAtMarket() {
        return availableAtMarket;
    }

    /**
     *
     * @param availableAtMarket
     * The availableAtMarket
     */
    public void setAvailableAtMarket(Boolean availableAtMarket) {
        this.availableAtMarket = availableAtMarket;
    }

    /**
     *
     * @return
     * The fields
     */
    public List<String> getFields() {
        return fields;
    }

    /**
     *
     * @param fields
     * The fields
     */
    public void setFields(List<String> fields) {
        this.fields = fields;
    }

    /**
     *
     * @return
     * The responseCode
     */
    public Integer getResponseCode() {
        return responseCode;
    }

    /**
     *
     * @param responseCode
     * The responseCode
     */
    public void setResponseCode(Integer responseCode) {
        this.responseCode = responseCode;
    }


    protected Version(Parcel in) {
        versionCode = in.readByte() == 0x00 ? null : in.readInt();
        versionName = in.readString();
        releaseNotes = in.readString();
        byte availableAtMarketVal = in.readByte();
        availableAtMarket = availableAtMarketVal == 0x02 ? null : availableAtMarketVal != 0x00;
        if (in.readByte() == 0x01) {
            fields = new ArrayList<String>();
            in.readList(fields, String.class.getClassLoader());
        } else {
            fields = null;
        }
        responseCode = in.readByte() == 0x00 ? null : in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (versionCode == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeInt(versionCode);
        }
        dest.writeString(versionName);
        dest.writeString(releaseNotes);
        if (availableAtMarket == null) {
            dest.writeByte((byte) (0x02));
        } else {
            dest.writeByte((byte) (availableAtMarket ? 0x01 : 0x00));
        }
        if (fields == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeList(fields);
        }
        if (responseCode == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeInt(responseCode);
        }
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<Version> CREATOR = new Parcelable.Creator<Version>() {
        @Override
        public Version createFromParcel(Parcel in) {
            return new Version(in);
        }

        @Override
        public Version[] newArray(int size) {
            return new Version[size];
        }
    };
}
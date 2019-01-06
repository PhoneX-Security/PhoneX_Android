
package net.phonex.rest.entities.auth.products;

//import javax.annotation.Generated;
import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

//@Generated("org.jsonschema2pojo")
public class AppPermission implements Parcelable {

    @SerializedName("permission")
    @Expose
    private String permission;
    @SerializedName("value")
    @Expose
    private Integer value;

    /**
     *
     * @return
     *     The permission
     */
    public String getPermission() {
        return permission;
    }

    /**
     *
     * @param permission
     *     The permission
     */
    public void setPermission(String permission) {
        this.permission = permission;
    }

    /**
     *
     * @return
     *     The value
     */
    public Integer getValue() {
        return value;
    }

    /**
     *
     * @param value
     *     The value
     */
    public void setValue(Integer value) {
        this.value = value;
    }

    protected AppPermission(Parcel in) {
        permission = in.readString();
        value = in.readByte() == 0x00 ? null : in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(permission);
        if (value == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeInt(value);
        }
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<AppPermission> CREATOR = new Parcelable.Creator<AppPermission>() {
        @Override
        public AppPermission createFromParcel(Parcel in) {
            return new AppPermission(in);
        }

        @Override
        public AppPermission[] newArray(int size) {
            return new AppPermission[size];
        }
    };
}

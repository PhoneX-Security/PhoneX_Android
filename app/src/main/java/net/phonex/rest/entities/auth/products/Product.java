package net.phonex.rest.entities.auth.products;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;
//import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import net.phonex.R;
import net.phonex.inapp.SkuDetails;

//@Generated("org.jsonschema2pojo")
public class Product implements Parcelable, ProductInterface {

    @SerializedName("id")
    @Expose
    private Integer id;
    @SerializedName("name")
    @Expose
    private String name;
    @SerializedName("platform")
    @Expose
    private Object platform;
    @SerializedName("priority")
    @Expose
    private Integer priority;
    @SerializedName("type")
    @Expose
    private String type;
    @SerializedName("app_permissions")
    @Expose
    private List<AppPermission> appPermissions = new ArrayList<AppPermission>();
    @SerializedName("display_name")
    @Expose
    private String displayName;
    @SerializedName("description")
    @Expose
    private String description;
    @SerializedName("period")
    @Expose
    private Integer period;
    @SerializedName("period_type")
    @Expose
    private String periodType;

    // SkuDetails are loaded via google play services and not serialized from json
    private SkuDetails skuDetails;

    /**
     *
     * @return
     *     The id
     */
    public Integer getId() {
        return id;
    }

    /**
     *
     * @param id
     *     The id
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     *
     * @return
     *     The name
     */
    public String getName() {
        return name;
    }

    /**
     *
     * @param name
     *     The name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     *
     * @return
     *     The platform
     */
    public Object getPlatform() {
        return platform;
    }

    /**
     *
     * @param platform
     *     The platform
     */
    public void setPlatform(Object platform) {
        this.platform = platform;
    }

    /**
     *
     * @return
     *     The priority
     */
    public Integer getPriority() {
        return priority;
    }

    /**
     *
     * @param priority
     *     The priority
     */
    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    /**
     *
     * @return
     *     The type
     */
    public String getType() {
        return type;
    }

    /**
     *
     * @param type
     *     The type
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     *
     * @return
     *     The appPermissions
     */
    public List<AppPermission> getAppPermissions() {
        return appPermissions;
    }

    /**
     *
     * @param appPermissions
     *     The app_permissions
     */
    public void setAppPermissions(List<AppPermission> appPermissions) {
        this.appPermissions = appPermissions;
    }

    /**
     *
     * @return
     *     The displayName
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     *
     * @param displayName
     *     The display_name
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     *
     * @return
     *     The description
     */
    public String getDescription() {
        return description;
    }

    /**
     *
     * @param description
     *     The description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     *
     * @return
     *     The period
     */
    public Integer getPeriod() {
        return period;
    }

    /**
     *
     * @param period
     *     The period
     */
    public void setPeriod(Integer period) {
        this.period = period;
    }

    /**
     *
     * @return
     *     The periodType
     */
    public String getPeriodType() {
        return periodType;
    }

    /**
     *
     * @param periodType
     *     The period_type
     */
    public void setPeriodType(String periodType) {
        this.periodType = periodType;
    }

    public SkuDetails getSkuDetails() {
        return skuDetails;
    }

    public void setSkuDetails(SkuDetails skuDetails) {
        this.skuDetails = skuDetails;
    }



    @Override
    public String toString() {
        return "Product{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", platform=" + platform +
                ", priority=" + priority +
                ", type='" + type + '\'' +
                ", appPermissions=" + appPermissions +
                ", displayName='" + displayName + '\'' +
                ", description='" + description + '\'' +
                ", period=" + period +
                ", periodType='" + periodType + '\'' +
                '}';
    }



    protected Product(Parcel in) {
        id = in.readByte() == 0x00 ? null : in.readInt();
        name = in.readString();
        platform = (Object) in.readValue(Object.class.getClassLoader());
        priority = in.readByte() == 0x00 ? null : in.readInt();
        type = in.readString();
        if (in.readByte() == 0x01) {
            appPermissions = new ArrayList<AppPermission>();
            in.readList(appPermissions, AppPermission.class.getClassLoader());
        } else {
            appPermissions = null;
        }
        displayName = in.readString();
        description = in.readString();
        period = in.readByte() == 0x00 ? null : in.readInt();
        periodType = in.readString();
        skuDetails = (SkuDetails) in.readValue(SkuDetails.class.getClassLoader());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (id == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeInt(id);
        }
        dest.writeString(name);
        dest.writeValue(platform);
        if (priority == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeInt(priority);
        }
        dest.writeString(type);
        if (appPermissions == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeList(appPermissions);
        }
        dest.writeString(displayName);
        dest.writeString(description);
        if (period == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeInt(period);
        }
        dest.writeString(periodType);
        dest.writeValue(skuDetails);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<Product> CREATOR = new Parcelable.Creator<Product>() {
        @Override
        public Product createFromParcel(Parcel in) {
            return new Product(in);
        }

        @Override
        public Product[] newArray(int size) {
            return new Product[size];
        }
    };

    @Override
    public int getTypeDescriptionResId() {
        /* Helper methods */
        if (getType().equals("consumable")){
            return R.string.single_package;
        } else {
            return R.string.month_subscription;
        }
    }
}
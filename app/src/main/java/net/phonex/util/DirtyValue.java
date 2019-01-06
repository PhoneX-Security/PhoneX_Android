package net.phonex.util;

import android.os.Parcel;
import android.os.Parcelable;

import net.phonex.pub.parcels.ZrtpLogEntry;

import java.io.Serializable;

/**
 * Wrapper value for any object allowing to track whether it was changed or not.
 * Created by dusanklinec on 13.01.16.
 */
public class DirtyValue<T> implements Serializable, Cloneable, Parcelable {
    private static final String TAG = "DirtyValue";

    /**
     * Value holding this object.
     */
    private T value;

    /**
     * Boolean flag indicating if the field was changed.
     */
    private boolean changed = false;

    /**
     * Boolean flag indicating if the field content was changed by setter.
     * i.e., if setter was called && the value differed from the value originally in this wrapper.
     */
    private boolean valueChanged = false;

    /**
     * Class for T if user wishes T to take part in parcelable.
     */
    private Class<T> tClass;

    public DirtyValue() {
    }

    /**
     * Initializes the value without setting it to changed (default value).
     * @param value
     */
    public DirtyValue(T value) {
        this.value = value;
    }

    public DirtyValue(T value, boolean changed) {
        this.value = value;
        this.changed = changed;
    }

    public DirtyValue(T value, boolean changed, Class<T> tClass) {
        this.value = value;
        this.changed = changed;
        this.tClass = tClass;
    }

    public DirtyValue(T value, Class<T> tClass) {
        this.value = value;
        this.tClass = tClass;
    }

    /**
     * Returns raw value.
     * @return
     */
    public T get() {
        return value;
    }

    /**
     * Use this method if T is an object that can be changed without using setter (i.e., collection).
     * @return
     */
    public T getMakeDirty() {
        this.setChanged(true);
        this.valueChanged = true;
        return value;
    }

    public DirtyValue set(T value) {
        final T prevValue = this.value;

        this.value = value;
        this.setChanged(true);

        if ((prevValue == null && value != null)
                || (prevValue != null && value == null)
                || (prevValue != null && !prevValue.equals(value)))
        {
            this.valueChanged = true;
        }

        return this;
    }

    public DirtyValue setDefault(T value) {
        this.value = value;
        return this;
    }

    public boolean isChanged() {
        return changed;
    }

    public DirtyValue setChanged(boolean changed) {
        this.changed = changed;
        return this;
    }

    public boolean isValueChanged() {
        return valueChanged;
    }

    public void setValueChanged(boolean valueChanged) {
        this.valueChanged = valueChanged;
    }

    public Class getValueClass() {
        return tClass;
    }

    /**
     * Setter of the value class.
     * If set, value will be serialized and deserialized in parcelable with this class loader.
     * @param tClass
     */
    public void setValueClass(Class<T> tClass) {
        this.tClass = tClass;
    }

    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    /**
     * Copies the object.
     * @return
     */
    public DirtyValue copy() {
        try {
            return (DirtyValue) this.clone();

        } catch (CloneNotSupportedException e) {
            Log.e(TAG, "Exception in cloning call session", e);
        }

        return null;
    }

    /**
     * Construct from parcelable <br/>
     * Only used by {@link #CREATOR}
     *
     * @param in parcelable to build from
     */
    protected DirtyValue(Parcel in) {
        readFromParcel(in);
    }

    /**
     * Initializes object from the parcel.
     * @param in
     */
    public void readFromParcel(Parcel in){
        changed = in.readInt() > 0;
        valueChanged = in.readInt() > 0;

        final boolean classSerialized = in.readInt() > 0;
        if (classSerialized){
            tClass = (Class<T>) in.readSerializable();
            if (tClass != null){
                final Object obj = in.readValue(tClass.getClassLoader());
                try {
                    value = (T) obj;
                } catch(Exception e){
                    Log.e(TAG, "Exception in casting unserialized value to given type");
                }
            }
        }
    }

    /**
     * @see Parcelable#writeToParcel(Parcel, int)
     */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(changed ? 1 : 0);
        dest.writeInt(valueChanged ? 1 : 0);

        if (tClass != null){
            dest.writeInt(1);
            dest.writeSerializable(tClass);
            dest.writeValue(value);

        } else {
            dest.writeInt(0);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Parcelable creator. So that it can be passed as an argument of the aidl
     * interface
     */
    public static final Parcelable.Creator<DirtyValue> CREATOR = new Parcelable.Creator<DirtyValue>() {
        public DirtyValue createFromParcel(Parcel in) {
            return new DirtyValue(in);
        }
        public DirtyValue[] newArray(int size) {
            return new DirtyValue[size];
        }
    };

    @Override
    public String toString() {
        return "{" + value + "|" + changed + "|" + valueChanged + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DirtyValue<?> that = (DirtyValue<?>) o;

        return !(value != null ? !value.equals(that.value) : that.value != null);

    }

    @Override
    public int hashCode() {
        return value != null ? value.hashCode() : 0;
    }
}

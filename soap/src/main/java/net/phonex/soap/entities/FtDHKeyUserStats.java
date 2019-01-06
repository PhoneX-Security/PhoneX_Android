package net.phonex.soap.entities;

import net.phonex.soap.SoapEnvelopeRegisterable;
import java.util.Hashtable;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.ksoap2.serialization.KvmSerializable;
import net.phonex.ksoap2.serialization.PropertyInfo;


public class FtDHKeyUserStats implements KvmSerializable, SoapEnvelopeRegisterable {

    public final static String NAMESPACE = "http://phoenix.com/hr/schemas";
    protected boolean ignoreNullWrappers = false;
    protected java.lang.String user;
    protected int readyCount;
    protected int usedCount;
    protected int expiredCount;
    protected int uploadedCount;


    /**
     * Gets the value of the user property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getUser() {
        return user;
    }

    /**
     * Sets the value of the user property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setUser(java.lang.String value) {
        this.user = value;
    }
    /**
     * Gets the value of the readyCount property.
     * 
     * @return
     *     possible object is
     *     {@link int }
     *     
     */
    public int getReadyCount() {
        return readyCount;
    }

    /**
     * Sets the value of the readyCount property.
     * 
     * @param value
     *     allowed object is
     *     {@link int }
     *     
     */
    public void setReadyCount(int value) {
        this.readyCount = value;
    }
    /**
     * Gets the value of the usedCount property.
     * 
     * @return
     *     possible object is
     *     {@link int }
     *     
     */
    public int getUsedCount() {
        return usedCount;
    }

    /**
     * Sets the value of the usedCount property.
     * 
     * @param value
     *     allowed object is
     *     {@link int }
     *     
     */
    public void setUsedCount(int value) {
        this.usedCount = value;
    }
    /**
     * Gets the value of the expiredCount property.
     * 
     * @return
     *     possible object is
     *     {@link int }
     *     
     */
    public int getExpiredCount() {
        return expiredCount;
    }

    /**
     * Sets the value of the expiredCount property.
     * 
     * @param value
     *     allowed object is
     *     {@link int }
     *     
     */
    public void setExpiredCount(int value) {
        this.expiredCount = value;
    }
    /**
     * Gets the value of the uploadedCount property.
     * 
     * @return
     *     possible object is
     *     {@link int }
     *     
     */
    public int getUploadedCount() {
        return uploadedCount;
    }

    /**
     * Sets the value of the uploadedCount property.
     * 
     * @param value
     *     allowed object is
     *     {@link int }
     *     
     */
    public void setUploadedCount(int value) {
        this.uploadedCount = value;
    }

    /**
     * Gets the value of the ignoreNullWrappers property.
     * 
     * @return
     *     possible object is
     *     {@link boolean }
     *     
     */
    public boolean getIgnoreNullWrappers() {
        return ignoreNullWrappers;
    }

    /**
     * Sets the value of the ignoreNullWrappers property.
     * 
     * @param value
     *     allowed object is
     *     {@link boolean }
     *     
     */
    public void setIgnoreNullWrappers(boolean value) {
        this.ignoreNullWrappers = value;
    }

    @Override 
    public int getPropertyCount() { 
        return 5;
    } 


    /**
     * Computes index shift for serialization methods in order to ignore null
     * wrappers during serialization so as not to produce lists with one empty
     * instance
     */
     protected int ignoreNullWrapperShift(int idx) {
        return idx;
    }

     /*
      * (non-Javadoc)
      * 
      * @see net.phonex.ksoap2.serialization.KvmSerializable#getProperty(int)
      */
    @Override
    public Object getProperty(int index) {
        index = this.ignoreNullWrapperShift(index);
        switch (index){
            case 0:
                return this.user;
            case 1:
                return this.readyCount;
            case 2:
                return this.usedCount;
            case 3:
                return this.expiredCount;
            case 4:
                return this.uploadedCount;
            default:
                return null;
        }
    }

     /*
      * (non-Javadoc)
      * 
      * @see net.phonex.ksoap2.serialization.KvmSerializable#getPropertyInfo(int,
      * java.util.Hashtable, net.phonex.ksoap2.serialization.PropertyInfo)
      */
    @Override
    public void getPropertyInfo(int index, Hashtable arg1, PropertyInfo info) {
        index = this.ignoreNullWrapperShift(index);
        switch (index){
            case 0:
                // type: java.lang.String
                info.name = "user";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 1:
                // type: int
                info.name = "readyCount";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.INTEGER_CLASS;
                break;
            case 2:
                // type: int
                info.name = "usedCount";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.INTEGER_CLASS;
                break;
            case 3:
                // type: int
                info.name = "expiredCount";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.INTEGER_CLASS;
                break;
            case 4:
                // type: int
                info.name = "uploadedCount";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.INTEGER_CLASS;
                break;
            default:
                break;
        }
    }

     /*
      * (non-Javadoc)
      * 
      * @see net.phonex.ksoap2.serialization.KvmSerializable#setProperty(int,
      * java.lang.Object)
      */
     @Override
     public void setProperty(int index, Object arg1) {
        index = this.ignoreNullWrapperShift(index);
        switch (index){
            case 0:
                // type: java.lang.String
                this.user = (java.lang.String)arg1;
                break;
            case 1:
                // type: int
                this.readyCount = Integer.parseInt(arg1.toString());
                break;
            case 2:
                // type: int
                this.usedCount = Integer.parseInt(arg1.toString());
                break;
            case 3:
                // type: int
                this.expiredCount = Integer.parseInt(arg1.toString());
                break;
            case 4:
                // type: int
                this.uploadedCount = Integer.parseInt(arg1.toString());
                break;
            default:
                return;
        }
    }

    @Override 
    public void register(SoapSerializationEnvelope soapEnvelope) { 
    } 

    @Override
    public String toString() {
        return "FtDHKeyUserStats{"+"user=" + this.user+", readyCount=" + this.readyCount+", usedCount=" + this.usedCount+", expiredCount=" + this.expiredCount+", uploadedCount=" + this.uploadedCount + '}';
    }
}

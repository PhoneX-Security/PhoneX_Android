package net.phonex.soap.entities;

import net.phonex.soap.SoapEnvelopeRegisterable;
import java.util.Hashtable;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.ksoap2.serialization.KvmSerializable;
import net.phonex.ksoap2.serialization.PropertyInfo;


public class WhitelistElement implements KvmSerializable, SoapEnvelopeRegisterable {

    public final static String NAMESPACE = "http://phoenix.com/hr/schemas";
    protected boolean ignoreNullWrappers = false;
    protected long userid;
    protected java.lang.String usersip;
    protected net.phonex.soap.entities.UserWhitelistStatus whitelistStatus;


    /**
     * Gets the value of the userid property.
     * 
     * @return
     *     possible object is
     *     {@link long }
     *     
     */
    public long getUserid() {
        return userid;
    }

    /**
     * Sets the value of the userid property.
     * 
     * @param value
     *     allowed object is
     *     {@link long }
     *     
     */
    public void setUserid(long value) {
        this.userid = value;
    }
    /**
     * Gets the value of the usersip property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getUsersip() {
        return usersip;
    }

    /**
     * Sets the value of the usersip property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setUsersip(java.lang.String value) {
        this.usersip = value;
    }
    /**
     * Gets the value of the whitelistStatus property.
     * 
     * @return
     *     possible object is
     *     {@link net.phonex.soap.entities.UserWhitelistStatus }
     *     
     */
    public net.phonex.soap.entities.UserWhitelistStatus getWhitelistStatus() {
        return whitelistStatus;
    }

    /**
     * Sets the value of the whitelistStatus property.
     * 
     * @param value
     *     allowed object is
     *     {@link net.phonex.soap.entities.UserWhitelistStatus }
     *     
     */
    public void setWhitelistStatus(net.phonex.soap.entities.UserWhitelistStatus value) {
        this.whitelistStatus = value;
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
        return 3;
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
                return this.userid;
            case 1:
                return this.usersip;
            case 2:
                return this.whitelistStatus == null ? null : this.whitelistStatus.toString().toLowerCase();
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
                // type: long
                info.name = "userid";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 1:
                // type: java.lang.String
                info.name = "usersip";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 2:
                // type: com.phoenix.soap.beans.UserWhitelistStatus
                info.name = "whitelistStatus";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
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
                // type: long
                this.userid = Long.parseLong(arg1.toString());
                break;
            case 1:
                // type: java.lang.String
                this.usersip = (java.lang.String)arg1;
                break;
            case 2:
                // type: com.phoenix.soap.beans.UserWhitelistStatus
                this.whitelistStatus = net.phonex.soap.entities.UserWhitelistStatus.fromValue((String) arg1);
                break;
            default:
                return;
        }
    }

    @Override 
    public void register(SoapSerializationEnvelope soapEnvelope) { 
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "whitelistStatus", net.phonex.soap.entities.UserWhitelistStatus.class); // same package
    } 

    @Override
    public String toString() {
        return "WhitelistElement{"+"userid=" + this.userid+", usersip=" + this.usersip+", whitelistStatus=" + this.whitelistStatus + '}';
    }
}

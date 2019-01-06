package net.phonex.soap.entities;

import net.phonex.soap.SoapEnvelopeRegisterable;
import java.util.Hashtable;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.ksoap2.serialization.KvmSerializable;
import net.phonex.ksoap2.serialization.PropertyInfo;
import net.phonex.util.DateUtils;
import net.phonex.util.Log;


public class FtDHKeyUserInfo implements KvmSerializable, SoapEnvelopeRegisterable {

    public final static String NAMESPACE = "http://phoenix.com/hr/schemas";
    protected boolean ignoreNullWrappers = false;
    protected java.lang.String user;
    protected java.lang.String nonce2;
    protected net.phonex.soap.entities.FtDHkeyState status;
    protected java.util.Calendar expires;
    protected java.util.Calendar created;
    protected java.lang.String creatorCertInfo;
    protected java.lang.String userCertInfo;


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
     * Gets the value of the nonce2 property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getNonce2() {
        return nonce2;
    }

    /**
     * Sets the value of the nonce2 property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setNonce2(java.lang.String value) {
        this.nonce2 = value;
    }
    /**
     * Gets the value of the status property.
     * 
     * @return
     *     possible object is
     *     {@link net.phonex.soap.entities.FtDHkeyState }
     *     
     */
    public net.phonex.soap.entities.FtDHkeyState getStatus() {
        return status;
    }

    /**
     * Sets the value of the status property.
     * 
     * @param value
     *     allowed object is
     *     {@link net.phonex.soap.entities.FtDHkeyState }
     *     
     */
    public void setStatus(net.phonex.soap.entities.FtDHkeyState value) {
        this.status = value;
    }
    /**
     * Gets the value of the expires property.
     * 
     * @return
     *     possible object is
     *     {@link java.util.Calendar }
     *     
     */
    public java.util.Calendar getExpires() {
        return expires;
    }

    /**
     * Sets the value of the expires property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.util.Calendar }
     *     
     */
    public void setExpires(java.util.Calendar value) {
        this.expires = value;
    }
    /**
     * Gets the value of the created property.
     * 
     * @return
     *     possible object is
     *     {@link java.util.Calendar }
     *     
     */
    public java.util.Calendar getCreated() {
        return created;
    }

    /**
     * Sets the value of the created property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.util.Calendar }
     *     
     */
    public void setCreated(java.util.Calendar value) {
        this.created = value;
    }
    /**
     * Gets the value of the creatorCertInfo property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getCreatorCertInfo() {
        return creatorCertInfo;
    }

    /**
     * Sets the value of the creatorCertInfo property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setCreatorCertInfo(java.lang.String value) {
        this.creatorCertInfo = value;
    }
    /**
     * Gets the value of the userCertInfo property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getUserCertInfo() {
        return userCertInfo;
    }

    /**
     * Sets the value of the userCertInfo property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setUserCertInfo(java.lang.String value) {
        this.userCertInfo = value;
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
        return 7;
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
                return this.nonce2;
            case 2:
                return this.status == null ? null : this.status.toString().toLowerCase();
            case 3:
                return this.expires;
            case 4:
                return this.created;
            case 5:
                return this.creatorCertInfo;
            case 6:
                return this.userCertInfo;
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
                // type: java.lang.String
                info.name = "nonce2";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 2:
                // type: com.phoenix.soap.beans.FtDHkeyState
                info.name = "status";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 3:
                // type: javax.xml.datatype.XMLGregorianCalendar
                info.name = "expires";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = java.util.Calendar.class;
                break;
            case 4:
                // type: javax.xml.datatype.XMLGregorianCalendar
                info.name = "created";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = java.util.Calendar.class;
                break;
            case 5:
                // type: java.lang.String
                info.name = "creatorCertInfo";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 6:
                // type: java.lang.String
                info.name = "userCertInfo";
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
                // type: java.lang.String
                this.user = (java.lang.String)arg1;
                break;
            case 1:
                // type: java.lang.String
                this.nonce2 = (java.lang.String)arg1;
                break;
            case 2:
                // type: com.phoenix.soap.beans.FtDHkeyState
                this.status = net.phonex.soap.entities.FtDHkeyState.fromValue((String) arg1);
                break;
            case 3:
                // type: javax.xml.datatype.XMLGregorianCalendar
                if (arg1 instanceof java.util.Calendar) {
                    this.expires = (java.util.Calendar) arg1;
                } else if (arg1 instanceof java.lang.String) {
                    final String str1_expires = (String) arg1; 
                    if (str1_expires==null || str1_expires.length()==0){ 
                        this.expires = null; 
                    } else { 
                        try { 
                            this.expires = DateUtils.stringToCalendar(str1_expires);
                        } catch (Exception e) { 
                            Log.e("FtDHKeyUserInfo", "Problem with date parsing", e);
                        } 
                    } 
                } 
                
                break;
            case 4:
                // type: javax.xml.datatype.XMLGregorianCalendar
                if (arg1 instanceof java.util.Calendar) {
                    this.created = (java.util.Calendar) arg1;
                } else if (arg1 instanceof java.lang.String) {
                    final String str1_created = (String) arg1; 
                    if (str1_created==null || str1_created.length()==0){ 
                        this.created = null; 
                    } else { 
                        try { 
                            this.created = DateUtils.stringToCalendar(str1_created);
                        } catch (Exception e) { 
                            Log.e("FtDHKeyUserInfo", "Problem with date parsing", e);
                        } 
                    } 
                } 
                
                break;
            case 5:
                // type: java.lang.String
                this.creatorCertInfo = (java.lang.String)arg1;
                break;
            case 6:
                // type: java.lang.String
                this.userCertInfo = (java.lang.String)arg1;
                break;
            default:
                return;
        }
    }

    @Override 
    public void register(SoapSerializationEnvelope soapEnvelope) { 
        new net.phonex.ksoap2.serialization.MarshalDate().register(soapEnvelope);
        new net.phonex.soap.marshallers.MarshalCalendar().register(soapEnvelope);
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "status", net.phonex.soap.entities.FtDHkeyState.class); // same package
    } 

    @Override
    public String toString() {
        return "FtDHKeyUserInfo{"+"user=" + this.user+", nonce2=" + this.nonce2+", status=" + this.status+", expires=" + this.expires+", created=" + this.created+", creatorCertInfo=" + this.creatorCertInfo+", userCertInfo=" + this.userCertInfo + '}';
    }
}

package net.phonex.soap.entities;

import net.phonex.util.Log;
import net.phonex.soap.SoapEnvelopeRegisterable;
import java.util.Hashtable;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.ksoap2.serialization.KvmSerializable;
import net.phonex.ksoap2.serialization.PropertyInfo;


public class CgroupUpdateRequest implements KvmSerializable, SoapEnvelopeRegisterable {

    public final static String NAMESPACE = "http://phoenix.com/hr/schemas";
    protected boolean ignoreNullWrappers = false;
    protected net.phonex.soap.entities.CgroupUpdateRequestList updatesList;
    protected java.lang.String targetUser;
    protected java.lang.Integer version;
    protected java.lang.Integer auxVersion;
    protected java.lang.String auxJSON;


    /**
     * Gets the value of the updatesList property.
     * 
     * @return
     *     possible object is
     *     {@link net.phonex.soap.entities.CgroupUpdateRequestList }
     *     
     */
    public net.phonex.soap.entities.CgroupUpdateRequestList getUpdatesList() {
        return updatesList;
    }

    /**
     * Sets the value of the updatesList property.
     * 
     * @param value
     *     allowed object is
     *     {@link net.phonex.soap.entities.CgroupUpdateRequestList }
     *     
     */
    public void setUpdatesList(net.phonex.soap.entities.CgroupUpdateRequestList value) {
        this.updatesList = value;
    }
    /**
     * Gets the value of the targetUser property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getTargetUser() {
        return targetUser;
    }

    /**
     * Sets the value of the targetUser property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setTargetUser(java.lang.String value) {
        this.targetUser = value;
    }
    /**
     * Gets the value of the version property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.Integer }
     *     
     */
    public java.lang.Integer getVersion() {
        return version;
    }

    /**
     * Sets the value of the version property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.Integer }
     *     
     */
    public void setVersion(java.lang.Integer value) {
        this.version = value;
    }
    /**
     * Gets the value of the auxVersion property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.Integer }
     *     
     */
    public java.lang.Integer getAuxVersion() {
        return auxVersion;
    }

    /**
     * Sets the value of the auxVersion property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.Integer }
     *     
     */
    public void setAuxVersion(java.lang.Integer value) {
        this.auxVersion = value;
    }
    /**
     * Gets the value of the auxJSON property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getAuxJSON() {
        return auxJSON;
    }

    /**
     * Sets the value of the auxJSON property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setAuxJSON(java.lang.String value) {
        this.auxJSON = value;
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
                return this.updatesList;
            case 1:
                return this.targetUser;
            case 2:
                return this.version;
            case 3:
                return this.auxVersion;
            case 4:
                return this.auxJSON;
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
                // type: com.phoenix.soap.beans.CgroupUpdateRequestList
                info.name = "updatesList";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = net.phonex.soap.entities.CgroupUpdateRequestList.class;
                break;
            case 1:
                // type: java.lang.String
                info.name = "targetUser";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 2:
                // type: java.lang.Integer
                info.name = "version";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.INTEGER_CLASS;
                break;
            case 3:
                // type: java.lang.Integer
                info.name = "auxVersion";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.INTEGER_CLASS;
                break;
            case 4:
                // type: java.lang.String
                info.name = "auxJSON";
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
                // type: com.phoenix.soap.beans.CgroupUpdateRequestList
                this.updatesList = (net.phonex.soap.entities.CgroupUpdateRequestList)arg1;
                break;
            case 1:
                // type: java.lang.String
                this.targetUser = (java.lang.String)arg1;
                break;
            case 2:
                // type: java.lang.Integer
               if (arg1 instanceof Integer) {
                   this.version = (java.lang.Integer) arg1;
               } else if (arg1 instanceof java.lang.String) {
                   final String tmpArg1 = (String) arg1;
                   try {
                       this.version = Integer.parseInt(tmpArg1);
                   } catch (Exception e) {
                       Log.ef("CgroupUpdateRequest", e, "Problem with version parsing, str=%s", tmpArg1);
                   }
               } else {
                   Log.e("CgroupUpdateRequest", "Problem with version parsing - unknown type");
               }
              
                break;
            case 3:
                // type: java.lang.Integer
               if (arg1 instanceof Integer) {
                   this.auxVersion = (java.lang.Integer) arg1;
               } else if (arg1 instanceof java.lang.String) {
                   final String tmpArg1 = (String) arg1;
                   try {
                       this.auxVersion = Integer.parseInt(tmpArg1);
                   } catch (Exception e) {
                       Log.ef("CgroupUpdateRequest", e, "Problem with auxVersion parsing, str=%s", tmpArg1);
                   }
               } else {
                   Log.e("CgroupUpdateRequest", "Problem with auxVersion parsing - unknown type");
               }
              
                break;
            case 4:
                // type: java.lang.String
                this.auxJSON = (java.lang.String)arg1;
                break;
            default:
                return;
        }
    }

    @Override 
    public void register(SoapSerializationEnvelope soapEnvelope) { 
        new net.phonex.soap.marshallers.MarshalInteger().register(soapEnvelope);
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "cgroupUpdateRequest", net.phonex.soap.entities.CgroupUpdateRequest.class); // root
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "updatesList", net.phonex.soap.entities.CgroupUpdateRequestList.class); // same package
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "CgroupUpdateRequestList", net.phonex.soap.entities.CgroupUpdateRequestList.class); // package i class
        new net.phonex.soap.entities.CgroupUpdateRequestList().register(soapEnvelope); // registerable 
    } 

    @Override
    public String toString() {
        return "CgroupUpdateRequest{"+"updatesList=" + this.updatesList+", targetUser=" + this.targetUser+", version=" + this.version+", auxVersion=" + this.auxVersion+", auxJSON=" + this.auxJSON + '}';
    }
}

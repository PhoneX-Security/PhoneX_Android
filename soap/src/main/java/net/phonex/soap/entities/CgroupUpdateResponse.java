package net.phonex.soap.entities;

import net.phonex.soap.SoapEnvelopeRegisterable;
import java.util.Hashtable;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.ksoap2.serialization.KvmSerializable;
import net.phonex.ksoap2.serialization.PropertyInfo;
import net.phonex.util.Log;


public class CgroupUpdateResponse implements KvmSerializable, SoapEnvelopeRegisterable {

    public final static String NAMESPACE = "http://phoenix.com/hr/schemas";
    protected boolean ignoreNullWrappers = false;
    protected int errCode;
    protected net.phonex.soap.entities.CgroupUpdateResultList resultList;
    protected java.lang.Integer version;
    protected java.lang.Integer auxVersion;
    protected java.lang.String auxJSON;


    /**
     * Gets the value of the errCode property.
     * 
     * @return
     *     possible object is
     *     {@link int }
     *     
     */
    public int getErrCode() {
        return errCode;
    }

    /**
     * Sets the value of the errCode property.
     * 
     * @param value
     *     allowed object is
     *     {@link int }
     *     
     */
    public void setErrCode(int value) {
        this.errCode = value;
    }
    /**
     * Gets the value of the resultList property.
     * 
     * @return
     *     possible object is
     *     {@link net.phonex.soap.entities.CgroupUpdateResultList }
     *     
     */
    public net.phonex.soap.entities.CgroupUpdateResultList getResultList() {
        return resultList;
    }

    /**
     * Sets the value of the resultList property.
     * 
     * @param value
     *     allowed object is
     *     {@link net.phonex.soap.entities.CgroupUpdateResultList }
     *     
     */
    public void setResultList(net.phonex.soap.entities.CgroupUpdateResultList value) {
        this.resultList = value;
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
                return this.errCode;
            case 1:
                return this.resultList;
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
                // type: int
                info.name = "errCode";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.INTEGER_CLASS;
                break;
            case 1:
                // type: com.phoenix.soap.beans.CgroupUpdateResultList
                info.name = "resultList";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = net.phonex.soap.entities.CgroupUpdateResultList.class;
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
                // type: int
                this.errCode = Integer.parseInt(arg1.toString());
                break;
            case 1:
                // type: com.phoenix.soap.beans.CgroupUpdateResultList
                this.resultList = (net.phonex.soap.entities.CgroupUpdateResultList)arg1;
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
                       Log.ef("CgroupUpdateResponse", e, "Problem with version parsing, str=%s", tmpArg1);
                   }
               } else {
                   Log.e("CgroupUpdateResponse", "Problem with version parsing - unknown type");
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
                       Log.ef("CgroupUpdateResponse", e, "Problem with auxVersion parsing, str=%s", tmpArg1);
                   }
               } else {
                   Log.e("CgroupUpdateResponse", "Problem with auxVersion parsing - unknown type");
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
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "cgroupUpdateResponse", net.phonex.soap.entities.CgroupUpdateResponse.class); // root
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "resultList", net.phonex.soap.entities.CgroupUpdateResultList.class); // same package
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "CgroupUpdateResultList", net.phonex.soap.entities.CgroupUpdateResultList.class); // package i class
        new net.phonex.soap.entities.CgroupUpdateResultList().register(soapEnvelope); // registerable 
    } 

    @Override
    public String toString() {
        return "CgroupUpdateResponse{"+"errCode=" + this.errCode+", resultList=" + this.resultList+", version=" + this.version+", auxVersion=" + this.auxVersion+", auxJSON=" + this.auxJSON + '}';
    }
}

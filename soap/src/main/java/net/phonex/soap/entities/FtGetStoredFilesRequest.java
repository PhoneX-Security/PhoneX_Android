package net.phonex.soap.entities;

import net.phonex.soap.SoapEnvelopeRegisterable;
import java.util.Hashtable;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.ksoap2.serialization.KvmSerializable;
import net.phonex.ksoap2.serialization.PropertyInfo;
import net.phonex.util.Log;


public class FtGetStoredFilesRequest implements KvmSerializable, SoapEnvelopeRegisterable {

    public final static String NAMESPACE = "http://phoenix.com/hr/schemas";
    protected boolean ignoreNullWrappers = false;
    protected boolean getAll;
    protected net.phonex.soap.entities.SipList users;
    protected net.phonex.soap.entities.FtNonceList nonceList;
    protected java.lang.Integer version;
    protected java.lang.Integer auxVersion;
    protected java.lang.String auxJSON;


    /**
     * Gets the value of the getAll property.
     * 
     * @return
     *     possible object is
     *     {@link boolean }
     *     
     */
    public boolean getGetAll() {
        return getAll;
    }

    /**
     * Sets the value of the getAll property.
     * 
     * @param value
     *     allowed object is
     *     {@link boolean }
     *     
     */
    public void setGetAll(boolean value) {
        this.getAll = value;
    }
    /**
     * Gets the value of the users property.
     * 
     * @return
     *     possible object is
     *     {@link net.phonex.soap.entities.SipList }
     *     
     */
    public net.phonex.soap.entities.SipList getUsers() {
        return users;
    }

    /**
     * Sets the value of the users property.
     * 
     * @param value
     *     allowed object is
     *     {@link net.phonex.soap.entities.SipList }
     *     
     */
    public void setUsers(net.phonex.soap.entities.SipList value) {
        this.users = value;
    }
    /**
     * Gets the value of the nonceList property.
     * 
     * @return
     *     possible object is
     *     {@link net.phonex.soap.entities.FtNonceList }
     *     
     */
    public net.phonex.soap.entities.FtNonceList getNonceList() {
        return nonceList;
    }

    /**
     * Sets the value of the nonceList property.
     * 
     * @param value
     *     allowed object is
     *     {@link net.phonex.soap.entities.FtNonceList }
     *     
     */
    public void setNonceList(net.phonex.soap.entities.FtNonceList value) {
        this.nonceList = value;
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
        return 6;
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
                return this.getAll;
            case 1:
                return this.users;
            case 2:
                return this.nonceList;
            case 3:
                return this.version;
            case 4:
                return this.auxVersion;
            case 5:
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
                // type: boolean
                info.name = "getAll";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.BOOLEAN_CLASS;
                break;
            case 1:
                // type: com.phoenix.soap.beans.SipList
                info.name = "users";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = net.phonex.soap.entities.SipList.class;
                break;
            case 2:
                // type: com.phoenix.soap.beans.FtNonceList
                info.name = "nonceList";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = net.phonex.soap.entities.FtNonceList.class;
                break;
            case 3:
                // type: java.lang.Integer
                info.name = "version";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.INTEGER_CLASS;
                break;
            case 4:
                // type: java.lang.Integer
                info.name = "auxVersion";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.INTEGER_CLASS;
                break;
            case 5:
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
                // type: boolean
                this.getAll = Boolean.parseBoolean(arg1.toString());
                break;
            case 1:
                // type: com.phoenix.soap.beans.SipList
                this.users = (net.phonex.soap.entities.SipList)arg1;
                break;
            case 2:
                // type: com.phoenix.soap.beans.FtNonceList
                this.nonceList = (net.phonex.soap.entities.FtNonceList)arg1;
                break;
            case 3:
                // type: java.lang.Integer
               if (arg1 instanceof Integer) {
                   this.version = (java.lang.Integer) arg1;
               } else if (arg1 instanceof java.lang.String) {
                   final String tmpArg1 = (String) arg1;
                   try {
                       this.version = Integer.parseInt(tmpArg1);
                   } catch (Exception e) {
                       Log.ef("FtGetStoredFilesRequest", e, "Problem with version parsing, str=%s", tmpArg1);
                   }
               } else {
                   Log.e("FtGetStoredFilesRequest", "Problem with version parsing - unknown type");
               }
              
                break;
            case 4:
                // type: java.lang.Integer
               if (arg1 instanceof Integer) {
                   this.auxVersion = (java.lang.Integer) arg1;
               } else if (arg1 instanceof java.lang.String) {
                   final String tmpArg1 = (String) arg1;
                   try {
                       this.auxVersion = Integer.parseInt(tmpArg1);
                   } catch (Exception e) {
                       Log.ef("FtGetStoredFilesRequest", e, "Problem with auxVersion parsing, str=%s", tmpArg1);
                   }
               } else {
                   Log.e("FtGetStoredFilesRequest", "Problem with auxVersion parsing - unknown type");
               }
              
                break;
            case 5:
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
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "ftGetStoredFilesRequest", net.phonex.soap.entities.FtGetStoredFilesRequest.class); // root
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "nonceList", net.phonex.soap.entities.FtNonceList.class); // same package
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "users", net.phonex.soap.entities.SipList.class); // same package
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "SipList", net.phonex.soap.entities.SipList.class); // package i class
        new net.phonex.soap.entities.SipList().register(soapEnvelope); // registerable 
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "FtNonceList", net.phonex.soap.entities.FtNonceList.class); // package i class
        new net.phonex.soap.entities.FtNonceList().register(soapEnvelope); // registerable 
    } 

    @Override
    public String toString() {
        return "FtGetStoredFilesRequest{"+"getAll=" + this.getAll+", users=" + this.users+", nonceList=" + this.nonceList+", version=" + this.version+", auxVersion=" + this.auxVersion+", auxJSON=" + this.auxJSON + '}';
    }
}

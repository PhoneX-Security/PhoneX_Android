package net.phonex.soap.entities;

import net.phonex.soap.SoapEnvelopeRegisterable;
import java.util.Hashtable;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.ksoap2.serialization.KvmSerializable;
import net.phonex.ksoap2.serialization.PropertyInfo;
import net.phonex.util.DateUtils;
import net.phonex.util.Log;


public class PairingRequestElement implements KvmSerializable, SoapEnvelopeRegisterable {

    public final static String NAMESPACE = "http://phoenix.com/hr/schemas";
    protected boolean ignoreNullWrappers = false;
    protected long id;
    protected java.lang.String owner;
    protected long tstamp;
    protected java.lang.String fromUser;
    protected java.lang.String fromUserResource;
    protected java.util.Calendar dateLastChange;
    protected java.lang.String fromUserAux;
    protected java.lang.String requestMessage;
    protected java.lang.String requestAux;
    protected net.phonex.soap.entities.PairingRequestResolutionEnum resolution;
    protected java.lang.String resolutionResource;
    protected java.lang.Long resolutionTstamp;
    protected java.lang.String resolutionMessage;
    protected java.lang.String resolutionAux;
    protected java.lang.Integer version;
    protected java.lang.Integer auxVersion;
    protected java.lang.String auxJSON;


    /**
     * Gets the value of the id property.
     * 
     * @return
     *     possible object is
     *     {@link long }
     *     
     */
    public long getId() {
        return id;
    }

    /**
     * Sets the value of the id property.
     * 
     * @param value
     *     allowed object is
     *     {@link long }
     *     
     */
    public void setId(long value) {
        this.id = value;
    }
    /**
     * Gets the value of the owner property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getOwner() {
        return owner;
    }

    /**
     * Sets the value of the owner property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setOwner(java.lang.String value) {
        this.owner = value;
    }
    /**
     * Gets the value of the tstamp property.
     * 
     * @return
     *     possible object is
     *     {@link long }
     *     
     */
    public long getTstamp() {
        return tstamp;
    }

    /**
     * Sets the value of the tstamp property.
     * 
     * @param value
     *     allowed object is
     *     {@link long }
     *     
     */
    public void setTstamp(long value) {
        this.tstamp = value;
    }
    /**
     * Gets the value of the fromUser property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getFromUser() {
        return fromUser;
    }

    /**
     * Sets the value of the fromUser property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setFromUser(java.lang.String value) {
        this.fromUser = value;
    }
    /**
     * Gets the value of the fromUserResource property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getFromUserResource() {
        return fromUserResource;
    }

    /**
     * Sets the value of the fromUserResource property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setFromUserResource(java.lang.String value) {
        this.fromUserResource = value;
    }
    /**
     * Gets the value of the dateLastChange property.
     * 
     * @return
     *     possible object is
     *     {@link java.util.Calendar }
     *     
     */
    public java.util.Calendar getDateLastChange() {
        return dateLastChange;
    }

    /**
     * Sets the value of the dateLastChange property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.util.Calendar }
     *     
     */
    public void setDateLastChange(java.util.Calendar value) {
        this.dateLastChange = value;
    }
    /**
     * Gets the value of the fromUserAux property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getFromUserAux() {
        return fromUserAux;
    }

    /**
     * Sets the value of the fromUserAux property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setFromUserAux(java.lang.String value) {
        this.fromUserAux = value;
    }
    /**
     * Gets the value of the requestMessage property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getRequestMessage() {
        return requestMessage;
    }

    /**
     * Sets the value of the requestMessage property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setRequestMessage(java.lang.String value) {
        this.requestMessage = value;
    }
    /**
     * Gets the value of the requestAux property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getRequestAux() {
        return requestAux;
    }

    /**
     * Sets the value of the requestAux property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setRequestAux(java.lang.String value) {
        this.requestAux = value;
    }
    /**
     * Gets the value of the resolution property.
     * 
     * @return
     *     possible object is
     *     {@link net.phonex.soap.entities.PairingRequestResolutionEnum }
     *     
     */
    public net.phonex.soap.entities.PairingRequestResolutionEnum getResolution() {
        return resolution;
    }

    /**
     * Sets the value of the resolution property.
     * 
     * @param value
     *     allowed object is
     *     {@link net.phonex.soap.entities.PairingRequestResolutionEnum }
     *     
     */
    public void setResolution(net.phonex.soap.entities.PairingRequestResolutionEnum value) {
        this.resolution = value;
    }
    /**
     * Gets the value of the resolutionResource property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getResolutionResource() {
        return resolutionResource;
    }

    /**
     * Sets the value of the resolutionResource property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setResolutionResource(java.lang.String value) {
        this.resolutionResource = value;
    }
    /**
     * Gets the value of the resolutionTstamp property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.Long }
     *     
     */
    public java.lang.Long getResolutionTstamp() {
        return resolutionTstamp;
    }

    /**
     * Sets the value of the resolutionTstamp property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.Long }
     *     
     */
    public void setResolutionTstamp(java.lang.Long value) {
        this.resolutionTstamp = value;
    }
    /**
     * Gets the value of the resolutionMessage property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getResolutionMessage() {
        return resolutionMessage;
    }

    /**
     * Sets the value of the resolutionMessage property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setResolutionMessage(java.lang.String value) {
        this.resolutionMessage = value;
    }
    /**
     * Gets the value of the resolutionAux property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getResolutionAux() {
        return resolutionAux;
    }

    /**
     * Sets the value of the resolutionAux property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setResolutionAux(java.lang.String value) {
        this.resolutionAux = value;
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
        return 17;
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
                return this.id;
            case 1:
                return this.owner;
            case 2:
                return this.tstamp;
            case 3:
                return this.fromUser;
            case 4:
                return this.fromUserResource;
            case 5:
                return this.dateLastChange;
            case 6:
                return this.fromUserAux;
            case 7:
                return this.requestMessage;
            case 8:
                return this.requestAux;
            case 9:
                return this.resolution == null ? null : this.resolution.toString().toLowerCase();
            case 10:
                return this.resolutionResource;
            case 11:
                return this.resolutionTstamp;
            case 12:
                return this.resolutionMessage;
            case 13:
                return this.resolutionAux;
            case 14:
                return this.version;
            case 15:
                return this.auxVersion;
            case 16:
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
                // type: long
                info.name = "id";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 1:
                // type: java.lang.String
                info.name = "owner";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 2:
                // type: long
                info.name = "tstamp";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 3:
                // type: java.lang.String
                info.name = "fromUser";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 4:
                // type: java.lang.String
                info.name = "fromUserResource";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 5:
                // type: javax.xml.datatype.XMLGregorianCalendar
                info.name = "dateLastChange";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = java.util.Calendar.class;
                break;
            case 6:
                // type: java.lang.String
                info.name = "fromUserAux";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 7:
                // type: java.lang.String
                info.name = "requestMessage";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 8:
                // type: java.lang.String
                info.name = "requestAux";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 9:
                // type: com.phoenix.soap.beans.PairingRequestResolutionEnum
                info.name = "resolution";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 10:
                // type: java.lang.String
                info.name = "resolutionResource";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 11:
                // type: java.lang.Long
                info.name = "resolutionTstamp";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 12:
                // type: java.lang.String
                info.name = "resolutionMessage";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 13:
                // type: java.lang.String
                info.name = "resolutionAux";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 14:
                // type: java.lang.Integer
                info.name = "version";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.INTEGER_CLASS;
                break;
            case 15:
                // type: java.lang.Integer
                info.name = "auxVersion";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.INTEGER_CLASS;
                break;
            case 16:
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
                // type: long
                this.id = Long.parseLong(arg1.toString());
                break;
            case 1:
                // type: java.lang.String
                this.owner = (java.lang.String)arg1;
                break;
            case 2:
                // type: long
                this.tstamp = Long.parseLong(arg1.toString());
                break;
            case 3:
                // type: java.lang.String
                this.fromUser = (java.lang.String)arg1;
                break;
            case 4:
                // type: java.lang.String
                this.fromUserResource = (java.lang.String)arg1;
                break;
            case 5:
                // type: javax.xml.datatype.XMLGregorianCalendar
                if (arg1 instanceof java.util.Calendar) {
                    this.dateLastChange = (java.util.Calendar) arg1;
                } else if (arg1 instanceof java.lang.String) {
                    final String str1_dateLastChange = (String) arg1; 
                    if (str1_dateLastChange==null || str1_dateLastChange.length()==0){ 
                        this.dateLastChange = null; 
                    } else { 
                        try { 
                            this.dateLastChange = DateUtils.stringToCalendar(str1_dateLastChange);
                        } catch (Exception e) { 
                            Log.e("PairingRequestElement", "Problem with date parsing", e);
                        } 
                    } 
                } 
                
                break;
            case 6:
                // type: java.lang.String
                this.fromUserAux = (java.lang.String)arg1;
                break;
            case 7:
                // type: java.lang.String
                this.requestMessage = (java.lang.String)arg1;
                break;
            case 8:
                // type: java.lang.String
                this.requestAux = (java.lang.String)arg1;
                break;
            case 9:
                // type: com.phoenix.soap.beans.PairingRequestResolutionEnum
                this.resolution = net.phonex.soap.entities.PairingRequestResolutionEnum.fromValue((String) arg1);
                break;
            case 10:
                // type: java.lang.String
                this.resolutionResource = (java.lang.String)arg1;
                break;
            case 11:
                // type: java.lang.Long
               if (arg1 instanceof Long) {
                   this.resolutionTstamp = (java.lang.Long) arg1;
               } else if (arg1 instanceof java.lang.String) {
                   final String tmpArg1 = (String) arg1;
                   try {
                       this.resolutionTstamp = Long.parseLong(tmpArg1);
                   } catch (Exception e) {
                       Log.ef("PairingRequestElement", e, "Problem with resolutionTstamp parsing, str=%s", tmpArg1);
                   }
               } else {
                   Log.e("PairingRequestElement", "Problem with resolutionTstamp parsing - unknown type");
               }
              
                break;
            case 12:
                // type: java.lang.String
                this.resolutionMessage = (java.lang.String)arg1;
                break;
            case 13:
                // type: java.lang.String
                this.resolutionAux = (java.lang.String)arg1;
                break;
            case 14:
                // type: java.lang.Integer
               if (arg1 instanceof Integer) {
                   this.version = (java.lang.Integer) arg1;
               } else if (arg1 instanceof java.lang.String) {
                   final String tmpArg1 = (String) arg1;
                   try {
                       this.version = Integer.parseInt(tmpArg1);
                   } catch (Exception e) {
                       Log.ef("PairingRequestElement", e, "Problem with version parsing, str=%s", tmpArg1);
                   }
               } else {
                   Log.e("PairingRequestElement", "Problem with version parsing - unknown type");
               }
              
                break;
            case 15:
                // type: java.lang.Integer
               if (arg1 instanceof Integer) {
                   this.auxVersion = (java.lang.Integer) arg1;
               } else if (arg1 instanceof java.lang.String) {
                   final String tmpArg1 = (String) arg1;
                   try {
                       this.auxVersion = Integer.parseInt(tmpArg1);
                   } catch (Exception e) {
                       Log.ef("PairingRequestElement", e, "Problem with auxVersion parsing, str=%s", tmpArg1);
                   }
               } else {
                   Log.e("PairingRequestElement", "Problem with auxVersion parsing - unknown type");
               }
              
                break;
            case 16:
                // type: java.lang.String
                this.auxJSON = (java.lang.String)arg1;
                break;
            default:
                return;
        }
    }

    @Override 
    public void register(SoapSerializationEnvelope soapEnvelope) { 
        new net.phonex.ksoap2.serialization.MarshalDate().register(soapEnvelope);
        new net.phonex.soap.marshallers.MarshalCalendar().register(soapEnvelope);
        new net.phonex.soap.marshallers.MarshalInteger().register(soapEnvelope);
        new net.phonex.soap.marshallers.MarshalLong().register(soapEnvelope);
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "resolution", net.phonex.soap.entities.PairingRequestResolutionEnum.class); // same package
    } 

    @Override
    public String toString() {
        return "PairingRequestElement{"+"id=" + this.id+", owner=" + this.owner+", tstamp=" + this.tstamp+", fromUser=" + this.fromUser+", fromUserResource=" + this.fromUserResource+", dateLastChange=" + this.dateLastChange+", fromUserAux=" + this.fromUserAux+", requestMessage=" + this.requestMessage+", requestAux=" + this.requestAux+", resolution=" + this.resolution+", resolutionResource=" + this.resolutionResource+", resolutionTstamp=" + this.resolutionTstamp+", resolutionMessage=" + this.resolutionMessage+", resolutionAux=" + this.resolutionAux+", version=" + this.version+", auxVersion=" + this.auxVersion+", auxJSON=" + this.auxJSON + '}';
    }
}

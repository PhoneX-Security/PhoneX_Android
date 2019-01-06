package net.phonex.soap.entities;

import net.phonex.ksoap2.serialization.KvmSerializable;
import net.phonex.ksoap2.serialization.PropertyInfo;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.soap.SoapEnvelopeRegisterable;
import net.phonex.util.DateUtils;
import net.phonex.util.Log;

import java.util.Hashtable;


public class ClistElementV2 implements KvmSerializable, SoapEnvelopeRegisterable {

    public final static String NAMESPACE = "http://phoenix.com/hr/schemas";
    protected boolean ignoreNullWrappers = false;
    protected java.lang.String owner;
    protected long userid;
    protected java.lang.String alias;
    protected java.lang.String usersip;
    protected net.phonex.soap.entities.UserPresenceStatus presenceStatus;
    protected net.phonex.soap.entities.EnabledDisabled contactlistStatus;
    protected net.phonex.soap.entities.UserWhitelistStatus whitelistStatus;
    protected boolean hideInContactList;
    protected java.lang.String displayName;
    protected java.util.Calendar dateLastChange;
    protected java.lang.String auxData;
    protected java.lang.Long primaryGroup;
    protected java.lang.Integer version;
    protected java.lang.Integer auxVersion;
    protected java.lang.String auxJSON;


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
     * Gets the value of the alias property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getAlias() {
        return alias;
    }

    /**
     * Sets the value of the alias property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setAlias(java.lang.String value) {
        this.alias = value;
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
     * Gets the value of the presenceStatus property.
     * 
     * @return
     *     possible object is
     *     {@link net.phonex.soap.entities.UserPresenceStatus }
     *     
     */
    public net.phonex.soap.entities.UserPresenceStatus getPresenceStatus() {
        return presenceStatus;
    }

    /**
     * Sets the value of the presenceStatus property.
     * 
     * @param value
     *     allowed object is
     *     {@link net.phonex.soap.entities.UserPresenceStatus }
     *     
     */
    public void setPresenceStatus(net.phonex.soap.entities.UserPresenceStatus value) {
        this.presenceStatus = value;
    }
    /**
     * Gets the value of the contactlistStatus property.
     * 
     * @return
     *     possible object is
     *     {@link net.phonex.soap.entities.EnabledDisabled }
     *     
     */
    public net.phonex.soap.entities.EnabledDisabled getContactlistStatus() {
        return contactlistStatus;
    }

    /**
     * Sets the value of the contactlistStatus property.
     * 
     * @param value
     *     allowed object is
     *     {@link net.phonex.soap.entities.EnabledDisabled }
     *     
     */
    public void setContactlistStatus(net.phonex.soap.entities.EnabledDisabled value) {
        this.contactlistStatus = value;
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
     * Gets the value of the hideInContactList property.
     * 
     * @return
     *     possible object is
     *     {@link boolean }
     *     
     */
    public boolean getHideInContactList() {
        return hideInContactList;
    }

    /**
     * Sets the value of the hideInContactList property.
     * 
     * @param value
     *     allowed object is
     *     {@link boolean }
     *     
     */
    public void setHideInContactList(boolean value) {
        this.hideInContactList = value;
    }
    /**
     * Gets the value of the displayName property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getDisplayName() {
        return displayName;
    }

    /**
     * Sets the value of the displayName property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setDisplayName(java.lang.String value) {
        this.displayName = value;
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
     * Gets the value of the auxData property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getAuxData() {
        return auxData;
    }

    /**
     * Sets the value of the auxData property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setAuxData(java.lang.String value) {
        this.auxData = value;
    }
    /**
     * Gets the value of the primaryGroup property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.Long }
     *     
     */
    public java.lang.Long getPrimaryGroup() {
        return primaryGroup;
    }

    /**
     * Sets the value of the primaryGroup property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.Long }
     *     
     */
    public void setPrimaryGroup(java.lang.Long value) {
        this.primaryGroup = value;
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
        return 15;
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
                return this.owner;
            case 1:
                return this.userid;
            case 2:
                return this.alias;
            case 3:
                return this.usersip;
            case 4:
                return this.presenceStatus == null ? null : this.presenceStatus.toString().toLowerCase();
            case 5:
                return this.contactlistStatus == null ? null : this.contactlistStatus.toString().toLowerCase();
            case 6:
                return this.whitelistStatus == null ? null : this.whitelistStatus.toString().toLowerCase();
            case 7:
                return this.hideInContactList;
            case 8:
                return this.displayName;
            case 9:
                return this.dateLastChange;
            case 10:
                return this.auxData;
            case 11:
                return this.primaryGroup;
            case 12:
                return this.version;
            case 13:
                return this.auxVersion;
            case 14:
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
                // type: java.lang.String
                info.name = "owner";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 1:
                // type: long
                info.name = "userid";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 2:
                // type: java.lang.String
                info.name = "alias";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 3:
                // type: java.lang.String
                info.name = "usersip";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 4:
                // type: com.phoenix.soap.beans.UserPresenceStatus
                info.name = "presenceStatus";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 5:
                // type: com.phoenix.soap.beans.EnabledDisabled
                info.name = "contactlistStatus";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 6:
                // type: com.phoenix.soap.beans.UserWhitelistStatus
                info.name = "whitelistStatus";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 7:
                // type: boolean
                info.name = "hideInContactList";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.BOOLEAN_CLASS;
                break;
            case 8:
                // type: java.lang.String
                info.name = "displayName";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 9:
                // type: javax.xml.datatype.XMLGregorianCalendar
                info.name = "dateLastChange";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = java.util.Calendar.class;
                break;
            case 10:
                // type: java.lang.String
                info.name = "auxData";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 11:
                // type: java.lang.Long
                info.name = "primaryGroup";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 12:
                // type: java.lang.Integer
                info.name = "version";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.INTEGER_CLASS;
                break;
            case 13:
                // type: java.lang.Integer
                info.name = "auxVersion";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.INTEGER_CLASS;
                break;
            case 14:
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
                // type: java.lang.String
                this.owner = (java.lang.String)arg1;
                break;
            case 1:
                // type: long
                this.userid = Long.parseLong(arg1.toString());
                break;
            case 2:
                // type: java.lang.String
                this.alias = (java.lang.String)arg1;
                break;
            case 3:
                // type: java.lang.String
                this.usersip = (java.lang.String)arg1;
                break;
            case 4:
                // type: com.phoenix.soap.beans.UserPresenceStatus
                this.presenceStatus = net.phonex.soap.entities.UserPresenceStatus.fromValue((String) arg1);
                break;
            case 5:
                // type: com.phoenix.soap.beans.EnabledDisabled
                this.contactlistStatus = net.phonex.soap.entities.EnabledDisabled.fromValue((String) arg1);
                break;
            case 6:
                // type: com.phoenix.soap.beans.UserWhitelistStatus
                this.whitelistStatus = net.phonex.soap.entities.UserWhitelistStatus.fromValue((String) arg1);
                break;
            case 7:
                // type: boolean
                this.hideInContactList = Boolean.parseBoolean(arg1.toString());
                break;
            case 8:
                // type: java.lang.String
                this.displayName = (java.lang.String)arg1;
                break;
            case 9:
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
                            Log.e("ClistElementV2", "Problem with date parsing", e);
                        } 
                    } 
                } 
                
                break;
            case 10:
                // type: java.lang.String
                this.auxData = (java.lang.String)arg1;
                break;
            case 11:
                // type: java.lang.Long
               if (arg1 instanceof Long) {
                   this.primaryGroup = (java.lang.Long) arg1;
               } else if (arg1 instanceof java.lang.String) {
                   final String tmpArg1 = (String) arg1;
                   try {
                       this.primaryGroup = Long.parseLong(tmpArg1);
                   } catch (Exception e) {
                       Log.ef("ClistElementV2", e, "Problem with primaryGroup parsing, str=%s", tmpArg1);
                   }
               } else {
                   Log.e("ClistElementV2", "Problem with primaryGroup parsing - unknown type");
               }
              
                break;
            case 12:
                // type: java.lang.Integer
               if (arg1 instanceof Integer) {
                   this.version = (java.lang.Integer) arg1;
               } else if (arg1 instanceof java.lang.String) {
                   final String tmpArg1 = (String) arg1;
                   try {
                       this.version = Integer.parseInt(tmpArg1);
                   } catch (Exception e) {
                       Log.ef("ClistElementV2", e, "Problem with version parsing, str=%s", tmpArg1);
                   }
               } else {
                   Log.e("ClistElementV2", "Problem with version parsing - unknown type");
               }
              
                break;
            case 13:
                // type: java.lang.Integer
               if (arg1 instanceof Integer) {
                   this.auxVersion = (java.lang.Integer) arg1;
               } else if (arg1 instanceof java.lang.String) {
                   final String tmpArg1 = (String) arg1;
                   try {
                       this.auxVersion = Integer.parseInt(tmpArg1);
                   } catch (Exception e) {
                       Log.ef("ClistElementV2", e, "Problem with auxVersion parsing, str=%s", tmpArg1);
                   }
               } else {
                   Log.e("ClistElementV2", "Problem with auxVersion parsing - unknown type");
               }
              
                break;
            case 14:
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
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "whitelistStatus", net.phonex.soap.entities.UserWhitelistStatus.class); // same package
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "contactlistStatus", net.phonex.soap.entities.EnabledDisabled.class); // same package
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "presenceStatus", net.phonex.soap.entities.UserPresenceStatus.class); // same package
    } 

    @Override
    public String toString() {
        return "ClistElementV2{"+"owner=" + this.owner+", userid=" + this.userid+", alias=" + this.alias+", usersip=" + this.usersip+", presenceStatus=" + this.presenceStatus+", contactlistStatus=" + this.contactlistStatus+", whitelistStatus=" + this.whitelistStatus+", hideInContactList=" + this.hideInContactList+", displayName=" + this.displayName+", dateLastChange=" + this.dateLastChange+", auxData=" + this.auxData+", primaryGroup=" + this.primaryGroup+", version=" + this.version+", auxVersion=" + this.auxVersion+", auxJSON=" + this.auxJSON + '}';
    }
}

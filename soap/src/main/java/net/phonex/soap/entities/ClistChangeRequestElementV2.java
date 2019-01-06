package net.phonex.soap.entities;

import net.phonex.soap.SoapEnvelopeRegisterable;
import java.util.Hashtable;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.ksoap2.serialization.KvmSerializable;
import net.phonex.ksoap2.serialization.PropertyInfo;
import net.phonex.util.Log;


public class ClistChangeRequestElementV2 implements KvmSerializable, SoapEnvelopeRegisterable {

    public final static String NAMESPACE = "http://phoenix.com/hr/schemas";
    protected boolean ignoreNullWrappers = false;
    protected net.phonex.soap.entities.UserIdentifier user;
    protected net.phonex.soap.entities.ContactlistAction action;
    protected java.lang.String displayName;
    protected java.lang.String auxData;
    protected net.phonex.soap.entities.WhitelistAction whitelistAction;
    protected java.lang.Integer primaryGroup;
    protected java.lang.String targetUser;
    protected java.lang.Boolean managePairingRequests;
    protected java.lang.Integer version;
    protected java.lang.Integer auxVersion;
    protected java.lang.String auxJSON;


    /**
     * Gets the value of the user property.
     * 
     * @return
     *     possible object is
     *     {@link net.phonex.soap.entities.UserIdentifier }
     *     
     */
    public net.phonex.soap.entities.UserIdentifier getUser() {
        return user;
    }

    /**
     * Sets the value of the user property.
     * 
     * @param value
     *     allowed object is
     *     {@link net.phonex.soap.entities.UserIdentifier }
     *     
     */
    public void setUser(net.phonex.soap.entities.UserIdentifier value) {
        this.user = value;
    }
    /**
     * Gets the value of the action property.
     * 
     * @return
     *     possible object is
     *     {@link net.phonex.soap.entities.ContactlistAction }
     *     
     */
    public net.phonex.soap.entities.ContactlistAction getAction() {
        return action;
    }

    /**
     * Sets the value of the action property.
     * 
     * @param value
     *     allowed object is
     *     {@link net.phonex.soap.entities.ContactlistAction }
     *     
     */
    public void setAction(net.phonex.soap.entities.ContactlistAction value) {
        this.action = value;
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
     * Gets the value of the whitelistAction property.
     * 
     * @return
     *     possible object is
     *     {@link net.phonex.soap.entities.WhitelistAction }
     *     
     */
    public net.phonex.soap.entities.WhitelistAction getWhitelistAction() {
        return whitelistAction;
    }

    /**
     * Sets the value of the whitelistAction property.
     * 
     * @param value
     *     allowed object is
     *     {@link net.phonex.soap.entities.WhitelistAction }
     *     
     */
    public void setWhitelistAction(net.phonex.soap.entities.WhitelistAction value) {
        this.whitelistAction = value;
    }
    /**
     * Gets the value of the primaryGroup property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.Integer }
     *     
     */
    public java.lang.Integer getPrimaryGroup() {
        return primaryGroup;
    }

    /**
     * Sets the value of the primaryGroup property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.Integer }
     *     
     */
    public void setPrimaryGroup(java.lang.Integer value) {
        this.primaryGroup = value;
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
     * Gets the value of the managePairingRequests property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.Boolean }
     *     
     */
    public java.lang.Boolean getManagePairingRequests() {
        return managePairingRequests;
    }

    /**
     * Sets the value of the managePairingRequests property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.Boolean }
     *     
     */
    public void setManagePairingRequests(java.lang.Boolean value) {
        this.managePairingRequests = value;
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
        return 11;
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
                return this.action == null ? null : this.action.toString().toLowerCase();
            case 2:
                return this.displayName;
            case 3:
                return this.auxData;
            case 4:
                return this.whitelistAction == null ? null : this.whitelistAction.toString().toLowerCase();
            case 5:
                return this.primaryGroup;
            case 6:
                return this.targetUser;
            case 7:
                return this.managePairingRequests;
            case 8:
                return this.version;
            case 9:
                return this.auxVersion;
            case 10:
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
                // type: com.phoenix.soap.beans.UserIdentifier
                info.name = "user";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = net.phonex.soap.entities.UserIdentifier.class;
                break;
            case 1:
                // type: com.phoenix.soap.beans.ContactlistAction
                info.name = "action";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 2:
                // type: java.lang.String
                info.name = "displayName";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 3:
                // type: java.lang.String
                info.name = "auxData";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 4:
                // type: com.phoenix.soap.beans.WhitelistAction
                info.name = "whitelistAction";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 5:
                // type: java.lang.Integer
                info.name = "primaryGroup";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.INTEGER_CLASS;
                break;
            case 6:
                // type: java.lang.String
                info.name = "targetUser";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 7:
                // type: java.lang.Boolean
                info.name = "managePairingRequests";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 8:
                // type: java.lang.Integer
                info.name = "version";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.INTEGER_CLASS;
                break;
            case 9:
                // type: java.lang.Integer
                info.name = "auxVersion";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.INTEGER_CLASS;
                break;
            case 10:
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
                // type: com.phoenix.soap.beans.UserIdentifier
                this.user = (net.phonex.soap.entities.UserIdentifier)arg1;
                break;
            case 1:
                // type: com.phoenix.soap.beans.ContactlistAction
                this.action = net.phonex.soap.entities.ContactlistAction.fromValue((String) arg1);
                break;
            case 2:
                // type: java.lang.String
                this.displayName = (java.lang.String)arg1;
                break;
            case 3:
                // type: java.lang.String
                this.auxData = (java.lang.String)arg1;
                break;
            case 4:
                // type: com.phoenix.soap.beans.WhitelistAction
                this.whitelistAction = net.phonex.soap.entities.WhitelistAction.fromValue((String) arg1);
                break;
            case 5:
                // type: java.lang.Integer
               if (arg1 instanceof Integer) {
                   this.primaryGroup = (java.lang.Integer) arg1;
               } else if (arg1 instanceof java.lang.String) {
                   final String tmpArg1 = (String) arg1;
                   try {
                       this.primaryGroup = Integer.parseInt(tmpArg1);
                   } catch (Exception e) {
                       Log.ef("ClistChangeRequestElementV2", e, "Problem with primaryGroup parsing, str=%s", tmpArg1);
                   }
               } else {
                   Log.e("ClistChangeRequestElementV2", "Problem with primaryGroup parsing - unknown type");
               }
              
                break;
            case 6:
                // type: java.lang.String
                this.targetUser = (java.lang.String)arg1;
                break;
            case 7:
                // type: java.lang.Boolean
               if (arg1 instanceof Boolean) {
                   this.managePairingRequests = (java.lang.Boolean) arg1;
               } else if (arg1 instanceof java.lang.String) {
                   final String tmpArg1 = (String) arg1;
                   try {
                       this.managePairingRequests = Boolean.parseBoolean(tmpArg1);
                   } catch (Exception e) {
                       Log.ef("ClistChangeRequestElementV2", e, "Problem with managePairingRequests parsing, str=%s", tmpArg1);
                   }
               } else {
                   Log.e("ClistChangeRequestElementV2", "Problem with managePairingRequests parsing - unknown type");
               }
              
                break;
            case 8:
                // type: java.lang.Integer
               if (arg1 instanceof Integer) {
                   this.version = (java.lang.Integer) arg1;
               } else if (arg1 instanceof java.lang.String) {
                   final String tmpArg1 = (String) arg1;
                   try {
                       this.version = Integer.parseInt(tmpArg1);
                   } catch (Exception e) {
                       Log.ef("ClistChangeRequestElementV2", e, "Problem with version parsing, str=%s", tmpArg1);
                   }
               } else {
                   Log.e("ClistChangeRequestElementV2", "Problem with version parsing - unknown type");
               }
              
                break;
            case 9:
                // type: java.lang.Integer
               if (arg1 instanceof Integer) {
                   this.auxVersion = (java.lang.Integer) arg1;
               } else if (arg1 instanceof java.lang.String) {
                   final String tmpArg1 = (String) arg1;
                   try {
                       this.auxVersion = Integer.parseInt(tmpArg1);
                   } catch (Exception e) {
                       Log.ef("ClistChangeRequestElementV2", e, "Problem with auxVersion parsing, str=%s", tmpArg1);
                   }
               } else {
                   Log.e("ClistChangeRequestElementV2", "Problem with auxVersion parsing - unknown type");
               }
              
                break;
            case 10:
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
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "whitelistAction", net.phonex.soap.entities.WhitelistAction.class); // same package
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "action", net.phonex.soap.entities.ContactlistAction.class); // same package
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "user", net.phonex.soap.entities.UserIdentifier.class); // same package
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "UserIdentifier", net.phonex.soap.entities.UserIdentifier.class); // package i class
        new net.phonex.soap.entities.UserIdentifier().register(soapEnvelope); // registerable 
    } 

    @Override
    public String toString() {
        return "ClistChangeRequestElementV2{"+"user=" + this.user+", action=" + this.action+", displayName=" + this.displayName+", auxData=" + this.auxData+", whitelistAction=" + this.whitelistAction+", primaryGroup=" + this.primaryGroup+", targetUser=" + this.targetUser+", managePairingRequests=" + this.managePairingRequests+", version=" + this.version+", auxVersion=" + this.auxVersion+", auxJSON=" + this.auxJSON + '}';
    }
}
